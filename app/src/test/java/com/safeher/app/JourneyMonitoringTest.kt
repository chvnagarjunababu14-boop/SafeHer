package com.safeher.app

import android.content.SharedPreferences
import com.safeher.app.data.guardian.Guardian
import com.safeher.app.data.journey.Journey
import com.safeher.app.data.journey.JourneyEventType
import com.safeher.app.data.journey.JourneyMonitoringScheduler
import com.safeher.app.data.journey.JourneyOperationResult
import com.safeher.app.data.journey.JourneyRepositoryImpl
import com.safeher.app.data.journey.JourneyStatus
import com.safeher.app.data.location.LocationData
import com.safeher.app.data.location.LocationFreshness
import com.safeher.app.data.session.UserSessionManager
import com.safeher.app.data.sos.DirectSmsSender
import com.safeher.app.data.sos.DirectSosManager
import com.safeher.app.data.sos.DirectSosState
import com.safeher.app.data.sos.EmergencyMessageBuilder
import com.safeher.app.data.sos.RecipientDispatchState
import com.safeher.app.data.sos.RecipientDispatchStatus
import com.safeher.app.data.sos.SosPreferences
import com.safeher.app.data.sos.SosPreferencesRepository
import com.safeher.app.data.sos.TelephonyCapability
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.lang.reflect.Proxy

/**
 * Phase 9 Unit Test Suite for Journey Background Monitoring & Automatic Overdue SOS.
 */
class JourneyMonitoringTest {

    private lateinit var journeyDao: FakeJourneyDao
    private lateinit var journeyEventDao: FakeJourneyEventDao
    private lateinit var sosEventDao: FakeSosEventDao
    private lateinit var guardianRepository: FakeTestGuardianRepository
    private lateinit var scheduler: FakeJourneyMonitoringScheduler
    private lateinit var sessionManager: UserSessionManager
    private lateinit var journeyRepository: JourneyRepositoryImpl
    private lateinit var preferencesRepository: FakeSosPreferencesRepository
    private lateinit var smsSender: FakeTestDirectSmsSender
    private lateinit var locationRepository: FakeLocationRepository
    private lateinit var directSosManager: DirectSosManager
    private lateinit var memoryPrefs: MutableMap<String, Any>
    private lateinit var prefsProxy: SharedPreferences

    private val testUserId = "user_phase9_test"
    private val otherUserId = "user_other_account"

    @Before
    fun setUp() {
        memoryPrefs = mutableMapOf()
        var editorProxy: SharedPreferences.Editor? = null
        editorProxy = Proxy.newProxyInstance(
            SharedPreferences.Editor::class.java.classLoader,
            arrayOf(SharedPreferences.Editor::class.java)
        ) { _, method, args ->
            when (method.name) {
                "putString" -> {
                    memoryPrefs[args[0] as String] = args[1] as String
                    editorProxy
                }
                "putBoolean" -> {
                    memoryPrefs[args[0] as String] = args[1] as Boolean
                    editorProxy
                }
                "putInt" -> {
                    memoryPrefs[args[0] as String] = args[1] as Int
                    editorProxy
                }
                "remove" -> {
                    memoryPrefs.remove(args[0] as String)
                    editorProxy
                }
                "apply", "commit" -> null
                else -> editorProxy
            }
        } as SharedPreferences.Editor

        prefsProxy = Proxy.newProxyInstance(
            SharedPreferences::class.java.classLoader,
            arrayOf(SharedPreferences::class.java)
        ) { _, method, args ->
            when (method.name) {
                "getString" -> memoryPrefs[args[0] as String] as? String ?: args[1] as? String
                "getBoolean" -> memoryPrefs[args[0] as String] as? Boolean ?: args[1] as Boolean
                "getInt" -> memoryPrefs[args[0] as String] as? Int ?: args[1] as Int
                "edit" -> editorProxy
                else -> null
            }
        } as SharedPreferences

        sessionManager = UserSessionManager.createForTesting(
            prefs = prefsProxy,
            dispatcher = Dispatchers.Unconfined
        )
        sessionManager.setActiveUserId(testUserId)

        journeyDao = FakeJourneyDao()
        journeyEventDao = FakeJourneyEventDao()
        sosEventDao = FakeSosEventDao()
        guardianRepository = FakeTestGuardianRepository()
        scheduler = FakeJourneyMonitoringScheduler()

        journeyRepository = JourneyRepositoryImpl(
            dao = journeyDao,
            eventDao = journeyEventDao,
            database = null,
            sessionManager = sessionManager,
            scheduler = scheduler,
            ioDispatcher = Dispatchers.Unconfined
        )

        preferencesRepository = FakeSosPreferencesRepository()
        smsSender = FakeTestDirectSmsSender()
        locationRepository = FakeLocationRepository()

        directSosManager = DirectSosManager(
            guardianRepository = guardianRepository,
            sosPreferencesRepository = preferencesRepository,
            directSmsSender = smsSender,
            locationRepository = locationRepository,
            sosEventDao = sosEventDao,
            sessionManager = sessionManager,
            ioDispatcher = Dispatchers.Unconfined
        )
    }

    @Test
    fun journeyStartsAndMonitoringBecomesActive() = runBlocking {
        val arrivalTime = System.currentTimeMillis() + 30 * 60 * 1000L
        val result = journeyRepository.startJourney(
            title = "Office Commute",
            startLocation = "Home",
            destination = "Office HQ",
            expectedArrivalTimestamp = arrivalTime,
            enableMonitoring = true
        )

        assertTrue(result is JourneyOperationResult.Success)
        val journeyId = (result as JourneyOperationResult.Success).journeyId

        val journey = journeyRepository.getJourneyById(journeyId)
        assertNotNull(journey)
        assertTrue("Monitoring should be active", journey!!.isMonitoringActive)
        assertEquals("Office HQ", journey.destination)

        // Verify scheduler registered alarms
        assertTrue("Alarms should be scheduled", scheduler.scheduledAlarms.containsKey(journeyId))
        assertEquals(1, scheduler.scheduledAlarms.size)
    }

    @Test
    fun estimatedArrivalCreatesOnlyOneOverdueEvent() = runBlocking {
        val now = System.currentTimeMillis()
        val futureArrival = now + 60_000L
        val result = journeyRepository.startJourney(
            title = "Walk Home",
            startLocation = "Station",
            destination = "Home",
            expectedArrivalTimestamp = futureArrival,
            enableMonitoring = true
        )
        val journeyId = (result as JourneyOperationResult.Success).journeyId

        // Simulate elapsed ETA by directly updating the entity in DAO
        val entity = journeyDao.getJourneyById(journeyId)!!
        entity.expectedArrivalTimestamp = now - 5000L
        journeyDao.update(entity)

        // Initial refresh triggers overdue check
        val updated = journeyRepository.refreshJourneyStatus()
        assertNotNull(updated)
        assertEquals(JourneyStatus.OVERDUE, updated!!.status)

        val events1 = journeyRepository.getEventsForJourney(journeyId)
        val overdueEvents1 = events1.filter { it.eventType == JourneyEventType.OVERDUE_OBSERVED }
        assertEquals("Should create exactly one overdue event", 1, overdueEvents1.size)

        // Subsequent refresh should NOT duplicate the overdue event
        val secondRefresh = journeyRepository.refreshJourneyStatus()
        assertNotNull(secondRefresh)
        val events2 = journeyRepository.getEventsForJourney(journeyId)
        val overdueEvents2 = events2.filter { it.eventType == JourneyEventType.OVERDUE_OBSERVED }
        assertEquals("Should remain exactly one overdue event after repeated refresh", 1, overdueEvents2.size)
    }

    @Test
    fun imSafeCancelsEscalationAndRecordsEvent() = runBlocking {
        val now = System.currentTimeMillis()
        val futureArrival = now + 60_000L
        val result = journeyRepository.startJourney(
            title = null,
            destination = "Library",
            startLocation = "Campus",
            expectedArrivalTimestamp = futureArrival,
            enableMonitoring = true
        )
        val journeyId = (result as JourneyOperationResult.Success).journeyId

        // Simulate elapsed ETA and overdue status
        val entity = journeyDao.getJourneyById(journeyId)!!
        entity.expectedArrivalTimestamp = now - 60000L
        entity.status = JourneyStatus.OVERDUE.name
        journeyDao.update(entity)

        // Acknowledge safety check
        val ackResult = journeyRepository.acknowledgeSafetyCheck(journeyId)
        assertTrue(ackResult is JourneyOperationResult.Success)

        val journey = journeyRepository.getJourneyById(journeyId)
        assertNotNull(journey)
        assertTrue("Safety check must be acknowledged", journey!!.safetyCheckAcknowledged)

        // Escalation alarm should be cancelled
        assertTrue("Escalation alarm must be cancelled", scheduler.cancelledEscalationAlarms.contains(journeyId))

        // Safety acknowledged event should be persisted
        val events = journeyRepository.getEventsForJourney(journeyId)
        assertTrue("Event timeline must include SAFETY_ACKNOWLEDGED",
            events.any { it.eventType == JourneyEventType.SAFETY_ACKNOWLEDGED })
    }

    @Test
    fun needHelpInitiatesSosImmediately() = runBlocking {
        guardianRepository.guardians.add(
            Guardian(id = 1L, name = "Primary Guardian", phoneNumber = "+919876543210", isSosEnabled = true)
        )
        preferencesRepository.setConsentGiven(true)
        preferencesRepository.setDirectSmsEnabled(true)

        // Trigger immediate direct SOS (simulating user tapping "Need Help")
        directSosManager.triggerImmediateSos()

        val state = directSosManager.state.value
        assertTrue("Should transition to Completed or Dispatching",
            state is DirectSosState.Completed || state is DirectSosState.Dispatching)
        assertEquals(1, smsSender.sentMessages.size)
        assertEquals("+919876543210", smsSender.sentMessages[0].recipientPhone)
    }

    @Test
    fun fiveMinuteDeadlineTriggersOneEscalationAttempt() = runBlocking {
        guardianRepository.guardians.add(
            Guardian(id = 10L, name = "Mom", phoneNumber = "+919876543210", isSosEnabled = true)
        )
        preferencesRepository.setConsentGiven(true)
        preferencesRepository.setDirectSmsEnabled(true)
        preferencesRepository.setOverdueEscalationConsent(true)
        preferencesRepository.setOverdueEscalationEnabled(true)

        val now = System.currentTimeMillis()
        val journey = Journey(
            id = 55L,
            destination = "Metro Station",
            startLocation = "Office",
            startTimestamp = now - 35 * 60 * 1000L,
            expectedArrivalTimestamp = now - 6 * 60 * 1000L,
            status = JourneyStatus.OVERDUE,
            isMonitoringActive = true,
            safetyCheckAcknowledged = false,
            overdueEscalationAttempted = false
        )

        val dispatchResult = directSosManager.dispatchOverdueSos(journey)
        assertTrue("Dispatch should complete successfully", dispatchResult is DirectSosState.Completed)

        // Verified SMS sent
        assertEquals(1, smsSender.sentMessages.size)
        assertTrue("Message body should mention overdue journey",
            smsSender.sentMessages[0].message.contains("Metro Station") || smsSender.sentMessages[0].message.contains("SafeHer Overdue"))

        // Verified SOS event saved with journeyId link
        val savedSosEvents = sosEventDao.getEventsForJourney(55L)
        assertEquals(1, savedSosEvents.size)
        assertEquals(55L, savedSosEvents[0].journeyId)
    }

    @Test
    fun duplicateWorkersAndCallbacksDoNotDispatchDuplicateSos() = runBlocking {
        guardianRepository.guardians.add(
            Guardian(id = 10L, name = "Sister", phoneNumber = "+919876543210", isSosEnabled = true)
        )
        preferencesRepository.setConsentGiven(true)
        preferencesRepository.setDirectSmsEnabled(true)
        preferencesRepository.setOverdueEscalationConsent(true)
        preferencesRepository.setOverdueEscalationEnabled(true)

        val now = System.currentTimeMillis()
        val journey = Journey(
            id = 77L,
            destination = "Friend's House",
            startLocation = "Library",
            startTimestamp = now - 40 * 60 * 1000L,
            expectedArrivalTimestamp = now - 10 * 60 * 1000L,
            status = JourneyStatus.OVERDUE,
            isMonitoringActive = true,
            safetyCheckAcknowledged = false,
            overdueEscalationAttempted = false
        )

        // First dispatch
        val firstResult = directSosManager.dispatchOverdueSos(journey)
        assertTrue(firstResult is DirectSosState.Completed)
        assertEquals(1, smsSender.sentMessages.size)

        // Attempt second dispatch with the journey having overdueEscalationAttempted = true
        val duplicateJourney = journey.copy(overdueEscalationAttempted = true)
        val secondResult = directSosManager.dispatchOverdueSos(duplicateJourney)

        // Must reject or return error without sending second SMS
        assertTrue("Second dispatch must be refused", secondResult is DirectSosState.Error)
        assertEquals("Still only 1 SMS sent", 1, smsSender.sentMessages.size)
    }

    @Test
    fun journeyCompletionOrCancellationCancelsPendingEscalation() = runBlocking {
        val arrivalTime = System.currentTimeMillis() + 45 * 60 * 1000L
        val result = journeyRepository.startJourney(
            title = null,
            destination = "Gym",
            startLocation = "Home",
            expectedArrivalTimestamp = arrivalTime,
            enableMonitoring = true
        )
        val journeyId = (result as JourneyOperationResult.Success).journeyId

        // Complete the journey
        val completeResult = journeyRepository.completeJourney(journeyId)
        assertTrue(completeResult is JourneyOperationResult.Success)

        // All alarms must be cancelled
        assertTrue(scheduler.cancelledAllAlarms.contains(journeyId))

        // Attempting overdue SOS on a completed journey must abort
        val completedJourney = journeyRepository.getJourneyById(journeyId)!!
        val dispatchResult = directSosManager.dispatchOverdueSos(completedJourney)
        assertTrue(dispatchResult is DirectSosState.Error)
        assertEquals("No SMS should be sent for completed journey", 0, smsSender.sentMessages.size)
    }

    @Test
    fun missingConsentPreventsAutomaticSosDispatch() = runBlocking {
        guardianRepository.guardians.add(
            Guardian(id = 20L, name = "Guardian", phoneNumber = "+919876543210", isSosEnabled = true)
        )
        // Escalation consent is false
        preferencesRepository.setOverdueEscalationConsent(false)
        preferencesRepository.setOverdueEscalationEnabled(false)

        val now = System.currentTimeMillis()
        val journey = Journey(
            id = 99L,
            destination = "Cafe",
            startLocation = "Work",
            startTimestamp = now - 60 * 60 * 1000L,
            expectedArrivalTimestamp = now - 10 * 60 * 1000L,
            status = JourneyStatus.OVERDUE,
            isMonitoringActive = true
        )

        val result = directSosManager.dispatchOverdueSos(journey)
        assertTrue("Must fail with Error due to missing consent", result is DirectSosState.Error)
        assertEquals(0, smsSender.sentMessages.size)
    }

    @Test
    fun staleOrMissingGpsDoesNotFabricateArrivalOrCoordinates() = runBlocking {
        val now = System.currentTimeMillis()
        // Location from 10 minutes ago (> 3 mins -> stale)
        val staleTimestamp = now - 10 * 60 * 1000L
        val journey = Journey(
            id = 101L,
            destination = "Downtown",
            startLocation = "Home",
            startTimestamp = now - 60 * 1000L,
            expectedArrivalTimestamp = now + 60 * 1000L,
            lastLatitude = 12.9716,
            lastLongitude = 77.5946,
            lastLocationTimestamp = staleTimestamp
        )

        assertFalse("Stale location must not be considered fresh", journey.isLocationFresh)

        // Overdue message without fresh location
        val msg = EmergencyMessageBuilder.buildOverdueSosMessage(
            destination = journey.destination,
            expectedArrivalTimestamp = journey.expectedArrivalTimestamp,
            overdueMinutes = 5L,
            location = null,
            includeLocation = false
        )
        assertTrue(msg.contains("Downtown"))
        assertFalse(msg.contains("maps.google.com"))
    }

    @Test
    fun accountIsolationPreventsCrossAccountGuardianAlert() = runBlocking {
        // User A's guardian
        guardianRepository.guardians.add(
            Guardian(id = 1L, name = "User A Guardian", phoneNumber = "+911111111111", isSosEnabled = true)
        )
        preferencesRepository.setConsentGiven(true)
        preferencesRepository.setDirectSmsEnabled(true)
        preferencesRepository.setOverdueEscalationConsent(true)
        preferencesRepository.setOverdueEscalationEnabled(true)

        // Switch to other user
        val otherSession = UserSessionManager.createForTesting(
            prefs = prefsProxy,
            dispatcher = Dispatchers.Unconfined
        )
        otherSession.setActiveUserId(otherUserId)

        val otherGuardianRepo = FakeTestGuardianRepository()
        otherGuardianRepo.guardians.add(
            Guardian(id = 2L, name = "User B Guardian", phoneNumber = "+912222222222", isSosEnabled = true)
        )

        val otherDirectSosManager = DirectSosManager(
            guardianRepository = otherGuardianRepo,
            sosPreferencesRepository = preferencesRepository,
            directSmsSender = smsSender,
            locationRepository = locationRepository,
            sosEventDao = sosEventDao,
            sessionManager = otherSession,
            ioDispatcher = Dispatchers.Unconfined
        )

        val now = System.currentTimeMillis()
        val otherJourney = Journey(
            id = 202L,
            destination = "Other Place",
            startLocation = "Other Start",
            startTimestamp = now - 30 * 60 * 1000L,
            expectedArrivalTimestamp = now - 10 * 60 * 1000L,
            status = JourneyStatus.OVERDUE,
            isMonitoringActive = true
        )

        val result = otherDirectSosManager.dispatchOverdueSos(otherJourney)
        assertTrue(result is DirectSosState.Completed)
        // User B's guardian must receive the SMS, User A's guardian must NOT
        assertEquals(1, smsSender.sentMessages.size)
        assertEquals("+912222222222", smsSender.sentMessages[0].recipientPhone)
    }

    @Test
    fun appRestartRecoversActiveMonitoredJourneys() = runBlocking {
        val now = System.currentTimeMillis()
        val journey = Journey(
            id = 303L,
            destination = "Airport",
            startLocation = "Home",
            startTimestamp = now - 15 * 60 * 1000L,
            expectedArrivalTimestamp = now + 45 * 60 * 1000L,
            status = JourneyStatus.ACTIVE,
            isMonitoringActive = true
        )
        journeyDao.insert(
            com.safeher.app.data.local.db.JourneyEntity(
                journey.id,
                testUserId,
                journey.title,
                journey.startLocation,
                journey.destination,
                journey.startLatitude,
                journey.startLongitude,
                journey.destinationLatitude,
                journey.destinationLongitude,
                journey.startTimestamp,
                journey.expectedArrivalTimestamp,
                journey.completedTimestamp ?: 0L,
                journey.status.name,
                journey.lastCheckInTimestamp ?: 0L,
                journey.checkInCount,
                "",
                journey.createdAt,
                journey.updatedAt,
                journey.isMonitoringActive,
                journey.lastLatitude,
                journey.lastLongitude,
                journey.lastLocationTimestamp ?: 0L,
                journey.overdueSafetyCheckTimestamp ?: 0L,
                journey.safetyCheckAcknowledged,
                journey.overdueEscalationDeadline ?: 0L,
                journey.overdueEscalationAttempted
            )
        )

        // Query active monitored journeys (what BootCompletedReceiver does)
        val activeMonitored = journeyRepository.getActiveMonitoredJourneys()
        assertEquals(1, activeMonitored.size)
        assertEquals(303L, activeMonitored[0].id)
        assertTrue(activeMonitored[0].isMonitoringActive)
    }

    @Test
    fun missingSmsHardwareOrCapabilityProducesRecordedFailure() = runBlocking {
        guardianRepository.guardians.add(
            Guardian(id = 5L, name = "Dad", phoneNumber = "+919876543210", isSosEnabled = true)
        )
        preferencesRepository.setConsentGiven(true)
        preferencesRepository.setDirectSmsEnabled(true)
        preferencesRepository.setOverdueEscalationConsent(true)
        preferencesRepository.setOverdueEscalationEnabled(true)

        // Make sender report hardware failure
        smsSender.simulatedCapability = TelephonyCapability.Unavailable("No SIM card detected")

        val now = System.currentTimeMillis()
        val journey = Journey(
            id = 404L,
            destination = "Mall",
            startLocation = "Home",
            startTimestamp = now - 30 * 60 * 1000L,
            expectedArrivalTimestamp = now - 6 * 60 * 1000L,
            status = JourneyStatus.OVERDUE,
            isMonitoringActive = true
        )

        val result = directSosManager.dispatchOverdueSos(journey)
        assertTrue("Must return error when telephony hardware is absent", result is DirectSosState.Error)
        assertEquals(0, smsSender.sentMessages.size)
    }
}

/**
 * Test doubles for Journey Monitoring unit tests.
 */
class FakeJourneyMonitoringScheduler : JourneyMonitoringScheduler(null) {
    val scheduledAlarms = mutableMapOf<Long, Pair<Long, Long>>()
    val cancelledEscalationAlarms = mutableSetOf<Long>()
    val cancelledAllAlarms = mutableSetOf<Long>()

    override fun scheduleJourneyAlarms(
        journeyId: Long,
        expectedArrivalTimestamp: Long,
        escalationDeadline: Long
    ) {
        scheduledAlarms[journeyId] = Pair(expectedArrivalTimestamp, escalationDeadline)
    }

    override fun cancelEscalationAlarm(journeyId: Long) {
        cancelledEscalationAlarms.add(journeyId)
    }

    override fun cancelAllJourneyAlarms(journeyId: Long) {
        cancelledAllAlarms.add(journeyId)
        scheduledAlarms.remove(journeyId)
    }
}

class FakeTestDirectSmsSender : DirectSmsSender {
    data class SentRecord(val recipientPhone: String, val message: String)
    val sentMessages = mutableListOf<SentRecord>()
    var simulatedCapability: TelephonyCapability = TelephonyCapability.Available
    var hasPermission: Boolean = true

    override fun checkTelephonyCapability(): TelephonyCapability = simulatedCapability
    override fun hasSmsPermission(): Boolean = hasPermission

    override suspend fun sendEmergencySms(
        eventId: Long,
        guardianId: Long,
        guardianName: String,
        phoneNumber: String,
        message: String,
        onStatusUpdate: ((RecipientDispatchStatus) -> Unit)?
    ): RecipientDispatchStatus {
        sentMessages.add(SentRecord(phoneNumber, message))
        val status = RecipientDispatchStatus(
            guardianId = guardianId,
            guardianName = guardianName,
            phoneNumber = phoneNumber,
            state = RecipientDispatchState.SUBMITTED_TO_RADIO,
            statusMessage = "Sent via test simulator"
        )
        onStatusUpdate?.invoke(status)
        return status
    }
}

class FakeSosPreferencesRepository : SosPreferencesRepository {
    private var prefs = SosPreferences(
        isDirectSmsConsentGiven = true,
        isDirectSmsEnabled = true,
        isOverdueEscalationConsentGiven = true,
        isOverdueEscalationEnabled = true
    )
    private val flow = MutableStateFlow(prefs)

    override val preferencesFlow: StateFlow<SosPreferences> = flow.asStateFlow()
    override fun getPreferences(): SosPreferences = prefs

    override fun setConsentGiven(consentGiven: Boolean) {
        prefs = prefs.copy(isDirectSmsConsentGiven = consentGiven)
        flow.value = prefs
    }

    override fun setDirectSmsEnabled(enabled: Boolean) {
        prefs = prefs.copy(isDirectSmsEnabled = enabled)
        flow.value = prefs
    }

    override fun setCustomSosMessage(message: String) {
        prefs = prefs.copy(customSosMessage = message)
        flow.value = prefs
    }

    override fun setLocationSharingEnabled(enabled: Boolean) {
        prefs = prefs.copy(isLocationSharingEnabled = enabled)
        flow.value = prefs
    }

    override fun setCountdownDurationSeconds(seconds: Int) {
        prefs = prefs.copy(countdownDurationSeconds = seconds)
        flow.value = prefs
    }

    override fun setOverdueEscalationConsent(consentGiven: Boolean) {
        prefs = prefs.copy(isOverdueEscalationConsentGiven = consentGiven)
        flow.value = prefs
    }

    override fun setOverdueEscalationEnabled(enabled: Boolean) {
        prefs = prefs.copy(isOverdueEscalationEnabled = enabled)
        flow.value = prefs
    }

    override fun setBatteryAlertsConsent(consentGiven: Boolean) {
        prefs = prefs.copy(
            isBatteryAlertsConsentGiven = consentGiven,
            isBatteryAlertsEnabled = if (consentGiven) prefs.isBatteryAlertsEnabled else false
        )
        flow.value = prefs
    }

    override fun setBatteryAlertsEnabled(enabled: Boolean) {
        prefs = prefs.copy(isBatteryAlertsEnabled = enabled && prefs.isBatteryAlertsConsentGiven)
        flow.value = prefs
    }

    override fun setBatteryAlertLocationEnabled(enabled: Boolean) {
        prefs = prefs.copy(isBatteryAlertLocationEnabled = enabled)
        flow.value = prefs
    }

    override fun setBatteryLowThresholdPercent(percent: Int) {
        prefs = prefs.copy(batteryLowThresholdPercent = percent)
        flow.value = prefs
    }

    override fun setBatteryCriticalThresholdPercent(percent: Int) {
        prefs = prefs.copy(batteryCriticalThresholdPercent = percent)
        flow.value = prefs
    }
}
