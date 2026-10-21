package com.safeher.app.data.demo

import com.safeher.app.data.location.LocationData
import com.safeher.app.data.remoteguardian.RemoteGuardian
import com.safeher.app.data.remotesos.RecipientDeliveryInfo
import com.safeher.app.data.remotesos.RemoteAlertDeliveryState
import com.safeher.app.data.remotesos.RemoteAlertDetails
import com.safeher.app.data.remotesos.RemoteSosDispatchResult
import com.safeher.app.data.remotesos.RemoteSosRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/**
 * Self-contained mock RemoteSosRepository implementation for SafeHer Demo Mode.
 *
 * Simulates the full multi-stage delivery pipeline locally on-device without contacting
 * Firebase Cloud Messaging or Firebase Cloud Functions.
 *
 * Delivery pipeline transitions realistically:
 * CREATED -> QUEUED -> FCM_ACCEPTED -> DEVICE_RECEIVED -> ACKNOWLEDGED
 */
class DemoRemoteSosRepository(
    private val demoModeManager: DemoModeManager,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) : RemoteSosRepository {

    private val scope = CoroutineScope(SupervisorJob() + dispatcher)

    private val deliveryFlows = ConcurrentHashMap<String, MutableStateFlow<List<RecipientDeliveryInfo>>>()
    private val alertDetailsMap = ConcurrentHashMap<String, RemoteAlertDetails>()

    var lastDispatchedAlertId: String? = null
        private set

    init {
        demoModeManager.registerResetListener {
            deliveryFlows.clear()
            alertDetailsMap.clear()
            lastDispatchedAlertId = null
        }
    }

    override suspend fun dispatchRemoteSos(
        recipients: List<RemoteGuardian>,
        messageText: String,
        location: LocationData?,
        includeLocation: Boolean,
        idempotencyKey: String
    ): Result<RemoteSosDispatchResult> = withContext(dispatcher) {
        val alertId = "demo_alert_${System.currentTimeMillis() % 10000}"
        lastDispatchedAlertId = alertId

        val sender = demoModeManager.activeProfile.value.toAuthUser()

        val initialStatuses = recipients.map { guardian ->
            RecipientDeliveryInfo(
                guardianUid = guardian.guardianUid,
                guardianName = guardian.guardianDisplayName,
                status = RemoteAlertDeliveryState.CREATED,
                updatedAtMillis = System.currentTimeMillis()
            )
        }

        val flow = MutableStateFlow(initialStatuses)
        deliveryFlows[alertId] = flow

        val details = RemoteAlertDetails(
            alertId = alertId,
            senderUid = sender.uid,
            senderDisplayName = sender.displayName ?: "Demo User",
            messageText = messageText,
            hasLocation = includeLocation && location != null,
            latitude = if (includeLocation) location?.latitude else null,
            longitude = if (includeLocation) location?.longitude else null,
            accuracyMeters = if (includeLocation) location?.accuracyMeters else null,
            createdAtMillis = System.currentTimeMillis(),
            isAcknowledgedByMe = false
        )
        alertDetailsMap[alertId] = details

        // Simulate asynchronous FCM dispatch sequence in coroutine
        scope.launch {
            // Stage 1: Queued (approx 600ms)
            delay(600)
            updateAllStatuses(alertId, RemoteAlertDeliveryState.QUEUED)

            // Stage 2: Dispatched via FCM (approx 1200ms)
            delay(1200)
            updateAllStatuses(alertId, RemoteAlertDeliveryState.FCM_ACCEPTED)

            // Stage 3: App-Reported Receipt on recipient device (approx 1800ms)
            delay(1800)
            updateAllStatuses(alertId, RemoteAlertDeliveryState.DEVICE_RECEIVED)
        }

        Result.success(
            RemoteSosDispatchResult(
                alertId = alertId,
                isIdempotentReplay = false,
                recipientsCount = recipients.size,
                fcmAcceptedCount = recipients.size,
                fcmFailedCount = 0,
                authorizedRecipients = recipients.map { it.guardianUid }
            )
        )
    }

    override fun observeDeliveryStatuses(alertId: String): Flow<List<RecipientDeliveryInfo>> {
        val flow = deliveryFlows.getOrPut(alertId) {
            MutableStateFlow(
                listOf(
                    RecipientDeliveryInfo(
                        guardianUid = DemoProfile.GUARDIAN.uid,
                        guardianName = DemoProfile.GUARDIAN.displayName,
                        status = RemoteAlertDeliveryState.DEVICE_RECEIVED
                    )
                )
            )
        }
        return flow.asStateFlow()
    }

    override suspend fun getAlertDetails(alertId: String): Result<RemoteAlertDetails> = withContext(dispatcher) {
        val details = alertDetailsMap[alertId] ?: RemoteAlertDetails(
            alertId = alertId,
            senderUid = DemoProfile.USER.uid,
            senderDisplayName = DemoProfile.USER.displayName,
            messageText = "EMERGENCY: SafeHer SOS Alert triggered. [SIMULATED]",
            hasLocation = true,
            latitude = 12.9716,
            longitude = 77.5946,
            accuracyMeters = 8.5f,
            createdAtMillis = System.currentTimeMillis() - 30000,
            isAcknowledgedByMe = false
        )
        Result.success(details)
    }

    override suspend fun reportAlertReceived(alertId: String): Result<Unit> = withContext(dispatcher) {
        val currentUid = demoModeManager.activeProfile.value.uid
        updateSingleStatus(alertId, currentUid, RemoteAlertDeliveryState.DEVICE_RECEIVED)
        Result.success(Unit)
    }

    override suspend fun acknowledgeAlert(alertId: String, deviceModel: String): Result<Unit> = withContext(dispatcher) {
        val currentUid = demoModeManager.activeProfile.value.uid
        updateSingleStatus(alertId, currentUid, RemoteAlertDeliveryState.ACKNOWLEDGED)
        val details = alertDetailsMap[alertId]
        if (details != null) {
            alertDetailsMap[alertId] = details.copy(isAcknowledgedByMe = true)
        }
        Result.success(Unit)
    }

    /**
     * Evaluator helper: Simulates recipient guardian acknowledging the alert.
     */
    fun simulateGuardianAcknowledgement(alertId: String, targetGuardianUid: String? = null) {
        val flow = deliveryFlows[alertId] ?: return
        val updated = flow.value.map { item ->
            if (targetGuardianUid == null || item.guardianUid == targetGuardianUid) {
                item.copy(status = RemoteAlertDeliveryState.ACKNOWLEDGED, updatedAtMillis = System.currentTimeMillis())
            } else {
                item
            }
        }
        flow.value = updated
        val details = alertDetailsMap[alertId]
        if (details != null) {
            alertDetailsMap[alertId] = details.copy(isAcknowledgedByMe = true)
        }
    }

    private fun updateAllStatuses(alertId: String, status: RemoteAlertDeliveryState) {
        val flow = deliveryFlows[alertId] ?: return
        flow.value = flow.value.map {
            it.copy(status = status, updatedAtMillis = System.currentTimeMillis())
        }
    }

    private fun updateSingleStatus(alertId: String, guardianUid: String, status: RemoteAlertDeliveryState) {
        val flow = deliveryFlows[alertId] ?: return
        flow.value = flow.value.map {
            if (it.guardianUid == guardianUid) {
                it.copy(status = status, updatedAtMillis = System.currentTimeMillis())
            } else {
                it
            }
        }
    }
}
