package com.safeher.app.data.remoteguardian

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class FirebaseRemoteGuardianRepository(
    private val context: Context,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) : RemoteGuardianRepository {

    private val isFirebaseAvailable: Boolean
        get() = try {
            FirebaseApp.getApps(context).isNotEmpty()
        } catch (_: Exception) {
            false
        }

    override fun getRemoteGuardiansFlow(): Flow<List<RemoteGuardian>> = callbackFlow {
        if (!isFirebaseAvailable) {
            trySend(emptyList())
            awaitClose { }
            return@callbackFlow
        }

        val auth = FirebaseAuth.getInstance()
        val currentUid = auth.currentUser?.uid
        if (currentUid == null) {
            trySend(emptyList())
            awaitClose { }
            return@callbackFlow
        }

        val firestore = FirebaseFirestore.getInstance()
        // Listen to active relationships where current user is the sender (they are protected)
        val registration: ListenerRegistration = firestore.collection("relationships")
            .whereEqualTo("senderUid", currentUid)
            .whereEqualTo("status", "ACTIVE")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }

                val guardians = snapshot?.documents?.mapNotNull { doc ->
                    val guardianUid = doc.getString("guardianUid") ?: return@mapNotNull null
                    val name = doc.getString("guardianDisplayName") ?: "SafeHer Guardian"
                    val email = doc.getString("guardianEmail") ?: ""
                    val status = doc.getString("status") ?: "ACTIVE"
                    val createdTimestamp = doc.getTimestamp("createdAt")
                    val createdAt = createdTimestamp?.toDate()?.time ?: System.currentTimeMillis()

                    RemoteGuardian(
                        relationshipId = doc.id,
                        guardianUid = guardianUid,
                        guardianDisplayName = name,
                        guardianEmail = email,
                        status = status,
                        createdAtMillis = createdAt
                    )
                } ?: emptyList()

                trySend(guardians)
            }

        awaitClose {
            registration.remove()
        }
    }.flowOn(dispatcher)

    override suspend fun createInvitation(): Result<GuardianInvite> = withContext(dispatcher) {
        if (!isFirebaseAvailable) {
            return@withContext Result.failure(
                IllegalStateException("Firebase backend is not configured on this device.")
            )
        }

        try {
            val functions = FirebaseFunctions.getInstance()
            val result = functions.getHttpsCallable("createGuardianInvite").call().await()
            val data = result.data as? Map<*, *>
                ?: return@withContext Result.failure(IllegalStateException("Invalid server response."))

            val inviteId = data["inviteId"] as? String ?: ""
            val code = data["code"] as? String ?: ""
            val expiresAt = (data["expiresAtMillis"] as? Number)?.toLong() ?: 0L

            Result.success(GuardianInvite(inviteId, code, expiresAt))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun acceptInvitation(code: String): Result<RemoteGuardian> = withContext(dispatcher) {
        if (!isFirebaseAvailable) {
            return@withContext Result.failure(
                IllegalStateException("Firebase backend is not configured on this device.")
            )
        }

        try {
            val functions = FirebaseFunctions.getInstance()
            val payload = mapOf("code" to code.trim().uppercase())
            val result = functions.getHttpsCallable("acceptGuardianInvite").call(payload).await()
            val data = result.data as? Map<*, *>
                ?: return@withContext Result.failure(IllegalStateException("Invalid server response."))

            val relationshipId = data["relationshipId"] as? String ?: ""
            val senderUid = data["senderUid"] as? String ?: ""
            val senderDisplayName = data["senderDisplayName"] as? String ?: "SafeHer User"

            Result.success(
                RemoteGuardian(
                    relationshipId = relationshipId,
                    guardianUid = senderUid,
                    guardianDisplayName = senderDisplayName,
                    guardianEmail = "",
                    status = "ACTIVE"
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun revokeRelationship(relationshipId: String): Result<Unit> = withContext(dispatcher) {
        if (!isFirebaseAvailable) {
            return@withContext Result.failure(
                IllegalStateException("Firebase backend is not configured on this device.")
            )
        }

        try {
            val functions = FirebaseFunctions.getInstance()
            val payload = mapOf("relationshipId" to relationshipId)
            functions.getHttpsCallable("revokeGuardianRelationship").call(payload).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun refreshRemoteGuardians(): Result<List<RemoteGuardian>> = withContext(dispatcher) {
        if (!isFirebaseAvailable) {
            return@withContext Result.success(emptyList())
        }

        val auth = FirebaseAuth.getInstance()
        val currentUid = auth.currentUser?.uid ?: return@withContext Result.success(emptyList())

        try {
            val firestore = FirebaseFirestore.getInstance()
            val snapshot = firestore.collection("relationships")
                .whereEqualTo("senderUid", currentUid)
                .whereEqualTo("status", "ACTIVE")
                .get()
                .await()

            val list = snapshot.documents.mapNotNull { doc ->
                val guardianUid = doc.getString("guardianUid") ?: return@mapNotNull null
                val name = doc.getString("guardianDisplayName") ?: "SafeHer Guardian"
                val email = doc.getString("guardianEmail") ?: ""
                val status = doc.getString("status") ?: "ACTIVE"
                val createdTimestamp = doc.getTimestamp("createdAt")
                val createdAt = createdTimestamp?.toDate()?.time ?: System.currentTimeMillis()

                RemoteGuardian(
                    relationshipId = doc.id,
                    guardianUid = guardianUid,
                    guardianDisplayName = name,
                    guardianEmail = email,
                    status = status,
                    createdAtMillis = createdAt
                )
            }
            Result.success(list)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
