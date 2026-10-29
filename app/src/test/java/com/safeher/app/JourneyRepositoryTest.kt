package com.safeher.app

import com.safeher.app.data.journey.Journey
import com.safeher.app.data.journey.JourneyOperationResult
import com.safeher.app.data.journey.JourneyRepositoryImpl
import com.safeher.app.data.journey.JourneyStatus
import com.safeher.app.data.local.db.JourneyDao
import com.safeher.app.data.local.db.JourneyEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class FakeJourneyDao : JourneyDao {
    private val storage = mutableMapOf<Long, JourneyEntity>()
    private var nextId = 1L

    override fun getAllJourneys(): List<JourneyEntity> {
        return storage.values.sortedByDescending { it.startTimestamp }
    }

    override fun getActiveOrOverdueJourneys(): List<JourneyEntity> {
        return storage.values
            .filter { it.status == JourneyStatus.ACTIVE.name || it.status == JourneyStatus.OVERDUE.name }
            .sortedByDescending { it.startTimestamp }
    }

    override fun getLatestActiveOrOverdueJourney(): JourneyEntity? {
        return getActiveOrOverdueJourneys().firstOrNull()
    }

    override fun getJourneyById(id: Long): JourneyEntity? {
        return storage[id]
    }

    override fun getCompletedOrCancelledJourneys(): List<JourneyEntity> {
        return storage.values
            .filter { it.status == JourneyStatus.COMPLETED.name || it.status == JourneyStatus.CANCELLED.name }
            .sortedByDescending { it.startTimestamp }
    }

    override fun getJourneysForUser(userId: String): List<JourneyEntity> {
        return storage.values.filter { it.userId == userId }.sortedByDescending { it.startTimestamp }
    }

    override fun getActiveOrOverdueJourneysForUser(userId: String): List<JourneyEntity> {
        return storage.values
            .filter { it.userId == userId && (it.status == JourneyStatus.ACTIVE.name || it.status == JourneyStatus.OVERDUE.name) }
            .sortedByDescending { it.startTimestamp }
    }

    override fun getLatestActiveOrOverdueJourneyForUser(userId: String): JourneyEntity? {
        return getActiveOrOverdueJourneysForUser(userId).firstOrNull()
    }

    override fun getJourneyByIdForUser(id: Long, userId: String): JourneyEntity? {
        return storage[id]?.takeIf { it.userId == userId }
    }

    override fun getCompletedOrCancelledJourneysForUser(userId: String): List<JourneyEntity> {
        return storage.values
            .filter { it.userId == userId && (it.status == JourneyStatus.COMPLETED.name || it.status == JourneyStatus.CANCELLED.name) }
            .sortedByDescending { it.startTimestamp }
    }

    override fun getUnassignedJourneys(): List<JourneyEntity> {
        return storage.values.filter { it.userId == null }.sortedByDescending { it.startTimestamp }
    }

    override fun getUnassignedJourneyCount(): Int {
        return storage.values.count { it.userId == null }
    }

    override fun assignUnassignedJourneysToUser(userId: String): Int {
        var count = 0
        storage.entries.forEach { entry ->
            val entity = entry.value
            if (entity.userId == null) {
                entity.userId = userId
                storage[entry.key] = entity
                count++
            }
        }
        return count
    }

    override fun insert(journey: JourneyEntity): Long {
        val assignedId = if (journey.id > 0) journey.id else nextId++
        journey.id = assignedId
        storage[assignedId] = journey
        return assignedId
    }

    override fun update(journey: JourneyEntity): Int {
        return if (storage.containsKey(journey.id)) {
            storage[journey.id] = journey
            1
        } else {
            0
        }
    }

    override fun delete(journey: JourneyEntity): Int {
        return if (storage.remove(journey.id) != null) 1 else 0
    }

    override fun deleteById(id: Long): Int {
        return if (storage.remove(id) != null) 1 else 0
    }

    override fun deleteByIdForUser(id: Long, userId: String): Int {
        val existing = storage[id]
        return if (existing != null && existing.userId == userId) {
            storage.remove(id)
            1
        } else {
            0
        }
    }

    override fun getActiveMonitoredJourneys(): List<JourneyEntity> {
        return storage.values.filter { it.isMonitoringActive && (it.status == JourneyStatus.ACTIVE.name || it.status == JourneyStatus.OVERDUE.name) }.sortedByDescending { it.startTimestamp }
    }

    override fun getActiveMonitoredJourneysForUser(userId: String): List<JourneyEntity> {
        return storage.values.filter { it.userId == userId && it.isMonitoringActive && (it.status == JourneyStatus.ACTIVE.name || it.status == JourneyStatus.OVERDUE.name) }.sortedByDescending { it.startTimestamp }
    }

    override fun updateLastLocation(journeyId: Long, lat: Double?, lng: Double?, timestamp: Long): Int {
        val existing = storage[journeyId] ?: return 0
        existing.lastLatitude = lat
        existing.lastLongitude = lng
        existing.lastLocationTimestamp = timestamp
        existing.updatedAt = timestamp
        return 1
    }

    override fun setMonitoringActive(journeyId: Long, isActive: Boolean, updatedAt: Long): Int {
        val existing = storage[journeyId] ?: return 0
        existing.isMonitoringActive = isActive
        existing.updatedAt = updatedAt
        return 1
    }

    override fun markSafetyCheckAcknowledged(journeyId: Long, updatedAt: Long): Int {
        val existing = storage[journeyId] ?: return 0
        existing.safetyCheckAcknowledged = true
        existing.updatedAt = updatedAt
        return 1
    }

    override fun markOverdueEscalationAttempted(journeyId: Long, updatedAt: Long): Int {
        val existing = storage[journeyId] ?: return 0
        existing.overdueEscalationAttempted = true
        existing.updatedAt = updatedAt
        return 1
    }

    override fun recordSafetyCheckNotification(journeyId: Long, checkTime: Long, deadline: Long, updatedAt: Long): Int {
        val existing = storage[journeyId] ?: return 0
        existing.overdueSafetyCheckTimestamp = checkTime
        existing.overdueEscalationDeadline = deadline
        existing.updatedAt = updatedAt
        return 1
    }
}

class FakeJourneyEventDao : com.safeher.app.data.local.db.JourneyEventDao {
    private val storage = mutableListOf<com.safeher.app.data.local.db.JourneyEventEntity>()
    private var nextId = 1L

    override fun insert(event: com.safeher.app.data.local.db.JourneyEventEntity): Long {
        val assignedId = if (event.id > 0) event.id else nextId++
        val entity = com.safeher.app.data.local.db.JourneyEventEntity(
            assignedId,
            event.journeyId,
            event.eventType,
            event.timestamp,
            event.metadata
        )
        storage.add(entity)
        return assignedId
    }

    override fun getEventsForJourney(journeyId: Long): List<com.safeher.app.data.local.db.JourneyEventEntity> {
        return storage.filter { it.journeyId == journeyId }.sortedWith(compareBy({ it.timestamp }, { it.id }))
    }

    override fun getLatestEventForJourney(journeyId: Long): com.safeher.app.data.local.db.JourneyEventEntity? {
        return storage.filter { it.journeyId == journeyId }.maxByOrNull { it.timestamp }
    }

    override fun countOverdueEvents(journeyId: Long): Int {
        return storage.count { it.journeyId == journeyId && it.eventType == "OVERDUE_OBSERVED" }
    }

    override fun deleteEventsForJourney(journeyId: Long): Int {
        val initial = storage.size
        storage.removeAll { it.journeyId == journeyId }
        return initial - storage.size
    }
}

class JourneyRepositoryTest {

    private lateinit var dao: FakeJourneyDao
    private lateinit var eventDao: FakeJourneyEventDao
    private lateinit var repository: JourneyRepositoryImpl

    @Before
    fun setUp() {
        dao = FakeJourneyDao()
        eventDao = FakeJourneyEventDao()
        repository = JourneyRepositoryImpl(
            dao = dao,
            eventDao = eventDao,
            ioDispatcher = Dispatchers.Unconfined
        )
    }

    @Test
    fun startJourney_validData_returnsSuccessAndPersists() = runBlocking {
        val now = System.currentTimeMillis()
        val eta = now + 30 * 60 * 1000L

        val result = repository.startJourney(
            title = "Evening Commute",
            startLocation = "Office",
            destination = "Home",
            expectedArrivalTimestamp = eta,
            startLatitude = 12.9716,
            startLongitude = 77.5946,
            destinationLatitude = 12.9352,
            destinationLongitude = 77.6245
        )

        assertTrue(result is JourneyOperationResult.Success)
        val journeyId = (result as JourneyOperationResult.Success).journeyId

        val saved = repository.getJourneyById(journeyId)
        assertNotNull(saved)
        assertEquals("Home", saved!!.destination)
        assertEquals("Evening Commute", saved.title)
        assertEquals("Office", saved.startLocation)
        assertEquals(JourneyStatus.ACTIVE, saved.status)
        assertEquals(eta, saved.expectedArrivalTimestamp)
        assertEquals(12.9716, saved.startLatitude!!, 0.0001)
        assertEquals(77.5946, saved.startLongitude!!, 0.0001)
        assertEquals(12.9352, saved.destinationLatitude!!, 0.0001)
        assertEquals(77.6245, saved.destinationLongitude!!, 0.0001)
        assertEquals(0, saved.checkInCount)
        assertNull(saved.completedTimestamp)
    }

    @Test
    fun startJourney_blankDestination_returnsValidationError() = runBlocking {
        val now = System.currentTimeMillis()
        val eta = now + 30 * 60 * 1000L

        val result = repository.startJourney(
            title = null,
            startLocation = "Office",
            destination = "   ",
            expectedArrivalTimestamp = eta
        )

        assertTrue(result is JourneyOperationResult.ValidationError)
        val err = result as JourneyOperationResult.ValidationError
        assertEquals("destination", err.field)
    }

    @Test
    fun startJourney_pastArrivalTime_returnsValidationError() = runBlocking {
        val pastTime = System.currentTimeMillis() - 5000L

        val result = repository.startJourney(
            title = null,
            startLocation = "Office",
            destination = "Home",
            expectedArrivalTimestamp = pastTime
        )

        assertTrue(result is JourneyOperationResult.ValidationError)
        val err = result as JourneyOperationResult.ValidationError
        assertEquals("expectedArrivalTimestamp", err.field)
    }

    @Test
    fun startJourney_whenActiveJourneyAlreadyExists_preventsDuplicate() = runBlocking {
        val now = System.currentTimeMillis()
        val eta = now + 30 * 60 * 1000L

        val res1 = repository.startJourney(
            title = "First Trip",
            startLocation = "Point A",
            destination = "Point B",
            expectedArrivalTimestamp = eta
        )
        assertTrue(res1 is JourneyOperationResult.Success)

        // Attempt second simultaneous active journey
        val res2 = repository.startJourney(
            title = "Second Trip",
            startLocation = "Point C",
            destination = "Point D",
            expectedArrivalTimestamp = eta + 10000L
        )
        assertTrue(res2 is JourneyOperationResult.ActiveJourneyExists)
    }

    @Test
    fun checkIn_activeJourney_incrementsCountAndUpdatesTimestamp() = runBlocking {
        val now = System.currentTimeMillis()
        val eta = now + 30 * 60 * 1000L
        val res = repository.startJourney(null, "A", "B", eta) as JourneyOperationResult.Success

        val checkInRes = repository.checkIn(res.journeyId)
        assertTrue(checkInRes is JourneyOperationResult.Success)

        val journey = repository.getJourneyById(res.journeyId)
        assertNotNull(journey)
        assertEquals(1, journey!!.checkInCount)
        assertNotNull(journey.lastCheckInTimestamp)
        assertEquals(1, journey.checkInHistory.size)
    }

    @Test
    fun extendJourney_validFutureTime_updatesExpectedArrival() = runBlocking {
        val now = System.currentTimeMillis()
        val eta = now + 15 * 60 * 1000L
        val res = repository.startJourney(null, "A", "B", eta) as JourneyOperationResult.Success

        val newEta = eta + 20 * 60 * 1000L
        val extendRes = repository.extendJourney(res.journeyId, newEta)
        assertTrue(extendRes is JourneyOperationResult.Success)

        val updated = repository.getJourneyById(res.journeyId)
        assertNotNull(updated)
        assertEquals(newEta, updated!!.expectedArrivalTimestamp)
        assertEquals(JourneyStatus.ACTIVE, updated.status)
    }

    @Test
    fun extendJourney_overdueJourney_restoresStatusToActive() = runBlocking {
        val pastEta = System.currentTimeMillis() - 10000L
        // Insert manually as overdue
        val entity = JourneyEntity(
            1L,
            null,
            "Start",
            "Dest",
            null,
            null,
            null,
            null,
            pastEta - 30000L,
            pastEta,
            0L,
            JourneyStatus.OVERDUE.name,
            0L,
            0,
            null,
            pastEta - 30000L,
            pastEta
        )
        dao.insert(entity)

        val newFutureEta = System.currentTimeMillis() + 20 * 60 * 1000L
        val extendRes = repository.extendJourney(1L, newFutureEta)
        assertTrue(extendRes is JourneyOperationResult.Success)

        val updated = repository.getJourneyById(1L)
        assertNotNull(updated)
        assertEquals(JourneyStatus.ACTIVE, updated!!.status)
        assertEquals(newFutureEta, updated.expectedArrivalTimestamp)
    }

    @Test
    fun completeJourney_movesToTerminalStateAndRecordsTimestamp() = runBlocking {
        val now = System.currentTimeMillis()
        val eta = now + 30 * 60 * 1000L
        val res = repository.startJourney(null, "A", "B", eta) as JourneyOperationResult.Success

        val completeRes = repository.completeJourney(res.journeyId)
        assertTrue(completeRes is JourneyOperationResult.Success)

        val completed = repository.getJourneyById(res.journeyId)
        assertNotNull(completed)
        assertEquals(JourneyStatus.COMPLETED, completed!!.status)
        assertNotNull(completed.completedTimestamp)
        assertTrue(completed.isTerminal)

        // Cannot check-in or extend after completion
        val checkInRes = repository.checkIn(res.journeyId)
        assertTrue(checkInRes is JourneyOperationResult.TerminalStateError)

        val extendRes = repository.extendJourney(res.journeyId, now + 60 * 60 * 1000L)
        assertTrue(extendRes is JourneyOperationResult.TerminalStateError)
    }

    @Test
    fun cancelJourney_movesToTerminalState() = runBlocking {
        val now = System.currentTimeMillis()
        val eta = now + 30 * 60 * 1000L
        val res = repository.startJourney(null, "A", "B", eta) as JourneyOperationResult.Success

        val cancelRes = repository.cancelJourney(res.journeyId)
        assertTrue(cancelRes is JourneyOperationResult.Success)

        val cancelled = repository.getJourneyById(res.journeyId)
        assertNotNull(cancelled)
        assertEquals(JourneyStatus.CANCELLED, cancelled!!.status)
        assertTrue(cancelled.isTerminal)

        // Cannot cancel again or complete
        val secondCancel = repository.cancelJourney(res.journeyId)
        assertTrue(secondCancel is JourneyOperationResult.TerminalStateError)

        val completeRes = repository.completeJourney(res.journeyId)
        assertTrue(completeRes is JourneyOperationResult.TerminalStateError)
    }

    @Test
    fun getEffectiveStatus_derivesOverdueWhenExpectedArrivalPassed() {
        val now = System.currentTimeMillis()
        val journey = Journey(
            id = 1L,
            startLocation = "Origin",
            destination = "Target",
            startTimestamp = now - 60000L,
            expectedArrivalTimestamp = now - 1000L, // past
            status = JourneyStatus.ACTIVE,
            createdAt = now - 60000L,
            updatedAt = now - 60000L
        )

        assertEquals(JourneyStatus.OVERDUE, journey.getEffectiveStatus(now))
        assertTrue(journey.isOverdue(now))
        assertTrue(journey.overdueMillis(now) > 0)
    }

    @Test
    fun deleteJourney_removesRecordSuccessfully() = runBlocking {
        val now = System.currentTimeMillis()
        val eta = now + 30 * 60 * 1000L
        val res = repository.startJourney(null, "A", "B", eta) as JourneyOperationResult.Success

        val deleted = repository.deleteJourney(res.journeyId)
        assertTrue(deleted)
        assertNull(repository.getJourneyById(res.journeyId))
    }

    @Test
    fun startJourney_createsStartedEvent() = runBlocking {
        val now = System.currentTimeMillis()
        val eta = now + 30 * 60 * 1000L
        val res = repository.startJourney(null, "A", "B", eta) as JourneyOperationResult.Success

        val events = repository.getEventsForJourney(res.journeyId)
        assertEquals(1, events.size)
        assertEquals(com.safeher.app.data.journey.JourneyEventType.STARTED, events.first().eventType)
    }

    @Test
    fun checkIn_createsCheckInEvent_preservesOverdueStatus() = runBlocking {
        val now = System.currentTimeMillis()
        val eta = now + 30 * 60 * 1000L
        val res = repository.startJourney(null, "A", "B", eta) as JourneyOperationResult.Success

        val checkInRes = repository.checkIn(res.journeyId)
        assertTrue(checkInRes is JourneyOperationResult.Success)

        val events = repository.getEventsForJourney(res.journeyId)
        assertEquals(2, events.size)
        assertEquals(com.safeher.app.data.journey.JourneyEventType.CHECK_IN, events[1].eventType)
        assertEquals("Check-in #1", events[1].metadata)

        // Simulate journey becoming overdue
        val entity = dao.getJourneyById(res.journeyId)!!
        dao.update(
            JourneyEntity(
                entity.id, entity.title, entity.startLocation, entity.destination,
                entity.startLatitude, entity.startLongitude, entity.destinationLatitude, entity.destinationLongitude,
                entity.startTimestamp, now - 5000L, 0L, JourneyStatus.OVERDUE.name,
                entity.lastCheckInTimestamp, entity.checkInCount, entity.checkInHistory,
                entity.createdAt, now
            )
        )

        // Check in during overdue (simulate past debounce window)
        val updatedEntity = dao.getJourneyById(res.journeyId)!!
        dao.update(
            JourneyEntity(
                updatedEntity.id, updatedEntity.title, updatedEntity.startLocation, updatedEntity.destination,
                updatedEntity.startLatitude, updatedEntity.startLongitude, updatedEntity.destinationLatitude, updatedEntity.destinationLongitude,
                updatedEntity.startTimestamp, updatedEntity.expectedArrivalTimestamp, 0L, JourneyStatus.OVERDUE.name,
                now - 3000L, updatedEntity.checkInCount, updatedEntity.checkInHistory,
                updatedEntity.createdAt, now
            )
        )

        val overdueCheckIn = repository.checkIn(res.journeyId)
        assertTrue(overdueCheckIn is JourneyOperationResult.Success)

        // Status must remain OVERDUE! Check-in does NOT falsely clear overdue
        val overdueJourney = repository.getJourneyById(res.journeyId)
        assertEquals(JourneyStatus.OVERDUE, overdueJourney!!.status)
    }

    @Test
    fun checkIn_rapidDuplicateTap_isIdempotent() = runBlocking {
        val now = System.currentTimeMillis()
        val eta = now + 30 * 60 * 1000L
        val res = repository.startJourney(null, "A", "B", eta) as JourneyOperationResult.Success

        // First check in
        val first = repository.checkIn(res.journeyId)
        assertTrue(first is JourneyOperationResult.Success)

        // Immediate second tap (within <2000ms)
        val second = repository.checkIn(res.journeyId)
        assertTrue(second is JourneyOperationResult.Success)

        // Count must not have incremented twice
        val journey = repository.getJourneyById(res.journeyId)
        assertEquals(1, journey!!.checkInCount)
    }

    @Test
    fun extendJourney_fromOverdue_recoversActiveStatusAndCreatesEvent() = runBlocking {
        val now = System.currentTimeMillis()
        val eta = now + 30 * 60 * 1000L
        val res = repository.startJourney(null, "A", "B", eta) as JourneyOperationResult.Success

        // Make overdue
        val entity = dao.getJourneyById(res.journeyId)!!
        dao.update(
            JourneyEntity(
                entity.id, entity.title, entity.startLocation, entity.destination,
                entity.startLatitude, entity.startLongitude, entity.destinationLatitude, entity.destinationLongitude,
                entity.startTimestamp, entity.expectedArrivalTimestamp, 0L, JourneyStatus.OVERDUE.name,
                entity.lastCheckInTimestamp, entity.checkInCount, entity.checkInHistory,
                entity.createdAt, now
            )
        )

        val newEta = now + 45 * 60 * 1000L
        val extendRes = repository.extendJourney(res.journeyId, newEta)
        assertTrue(extendRes is JourneyOperationResult.Success)

        val recovered = repository.getJourneyById(res.journeyId)
        assertEquals(JourneyStatus.ACTIVE, recovered!!.status)

        val events = repository.getEventsForJourney(res.journeyId)
        assertTrue(events.any { it.eventType == com.safeher.app.data.journey.JourneyEventType.ETA_EXTENDED })
    }

    @Test
    fun reconcileOverdue_recordsExactlyOneOverdueEvent() = runBlocking {
        val now = System.currentTimeMillis()
        val eta = now - 10000L // past
        val entity = JourneyEntity(
            100L, "Commute", "Work", "Home",
            null, null, null, null,
            now - 30000L, eta, 0L, JourneyStatus.ACTIVE.name,
            0L, 0, null, now - 30000L, now - 30000L
        )
        dao.insert(entity)

        // 1st query: transitions to OVERDUE and inserts 1 OVERDUE_OBSERVED event
        val journeys1 = repository.getJourneys()
        assertEquals(JourneyStatus.OVERDUE, journeys1.first().status)
        assertEquals(1, eventDao.countOverdueEvents(100L))

        // 2nd query: already OVERDUE, must NOT insert a duplicate event!
        val journeys2 = repository.getJourneys()
        assertEquals(JourneyStatus.OVERDUE, journeys2.first().status)
        assertEquals(1, eventDao.countOverdueEvents(100L))

        // refreshJourneyStatus call: must also not insert duplicate
        repository.refreshJourneyStatus()
        assertEquals(1, eventDao.countOverdueEvents(100L))
    }

    @Test
    fun clockChange_clockRollback_reconcilesToActiveSafely() = runBlocking {
        val now = System.currentTimeMillis()
        val futureEta = now + 60000L
        val entity = JourneyEntity(
            200L, "Commute", "Work", "Home",
            null, null, null, null,
            now - 30000L, futureEta, 0L, JourneyStatus.OVERDUE.name,
            0L, 0, null, now - 30000L, now - 30000L
        )
        dao.insert(entity)

        val journey = repository.getJourneyById(200L)
        assertEquals(JourneyStatus.ACTIVE, journey!!.status)
    }

    @Test
    fun terminalJourneys_cannotBeModifiedOrReconciled() = runBlocking {
        val now = System.currentTimeMillis()
        val entity = JourneyEntity(
            300L, "Commute", "Work", "Home",
            null, null, null, null,
            now - 30000L, now - 1000L, now, JourneyStatus.COMPLETED.name,
            0L, 0, null, now - 30000L, now
        )
        dao.insert(entity)

        val res = repository.extendJourney(300L, now + 60000L)
        assertTrue(res is JourneyOperationResult.TerminalStateError)

        val checkInRes = repository.checkIn(300L)
        assertTrue(checkInRes is JourneyOperationResult.TerminalStateError)

        val fetched = repository.getJourneyById(300L)
        assertEquals(JourneyStatus.COMPLETED, fetched!!.status)
    }

    @Test
    fun deleteJourney_cleansUpAssociatedEvents() = runBlocking {
        val now = System.currentTimeMillis()
        val eta = now + 30 * 60 * 1000L
        val res = repository.startJourney(null, "A", "B", eta) as JourneyOperationResult.Success

        assertEquals(1, repository.getEventsForJourney(res.journeyId).size)

        repository.deleteJourney(res.journeyId)
        assertEquals(0, repository.getEventsForJourney(res.journeyId).size)
    }
}
