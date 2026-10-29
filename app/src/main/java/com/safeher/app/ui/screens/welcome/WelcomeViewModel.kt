package com.safeher.app.ui.screens.welcome

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.safeher.app.data.auth.AuthRepository
import com.safeher.app.data.auth.AuthResult
import com.safeher.app.data.auth.AuthState
import com.safeher.app.data.demo.DemoModeManager
import com.safeher.app.data.demo.DemoProfile
import com.safeher.app.data.guardian.GuardianOperationResult
import com.safeher.app.data.guardian.GuardianRepository
import com.safeher.app.data.onboarding.OnboardingPreferencesRepository
import com.safeher.app.data.remoteguardian.RemoteGuardianRepository
import com.safeher.app.data.session.UserRole
import com.safeher.app.data.session.UserSessionManager
import com.safeher.app.data.sos.SosPreferencesRepository
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

data class WelcomeUiState(
    val currentStep: WelcomeStep = WelcomeStep.WELCOME,
    val slideIndex: Int = 0,
    val selectedRole: UserRole = UserRole.PERSONAL_SAFETY_USER,

    // Authentication Form State
    val isSignInMode: Boolean = true,
    val emailInput: String = "",
    val passwordInput: String = "",
    val confirmPasswordInput: String = "",
    val displayNameInput: String = "",
    val isPasswordVisible: Boolean = false,
    val isConfirmPasswordVisible: Boolean = false,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val infoMessage: String? = null,

    // Password Reset
    val showForgotPasswordDialog: Boolean = false,
    val resetEmailInput: String = "",
    val isResettingPassword: Boolean = false,

    // Guardian Connection Form State
    val inviteCodeInput: String = "",
    val isConnectingGuardian: Boolean = false,
    val connectGuardianError: String? = null,
    val connectGuardianSuccess: Boolean = false,

    // First Login Setup Checklist
    val hasAddedGuardian: Boolean = false,
    val isLocationPermissionGranted: Boolean = false,
    val isSmsPermissionGranted: Boolean = false,
    val isNotificationPermissionGranted: Boolean = false,
    val isDirectSmsConsentGiven: Boolean = false,
    val guardianNameInput: String = "",
    val guardianPhoneInput: String = "",
    val guardianRelationshipInput: String = "",
    val isAddingGuardian: Boolean = false,
    val showGuardianAddDialog: Boolean = false,

    // Flavor State
    val isDemoMode: Boolean = false
)

class WelcomeViewModel(
    private val authRepository: AuthRepository,
    private val onboardingPreferencesRepository: OnboardingPreferencesRepository,
    private val guardianRepository: GuardianRepository? = null,
    private val sessionManager: UserSessionManager? = null,
    private val sosPreferencesRepository: SosPreferencesRepository? = null,
    private val demoModeManager: DemoModeManager? = null,
    private val remoteGuardianRepository: RemoteGuardianRepository? = null,
    coroutineScope: CoroutineScope? = null,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Main
) : ViewModel() {

    private val scope: CoroutineScope = coroutineScope ?: viewModelScope

    private val _uiState = MutableStateFlow(
        WelcomeUiState(
            currentStep = run {
                val currentUid = sessionManager?.currentUserId
                if (!currentUid.isNullOrBlank() &&
                    !onboardingPreferencesRepository.isUserSetupCompleted(currentUid) &&
                    sessionManager.isNewRegistration(currentUid)
                ) {
                    when (sessionManager.getUserRole(currentUid)) {
                        UserRole.GUARDIAN -> WelcomeStep.GUARDIAN_CONNECT
                        else -> WelcomeStep.FIRST_LOGIN_SETUP
                    }
                } else {
                    WelcomeStep.WELCOME
                }
            },
            selectedRole = run {
                val currentUid = sessionManager?.currentUserId
                if (!currentUid.isNullOrBlank()) {
                    sessionManager.getUserRole(currentUid)
                } else {
                    UserRole.PERSONAL_SAFETY_USER
                }
            }
        )
    )
    val uiState: StateFlow<WelcomeUiState> = _uiState.asStateFlow()

    init {
        // Observe Demo Mode in demo builds
        if (com.safeher.app.BuildConfig.IS_DEMO_DEFAULT) {
            demoModeManager?.isDemoMode
                ?.onEach { isDemo -> _uiState.update { it.copy(isDemoMode = isDemo) } }
                ?.launchIn(scope)
        }

        // Observe active guardian count if available to update checklist
        guardianRepository?.let { repo ->
            repo.getGuardianCountFlow()
                .onEach { count ->
                    _uiState.update { it.copy(hasAddedGuardian = count > 0) }
                }
                .launchIn(scope)
        }

        // Observe SOS consent if available
        sosPreferencesRepository?.let { repo ->
            repo.preferencesFlow
                .onEach { prefs ->
                    _uiState.update { it.copy(isDirectSmsConsentGiven = prefs.isDirectSmsConsentGiven) }
                }
                .launchIn(scope)
        }
    }

    // --- Navigation Flow ---

    fun navigateToStep(step: WelcomeStep) {
        _uiState.update {
            val role = when (step) {
                WelcomeStep.GUARDIAN_LOGIN, WelcomeStep.GUARDIAN_SIGN_UP -> UserRole.GUARDIAN
                WelcomeStep.USER_LOGIN, WelcomeStep.USER_SIGN_UP -> UserRole.PERSONAL_SAFETY_USER
                else -> it.selectedRole
            }
            val isSignIn = when (step) {
                WelcomeStep.USER_LOGIN, WelcomeStep.GUARDIAN_LOGIN -> true
                WelcomeStep.USER_SIGN_UP, WelcomeStep.GUARDIAN_SIGN_UP -> false
                else -> it.isSignInMode
            }
            it.copy(currentStep = step, selectedRole = role, isSignInMode = isSignIn, errorMessage = null, infoMessage = null)
        }
    }

    fun selectRole(role: UserRole) {
        _uiState.update { current ->
            val newStep = when (current.currentStep) {
                WelcomeStep.USER_LOGIN, WelcomeStep.GUARDIAN_LOGIN -> {
                    if (role == UserRole.GUARDIAN) WelcomeStep.GUARDIAN_LOGIN else WelcomeStep.USER_LOGIN
                }
                WelcomeStep.USER_SIGN_UP, WelcomeStep.GUARDIAN_SIGN_UP -> {
                    if (role == UserRole.GUARDIAN) WelcomeStep.GUARDIAN_SIGN_UP else WelcomeStep.USER_SIGN_UP
                }
                else -> current.currentStep
            }
            current.copy(selectedRole = role, currentStep = newStep, errorMessage = null)
        }
    }

    fun onSlideChangedInWelcome(page: Int) {
        val role = if (page == 1) UserRole.GUARDIAN else UserRole.PERSONAL_SAFETY_USER
        _uiState.update { it.copy(selectedRole = role) }
    }

    fun onContinueAsUser() {
        _uiState.update {
            it.copy(
                selectedRole = UserRole.PERSONAL_SAFETY_USER,
                currentStep = WelcomeStep.USER_LOGIN,
                isSignInMode = true,
                errorMessage = null,
                infoMessage = null
            )
        }
    }

    fun onContinueAsGuardian() {
        _uiState.update {
            it.copy(
                selectedRole = UserRole.GUARDIAN,
                currentStep = WelcomeStep.GUARDIAN_LOGIN,
                isSignInMode = true,
                errorMessage = null,
                infoMessage = null
            )
        }
    }

    fun onGetStartedClicked() {
        _uiState.update {
            it.copy(
                currentStep = WelcomeStep.ONBOARDING,
                slideIndex = 0,
                errorMessage = null,
                infoMessage = null
            )
        }
    }

    fun onAlreadyHaveAccountClicked() {
        _uiState.update {
            it.copy(
                currentStep = WelcomeStep.AUTH,
                selectedRole = UserRole.PERSONAL_SAFETY_USER,
                isSignInMode = true,
                errorMessage = null,
                infoMessage = null
            )
        }
    }

    fun setSlideIndex(index: Int) {
        val clamped = index.coerceIn(0, 3)
        _uiState.update { it.copy(slideIndex = clamped) }
    }

    fun onSlideNext() {
        val current = _uiState.value.slideIndex
        if (current < 3) {
            _uiState.update { it.copy(slideIndex = current + 1) }
        } else {
            onFinishOnboarding()
        }
    }

    fun onSlidePrevious() {
        val current = _uiState.value.slideIndex
        if (current > 0) {
            _uiState.update { it.copy(slideIndex = current - 1) }
        } else {
            _uiState.update { it.copy(currentStep = WelcomeStep.WELCOME) }
        }
    }

    fun onSkipOnboarding() {
        onboardingPreferencesRepository.setIntroductionCompleted(true)
        _uiState.update {
            it.copy(
                currentStep = WelcomeStep.AUTH,
                selectedRole = UserRole.PERSONAL_SAFETY_USER,
                isSignInMode = false,
                errorMessage = null,
                infoMessage = null
            )
        }
    }

    fun onFinishOnboarding() {
        onboardingPreferencesRepository.setIntroductionCompleted(true)
        _uiState.update {
            it.copy(
                currentStep = WelcomeStep.AUTH,
                selectedRole = UserRole.PERSONAL_SAFETY_USER,
                isSignInMode = false,
                errorMessage = null,
                infoMessage = null
            )
        }
    }

    fun onBackFromAuth() {
        _uiState.update { it.copy(currentStep = WelcomeStep.WELCOME, errorMessage = null, infoMessage = null) }
    }

    fun signOut() {
        scope.launch {
            authRepository.signOut()
            _uiState.update { it.copy(currentStep = WelcomeStep.WELCOME, errorMessage = null, infoMessage = null) }
        }
    }

    // --- Authentication Actions ---

    fun toggleAuthMode() {
        _uiState.update { current ->
            val newSignInMode = !current.isSignInMode
            val newStep = when (current.currentStep) {
                WelcomeStep.AUTH -> WelcomeStep.AUTH
                WelcomeStep.GUARDIAN_LOGIN, WelcomeStep.GUARDIAN_SIGN_UP -> {
                    if (newSignInMode) WelcomeStep.GUARDIAN_LOGIN else WelcomeStep.GUARDIAN_SIGN_UP
                }
                WelcomeStep.USER_LOGIN, WelcomeStep.USER_SIGN_UP -> {
                    if (newSignInMode) WelcomeStep.USER_LOGIN else WelcomeStep.USER_SIGN_UP
                }
                else -> current.currentStep
            }
            current.copy(
                isSignInMode = newSignInMode,
                currentStep = newStep,
                errorMessage = null,
                infoMessage = null
            )
        }
    }

    fun updateEmail(value: String) = _uiState.update { it.copy(emailInput = value, errorMessage = null) }
    fun updatePassword(value: String) = _uiState.update { it.copy(passwordInput = value, errorMessage = null) }
    fun updateConfirmPassword(value: String) = _uiState.update { it.copy(confirmPasswordInput = value, errorMessage = null) }
    fun updateDisplayName(value: String) = _uiState.update { it.copy(displayNameInput = value, errorMessage = null) }
    fun togglePasswordVisibility() = _uiState.update { it.copy(isPasswordVisible = !it.isPasswordVisible) }
    fun toggleConfirmPasswordVisibility() = _uiState.update { it.copy(isConfirmPasswordVisible = !it.isConfirmPasswordVisible) }
    companion object {
        private val EMAIL_REGEX = Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}\$")

        internal fun isValidEmailAddress(email: String): Boolean {
            val trimmed = email.trim()
            if (trimmed.isEmpty()) return false
            return try {
                val pattern = android.util.Patterns.EMAIL_ADDRESS
                if (pattern != null) {
                    pattern.matcher(trimmed).matches()
                } else {
                    EMAIL_REGEX.matches(trimmed)
                }
            } catch (_: Exception) {
                EMAIL_REGEX.matches(trimmed)
            }
        }
    }

    fun clearMessages() = _uiState.update { it.copy(errorMessage = null, infoMessage = null) }

    fun signInWithEmail() {
        val state = _uiState.value
        if (state.isLoading) return

        val email = state.emailInput.trim()
        val password = state.passwordInput

        if (email.isBlank() || password.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Email and password are required.") }
            return
        }

        if (!isValidEmailAddress(email)) {
            _uiState.update { it.copy(errorMessage = "Please enter a valid email address.") }
            return
        }

        if (password.length < 6) {
            _uiState.update { it.copy(errorMessage = "Password must be at least 6 characters.") }
            return
        }

        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        sessionManager?.setPendingRoleForNextAuthentication(state.selectedRole)
        scope.launch {
            when (val res = authRepository.signInWithEmail(email, password)) {
                is AuthResult.Success -> {
                    val uid = res.user.uid
                    val establishedRole = sessionManager?.getEstablishedUserRole(uid)
                    val effectiveRole = establishedRole ?: state.selectedRole
                    sessionManager?.setUserRole(uid, effectiveRole)
                    sessionManager?.clearPendingRoleForNextAuthentication()
                    val isSetupDone = onboardingPreferencesRepository.isUserSetupCompleted(uid)
                    val isNewReg = sessionManager?.isNewRegistration(uid) == true

                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            emailInput = "",
                            passwordInput = "",
                            selectedRole = effectiveRole,
                            currentStep = if (effectiveRole == UserRole.GUARDIAN) {
                                if (!isSetupDone && isNewReg) {
                                    WelcomeStep.GUARDIAN_CONNECT
                                } else {
                                    WelcomeStep.WELCOME
                                }
                            } else if (!isSetupDone && isNewReg) {
                                WelcomeStep.FIRST_LOGIN_SETUP
                            } else {
                                it.currentStep
                            }
                        )
                    }
                }
                is AuthResult.Error -> {
                    sessionManager?.clearPendingRoleForNextAuthentication()
                    _uiState.update { it.copy(isLoading = false, errorMessage = res.message) }
                }
                AuthResult.Cancelled -> {
                    sessionManager?.clearPendingRoleForNextAuthentication()
                    _uiState.update { it.copy(isLoading = false) }
                }
            }
        }
    }

    fun signUpWithEmail() {
        val state = _uiState.value
        if (state.isLoading) return

        val email = state.emailInput.trim()
        val password = state.passwordInput
        val confirmPassword = state.confirmPasswordInput
        val displayName = state.displayNameInput.trim()

        if (state.selectedRole == UserRole.GUARDIAN && displayName.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Display name is required for guardian registration.") }
            return
        }

        if (email.isBlank() || password.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Email and password are required.") }
            return
        }

        if (!isValidEmailAddress(email)) {
            _uiState.update { it.copy(errorMessage = "Please enter a valid email address.") }
            return
        }

        if (password.length < 6) {
            _uiState.update { it.copy(errorMessage = "Password must be at least 6 characters.") }
            return
        }

        if (password != confirmPassword) {
            _uiState.update { it.copy(errorMessage = "Passwords do not match.") }
            return
        }

        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        sessionManager?.setPendingRoleForNextAuthentication(state.selectedRole)
        sessionManager?.setPendingNewRegistration(true)
        scope.launch {
            when (val res = authRepository.signUpWithEmail(email, password, displayName)) {
                is AuthResult.Success -> {
                    val uid = res.user.uid
                    val role = state.selectedRole
                    sessionManager?.markAsNewRegistration(uid)
                    sessionManager?.setUserRole(uid, role)
                    sessionManager?.clearPendingRoleForNextAuthentication()

                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            emailInput = "",
                            passwordInput = "",
                            confirmPasswordInput = "",
                            displayNameInput = "",
                            selectedRole = role,
                            currentStep = if (role == UserRole.GUARDIAN) {
                                WelcomeStep.GUARDIAN_CONNECT
                            } else {
                                WelcomeStep.FIRST_LOGIN_SETUP
                            },
                            infoMessage = if (role == UserRole.GUARDIAN) {
                                "Guardian account created successfully!"
                            } else {
                                "Account created successfully!"
                            }
                        )
                    }
                }
                is AuthResult.Error -> {
                    sessionManager?.clearPendingRoleForNextAuthentication()
                    _uiState.update { it.copy(isLoading = false, errorMessage = res.message) }
                }
                AuthResult.Cancelled -> {
                    sessionManager?.clearPendingRoleForNextAuthentication()
                    _uiState.update { it.copy(isLoading = false) }
                }
            }
        }
    }

    fun signInWithGoogle(context: Context, webClientId: String) {
        if (_uiState.value.isLoading) return

        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        sessionManager?.setPendingRoleForNextAuthentication(_uiState.value.selectedRole)
        scope.launch {
            when (val res = authRepository.signInWithGoogle(context, webClientId)) {
                is AuthResult.Success -> {
                    val uid = res.user.uid
                    val establishedRole = sessionManager?.getEstablishedUserRole(uid)
                    val effectiveRole = establishedRole ?: _uiState.value.selectedRole
                    sessionManager?.setUserRole(uid, effectiveRole)
                    sessionManager?.clearPendingRoleForNextAuthentication()
                    val isSetupDone = onboardingPreferencesRepository.isUserSetupCompleted(uid)
                    val isNewReg = sessionManager?.isNewRegistration(uid) == true

                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            selectedRole = effectiveRole,
                            currentStep = if (effectiveRole == UserRole.GUARDIAN) {
                                if (!isSetupDone && isNewReg) {
                                    WelcomeStep.GUARDIAN_CONNECT
                                } else {
                                    WelcomeStep.WELCOME
                                }
                            } else if (!isSetupDone && isNewReg) {
                                WelcomeStep.FIRST_LOGIN_SETUP
                            } else {
                                it.currentStep
                            }
                        )
                    }
                }
                is AuthResult.Error -> {
                    sessionManager?.clearPendingRoleForNextAuthentication()
                    _uiState.update { it.copy(isLoading = false, errorMessage = res.message) }
                }
                AuthResult.Cancelled -> {
                    sessionManager?.clearPendingRoleForNextAuthentication()
                    _uiState.update { it.copy(isLoading = false) }
                }
            }
        }
    }

    // --- Password Reset ---

    fun openForgotPasswordDialog() {
        val currentEmail = _uiState.value.emailInput.trim()
        _uiState.update {
            it.copy(
                showForgotPasswordDialog = true,
                resetEmailInput = currentEmail,
                errorMessage = null,
                infoMessage = null
            )
        }
    }

    fun dismissForgotPasswordDialog() {
        _uiState.update { it.copy(showForgotPasswordDialog = false) }
    }

    fun updateResetEmail(value: String) {
        _uiState.update { it.copy(resetEmailInput = value) }
    }

    fun sendPasswordReset() {
        val email = _uiState.value.resetEmailInput.trim()
        if (email.isBlank() || !isValidEmailAddress(email)) {
            _uiState.update { it.copy(errorMessage = "Please enter a valid email address.") }
            return
        }

        _uiState.update { it.copy(isResettingPassword = true) }
        scope.launch {
            authRepository.sendPasswordResetEmail(email)
            _uiState.update {
                it.copy(
                    isResettingPassword = false,
                    showForgotPasswordDialog = false,
                    infoMessage = "If an account exists for $email, a password reset email has been sent."
                )
            }
        }
    }

    // --- Demo Mode Action ---

    fun continueAsDemoUser() {
        if (!com.safeher.app.BuildConfig.IS_DEMO_DEFAULT) return
        demoModeManager?.setDemoMode(true)
        val role = _uiState.value.selectedRole
        scope.launch {
            when (val res = if (role == UserRole.GUARDIAN) {
                authRepository.signInWithEmail("guardian@safeher.app", "SafeHerGuardian123!")
            } else {
                authRepository.signInWithEmail("demo@safeher.app", "SafeHerDemo123!")
            }) {
                is AuthResult.Success -> {
                    sessionManager?.setUserRole(res.user.uid, role)
                }
                else -> { /* stay on welcome; demo sign-in failed */ }
            }
        }
    }

    // --- Guardian Connection Actions ---

    fun updateInviteCode(code: String) {
        val sanitized = code.uppercase()
            .map { if (it.isWhitespace()) '-' else it }
            .filter { it.isLetterOrDigit() || it == '-' }
            .joinToString("")
            .take(48)
        _uiState.update { it.copy(inviteCodeInput = sanitized, connectGuardianError = null) }
    }

    fun submitGuardianConnectCode(onSuccess: () -> Unit = {}) {
        val state = _uiState.value
        if (state.isConnectingGuardian) return
        val code = state.inviteCodeInput.trim().uppercase()
        if (code.length < 10) {
            _uiState.update {
                it.copy(connectGuardianError = "Enter the full invitation phrase (example: BLUE-TIGER-42-MAPLE).")
            }
            return
        }

        val repo = remoteGuardianRepository ?: run {
            _uiState.update {
                it.copy(
                    connectGuardianError = "Remote guardian repository is unavailable. Sign in again and retry."
                )
            }
            return
        }

        _uiState.update { it.copy(isConnectingGuardian = true, connectGuardianError = null) }
        scope.launch {
            try {
                val result = repo.acceptInvitation(code)
                if (result.isSuccess) {
                    _uiState.update {
                        it.copy(
                            isConnectingGuardian = false,
                            connectGuardianSuccess = true,
                            connectGuardianError = null
                        )
                    }
                    onSuccess()
                } else {
                    val exception = result.exceptionOrNull()
                    val msg = exception?.message ?: ""
                    val userFriendlyMessage = mapGuardianConnectError(msg)
                    _uiState.update {
                        it.copy(
                            isConnectingGuardian = false,
                            connectGuardianError = userFriendlyMessage
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isConnectingGuardian = false,
                        connectGuardianError = mapGuardianConnectError(e.message ?: "")
                    )
                }
            }
        }
    }

    private fun mapGuardianConnectError(msg: String): String {
        return when {
            msg.contains("Permission denied", ignoreCase = true) ->
                "Could not claim this invitation. It may be invalid, expired, already used, or local security rules may not allow the claim yet."
            msg.contains("already exists", ignoreCase = true) || msg.contains("already active", ignoreCase = true) ->
                "You are already connected with this user."
            msg.contains("has expired", ignoreCase = true) ->
                "This invitation has expired. Ask the user for a new phrase."
            msg.contains("already been used", ignoreCase = true) || msg.contains("revoked", ignoreCase = true) ->
                "This invitation has already been used or was revoked."
            msg.contains("your own", ignoreCase = true) ->
                "You cannot connect to yourself as a guardian."
            msg.contains("not found", ignoreCase = true) ->
                "Invitation not found. Check the phrase and try again."
            msg.isNotBlank() -> msg
            else -> "Failed to connect. Verify the invitation phrase and try again."
        }
    }

    fun dismissGuardianConnectError() {
        _uiState.update { it.copy(connectGuardianError = null) }
    }

    fun skipGuardianConnect(userId: String) {
        onboardingPreferencesRepository.setUserSetupCompleted(userId, true)
    }

    // --- First-Login Setup Checklist ---

    fun updatePermissionStates(location: Boolean, sms: Boolean, notification: Boolean) {
        _uiState.update {
            it.copy(
                isLocationPermissionGranted = location,
                isSmsPermissionGranted = sms,
                isNotificationPermissionGranted = notification
            )
        }
    }

    fun openAddGuardianDialog() {
        _uiState.update {
            it.copy(
                showGuardianAddDialog = true,
                guardianNameInput = "",
                guardianPhoneInput = "",
                guardianRelationshipInput = "",
                errorMessage = null
            )
        }
    }

    fun dismissAddGuardianDialog() {
        _uiState.update { it.copy(showGuardianAddDialog = false) }
    }

    fun updateGuardianName(name: String) = _uiState.update { it.copy(guardianNameInput = name) }
    fun updateGuardianPhone(phone: String) = _uiState.update { it.copy(guardianPhoneInput = phone) }
    fun updateGuardianRelationship(rel: String) = _uiState.update { it.copy(guardianRelationshipInput = rel) }

    fun saveQuickGuardian() {
        val state = _uiState.value
        val name = state.guardianNameInput.trim()
        val phone = state.guardianPhoneInput.trim()
        val relationship = state.guardianRelationshipInput.trim()

        if (name.isBlank() || phone.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Name and phone number are required.") }
            return
        }

        val repo = guardianRepository ?: run {
            _uiState.update { it.copy(hasAddedGuardian = true, showGuardianAddDialog = false) }
            return
        }

        _uiState.update { it.copy(isAddingGuardian = true, errorMessage = null) }
        scope.launch {
            val result = repo.addGuardian(name, phone, relationship.ifBlank { null })
            when (result) {
                is GuardianOperationResult.Success -> {
                    _uiState.update {
                        it.copy(
                            isAddingGuardian = false,
                            hasAddedGuardian = true,
                            showGuardianAddDialog = false,
                            infoMessage = "Guardian $name added successfully."
                        )
                    }
                }
                is GuardianOperationResult.DuplicatePhone -> {
                    _uiState.update {
                        it.copy(
                            isAddingGuardian = false,
                            errorMessage = result.message
                        )
                    }
                }
                is GuardianOperationResult.ValidationError -> {
                    _uiState.update {
                        it.copy(
                            isAddingGuardian = false,
                            errorMessage = result.message
                        )
                    }
                }
                is GuardianOperationResult.Error -> {
                    _uiState.update {
                        it.copy(
                            isAddingGuardian = false,
                            errorMessage = result.message
                        )
                    }
                }
            }
        }
    }

    fun setDirectSmsConsent(consentGiven: Boolean) {
        sosPreferencesRepository?.setConsentGiven(consentGiven)
        if (consentGiven) {
            sosPreferencesRepository?.setDirectSmsEnabled(true)
        }
        _uiState.update { it.copy(isDirectSmsConsentGiven = consentGiven) }
    }

    fun completeSetup(userId: String) {
        onboardingPreferencesRepository.setUserSetupCompleted(userId, true)
    }

    fun skipSetup(userId: String) {
        onboardingPreferencesRepository.setUserSetupCompleted(userId, true)
    }
}
