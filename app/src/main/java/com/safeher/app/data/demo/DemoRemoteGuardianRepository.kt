package com.safeher.app.data.demo

import com.safeher.app.data.remoteguardian.GuardianInvite
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

    init {
        scope.launch {
            demoModeManager.activeProfile.collect { profile ->
                _guardiansFlow.value = if (profile == DemoProfile.USER) {
                    defaultUserGuardians
                } else {
                    defaultGuardianWards
                }
            }
        }

        demoModeManager.registerResetListener {
            _guardiansFlow.value = if (demoModeManager.activeProfile.value == DemoProfile.USER) {
                defaultUserGuardians
            } else {
                defaultGuardianWards
            }
        }
    }

    override fun getRemoteGuardiansFlow(): Flow<List<RemoteGuardian>> = _guardiansFlow.asStateFlow()

    override suspend fun createInvitation(): Result<GuardianInvite> = withContext(dispatcher) {
        val randomNum = (1000..9999).random()
        val code = "DEMO$randomNum"
        val invite = GuardianInvite(
            inviteId = "demo_inv_${System.currentTimeMillis() % 10000}",
            code = code,
            expiresAtMillis = System.currentTimeMillis() + (48 * 3600 * 1000L)
        )
        Result.success(invite)
    }

    override suspend fun acceptInvitation(code: String): Result<RemoteGuardian> = withContext(dispatcher) {
        val cleanCode = code.trim().uppercase()
        if (cleanCode.length < 4) {
            return@withContext Result.failure(IllegalArgumentException("Please enter a valid invitation code."))
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
        val updated = _guardiansFlow.value.filter { it.relationshipId != relationshipId }
        _guardiansFlow.value = updated
        Result.success(Unit)
    }

    override suspend fun refreshRemoteGuardians(): Result<List<RemoteGuardian>> = withContext(dispatcher) {
        Result.success(_guardiansFlow.value)
    }
}
