package com.safeher.app

import android.content.SharedPreferences
import com.safeher.app.data.guardian.GuardianOperationResult
import com.safeher.app.data.guardian.GuardianRepositoryImpl
import com.safeher.app.data.journey.JourneyOperationResult
import com.safeher.app.data.journey.JourneyRepositoryImpl
import com.safeher.app.data.local.db.GuardianEntity
import com.safeher.app.data.local.db.JourneyEntity
import com.safeher.app.data.local.db.SafeHerDatabase
import com.safeher.app.data.session.UserSessionManager
import com.safeher.app.data.remoteguardian.GuardianInvite
import com.safeher.app.data.remoteguardian.RemoteGuardian
import com.safeher.app.data.remoteguardian.RemoteGuardianRepository
import com.safeher.app.ui.screens.account.AccountUiState
import com.safeher.app.ui.screens.account.AccountViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.lang.reflect.Proxy

class AccountIsolationRegressionTest {

    private lateinit var guardianDao: FakeDao
    private lateinit var journeyDao: FakeJourneyDao
    private lateinit var eventDao: FakeJourneyEventDao
    private lateinit var memoryPrefs: MutableMap<String, Any>
    private lateinit var prefsProxy: SharedPreferences
    private lateinit var sessionManager: UserSessionManager
    private lateinit var guardianRepo: GuardianRepositoryImpl
    private lateinit var journeyRepo: JourneyRepositoryImpl

    @Before
    fun setUp() {
        guardianDao = FakeDao()
        journeyDao = FakeJourneyDao()
        eventDao = FakeJourneyEventDao()
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
                "apply", "commit" -> null
                else -> editorProxy
            }
        } as SharedPreferences.Editor

        prefsProxy = Proxy.newProxyInstance(
            SharedPreferences::class.java.classLoader,
            arrayOf(SharedPreferences::class.java)
        ) { _, method, args ->
            when (method.name) {
                "getString" -> memoryPrefs[args[0] as String] as? String ?: args[1] as? String
                "getBoolean" -> memoryPrefs[args[0] as String] as? Boolean ?: args[1] as Boolean
                "edit" -> editorProxy
                else -> null
            }
        } as SharedPreferences

        sessionManager = UserSessionManager.createForTesting(
            prefs = prefsProxy,
            guardianDao = guardianDao,
            journeyDao = journeyDao,
            dispatcher = Dispatchers.Unconfined
        )

        guardianRepo = GuardianRepositoryImpl(
            dao = guardianDao,
            sessionManager = sessionManager,
            ioDispatcher = Dispatchers.Unconfined
        )

        journeyRepo = JourneyRepositoryImpl(
            dao = journeyDao,
            eventDao = eventDao,
            sessionManager = sessionManager,
            ioDispatcher = Dispatchers.Unconfined
        )
    }

    @Test
    fun legacyRecords_surviveMigrationAndDoNotLeakToNewRegistrations() = runBlocking {
        // 1. Simulate legacy records saved prior to account scoping (userId == null)
        val legacyGuardian = GuardianEntity(
            0, null, "Legacy Mother", "+919876543210", "Mother", true, 1000L, 1000L, 1000L
        )
        guardianDao.insert(legacyGuardian)

        val legacyJourney = JourneyEntity(
            0, null, "Office commute", "Home", "Office", 12.0, 77.0, 12.1, 77.1,
            1000L, 5000L, 0L, "COMPLETED", 0L, 0, null, 1000L, 1000L
        )
        journeyDao.insert(legacyJourney)

        assertEquals(1, guardianDao.unassignedGuardianCount)
        assertEquals(1, journeyDao.unassignedJourneyCount)

        // 2. A brand-new registration signs up
        val newUserId = "firebase_new_user_456"
        sessionManager.markAsNewRegistration(newUserId)
        sessionManager.setActiveUserId(newUserId)

        // Ensure newly registered account cannot claim legacy records
        val claimed = sessionManager.claimLegacyDataIfEligible(newUserId)
        assertFalse("New registration must never claim legacy data", claimed)

        // New user must see completely empty state
        val guardians = guardianRepo.getGuardians()
        val journeys = journeyRepo.getJourneys()
        assertTrue("Newly registered account must see empty guardian list", guardians.isEmpty())
        assertTrue("Newly registered account must see empty journey list", journeys.isEmpty())

        // 3. Original returning account signs in
        val originalUserId = "firebase_original_user_123"
        sessionManager.setActiveUserId(originalUserId)
        val claimedByOriginal = sessionManager.claimLegacyDataIfEligible(originalUserId)
        assertTrue("Original returning account claims legacy data", claimedByOriginal)

        // Original user sees their restored data
        val restoredGuardians = guardianRepo.getGuardians()
        val restoredJourneys = journeyRepo.getJourneys()
        assertEquals(1, restoredGuardians.size)
        assertEquals("Legacy Mother", restoredGuardians[0].name)
        assertEquals(1, restoredJourneys.size)
        assertEquals("Office commute", restoredJourneys[0].title)
    }

    @Test
    fun accountIsolation_accountBSeesNoDataFromAccountA() = runBlocking {
        // Account A signs in and creates data
        sessionManager.setActiveUserId("account_A")
        val addRes = guardianRepo.addGuardian("Alice Guardian", "+919111111111", "Sister")
        assertTrue(addRes is GuardianOperationResult.Success)

        val journeyRes = journeyRepo.startJourney(
            title = "Account A Journey",
            startLocation = "Library",
            destination = "Dorm",
            expectedArrivalTimestamp = System.currentTimeMillis() + 3600000L,
            startLatitude = 12.0,
            startLongitude = 77.0,
            destinationLatitude = 12.1,
            destinationLongitude = 77.1
        )
        assertTrue(journeyRes is JourneyOperationResult.Success)

        assertEquals(1, guardianRepo.getGuardianCount())
        assertNotNull(journeyRepo.getActiveJourney())

        // Account B signs in
        sessionManager.setActiveUserId("account_B")

        // Account B must see zero guardians and zero journeys
        assertEquals(0, guardianRepo.getGuardianCount())
        assertTrue(guardianRepo.getGuardians().isEmpty())
        assertNull(journeyRepo.getActiveJourney())
        assertTrue(journeyRepo.getJourneys().isEmpty())

        // Account B adds their own guardian
        guardianRepo.addGuardian("Bob Contact", "+919222222222", "Friend")
        assertEquals(1, guardianRepo.getGuardianCount())
        assertEquals("Bob Contact", guardianRepo.getGuardians()[0].name)

        // Switch back to Account A
        sessionManager.setActiveUserId("account_A")

        // Account A sees only their original data
        assertEquals(1, guardianRepo.getGuardianCount())
        assertEquals("Alice Guardian", guardianRepo.getGuardians()[0].name)
        assertNotNull(journeyRepo.getActiveJourney())
        assertEquals("Dorm", journeyRepo.getActiveJourney()?.destination)
    }

    @Test
    fun signingOut_immediatelyHidesPrivateData() = runBlocking {
        sessionManager.setActiveUserId("account_A")
        guardianRepo.addGuardian("Alice Guardian", "+919111111111", "Sister")
        journeyRepo.startJourney(
            title = "A Journey",
            startLocation = "A",
            destination = "B",
            expectedArrivalTimestamp = System.currentTimeMillis() + 3600000L,
            startLatitude = 12.0,
            startLongitude = 77.0,
            destinationLatitude = 12.1,
            destinationLongitude = 77.1
        )

        assertEquals(1, guardianRepo.getGuardiansFlow().first().size)
        assertNotNull(journeyRepo.getActiveJourneyFlow().first())

        // Sign out
        sessionManager.setActiveUserId(null)

        // Flows immediately emit empty data
        assertEquals(0, guardianRepo.getGuardiansFlow().first().size)
        assertEquals(0, guardianRepo.getGuardianCountFlow().first())
        assertNull(journeyRepo.getActiveJourneyFlow().first())
        assertTrue(journeyRepo.getJourneysFlow().first().isEmpty())

        // Methods return empty / error when signed out
        assertTrue(guardianRepo.getGuardians().isEmpty())
        assertEquals(0, guardianRepo.getGuardianCount())
        assertNull(journeyRepo.getActiveJourney())
        assertTrue(journeyRepo.getJourneys().isEmpty())

        val cannotAdd = guardianRepo.addGuardian("Test", "+919333333333", "Friend")
        assertTrue(cannotAdd is GuardianOperationResult.Error)

        val cannotStart = journeyRepo.startJourney(
            null, "A", "B", System.currentTimeMillis() + 10000, null, null, null, null
        )
        assertTrue(cannotStart is JourneyOperationResult.Error)
    }

    @Test
    fun eventTimelinesAndCheckIns_areAccountIsolated() = runBlocking {
        sessionManager.setActiveUserId("account_A")
        val startResult = journeyRepo.startJourney(
            title = "Timeline Test",
            startLocation = "Point 1",
            destination = "Point 2",
            expectedArrivalTimestamp = System.currentTimeMillis() + 3600000L,
            startLatitude = 12.0,
            startLongitude = 77.0,
            destinationLatitude = 12.1,
            destinationLongitude = 77.1
        )
        val journeyId = (startResult as JourneyOperationResult.Success).journeyId

        // Record a check in as Account A
        journeyRepo.checkIn(journeyId)

        val timelineA = journeyRepo.getEventsForJourney(journeyId)
        assertTrue("Account A must see timeline events (STARTED + CHECK_IN)", timelineA.size >= 2)

        // Switch to Account B
        sessionManager.setActiveUserId("account_B")

        // Account B cannot view events for Account A's journey
        val timelineB = journeyRepo.getEventsForJourney(journeyId)
        assertTrue("Account B must NOT see events from Account A's journey", timelineB.isEmpty())

        // Account B cannot check in to Account A's journey
        val checkInResB = journeyRepo.checkIn(journeyId)
        assertTrue("Account B cannot check in to another user's journey", checkInResB is JourneyOperationResult.Error)
    }

    @Test
    fun samePhoneNumber_canBeUsedByDifferentAccountsSafely() = runBlocking {
        val sharedPhone = "+919876543210"

        // Account A registers this number
        sessionManager.setActiveUserId("account_A")
        val resA = guardianRepo.addGuardian("Mother A", sharedPhone, "Family")
        assertTrue("Account A can add phone", resA is GuardianOperationResult.Success)

        // Account A adding the same number again fails as duplicate
        val dupA = guardianRepo.addGuardian("Mom duplicate", sharedPhone, "Family")
        assertTrue("Duplicate phone within same account must fail", dupA is GuardianOperationResult.DuplicatePhone)

        // Account B registers the same number for their own emergency contact
        sessionManager.setActiveUserId("account_B")
        val resB = guardianRepo.addGuardian("Mother B", sharedPhone, "Family")
        assertTrue("Account B can add same phone for their own circle without constraint collision", resB is GuardianOperationResult.Success)
    }

    @Test
    fun demoAccountSwitchingUi_hasBeenRemoved() {
        val stateFields = AccountUiState::class.java.declaredFields.map { it.name }
        assertFalse(
            "AccountUiState must no longer have activeDemoProfile",
            stateFields.contains("activeDemoProfile")
        )

        val viewModelMethods = AccountViewModel::class.java.declaredMethods.map { it.name }
        assertFalse(
            "AccountViewModel must no longer contain switchDemoProfile",
            viewModelMethods.contains("switchDemoProfile")
        )
    }

    @Test
    fun guardianCountFlow_executesOnIoDispatcherEvenWhenCollectedOnOtherThread() = runBlocking {
        var executionThreadName = ""
        val threadTrackingDao = object : com.safeher.app.data.local.db.GuardianDao by guardianDao {
            override fun getGuardianCountForUser(userId: String): Int {
                executionThreadName = Thread.currentThread().name
                return guardianDao.getGuardianCountForUser(userId)
            }
        }

        val testIoExecutor = java.util.concurrent.Executors.newSingleThreadExecutor { r ->
            Thread(r, "safeher-test-io-thread")
        }
        val testIoDispatcher = testIoExecutor.asCoroutineDispatcher()

        val repo = GuardianRepositoryImpl(
            dao = threadTrackingDao,
            sessionManager = sessionManager,
            ioDispatcher = testIoDispatcher
        )

        sessionManager.setActiveUserId("account_test_thread")

        val count = kotlinx.coroutines.withContext(Dispatchers.Default) {
            repo.getGuardianCountFlow().first()
        }

        assertEquals(0, count)
        assertTrue(
            "DAO query must execute on background IO dispatcher thread, but was: $executionThreadName",
            executionThreadName.contains("safeher-test-io-thread")
        )
        testIoExecutor.shutdown()
    }

    @Test
    fun productionBuild_demoModeIsStrictlyDisabledAndCannotBeEnabled() {
        val testPrefs = mutableMapOf<String, Any>("key_is_demo_mode" to true)
        val editor = Proxy.newProxyInstance(
            SharedPreferences.Editor::class.java.classLoader,
            arrayOf(SharedPreferences.Editor::class.java)
        ) { _, method, args ->
            when (method.name) {
                "remove" -> testPrefs.remove(args[0] as String)
                "putBoolean" -> testPrefs[args[0] as String] = args[1] as Boolean
            }
            Proxy.newProxyInstance(
                SharedPreferences.Editor::class.java.classLoader,
                arrayOf(SharedPreferences.Editor::class.java)
            ) { _, _, _ -> null }
        } as SharedPreferences.Editor

        val prefs = Proxy.newProxyInstance(
            SharedPreferences::class.java.classLoader,
            arrayOf(SharedPreferences::class.java)
        ) { _, method, args ->
            when (method.name) {
                "getBoolean" -> testPrefs[args[0] as String] as? Boolean ?: args[1] as Boolean
                "edit" -> editor
                else -> null
            }
        } as SharedPreferences

        // Simulate production configuration with explicitAllowedMode = false
        val prodDemoManager = com.safeher.app.data.demo.DemoModeManager(prefs, explicitAllowedMode = false)
        assertFalse("Demo mode must be false in production", prodDemoManager.isDemoMode.value)
        assertFalse("Stale key_is_demo_mode must be cleared in production", testPrefs.containsKey("key_is_demo_mode"))

        // Attempting to enable demo mode in production must fail
        prodDemoManager.setDemoMode(true)
        assertFalse("Demo mode must remain false when attempted in production", prodDemoManager.isDemoMode.value)
    }

    @Test
    fun googleSignIn_successCreatesRealSessionAndCorrectUid() = runBlocking {
        val authRepo = FakeAuthRepository()
        sessionManager.initialize(authRepo)

        val dummyContext = object : android.content.ContextWrapper(null) {}
        val res = authRepo.signInWithGoogle(dummyContext, "fake-client-id")
        assertTrue(res is com.safeher.app.data.auth.AuthResult.Success)
        val user = (res as com.safeher.app.data.auth.AuthResult.Success).user
        assertEquals("google_user", user.uid)
        assertEquals("google_user", sessionManager.currentUserId)
        assertFalse("Must not be demo UID", sessionManager.currentUserId == "demo_user_sender_101")
    }

    @Test
    fun googleSignIn_cancellationOrFailure_doesNotCreateDemoSession() = runBlocking {
        val cancellingAuthRepo = object : com.safeher.app.data.auth.AuthRepository by FakeAuthRepository() {
            override suspend fun signInWithGoogle(context: android.content.Context, webClientId: String): com.safeher.app.data.auth.AuthResult {
                return com.safeher.app.data.auth.AuthResult.Cancelled
            }
        }
        sessionManager.initialize(cancellingAuthRepo)
        val dummyContext = object : android.content.ContextWrapper(null) {}
        val res = cancellingAuthRepo.signInWithGoogle(dummyContext, "fake-client-id")
        assertTrue(res is com.safeher.app.data.auth.AuthResult.Cancelled)
        assertNull("Session must remain null on cancellation", sessionManager.currentUserId)

        val failingAuthRepo = object : com.safeher.app.data.auth.AuthRepository by FakeAuthRepository() {
            override suspend fun signInWithGoogle(context: android.content.Context, webClientId: String): com.safeher.app.data.auth.AuthResult {
                return com.safeher.app.data.auth.AuthResult.Error("OAuth error")
            }
        }
        sessionManager.initialize(failingAuthRepo)
        val failRes = failingAuthRepo.signInWithGoogle(dummyContext, "fake-client-id")
        assertTrue(failRes is com.safeher.app.data.auth.AuthResult.Error)
        assertNull("Session must remain null on failure", sessionManager.currentUserId)
    }

    @Test
    fun signOut_clearsActiveSessionWithoutDeletingLocalData() = runBlocking {
        sessionManager.setActiveUserId("active_user")
        val addRes = guardianRepo.addGuardian("Persistent Contact", "+919876543210", "Friend")
        assertTrue("Guardian addition must succeed", addRes is GuardianOperationResult.Success)
        assertEquals(1, guardianRepo.getGuardiansFlow().first().size)

        // Sign out
        sessionManager.setActiveUserId(null)
        assertNull(sessionManager.currentUserId)

        // While signed out, private flow returns 0
        assertEquals(0, guardianRepo.getGuardiansFlow().first().size)

        // Sign back in with the same user: contact is intact and preserved
        sessionManager.setActiveUserId("active_user")
        val restored = guardianRepo.getGuardiansFlow().first()
        assertEquals(1, restored.size)
        assertEquals("Persistent Contact", restored[0].name)
    }

    @Test
    fun demoUserUid_cannotPermanentlyLockLegacyDatabaseRecords() = runBlocking {
        // Simulate legacy unassigned records
        val legacyGuardian = GuardianEntity(
            0, null, "Mom Original", "+15559998", "Parent", true, 1000L, 1000L, 1000L
        )
        guardianDao.insert(legacyGuardian)

        // Suppose key_legacy_owner_uid was set to demo UID
        memoryPrefs[UserSessionManager.KEY_LEGACY_OWNER_UID] = "demo_user_sender_101"

        // Real Firebase user signs in
        val realUserUid = "real_firebase_user_abc"
        val claimed = sessionManager.claimLegacyDataIfEligible(realUserUid)

        assertTrue("Real user must be allowed to claim unassigned records even if demo UID was saved in prefs", claimed)
        assertEquals(realUserUid, memoryPrefs[UserSessionManager.KEY_LEGACY_OWNER_UID])
        val userGuardians = guardianDao.getGuardiansForUser(realUserUid)
        assertEquals(1, userGuardians.size)
        assertEquals("Mom Original", userGuardians[0].name)
    }

    @Test
    fun createInvitation_whenBackendFunctionMissing_terminatesLoadingAndPreservesSession() = runBlocking {
        val fakeAuth = FakeAuthRepository()
        fakeAuth.signInWithEmail("user@safeher.org", "password123")

        val missingFunctionGuardianRepo = object : RemoteGuardianRepository by FakeRemoteGuardianRepository() {
            override suspend fun createInvitation(): Result<GuardianInvite> {
                return Result.failure(
                    IllegalStateException("Remote guardian invitations are not configured yet. The backend Cloud Functions require deployment to enable secure invitation codes.")
                )
            }
        }

        val viewModel = AccountViewModel(
            authRepository = fakeAuth,
            remoteGuardianRepository = missingFunctionGuardianRepo,
            userSessionManager = sessionManager,
            coroutineScope = kotlinx.coroutines.CoroutineScope(Dispatchers.Unconfined)
        )

        // User is authenticated
        assertTrue(viewModel.uiState.value.authState is com.safeher.app.data.auth.AuthState.Authenticated)

        // User clicks "Invite Guardian"
        viewModel.openInviteDialog()

        // Wait/assert that invite failure terminates loading and dialog
        val state = viewModel.uiState.value
        assertFalse("Invite dialog must not be left open in endless spinner", state.showInviteDialog)
        assertFalse("Generating invite loading must terminate", state.isGeneratingInvite)
        assertNull("Generated invite must be null on failure", state.generatedInvite)
        assertNotNull("Error message must be present", state.errorMessage)
        assertTrue(
            "Error message must be friendly, got: ${state.errorMessage}",
            state.errorMessage!!.contains("Remote guardian invitations are not configured yet")
        )
        assertFalse("Must not expose raw NOT_FOUND", state.errorMessage!!.equals("NOT_FOUND"))

        // Session must be completely preserved
        assertTrue("Session must remain authenticated", viewModel.uiState.value.authState is com.safeher.app.data.auth.AuthState.Authenticated)
        assertEquals("user_123", (viewModel.uiState.value.authState as com.safeher.app.data.auth.AuthState.Authenticated).user.uid)
    }

    @Test
    fun acceptInvitation_whenFirestoreDeniesClaim_terminatesLoadingAndPreservesSession() = runBlocking {
        val fakeAuth = FakeAuthRepository()
        fakeAuth.signInWithEmail("user@safeher.org", "password123")

        val deniedGuardianRepo = object : RemoteGuardianRepository by FakeRemoteGuardianRepository() {
            override suspend fun acceptInvitation(code: String): Result<RemoteGuardian> {
                return Result.failure(
                    IllegalStateException(
                        "Permission denied. The invitation may be invalid, expired, already used, " +
                            "or local Firestore security rules may not yet allow this operation."
                    )
                )
            }
        }

        val viewModel = AccountViewModel(
            authRepository = fakeAuth,
            remoteGuardianRepository = deniedGuardianRepo,
            userSessionManager = sessionManager,
            coroutineScope = kotlinx.coroutines.CoroutineScope(Dispatchers.Unconfined)
        )

        viewModel.openAcceptInviteDialog()
        viewModel.updateInviteCode("BLUE-TIGER-42-MAPLE")
        viewModel.submitAcceptInvite()

        val state = viewModel.uiState.value
        assertFalse("Accepting invite loading must terminate", state.isAcceptingInvite)
        assertNotNull("Error message must be present", state.errorMessage)
        assertTrue(
            "Error message must explain denial, got: ${state.errorMessage}",
            state.errorMessage!!.contains("Permission denied", ignoreCase = true)
        )
        assertFalse("Must not claim success", state.showAcceptInviteDialog && state.acceptInviteError == null && state.infoMessage != null)

        // Session must be completely preserved
        assertTrue("Session must remain authenticated", viewModel.uiState.value.authState is com.safeher.app.data.auth.AuthState.Authenticated)
    }

    @Test
    fun demoUid_strictlyForbiddenFromClaimingLegacyProductionData() = runBlocking {
        // Setup unassigned legacy records
        val legacyGuardian = GuardianEntity(
            0, null, "Legacy Contact", "+919999988888", "Family", true, 1000L, 1000L, 1000L
        )
        guardianDao.insert(legacyGuardian)
        val legacyJourney = JourneyEntity(
            0, null, "Pre-migration Commute", "Station", "Home", 12.0, 77.0, 12.1, 77.1,
            1000L, 5000L, 0L, "COMPLETED", 0L, 0, null, 1000L, 1000L
        )
        journeyDao.insert(legacyJourney)

        assertEquals(1, guardianDao.unassignedGuardianCount)
        assertEquals(1, journeyDao.unassignedJourneyCount)

        // Attempt claim with canonical demo UID
        val demoClaim = sessionManager.claimLegacyDataIfEligible(com.safeher.app.data.demo.DemoProfile.USER.uid)
        assertFalse("DemoProfile.USER.uid must NEVER claim production legacy data", demoClaim)

        // Attempt claim with custom demo UID
        val customDemoClaim = sessionManager.claimLegacyDataIfEligible("demo_evaluator_999")
        assertFalse("Any demo_* UID must NEVER claim production legacy data", customDemoClaim)

        // Verify records remain strictly unassigned
        assertEquals(1, guardianDao.unassignedGuardianCount)
        assertEquals(1, journeyDao.unassignedJourneyCount)

        // Now a genuine real Firebase user signs in
        val realClaim = sessionManager.claimLegacyDataIfEligible("firebase_real_user_789")
        assertTrue("Legitimate returning production user can claim unassigned records", realClaim)
        assertEquals(0, guardianDao.unassignedGuardianCount)
        assertEquals(0, journeyDao.unassignedJourneyCount)
        assertEquals(1, guardianDao.getGuardiansForUser("firebase_real_user_789").size)
    }

    @Test
    fun homeViewModel_synchronizesUserNameWithAuthState_andResetsOnSignOut() = runBlocking {
        val fakeAuth = FakeAuthRepository()
        val homeViewModel = com.safeher.app.ui.screens.home.HomeViewModel(
            authRepository = fakeAuth,
            dispatcher = Dispatchers.Unconfined,
            coroutineScope = kotlinx.coroutines.CoroutineScope(Dispatchers.Unconfined)
        )

        // Initially unauthenticated
        assertEquals("Guest", homeViewModel.uiState.value.userName)

        // Sign in with verified account
        fakeAuth.signInWithEmail("priya@safeher.org", "password123")
        assertEquals("Test User", homeViewModel.uiState.value.userName)

        // Sign out
        fakeAuth.signOut()
        assertEquals("Guest", homeViewModel.uiState.value.userName)
    }

    @Test
    fun crossAccount_unauthorizedMutations_strictlyBlocked() = runBlocking {
        // Account A creates a journey
        sessionManager.setActiveUserId("account_victim")
        val startRes = journeyRepo.startJourney(
            title = "Victim Journey",
            startLocation = "A",
            destination = "B",
            expectedArrivalTimestamp = System.currentTimeMillis() + 600000L,
            startLatitude = 12.0,
            startLongitude = 77.0,
            destinationLatitude = 12.1,
            destinationLongitude = 77.1
        )
        val journeyId = (startRes as JourneyOperationResult.Success).journeyId

        // Account B attempts to mutate or delete Account A's journey
        sessionManager.setActiveUserId("account_attacker")

        // 1. Delete
        val deleted = journeyRepo.deleteJourney(journeyId)
        assertFalse("Attacker must not be able to delete another account's journey", deleted)

        // 2. Check-in
        val checkInRes = journeyRepo.checkIn(journeyId)
        assertTrue("Attacker must not be able to check-in to another account's journey", checkInRes is JourneyOperationResult.Error)

        // 3. Extend
        val extendRes = journeyRepo.extendJourney(journeyId, System.currentTimeMillis() + 1200000L)
        assertTrue("Attacker must not be able to extend another account's journey", extendRes is JourneyOperationResult.Error)

        // 4. Complete
        val completeRes = journeyRepo.completeJourney(journeyId)
        assertTrue("Attacker must not be able to complete another account's journey", completeRes is JourneyOperationResult.Error)

        // 5. Cancel
        val cancelRes = journeyRepo.cancelJourney(journeyId)
        assertTrue("Attacker must not be able to cancel another account's journey", cancelRes is JourneyOperationResult.Error)

        // 6. View events
        val events = journeyRepo.getEventsForJourney(journeyId)
        assertTrue("Attacker must not see another account's journey events", events.isEmpty())

        // Switch back to Account A and confirm journey is intact
        sessionManager.setActiveUserId("account_victim")
        val victimJourney = journeyRepo.getJourneyById(journeyId)
        assertNotNull("Victim's journey must remain intact", victimJourney)
        assertEquals("ACTIVE", victimJourney!!.status.name)
    }
}
