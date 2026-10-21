package com.safeher.app.ui.screens.account

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.safeher.app.data.auth.AuthRepository
import com.safeher.app.data.auth.AuthResult
import com.safeher.app.data.auth.AuthState
import com.safeher.app.data.auth.AuthUser
import com.safeher.app.data.remoteguardian.GuardianInvite
import com.safeher.app.data.remoteguardian.RemoteGuardian
import com.safeher.app.data.remoteguardian.RemoteGuardianRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AccountUiState(
    val authState: AuthState = AuthState.Loading,
    val isSignInMode: Boolean = true,
    val emailInput: String = "",
    val passwordInput: String = "",
    val displayNameInput: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val infoMessage: String? = null,
    val remoteGuardians: List<RemoteGuardian> = emptyList(),
    val generatedInvite: GuardianInvite? = null,
    val showInviteDialog: Boolean = false,
    val showAcceptInviteDialog: Boolean = false,
    val inviteCodeInput: String = "",
    val isAcceptingInvite: Boolean = false,
    val revokingRelationshipId: String? = null,
    val isDemoMode: Boolean = false,
    val activeDemoProfile: com.safeher.app.data.demo.DemoProfile = com.safeher.app.data.demo.DemoProfile.USER
)

class AccountViewModel(
    private val authRepository: AuthRepository,
    private val remoteGuardianRepository: RemoteGuardianRepository,
    private val demoModeManager: com.safeher.app.data.demo.DemoModeManager? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(AccountUiState())
    val uiState: StateFlow<AccountUiState> = _uiState.asStateFlow()

    init {
        demoModeManager?.let { dmm ->
            dmm.isDemoMode
                .onEach { isDemo -> _uiState.update { it.copy(isDemoMode = isDemo) } }
                .launchIn(viewModelScope)
            dmm.activeProfile
                .onEach { profile -> _uiState.update { it.copy(activeDemoProfile = profile) } }
                .launchIn(viewModelScope)
        }

        authRepository.authState
            .onEach { auth ->
                _uiState.update { it.copy(authState = auth, isLoading = false) }
                if (auth is AuthState.Authenticated) {
                    observeRemoteGuardians()
                }
            }
            .launchIn(viewModelScope)
    }

    private fun observeRemoteGuardians() {
        remoteGuardianRepository.getRemoteGuardiansFlow()
            .onEach { guardians ->
                _uiState.update { it.copy(remoteGuardians = guardians) }
            }
            .launchIn(viewModelScope)
    }

    fun toggleAuthMode() {
        _uiState.update {
            it.copy(
                isSignInMode = !it.isSignInMode,
                errorMessage = null,
                infoMessage = null
            )
        }
    }

    fun updateEmail(value: String) = _uiState.update { it.copy(emailInput = value, errorMessage = null) }
    fun updatePassword(value: String) = _uiState.update { it.copy(passwordInput = value, errorMessage = null) }
    fun updateDisplayName(value: String) = _uiState.update { it.copy(displayNameInput = value, errorMessage = null) }
    fun updateInviteCode(value: String) = _uiState.update { it.copy(inviteCodeInput = value.uppercase(), errorMessage = null) }

    fun clearMessages() = _uiState.update { it.copy(errorMessage = null, infoMessage = null) }

    fun signInWithEmail() {
        val state = _uiState.value
        if (state.emailInput.isBlank() || state.passwordInput.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Email and password are required.") }
            return
        }

        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            when (val res = authRepository.signInWithEmail(state.emailInput, state.passwordInput)) {
                is AuthResult.Success -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            emailInput = "",
                            passwordInput = "",
                            infoMessage = "Welcome back, ${res.user.displayLabel}!"
                        )
                    }
                }
                is AuthResult.Error -> {
                    _uiState.update { it.copy(isLoading = false, errorMessage = res.message) }
                }
                AuthResult.Cancelled -> {
                    _uiState.update { it.copy(isLoading = false) }
                }
            }
        }
    }

    fun signUpWithEmail() {
        val state = _uiState.value
        if (state.emailInput.isBlank() || state.passwordInput.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Email and password are required.") }
            return
        }

        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            when (val res = authRepository.signUpWithEmail(state.emailInput, state.passwordInput, state.displayNameInput)) {
                is AuthResult.Success -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            emailInput = "",
                            passwordInput = "",
                            displayNameInput = "",
                            infoMessage = "Account created successfully!"
                        )
                    }
                }
                is AuthResult.Error -> {
                    _uiState.update { it.copy(isLoading = false, errorMessage = res.message) }
                }
                AuthResult.Cancelled -> {
                    _uiState.update { it.copy(isLoading = false) }
                }
            }
        }
    }

    fun signInWithGoogle(context: Context, webClientId: String) {
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            when (val res = authRepository.signInWithGoogle(context, webClientId)) {
                is AuthResult.Success -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            infoMessage = "Signed in as ${res.user.displayLabel}"
                        )
                    }
                }
                is AuthResult.Error -> {
                    _uiState.update { it.copy(isLoading = false, errorMessage = res.message) }
                }
                AuthResult.Cancelled -> {
                    _uiState.update { it.copy(isLoading = false) }
                }
            }
        }
    }

    fun sendPasswordReset() {
        val email = _uiState.value.emailInput.trim()
        if (email.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Please enter your email address to reset password.") }
            return
        }
        viewModelScope.launch {
            authRepository.sendPasswordResetEmail(email)
            _uiState.update {
                it.copy(infoMessage = "If an account exists for $email, a password reset email has been sent.")
            }
        }
    }

    fun sendEmailVerification() {
        viewModelScope.launch {
            val result = authRepository.sendEmailVerification()
            if (result.isSuccess) {
                _uiState.update { it.copy(infoMessage = "Verification email sent. Please check your inbox.") }
            } else {
                _uiState.update { it.copy(errorMessage = "Could not send verification email. Try again later.") }
            }
        }
    }

    fun signOut() {
        viewModelScope.launch {
            authRepository.signOut()
            _uiState.update {
                it.copy(
                    remoteGuardians = emptyList(),
                    generatedInvite = null,
                    infoMessage = "Signed out successfully."
                )
            }
        }
    }

    fun openInviteDialog() {
        _uiState.update { it.copy(showInviteDialog = true, errorMessage = null) }
        viewModelScope.launch {
            val res = remoteGuardianRepository.createInvitation()
            res.fold(
                onSuccess = { invite ->
                    _uiState.update { it.copy(generatedInvite = invite) }
                },
                onFailure = { err ->
                    _uiState.update { it.copy(errorMessage = err.localizedMessage ?: "Failed to generate invite code.") }
                }
            )
        }
    }

    fun dismissInviteDialog() {
        _uiState.update { it.copy(showInviteDialog = false, generatedInvite = null) }
    }

    fun openAcceptInviteDialog() {
        _uiState.update { it.copy(showAcceptInviteDialog = true, inviteCodeInput = "", errorMessage = null) }
    }

    fun dismissAcceptInviteDialog() {
        _uiState.update { it.copy(showAcceptInviteDialog = false, inviteCodeInput = "") }
    }

    fun submitAcceptInvite() {
        val code = _uiState.value.inviteCodeInput.trim()
        if (code.length < 6) {
            _uiState.update { it.copy(errorMessage = "Please enter a valid 8-character code.") }
            return
        }

        _uiState.update { it.copy(isAcceptingInvite = true, errorMessage = null) }
        viewModelScope.launch {
            val res = remoteGuardianRepository.acceptInvitation(code)
            res.fold(
                onSuccess = { guardian ->
                    _uiState.update {
                        it.copy(
                            isAcceptingInvite = false,
                            showAcceptInviteDialog = false,
                            inviteCodeInput = "",
                            infoMessage = "Successfully linked with ${guardian.guardianDisplayName} as remote guardian."
                        )
                    }
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(
                            isAcceptingInvite = false,
                            errorMessage = err.localizedMessage ?: "Failed to accept invite code."
                        )
                    }
                }
            )
        }
    }

    fun setDemoMode(enabled: Boolean) {
        demoModeManager?.setDemoMode(enabled)
    }

    fun switchDemoProfile(profile: com.safeher.app.data.demo.DemoProfile) {
        demoModeManager?.switchProfile(profile)
    }

    fun resetDemoData() {
        demoModeManager?.resetDemoData()
        _uiState.update { it.copy(infoMessage = "Demo data reset to defaults. Local Room database remains untouched.") }
    }

    fun revokeRelationship(relationshipId: String) {
        _uiState.update { it.copy(revokingRelationshipId = relationshipId) }
        viewModelScope.launch {
            val res = remoteGuardianRepository.revokeRelationship(relationshipId)
            _uiState.update { it.copy(revokingRelationshipId = null) }
            res.fold(
                onSuccess = {
                    _uiState.update { it.copy(infoMessage = "Remote guardian relationship revoked.") }
                },
                onFailure = { err ->
                    _uiState.update { it.copy(errorMessage = err.localizedMessage ?: "Failed to revoke relationship.") }
                }
            )
        }
    }
}
