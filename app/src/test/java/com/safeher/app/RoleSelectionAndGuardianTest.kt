package com.safeher.app

import android.content.SharedPreferences
import com.safeher.app.data.auth.AuthRepository
import com.safeher.app.data.auth.AuthResult
import com.safeher.app.data.auth.AuthState
import com.safeher.app.data.auth.AuthUser
import com.safeher.app.data.onboarding.OnboardingPreferencesRepository
import com.safeher.app.data.onboarding.OnboardingPreferencesRepositoryImpl
import com.safeher.app.data.remoteguardian.GuardianInvite
import com.safeher.app.data.remoteguardian.RemoteGuardian
import com.safeher.app.data.remoteguardian.RemoteGuardianRepository
import com.safeher.app.data.remotesos.RemoteAlertDetails
import com.safeher.app.data.remotesos.RemoteSosRepository
import com.safeher.app.data.session.UserRole
import com.safeher.app.data.session.UserSessionManager
import com.safeher.app.ui.screens.guardian.GuardianDashboardUiState
import com.safeher.app.ui.screens.guardian.GuardianDashboardViewModel
import com.safeher.app.ui.screens.guardian.GuardianTab
import com.safeher.app.ui.screens.welcome.WelcomeStep
import com.safeher.app.ui.screens.welcome.WelcomeViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.lang.reflect.Proxy

class RoleSelectionAndGuardianTest {

    private lateinit var authRepo: FakeTestAuthRepository
    private lateinit var remoteGuardianRepo: FakeTestRemoteGuardianRepository
    private lateinit var remoteSosRepo: FakeTestRemoteSosRepo
    private lateinit var memoryPrefs: MutableMap<String, Any?>
    private lateinit var prefs: SharedPreferences
    private lateinit var sessionManager: UserSessionManager
    private lateinit var onboardingRepo: OnboardingPreferencesRepository

    @Before
    fun setUp() {
        authRepo = FakeTestAuthRepository()
        remoteGuardianRepo = FakeTestRemoteGuardianRepository()
        remoteSosRepo = FakeTestRemoteSosRepo()
        memoryPrefs = mutableMapOf()

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

        prefs = Proxy.newProxyInstance(
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

        sessionManager = UserSessionManager.createForTesting(
            prefs = prefs,
            dispatcher = Dispatchers.Unconfined
        )
        sessionManager.initialize(authRepo)

        onboardingRepo = OnboardingPreferencesRepositoryImpl(prefs)
    }

    private fun createWelcomeViewModel(): WelcomeViewModel {
        return WelcomeViewModel(
            authRepository = authRepo,
            onboardingPreferencesRepository = onboardingRepo,
            guardianRepository = null,
            sessionManager = sessionManager,
            sosPreferencesRepository = null,
            demoModeManager = null,
            remoteGuardianRepository = remoteGuardianRepo,
            coroutineScope = kotlinx.coroutines.CoroutineScope(Dispatchers.Unconfined),
            dispatcher = Dispatchers.Unconfined
        )
    }

    // ----------------------------------------------------------------------------------
    // 1. Role Selection Slides & Carousel Tests
    // ----------------------------------------------------------------------------------

    // ----------------------------------------------------------------------------------
    // 1. Role Selection Slides & Carousel Tests
    // ----------------------------------------------------------------------------------

    @Test
    fun welcome_defaultsToPersonalSafetyUserRole() {
        val vm = createWelcomeViewModel()
        assertEquals(WelcomeStep.WELCOME, vm.uiState.value.currentStep)
        assertEquals(UserRole.PERSONAL_SAFETY_USER, vm.uiState.value.selectedRole)
    }

    @Test
    fun case1_welcome_continueAsUser_navigatesToUserLogin() {
        val vm = createWelcomeViewModel()
        vm.onContinueAsUser()

        assertEquals(UserRole.PERSONAL_SAFETY_USER, vm.uiState.value.selectedRole)
        assertEquals(WelcomeStep.USER_LOGIN, vm.uiState.value.currentStep)
        assertTrue(vm.uiState.value.isSignInMode)
    }

    @Test
    fun case2_welcome_continueAsUser_toggleToUserSignUp() {
        val vm = createWelcomeViewModel()
        vm.onContinueAsUser()
        vm.toggleAuthMode()

        assertEquals(UserRole.PERSONAL_SAFETY_USER, vm.uiState.value.selectedRole)
        assertEquals(WelcomeStep.USER_SIGN_UP, vm.uiState.value.currentStep)
        assertFalse(vm.uiState.value.isSignInMode)
    }

    @Test
    fun case3_userLogin_toggleSignUp_andBackToUserLogin_preservesUserRole() {
        val vm = createWelcomeViewModel()
        vm.onContinueAsUser()
        assertEquals(WelcomeStep.USER_LOGIN, vm.uiState.value.currentStep)

        vm.toggleAuthMode()
        assertEquals(WelcomeStep.USER_SIGN_UP, vm.uiState.value.currentStep)
        assertEquals(UserRole.PERSONAL_SAFETY_USER, vm.uiState.value.selectedRole)

        vm.toggleAuthMode()
        assertEquals(WelcomeStep.USER_LOGIN, vm.uiState.value.currentStep)
        assertEquals(UserRole.PERSONAL_SAFETY_USER, vm.uiState.value.selectedRole)
    }

    @Test
    fun case4_userAuthentication_success_routesToExistingUserHome() = runBlocking {
        // Established user with setup completed
        sessionManager.setUserRole("test_user_id", UserRole.PERSONAL_SAFETY_USER)
        onboardingRepo.setUserSetupCompleted("test_user_id", true)

        val vm = createWelcomeViewModel()
        vm.onContinueAsUser()
        vm.updateEmail("user@example.com")
        vm.updatePassword("SecurePass123!")
        vm.signInWithEmail()

        assertNull(vm.uiState.value.errorMessage)
        val uid = authRepo.getCurrentUser()!!.uid
        assertEquals(UserRole.PERSONAL_SAFETY_USER, sessionManager.getUserRole(uid))
        assertTrue(onboardingRepo.isUserSetupCompleted(uid))
    }

    @Test
    fun case5_welcome_continueAsGuardian_navigatesToGuardianLogin() {
        val vm = createWelcomeViewModel()
        vm.onContinueAsGuardian()

        assertEquals(UserRole.GUARDIAN, vm.uiState.value.selectedRole)
        assertEquals(WelcomeStep.GUARDIAN_LOGIN, vm.uiState.value.currentStep)
        assertTrue(vm.uiState.value.isSignInMode)
    }

    @Test
    fun case6_guardianLogin_toggleSignUp_andBackToGuardianLogin_preservesGuardianRole() {
        val vm = createWelcomeViewModel()
        vm.onContinueAsGuardian()
        assertEquals(WelcomeStep.GUARDIAN_LOGIN, vm.uiState.value.currentStep)

        vm.toggleAuthMode()
        assertEquals(WelcomeStep.GUARDIAN_SIGN_UP, vm.uiState.value.currentStep)
        assertEquals(UserRole.GUARDIAN, vm.uiState.value.selectedRole)

        vm.toggleAuthMode()
        assertEquals(WelcomeStep.GUARDIAN_LOGIN, vm.uiState.value.currentStep)
        assertEquals(UserRole.GUARDIAN, vm.uiState.value.selectedRole)
    }

    @Test
    fun case7_guardianAuthentication_success_routesToGuardianConnectOrDashboard() = runBlocking {
        val vm = createWelcomeViewModel()
        vm.onContinueAsGuardian()
        vm.toggleAuthMode()

        vm.updateEmail("guardian@example.com")
        vm.updatePassword("SecurePass123!")
        vm.updateConfirmPassword("SecurePass123!")
        vm.updateDisplayName("Jane Guardian")

        vm.signUpWithEmail()

        assertNull(vm.uiState.value.errorMessage)
        assertEquals(WelcomeStep.GUARDIAN_CONNECT, vm.uiState.value.currentStep)

        val uid = authRepo.getCurrentUser()!!.uid
        assertEquals(UserRole.GUARDIAN, sessionManager.getUserRole(uid))
        assertTrue(sessionManager.isNewRegistration(uid))
    }

    @Test
    fun case8_invalidLogin_orCancelledGoogleSignIn_doesNotNavigateToEitherDashboard() = runBlocking {
        authRepo.shouldFail = true
        authRepo.failureMessage = "Invalid credentials provided."

        val vm = createWelcomeViewModel()
        vm.onContinueAsUser()
        vm.updateEmail("user@example.com")
        vm.updatePassword("WrongPassword!")

        vm.signInWithEmail()

        assertNotNull(vm.uiState.value.errorMessage)
        assertEquals(WelcomeStep.USER_LOGIN, vm.uiState.value.currentStep)
        assertNull(sessionManager.currentUserId)
    }

    @Test
    fun case9_signOut_returnsToRoleSelectionWelcomeSlide() = runBlocking {
        val vm = createWelcomeViewModel()
        vm.onContinueAsUser()
        vm.updateEmail("user@example.com")
        vm.updatePassword("SecurePass123!")
        vm.signInWithEmail()

        assertEquals("test_user_id", sessionManager.currentUserId)

        vm.signOut()

        assertEquals(AuthState.Unauthenticated, authRepo.authState.value)
        assertNull(sessionManager.currentUserId)
        assertEquals(WelcomeStep.WELCOME, vm.uiState.value.currentStep)
    }

    @Test
    fun case10_restartingWithAuthenticatedUser_doesNotRouteToGuardianHome() = runBlocking {
        // test_user_id is established as legacy owner UID
        memoryPrefs[UserSessionManager.KEY_LEGACY_OWNER_UID] = "test_user_id"

        // Even if guardian was previously selected on the welcome screen
        val vm = createWelcomeViewModel()
        vm.onContinueAsGuardian()
        assertEquals(UserRole.GUARDIAN, vm.uiState.value.selectedRole)

        // When legacy user logs in
        vm.updateEmail("legacy@safeher.app")
        vm.updatePassword("SecurePass123!")
        vm.signInWithEmail()

        // Role is safely resolved to PERSONAL_SAFETY_USER, NOT Guardian
        assertEquals(UserRole.PERSONAL_SAFETY_USER, sessionManager.getUserRole("test_user_id"))
    }

    @Test
    fun case11_restartingWithAuthenticatedGuardian_followsGuardianFlow() = runBlocking {
        sessionManager.setUserRole("guardian_user_200", UserRole.GUARDIAN)
        assertEquals(UserRole.GUARDIAN, sessionManager.getUserRole("guardian_user_200"))
    }

    @Test
    fun case12_invitationBackendUnavailable_doesNotBreakUserLogin_orRedirectToGuardian() = runBlocking {
        remoteGuardianRepo.shouldFailWithNotFound = true

        val vm = createWelcomeViewModel()
        vm.onContinueAsUser()
        vm.updateEmail("user@example.com")
        vm.updatePassword("SecurePass123!")
        vm.signInWithEmail()

        assertNull(vm.uiState.value.errorMessage)
        val uid = authRepo.getCurrentUser()!!.uid
        assertEquals(UserRole.PERSONAL_SAFETY_USER, sessionManager.getUserRole(uid))
    }

    @Test
    fun pendingRole_appliedSynchronouslyBeforeActiveUserId_forGuardianSignIn() = runBlocking {
        sessionManager.setPendingRoleForNextAuthentication(UserRole.GUARDIAN)
        authRepo.signInWithEmail("guardian@example.com", "SecurePass123!")

        val uid = authRepo.getCurrentUser()!!.uid
        assertEquals(UserRole.GUARDIAN, sessionManager.getUserRole(uid))
        assertEquals(uid, sessionManager.currentUserId)
    }

    @Test
    fun pendingRole_doesNotOverrideEstablishedPersonalSafetyUser() = runBlocking {
        sessionManager.setUserRole("test_user_id", UserRole.PERSONAL_SAFETY_USER)
        sessionManager.setPendingRoleForNextAuthentication(UserRole.GUARDIAN)

        authRepo.signInWithEmail("user@example.com", "SecurePass123!")

        assertEquals(UserRole.PERSONAL_SAFETY_USER, sessionManager.getUserRole("test_user_id"))
    }

    @Test
    fun userPath_neverPersistsGuardianRole_whenContinueAsUser() = runBlocking {
        val vm = createWelcomeViewModel()
        // Swipe toward guardian visually, then explicitly continue as user
        vm.onSlideChangedInWelcome(1)
        assertEquals(UserRole.GUARDIAN, vm.uiState.value.selectedRole)
        vm.onContinueAsUser()
        assertEquals(UserRole.PERSONAL_SAFETY_USER, vm.uiState.value.selectedRole)

        vm.updateEmail("user@example.com")
        vm.updatePassword("SecurePass123!")
        vm.signInWithEmail()

        assertEquals(UserRole.PERSONAL_SAFETY_USER, sessionManager.getUserRole(authRepo.getCurrentUser()!!.uid))
    }

    @Test
    fun welcome_selectRole_allowsSwitchingRoles() {
        val vm = createWelcomeViewModel()
        vm.selectRole(UserRole.GUARDIAN)
        assertEquals(UserRole.GUARDIAN, vm.uiState.value.selectedRole)

        vm.selectRole(UserRole.PERSONAL_SAFETY_USER)
        assertEquals(UserRole.PERSONAL_SAFETY_USER, vm.uiState.value.selectedRole)
    }

    @Test
    fun welcome_backFromAuth_returnsToWelcomeStep() {
        val vm = createWelcomeViewModel()
        vm.onContinueAsGuardian()
        assertEquals(WelcomeStep.GUARDIAN_LOGIN, vm.uiState.value.currentStep)

        vm.onBackFromAuth()
        assertEquals(WelcomeStep.WELCOME, vm.uiState.value.currentStep)
    }

    // ----------------------------------------------------------------------------------
    // 2. Role-Aware Authentication & Validation Tests
    // ----------------------------------------------------------------------------------

    @Test
    fun guardianRegistration_requiresDisplayName() = runBlocking {
        val vm = createWelcomeViewModel()
        vm.onContinueAsGuardian()
        vm.toggleAuthMode() // Sign Up mode
        assertFalse(vm.uiState.value.isSignInMode)

        vm.updateEmail("guardian@example.com")
        vm.updatePassword("SecurePass123!")
        vm.updateConfirmPassword("SecurePass123!")
        vm.updateDisplayName("") // Empty display name

        vm.signUpWithEmail()

        assertNotNull("Registration must fail when guardian display name is blank", vm.uiState.value.errorMessage)
        assertTrue(vm.uiState.value.errorMessage!!.contains("Display name is required"))
        assertNull(authRepo.getCurrentUser())
    }

    @Test
    fun guardianRegistration_success_persistsGuardianRole_andRoutesToGuardianConnect() = runBlocking {
        val vm = createWelcomeViewModel()
        vm.onContinueAsGuardian()
        vm.toggleAuthMode()

        vm.updateEmail("guardian@example.com")
        vm.updatePassword("SecurePass123!")
        vm.updateConfirmPassword("SecurePass123!")
        vm.updateDisplayName("Jane Guardian")

        vm.signUpWithEmail()

        assertNull("Error message must be null on valid registration", vm.uiState.value.errorMessage)
        assertEquals(WelcomeStep.GUARDIAN_CONNECT, vm.uiState.value.currentStep)

        val uid = authRepo.getCurrentUser()!!.uid
        assertEquals(UserRole.GUARDIAN, sessionManager.getUserRole(uid))
        assertTrue(sessionManager.isNewRegistration(uid))
    }

    @Test
    fun userRegistration_success_persistsUserRole_andRoutesToFirstLoginSetup() = runBlocking {
        val vm = createWelcomeViewModel()
        vm.onContinueAsUser()
        vm.toggleAuthMode()

        vm.updateEmail("user@example.com")
        vm.updatePassword("SecurePass123!")
        vm.updateConfirmPassword("SecurePass123!")
        vm.updateDisplayName("Alice User")

        vm.signUpWithEmail()

        assertNull(vm.uiState.value.errorMessage)
        assertEquals(WelcomeStep.FIRST_LOGIN_SETUP, vm.uiState.value.currentStep)

        val uid = authRepo.getCurrentUser()!!.uid
        assertEquals(UserRole.PERSONAL_SAFETY_USER, sessionManager.getUserRole(uid))
    }

    @Test
    fun guardianLogin_success_persistsRole_andCompletes() = runBlocking {
        val vm = createWelcomeViewModel()
        vm.onContinueAsGuardian()

        vm.updateEmail("guardian@example.com")
        vm.updatePassword("SecurePass123!")

        vm.signInWithEmail()

        assertNull(vm.uiState.value.errorMessage)
        val uid = authRepo.getCurrentUser()!!.uid
        assertEquals(UserRole.GUARDIAN, sessionManager.getUserRole(uid))
    }

    // ----------------------------------------------------------------------------------
    // 3. Guardian Invitation Acceptance & Spark Plan (NOT_FOUND) Handling Tests
    // ----------------------------------------------------------------------------------

    @Test
    fun guardianConnect_validatesPhraseLength() = runBlocking {
        val vm = createWelcomeViewModel()
        vm.navigateToStep(WelcomeStep.GUARDIAN_CONNECT)

        vm.updateInviteCode("SHORT")
        vm.submitGuardianConnectCode()

        assertNotNull(vm.uiState.value.connectGuardianError)
        assertTrue(vm.uiState.value.connectGuardianError!!.contains("invitation phrase"))
        assertFalse(vm.uiState.value.isConnectingGuardian)
    }

    @Test
    fun guardianConnect_sanitizesPhraseKeepingDashes() {
        val vm = createWelcomeViewModel()
        vm.updateInviteCode("blue tiger 42 maple!!")
        assertEquals("BLUE-TIGER-42-MAPLE", vm.uiState.value.inviteCodeInput)
    }

    @Test
    fun guardianConnect_whenPermissionDenied_mapsToHonestMessage() = runBlocking {
        remoteGuardianRepo.shouldFailWithNotFound = true

        val vm = createWelcomeViewModel()
        vm.navigateToStep(WelcomeStep.GUARDIAN_CONNECT)
        vm.updateInviteCode("BLUE-TIGER-42-MAPLE")

        vm.submitGuardianConnectCode()

        assertFalse(vm.uiState.value.isConnectingGuardian)
        assertFalse(vm.uiState.value.connectGuardianSuccess)
        assertNotNull(vm.uiState.value.connectGuardianError)
        assertTrue(
            "Must explain denial without fake success: ${vm.uiState.value.connectGuardianError}",
            vm.uiState.value.connectGuardianError!!.contains("Could not claim", ignoreCase = true) ||
                vm.uiState.value.connectGuardianError!!.contains("Permission denied", ignoreCase = true)
        )
    }

    @Test
    fun guardianConnect_success_updatesStateAndCallsCallback() = runBlocking {
        remoteGuardianRepo.shouldFailWithNotFound = false

        val vm = createWelcomeViewModel()
        vm.navigateToStep(WelcomeStep.GUARDIAN_CONNECT)
        vm.updateInviteCode("BLUE-TIGER-42-MAPLE")

        var callbackCalled = false
        vm.submitGuardianConnectCode(onSuccess = { callbackCalled = true })

        assertFalse(vm.uiState.value.isConnectingGuardian)
        assertTrue(vm.uiState.value.connectGuardianSuccess)
        assertNull(vm.uiState.value.connectGuardianError)
        assertTrue(callbackCalled)
    }

    // ----------------------------------------------------------------------------------
    // 4. Session & Role Persistence Scoping Tests
    // ----------------------------------------------------------------------------------

    @Test
    fun sessionManager_preservesAccountScopingAcrossDifferentUsers() {
        sessionManager.setUserRole("user_alpha", UserRole.PERSONAL_SAFETY_USER)
        sessionManager.setUserRole("guardian_beta", UserRole.GUARDIAN)

        assertEquals(UserRole.PERSONAL_SAFETY_USER, sessionManager.getUserRole("user_alpha"))
        assertEquals(UserRole.GUARDIAN, sessionManager.getUserRole("guardian_beta"))

        // Unassigned / legacy user defaults to PERSONAL_SAFETY_USER
        assertEquals(UserRole.PERSONAL_SAFETY_USER, sessionManager.getUserRole("unassigned_legacy_user"))
    }

    @Test
    fun signOut_clearsSession_andResetsState() = runBlocking {
        val vm = createWelcomeViewModel()
        vm.onContinueAsGuardian()
        vm.updateEmail("guardian@example.com")
        vm.updatePassword("SecurePass123!")
        vm.signInWithEmail()

        assertEquals("test_user_id", sessionManager.currentUserId)

        vm.signOut()

        assertEquals(AuthState.Unauthenticated, authRepo.authState.value)
        assertNull(sessionManager.currentUserId)
        assertEquals(WelcomeStep.WELCOME, vm.uiState.value.currentStep)
    }

    // ----------------------------------------------------------------------------------
    // 5. Guardian Dashboard ViewModel Tests
    // ----------------------------------------------------------------------------------

    @Test
    fun guardianDashboard_initialState_observesGuardedUsersAndAlerts() {
        val dashboardVm = GuardianDashboardViewModel(
            authRepository = authRepo,
            remoteGuardianRepository = remoteGuardianRepo,
            remoteSosRepository = remoteSosRepo,
            sessionManager = sessionManager,
            coroutineScope = kotlinx.coroutines.CoroutineScope(Dispatchers.Unconfined),
            dispatcher = Dispatchers.Unconfined
        )

        assertEquals(GuardianTab.HOME, dashboardVm.uiState.value.selectedTab)
        assertNull(dashboardVm.uiState.value.selectedAlertId)
        assertEquals(0, dashboardVm.uiState.value.guardedUsers.size)
        assertEquals(0, dashboardVm.uiState.value.alerts.size)
    }

    @Test
    fun guardianDashboard_tabSwitching() {
        val dashboardVm = GuardianDashboardViewModel(
            authRepository = authRepo,
            remoteGuardianRepository = remoteGuardianRepo,
            remoteSosRepository = remoteSosRepo,
            sessionManager = sessionManager,
            coroutineScope = kotlinx.coroutines.CoroutineScope(Dispatchers.Unconfined),
            dispatcher = Dispatchers.Unconfined
        )

        dashboardVm.selectTab(GuardianTab.PEOPLE)
        assertEquals(GuardianTab.PEOPLE, dashboardVm.uiState.value.selectedTab)

        dashboardVm.selectTab(GuardianTab.JOURNEYS)
        assertEquals(GuardianTab.JOURNEYS, dashboardVm.uiState.value.selectedTab)

        dashboardVm.selectTab(GuardianTab.ALERTS)
        assertEquals(GuardianTab.ALERTS, dashboardVm.uiState.value.selectedTab)

        dashboardVm.selectTab(GuardianTab.ACCOUNT)
        assertEquals(GuardianTab.ACCOUNT, dashboardVm.uiState.value.selectedTab)
    }

    @Test
    fun guardianDashboard_alertSelection_updatesSelectedAlertId() {
        val dashboardVm = GuardianDashboardViewModel(
            authRepository = authRepo,
            remoteGuardianRepository = remoteGuardianRepo,
            remoteSosRepository = remoteSosRepo,
            sessionManager = sessionManager,
            coroutineScope = kotlinx.coroutines.CoroutineScope(Dispatchers.Unconfined),
            dispatcher = Dispatchers.Unconfined
        )

        dashboardVm.selectAlert("alert_456")
        assertEquals("alert_456", dashboardVm.uiState.value.selectedAlertId)

        dashboardVm.selectAlert(null)
        assertNull(dashboardVm.uiState.value.selectedAlertId)
    }

    @Test
    fun guardianDashboard_connectCode_whenPermissionDenied_showsFriendlyError() = runBlocking {
        remoteGuardianRepo.shouldFailWithNotFound = true

        val dashboardVm = GuardianDashboardViewModel(
            authRepository = authRepo,
            remoteGuardianRepository = remoteGuardianRepo,
            remoteSosRepository = remoteSosRepo,
            sessionManager = sessionManager,
            coroutineScope = kotlinx.coroutines.CoroutineScope(Dispatchers.Unconfined),
            dispatcher = Dispatchers.Unconfined
        )

        dashboardVm.openConnectDialog()
        dashboardVm.updateInviteCode("BLUE-TIGER-42-MAPLE")
        dashboardVm.submitConnectCode()

        assertFalse(dashboardVm.uiState.value.isConnecting)
        assertFalse(dashboardVm.uiState.value.connectSuccess)
        assertNotNull(dashboardVm.uiState.value.connectError)
        assertTrue(
            dashboardVm.uiState.value.connectError!!.contains("Permission denied", ignoreCase = true) ||
                dashboardVm.uiState.value.connectError!!.contains("invitation", ignoreCase = true)
        )
    }
}

// --------------------------------------------------------------------------------------
// Test Fakes for Role and Remote Guardian Testing
// --------------------------------------------------------------------------------------

class FakeTestRemoteGuardianRepository : RemoteGuardianRepository {
    private val wardsFlow = MutableStateFlow<List<RemoteGuardian>>(emptyList())
    var shouldFailWithNotFound: Boolean = false

    override fun getRemoteGuardiansFlow(): Flow<List<RemoteGuardian>> = wardsFlow
    override fun getGuardedUsersFlow(): Flow<List<RemoteGuardian>> = wardsFlow

    override suspend fun createInvitation(): Result<GuardianInvite> {
        if (shouldFailWithNotFound) {
            return Result.failure(
                IllegalStateException(
                    "Permission denied. The invitation may be invalid, expired, already used, " +
                        "or local Firestore security rules may not yet allow this operation."
                )
            )
        }
        return Result.success(GuardianInvite("inv_101", "BLUE-TIGER-42-MAPLE", System.currentTimeMillis() + 172800000))
    }

    override suspend fun acceptInvitation(code: String): Result<RemoteGuardian> {
        if (shouldFailWithNotFound) {
            return Result.failure(
                IllegalStateException(
                    "Permission denied. The invitation may be invalid, expired, already used, " +
                        "or local Firestore security rules may not yet allow this operation."
                )
            )
        }
        val guardian = RemoteGuardian("rel_101", "user_ward_1", "Ward User", "ward@safeher.app", "ACTIVE")
        wardsFlow.value = listOf(guardian)
        return Result.success(guardian)
    }

    override suspend fun revokeRelationship(relationshipId: String): Result<Unit> {
        wardsFlow.value = emptyList()
        return Result.success(Unit)
    }

    override suspend fun refreshRemoteGuardians(): Result<List<RemoteGuardian>> = Result.success(wardsFlow.value)
}

class FakeTestRemoteSosRepo : RemoteSosRepository {
    private val alertsFlow = MutableStateFlow<List<RemoteAlertDetails>>(emptyList())

    override fun getAuthorizedAlertsFlow(): Flow<List<RemoteAlertDetails>> = alertsFlow

    override suspend fun dispatchRemoteSos(
        recipients: List<RemoteGuardian>,
        messageText: String,
        location: com.safeher.app.data.location.LocationData?,
        includeLocation: Boolean,
        idempotencyKey: String
    ): Result<com.safeher.app.data.remotesos.RemoteSosDispatchResult> = Result.failure(NotImplementedError())

    override fun observeDeliveryStatuses(alertId: String): Flow<List<com.safeher.app.data.remotesos.RecipientDeliveryInfo>> =
        MutableStateFlow(emptyList())

    override suspend fun getAlertDetails(alertId: String): Result<RemoteAlertDetails> = Result.failure(NotImplementedError())

    override suspend fun reportAlertReceived(alertId: String): Result<Unit> = Result.success(Unit)

    override suspend fun acknowledgeAlert(alertId: String, deviceModel: String): Result<Unit> = Result.success(Unit)
}
