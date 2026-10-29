package com.safeher.app

import android.content.SharedPreferences
import com.safeher.app.data.battery.BatteryAlertDispatchMode
import com.safeher.app.data.battery.BatteryAlertManager
import com.safeher.app.data.battery.BatteryAlertStateStore
import com.safeher.app.data.battery.BatteryInfo
import com.safeher.app.data.battery.BatteryInfoFormatter
import com.safeher.app.data.battery.BatteryStateReader
import com.safeher.app.data.battery.ChargingState
import com.safeher.app.data.guardian.Guardian
import com.safeher.app.data.location.LocationData
import com.safeher.app.data.location.LocationFreshness
import com.safeher.app.data.sos.DemoDirectSmsSender
import com.safeher.app.data.sos.DirectSosManager
import com.safeher.app.data.sos.DirectSosState
import com.safeher.app.data.sos.EmergencyMessageBuilder
import com.safeher.app.data.sos.SosPreferences
import com.safeher.app.data.sos.SosPreferencesRepositoryImpl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.lang.reflect.Proxy

class BatteryAlertTest {

    private lateinit var memoryPrefs: MutableMap<String, Any>
    private lateinit var prefsProxy: SharedPreferences
    private lateinit var sosPrefs: SosPreferencesRepositoryImpl
    private lateinit var stateStore: BatteryAlertStateStore
    private lateinit var guardianRepo: FakeTestGuardianRepository
    private lateinit var eventDao: FakeSosEventDao
    private lateinit var sender: DemoDirectSmsSender

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
                "clear" -> {
                    memoryPrefs.clear()
                    editorProxy
                }
                "apply", "commit" -> null
                else -> null
            }
        } as SharedPreferences.Editor

        prefsProxy = Proxy.newProxyInstance(
            SharedPreferences::class.java.classLoader,
            arrayOf(SharedPreferences::class.java)
        ) { _, method, args ->
            when (method.name) {
                "getString" -> memoryPrefs[args[0] as String] ?: args[1]
                "getBoolean" -> memoryPrefs[args[0] as String] ?: args[1]
                "getInt" -> memoryPrefs[args[0] as String] ?: args[1]
                "contains" -> memoryPrefs.containsKey(args[0] as String)
                "edit" -> editorProxy
                else -> null
            }
        } as SharedPreferences

        sosPrefs = SosPreferencesRepositoryImpl(prefsProxy, null)
        stateStore = BatteryAlertStateStore(prefsProxy, null)
        guardianRepo = FakeTestGuardianRepository()
        eventDao = FakeSosEventDao()
        sender = DemoDirectSmsSender()
    }

    private fun enableBatteryAlerts() {
        sosPrefs.setBatteryAlertsConsent(true)
        sosPrefs.setBatteryAlertsEnabled(true)
    }

    private fun createManager(
        smsPermission: Boolean = true
    ): BatteryAlertManager {
        sender.simulatedPermissionGranted = smsPermission
        sender.stepDelayMs = 0L
        return BatteryAlertManager(
            guardianRepository = guardianRepo,
            sosPreferencesRepository = sosPrefs,
            directSmsSender = sender,
            sosEventDao = eventDao,
            stateStore = stateStore,
            locationRepository = null,
            sessionManager = null,
            ioDispatcher = Dispatchers.Unconfined,
            managerScope = kotlinx.coroutines.CoroutineScope(Dispatchers.Unconfined)
        )
    }

    @Test
    fun formatter_formatsKnownAndUnknownBattery() {
        val known = BatteryInfo(18, ChargingState.NOT_CHARGING)
        assertEquals("18%", BatteryInfoFormatter.formatPercent(known))
        assertEquals("Not charging", BatteryInfoFormatter.formatCharging(known))

        val unknown = BatteryInfo(null, ChargingState.UNKNOWN)
        assertEquals("Unknown", BatteryInfoFormatter.formatPercent(unknown))
        assertEquals("Unknown", BatteryInfoFormatter.formatCharging(unknown))
    }

    @Test
    fun formatter_thresholdCrossingAndReset() {
        assertTrue(BatteryInfoFormatter.crossedThresholdDown(20, 15, 15))
        assertFalse(BatteryInfoFormatter.crossedThresholdDown(15, 14, 15))
        assertFalse(BatteryInfoFormatter.crossedThresholdDown(null, 10, 15))
        assertFalse(BatteryInfoFormatter.crossedThresholdDown(20, null, 15))

        assertTrue(BatteryInfoFormatter.shouldResetThreshold(20, false, 15))
        assertTrue(BatteryInfoFormatter.shouldResetThreshold(10, true, 15))
        assertFalse(BatteryInfoFormatter.shouldResetThreshold(10, false, 15))
        assertFalse(BatteryInfoFormatter.shouldResetThreshold(null, false, 15))
    }

    @Test
    fun sosMessage_includesBatteryTimestampAndOmitsLocationWhenDisabled() {
        val battery = BatteryInfo(18, ChargingState.NOT_CHARGING, capturedAtMillis = 1_700_000_000_000L)
        val message = EmergencyMessageBuilder.buildDirectSosMessage(
            customMessage = "Emergency: I may need help.",
            location = null,
            includeLocation = false,
            batteryInfo = battery,
            alertTimestampMillis = 1_700_000_000_000L
        )
        assertTrue(message.contains("SAFEHER SOS ALERT"))
        assertTrue(message.contains("Emergency: I may need help."))
        assertTrue(message.contains("Battery: 18%"))
        assertTrue(message.contains("Charging: Not charging"))
        assertTrue(message.contains("Time:"))
        assertFalse(message.contains("maps.google.com"))
    }

    @Test
    fun sosMessage_includesLocationWhenEnabledAndFresh() {
        val location = LocationData(
            latitude = 12.9716,
            longitude = 77.5946,
            accuracyMeters = 12f,
            timestampMillis = System.currentTimeMillis(),
            isFresh = true,
            freshness = LocationFreshness.FRESH
        )
        val message = EmergencyMessageBuilder.buildDirectSosMessage(
            customMessage = "Help",
            location = location,
            includeLocation = true,
            batteryInfo = BatteryInfo(42, ChargingState.CHARGING),
            alertTimestampMillis = System.currentTimeMillis()
        )
        assertTrue(message.contains("maps.google.com"))
        assertTrue(message.contains("Location updated:"))
        assertTrue(message.contains("Battery: 42%"))
        assertTrue(message.contains("Charging: Charging"))
    }

    @Test
    fun batteryAlertMessage_isDistinctFromSos() {
        val message = EmergencyMessageBuilder.buildBatteryAlertMessage(
            kind = EmergencyMessageBuilder.BatteryAlertKind.LOW,
            batteryInfo = BatteryInfo(14, ChargingState.NOT_CHARGING),
            includeLocation = false
        )
        assertTrue(message.contains("SAFEHER LOW BATTERY ALERT"))
        assertTrue(message.contains(EmergencyMessageBuilder.BATTERY_ALERT_FOOTER))
        assertFalse(message.contains("SAFEHER SOS ALERT"))
        assertFalse(message.contains("I NEED HELP"))
    }

    @Test
    fun preferences_defaultBatteryAlertsOff() {
        val defaults = sosPrefs.getPreferences()
        assertFalse(defaults.isBatteryAlertsEnabled)
        assertFalse(defaults.isBatteryAlertsConsentGiven)
        assertFalse(defaults.isBatteryAlertLocationEnabled)
        assertEquals(SosPreferences.DEFAULT_BATTERY_LOW_THRESHOLD, defaults.batteryLowThresholdPercent)
        assertEquals(SosPreferences.DEFAULT_BATTERY_CRITICAL_THRESHOLD, defaults.batteryCriticalThresholdPercent)
    }

    @Test
    fun noAlert_whenDisabledOrNoEligibleGuardian() = runBlocking {
        val manager = createManager()
        manager.evaluateAndMaybeDispatch(BatteryInfo(80, ChargingState.NOT_CHARGING))
        val mode = manager.evaluateAndMaybeDispatch(BatteryInfo(10, ChargingState.NOT_CHARGING))
        assertNull(mode)
        assertEquals(0, eventDao.events.size)
        assertEquals(0, sender.sentMessages.size)

        enableBatteryAlerts()
        manager.evaluateAndMaybeDispatch(BatteryInfo(80, ChargingState.NOT_CHARGING))
        val stillNone = manager.evaluateAndMaybeDispatch(BatteryInfo(10, ChargingState.NOT_CHARGING))
        assertNull(stillNone)
        assertEquals(0, sender.sentMessages.size)
    }

    @Test
    fun lowBattery_firesOncePerDischargeCycle() = runBlocking {
        enableBatteryAlerts()
        guardianRepo.guardians.add(
            Guardian(id = 1, name = "Mom", phoneNumber = "+15551234567", isBatteryAlertEnabled = true)
        )
        val manager = createManager()

        manager.evaluateAndMaybeDispatch(BatteryInfo(20, ChargingState.NOT_CHARGING))
        val first = manager.evaluateAndMaybeDispatch(BatteryInfo(15, ChargingState.NOT_CHARGING))
        assertEquals(BatteryAlertDispatchMode.LOW_BATTERY_ALERT, first)
        assertEquals(1, sender.sentMessages.size)
        assertEquals("LOW_BATTERY_ALERT", eventDao.events.first().dispatchMode)

        val duplicate = manager.evaluateAndMaybeDispatch(BatteryInfo(12, ChargingState.NOT_CHARGING))
        assertNull(duplicate)
        assertEquals(1, sender.sentMessages.size)
    }

    @Test
    fun criticalBattery_firesAndSuppressesLowDuplicate() = runBlocking {
        enableBatteryAlerts()
        guardianRepo.guardians.add(
            Guardian(id = 2, name = "Dad", phoneNumber = "+15557654321", isBatteryAlertEnabled = true)
        )
        val manager = createManager()

        manager.evaluateAndMaybeDispatch(BatteryInfo(10, ChargingState.NOT_CHARGING))
        val critical = manager.evaluateAndMaybeDispatch(BatteryInfo(5, ChargingState.NOT_CHARGING))
        assertEquals(BatteryAlertDispatchMode.CRITICAL_BATTERY_ALERT, critical)
        assertEquals("CRITICAL_BATTERY_ALERT", eventDao.events.first().dispatchMode)

        val again = manager.evaluateAndMaybeDispatch(BatteryInfo(3, ChargingState.NOT_CHARGING))
        assertNull(again)
        assertEquals(1, sender.sentMessages.size)
    }

    @Test
    fun resetAfterCharging_allowsNewAlert() = runBlocking {
        enableBatteryAlerts()
        guardianRepo.guardians.add(
            Guardian(id = 3, name = "Sis", phoneNumber = "+15550001111", isBatteryAlertEnabled = true)
        )
        val manager = createManager()

        manager.evaluateAndMaybeDispatch(BatteryInfo(20, ChargingState.NOT_CHARGING))
        assertEquals(
            BatteryAlertDispatchMode.LOW_BATTERY_ALERT,
            manager.evaluateAndMaybeDispatch(BatteryInfo(14, ChargingState.NOT_CHARGING))
        )

        manager.evaluateAndMaybeDispatch(BatteryInfo(30, ChargingState.CHARGING))
        manager.evaluateAndMaybeDispatch(BatteryInfo(40, ChargingState.NOT_CHARGING))
        val again = manager.evaluateAndMaybeDispatch(BatteryInfo(14, ChargingState.NOT_CHARGING))
        assertEquals(BatteryAlertDispatchMode.LOW_BATTERY_ALERT, again)
        assertEquals(2, sender.sentMessages.size)
    }

    @Test
    fun appRestartWhileBelowThreshold_doesNotRefire() = runBlocking {
        enableBatteryAlerts()
        guardianRepo.guardians.add(
            Guardian(id = 4, name = "Bro", phoneNumber = "+15552223333", isBatteryAlertEnabled = true)
        )
        // Simulate persisted last observation already at 10% with no prior arming in this process.
        stateStore.lastObservedPercent = null

        val manager = createManager()
        val firstObs = manager.evaluateAndMaybeDispatch(BatteryInfo(10, ChargingState.NOT_CHARGING))
        assertNull("First observation after restart must arm without firing", firstObs)
        assertEquals(0, sender.sentMessages.size)

        val stillBelow = manager.evaluateAndMaybeDispatch(BatteryInfo(9, ChargingState.NOT_CHARGING))
        // 9 is still above critical(5) and already below low(15); crossing low requires previous > 15
        assertNull(stillBelow)
    }

    @Test
    fun smsPermissionDenied_doesNotSend() = runBlocking {
        enableBatteryAlerts()
        guardianRepo.guardians.add(
            Guardian(id = 5, name = "Aunt", phoneNumber = "+15554445555", isBatteryAlertEnabled = true)
        )
        val manager = createManager(smsPermission = false)
        manager.evaluateAndMaybeDispatch(BatteryInfo(20, ChargingState.NOT_CHARGING))
        manager.evaluateAndMaybeDispatch(BatteryInfo(10, ChargingState.NOT_CHARGING))
        assertEquals(0, sender.sentMessages.size)
    }

    @Test
    fun directSos_includesBatteryFromReader() = runBlocking {
        guardianRepo.guardians.add(
            Guardian(id = 9, name = "Emergency", phoneNumber = "+15559998888", isSosEnabled = true)
        )
        sosPrefs.setConsentGiven(true)
        sosPrefs.setDirectSmsEnabled(true)
        sosPrefs.setLocationSharingEnabled(false)
        sender.stepDelayMs = 0L

        val batteryReader = BatteryStateReader {
            BatteryInfo(18, ChargingState.NOT_CHARGING)
        }
        val manager = DirectSosManager(
            guardianRepository = guardianRepo,
            sosPreferencesRepository = sosPrefs,
            directSmsSender = sender,
            locationRepository = null,
            sosEventDao = eventDao,
            sessionManager = null,
            batteryStateReader = batteryReader,
            ioDispatcher = Dispatchers.Unconfined,
            managerScope = kotlinx.coroutines.CoroutineScope(Dispatchers.Unconfined)
        )

        manager.triggerImmediateSos()
        var completed: DirectSosState.Completed? = null
        repeat(40) {
            completed = manager.state.value as? DirectSosState.Completed
            if (completed != null) return@repeat
            delay(25)
        }

        assertNotNull("Direct SOS should complete with battery-enriched message", completed)
        val body = sender.sentMessages.first().message
        assertTrue(body.contains("Battery: 18%"))
        assertTrue(body.contains("Charging: Not charging"))
        assertEquals("DIRECT_AUTOMATIC_SMS", eventDao.events.first().dispatchMode)
    }

    @Test
    fun unknownBattery_doesNotCrossThreshold() = runBlocking {
        enableBatteryAlerts()
        guardianRepo.guardians.add(
            Guardian(id = 6, name = "Uncle", phoneNumber = "+15556667777", isBatteryAlertEnabled = true)
        )
        val manager = createManager()
        manager.evaluateAndMaybeDispatch(BatteryInfo(20, ChargingState.NOT_CHARGING))
        val unknown = manager.evaluateAndMaybeDispatch(BatteryInfo(null, ChargingState.UNKNOWN))
        assertNull(unknown)
        assertEquals(0, sender.sentMessages.size)
    }
}
