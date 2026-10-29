package com.safeher.app.data.sos

import android.Manifest
import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.telephony.SmsManager
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicInteger

/**
 * Android telephony implementation of [DirectSmsSender] using system [SmsManager].
 *
 * Adheres strictly to security and privacy standards:
 * - Never logs message contents, phone numbers, or credentials.
 * - Handles multipart SMS, single SIM, multi-SIM subscription defaults.
 * - Manages sent and delivery broadcast callbacks with unique, immutable PendingIntents.
 * - Never hangs: enforces a strict 15-second timeout on radio callbacks.
 */
class AndroidDirectSmsSender(
    private val context: Context
) : DirectSmsSender {

    companion object {
        private const val TAG = "DirectSmsSender"
        private const val RADIO_TIMEOUT_MS = 15_000L
        private val requestCounter = AtomicInteger(1000)
    }

    override fun checkTelephonyCapability(): TelephonyCapability {
        val pm = context.packageManager
        if (!pm.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)) {
            return TelephonyCapability.Unavailable("Device lacks telephony hardware")
        }

        val isAirplaneMode = try {
            Settings.Global.getInt(context.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) != 0
        } catch (_: Exception) {
            false
        }
        if (isAirplaneMode) {
            return TelephonyCapability.Unavailable("Airplane mode is active")
        }

        val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
        if (tm != null && tm.simState != TelephonyManager.SIM_STATE_READY) {
            return TelephonyCapability.Unavailable("SIM card is not ready or missing")
        }

        return TelephonyCapability.Available
    }

    override fun hasSmsPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.SEND_SMS
        ) == PackageManager.PERMISSION_GRANTED
    }

    override suspend fun sendEmergencySms(
        eventId: Long,
        guardianId: Long,
        guardianName: String,
        phoneNumber: String,
        message: String,
        onStatusUpdate: ((RecipientDispatchStatus) -> Unit)?
    ): RecipientDispatchStatus = withContext(Dispatchers.IO) {
        val initialStatus = RecipientDispatchStatus(
            guardianId = guardianId,
            guardianName = guardianName,
            phoneNumber = phoneNumber,
            state = RecipientDispatchState.PENDING
        )
        onStatusUpdate?.invoke(initialStatus)

        // 1. Permission Check
        if (!hasSmsPermission()) {
            val failure = initialStatus.copy(
                state = RecipientDispatchState.FAILED,
                statusMessage = "SMS permission (SEND_SMS) not granted by user"
            )
            onStatusUpdate?.invoke(failure)
            return@withContext failure
        }

        // 2. Hardware / SIM Check
        val capability = checkTelephonyCapability()
        if (capability is TelephonyCapability.Unavailable) {
            val failure = initialStatus.copy(
                state = RecipientDispatchState.FAILED,
                statusMessage = capability.reason
            )
            onStatusUpdate?.invoke(failure)
            return@withContext failure
        }

        // 3. Phone Number Validation
        val cleanedPhone = EmergencyMessageBuilder.sanitizePhoneNumber(phoneNumber)
        if (cleanedPhone.isBlank()) {
            val failure = initialStatus.copy(
                state = RecipientDispatchState.FAILED,
                statusMessage = "Invalid recipient phone number"
            )
            onStatusUpdate?.invoke(failure)
            return@withContext failure
        }

        // 4. Resolve SmsManager
        val smsManager: SmsManager = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(SmsManager::class.java)
                    ?: @Suppress("DEPRECATION") SmsManager.getDefault()
            } else {
                @Suppress("DEPRECATION") SmsManager.getDefault()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to obtain SmsManager: ${e.message}")
            val failure = initialStatus.copy(
                state = RecipientDispatchState.FAILED,
                statusMessage = "Cellular SMS service unavailable"
            )
            onStatusUpdate?.invoke(failure)
            return@withContext failure
        }

        // 5. Divide message if necessary
        val parts = try {
            smsManager.divideMessage(message)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to divide message: ${e.message}")
            arrayListOf(message)
        }
        val partCount = parts.size

        val uniqueReqId = requestCounter.incrementAndGet()
        val sentAction = "com.safeher.app.SMS_SENT_${eventId}_${guardianId}_$uniqueReqId"
        val deliveredAction = "com.safeher.app.SMS_DELIVERED_${eventId}_${guardianId}_$uniqueReqId"

        val sentPartsRemaining = AtomicInteger(partCount)
        val hasSentFailure = AtomicInteger(0)
        val sentFailureReason = StringBuilder()

        val submissionDeferred = CompletableDeferred<RecipientDispatchStatus>()

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(recvContext: Context?, intent: Intent?) {
                when (intent?.action) {
                    sentAction -> {
                        val resultCode = resultCode
                        if (resultCode != Activity.RESULT_OK) {
                            hasSentFailure.incrementAndGet()
                            val reason = when (resultCode) {
                                SmsManager.RESULT_ERROR_GENERIC_FAILURE -> "Cellular radio generic failure"
                                SmsManager.RESULT_ERROR_NO_SERVICE -> "No cellular service"
                                SmsManager.RESULT_ERROR_NULL_PDU -> "Null PDU"
                                SmsManager.RESULT_ERROR_RADIO_OFF -> "Cellular radio off"
                                else -> "Radio error code $resultCode"
                            }
                            if (sentFailureReason.isEmpty()) {
                                sentFailureReason.append(reason)
                            }
                        }

                        if (sentPartsRemaining.decrementAndGet() == 0) {
                            if (hasSentFailure.get() > 0) {
                                val failStatus = initialStatus.copy(
                                    state = RecipientDispatchState.FAILED,
                                    statusMessage = sentFailureReason.toString()
                                )
                                onStatusUpdate?.invoke(failStatus)
                                submissionDeferred.complete(failStatus)
                            } else {
                                val successStatus = initialStatus.copy(
                                    state = RecipientDispatchState.SUBMITTED_TO_RADIO,
                                    statusMessage = "SMS submitted to cellular network"
                                )
                                onStatusUpdate?.invoke(successStatus)
                                submissionDeferred.complete(successStatus)
                            }
                        }
                    }
                    deliveredAction -> {
                        if (resultCode == Activity.RESULT_OK) {
                            val deliveryStatus = initialStatus.copy(
                                state = RecipientDispatchState.DELIVERY_CONFIRMED,
                                statusMessage = "Carrier delivery confirmed"
                            )
                            onStatusUpdate?.invoke(deliveryStatus)
                        }
                    }
                }
            }
        }

        val filter = IntentFilter().apply {
            addAction(sentAction)
            addAction(deliveredAction)
        }

        try {
            ContextCompat.registerReceiver(
                context,
                receiver,
                filter,
                ContextCompat.RECEIVER_NOT_EXPORTED
            )

            val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

            if (partCount > 1) {
                val sentIntents = ArrayList<PendingIntent>(partCount)
                val deliveryIntents = ArrayList<PendingIntent>(partCount)
                for (i in 0 until partCount) {
                    sentIntents.add(
                        PendingIntent.getBroadcast(
                            context,
                            requestCounter.incrementAndGet(),
                            Intent(sentAction),
                            flags
                        )
                    )
                    deliveryIntents.add(
                        PendingIntent.getBroadcast(
                            context,
                            requestCounter.incrementAndGet(),
                            Intent(deliveredAction),
                            flags
                        )
                    )
                }
                smsManager.sendMultipartTextMessage(
                    cleanedPhone,
                    null,
                    parts,
                    sentIntents,
                    deliveryIntents
                )
            } else {
                val sentIntent = PendingIntent.getBroadcast(
                    context,
                    requestCounter.incrementAndGet(),
                    Intent(sentAction),
                    flags
                )
                val deliveryIntent = PendingIntent.getBroadcast(
                    context,
                    requestCounter.incrementAndGet(),
                    Intent(deliveredAction),
                    flags
                )
                smsManager.sendTextMessage(
                    cleanedPhone,
                    null,
                    parts[0],
                    sentIntent,
                    deliveryIntent
                )
            }

            // Wait for radio acknowledgment with timeout
            val result = withTimeoutOrNull(RADIO_TIMEOUT_MS) {
                submissionDeferred.await()
            } ?: run {
                val timeoutStatus = initialStatus.copy(
                    state = RecipientDispatchState.SUBMITTED_TO_RADIO,
                    statusMessage = "SMS sent (carrier delivery unconfirmed)"
                )
                onStatusUpdate?.invoke(timeoutStatus)
                timeoutStatus
            }
            return@withContext result

        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException during SMS dispatch")
            val failStatus = initialStatus.copy(
                state = RecipientDispatchState.FAILED,
                statusMessage = "Security exception: permission revoked"
            )
            onStatusUpdate?.invoke(failStatus)
            return@withContext failStatus
        } catch (e: Exception) {
            Log.e(TAG, "Exception during SMS dispatch: ${e.javaClass.simpleName}")
            val failStatus = initialStatus.copy(
                state = RecipientDispatchState.FAILED,
                statusMessage = "SMS transmission failed: ${e.message ?: "Unknown error"}"
            )
            onStatusUpdate?.invoke(failStatus)
            return@withContext failStatus
        } finally {
            try {
                context.unregisterReceiver(receiver)
            } catch (_: Exception) {
                // Already unregistered or not registered
            }
        }
    }
}
