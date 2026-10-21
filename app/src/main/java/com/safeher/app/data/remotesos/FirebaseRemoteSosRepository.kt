package com.safeher.app.data.remotesos

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.functions.FirebaseFunctions
import com.safeher.app.data.location.LocationData
import com.safeher.app.data.remoteguardian.RemoteGuardian
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class FirebaseRemoteSosRepository(
    private val context: Context,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) : RemoteSosRepository {

    private val isFirebaseAvailable: Boolean
        get() = try {
            FirebaseApp.getApps(context).isNotEmpty()
        } catch (_: Exception) {
            false
        }

    override suspend fun dispatchRemoteSos(
        recipients: List<RemoteGuardian>,
        messageText: String,
        location: LocationData?,
        includeLocation: Boolean,
        idempotencyKey: String
    ): Result<RemoteSosDispatchResult> = withContext(dispatcher) {
        if (!isFirebaseAvailable) {
            return@withContext Result.failure(
                IllegalStateException("Firebase is not configured on this device. Use SMS composer fallback.")
            )
        }

        val auth = FirebaseAuth.getInstance()
        if (auth.currentUser == null) {
            return@withContext Result.failure(
                IllegalStateException("You must be signed in to dispatch remote guardian alerts.")
            )
        }

        val activeRecipients = recipients.filter { it.isActive }.map { it.guardianUid }
        if (activeRecipients.isEmpty()) {
            return@withContext Result.failure(
                IllegalArgumentException("No active remote guardians selected.")
            )
        }

        try {
            val functions = FirebaseFunctions.getInstance()
            val payload = mutableMapOf<String, Any>(
                "idempotencyKey" to idempotencyKey,
                "recipientGuardianUids" to activeRecipients,
                "messageText" to messageText
            )

            if (includeLocation && location != null && !location.isStale) {
                payload["location"] = mapOf(
                    "latitude" to location.latitude,
                    "longitude" to location.longitude,
                    "accuracyMeters" to location.accuracyMeters
                )
            }

            val callResult = functions.getHttpsCallable("dispatchRemoteSos").call(payload).await()
            val data = callResult.data as? Map<*, *>
                ?: return@withContext Result.failure(IllegalStateException("Invalid server response."))

            val alertId = data["alertId"] as? String ?: ""
            val isIdempotent = data["isIdempotentReplay"] as? Boolean ?: false
            val recipientsCount = (data["recipientsCount"] as? Number)?.toInt() ?: 0
            val fcmAcceptedCount = (data["fcmAcceptedCount"] as? Number)?.toInt() ?: 0
            val fcmFailedCount = (data["fcmFailedCount"] as? Number)?.toInt() ?: 0
            @Suppress("UNCHECKED_CAST")
            val authorizedRecipients = (data["authorizedRecipients"] as? List<String>) ?: emptyList()

            Result.success(
                RemoteSosDispatchResult(
                    alertId = alertId,
                    isIdempotentReplay = isIdempotent,
                    recipientsCount = recipientsCount,
                    fcmAcceptedCount = fcmAcceptedCount,
                    fcmFailedCount = fcmFailedCount,
                    authorizedRecipients = authorizedRecipients
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun observeDeliveryStatuses(alertId: String): Flow<List<RecipientDeliveryInfo>> =
        callbackFlow {
            if (!isFirebaseAvailable || alertId.isBlank()) {
                trySend(emptyList())
                awaitClose { }
                return@callbackFlow
            }

            val firestore = FirebaseFirestore.getInstance()
            val registration: ListenerRegistration = firestore
                .collection("alerts")
                .document(alertId)
                .collection("delivery_status")
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        trySend(emptyList())
                        return@addSnapshotListener
                    }

                    val statuses = snapshot?.documents?.mapNotNull { doc ->
                        val guardianUid = doc.getString("guardianUid") ?: doc.id
                        val rawStatus = doc.getString("status") ?: "UNKNOWN"
                        val deliveryState = try {
                            RemoteAlertDeliveryState.valueOf(rawStatus)
                        } catch (_: Exception) {
                            RemoteAlertDeliveryState.UNKNOWN
                        }
                        val failureReason = doc.getString("failureReason")
                        val updatedTimestamp = doc.getTimestamp("updatedAt")
                        val updatedAt = updatedTimestamp?.toDate()?.time ?: System.currentTimeMillis()

                        RecipientDeliveryInfo(
                            guardianUid = guardianUid,
                            guardianName = "Guardian",
                            status = deliveryState,
                            updatedAtMillis = updatedAt,
                            failureReason = failureReason
                        )
                    } ?: emptyList()

                    trySend(statuses)
                }

            awaitClose {
                registration.remove()
            }
        }.flowOn(dispatcher)

    override suspend fun getAlertDetails(alertId: String): Result<RemoteAlertDetails> =
        withContext(dispatcher) {
            if (!isFirebaseAvailable) {
                return@withContext Result.failure(IllegalStateException("Firebase is not configured."))
            }

            try {
                val firestore = FirebaseFirestore.getInstance()
                val alertDoc = firestore.collection("alerts").document(alertId).get().await()
                if (!alertDoc.exists()) {
                    return@withContext Result.failure(NoSuchElementException("Alert not found."))
                }

                val senderUid = alertDoc.getString("senderUid") ?: ""
                val senderDisplayName = alertDoc.getString("senderDisplayName") ?: "SafeHer User"
                val messageText = alertDoc.getString("messageText") ?: "Emergency Alert"
                val hasLocation = alertDoc.getBoolean("hasLocation") ?: false
                val createdAt = alertDoc.getTimestamp("createdAt")?.toDate()?.time ?: System.currentTimeMillis()

                var lat: Double? = null
                var lng: Double? = null
                var acc: Float? = null

                if (hasLocation) {
                    try {
                        val locDoc = firestore.collection("alerts")
                            .document(alertId)
                            .collection("sensitive")
                            .document("location")
                            .get()
                            .await()

                        if (locDoc.exists()) {
                            lat = locDoc.getDouble("latitude")
                            lng = locDoc.getDouble("longitude")
                            acc = locDoc.getDouble("accuracyMeters")?.toFloat()
                        }
                    } catch (_: Exception) {
                        // Location may have expired or permission denied
                    }
                }

                val auth = FirebaseAuth.getInstance()
                val currentUid = auth.currentUser?.uid
                var isAcknowledged = false

                if (currentUid != null) {
                    val statusDoc = firestore.collection("alerts")
                        .document(alertId)
                        .collection("delivery_status")
                        .document(currentUid)
                        .get()
                        .await()

                    if (statusDoc.exists() && statusDoc.getString("status") == "ACKNOWLEDGED") {
                        isAcknowledged = true
                    }
                }

                Result.success(
                    RemoteAlertDetails(
                        alertId = alertId,
                        senderUid = senderUid,
                        senderDisplayName = senderDisplayName,
                        messageText = messageText,
                        hasLocation = hasLocation,
                        latitude = lat,
                        longitude = lng,
                        accuracyMeters = acc,
                        createdAtMillis = createdAt,
                        isAcknowledgedByMe = isAcknowledged
                    )
                )
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    override suspend fun reportAlertReceived(alertId: String): Result<Unit> = withContext(dispatcher) {
        if (!isFirebaseAvailable) return@withContext Result.success(Unit)
        try {
            val functions = FirebaseFunctions.getInstance()
            functions.getHttpsCallable("reportAlertReceived").call(mapOf("alertId" to alertId)).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun acknowledgeAlert(alertId: String, deviceModel: String): Result<Unit> =
        withContext(dispatcher) {
            if (!isFirebaseAvailable) {
                return@withContext Result.failure(IllegalStateException("Firebase is not configured."))
            }

            try {
                val functions = FirebaseFunctions.getInstance()
                val payload = mapOf("alertId" to alertId, "deviceModel" to deviceModel)
                functions.getHttpsCallable("acknowledgeAlert").call(payload).await()
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
}
