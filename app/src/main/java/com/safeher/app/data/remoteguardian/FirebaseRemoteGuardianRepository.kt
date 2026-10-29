package com.safeher.app.data.remoteguardian

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.Date

/**
 * Firestore-only remote guardian invitations (Firebase Spark compatible).
 *
 * Does not call Cloud Functions for create/accept/revoke. Phrase document IDs are
 * SHA-256 of the normalized phrase; security relies on rules + phrase entropy + TTL.
 * Remote SOS / alerts remain unchanged (still Functions-backed).
 */
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
        val registration: ListenerRegistration = firestore.collection(COL_RELATIONSHIPS)
            .whereEqualTo("senderUid", currentUid)
            .whereEqualTo("status", "ACTIVE")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                trySend(snapshot?.documents?.mapNotNull { it.toRemoteGuardianAsGuardian() } ?: emptyList())
            }

        awaitClose { registration.remove() }
    }.flowOn(dispatcher)

    override fun getGuardedUsersFlow(): Flow<List<RemoteGuardian>> = callbackFlow {
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
        val registration: ListenerRegistration = firestore.collection(COL_RELATIONSHIPS)
            .whereEqualTo("guardianUid", currentUid)
            .whereEqualTo("status", "ACTIVE")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                trySend(snapshot?.documents?.mapNotNull { it.toRemoteGuardianAsWard() } ?: emptyList())
            }

        awaitClose { registration.remove() }
    }.flowOn(dispatcher)

    override suspend fun createInvitation(): Result<GuardianInvite> = withContext(dispatcher) {
        if (!isFirebaseAvailable) {
            return@withContext Result.failure(
                IllegalStateException("Firebase backend is not configured on this device.")
            )
        }

        val auth = FirebaseAuth.getInstance()
        val user = auth.currentUser
            ?: return@withContext Result.failure(IllegalStateException("Sign in required to create an invitation."))

        try {
            val firestore = FirebaseFirestore.getInstance()
            val now = Timestamp.now()
            val expiresAt = Timestamp(Date(InvitePhraseGenerator.expiresAtMillis(now.toDate().time)))

            var created: GuardianInvite? = null
            var lastError: Exception? = null
            repeat(8) {
                if (created != null) return@repeat
                val candidate = InvitePhraseGenerator.generatePhrase()
                val inviteId = InvitePhraseGenerator.documentIdForPhrase(candidate)
                val ref = firestore.collection(COL_INVITATIONS).document(inviteId)
                val existing = try {
                    ref.get().await()
                } catch (e: Exception) {
                    lastError = e
                    return@repeat
                }
                if (existing.exists()) {
                    return@repeat
                }

                val data = hashMapOf<String, Any>(
                    "id" to inviteId,
                    "code" to candidate,
                    "senderUid" to user.uid,
                    "senderDisplayName" to (user.displayName?.ifBlank { null } ?: "SafeHer User"),
                    "senderEmail" to (user.email ?: ""),
                    "status" to "PENDING",
                    "createdAt" to FieldValue.serverTimestamp(),
                    "expiresAt" to expiresAt
                )

                try {
                    // Rules allow create only when the document does not already exist.
                    ref.set(data).await()
                    val written = ref.get().await()
                    val expiresMillis = written.getTimestamp("expiresAt")?.toDate()?.time
                        ?: expiresAt.toDate().time
                    created = GuardianInvite(
                        inviteId = inviteId,
                        code = candidate,
                        expiresAtMillis = expiresMillis
                    )
                } catch (e: FirebaseFirestoreException) {
                    if (e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                        throw e
                    }
                    lastError = e
                } catch (e: Exception) {
                    lastError = e
                }
            }

            created?.let { return@withContext Result.success(it) }
            Result.failure(
                lastError ?: IllegalStateException("Could not create invitation. Please try again.")
            )
        } catch (e: Exception) {
            Result.failure(mapFirestoreException(e, "Could not create invitation."))
        }
    }

    override suspend fun acceptInvitation(code: String): Result<RemoteGuardian> = withContext(dispatcher) {
        if (!isFirebaseAvailable) {
            return@withContext Result.failure(
                IllegalStateException("Firebase backend is not configured on this device.")
            )
        }

        val auth = FirebaseAuth.getInstance()
        val user = auth.currentUser
            ?: return@withContext Result.failure(IllegalStateException("Sign in required to accept an invitation."))

        val normalized = InvitePhraseGenerator.normalize(code)
        if (!InvitePhraseGenerator.isValidFormat(normalized)) {
            return@withContext Result.failure(
                IllegalArgumentException(
                    "Enter the full invitation phrase (example format: BLUE-TIGER-42-MAPLE)."
                )
            )
        }

        try {
            val firestore = FirebaseFirestore.getInstance()
            val inviteId = InvitePhraseGenerator.documentIdForPhrase(normalized)
            val inviteRef = firestore.collection(COL_INVITATIONS).document(inviteId)

            val result = firestore.runTransaction { transaction ->
                val inviteSnap = transaction.get(inviteRef)
                if (!inviteSnap.exists()) {
                    throw InviteProtocolException("Invitation not found. Check the phrase and try again.")
                }

                val status = inviteSnap.getString("status") ?: ""
                val senderUid = inviteSnap.getString("senderUid")
                    ?: throw InviteProtocolException("Invitation is incomplete.")
                val senderDisplayName = inviteSnap.getString("senderDisplayName") ?: "SafeHer User"
                val senderEmail = inviteSnap.getString("senderEmail") ?: ""
                val expiresAt = inviteSnap.getTimestamp("expiresAt")
                    ?: throw InviteProtocolException("Invitation is incomplete.")

                if (status != "PENDING") {
                    throw InviteProtocolException(
                        "This invitation has already been used or revoked."
                    )
                }
                if (expiresAt.toDate().time <= System.currentTimeMillis()) {
                    throw InviteProtocolException("This invitation has expired.")
                }
                if (senderUid == user.uid) {
                    throw InviteProtocolException("You cannot accept your own invitation.")
                }

                val relationshipId = "${senderUid}_${user.uid}"
                val relationshipRef = firestore.collection(COL_RELATIONSHIPS).document(relationshipId)
                val existingRel = transaction.get(relationshipRef)
                if (existingRel.exists() && existingRel.getString("status") == "ACTIVE") {
                    throw InviteProtocolException(
                        "An active guardian relationship already exists with this user."
                    )
                }

                transaction.update(
                    inviteRef,
                    mapOf(
                        "status" to "ACCEPTED",
                        "acceptedByUid" to user.uid,
                        "acceptedAt" to FieldValue.serverTimestamp()
                    )
                )

                val relationshipData = hashMapOf<String, Any>(
                    "id" to relationshipId,
                    "senderUid" to senderUid,
                    "senderDisplayName" to senderDisplayName,
                    "senderEmail" to senderEmail,
                    "guardianUid" to user.uid,
                    "guardianDisplayName" to (user.displayName?.ifBlank { null } ?: "SafeHer Guardian"),
                    "guardianEmail" to (user.email ?: ""),
                    "status" to "ACTIVE",
                    "createdAt" to FieldValue.serverTimestamp(),
                    "sourceInviteId" to inviteId
                )
                transaction.set(relationshipRef, relationshipData)

                RemoteGuardian(
                    relationshipId = relationshipId,
                    guardianUid = senderUid,
                    guardianDisplayName = senderDisplayName,
                    guardianEmail = senderEmail,
                    status = "ACTIVE"
                )
            }.await()

            Result.success(result)
        } catch (e: Exception) {
            Result.failure(mapFirestoreException(e, "Could not accept invitation."))
        }
    }

    override suspend fun revokeRelationship(relationshipId: String): Result<Unit> = withContext(dispatcher) {
        if (!isFirebaseAvailable) {
            return@withContext Result.failure(
                IllegalStateException("Firebase backend is not configured on this device.")
            )
        }

        val auth = FirebaseAuth.getInstance()
        if (auth.currentUser == null) {
            return@withContext Result.failure(IllegalStateException("Sign in required to revoke a relationship."))
        }

        try {
            val firestore = FirebaseFirestore.getInstance()
            val ref = firestore.collection(COL_RELATIONSHIPS).document(relationshipId)
            val snap = ref.get().await()
            if (!snap.exists()) {
                return@withContext Result.failure(IllegalStateException("Relationship not found."))
            }
            if (snap.getString("status") != "ACTIVE") {
                return@withContext Result.failure(IllegalStateException("Relationship is not active."))
            }

            ref.update(
                mapOf(
                    "status" to "REVOKED",
                    "revokedAt" to FieldValue.serverTimestamp(),
                    "revokedBy" to auth.currentUser!!.uid
                )
            ).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(mapFirestoreException(e, "Could not revoke relationship."))
        }
    }

    override suspend fun listPendingInvitations(): Result<List<GuardianInvite>> = withContext(dispatcher) {
        if (!isFirebaseAvailable) {
            return@withContext Result.success(emptyList())
        }
        val uid = FirebaseAuth.getInstance().currentUser?.uid
            ?: return@withContext Result.success(emptyList())

        try {
            val firestore = FirebaseFirestore.getInstance()
            val snapshot = firestore.collection(COL_INVITATIONS)
                .whereEqualTo("senderUid", uid)
                .whereEqualTo("status", "PENDING")
                .get()
                .await()

            val now = System.currentTimeMillis()
            val list = snapshot.documents.mapNotNull { doc ->
                val code = doc.getString("code") ?: return@mapNotNull null
                val expires = doc.getTimestamp("expiresAt")?.toDate()?.time ?: return@mapNotNull null
                if (expires <= now) return@mapNotNull null
                GuardianInvite(
                    inviteId = doc.id,
                    code = code,
                    expiresAtMillis = expires
                )
            }
            Result.success(list)
        } catch (e: Exception) {
            Result.failure(mapFirestoreException(e, "Could not load pending invitations."))
        }
    }

    override suspend fun revokeInvitation(inviteId: String): Result<Unit> = withContext(dispatcher) {
        if (!isFirebaseAvailable) {
            return@withContext Result.failure(
                IllegalStateException("Firebase backend is not configured on this device.")
            )
        }
        val uid = FirebaseAuth.getInstance().currentUser?.uid
            ?: return@withContext Result.failure(IllegalStateException("Sign in required."))

        try {
            val firestore = FirebaseFirestore.getInstance()
            val ref = firestore.collection(COL_INVITATIONS).document(inviteId)
            val snap = ref.get().await()
            if (!snap.exists()) {
                return@withContext Result.failure(IllegalStateException("Invitation not found."))
            }
            if (snap.getString("senderUid") != uid) {
                return@withContext Result.failure(IllegalStateException("You can only revoke your own invitations."))
            }
            if (snap.getString("status") != "PENDING") {
                return@withContext Result.failure(IllegalStateException("Only unused invitations can be revoked."))
            }

            ref.update(
                mapOf(
                    "status" to "REVOKED",
                    "revokedAt" to FieldValue.serverTimestamp(),
                    "revokedBy" to uid
                )
            ).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(mapFirestoreException(e, "Could not revoke invitation."))
        }
    }

    override suspend fun refreshRemoteGuardians(): Result<List<RemoteGuardian>> = withContext(dispatcher) {
        if (!isFirebaseAvailable) {
            return@withContext Result.success(emptyList())
        }

        val currentUid = FirebaseAuth.getInstance().currentUser?.uid
            ?: return@withContext Result.success(emptyList())

        try {
            val firestore = FirebaseFirestore.getInstance()
            val snapshot = firestore.collection(COL_RELATIONSHIPS)
                .whereEqualTo("senderUid", currentUid)
                .whereEqualTo("status", "ACTIVE")
                .get()
                .await()

            Result.success(snapshot.documents.mapNotNull { it.toRemoteGuardianAsGuardian() })
        } catch (e: Exception) {
            Result.failure(mapFirestoreException(e, "Could not refresh remote guardians."))
        }
    }

    private fun com.google.firebase.firestore.DocumentSnapshot.toRemoteGuardianAsGuardian(): RemoteGuardian? {
        val guardianUid = getString("guardianUid") ?: return null
        val name = getString("guardianDisplayName") ?: "SafeHer Guardian"
        val email = getString("guardianEmail") ?: ""
        val status = getString("status") ?: "ACTIVE"
        val createdAt = getTimestamp("createdAt")?.toDate()?.time ?: System.currentTimeMillis()
        return RemoteGuardian(
            relationshipId = id,
            guardianUid = guardianUid,
            guardianDisplayName = name,
            guardianEmail = email,
            status = status,
            createdAtMillis = createdAt
        )
    }

    private fun com.google.firebase.firestore.DocumentSnapshot.toRemoteGuardianAsWard(): RemoteGuardian? {
        val senderUid = getString("senderUid") ?: return null
        val name = getString("senderDisplayName") ?: "SafeHer User"
        val email = getString("senderEmail") ?: ""
        val status = getString("status") ?: "ACTIVE"
        val createdAt = getTimestamp("createdAt")?.toDate()?.time ?: System.currentTimeMillis()
        return RemoteGuardian(
            relationshipId = id,
            guardianUid = senderUid,
            guardianDisplayName = name,
            guardianEmail = email,
            status = status,
            createdAtMillis = createdAt
        )
    }

    private fun mapFirestoreException(e: Exception, defaultMessage: String): Exception {
        when (e) {
            is InviteProtocolException -> return e
            is FirebaseFirestoreException -> {
                return when (e.code) {
                    FirebaseFirestoreException.Code.PERMISSION_DENIED ->
                        IllegalStateException(
                            "Permission denied. The invitation may be invalid, expired, already used, " +
                                "or local Firestore security rules may not yet allow this operation."
                        )
                    FirebaseFirestoreException.Code.UNAVAILABLE ->
                        IllegalStateException("Firestore is temporarily unavailable. Check your connection and retry.")
                    FirebaseFirestoreException.Code.NOT_FOUND ->
                        IllegalStateException("Invitation not found. Check the phrase and try again.")
                    else -> IllegalStateException(e.localizedMessage ?: defaultMessage)
                }
            }
        }
        val cause = e.cause
        if (cause is InviteProtocolException) return cause
        // Transaction failures often wrap the protocol message
        val msg = e.message.orEmpty()
        if (msg.contains("Invitation", ignoreCase = true) ||
            msg.contains("relationship", ignoreCase = true) ||
            msg.contains("expired", ignoreCase = true)
        ) {
            return IllegalStateException(e.localizedMessage ?: defaultMessage)
        }
        return IllegalStateException(e.localizedMessage ?: defaultMessage)
    }

    private class InviteProtocolException(message: String) : IllegalStateException(message)

    companion object {
        private const val COL_INVITATIONS = "invitations"
        private const val COL_RELATIONSHIPS = "relationships"
    }
}
