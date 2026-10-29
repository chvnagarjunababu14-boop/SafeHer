package com.safeher.app.data.sos

/**
 * Status of an individual recipient's SMS dispatch.
 */
enum class RecipientDispatchState {
    PENDING,
    SUBMITTED_TO_RADIO,
    DELIVERY_CONFIRMED,
    FAILED
}

/**
 * Detailed status for an individual recipient during direct SOS dispatch.
 */
data class RecipientDispatchStatus(
    val guardianId: Long,
    val guardianName: String,
    val phoneNumber: String,
    val state: RecipientDispatchState,
    val statusMessage: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Result of checking telephony hardware, SIM presence, and airplane mode.
 */
sealed class TelephonyCapability {
    object Available : TelephonyCapability()
    data class Unavailable(val reason: String) : TelephonyCapability()
}

/**
 * Contract for sending direct emergency SMS messages via telephony hardware or test simulators.
 */
interface DirectSmsSender {

    /**
     * Checks if the device has telephony hardware, an active SIM card, and network readiness.
     */
    fun checkTelephonyCapability(): TelephonyCapability

    /**
     * Checks if android.permission.SEND_SMS has been granted by the user.
     */
    fun hasSmsPermission(): Boolean

    /**
     * Directly sends an emergency SMS to a single recipient, notifying callers of
     * status transitions (e.g. SUBMITTED_TO_RADIO, DELIVERY_CONFIRMED, FAILED).
     *
     * Never throws unhandled exceptions; errors are captured in the returned [RecipientDispatchStatus].
     */
    suspend fun sendEmergencySms(
        eventId: Long,
        guardianId: Long,
        guardianName: String,
        phoneNumber: String,
        message: String,
        onStatusUpdate: ((RecipientDispatchStatus) -> Unit)? = null
    ): RecipientDispatchStatus
}
