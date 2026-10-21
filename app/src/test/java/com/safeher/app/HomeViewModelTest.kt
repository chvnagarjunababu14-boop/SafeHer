package com.safeher.app

import com.safeher.app.data.guardian.Guardian
import com.safeher.app.data.remoteguardian.RemoteGuardian
import com.safeher.app.data.remotesos.RecipientDeliveryInfo
import com.safeher.app.data.remotesos.RemoteAlertDeliveryState
import com.safeher.app.ui.screens.home.ActiveDialog
import com.safeher.app.ui.screens.home.HomeTab
import com.safeher.app.ui.screens.home.HomeViewModel
import com.safeher.app.ui.screens.home.SosAlertChannel
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeViewModelTest {

    private fun createViewModel() = HomeViewModel(
        dispatcher = Dispatchers.Unconfined,
        coroutineScope = kotlinx.coroutines.CoroutineScope(Dispatchers.Unconfined)
    )

    @Test
    fun defaultState_isHomeTabAndIdleSos() {
        val viewModel = createViewModel()
        val state = viewModel.uiState.value

        assertEquals(HomeTab.HOME, state.currentTab)
        assertEquals("Guest", state.userName)
        assertEquals(0, state.guardianCount)
        assertFalse(state.isHoldingSos)
        assertEquals(0f, state.sosHoldProgress, 0.001f)
        assertEquals(ActiveDialog.None, state.activeDialog)
    }

    @Test
    fun selectTab_updatesCurrentTab() {
        val viewModel = createViewModel()

        viewModel.selectTab(HomeTab.MAP)
        assertEquals(HomeTab.MAP, viewModel.uiState.value.currentTab)

        viewModel.selectTab(HomeTab.JOURNEYS)
        assertEquals(HomeTab.JOURNEYS, viewModel.uiState.value.currentTab)

        viewModel.selectTab(HomeTab.CONTACTS)
        assertEquals(HomeTab.CONTACTS, viewModel.uiState.value.currentTab)

        viewModel.selectTab(HomeTab.MORE)
        assertEquals(HomeTab.MORE, viewModel.uiState.value.currentTab)

        viewModel.selectTab(HomeTab.HOME)
        assertEquals(HomeTab.HOME, viewModel.uiState.value.currentTab)
    }

    @Test
    fun sosHold_cancelResetsProgress() {
        val viewModel = createViewModel()

        viewModel.startSosHold()
        assertEquals("Keep holding…", viewModel.uiState.value.sosFeedbackText)

        viewModel.cancelSosHold()
        assertFalse(viewModel.uiState.value.isHoldingSos)
        assertEquals(0f, viewModel.uiState.value.sosHoldProgress, 0.001f)
        assertEquals("Released early • Cancelled", viewModel.uiState.value.sosFeedbackText)
    }

    @Test
    fun dialogManagement_openAndDismissWorkCorrectly() {
        val viewModel = createViewModel()

        viewModel.openDialog(ActiveDialog.EmergencyCallPreview)
        assertEquals(ActiveDialog.EmergencyCallPreview, viewModel.uiState.value.activeDialog)

        viewModel.dismissDialog()
        assertEquals(ActiveDialog.None, viewModel.uiState.value.activeDialog)

        viewModel.openDialog(ActiveDialog.LiveTrackingPreview)
        assertEquals(ActiveDialog.LiveTrackingPreview, viewModel.uiState.value.activeDialog)

        viewModel.dismissDialog()
        assertEquals(ActiveDialog.None, viewModel.uiState.value.activeDialog)
    }

    @Test
    fun sosSimulationDialog_displaysWithoutEmergencyDispatch() {
        val viewModel = createViewModel()

        viewModel.openDialog(ActiveDialog.SosSimulation)
        assertEquals(ActiveDialog.SosSimulation, viewModel.uiState.value.activeDialog)

        viewModel.dismissDialog()
        assertEquals(ActiveDialog.None, viewModel.uiState.value.activeDialog)
    }

    @Test
    fun guardianCount_updatesFromRepositoryFlow() {
        val repo = FakeGuardianRepository()
        val viewModel = HomeViewModel(
            guardianRepository = repo,
            dispatcher = Dispatchers.Unconfined,
            coroutineScope = kotlinx.coroutines.CoroutineScope(Dispatchers.Unconfined)
        )

        assertEquals(0, viewModel.uiState.value.guardianCount)

        kotlinx.coroutines.runBlocking {
            repo.addGuardian("Alice", "+15551234567", "Sister")
        }

        assertEquals(1, viewModel.uiState.value.guardianCount)
    }

    @Test
    fun activeJourney_updatesFromJourneyRepositoryFlow() = kotlinx.coroutines.runBlocking {
        val journeyRepo = FakeJourneyRepository()
        val viewModel = HomeViewModel(
            journeyRepository = journeyRepo,
            dispatcher = Dispatchers.Unconfined,
            coroutineScope = kotlinx.coroutines.CoroutineScope(Dispatchers.Unconfined)
        )

        assertNull(viewModel.uiState.value.activeJourney)

        journeyRepo.startJourney(
            title = "Trip to Office",
            startLocation = "Home",
            destination = "Office",
            expectedArrivalTimestamp = System.currentTimeMillis() + 100000L
        )

        val active = viewModel.uiState.value.activeJourney
        assertNotNull(active)
        assertEquals("Office", active!!.destination)
    }

    @Test
    fun sosWorkflow_opensAndLoadsEligibleGuardians() = kotlinx.coroutines.runBlocking {
        val guardianRepo = FakeGuardianRepository()
        guardianRepo.addGuardian("Alice", "+15551234567", "Sister")
        guardianRepo.addGuardian("Bob", "+442079460991", "Friend")
        guardianRepo.insertDirect(
            Guardian(id = 99L, name = "Dan", phoneNumber = "   ", relationship = "Other", isVerified = false)
        )

        val locationRepo = FakeLocationRepository()
        val viewModel = HomeViewModel(
            guardianRepository = guardianRepo,
            locationRepository = locationRepo,
            dispatcher = Dispatchers.Unconfined,
            coroutineScope = kotlinx.coroutines.CoroutineScope(Dispatchers.Unconfined)
        )

        viewModel.triggerSosWorkflow()

        val state = viewModel.uiState.value
        assertEquals(ActiveDialog.EmergencySos, state.activeDialog)
        assertTrue(state.sosWorkflow.isOpen)
        assertEquals(3, state.sosWorkflow.guardians.size)

        // Only 2 eligible guardians should be pre-selected
        assertEquals(2, state.sosWorkflow.selectedGuardianIds.size)
        assertEquals(2, state.sosWorkflow.selectedRecipients.size)
        assertTrue(state.sosWorkflow.hasEligibleRecipients)
        assertTrue(state.sosWorkflow.selectedRecipients.contains("+15551234567"))
        assertTrue(state.sosWorkflow.selectedRecipients.contains("+442079460991"))
    }

    @Test
    fun sosWorkflow_toggleGuardianSelection_updatesRecipients() = kotlinx.coroutines.runBlocking {
        val guardianRepo = FakeGuardianRepository()
        guardianRepo.addGuardian("Alice", "+15551234567", "Sister")
        guardianRepo.addGuardian("Bob", "+442079460991", "Friend")

        val viewModel = HomeViewModel(
            guardianRepository = guardianRepo,
            dispatcher = Dispatchers.Unconfined,
            coroutineScope = kotlinx.coroutines.CoroutineScope(Dispatchers.Unconfined)
        )

        viewModel.triggerSosWorkflow()
        val aliceId = viewModel.uiState.value.sosWorkflow.guardians.first { it.name == "Alice" }.id

        // Deselect Alice
        viewModel.toggleGuardianSelection(aliceId)
        assertEquals(1, viewModel.uiState.value.sosWorkflow.selectedGuardianIds.size)
        assertEquals(listOf("+442079460991"), viewModel.uiState.value.sosWorkflow.selectedRecipients)

        // Select Alice back
        viewModel.toggleGuardianSelection(aliceId)
        assertEquals(2, viewModel.uiState.value.sosWorkflow.selectedGuardianIds.size)
    }

    @Test
    fun sosWorkflow_selectAllAndDeselectAll_togglesAllEligible() = kotlinx.coroutines.runBlocking {
        val guardianRepo = FakeGuardianRepository()
        guardianRepo.addGuardian("Alice", "+15551234567", "Sister")
        guardianRepo.addGuardian("Bob", "+442079460991", "Friend")

        val viewModel = HomeViewModel(
            guardianRepository = guardianRepo,
            dispatcher = Dispatchers.Unconfined,
            coroutineScope = kotlinx.coroutines.CoroutineScope(Dispatchers.Unconfined)
        )

        viewModel.triggerSosWorkflow()

        viewModel.deselectAllGuardians()
        assertTrue(viewModel.uiState.value.sosWorkflow.selectedGuardianIds.isEmpty())
        assertFalse(viewModel.uiState.value.sosWorkflow.hasEligibleRecipients)

        viewModel.selectAllGuardians()
        assertEquals(2, viewModel.uiState.value.sosWorkflow.selectedGuardianIds.size)
        assertTrue(viewModel.uiState.value.sosWorkflow.hasEligibleRecipients)
    }

    @Test
    fun sosWorkflow_unverifiedGuardians_remainVisiblyUnverified() = kotlinx.coroutines.runBlocking {
        val guardianRepo = FakeGuardianRepository()
        guardianRepo.addGuardian("Sister", "+15551234567", "Family")

        val viewModel = HomeViewModel(
            guardianRepository = guardianRepo,
            dispatcher = Dispatchers.Unconfined,
            coroutineScope = kotlinx.coroutines.CoroutineScope(Dispatchers.Unconfined)
        )

        viewModel.triggerSosWorkflow()
        val guardian = viewModel.uiState.value.sosWorkflow.guardians.first()

        assertFalse("Unverified guardian must have isVerified=false", guardian.isVerified)
        assertEquals(
            com.safeher.app.data.guardian.GuardianVerificationState.UNVERIFIED,
            guardian.verificationState
        )
    }

    @Test
    fun sosWorkflow_withFreshLocation_messageContainsCoordinates() = kotlinx.coroutines.runBlocking {
        val guardianRepo = FakeGuardianRepository()
        guardianRepo.addGuardian("Mom", "+15551234567", "Parent")

        val locationRepo = FakeLocationRepository()
        val viewModel = HomeViewModel(
            guardianRepository = guardianRepo,
            locationRepository = locationRepo,
            dispatcher = Dispatchers.Unconfined,
            coroutineScope = kotlinx.coroutines.CoroutineScope(Dispatchers.Unconfined)
        )

        viewModel.triggerSosWorkflow()

        val message = viewModel.uiState.value.sosWorkflow.editableMessage
        assertTrue("Message must contain google maps link", message.contains("https://maps.google.com/?q=12.971600,77.594600"))
        assertTrue("Message must contain accuracy", message.contains("±15m"))
    }

    @Test
    fun sosWorkflow_withLocationDenied_messageContainsSafeFallback() = kotlinx.coroutines.runBlocking {
        val guardianRepo = FakeGuardianRepository()
        guardianRepo.addGuardian("Mom", "+15551234567", "Parent")

        val locationRepo = FakeLocationRepository().apply {
            resultToReturn = com.safeher.app.data.location.LocationResult.PermissionDenied(false)
        }
        val viewModel = HomeViewModel(
            guardianRepository = guardianRepo,
            locationRepository = locationRepo,
            dispatcher = Dispatchers.Unconfined,
            coroutineScope = kotlinx.coroutines.CoroutineScope(Dispatchers.Unconfined)
        )

        viewModel.triggerSosWorkflow()

        val message = viewModel.uiState.value.sosWorkflow.editableMessage
        assertFalse(message.contains("maps.google.com"))
        assertTrue(message.contains("Location: Unavailable"))
    }

    @Test
    fun sosWorkflow_toggleIncludeLocation_updatesMessageText() = kotlinx.coroutines.runBlocking {
        val guardianRepo = FakeGuardianRepository()
        guardianRepo.addGuardian("Mom", "+15551234567", "Parent")

        val locationRepo = FakeLocationRepository()
        val viewModel = HomeViewModel(
            guardianRepository = guardianRepo,
            locationRepository = locationRepo,
            dispatcher = Dispatchers.Unconfined,
            coroutineScope = kotlinx.coroutines.CoroutineScope(Dispatchers.Unconfined)
        )

        viewModel.triggerSosWorkflow()
        assertTrue(viewModel.uiState.value.sosWorkflow.editableMessage.contains("maps.google.com"))

        viewModel.toggleIncludeLocation(false)
        assertFalse(viewModel.uiState.value.sosWorkflow.includeLocation)
        assertFalse(viewModel.uiState.value.sosWorkflow.editableMessage.contains("maps.google.com"))
        assertFalse(viewModel.uiState.value.sosWorkflow.editableMessage.contains("Location: Unavailable"))
    }

    @Test
    fun sosWorkflow_updateEditableMessage_preservesEdits() = kotlinx.coroutines.runBlocking {
        val viewModel = HomeViewModel(
            dispatcher = Dispatchers.Unconfined,
            coroutineScope = kotlinx.coroutines.CoroutineScope(Dispatchers.Unconfined)
        )

        viewModel.triggerSosWorkflow()
        viewModel.updateEditableMessage("Custom urgent message: I am on bus 42.")

        assertEquals("Custom urgent message: I am on bus 42.", viewModel.uiState.value.sosWorkflow.editableMessage)
    }

    @Test
    fun sosWorkflow_cancel_resetsWorkflowState() = kotlinx.coroutines.runBlocking {
        val viewModel = HomeViewModel(
            dispatcher = Dispatchers.Unconfined,
            coroutineScope = kotlinx.coroutines.CoroutineScope(Dispatchers.Unconfined)
        )

        viewModel.triggerSosWorkflow()
        assertTrue(viewModel.uiState.value.sosWorkflow.isOpen)

        viewModel.cancelSosWorkflow()
        assertFalse(viewModel.uiState.value.sosWorkflow.isOpen)
        assertEquals(ActiveDialog.None, viewModel.uiState.value.activeDialog)
    }

    @Test
    fun sosWorkflow_launchAndErrorHandling_guardsAgainstDoubleTap() = kotlinx.coroutines.runBlocking {
        val viewModel = HomeViewModel(
            dispatcher = Dispatchers.Unconfined,
            coroutineScope = kotlinx.coroutines.CoroutineScope(Dispatchers.Unconfined)
        )

        viewModel.triggerSosWorkflow()
        assertFalse(viewModel.uiState.value.sosWorkflow.isLaunchingComposer)

        viewModel.startComposerLaunch()
        assertTrue(viewModel.uiState.value.sosWorkflow.isLaunchingComposer)

        viewModel.onComposerFailed("No compatible SMS application found")
        assertFalse(viewModel.uiState.value.sosWorkflow.isLaunchingComposer)
        assertEquals("No compatible SMS application found", viewModel.uiState.value.sosWorkflow.handoffError)

        viewModel.onMessageCopied()
        assertTrue(viewModel.uiState.value.sosWorkflow.isCopiedToClipboard)
    }

    @Test
    fun sosWorkflow_channelSwitching_togglesBetweenRemoteAndSms() = kotlinx.coroutines.runBlocking {
        val viewModel = HomeViewModel(
            dispatcher = Dispatchers.Unconfined,
            coroutineScope = kotlinx.coroutines.CoroutineScope(Dispatchers.Unconfined)
        )

        viewModel.triggerSosWorkflow()
        viewModel.setSosAlertChannel(SosAlertChannel.REMOTE_FCM)
        assertEquals(SosAlertChannel.REMOTE_FCM, viewModel.uiState.value.sosWorkflow.alertChannel)

        viewModel.setSosAlertChannel(SosAlertChannel.SYSTEM_SMS)
        assertEquals(SosAlertChannel.SYSTEM_SMS, viewModel.uiState.value.sosWorkflow.alertChannel)
    }

    @Test
    fun sosWorkflow_remoteGuardians_selectionAndToggle() = kotlinx.coroutines.runBlocking {
        val remoteRepo = FakeRemoteGuardianRepository()
        remoteRepo.addRemoteGuardian(RemoteGuardian("r1", "uid_alice", "Alice", "alice@safeher.org", "ACTIVE"))
        remoteRepo.addRemoteGuardian(RemoteGuardian("r2", "uid_bob", "Bob", "bob@safeher.org", "ACTIVE"))

        val viewModel = HomeViewModel(
            remoteGuardianRepository = remoteRepo,
            dispatcher = Dispatchers.Unconfined,
            coroutineScope = kotlinx.coroutines.CoroutineScope(Dispatchers.Unconfined)
        )

        viewModel.triggerSosWorkflow()
        val state = viewModel.uiState.value.sosWorkflow
        assertEquals(2, state.remoteGuardians.size)
        assertEquals(2, state.selectedRemoteGuardianUids.size)
        assertTrue(state.hasEligibleRemoteRecipients)

        viewModel.toggleRemoteGuardianSelection("uid_alice")
        assertEquals(1, viewModel.uiState.value.sosWorkflow.selectedRemoteGuardianUids.size)
        assertFalse(viewModel.uiState.value.sosWorkflow.selectedRemoteGuardianUids.contains("uid_alice"))

        viewModel.deselectAllRemoteGuardians()
        assertEquals(0, viewModel.uiState.value.sosWorkflow.selectedRemoteGuardianUids.size)
        assertFalse(viewModel.uiState.value.sosWorkflow.hasEligibleRemoteRecipients)

        viewModel.selectAllRemoteGuardians()
        assertEquals(2, viewModel.uiState.value.sosWorkflow.selectedRemoteGuardianUids.size)
        assertTrue(viewModel.uiState.value.sosWorkflow.hasEligibleRemoteRecipients)
    }

    @Test
    fun sosWorkflow_dispatchRemoteSos_successSetsActiveAlertAndTracksDelivery() = kotlinx.coroutines.runBlocking {
        val remoteGuardianRepo = FakeRemoteGuardianRepository()
        remoteGuardianRepo.addRemoteGuardian(RemoteGuardian("r1", "uid_guardian", "Guardian", "g@safeher.org", "ACTIVE"))

        val remoteSosRepo = FakeRemoteSosRepository()

        val viewModel = HomeViewModel(
            remoteGuardianRepository = remoteGuardianRepo,
            remoteSosRepository = remoteSosRepo,
            dispatcher = Dispatchers.Unconfined,
            coroutineScope = kotlinx.coroutines.CoroutineScope(Dispatchers.Unconfined)
        )

        viewModel.triggerSosWorkflow()
        viewModel.dispatchRemoteSosAlert()

        val state = viewModel.uiState.value.sosWorkflow
        assertTrue(state.isRemoteDispatchSuccessful)
        assertEquals("alert_100", state.activeRemoteAlertId)
        assertFalse(state.isDispatchingRemote)
        assertEquals(1, state.remoteDeliveryStatuses.size)
        assertEquals(RemoteAlertDeliveryState.QUEUED, state.remoteDeliveryStatuses[0].status)

        // Simulate status transition to ACKNOWLEDGED
        remoteSosRepo.updateDeliveryStatuses(
            listOf(RecipientDeliveryInfo("uid_guardian", "Guardian", RemoteAlertDeliveryState.ACKNOWLEDGED))
        )
        assertEquals(RemoteAlertDeliveryState.ACKNOWLEDGED, viewModel.uiState.value.sosWorkflow.remoteDeliveryStatuses[0].status)
    }

    @Test
    fun sosWorkflow_dispatchRemoteSos_failurePreservesSmsFallbackOption() = kotlinx.coroutines.runBlocking {
        val viewModel = HomeViewModel(
            dispatcher = Dispatchers.Unconfined,
            coroutineScope = kotlinx.coroutines.CoroutineScope(Dispatchers.Unconfined)
        )

        viewModel.triggerSosWorkflow()
        // No remote guardians selected -> should set error and keep SMS option available
        viewModel.dispatchRemoteSosAlert()

        val state = viewModel.uiState.value.sosWorkflow
        assertFalse(state.isRemoteDispatchSuccessful)
        assertNotNull(state.remoteDispatchError)

        // User can seamlessly switch to SMS channel
        viewModel.setSosAlertChannel(SosAlertChannel.SYSTEM_SMS)
        assertEquals(SosAlertChannel.SYSTEM_SMS, viewModel.uiState.value.sosWorkflow.alertChannel)
    }
}


