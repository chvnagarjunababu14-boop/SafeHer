package com.safeher.app

import android.content.Context
import android.content.SharedPreferences
import com.safeher.app.data.auth.AuthRepository
import com.safeher.app.data.auth.AuthResult
import com.safeher.app.data.auth.AuthState
import com.safeher.app.data.auth.AuthUser
import com.safeher.app.data.guardian.Guardian
import com.safeher.app.data.guardian.GuardianOperationResult
import com.safeher.app.data.guardian.GuardianRepository
import com.safeher.app.data.onboarding.OnboardingPreferencesRepository
import com.safeher.app.data.onboarding.OnboardingPreferencesRepositoryImpl
import com.safeher.app.data.session.UserSessionManager
import com.safeher.app.data.sos.SosPreferences
import com.safeher.app.data.sos.SosPreferencesRepository
import com.safeher.app.ui.screens.welcome.WelcomeStep
import com.safeher.app.ui.screens.welcome.WelcomeViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.lang.reflect.Proxy

class FakeTestAuthRepository : AuthRepository {
    private val _authState = MutableStateFlow<AuthState>(AuthState.Unauthenticated)
    override val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private var activeUser: AuthUser? = null
    var shouldFail: Boolean = false
    var failureMessage: String = "Authentication failed."
    var shouldCancel: Boolean = false
    var lastResetEmail: String? = null

    override fun getCurrentUser(): AuthUser? = activeUser

    override suspend fun signInWithEmail(email: String, password: String): AuthResult {
        if (shouldCancel) return AuthResult.Cancelled
        if (shouldFail) return AuthResult.Error(failureMessage)
        val user = AuthUser(uid = "test_user_id", email = email, displayName = "Test User")
        activeUser = user
        _authState.value = AuthState.Authenticated(user)
        return AuthResult.Success(user)
    }

    override suspend fun signUpWithEmail(email: String, password: String, displayName: String?): AuthResult {
        if (shouldCancel) return AuthResult.Cancelled
        if (shouldFail) return AuthResult.Error(failureMessage)
        val user = AuthUser(uid = "new_registered_user_id", email = email, displayName = displayName)
        activeUser = user
        _authState.value = AuthState.Authenticated(user)
        return AuthResult.Success(user)
    }

    override suspend fun sendPasswordResetEmail(email: String): Result<Unit> {
        lastResetEmail = email
        return if (shouldFail) Result.failure(Exception(failureMessage)) else Result.success(Unit)
    }

    override suspend fun sendEmailVerification(): Result<Unit> = Result.success(Unit)

    override suspend fun signInWithGoogle(context: Context, webClientId: String): AuthResult {
        if (shouldCancel) return AuthResult.Cancelled
        if (shouldFail) return AuthResult.Error(failureMessage)
        val user = AuthUser(uid = "google_user_id", email = "google@safeher.app", displayName = "Google User")
        activeUser = user
        _authState.value = AuthState.Authenticated(user)
        return AuthResult.Success(user)
    }

    override suspend fun signOut() {
        activeUser = null
        _authState.value = AuthState.Unauthenticated
    }
}

class WelcomeTestGuardianRepository : GuardianRepository {
    private val guardians = mutableListOf<Guardian>()
    private val guardianCountFlow = MutableStateFlow(0)

    override fun getGuardiansFlow(): Flow<List<Guardian>> = flowOf(guardians)
    override fun getGuardianCountFlow(): Flow<Int> = guardianCountFlow.asStateFlow()
    override suspend fun getGuardians(): List<Guardian> = guardians
    override suspend fun getGuardianCount(): Int = guardians.size

    override suspend fun addGuardian(
        name: String,
        phoneNumber: String,
        relationship: String?
    ): GuardianOperationResult {
        if (phoneNumber == "+19999999999") {
            return GuardianOperationResult.DuplicatePhone("A guardian with this number already exists.")
        }
        val g = Guardian(
            id = guardians.size.toLong() + 1,
            name = name,
            phoneNumber = phoneNumber,
            relationship = relationship
        )
        guardians.add(g)
        guardianCountFlow.value = guardians.size
        return GuardianOperationResult.Success(g.id)
    }

    override suspend fun updateGuardian(
        id: Long,
        name: String,
        phoneNumber: String,
        relationship: String?
    ): GuardianOperationResult = GuardianOperationResult.Success(id)

    override suspend fun deleteGuardian(id: Long): Boolean = true
    override suspend fun getGuardianById(id: Long): Guardian? = guardians.find { it.id == id }
    override suspend fun updateSosEnabled(id: Long, isSosEnabled: Boolean): Boolean = true
    override suspend fun getSosEnabledGuardians(): List<Guardian> = guardians
    override fun getSosEnabledGuardiansFlow(): Flow<List<Guardian>> = flowOf(guardians)
    override suspend fun updateBatteryAlertEnabled(id: Long, isBatteryAlertEnabled: Boolean): Boolean = true
    override suspend fun getBatteryAlertEnabledGuardians(): List<Guardian> = guardians.filter { it.isBatteryAlertEnabled }
}

class WelcomeTestSosPreferencesRepository : SosPreferencesRepository {
    private val _prefs = MutableStateFlow(SosPreferences())
    override val preferencesFlow: StateFlow<SosPreferences> = _prefs.asStateFlow()
    override fun getPreferences(): SosPreferences = _prefs.value

    override fun setConsentGiven(consentGiven: Boolean) {
        _prefs.value = _prefs.value.copy(isDirectSmsConsentGiven = consentGiven)
    }

    override fun setDirectSmsEnabled(enabled: Boolean) {
        _prefs.value = _prefs.value.copy(isDirectSmsEnabled = enabled)
    }

    override fun setCustomSosMessage(message: String) {
        _prefs.value = _prefs.value.copy(customSosMessage = message)
    }

    override fun setLocationSharingEnabled(enabled: Boolean) {
        _prefs.value = _prefs.value.copy(isLocationSharingEnabled = enabled)
    }

    override fun setCountdownDurationSeconds(seconds: Int) {
        _prefs.value = _prefs.value.copy(countdownDurationSeconds = seconds)
    }

    override fun setOverdueEscalationConsent(consentGiven: Boolean) {
        _prefs.value = _prefs.value.copy(isOverdueEscalationConsentGiven = consentGiven)
    }

    override fun setOverdueEscalationEnabled(enabled: Boolean) {
        _prefs.value = _prefs.value.copy(isOverdueEscalationEnabled = enabled)
    }

    override fun setBatteryAlertsConsent(consentGiven: Boolean) {
        _prefs.value = _prefs.value.copy(
            isBatteryAlertsConsentGiven = consentGiven,
            isBatteryAlertsEnabled = if (consentGiven) _prefs.value.isBatteryAlertsEnabled else false
        )
    }

    override fun setBatteryAlertsEnabled(enabled: Boolean) {
        _prefs.value = _prefs.value.copy(
            isBatteryAlertsEnabled = enabled && _prefs.value.isBatteryAlertsConsentGiven
        )
    }

    override fun setBatteryAlertLocationEnabled(enabled: Boolean) {
        _prefs.value = _prefs.value.copy(isBatteryAlertLocationEnabled = enabled)
    }

    override fun setBatteryLowThresholdPercent(percent: Int) {
        _prefs.value = _prefs.value.copy(batteryLowThresholdPercent = percent)
    }

    override fun setBatteryCriticalThresholdPercent(percent: Int) {
        _prefs.value = _prefs.value.copy(batteryCriticalThresholdPercent = percent)
    }
}

class WelcomeViewModelTest {

    private lateinit var authRepo: FakeTestAuthRepository
    private lateinit var onboardingRepo: OnboardingPreferencesRepositoryImpl
    private lateinit var guardianRepo: WelcomeTestGuardianRepository
    private lateinit var sosPrefsRepo: WelcomeTestSosPreferencesRepository
    private lateinit var sessionManager: UserSessionManager
    private val memoryPrefs = mutableMapOf<String, Any>()

    @Before
    fun setUp() {
        memoryPrefs.clear()

        var editorProxy: SharedPreferences.Editor? = null
        editorProxy = Proxy.newProxyInstance(
            SharedPreferences.Editor::class.java.classLoader,
            arrayOf(SharedPreferences.Editor::class.java)
        ) { _, method, args ->
            when (method.name) {
                "putString" -> {
                    memoryPrefs[args[0] as String] = args[1] as String
                    editorProxy
                }
                "putBoolean" -> {
                    memoryPrefs[args[0] as String] = args[1] as Boolean
                    editorProxy
                }
                "remove" -> {
                    memoryPrefs.remove(args[0] as String)
                    editorProxy
                }
                "clear" -> {
                    memoryPrefs.clear()
                    editorProxy
                }
                "apply", "commit" -> null
                else -> null
            }
        } as SharedPreferences.Editor

        val prefsProxy = Proxy.newProxyInstance(
            SharedPreferences::class.java.classLoader,
            arrayOf(SharedPreferences::class.java)
        ) { _, method, args ->
            when (method.name) {
                "getString" -> memoryPrefs[args[0] as String] ?: args[1]
                "getBoolean" -> memoryPrefs[args[0] as String] ?: args[1]
                "contains" -> memoryPrefs.containsKey(args[0] as String)
                "edit" -> editorProxy
                else -> null
            }
        } as SharedPreferences

        authRepo = FakeTestAuthRepository()
        onboardingRepo = OnboardingPreferencesRepositoryImpl(prefsProxy)
        guardianRepo = WelcomeTestGuardianRepository()
        sosPrefsRepo = WelcomeTestSosPreferencesRepository()
        sessionManager = UserSessionManager.createForTesting(prefsProxy)
        sessionManager.initialize(authRepo)
    }

    private fun createViewModel(): WelcomeViewModel {
        return WelcomeViewModel(
            authRepository = authRepo,
            onboardingPreferencesRepository = onboardingRepo,
            guardianRepository = guardianRepo,
            sessionManager = sessionManager,
            sosPreferencesRepository = sosPrefsRepo,
            coroutineScope = kotlinx.coroutines.CoroutineScope(Dispatchers.Unconfined)
        )
    }

    @Test
    fun defaultState_onFirstLaunch_startsOnWelcomeStep() {
        val vm = createViewModel()
        assertEquals(WelcomeStep.WELCOME, vm.uiState.value.currentStep)
        assertFalse(onboardingRepo.isIntroductionCompleted())
    }

    @Test
    fun getStarted_navigatesToOnboardingSlide0() {
        val vm = createViewModel()
        vm.onGetStartedClicked()

        assertEquals(WelcomeStep.ONBOARDING, vm.uiState.value.currentStep)
        assertEquals(0, vm.uiState.value.slideIndex)
    }

    @Test
    fun onboardingSlidesNavigation_nextAndPrevious() {
        val vm = createViewModel()
        vm.onGetStartedClicked()

        vm.onSlideNext()
        assertEquals(1, vm.uiState.value.slideIndex)

        vm.onSlideNext()
        assertEquals(2, vm.uiState.value.slideIndex)

        vm.onSlideNext()
        assertEquals(3, vm.uiState.value.slideIndex)

        // Previous from 3 to 2, 1, 0
        vm.onSlidePrevious()
        assertEquals(2, vm.uiState.value.slideIndex)

        vm.onSlidePrevious()
        assertEquals(1, vm.uiState.value.slideIndex)

        vm.onSlidePrevious()
        assertEquals(0, vm.uiState.value.slideIndex)

        // Previous from 0 returns to Welcome
        vm.onSlidePrevious()
        assertEquals(WelcomeStep.WELCOME, vm.uiState.value.currentStep)
    }

    @Test
    fun skipOnboarding_marksIntroCompleted_andNavigatesToAuth() {
        val vm = createViewModel()
        vm.onGetStartedClicked()
        vm.onSkipOnboarding()

        assertTrue(onboardingRepo.isIntroductionCompleted())
        assertEquals(WelcomeStep.AUTH, vm.uiState.value.currentStep)
        assertFalse(vm.uiState.value.isSignInMode) // Defaults to sign-up on completion
    }

    @Test
    fun finishOnboarding_onLastSlide_marksIntroCompleted_andNavigatesToAuth() {
        val vm = createViewModel()
        vm.onGetStartedClicked()
        vm.setSlideIndex(3)
        vm.onFinishOnboarding()

        assertTrue(onboardingRepo.isIntroductionCompleted())
        assertEquals(WelcomeStep.AUTH, vm.uiState.value.currentStep)
    }

    @Test
    fun alreadyHaveAccount_navigatesDirectlyToSignInAuth() {
        val vm = createViewModel()
        vm.onAlreadyHaveAccountClicked()

        assertEquals(WelcomeStep.AUTH, vm.uiState.value.currentStep)
        assertTrue(vm.uiState.value.isSignInMode)
    }

    @Test
    fun toggleAuthMode_switchesBetweenSignInAndSignUp() {
        val vm = createViewModel()
        vm.onAlreadyHaveAccountClicked()
        assertTrue(vm.uiState.value.isSignInMode)

        vm.toggleAuthMode()
        assertFalse(vm.uiState.value.isSignInMode)

        vm.toggleAuthMode()
        assertTrue(vm.uiState.value.isSignInMode)
    }

    @Test
    fun signInWithEmail_emptyCredentials_showsError() {
        val vm = createViewModel()
        vm.onAlreadyHaveAccountClicked()

        vm.signInWithEmail()
        assertEquals("Email and password are required.", vm.uiState.value.errorMessage)
        assertEquals(WelcomeStep.AUTH, vm.uiState.value.currentStep)
    }

    @Test
    fun signInWithEmail_invalidEmail_showsError() {
        val vm = createViewModel()
        vm.onAlreadyHaveAccountClicked()
        vm.updateEmail("invalid_email")
        vm.updatePassword("password123")

        vm.signInWithEmail()
        assertEquals("Please enter a valid email address.", vm.uiState.value.errorMessage)
    }

    @Test
    fun signInWithEmail_shortPassword_showsError() {
        val vm = createViewModel()
        vm.onAlreadyHaveAccountClicked()
        vm.updateEmail("user@safeher.app")
        vm.updatePassword("123")

        vm.signInWithEmail()
        assertEquals("Password must be at least 6 characters.", vm.uiState.value.errorMessage)
    }

    @Test
    fun signInWithEmail_authFailure_remainsOnAuthScreen_andDisplaysError() {
        authRepo.shouldFail = true
        authRepo.failureMessage = "Invalid email or password."

        val vm = createViewModel()
        vm.onAlreadyHaveAccountClicked()
        vm.updateEmail("user@safeher.app")
        vm.updatePassword("wrongpassword")

        vm.signInWithEmail()
        assertEquals(WelcomeStep.AUTH, vm.uiState.value.currentStep)
        assertEquals("Invalid email or password.", vm.uiState.value.errorMessage)
        assertFalse(vm.uiState.value.isLoading)
    }

    @Test
    fun signUpWithEmail_passwordMismatch_showsError() {
        val vm = createViewModel()
        vm.navigateToStep(WelcomeStep.AUTH)
        vm.toggleAuthMode() // Sign Up mode
        vm.updateEmail("newuser@safeher.app")
        vm.updatePassword("secret123")
        vm.updateConfirmPassword("mismatch456")

        vm.signUpWithEmail()
        assertEquals("Passwords do not match.", vm.uiState.value.errorMessage)
        assertEquals(WelcomeStep.AUTH, vm.uiState.value.currentStep)
    }

    @Test
    fun signUpWithEmail_success_marksNewRegistration_andNavigatesToFirstLoginSetup() {
        val vm = createViewModel()
        vm.navigateToStep(WelcomeStep.AUTH)
        vm.toggleAuthMode() // Sign Up mode
        vm.updateDisplayName("Jane Doe")
        vm.updateEmail("jane@safeher.app")
        vm.updatePassword("safePass123")
        vm.updateConfirmPassword("safePass123")

        vm.signUpWithEmail()

        assertEquals(WelcomeStep.FIRST_LOGIN_SETUP, vm.uiState.value.currentStep)
        assertTrue(sessionManager.isNewRegistration("new_registered_user_id"))
        assertEquals("Account created successfully!", vm.uiState.value.infoMessage)
    }

    @Test
    fun googleSignIn_success_forNewUser_navigatesToFirstLoginSetup() {
        val vm = createViewModel()
        vm.navigateToStep(WelcomeStep.AUTH)

        val mockContext = android.content.ContextWrapper(null)

        // Mark as new registration in session manager
        sessionManager.markAsNewRegistration("google_user_id")

        vm.signInWithGoogle(mockContext, "dummy_web_client_id")

        assertEquals(WelcomeStep.FIRST_LOGIN_SETUP, vm.uiState.value.currentStep)
        assertNull(vm.uiState.value.errorMessage)
    }

    @Test
    fun googleSignIn_cancelled_resetsLoadingWithoutError() {
        authRepo.shouldCancel = true
        val vm = createViewModel()
        vm.navigateToStep(WelcomeStep.AUTH)

        val mockContext = android.content.ContextWrapper(null)

        vm.signInWithGoogle(mockContext, "dummy_web_client_id")

        assertFalse(vm.uiState.value.isLoading)
        assertNull(vm.uiState.value.errorMessage)
    }

    @Test
    fun passwordReset_validEmail_invokesAuthRepository() {
        val vm = createViewModel()
        vm.navigateToStep(WelcomeStep.AUTH)
        vm.openForgotPasswordDialog()

        assertTrue(vm.uiState.value.showForgotPasswordDialog)

        vm.updateResetEmail("forgot@safeher.app")
        vm.sendPasswordReset()

        assertFalse(vm.uiState.value.showForgotPasswordDialog)
        assertEquals("forgot@safeher.app", authRepo.lastResetEmail)
        assertNotNull(vm.uiState.value.infoMessage)
    }

    @Test
    fun firstLoginSetup_saveGuardian_addsToRepository_andUpdatesState() = runBlocking {
        val vm = createViewModel()
        vm.navigateToStep(WelcomeStep.FIRST_LOGIN_SETUP)
        assertFalse(vm.uiState.value.hasAddedGuardian)

        vm.openAddGuardianDialog()
        assertTrue(vm.uiState.value.showGuardianAddDialog)

        vm.updateGuardianName("Mom")
        vm.updateGuardianPhone("+15551234567")
        vm.updateGuardianRelationship("Mother")

        vm.saveQuickGuardian()

        assertFalse(vm.uiState.value.showGuardianAddDialog)
        assertTrue(vm.uiState.value.hasAddedGuardian)
        assertEquals(1, guardianRepo.getGuardianCount())
    }

    @Test
    fun firstLoginSetup_completeSetup_persistsSetupCompletedForUser() {
        val vm = createViewModel()
        vm.navigateToStep(WelcomeStep.FIRST_LOGIN_SETUP)

        val userId = "user_alpha"
        assertFalse(onboardingRepo.isUserSetupCompleted(userId))

        vm.completeSetup(userId)
        assertTrue(onboardingRepo.isUserSetupCompleted(userId))
    }

    @Test
    fun firstLoginSetup_skipSetup_defersSetupAndPersists() {
        val vm = createViewModel()
        vm.navigateToStep(WelcomeStep.FIRST_LOGIN_SETUP)

        val userId = "user_beta"
        assertFalse(onboardingRepo.isUserSetupCompleted(userId))

        vm.skipSetup(userId)
        assertTrue(onboardingRepo.isUserSetupCompleted(userId))
    }

    @Test
    fun accountIsolation_newAccountDoesNotInheritSetupStateOfAnotherAccount() {
        val user1 = "account_user_1"
        val user2 = "account_user_2"

        onboardingRepo.setUserSetupCompleted(user1, true)
        assertTrue(onboardingRepo.isUserSetupCompleted(user1))

        // user2 must NOT have setup completed
        assertFalse(onboardingRepo.isUserSetupCompleted(user2))
    }

    @Test
    fun signOut_clearsActiveSession_andTransitionsAuthStateToUnauthenticated() = runBlocking {
        authRepo.signInWithEmail("user@safeher.app", "pass123")
        assertTrue(authRepo.authState.value is AuthState.Authenticated)
        assertEquals("test_user_id", sessionManager.currentUserId)

        authRepo.signOut()
        assertEquals(AuthState.Unauthenticated, authRepo.authState.value)
        assertNull(sessionManager.currentUserId)
    }
}
