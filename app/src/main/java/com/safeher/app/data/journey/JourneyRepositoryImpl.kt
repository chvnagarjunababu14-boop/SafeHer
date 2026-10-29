package com.safeher.app.data.journey

import com.safeher.app.data.local.db.JourneyDao
import com.safeher.app.data.local.db.JourneyEntity
import com.safeher.app.data.local.db.JourneyEventDao
import com.safeher.app.data.local.db.JourneyEventEntity
import com.safeher.app.data.local.db.SafeHerDatabase
import com.safeher.app.data.session.UserSessionManager
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Concrete implementation of [JourneyRepository] backed by Room SQLite.
 *
 * Guarantees:
 * - Account isolation: All records and queries are scoped to the authenticated Firebase/Demo user.
 * - Local-first persistence; all records survive process termination and reboot.
 * - Single active journey rule enforced at data layer.
 * - Safe terminal state enforcement (cannot mutate completed or cancelled journeys).
 * - Persistent journey event timeline history with zero duplicate transitions.
 * - Exactly one OVERDUE_OBSERVED event per observed transition.
 * - Safe clock recovery reconciling status if device time changes.
 * - Zero network transmission or background location tracking.
 */
class JourneyRepositoryImpl(
    private val dao: JourneyDao,
    private val eventDao: JourneyEventDao? = null,
    private val database: SafeHerDatabase? = null,
    private val sessionManager: UserSessionManager? = null,
    private val scheduler: JourneyMonitoringScheduler? = null,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : JourneyRepository {

    constructor(
        database: SafeHerDatabase,
        sessionManager: UserSessionManager? = null,
        scheduler: JourneyMonitoringScheduler? = null,
        ioDispatcher: CoroutineDispatcher = Dispatchers.IO
    ) : this(
        dao = database.journeyDao(),
        eventDao = database.journeyEventDao(),
        database = database,
        sessionManager = sessionManager,
        scheduler = scheduler,
        ioDispatcher = ioDispatcher
    )

    constructor(
        database: SafeHerDatabase,
        sessionManager: UserSessionManager? = null
    ) : this(
        database = database,
        sessionManager = sessionManager,
        scheduler = null,
        ioDispatcher = Dispatchers.IO
    )

    private val updateTrigger = MutableStateFlow(System.currentTimeMillis())
    private val mutationMutex = Mutex()

    override fun getJourneysFlow(): Flow<List<Journey>> {
        val sessionFlow = sessionManager?.activeUserId ?: MutableStateFlow(null)
        return combine(sessionFlow, updateTrigger) { uid, _ ->
            if (sessionManager != null && uid == null) {
                emptyList()
            } else {
                getJourneys()
            }
        }.flowOn(ioDispatcher)
    }

    override fun getActiveJourneyFlow(): Flow<Journey?> {
        val sessionFlow = sessionManager?.activeUserId ?: MutableStateFlow(null)
        return combine(sessionFlow, updateTrigger) { uid, _ ->
            if (sessionManager != null && uid == null) {
                null
            } else {
                getActiveJourney()
            }
        }.flowOn(ioDispatcher)
    }

    override suspend fun getJourneys(): List<Journey> = withContext(ioDispatcher) {
        mutationMutex.withLock {
            val now = System.currentTimeMillis()
            val list = if (sessionManager != null) {
                val uid = sessionManager.currentUserId ?: return@withContext emptyList()
                dao.getJourneysForUser(uid)
            } else {
                dao.allJourneys
            }
            list.map { entity ->
                reconcileEntityStatus(entity, now).toDomain()
            }
        }
    }

    override suspend fun getActiveJourney(): Journey? = withContext(ioDispatcher) {
        mutationMutex.withLock {
            val now = System.currentTimeMillis()
            val activeEntity = if (sessionManager != null) {
                val uid = sessionManager.currentUserId ?: return@withContext null
                dao.getLatestActiveOrOverdueJourneyForUser(uid)
            } else {
                dao.latestActiveOrOverdueJourney
            } ?: return@withContext null
            reconcileEntityStatus(activeEntity, now).toDomain()
        }
    }

    override suspend fun getJourneyById(id: Long): Journey? = withContext(ioDispatcher) {
        mutationMutex.withLock {
            val now = System.currentTimeMillis()
            val entity = if (sessionManager != null) {
                val uid = sessionManager.currentUserId ?: return@withContext null
                dao.getJourneyByIdForUser(id, uid)
            } else {
                dao.getJourneyById(id)
            } ?: return@withContext null
            reconcileEntityStatus(entity, now).toDomain()
        }
    }

    override suspend fun refreshJourneyStatus(): Journey? = withContext(ioDispatcher) {
        mutationMutex.withLock {
            val now = System.currentTimeMillis()
            val activeEntity = if (sessionManager != null) {
                val uid = sessionManager.currentUserId ?: return@withContext null
                dao.getLatestActiveOrOverdueJourneyForUser(uid)
            } else {
                dao.latestActiveOrOverdueJourney
            } ?: return@withContext null
            val reconciled = reconcileEntityStatus(activeEntity, now)
            notifyDataChanged()
            reconciled.toDomain()
        }
    }

    private fun reconcileEntityStatus(entity: JourneyEntity, now: Long): JourneyEntity {
        val domain = entity.toDomain()
        if (domain.isTerminal) {
            return entity
        }

        // 1. Transition ACTIVE -> OVERDUE when now >= expectedArrivalTimestamp
        if (domain.status == JourneyStatus.ACTIVE && now >= domain.expectedArrivalTimestamp) {
            val updated = entity.copyEntity(status = JourneyStatus.OVERDUE.name, updatedAt = now)
            runInDbTransaction {
                dao.update(updated)
                // Insert exactly one OVERDUE_OBSERVED event for this transition
                eventDao?.insert(
                    JourneyEventEntity(
                        0L,
                        entity.id,
                        JourneyEventType.OVERDUE_OBSERVED.name,
                        now,
                        null
                    )
                )
            }
            return updated
        }

        // 2. Clock change recovery: if OVERDUE but now < expectedArrivalTimestamp, reconcile back to ACTIVE
        if (domain.status == JourneyStatus.OVERDUE && now < domain.expectedArrivalTimestamp) {
            val updated = entity.copyEntity(status = JourneyStatus.ACTIVE.name, updatedAt = now)
            runInDbTransaction {
                dao.update(updated)
            }
            return updated
        }

        return entity
    }

    override suspend fun startJourney(
        title: String?,
        startLocation: String,
        destination: String,
        expectedArrivalTimestamp: Long,
        startLatitude: Double?,
        startLongitude: Double?,
        destinationLatitude: Double?,
        destinationLongitude: Double?,
        enableMonitoring: Boolean
    ): JourneyOperationResult = withContext(ioDispatcher) {
        mutationMutex.withLock {
            val currentUid = sessionManager?.currentUserId
            if (sessionManager != null && currentUid == null) {
                return@withContext JourneyOperationResult.Error("Cannot start journey when signed out.")
            }

            val trimmedDestination = destination.trim()
            if (trimmedDestination.isEmpty()) {
                return@withContext JourneyOperationResult.ValidationError(
                    "destination",
                    "Destination is required to start a safety journey."
                )
            }

            val trimmedStartLocation = startLocation.trim().ifEmpty { "Starting point" }
            val now = System.currentTimeMillis()

            if (expectedArrivalTimestamp <= now) {
                return@withContext JourneyOperationResult.ValidationError(
                    "expectedArrivalTimestamp",
                    "Expected arrival time must be set in the future."
                )
            }

            // Enforce single active journey constraint for this user
            val existingActive = if (currentUid != null) {
                dao.getLatestActiveOrOverdueJourneyForUser(currentUid)
            } else {
                dao.latestActiveOrOverdueJourney
            }
            if (existingActive != null && !existingActive.toDomain().isTerminal) {
                return@withContext JourneyOperationResult.ActiveJourneyExists(
                    existingActive.id,
                    "A safety journey to \"${existingActive.destination}\" is already active. Complete or cancel it before starting another."
                )
            }

            val deadline = expectedArrivalTimestamp + JourneyMonitoringScheduler.DEFAULT_ESCALATION_WINDOW_MILLIS
            val entity = JourneyEntity(
                0,
                currentUid,
                title?.trim()?.ifBlank { null },
                trimmedStartLocation,
                trimmedDestination,
                startLatitude,
                startLongitude,
                destinationLatitude,
                destinationLongitude,
                now,
                expectedArrivalTimestamp,
                0L,
                JourneyStatus.ACTIVE.name,
                0L,
                0,
                null,
                now,
                now,
                enableMonitoring,
                startLatitude,
                startLongitude,
                now,
                0L,
                false,
                deadline,
                false
            )

            return@withContext try {
                val insertedId = runInDbTransaction {
                    val id = dao.insert(entity)
                    eventDao?.insert(
                        JourneyEventEntity(
                            0L,
                            id,
                            JourneyEventType.STARTED.name,
                            now,
                            null
                        )
                    )
                    id
                }
                if (enableMonitoring) {
                    scheduler?.scheduleJourneyAlarms(insertedId, expectedArrivalTimestamp, deadline)
                }
                notifyDataChanged()
                JourneyOperationResult.Success(insertedId, "Safety journey started successfully.")
            } catch (e: Exception) {
                JourneyOperationResult.Error(e.localizedMessage ?: "Failed to save journey locally.")
            }
        }
    }

    override suspend fun checkIn(id: Long): JourneyOperationResult = withContext(ioDispatcher) {
        mutationMutex.withLock {
            val currentUid = sessionManager?.currentUserId
            if (sessionManager != null && currentUid == null) {
                return@withContext JourneyOperationResult.Error("Cannot check in when signed out.")
            }

            val existing = if (currentUid != null) {
                dao.getJourneyByIdForUser(id, currentUid)
            } else {
                dao.getJourneyById(id)
            } ?: return@withContext JourneyOperationResult.Error("Journey not found.")

            val domain = existing.toDomain()
            if (domain.isTerminal) {
                return@withContext JourneyOperationResult.TerminalStateError(
                    "Cannot check in: this journey is already ${domain.status.name.lowercase()}."
                )
            }

            val now = System.currentTimeMillis()

            // Idempotent protection against accidental double-taps within 2 seconds
            if (now - existing.lastCheckInTimestamp < 2000L && existing.checkInCount > 0) {
                return@withContext JourneyOperationResult.Success(id, "Check-in recorded locally.")
            }

            val newCount = existing.checkInCount + 1
            val newHistory = domain.checkInHistory + now
            val serializedHistory = newHistory.joinToString(",")

            val updated = existing.copyEntity(
                lastCheckInTimestamp = now,
                checkInCount = newCount,
                checkInHistory = serializedHistory,
                updatedAt = now
            )

            return@withContext try {
                runInDbTransaction {
                    dao.update(updated)
                    eventDao?.insert(
                        JourneyEventEntity(
                            0L,
                            id,
                            JourneyEventType.CHECK_IN.name,
                            now,
                            "Check-in #$newCount"
                        )
                    )
                }
                notifyDataChanged()
                JourneyOperationResult.Success(id, "Check-in recorded locally.")
            } catch (e: Exception) {
                JourneyOperationResult.Error(e.localizedMessage ?: "Failed to record check-in.")
            }
        }
    }

    override suspend fun extendJourney(
        id: Long,
        newExpectedArrivalTimestamp: Long
    ): JourneyOperationResult = withContext(ioDispatcher) {
        mutationMutex.withLock {
            val currentUid = sessionManager?.currentUserId
            if (sessionManager != null && currentUid == null) {
                return@withContext JourneyOperationResult.Error("Cannot extend journey when signed out.")
            }

            val existing = if (currentUid != null) {
                dao.getJourneyByIdForUser(id, currentUid)
            } else {
                dao.getJourneyById(id)
            } ?: return@withContext JourneyOperationResult.Error("Journey not found.")

            val domain = existing.toDomain()
            if (domain.isTerminal) {
                return@withContext JourneyOperationResult.TerminalStateError(
                    "Cannot extend: this journey is already ${domain.status.name.lowercase()}."
                )
            }

            val now = System.currentTimeMillis()
            if (newExpectedArrivalTimestamp <= now) {
                return@withContext JourneyOperationResult.ValidationError(
                    "expectedArrivalTimestamp",
                    "New expected arrival time must be set in the future."
                )
            }

            val newDeadline = newExpectedArrivalTimestamp + JourneyMonitoringScheduler.DEFAULT_ESCALATION_WINDOW_MILLIS

            // Transition overdue back to active upon extension, resetting overdue flags
            val updated = existing.copyEntity(
                expectedArrivalTimestamp = newExpectedArrivalTimestamp,
                status = JourneyStatus.ACTIVE.name,
                safetyCheckAcknowledged = false,
                overdueEscalationAttempted = false,
                overdueEscalationDeadline = newDeadline,
                updatedAt = now
            )

            val timeStr = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(newExpectedArrivalTimestamp))

            return@withContext try {
                runInDbTransaction {
                    dao.update(updated)
                    eventDao?.insert(
                        JourneyEventEntity(
                            0L,
                            id,
                            JourneyEventType.ETA_EXTENDED.name,
                            now,
                            "Extended to $timeStr"
                        )
                    )
                }
                if (existing.isMonitoringActive) {
                    scheduler?.scheduleJourneyAlarms(id, newExpectedArrivalTimestamp, newDeadline)
                }
                notifyDataChanged()
                JourneyOperationResult.Success(id, "Journey extended successfully.")
            } catch (e: Exception) {
                JourneyOperationResult.Error(e.localizedMessage ?: "Failed to extend journey.")
            }
        }
    }

    override suspend fun completeJourney(id: Long): JourneyOperationResult = withContext(ioDispatcher) {
        mutationMutex.withLock {
            val currentUid = sessionManager?.currentUserId
            if (sessionManager != null && currentUid == null) {
                return@withContext JourneyOperationResult.Error("Cannot complete journey when signed out.")
            }

            val existing = if (currentUid != null) {
                dao.getJourneyByIdForUser(id, currentUid)
            } else {
                dao.getJourneyById(id)
            } ?: return@withContext JourneyOperationResult.Error("Journey not found.")

            val domain = existing.toDomain()
            if (domain.isTerminal) {
                return@withContext JourneyOperationResult.TerminalStateError(
                    "This journey has already been ${domain.status.name.lowercase()}."
                )
            }

            val now = System.currentTimeMillis()
            val updated = existing.copyEntity(
                completedTimestamp = now,
                status = JourneyStatus.COMPLETED.name,
                isMonitoringActive = false,
                updatedAt = now
            )

            return@withContext try {
                runInDbTransaction {
                    dao.update(updated)
                    eventDao?.insert(
                        JourneyEventEntity(
                            0L,
                            id,
                            JourneyEventType.COMPLETED.name,
                            now,
                            null
                        )
                    )
                }
                scheduler?.cancelAllJourneyAlarms(id)
                notifyDataChanged()
                JourneyOperationResult.Success(id, "Arrived safely! Journey completed.")
            } catch (e: Exception) {
                JourneyOperationResult.Error(e.localizedMessage ?: "Failed to complete journey.")
            }
        }
    }

    override suspend fun cancelJourney(id: Long): JourneyOperationResult = withContext(ioDispatcher) {
        mutationMutex.withLock {
            val currentUid = sessionManager?.currentUserId
            if (sessionManager != null && currentUid == null) {
                return@withContext JourneyOperationResult.Error("Cannot cancel journey when signed out.")
            }

            val existing = if (currentUid != null) {
                dao.getJourneyByIdForUser(id, currentUid)
            } else {
                dao.getJourneyById(id)
            } ?: return@withContext JourneyOperationResult.Error("Journey not found.")

            val domain = existing.toDomain()
            if (domain.isTerminal) {
                return@withContext JourneyOperationResult.TerminalStateError(
                    "This journey has already been ${domain.status.name.lowercase()}."
                )
            }

            val now = System.currentTimeMillis()
            val updated = existing.copyEntity(
                completedTimestamp = now,
                status = JourneyStatus.CANCELLED.name,
                isMonitoringActive = false,
                updatedAt = now
            )

            return@withContext try {
                runInDbTransaction {
                    dao.update(updated)
                    eventDao?.insert(
                        JourneyEventEntity(
                            0L,
                            id,
                            JourneyEventType.CANCELLED.name,
                            now,
                            null
                        )
                    )
                }
                scheduler?.cancelAllJourneyAlarms(id)
                notifyDataChanged()
                JourneyOperationResult.Success(id, "Journey cancelled.")
            } catch (e: Exception) {
                JourneyOperationResult.Error(e.localizedMessage ?: "Failed to cancel journey.")
            }
        }
    }

    override suspend fun deleteJourney(id: Long): Boolean = withContext(ioDispatcher) {
        mutationMutex.withLock {
            val currentUid = sessionManager?.currentUserId
            if (sessionManager != null && currentUid == null) {
                return@withContext false
            }

            val existing = if (currentUid != null) {
                dao.getJourneyByIdForUser(id, currentUid)
            } else {
                dao.getJourneyById(id)
            } ?: return@withContext false

            val rows = runInDbTransaction {
                eventDao?.deleteEventsForJourney(existing.id)
                if (currentUid != null) {
                    dao.deleteByIdForUser(existing.id, currentUid)
                } else {
                    dao.deleteById(existing.id)
                }
            }
            if (rows > 0) {
                notifyDataChanged()
                true
            } else {
                false
            }
        }
    }

    override suspend fun acknowledgeSafetyCheck(id: Long): JourneyOperationResult = withContext(ioDispatcher) {
        mutationMutex.withLock {
            val currentUid = sessionManager?.currentUserId
            if (sessionManager != null && currentUid == null) {
                return@withContext JourneyOperationResult.Error("Cannot acknowledge safety check when signed out.")
            }

            val existing = if (currentUid != null) {
                dao.getJourneyByIdForUser(id, currentUid)
            } else {
                dao.getJourneyById(id)
            } ?: return@withContext JourneyOperationResult.Error("Journey not found.")

            val domain = existing.toDomain()
            if (domain.isTerminal) {
                return@withContext JourneyOperationResult.TerminalStateError("This journey is already ${domain.status.name.lowercase()}.")
            }

            val now = System.currentTimeMillis()
            runInDbTransaction {
                dao.markSafetyCheckAcknowledged(id, now)
                eventDao?.insert(
                    JourneyEventEntity(
                        0L,
                        id,
                        JourneyEventType.SAFETY_ACKNOWLEDGED.name,
                        now,
                        "Safety confirmed by user. Automatic escalation cancelled."
                    )
                )
            }

            scheduler?.cancelEscalationAlarm(id)
            notifyDataChanged()
            JourneyOperationResult.Success(id, "Safety confirmed! Overdue escalation has been cancelled.")
        }
    }

    override suspend fun updateJourneyLocation(
        journeyId: Long,
        latitude: Double,
        longitude: Double,
        timestamp: Long
    ): Boolean = withContext(ioDispatcher) {
        val rows = dao.updateLastLocation(journeyId, latitude, longitude, timestamp)
        if (rows > 0) {
            notifyDataChanged()
            true
        } else {
            false
        }
    }

    override suspend fun recordSafetyCheckSent(
        journeyId: Long,
        checkTime: Long,
        escalationDeadline: Long
    ): Boolean = withContext(ioDispatcher) {
        val rows = runInDbTransaction {
            val count = dao.recordSafetyCheckNotification(journeyId, checkTime, escalationDeadline, checkTime)
            eventDao?.insert(
                JourneyEventEntity(
                    0L,
                    journeyId,
                    JourneyEventType.OVERDUE_SAFETY_CHECK_SENT.name,
                    checkTime,
                    "Prompted user with a 5-minute safety confirmation window."
                )
            )
            count
        }
        if (rows > 0) {
            notifyDataChanged()
            true
        } else {
            false
        }
    }

    override suspend fun markOverdueEscalationAttempted(journeyId: Long): Boolean = withContext(ioDispatcher) {
        val now = System.currentTimeMillis()
        val rows = runInDbTransaction {
            val count = dao.markOverdueEscalationAttempted(journeyId, now)
            eventDao?.insert(
                JourneyEventEntity(
                    0L,
                    journeyId,
                    JourneyEventType.OVERDUE_ESCALATION_SOS.name,
                    now,
                    "Automatic overdue emergency SOS alert dispatched to guardians."
                )
            )
            count
        }
        if (rows > 0) {
            notifyDataChanged()
            true
        } else {
            false
        }
    }

    override suspend fun setMonitoringActive(journeyId: Long, isActive: Boolean): Boolean = withContext(ioDispatcher) {
        val now = System.currentTimeMillis()
        val rows = dao.setMonitoringActive(journeyId, isActive, now)
        if (rows > 0) {
            notifyDataChanged()
            true
        } else {
            false
        }
    }

    override suspend fun getActiveMonitoredJourneys(): List<Journey> = withContext(ioDispatcher) {
        val now = System.currentTimeMillis()
        val list = if (sessionManager != null) {
            val uid = sessionManager.currentUserId ?: return@withContext emptyList()
            dao.getActiveMonitoredJourneysForUser(uid)
        } else {
            dao.activeMonitoredJourneys
        }
        list.map { reconcileEntityStatus(it, now).toDomain() }
    }

    override suspend fun getEventsForJourney(journeyId: Long): List<JourneyEvent> = withContext(ioDispatcher) {
        val currentUid = sessionManager?.currentUserId
        if (sessionManager != null && currentUid == null) {
            return@withContext emptyList()
        }
        if (currentUid != null) {
            val journey = dao.getJourneyByIdForUser(journeyId, currentUid)
            if (journey == null) return@withContext emptyList()
        }
        eventDao?.getEventsForJourney(journeyId)?.map { it.toDomain() } ?: emptyList()
    }

    override fun getEventsFlowForJourney(journeyId: Long): Flow<List<JourneyEvent>> {
        val sessionFlow = sessionManager?.activeUserId ?: MutableStateFlow(null)
        return combine(sessionFlow, updateTrigger) { uid, _ ->
            if (sessionManager != null && uid == null) {
                emptyList()
            } else {
                getEventsForJourney(journeyId)
            }
        }.flowOn(ioDispatcher)
    }

    private inline fun <T> runInDbTransaction(crossinline block: () -> T): T {
        return if (database != null) {
            database.runInTransaction<T> { block() }
        } else {
            block()
        }
    }

    private fun notifyDataChanged() {
        updateTrigger.value = System.currentTimeMillis()
    }

    private fun JourneyEntity.copyEntity(
        userId: String? = this.userId,
        title: String? = this.title,
        startLocation: String = this.startLocation,
        destination: String = this.destination,
        startLatitude: Double? = this.startLatitude,
        startLongitude: Double? = this.startLongitude,
        destinationLatitude: Double? = this.destinationLatitude,
        destinationLongitude: Double? = this.destinationLongitude,
        startTimestamp: Long = this.startTimestamp,
        expectedArrivalTimestamp: Long = this.expectedArrivalTimestamp,
        completedTimestamp: Long = this.completedTimestamp,
        status: String = this.status,
        lastCheckInTimestamp: Long = this.lastCheckInTimestamp,
        checkInCount: Int = this.checkInCount,
        checkInHistory: String? = this.checkInHistory,
        createdAt: Long = this.createdAt,
        updatedAt: Long = this.updatedAt,
        isMonitoringActive: Boolean = this.isMonitoringActive,
        lastLatitude: Double? = this.lastLatitude,
        lastLongitude: Double? = this.lastLongitude,
        lastLocationTimestamp: Long = this.lastLocationTimestamp,
        overdueSafetyCheckTimestamp: Long = this.overdueSafetyCheckTimestamp,
        safetyCheckAcknowledged: Boolean = this.safetyCheckAcknowledged,
        overdueEscalationDeadline: Long = this.overdueEscalationDeadline,
        overdueEscalationAttempted: Boolean = this.overdueEscalationAttempted
    ): JourneyEntity {
        return JourneyEntity(
            id,
            userId,
            title,
            startLocation,
            destination,
            startLatitude,
            startLongitude,
            destinationLatitude,
            destinationLongitude,
            startTimestamp,
            expectedArrivalTimestamp,
            completedTimestamp,
            status,
            lastCheckInTimestamp,
            checkInCount,
            checkInHistory,
            createdAt,
            updatedAt,
            isMonitoringActive,
            lastLatitude,
            lastLongitude,
            lastLocationTimestamp,
            overdueSafetyCheckTimestamp,
            safetyCheckAcknowledged,
            overdueEscalationDeadline,
            overdueEscalationAttempted
        )
    }

    private fun JourneyEntity.toDomain(): Journey {
        val checkIns = checkInHistory?.split(",")
            ?.mapNotNull { it.trim().toLongOrNull() }
            ?: emptyList()

        val parsedStatus = try {
            JourneyStatus.valueOf(status ?: JourneyStatus.ACTIVE.name)
        } catch (e: Exception) {
            JourneyStatus.ACTIVE
        }

        return Journey(
            id = id,
            title = title,
            startLocation = startLocation ?: "Starting point",
            destination = destination ?: "Unknown destination",
            startLatitude = startLatitude,
            startLongitude = startLongitude,
            destinationLatitude = destinationLatitude,
            destinationLongitude = destinationLongitude,
            startTimestamp = startTimestamp,
            expectedArrivalTimestamp = expectedArrivalTimestamp,
            completedTimestamp = if (completedTimestamp > 0) completedTimestamp else null,
            status = parsedStatus,
            lastCheckInTimestamp = if (lastCheckInTimestamp > 0) lastCheckInTimestamp else null,
            checkInCount = checkInCount,
            checkInHistory = checkIns,
            createdAt = createdAt,
            updatedAt = updatedAt,
            isMonitoringActive = isMonitoringActive,
            lastLatitude = lastLatitude,
            lastLongitude = lastLongitude,
            lastLocationTimestamp = if (lastLocationTimestamp > 0L) lastLocationTimestamp else null,
            overdueSafetyCheckTimestamp = if (overdueSafetyCheckTimestamp > 0L) overdueSafetyCheckTimestamp else null,
            safetyCheckAcknowledged = safetyCheckAcknowledged,
            overdueEscalationDeadline = if (overdueEscalationDeadline > 0L) overdueEscalationDeadline else null,
            overdueEscalationAttempted = overdueEscalationAttempted
        )
    }
}
