package com.safeher.app.data.remoteguardian

import kotlinx.coroutines.flow.Flow

interface RemoteGuardianRepository {
    fun getRemoteGuardiansFlow(): Flow<List<RemoteGuardian>>

    suspend fun createInvitation(): Result<GuardianInvite>

    suspend fun acceptInvitation(code: String): Result<RemoteGuardian>

    suspend fun revokeRelationship(relationshipId: String): Result<Unit>

    suspend fun refreshRemoteGuardians(): Result<List<RemoteGuardian>>
}
