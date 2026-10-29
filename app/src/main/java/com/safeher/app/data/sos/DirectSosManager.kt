package com.safeher.app.data.sos

import com.safeher.app.data.battery.BatteryStateReader
import com.safeher.app.data.guardian.Guardian
import com.safeher.app.data.guardian.GuardianRepository
import com.safeher.app.data.journey.Journey
import com.safeher.app.data.location.LocationData
import com.safeher.app.data.location.LocationRepository
import com.safeher.app.data.location.LocationResult
import com.safeher.app.data.local.db.SosEventDao
import com.safeher.app.data.local.db.SosEventEntity
import com.safeher.app.data.session.UserSessionManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicBoolean

/**
 * State representing the progression of an automatic direct SOS dispatch.
 */
sealed interface DirectSosState {
    data object Idle : DirectSosState

    data class Countdown(
        val secondsRemaining: Int,
        val totalSeconds: Int
    ) : DirectSosState

    data class Dispatching(
        val eventId: Long,
        val statuses: List<RecipientDispatchStatus>,
        val locationAttached: Boolean,
        val locationNote: String? = null
    ) : DirectSosState

    data class Completed(
        val eventId: Long,
        val statuses: List<RecipientDispatchStatus>,
        val submittedCount: Int,
        val deliveryConfirmedCount: Int,
        val failedCount: Int,
        val locationAttached: Boolean,
        val locationNote: String? = null
    ) : DirectSosState

    data class Error(
        val reason: String,
        val canFallbackToManual: Boolean = true
    ) : DirectSosState
}

/**
 * Manages the lifecycle of an emergency SOS dispatch:
 * 1. Cancellable pre-dispatch countdown.
 * 2. Pre-flight permission and hardware readiness validation.
 * 3. User-scoped, enabled guardian resolution.
 * 4. Fast, non-blocking location acquisition (max 3-second timeout).
 * 5. Independent parallel SMS transmission per guardian.
 * 6. Real-time recipient state updates (SUBMITTED_TO_RADIO, DELIVERY_CONFIRMED, FAILED).
 * 7. Persistent Room SOS event recording.
 * 8. Strict idempotency to prevent duplicate dispatches.
 */
class DirectSosManager(
    private val guardianRepository: GuardianRepository,
    private val sosPreferencesRepository: SosPreferencesRepository,
    private val directSmsSender: DirectSmsSender,
    private val locationRepository: LocationRepository? = null,
    private val sosEventDao: SosEventDao,
    private val sessionManager: UserSessionManager? = null,
    private val batteryStateReader: BatteryStateReader? = null,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val managerScope: CoroutineScope = CoroutineScope(ioDispatcher)
) {
    private val _state = MutableStateFlow<DirectSosState>(DirectSosState.Idle)
    val state: StateFlow<DirectSosState> = _state.asStateFlow()

    private val isDispatching = AtomicBoolean(false)
    private var countdownJob: Job? = null
    private val statusLock = Mutex()

    /**
     * Initiates the SOS workflow with a cancellable countdown.
     * If user cancels before countdown reaches 0, no SMS is sent.
     */
    fun startCancellableSos() {
        if (isDispatching.get()) return
        val current = _state.value
        if (current is DirectSosState.Dispatching || current is DirectSosState.Completed) return

        countdownJob?.cancel()
        val prefs = sosPreferencesRepository.getPreferences()
        val countdownSeconds = prefs.countdownDurationSeconds.coerceAtLeast(1)

        countdownJob = managerScope.launch {
            try {
                for (sec in countdownSeconds downTo 1) {
                    _state.value = DirectSosState.Countdown(
                        secondsRemaining = sec,
                        totalSeconds = countdownSeconds
                    )
                    delay(1000L)
                }
                // Countdown completed -> Trigger immediate dispatch
                executeDispatchInternal()
            } catch (_: CancellationException) {
                _state.value = DirectSosState.Idle
            }
        }
    }

    /**
     * Cancels the active countdown before dispatch begins.
     */
    fun cancelCountdown() {
        countdownJob?.cancel()
        countdownJob = null
        if (!isDispatching.get()) {
            _state.value = DirectSosState.Idle
        }
    }

    /**
     * Immediately dispatches direct SOS without a countdown (e.g. for quick emergency triggers).
     */
    fun triggerImmediateSos() {
        if (isDispatching.get()) return
        val current = _state.value
        if (current is DirectSosState.Dispatching || current is DirectSosState.Completed) return

        countdownJob?.cancel()
        countdownJob = null
        managerScope.launch {
            executeDispatchInternal()
        }
    }

    /**
     * Resets the manager back to Idle state once reviewed by the user.
     */
    fun reset() {
        if (!isDispatching.get()) {
            countdownJob?.cancel()
            countdownJob = null
            _state.value = DirectSosState.Idle
        }
    }

    private suspend fun executeDispatchInternal() = withContext(ioDispatcher) {
        if (!isDispatching.compareAndSet(false, true)) {
            return@withContext
        }

        val currentState = _state.value
        if (currentState is DirectSosState.Completed) {
            isDispatching.set(false)
            return@withContext
        }

        try {
            // 1. Verify consent and feature toggle
            val prefs = sosPreferencesRepository.getPreferences()
            if (!prefs.isDirectSmsConsentGiven || !prefs.isDirectSmsEnabled) {
                _state.value = DirectSosState.Error(
                    reason = "Direct SMS is not enabled. Please enable and authorize it in SOS Settings.",
                    canFallbackToManual = true
                )
                return@withContext
            }

            // 2. Verify SEND_SMS permission
            if (!directSmsSender.hasSmsPermission()) {
                _state.value = DirectSosState.Error(
                    reason = "SMS permission (SEND_SMS) is not granted. Please grant permission in SOS Settings or use manual SMS.",
                    canFallbackToManual = true
                )
                return@withContext
            }

            // 3. Verify telephony readiness
            val capability = directSmsSender.checkTelephonyCapability()
            if (capability is TelephonyCapability.Unavailable) {
                _state.value = DirectSosState.Error(
                    reason = "Telephony unavailable: ${capability.reason}.",
                    canFallbackToManual = true
                )
                return@withContext
            }

            // 4. Resolve eligible guardians scoped to current user
            val enabledGuardians = guardianRepository.getSosEnabledGuardians()
            val eligibleGuardians = EmergencyMessageBuilder.getEligibleGuardians(enabledGuardians)

            if (eligibleGuardians.isEmpty()) {
                _state.value = DirectSosState.Error(
                    reason = if (enabledGuardians.isEmpty()) {
                        "No guardians are enabled for SOS. Open SOS Settings or Contacts to enable recipients."
                    } else {
                        "Enabled guardians do not have valid phone numbers for SMS."
                    },
                    canFallbackToManual = false
                )
                return@withContext
            }

            // 5. Non-blocking fresh location acquisition (max 3 seconds)
            var locationData: LocationData? = null
            var locationNote: String? = null

            if (prefs.isLocationSharingEnabled && locationRepository != null) {
                val locResult = withTimeoutOrNull(3000L) {
                    try {
                        locationRepository.getCurrentLocation(isPrecise = true)
                    } catch (_: Exception) {
                        null
                    }
                }
                when (locResult) {
                    is LocationResult.Success -> {
                        if (!locResult.location.isStale) {
                            locationData = locResult.location
                        } else {
                            locationNote = "GPS signal was stale; sent message without live coordinates."
                        }
                    }
                    is LocationResult.PermissionDenied -> {
                        locationNote = "Location permission not granted; sent message without coordinates."
                    }
                    is LocationResult.LocationDisabled -> {
                        locationNote = "Device location services disabled; sent message without coordinates."
                    }
                    is LocationResult.Error -> {
                        locationNote = "Location fix unavailable: ${locResult.message}."
                    }
                    null -> {
                        locationNote = "Location request timed out; dispatched immediately without delaying emergency SMS."
                    }
                }
            } else if (!prefs.isLocationSharingEnabled) {
                locationNote = "Location sharing disabled in SOS Settings."
            }

            // 6. Capture battery near dispatch time and build final emergency SMS text
            val eventTimestamp = System.currentTimeMillis()
            val batteryInfo = try {
                batteryStateReader?.readBatteryInfo()
            } catch (_: Exception) {
                null
            }
            val messageText = EmergencyMessageBuilder.buildDirectSosMessage(
                customMessage = prefs.customSosMessage,
                location = locationData,
                includeLocation = prefs.isLocationSharingEnabled,
                batteryInfo = batteryInfo,
                alertTimestampMillis = eventTimestamp
            )

            // 7. Initialize Room SOS Event Record
            val currentUid = sessionManager?.currentUserId
            val eventEntity = SosEventEntity(
                0,
                currentUid,
                eventTimestamp,
                messageText,
                locationData?.latitude,
                locationData?.longitude,
                locationData?.accuracyMeters,
                eligibleGuardians.size,
                0, // successCount
                0, // failureCount
                "DIRECT_AUTOMATIC_SMS",
                "{}"
            )
            val eventId = try {
                sosEventDao.insert(eventEntity)
            } catch (_: Exception) {
                0L
            }
            eventEntity.id = eventId

            // 8. Initialize in-memory recipient statuses
            val statusMap = mutableMapOf<Long, RecipientDispatchStatus>()
            for (g in eligibleGuardians) {
                statusMap[g.id] = RecipientDispatchStatus(
                    guardianId = g.id,
                    guardianName = g.name,
                    phoneNumber = g.phoneNumber,
                    state = RecipientDispatchState.PENDING
                )
            }

            val hasLocation = locationData != null
            _state.value = DirectSosState.Dispatching(
                eventId = eventId,
                statuses = statusMap.values.toList(),
                locationAttached = hasLocation,
                locationNote = locationNote
            )

            // 9. Dispatch to each guardian independently
            val dispatchTasks = eligibleGuardians.map { guardian ->
                async {
                    directSmsSender.sendEmergencySms(
                        eventId = eventId,
                        guardianId = guardian.id,
                        guardianName = guardian.name,
                        phoneNumber = guardian.phoneNumber,
                        message = messageText
                    ) { updatedStatus ->
                        managerScope.launch(ioDispatcher) {
                            statusLock.withLock {
                                statusMap[guardian.id] = updatedStatus
                                _state.value = DirectSosState.Dispatching(
                                    eventId = eventId,
                                    statuses = statusMap.values.toList(),
                                    locationAttached = hasLocation,
                                    locationNote = locationNote
                                )
                            }
                        }
                    }
                }
            }

            val finalStatuses = dispatchTasks.awaitAll()

            // 10. Compute final metrics and update database
            var submitted = 0
            var confirmed = 0
            var failed = 0

            statusLock.withLock {
                for (s in finalStatuses) {
                    statusMap[s.guardianId] = s
                    when (s.state) {
                        RecipientDispatchState.SUBMITTED_TO_RADIO -> submitted++
                        RecipientDispatchState.DELIVERY_CONFIRMED -> confirmed++
                        RecipientDispatchState.FAILED -> failed++
                        RecipientDispatchState.PENDING -> {}
                    }
                }
            }

            if (eventId > 0) {
                try {
                    eventEntity.successCount = submitted + confirmed
                    eventEntity.failureCount = failed
                    sosEventDao.update(eventEntity)
                } catch (_: Exception) {
                    // Non-fatal if local event history update fails
                }
            }

            _state.value = DirectSosState.Completed(
                eventId = eventId,
                statuses = statusMap.values.toList(),
                submittedCount = submitted,
                deliveryConfirmedCount = confirmed,
                failedCount = failed,
                locationAttached = hasLocation,
                locationNote = locationNote
            )

        } catch (e: Exception) {
            _state.value = DirectSosState.Error(
                reason = "Unexpected error during SOS dispatch: ${e.localizedMessage ?: "Unknown error"}",
                canFallbackToManual = true
            )
        } finally {
            isDispatching.set(false)
        }
    }

    /**
     * Dispatches automatic overdue SOS alerts to configured guardians when a monitored
     * journey exceeds its ETA and 5-minute safety check window without acknowledgment.
     * Enforces strict idempotency, consent verification, recipient isolation, and non-blocking location.
     */
    suspend fun dispatchOverdueSos(
        journey: Journey,
        cachedLocation: LocationData? = null
    ): DirectSosState = withContext(ioDispatcher) {
        if (!isDispatching.compareAndSet(false, true)) {
            return@withContext _state.value
        }

        try {
            // 1. Verify journey is not already completed or cancelled
            if (journey.isTerminal) {
                val err = DirectSosState.Error("Journey is already ${journey.status.name.lowercase()}.", canFallbackToManual = false)
                _state.value = err
                return@withContext err
            }

            // 2. Verify safety check was not acknowledged
            if (journey.safetyCheckAcknowledged) {
                val err = DirectSosState.Error("Safety check was already acknowledged by user.", canFallbackToManual = false)
                _state.value = err
                return@withContext err
            }

            // 3. Verify overdue escalation has not already been attempted (Idempotency)
            if (journey.overdueEscalationAttempted) {
                val err = DirectSosState.Error("Overdue escalation has already been attempted for this journey.", canFallbackToManual = false)
                _state.value = err
                return@withContext err
            }

            // 4. Verify user consent & preference
            val prefs = sosPreferencesRepository.getPreferences()
            if (!prefs.isOverdueEscalationConsentGiven || !prefs.isOverdueEscalationEnabled) {
                val err = DirectSosState.Error(
                    reason = "Automatic overdue SOS escalation is not enabled. Please enable and authorize it in Journey Safety Settings.",
                    canFallbackToManual = true
                )
                _state.value = err
                return@withContext err
            }

            // 5. Verify SEND_SMS permission
            if (!directSmsSender.hasSmsPermission()) {
                val err = DirectSosState.Error(
                    reason = "SMS permission (SEND_SMS) is not granted. Automatic emergency SMS could not be sent.",
                    canFallbackToManual = true
                )
                _state.value = err
                return@withContext err
            }

            // 6. Verify telephony hardware readiness
            val capability = directSmsSender.checkTelephonyCapability()
            if (capability is TelephonyCapability.Unavailable) {
                val err = DirectSosState.Error(
                    reason = "Telephony unavailable: ${capability.reason}.",
                    canFallbackToManual = true
                )
                _state.value = err
                return@withContext err
            }

            // 7. Resolve eligible guardians scoped to current user
            val enabledGuardians = guardianRepository.getSosEnabledGuardians()
            val eligibleGuardians = EmergencyMessageBuilder.getEligibleGuardians(enabledGuardians)
            if (eligibleGuardians.isEmpty()) {
                val err = DirectSosState.Error(
                    reason = if (enabledGuardians.isEmpty()) {
                        "No guardians are enabled for SOS. Add or enable guardians in Contacts."
                    } else {
                        "Enabled guardians do not have valid phone numbers for SMS."
                    },
                    canFallbackToManual = false
                )
                _state.value = err
                return@withContext err
            }

            // 8. Location acquisition (use cached journey location or fast 2-sec GPS fix)
            var locationData: LocationData? = if (cachedLocation != null && !cachedLocation.isStale) {
                cachedLocation
            } else if (journey.lastLatitude != null && journey.lastLongitude != null && journey.isLocationFresh) {
                LocationData(
                    latitude = journey.lastLatitude,
                    longitude = journey.lastLongitude,
                    accuracyMeters = 20f,
                    timestampMillis = journey.lastLocationTimestamp ?: System.currentTimeMillis(),
                    isFresh = true,
                    isApproximate = false,
                    provider = "journey_monitoring"
                )
            } else null

            var locationNote: String? = null
            if (locationData == null && prefs.isLocationSharingEnabled && locationRepository != null) {
                val freshFix = withTimeoutOrNull(2000L) {
                    try {
                        locationRepository.getCurrentLocation(isPrecise = true)
                    } catch (_: Exception) { null }
                }
                if (freshFix is LocationResult.Success && !freshFix.location.isStale) {
                    locationData = freshFix.location
                } else {
                    locationNote = "GPS coordinates unavailable; sent emergency alert without location."
                }
            } else if (!prefs.isLocationSharingEnabled) {
                locationNote = "Location sharing disabled in SOS Settings."
            }

            // 9. Build overdue emergency message
            val now = System.currentTimeMillis()
            val overdueMinutes = ((now - journey.expectedArrivalTimestamp) / (60 * 1000L)).coerceAtLeast(5L)
            val messageText = EmergencyMessageBuilder.buildOverdueSosMessage(
                destination = journey.destination,
                expectedArrivalTimestamp = journey.expectedArrivalTimestamp,
                overdueMinutes = overdueMinutes,
                location = locationData,
                includeLocation = prefs.isLocationSharingEnabled
            )

            // 10. Record Room SOS Event
            val currentUid = sessionManager?.currentUserId
            val eventEntity = SosEventEntity(
                0,
                currentUid,
                now,
                messageText,
                locationData?.latitude,
                locationData?.longitude,
                locationData?.accuracyMeters,
                eligibleGuardians.size,
                0,
                0,
                "AUTOMATIC_OVERDUE_ESCALATION",
                "{\"journeyId\":${journey.id}}",
                journey.id
            )
            val eventId = try {
                sosEventDao.insert(eventEntity)
            } catch (_: Exception) { 0L }
            eventEntity.id = eventId

            // 11. Initial dispatch state
            val statusMap = mutableMapOf<Long, RecipientDispatchStatus>()
            for (g in eligibleGuardians) {
                statusMap[g.id] = RecipientDispatchStatus(
                    guardianId = g.id,
                    guardianName = g.name,
                    phoneNumber = g.phoneNumber,
                    state = RecipientDispatchState.PENDING
                )
            }

            val hasLocation = locationData != null
            _state.value = DirectSosState.Dispatching(
                eventId = eventId,
                statuses = statusMap.values.toList(),
                locationAttached = hasLocation,
                locationNote = locationNote
            )

            // 12. Dispatch to each guardian independently
            val dispatchTasks = eligibleGuardians.map { guardian ->
                async {
                    directSmsSender.sendEmergencySms(
                        eventId = eventId,
                        guardianId = guardian.id,
                        guardianName = guardian.name,
                        phoneNumber = guardian.phoneNumber,
                        message = messageText
                    ) { updatedStatus ->
                        managerScope.launch(ioDispatcher) {
                            statusLock.withLock {
                                statusMap[guardian.id] = updatedStatus
                                _state.value = DirectSosState.Dispatching(
                                    eventId = eventId,
                                    statuses = statusMap.values.toList(),
                                    locationAttached = hasLocation,
                                    locationNote = locationNote
                                )
                            }
                        }
                    }
                }
            }

            val finalStatuses = dispatchTasks.awaitAll()

            // 13. Summarize metrics
            var submitted = 0
            var confirmed = 0
            var failed = 0
            statusLock.withLock {
                for (s in finalStatuses) {
                    statusMap[s.guardianId] = s
                    when (s.state) {
                        RecipientDispatchState.SUBMITTED_TO_RADIO -> submitted++
                        RecipientDispatchState.DELIVERY_CONFIRMED -> confirmed++
                        RecipientDispatchState.FAILED -> failed++
                        RecipientDispatchState.PENDING -> {}
                    }
                }
            }

            if (eventId > 0) {
                try {
                    eventEntity.successCount = submitted + confirmed
                    eventEntity.failureCount = failed
                    sosEventDao.update(eventEntity)
                } catch (_: Exception) {}
            }

            val completed = DirectSosState.Completed(
                eventId = eventId,
                statuses = statusMap.values.toList(),
                submittedCount = submitted,
                deliveryConfirmedCount = confirmed,
                failedCount = failed,
                locationAttached = hasLocation,
                locationNote = locationNote
            )
            _state.value = completed
            completed
        } catch (e: Exception) {
            val err = DirectSosState.Error(
                reason = "Unexpected error during overdue SOS dispatch: ${e.localizedMessage ?: "Unknown error"}",
                canFallbackToManual = true
            )
            _state.value = err
            err
        } finally {
            isDispatching.set(false)
        }
    }
}
