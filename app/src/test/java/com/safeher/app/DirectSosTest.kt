package com.safeher.app

import android.content.SharedPreferences
import com.safeher.app.data.guardian.Guardian
import com.safeher.app.data.guardian.GuardianRepository
import com.safeher.app.data.location.LocationData
import com.safeher.app.data.location.LocationFreshness
import com.safeher.app.data.location.LocationRepository
import com.safeher.app.data.location.LocationResult
import com.safeher.app.data.local.db.SosEventDao
import com.safeher.app.data.local.db.SosEventEntity
import com.safeher.app.data.session.UserSessionManager
import com.safeher.app.data.sos.DemoDirectSmsSender
import com.safeher.app.data.sos.DirectSosManager
import com.safeher.app.data.sos.DirectSosState
import com.safeher.app.data.sos.RecipientDispatchState
import com.safeher.app.data.sos.SosPreferences
import com.safeher.app.data.sos.SosPreferencesRepository
import com.safeher.app.data.sos.SosPreferencesRepositoryImpl
import com.safeher.app.data.sos.TelephonyCapability
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.lang.reflect.Proxy

class FakeSosEventDao : SosEventDao {
    val events = mutableListOf<SosEventEntity>()
    private var idCounter = 1L

    override fun insert(event: SosEventEntity): Long {
        event.id = idCounter++
        events.add(event)
        return event.id
    }

    override fun update(event: SosEventEntity): Int {
        val idx = events.indexOfFirst { it.id == event.id }
        if (idx != -1) {
            events[idx] = event
            return 1
        }
        return 0
    }

    override fun getEventsForUser(userId: String): List<SosEventEntity> =
        events.filter { it.userId == userId }

    override fun getRecentEventsForUser(userId: String, limit: Int): List<SosEventEntity> =
        events.filter { it.userId == userId }.take(limit)

    override fun getEventByIdForUser(id: Long, userId: String): SosEventEntity? =
        events.find { it.id == id && it.userId == userId }

    override fun getEventCountForUser(userId: String): Int =
        events.count { it.userId == userId }

    override fun deleteAllForUser(userId: String): Int {
        val count = events.count { it.userId == userId }
        events.removeIf { it.userId == userId }
        return count
    }

    override fun getEventsForJourney(journeyId: Long): List<SosEventEntity> =
        events.filter { it.journeyId == journeyId }
}

class FakeTestGuardianRepository : GuardianRepository {
    val guardians = mutableListOf<Guardian>()

    override fun getGuardiansFlow(): Flow<List<Guardian>> = flowOf(guardians.toList())
    override fun getGuardianCountFlow(): Flow<Int> = flowOf(guardians.size)
    override suspend fun getGuardians(): List<Guardian> = guardians.toList()
    override suspend fun getGuardianCount(): Int = guardians.size

    override suspend fun addGuardian(
        name: String,
        phoneNumber: String,
        relationship: String?
    ) = com.safeher.app.data.guardian.GuardianOperationResult.Success(1L)

    override suspend fun updateGuardian(
        id: Long,
        name: String,
        phoneNumber: String,
        relationship: String?
    ) = com.safeher.app.data.guardian.GuardianOperationResult.Success(id)

    override suspend fun deleteGuardian(id: Long): Boolean = guardians.removeIf { it.id == id }

    override suspend fun getGuardianById(id: Long): Guardian? = guardians.find { it.id == id }

    override suspend fun updateSosEnabled(id: Long, isSosEnabled: Boolean): Boolean {
        val idx = guardians.indexOfFirst { it.id == id }
        if (idx != -1) {
            guardians[idx] = guardians[idx].copy(isSosEnabled = isSosEnabled)
            return true
        }
        return false
    }

    override suspend fun getSosEnabledGuardians(): List<Guardian> =
        guardians.filter { it.isSosEnabled }

    override fun getSosEnabledGuardiansFlow(): Flow<List<Guardian>> =
        flowOf(guardians.filter { it.isSosEnabled })

    override suspend fun updateBatteryAlertEnabled(id: Long, isBatteryAlertEnabled: Boolean): Boolean {
        val idx = guardians.indexOfFirst { it.id == id }
        if (idx != -1) {
            guardians[idx] = guardians[idx].copy(isBatteryAlertEnabled = isBatteryAlertEnabled)
            return true
        }
        return false
    }

    override suspend fun getBatteryAlertEnabledGuardians(): List<Guardian> =
        guardians.filter { it.isBatteryAlertEnabled }
}

class DirectSosTest {

    private lateinit var memoryPrefs: MutableMap<String, Any>
    private lateinit var prefsProxy: SharedPreferences
    private lateinit var sessionManager: UserSessionManager
    private lateinit var guardianRepo: FakeTestGuardianRepository
    private lateinit var locationRepo: FakeLocationRepository
    private lateinit var eventDao: FakeSosEventDao
    private lateinit var sosPrefsRepo: SosPreferencesRepository
    private lateinit var sender: DemoDirectSmsSender
    private lateinit var manager: DirectSosManager

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
        sessionManager.setActiveUserId("user_alice")

        guardianRepo = FakeTestGuardianRepository()
        locationRepo = FakeLocationRepository()
        eventDao = FakeSosEventDao()
        sosPrefsRepo = SosPreferencesRepositoryImpl(prefsProxy, sessionManager)
        sender = DemoDirectSmsSender(stepDelayMs = 0L) // Instant in tests

        manager = DirectSosManager(
            guardianRepository = guardianRepo,
            sosPreferencesRepository = sosPrefsRepo,
            directSmsSender = sender,
            locationRepository = locationRepo,
            sosEventDao = eventDao,
            sessionManager = sessionManager,
            ioDispatcher = Dispatchers.Unconfined
        )

        // Setup default authorized state
        sosPrefsRepo.setConsentGiven(true)
        sosPrefsRepo.setDirectSmsEnabled(true)
    }

    @Test
    fun directSos_successfulSubmission_allRecipientsDelivered() = runBlocking {
        // Arrange 2 enabled guardians
        guardianRepo.guardians.add(
            Guardian(1L, "Mom", "+919876543210", "Mother", true, 0L, 0L, 0L, isSosEnabled = true)
        )
        guardianRepo.guardians.add(
            Guardian(2L, "Sister", "+919123456780", "Sister", true, 0L, 0L, 0L, isSosEnabled = true)
        )

        manager.triggerImmediateSos()

        val state = manager.state.value
        assertTrue("State must be Completed, was: $state", state is DirectSosState.Completed)
        val completed = state as DirectSosState.Completed

        assertEquals(2, completed.statuses.size)
        assertEquals(0, completed.failedCount)
        assertEquals(2, completed.deliveryConfirmedCount)
        assertTrue("Location should be attached", completed.locationAttached)

        // Verify Room persistence
        assertEquals(1, eventDao.events.size)
        val event = eventDao.events[0]
        assertEquals("user_alice", event.userId)
        assertEquals(2, event.recipientsCount)
        assertEquals(2, event.successCount)
        assertEquals(0, event.failureCount)
        assertTrue(event.message.contains("Location: https://maps.google.com/?q="))
    }

    @Test
    fun directSos_partialRecipientFailure_continuesIndependently() = runBlocking {
        // Guardian 1 will fail, Guardian 2 will succeed
        guardianRepo.guardians.add(
            Guardian(10L, "Failing Guardian", "+919000000001", "Friend", true, 0L, 0L, 0L, isSosEnabled = true)
        )
        guardianRepo.guardians.add(
            Guardian(20L, "Working Guardian", "+919000000002", "Friend", true, 0L, 0L, 0L, isSosEnabled = true)
        )
        sender.simulatedFailureGuardianIds = setOf(10L)

        manager.triggerImmediateSos()

        val state = manager.state.value
        assertTrue(state is DirectSosState.Completed)
        val completed = state as DirectSosState.Completed

        assertEquals(1, completed.failedCount)
        assertEquals(1, completed.deliveryConfirmedCount)
        assertEquals(RecipientDispatchState.FAILED, completed.statuses.first { it.guardianId == 10L }.state)
        assertEquals(RecipientDispatchState.DELIVERY_CONFIRMED, completed.statuses.first { it.guardianId == 20L }.state)

        // Room entity recorded
        assertEquals(1, eventDao.events.size)
        val event = eventDao.events[0]
        assertEquals(1, event.successCount)
        assertEquals(1, event.failureCount)
    }

    @Test
    fun directSos_permissionDenied_reportsClearError() = runBlocking {
        sender.simulatedPermissionGranted = false
        guardianRepo.guardians.add(
            Guardian(1L, "Mom", "+919876543210", "Mother", true, 0L, 0L, 0L, isSosEnabled = true)
        )

        manager.triggerImmediateSos()

        val state = manager.state.value
        assertTrue(state is DirectSosState.Error)
        val error = state as DirectSosState.Error
        assertTrue(error.reason.contains("SEND_SMS"))
        assertTrue("Must offer manual fallback", error.canFallbackToManual)
    }

    @Test
    fun directSos_unavailableTelephony_reportsClearError() = runBlocking {
        sender.simulatedCapability = TelephonyCapability.Unavailable("Airplane mode is active")
        guardianRepo.guardians.add(
            Guardian(1L, "Mom", "+919876543210", "Mother", true, 0L, 0L, 0L, isSosEnabled = true)
        )

        manager.triggerImmediateSos()

        val state = manager.state.value
        assertTrue(state is DirectSosState.Error)
        val error = state as DirectSosState.Error
        assertTrue(error.reason.contains("Airplane mode"))
        assertTrue(error.canFallbackToManual)
    }

    @Test
    fun directSos_missingSim_reportsClearError() = runBlocking {
        sender.simulatedCapability = TelephonyCapability.Unavailable("SIM card is not ready or missing")
        guardianRepo.guardians.add(
            Guardian(1L, "Mom", "+919876543210", "Mother", true, 0L, 0L, 0L, isSosEnabled = true)
        )

        manager.triggerImmediateSos()

        val state = manager.state.value
        assertTrue(state is DirectSosState.Error)
        val error = state as DirectSosState.Error
        assertTrue(error.reason.contains("SIM card"))
    }

    @Test
    fun directSos_noEligibleGuardians_reportsClearError() = runBlocking {
        // No guardians in repo
        manager.triggerImmediateSos()

        val state = manager.state.value
        assertTrue(state is DirectSosState.Error)
        val error = state as DirectSosState.Error
        assertTrue(error.reason.contains("No guardians"))
        assertFalse("Cannot fallback to manual without recipients", error.canFallbackToManual)
    }

    @Test
    fun directSos_guardianDisabledForSos_isExcludedFromDispatch() = runBlocking {
        // One enabled, one disabled
        guardianRepo.guardians.add(
            Guardian(1L, "Active SOS", "+919876543210", "Friend", true, 0L, 0L, 0L, isSosEnabled = true)
        )
        guardianRepo.guardians.add(
            Guardian(2L, "Disabled SOS", "+919876543211", "Friend", true, 0L, 0L, 0L, isSosEnabled = false)
        )

        manager.triggerImmediateSos()

        val state = manager.state.value
        assertTrue(state is DirectSosState.Completed)
        val completed = state as DirectSosState.Completed
        assertEquals(1, completed.statuses.size)
        assertEquals(1L, completed.statuses[0].guardianId)
    }

    @Test
    fun directSos_missingLocation_sendsWithoutCoordinates() = runBlocking {
        locationRepo.resultToReturn = LocationResult.LocationDisabled
        guardianRepo.guardians.add(
            Guardian(1L, "Mom", "+919876543210", "Mother", true, 0L, 0L, 0L, isSosEnabled = true)
        )

        manager.triggerImmediateSos()

        val state = manager.state.value
        assertTrue(state is DirectSosState.Completed)
        val completed = state as DirectSosState.Completed
        assertFalse(completed.locationAttached)
        assertNotNull(completed.locationNote)

        val event = eventDao.events[0]
        assertNull(event.latitude)
        assertNull(event.longitude)
        assertFalse(event.message.contains("https://maps.google.com"))
    }

    @Test
    fun directSos_cancellableCountdown_cancelsBeforeSending() = runBlocking {
        guardianRepo.guardians.add(
            Guardian(1L, "Mom", "+919876543210", "Mother", true, 0L, 0L, 0L, isSosEnabled = true)
        )
        sosPrefsRepo.setCountdownDurationSeconds(5)

        manager.startCancellableSos()
        assertTrue(manager.state.value is DirectSosState.Countdown)

        manager.cancelCountdown()
        assertEquals(DirectSosState.Idle, manager.state.value)
        assertEquals(0, eventDao.events.size)
    }

    @Test
    fun directSos_rapidRepeatedTaps_executesOnlyOnce() = runBlocking {
        guardianRepo.guardians.add(
            Guardian(1L, "Mom", "+919876543210", "Mother", true, 0L, 0L, 0L, isSosEnabled = true)
        )

        manager.triggerImmediateSos()
        manager.triggerImmediateSos()
        manager.triggerImmediateSos()

        // Should only have created 1 event
        assertEquals(1, eventDao.events.size)
    }
}
