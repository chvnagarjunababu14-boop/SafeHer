package com.safeher.app.data.guardian

/**
 * Explicit verification states for trusted guardians.
 *
 * SafeHer treats verification honestly:
 * - [UNVERIFIED]: Number is saved locally; no delivery confirmation or contact acknowledgement.
 * - [VERIFICATION_PENDING]: Reserved for Phase 7 when a real carrier OTP / invitation flow exists.
 * - [VERIFIED]: Contact has genuinely acknowledged participation in safety circle.
 */
enum class GuardianVerificationState {
    UNVERIFIED,
    VERIFICATION_PENDING,
    VERIFIED
}

/**
 * Domain model representing a trusted guardian contact stored locally on device.
 *
 * @param id Unique local identifier
 * @param name Display name of guardian
 * @param phoneNumber E.164 or normalized phone number
 * @param relationship Optional relationship tag (e.g. "Family", "Friend", "Partner")
 * @param isVerified Whether this guardian has acknowledged verification (default false)
 * @param createdAt Creation timestamp (epoch millis)
 * @param updatedAt Last update timestamp (epoch millis)
 * @param verifiedAt Timestamp when verification occurred, if verified
 * @param verificationState Explicit enum state (defaults to [GuardianVerificationState.UNVERIFIED])
 */
data class Guardian(
    val id: Long = 0,
    val name: String,
    val phoneNumber: String,
    val relationship: String? = null,
    val isVerified: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val verifiedAt: Long? = null,
    val verificationState: GuardianVerificationState = if (isVerified) GuardianVerificationState.VERIFIED else GuardianVerificationState.UNVERIFIED,
    val isSosEnabled: Boolean = true,
    /** Opt-in for automatic low/critical battery SMS. Defaults OFF — never assumed from SOS. */
    val isBatteryAlertEnabled: Boolean = false
)

/**
 * Result abstraction for guardian operations (add/edit/delete).
 */
sealed interface GuardianOperationResult {
    data class Success(val guardianId: Long) : GuardianOperationResult
    data class DuplicatePhone(val message: String) : GuardianOperationResult
    data class ValidationError(val field: String, val message: String) : GuardianOperationResult
    data class Error(val message: String) : GuardianOperationResult
}
