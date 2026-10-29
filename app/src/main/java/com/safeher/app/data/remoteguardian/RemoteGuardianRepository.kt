package com.safeher.app.data.remoteguardian

import kotlinx.coroutines.flow.Flow

interface RemoteGuardianRepository {
    fun getRemoteGuardiansFlow(): Flow<List<RemoteGuardian>>

    /**
     * Observes users who have added the current authenticated user as their guardian.
     * Used by the Guardian Dashboard to display the genuine protected circle.
     */
    fun getGuardedUsersFlow(): Flow<List<RemoteGuardian>>

    suspend fun createInvitation(): Result<GuardianInvite>

    suspend fun acceptInvitation(code: String): Result<RemoteGuardian>

    suspend fun revokeRelationship(relationshipId: String): Result<Unit>

    suspend fun refreshRemoteGuardians(): Result<List<RemoteGuardian>>

    /** Pending invitations owned by the current user (Firestore-only protocol). */
    suspend fun listPendingInvitations(): Result<List<GuardianInvite>> = Result.success(emptyList())

    /** Owner revoke of an unused pending invitation. */
    suspend fun revokeInvitation(inviteId: String): Result<Unit> =
        Result.failure(UnsupportedOperationException("Invitation revoke is not supported."))
}
