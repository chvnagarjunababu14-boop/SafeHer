package com.safeher.app.data.remotesos

enum class RemoteAlertDeliveryState(val displayLabel: String) {
    CREATED("Alert Created"),
    QUEUED("Notification Queued"),
    FCM_ACCEPTED("Dispatched via FCM"),
    DEVICE_RECEIVED("App-Reported Receipt"),
    ACKNOWLEDGED("Guardian Acknowledged"),
    FAILED("Dispatch Failed"),
    UNKNOWN("Delivery Unconfirmed")
}

data class RecipientDeliveryInfo(
    val guardianUid: String,
    val guardianName: String,
    val status: RemoteAlertDeliveryState,
    val updatedAtMillis: Long = System.currentTimeMillis(),
    val failureReason: String? = null
)

data class RemoteSosDispatchResult(
    val alertId: String,
    val isIdempotentReplay: Boolean,
    val recipientsCount: Int,
    val fcmAcceptedCount: Int,
    val fcmFailedCount: Int,
    val authorizedRecipients: List<String>
)

data class RemoteAlertDetails(
    val alertId: String,
    val senderUid: String,
    val senderDisplayName: String,
    val messageText: String,
    val hasLocation: Boolean,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val accuracyMeters: Float? = null,
    val createdAtMillis: Long = System.currentTimeMillis(),
    val isAcknowledgedByMe: Boolean = false
)
