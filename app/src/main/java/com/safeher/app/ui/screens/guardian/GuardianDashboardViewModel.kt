package com.safeher.app.ui.screens.guardian

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.safeher.app.data.auth.AuthRepository
import com.safeher.app.data.auth.AuthUser
import com.safeher.app.data.demo.DemoModeManager
import com.safeher.app.data.remoteguardian.RemoteGuardian
import com.safeher.app.data.remoteguardian.RemoteGuardianRepository
import com.safeher.app.data.remotesos.RemoteAlertDetails
import com.safeher.app.data.remotesos.RemoteSosRepository
import com.safeher.app.data.session.UserSessionManager
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class GuardianTab {
    HOME,
    PEOPLE,
    JOURNEYS,
    ALERTS,
    ACCOUNT
}

data class GuardianDashboardUiState(
    val selectedTab: GuardianTab = GuardianTab.HOME,
    val currentUser: AuthUser? = null,
    val guardedUsers: List<RemoteGuardian> = emptyList(),
    val alerts: List<RemoteAlertDetails> = emptyList(),
    val isLoadingGuardedUsers: Boolean = false,
    val isLoadingAlerts: Boolean = false,

    // Invitation / Connect Dialog State
    val showConnectDialog: Boolean = false,
    val inviteCodeInput: String = "",
    val isConnecting: Boolean = false,
    val connectError: String? = null,
    val connectSuccess: Boolean = false,

    // Relationship Revocation State
    val revokingRelationship: RemoteGuardian? = null,
    val isRevoking: Boolean = false,

    // Viewing Alert Detail State
    val selectedAlertId: String? = null,

    // Account Actions
    val isSendingVerification: Boolean = false,
    val verificationSent: Boolean = false,

    // Messages
    val infoMessage: String? = null,
    val errorMessage: String? = null,

    val isDemoMode: Boolean = false
)

class GuardianDashboardViewModel(
    private val authRepository: AuthRepository,
    private val remoteGuardianRepository: RemoteGuardianRepository,
    private val remoteSosRepository: RemoteSosRepository,
    private val sessionManager: UserSessionManager? = null,
    private val demoModeManager: DemoModeManager? = null,
    coroutineScope: CoroutineScope? = null,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Main
) : ViewModel() {

    private val scope: CoroutineScope = coroutineScope ?: viewModelScope

    private val _uiState = MutableStateFlow(
        GuardianDashboardUiState(
            currentUser = authRepository.getCurrentUser()
        )
    )
    val uiState: StateFlow<GuardianDashboardUiState> = _uiState.asStateFlow()

    init {
        // Observe demo mode if available
        if (com.safeher.app.BuildConfig.IS_DEMO_DEFAULT) {
            demoModeManager?.isDemoMode
                ?.onEach { isDemo -> _uiState.update { it.copy(isDemoMode = isDemo) } }
                ?.launchIn(scope)
        }

        // Observe current authenticated user
        authRepository.authState
            .onEach { auth ->
                if (auth is com.safeher.app.data.auth.AuthState.Authenticated) {
                    _uiState.update { it.copy(currentUser = auth.user) }
                } else if (auth is com.safeher.app.data.auth.AuthState.Unauthenticated) {
                    _uiState.update { it.copy(currentUser = null) }
                }
            }
            .launchIn(scope)

        // Observe protected people genuinely linked to this guardian
        _uiState.update { it.copy(isLoadingGuardedUsers = true) }
        remoteGuardianRepository.getGuardedUsersFlow()
            .onEach { users ->
                _uiState.update {
                    it.copy(
                        guardedUsers = users,
                        isLoadingGuardedUsers = false
                    )
                }
            }
            .launchIn(scope)

        // Observe real SOS alerts authorized for this guardian
        _uiState.update { it.copy(isLoadingAlerts = true) }
        remoteSosRepository.getAuthorizedAlertsFlow()
            .onEach { alerts ->
                _uiState.update {
                    it.copy(
                        alerts = alerts,
                        isLoadingAlerts = false
                    )
                }
            }
            .launchIn(scope)
    }

    fun selectTab(tab: GuardianTab) {
        _uiState.update { it.copy(selectedTab = tab, selectedAlertId = null) }
    }

    fun selectAlert(alertId: String?) {
        _uiState.update { it.copy(selectedAlertId = alertId) }
    }

    fun openConnectDialog() {
        _uiState.update {
            it.copy(
                showConnectDialog = true,
                inviteCodeInput = "",
                connectError = null,
                connectSuccess = false
            )
        }
    }

    fun dismissConnectDialog() {
        _uiState.update {
            it.copy(
                showConnectDialog = false,
                connectError = null,
                connectSuccess = false
            )
        }
    }

    fun updateInviteCode(code: String) {
        val sanitized = code.uppercase()
            .map { if (it.isWhitespace()) '-' else it }
            .filter { it.isLetterOrDigit() || it == '-' }
            .joinToString("")
            .take(48)
        _uiState.update { it.copy(inviteCodeInput = sanitized, connectError = null) }
    }

    fun submitConnectCode() {
        val state = _uiState.value
        if (state.isConnecting) return

        val code = state.inviteCodeInput.trim().uppercase()
        if (code.length < 10) {
            _uiState.update {
                it.copy(connectError = "Enter the full invitation phrase (example: BLUE-TIGER-42-MAPLE).")
            }
            return
        }

        _uiState.update { it.copy(isConnecting = true, connectError = null) }
        scope.launch {
            try {
                val result = remoteGuardianRepository.acceptInvitation(code)
                if (result.isSuccess) {
                    _uiState.update {
                        it.copy(
                            isConnecting = false,
                            connectSuccess = true,
                            connectError = null,
                            infoMessage = "Successfully connected with SafeHer user."
                        )
                    }
                } else {
                    val msg = result.exceptionOrNull()?.message ?: ""
                    val friendlyMsg = mapConnectionErrorMessage(msg)
                    _uiState.update {
                        it.copy(
                            isConnecting = false,
                            connectError = friendlyMsg
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isConnecting = false,
                        connectError = mapConnectionErrorMessage(e.message ?: "")
                    )
                }
            }
        }
    }

    fun promptRevokeRelationship(relationship: RemoteGuardian) {
        _uiState.update { it.copy(revokingRelationship = relationship) }
    }

    fun dismissRevokePrompt() {
        _uiState.update { it.copy(revokingRelationship = null) }
    }

    fun confirmRevokeRelationship() {
        val relationship = _uiState.value.revokingRelationship ?: return
        _uiState.update { it.copy(isRevoking = true, errorMessage = null) }

        scope.launch {
            try {
                val result = remoteGuardianRepository.revokeRelationship(relationship.relationshipId)
                if (result.isSuccess) {
                    _uiState.update {
                        it.copy(
                            isRevoking = false,
                            revokingRelationship = null,
                            infoMessage = "Connection with ${relationship.guardianDisplayName} revoked."
                        )
                    }
                } else {
                    val err = result.exceptionOrNull()?.localizedMessage ?: "Failed to revoke connection."
                    _uiState.update {
                        it.copy(
                            isRevoking = false,
                            errorMessage = err
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isRevoking = false,
                        errorMessage = e.localizedMessage ?: "Failed to revoke connection."
                    )
                }
            }
        }
    }

    fun sendEmailVerification() {
        if (_uiState.value.isSendingVerification) return
        _uiState.update { it.copy(isSendingVerification = true, errorMessage = null) }
        scope.launch {
            val res = authRepository.sendEmailVerification()
            res.fold(
                onSuccess = {
                    _uiState.update {
                        it.copy(
                            isSendingVerification = false,
                            verificationSent = true,
                            infoMessage = "Verification email sent. Please check your inbox."
                        )
                    }
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(
                            isSendingVerification = false,
                            errorMessage = err.localizedMessage ?: "Failed to send verification email."
                        )
                    }
                }
            )
        }
    }

    fun clearMessages() {
        _uiState.update { it.copy(infoMessage = null, errorMessage = null) }
    }

    fun signOut() {
        scope.launch {
            authRepository.signOut()
        }
    }

    private fun mapConnectionErrorMessage(msg: String): String {
        return when {
            msg.contains("Permission denied", ignoreCase = true) ->
                "Could not claim this invitation. It may be invalid, expired, already used, or local security rules may not allow the claim yet."
            msg.contains("ALREADY_CONNECTED", ignoreCase = true) || msg.contains("already exists", ignoreCase = true) ->
                "You are already connected with this user."
            msg.contains("has expired", ignoreCase = true) ->
                "This invitation has expired. Ask the user for a new phrase."
            msg.contains("already been used", ignoreCase = true) || msg.contains("revoked", ignoreCase = true) ->
                "This invitation has already been used or was revoked."
            msg.contains("SELF_INVITE", ignoreCase = true) || msg.contains("your own", ignoreCase = true) ->
                "You cannot connect to yourself as a guardian."
            msg.contains("not found", ignoreCase = true) || msg.contains("Invalid invitation", ignoreCase = true) ->
                "Invitation not found. Check the phrase and try again."
            msg.isNotBlank() -> msg
            else -> "Failed to connect. Verify the invitation phrase and try again."
        }
    }
}
