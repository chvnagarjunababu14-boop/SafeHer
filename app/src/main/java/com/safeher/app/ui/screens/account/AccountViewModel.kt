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
    val isGeneratingInvite: Boolean = false,
    val inviteGenerationError: String? = null,
    val showInviteDialog: Boolean = false,
    val showAcceptInviteDialog: Boolean = false,
    val inviteCodeInput: String = "",
    val isAcceptingInvite: Boolean = false,
    val acceptInviteError: String? = null,
    val revokingRelationshipId: String? = null,
    val isDemoMode: Boolean = false
)

class AccountViewModel(
    private val authRepository: AuthRepository,
    private val remoteGuardianRepository: RemoteGuardianRepository,
    private val demoModeManager: com.safeher.app.data.demo.DemoModeManager? = null,
    private val userSessionManager: com.safeher.app.data.session.UserSessionManager? = null,
    coroutineScope: kotlinx.coroutines.CoroutineScope? = null
) : ViewModel() {

    private val scope: kotlinx.coroutines.CoroutineScope = coroutineScope ?: viewModelScope

    private val _uiState = MutableStateFlow(AccountUiState())
    val uiState: StateFlow<AccountUiState> = _uiState.asStateFlow()

    init {
        if (com.safeher.app.BuildConfig.IS_DEMO_DEFAULT) {
            demoModeManager?.let { dmm ->
                dmm.isDemoMode
                    .onEach { isDemo -> _uiState.update { it.copy(isDemoMode = isDemo) } }
                    .launchIn(scope)
            }
        } else {
            _uiState.update { it.copy(isDemoMode = false) }
        }

        authRepository.authState
            .onEach { auth ->
                _uiState.update { it.copy(authState = auth, isLoading = false) }
                if (auth is AuthState.Authenticated) {
                    observeRemoteGuardians()
                } else if (auth is AuthState.Unauthenticated) {
                    _uiState.update { it.copy(remoteGuardians = emptyList(), generatedInvite = null) }
                }
            }
            .launchIn(scope)
    }

    private fun observeRemoteGuardians() {
        remoteGuardianRepository.getRemoteGuardiansFlow()
            .onEach { guardians ->
                _uiState.update { it.copy(remoteGuardians = guardians) }
            }
            .launchIn(scope)
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
        scope.launch {
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
        scope.launch {
            when (val res = authRepository.signUpWithEmail(state.emailInput, state.passwordInput, state.displayNameInput)) {
                is AuthResult.Success -> {
                    userSessionManager?.markAsNewRegistration(res.user.uid)
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
        scope.launch {
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
        scope.launch {
            authRepository.sendPasswordResetEmail(email)
            _uiState.update {
                it.copy(infoMessage = "If an account exists for $email, a password reset email has been sent.")
            }
        }
    }

    fun sendEmailVerification() {
        scope.launch {
            val result = authRepository.sendEmailVerification()
            if (result.isSuccess) {
                _uiState.update { it.copy(infoMessage = "Verification email sent. Please check your inbox.") }
            } else {
                _uiState.update { it.copy(errorMessage = "Could not send verification email. Try again later.") }
            }
        }
    }

    fun signOut() {
        scope.launch {
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
        _uiState.update {
            it.copy(
                showInviteDialog = true,
                isGeneratingInvite = true,
                generatedInvite = null,
                inviteGenerationError = null,
                errorMessage = null
            )
        }
        generateInvite()
    }

    fun retryGenerateInvite() {
        _uiState.update {
            it.copy(
                isGeneratingInvite = true,
                generatedInvite = null,
                inviteGenerationError = null
            )
        }
        generateInvite()
    }

    private fun generateInvite() {
        scope.launch {
            val res = remoteGuardianRepository.createInvitation()
            res.fold(
                onSuccess = { invite ->
                    _uiState.update {
                        it.copy(
                            generatedInvite = invite,
                            isGeneratingInvite = false,
                            inviteGenerationError = null
                        )
                    }
                },
                onFailure = { err ->
                    val friendlyMessage = (err.localizedMessage ?: "")
                        .ifBlank { "Could not generate invitation. Please check your connection and try again." }
                    _uiState.update {
                        it.copy(
                            isGeneratingInvite = false,
                            showInviteDialog = false,
                            generatedInvite = null,
                            inviteGenerationError = friendlyMessage,
                            errorMessage = friendlyMessage
                        )
                    }
                }
            )
        }
    }

    fun dismissInviteDialog() {
        _uiState.update {
            it.copy(
                showInviteDialog = false,
                isGeneratingInvite = false,
                generatedInvite = null,
                inviteGenerationError = null
            )
        }
    }

    fun openAcceptInviteDialog() {
        _uiState.update {
            it.copy(
                showAcceptInviteDialog = true,
                inviteCodeInput = "",
                acceptInviteError = null,
                errorMessage = null
            )
        }
    }

    fun dismissAcceptInviteDialog() {
        _uiState.update {
            it.copy(
                showAcceptInviteDialog = false,
                inviteCodeInput = "",
                acceptInviteError = null
            )
        }
    }

    fun submitAcceptInvite() {
        val code = _uiState.value.inviteCodeInput.trim()
        if (code.length < 10) {
            _uiState.update {
                it.copy(acceptInviteError = "Enter the full invitation phrase (example: BLUE-TIGER-42-MAPLE).")
            }
            return
        }

        _uiState.update { it.copy(isAcceptingInvite = true, acceptInviteError = null, errorMessage = null) }
        scope.launch {
            val res = remoteGuardianRepository.acceptInvitation(code)
            res.fold(
                onSuccess = { guardian ->
                    _uiState.update {
                        it.copy(
                            isAcceptingInvite = false,
                            showAcceptInviteDialog = false,
                            inviteCodeInput = "",
                            acceptInviteError = null,
                            infoMessage = "Successfully linked with ${guardian.guardianDisplayName}."
                        )
                    }
                },
                onFailure = { err ->
                    val friendlyMessage = (err.localizedMessage ?: "")
                        .ifBlank { "Failed to accept invitation. Verify the phrase and try again." }
                    _uiState.update {
                        it.copy(
                            isAcceptingInvite = false,
                            acceptInviteError = friendlyMessage,
                            errorMessage = friendlyMessage
                        )
                    }
                }
            )
        }
    }

    fun setDemoMode(enabled: Boolean) {
        if (com.safeher.app.BuildConfig.IS_DEMO_DEFAULT) {
            demoModeManager?.setDemoMode(enabled)
        }
    }

    fun resetDemoData() {
        if (com.safeher.app.BuildConfig.IS_DEMO_DEFAULT) {
            demoModeManager?.resetDemoData()
            _uiState.update { it.copy(infoMessage = "Demo data reset to defaults. Local Room database remains untouched.") }
        }
    }

    fun revokeRelationship(relationshipId: String) {
        _uiState.update { it.copy(revokingRelationshipId = relationshipId) }
        scope.launch {
            val res = remoteGuardianRepository.revokeRelationship(relationshipId)
            _uiState.update { it.copy(revokingRelationshipId = null) }
            res.fold(
                onSuccess = {
                    _uiState.update { it.copy(infoMessage = "Remote guardian relationship revoked.") }
                },
                onFailure = { err ->
                    val friendlyMessage = (err.localizedMessage ?: "")
                        .ifBlank { "Failed to revoke relationship." }
                    _uiState.update { it.copy(errorMessage = friendlyMessage) }
                }
            )
        }
    }
}
