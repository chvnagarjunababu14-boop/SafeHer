package com.safeher.app.data.sos

import kotlinx.coroutines.delay

/**
 * Simulated [DirectSmsSender] for Demo Mode and test environments.
 *
 * Guarantees that no cellular hardware is touched and no real SMS messages are sent.
 */
data class DemoSentSms(
    val eventId: Long,
    val guardianId: Long,
    val guardianName: String,
    val phoneNumber: String,
    val message: String
)

class DemoDirectSmsSender(
    var simulatedCapability: TelephonyCapability = TelephonyCapability.Available,
    var simulatedPermissionGranted: Boolean = true,
    var simulatedFailureGuardianIds: Set<Long> = emptySet(),
    var stepDelayMs: Long = 400L
) : DirectSmsSender {

    val sentMessages: MutableList<DemoSentSms> = mutableListOf()

    override fun checkTelephonyCapability(): TelephonyCapability = simulatedCapability

    override fun hasSmsPermission(): Boolean = simulatedPermissionGranted

    override suspend fun sendEmergencySms(
        eventId: Long,
        guardianId: Long,
        guardianName: String,
        phoneNumber: String,
        message: String,
        onStatusUpdate: ((RecipientDispatchStatus) -> Unit)?
    ): RecipientDispatchStatus {
        val initialStatus = RecipientDispatchStatus(
            guardianId = guardianId,
            guardianName = guardianName,
            phoneNumber = phoneNumber,
            state = RecipientDispatchState.PENDING
        )
        onStatusUpdate?.invoke(initialStatus)

        if (!simulatedPermissionGranted) {
            val failure = initialStatus.copy(
                state = RecipientDispatchState.FAILED,
                statusMessage = "[Demo] SMS permission not granted"
            )
            onStatusUpdate?.invoke(failure)
            return failure
        }

        if (simulatedCapability is TelephonyCapability.Unavailable) {
            val failure = initialStatus.copy(
                state = RecipientDispatchState.FAILED,
                statusMessage = "[Demo] ${(simulatedCapability as TelephonyCapability.Unavailable).reason}"
            )
            onStatusUpdate?.invoke(failure)
            return failure
        }

        val cleaned = EmergencyMessageBuilder.sanitizePhoneNumber(phoneNumber)
        if (cleaned.isBlank()) {
            val failure = initialStatus.copy(
                state = RecipientDispatchState.FAILED,
                statusMessage = "[Demo] Invalid phone number"
            )
            onStatusUpdate?.invoke(failure)
            return failure
        }

        if (stepDelayMs > 0) delay(stepDelayMs)

        if (simulatedFailureGuardianIds.contains(guardianId)) {
            val failure = initialStatus.copy(
                state = RecipientDispatchState.FAILED,
                statusMessage = "[Demo] Radio submission simulated failure"
            )
            onStatusUpdate?.invoke(failure)
            return failure
        }

        sentMessages.add(
            DemoSentSms(
                eventId = eventId,
                guardianId = guardianId,
                guardianName = guardianName,
                phoneNumber = phoneNumber,
                message = message
            )
        )

        // Transition to SUBMITTED_TO_RADIO
        val submitted = initialStatus.copy(
            state = RecipientDispatchState.SUBMITTED_TO_RADIO,
            statusMessage = "[Demo] Simulated SMS submitted to radio"
        )
        onStatusUpdate?.invoke(submitted)

        if (stepDelayMs > 0) delay(stepDelayMs)

        // Transition to DELIVERY_CONFIRMED
        val delivered = initialStatus.copy(
            state = RecipientDispatchState.DELIVERY_CONFIRMED,
            statusMessage = "[Demo] Simulated carrier delivery confirmed"
        )
        onStatusUpdate?.invoke(delivered)

        return delivered
    }
}
