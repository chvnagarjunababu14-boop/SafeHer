package com.safeher.app.data.demo

import com.safeher.app.data.remoteguardian.GuardianInvite
import com.safeher.app.data.remoteguardian.InvitePhraseGenerator
import com.safeher.app.data.remoteguardian.RemoteGuardian
import com.safeher.app.data.remoteguardian.RemoteGuardianRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Self-contained mock RemoteGuardianRepository implementation for SafeHer Demo Mode.
 *
 * Operates purely in-memory on-device without contacting Cloud Firestore or Cloud Functions.
 * Lets the evaluator create invitations, accept codes, view fictional linked guardians, and revoke relationships.
 */
class DemoRemoteGuardianRepository(
    private val demoModeManager: DemoModeManager,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) : RemoteGuardianRepository {

    private val scope = CoroutineScope(SupervisorJob() + dispatcher)

    private val defaultUserGuardians = listOf(
        RemoteGuardian(
            relationshipId = "demo_rel_priya",
            guardianUid = DemoProfile.GUARDIAN.uid,
            guardianDisplayName = "Demo Guardian (Priya)",
            guardianEmail = DemoProfile.GUARDIAN.email,
            status = "ACTIVE"
        )
    )

    private val defaultGuardianWards = listOf(
        RemoteGuardian(
            relationshipId = "demo_rel_user",
            guardianUid = DemoProfile.USER.uid,
            guardianDisplayName = "Demo User",
            guardianEmail = DemoProfile.USER.email,
            status = "ACTIVE"
        )
    )

    private val _guardiansFlow = MutableStateFlow<List<RemoteGuardian>>(defaultUserGuardians)
    private val _guardedUsersFlow = MutableStateFlow<List<RemoteGuardian>>(defaultGuardianWards)

    init {
        demoModeManager.registerResetListener {
            _guardiansFlow.value = defaultUserGuardians
            _guardedUsersFlow.value = defaultGuardianWards
        }
    }

    override fun getRemoteGuardiansFlow(): Flow<List<RemoteGuardian>> = _guardiansFlow.asStateFlow()

    override fun getGuardedUsersFlow(): Flow<List<RemoteGuardian>> = _guardedUsersFlow.asStateFlow()

    override suspend fun createInvitation(): Result<GuardianInvite> = withContext(dispatcher) {
        val phrase = InvitePhraseGenerator.generatePhrase()
        val invite = GuardianInvite(
            inviteId = InvitePhraseGenerator.documentIdForPhrase(phrase),
            code = phrase,
            expiresAtMillis = InvitePhraseGenerator.expiresAtMillis()
        )
        Result.success(invite)
    }

    override suspend fun acceptInvitation(code: String): Result<RemoteGuardian> = withContext(dispatcher) {
        val cleanCode = InvitePhraseGenerator.normalize(code)
        if (!InvitePhraseGenerator.isValidFormat(cleanCode) && cleanCode.length < 10) {
            return@withContext Result.failure(
                IllegalArgumentException("Enter a valid invitation phrase (example: BLUE-TIGER-42-MAPLE).")
            )
        }

        val newGuardian = RemoteGuardian(
            relationshipId = "demo_rel_ananya_${System.currentTimeMillis() % 1000}",
            guardianUid = "demo_guardian_ananya_303",
            guardianDisplayName = "Demo Guardian (Ananya)",
            guardianEmail = "ananya.guardian@safeher.local",
            status = "ACTIVE"
        )

        val updated = _guardiansFlow.value.toMutableList()
        if (updated.none { it.guardianUid == newGuardian.guardianUid }) {
            updated.add(newGuardian)
            _guardiansFlow.value = updated
        }

        Result.success(newGuardian)
    }

    override suspend fun revokeRelationship(relationshipId: String): Result<Unit> = withContext(dispatcher) {
        _guardiansFlow.value = _guardiansFlow.value.filter { it.relationshipId != relationshipId }
        _guardedUsersFlow.value = _guardedUsersFlow.value.filter { it.relationshipId != relationshipId }
        Result.success(Unit)
    }

    override suspend fun refreshRemoteGuardians(): Result<List<RemoteGuardian>> = withContext(dispatcher) {
        Result.success(_guardiansFlow.value)
    }
}
