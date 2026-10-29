package com.safeher.app.data.battery

import com.safeher.app.data.guardian.GuardianRepository
import com.safeher.app.data.location.LocationData
import com.safeher.app.data.location.LocationRepository
import com.safeher.app.data.location.LocationResult
import com.safeher.app.data.local.db.SosEventDao
import com.safeher.app.data.local.db.SosEventEntity
import com.safeher.app.data.session.UserSessionManager
import com.safeher.app.data.sos.DirectSmsSender
import com.safeher.app.data.sos.EmergencyMessageBuilder
import com.safeher.app.data.sos.SosPreferencesRepository
import com.safeher.app.data.sos.TelephonyCapability
import com.safeher.app.data.sos.RecipientDispatchState
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicBoolean

enum class BatteryAlertDispatchMode {
    LOW_BATTERY_ALERT,
    CRITICAL_BATTERY_ALERT
}

/**
 * Best-effort low/critical battery SMS alerts.
 *
 * Distinct from [com.safeher.app.data.sos.DirectSosManager]: does not start SOS countdown,
 * uses separate consent, and records separate event types.
 *
 * Android cannot guarantee execution immediately before power-off; treat critical alerts
 * as best effort only.
 */
class BatteryAlertManager(
    private val guardianRepository: GuardianRepository,
    private val sosPreferencesRepository: SosPreferencesRepository,
    private val directSmsSender: DirectSmsSender,
    private val sosEventDao: SosEventDao,
    private val stateStore: BatteryAlertStateStore,
    private val locationRepository: LocationRepository? = null,
    private val sessionManager: UserSessionManager? = null,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val managerScope: CoroutineScope = CoroutineScope(ioDispatcher)
) {
    private val isDispatching = AtomicBoolean(false)
    private val evaluateMutex = Mutex()
    private var inFlightJob: Job? = null

    /**
     * Evaluates a battery snapshot. Safe to call from a BroadcastReceiver.
     * First observation after process start while already below a threshold does not fire
     * (prevents duplicate alerts across app restarts).
     */
    fun onBatteryChanged(info: BatteryInfo) {
        inFlightJob?.cancel()
        inFlightJob = managerScope.launch {
            evaluateAndMaybeDispatch(info)
        }
    }

    suspend fun evaluateAndMaybeDispatch(info: BatteryInfo): BatteryAlertDispatchMode? =
        evaluateMutex.withLock {
            withContext(ioDispatcher) {
                val prefs = sosPreferencesRepository.getPreferences()
                if (!prefs.isBatteryAlertsEnabled || !prefs.isBatteryAlertsConsentGiven) {
                    return@withContext null
                }

                val lowThreshold = prefs.batteryLowThresholdPercent
                val criticalThreshold = prefs.batteryCriticalThresholdPercent
                    .coerceAtMost(lowThreshold)

                val isCharging = info.chargingState == ChargingState.CHARGING
                val percent = info.percent

                if (BatteryInfoFormatter.shouldResetThreshold(percent, isCharging, lowThreshold)) {
                    stateStore.firedLowForCycle = false
                }
                if (BatteryInfoFormatter.shouldResetThreshold(percent, isCharging, criticalThreshold)) {
                    stateStore.firedCriticalForCycle = false
                }

                val previous = stateStore.lastObservedPercent
                if (percent != null) {
                    stateStore.lastObservedPercent = percent
                }

                // Arm without firing on first observation (including restart while already low).
                if (previous == null || percent == null || isCharging) {
                    return@withContext null
                }

                val crossedCritical = BatteryInfoFormatter.crossedThresholdDown(
                    previous,
                    percent,
                    criticalThreshold
                ) && !stateStore.firedCriticalForCycle

                val crossedLow = BatteryInfoFormatter.crossedThresholdDown(
                    previous,
                    percent,
                    lowThreshold
                ) && !stateStore.firedLowForCycle

                when {
                    crossedCritical -> {
                        val sent = dispatchAlert(BatteryAlertDispatchMode.CRITICAL_BATTERY_ALERT, info)
                        if (sent) {
                            stateStore.firedCriticalForCycle = true
                            stateStore.firedLowForCycle = true
                            BatteryAlertDispatchMode.CRITICAL_BATTERY_ALERT
                        } else {
                            null
                        }
                    }
                    crossedLow -> {
                        val sent = dispatchAlert(BatteryAlertDispatchMode.LOW_BATTERY_ALERT, info)
                        if (sent) {
                            stateStore.firedLowForCycle = true
                            BatteryAlertDispatchMode.LOW_BATTERY_ALERT
                        } else {
                            null
                        }
                    }
                    else -> null
                }
            }
        }

    /**
     * @return true when SMS dispatch was attempted to at least one eligible recipient.
     */
    private suspend fun dispatchAlert(
        mode: BatteryAlertDispatchMode,
        batteryInfo: BatteryInfo
    ): Boolean {
        if (!isDispatching.compareAndSet(false, true)) return false
        try {
            if (!directSmsSender.hasSmsPermission()) return false
            if (directSmsSender.checkTelephonyCapability() is TelephonyCapability.Unavailable) return false

            val recipients = EmergencyMessageBuilder.getEligibleGuardians(
                guardianRepository.getBatteryAlertEnabledGuardians()
            )
            if (recipients.isEmpty()) return false

            val prefs = sosPreferencesRepository.getPreferences()
            var locationData: LocationData? = null
            if (prefs.isBatteryAlertLocationEnabled && locationRepository != null) {
                val locResult = withTimeoutOrNull(1500L) {
                    try {
                        locationRepository.getCurrentLocation(isPrecise = false)
                    } catch (_: Exception) {
                        null
                    }
                }
                if (locResult is LocationResult.Success) {
                    locationData = locResult.location
                }
            }

            val now = System.currentTimeMillis()
            val kind = when (mode) {
                BatteryAlertDispatchMode.LOW_BATTERY_ALERT ->
                    EmergencyMessageBuilder.BatteryAlertKind.LOW
                BatteryAlertDispatchMode.CRITICAL_BATTERY_ALERT ->
                    EmergencyMessageBuilder.BatteryAlertKind.CRITICAL
            }
            val messageText = EmergencyMessageBuilder.buildBatteryAlertMessage(
                kind = kind,
                batteryInfo = batteryInfo,
                location = locationData,
                includeLocation = prefs.isBatteryAlertLocationEnabled,
                alertTimestampMillis = now
            )

            val uid = sessionManager?.currentUserId
            val eventEntity = SosEventEntity(
                0,
                uid,
                now,
                messageText,
                locationData?.latitude,
                locationData?.longitude,
                locationData?.accuracyMeters,
                recipients.size,
                0,
                0,
                mode.name,
                "{\"batteryPercent\":${batteryInfo.percent ?: -1}}"
            )
            val eventId = try {
                sosEventDao.insert(eventEntity)
            } catch (_: Exception) {
                0L
            }

            val results = coroutineScope {
                recipients.map { guardian ->
                    async {
                        directSmsSender.sendEmergencySms(
                            eventId = eventId,
                            guardianId = guardian.id,
                            guardianName = guardian.name,
                            phoneNumber = guardian.phoneNumber,
                            message = messageText
                        ) { /* status updates ignored for battery alerts */ }
                    }
                }.awaitAll()
            }

            if (eventId > 0) {
                try {
                    var success = 0
                    var failed = 0
                    for (status in results) {
                        when (status.state) {
                            RecipientDispatchState.SUBMITTED_TO_RADIO,
                            RecipientDispatchState.DELIVERY_CONFIRMED -> success++
                            RecipientDispatchState.FAILED -> failed++
                            else -> {}
                        }
                    }
                    eventEntity.id = eventId
                    eventEntity.successCount = success
                    eventEntity.failureCount = failed
                    sosEventDao.update(eventEntity)
                } catch (_: Exception) {
                    // Non-fatal
                }
            }
            return true
        } finally {
            isDispatching.set(false)
        }
    }
}
