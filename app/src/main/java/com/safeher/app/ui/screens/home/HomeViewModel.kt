package com.safeher.app.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.safeher.app.data.guardian.Guardian
import com.safeher.app.data.guardian.GuardianRepository
import com.safeher.app.data.journey.Journey
import com.safeher.app.data.journey.JourneyRepository
import com.safeher.app.data.location.LocationData
import com.safeher.app.data.location.LocationRepository
import com.safeher.app.data.location.LocationResult
import com.safeher.app.data.sos.DirectSosManager
import com.safeher.app.data.sos.DirectSosState
import com.safeher.app.data.sos.EmergencyMessageBuilder
import com.safeher.app.data.sos.SosPreferences
import com.safeher.app.data.sos.SosPreferencesRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class HomeTab {
    HOME,
    MAP,
    JOURNEYS,
    CONTACTS,
    MORE
}

sealed interface ActiveDialog {
    data object None : ActiveDialog
    data object SosSimulation : ActiveDialog
    data object EmergencySos : ActiveDialog
    data object DirectSos : ActiveDialog
    data object SosSettings : ActiveDialog
    data object AccessibleSosConfirmation : ActiveDialog
    data object LiveTrackingPreview : ActiveDialog
    data object TrustedContactsPreview : ActiveDialog
    data object SafeJourneyPreview : ActiveDialog
    data object EmergencyCallPreview : ActiveDialog
    data object ShareStatusPreview : ActiveDialog
    data object CheckInPreview : ActiveDialog
    data object SafetyTipsPreview : ActiveDialog
    data object ProfileSettingsPreview : ActiveDialog
}

enum class SosAlertChannel {
    REMOTE_FCM,
    SYSTEM_SMS
}

data class SosWorkflowState(
    val isOpen: Boolean = false,
    val alertChannel: SosAlertChannel = SosAlertChannel.REMOTE_FCM,
    val guardians: List<Guardian> = emptyList(),
    val selectedGuardianIds: Set<Long> = emptySet(),
    val remoteGuardians: List<com.safeher.app.data.remoteguardian.RemoteGuardian> = emptyList(),
    val selectedRemoteGuardianUids: Set<String> = emptySet(),
    val isAcquiringLocation: Boolean = false,
    val locationData: LocationData? = null,
    val locationError: String? = null,
    val includeLocation: Boolean = true,
    val customNote: String = "",
    val editableMessage: String = "",
    val isLaunchingComposer: Boolean = false,
    val handoffNotice: String? = null,
    val handoffError: String? = null,
    val isCopiedToClipboard: Boolean = false,
    val isDispatchingRemote: Boolean = false,
    val activeRemoteAlertId: String? = null,
    val remoteDeliveryStatuses: List<com.safeher.app.data.remotesos.RecipientDeliveryInfo> = emptyList(),
    val remoteDispatchError: String? = null,
    val isRemoteDispatchSuccessful: Boolean = false,
    val isDemoMode: Boolean = false
) {
    val selectedGuardians: List<Guardian>
        get() = guardians.filter { selectedGuardianIds.contains(it.id) }

    val selectedRecipients: List<String>
        get() = EmergencyMessageBuilder.cleanAndDeduplicateRecipients(
            selectedGuardians.map { it.phoneNumber }
        )

    val hasEligibleRecipients: Boolean
        get() = selectedRecipients.isNotEmpty()

    val selectedRemoteGuardians: List<com.safeher.app.data.remoteguardian.RemoteGuardian>
        get() = remoteGuardians.filter { selectedRemoteGuardianUids.contains(it.guardianUid) }

    val hasEligibleRemoteRecipients: Boolean
        get() = selectedRemoteGuardians.isNotEmpty()
}

data class HomeUiState(
    val currentTab: HomeTab = HomeTab.HOME,
    val userName: String = "Guest",
    val isProtectionActive: Boolean = false,
    val guardianCount: Int = 0,
    val activeJourney: Journey? = null,
    val currentTimeMillis: Long = System.currentTimeMillis(),
    val isHoldingSos: Boolean = false,
    val sosHoldProgress: Float = 0f,
    val sosFeedbackText: String = "Press and hold for SOS",
    val activeDialog: ActiveDialog = ActiveDialog.None,
    val isDemoMode: Boolean = false,
    val sosWorkflow: SosWorkflowState = SosWorkflowState(),
    val directSosState: DirectSosState = DirectSosState.Idle,
    val sosPreferences: SosPreferences = SosPreferences(),
    val localGuardians: List<Guardian> = emptyList()
)

class HomeViewModel(
    private val guardianRepository: GuardianRepository? = null,
    private val journeyRepository: JourneyRepository? = null,
    private val locationRepository: LocationRepository? = null,
    private val authRepository: com.safeher.app.data.auth.AuthRepository? = null,
    private val remoteGuardianRepository: com.safeher.app.data.remoteguardian.RemoteGuardianRepository? = null,
    private val remoteSosRepository: com.safeher.app.data.remotesos.RemoteSosRepository? = null,
    private val demoModeManager: com.safeher.app.data.demo.DemoModeManager? = null,
    private val directSosManager: DirectSosManager? = null,
    private val sosPreferencesRepository: SosPreferencesRepository? = null,
    private val dispatcher: kotlinx.coroutines.CoroutineDispatcher = kotlinx.coroutines.Dispatchers.Main,
    coroutineScope: kotlinx.coroutines.CoroutineScope? = null
) : ViewModel() {

    private val scope: kotlinx.coroutines.CoroutineScope = coroutineScope ?: viewModelScope

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private var clockJob: Job? = null
    private var deliveryStatusJob: Job? = null

    init {
        guardianRepository?.getGuardianCountFlow()
            ?.onEach { count ->
                _uiState.update { it.copy(guardianCount = count) }
            }
            ?.launchIn(scope)

        guardianRepository?.getGuardiansFlow()
            ?.onEach { list ->
                _uiState.update { it.copy(localGuardians = list) }
            }
            ?.launchIn(scope)

        journeyRepository?.getActiveJourneyFlow()
            ?.onEach { active ->
                _uiState.update { it.copy(activeJourney = active) }
            }
            ?.launchIn(scope)

        authRepository?.authState
            ?.onEach { auth ->
                val name = when (auth) {
                    is com.safeher.app.data.auth.AuthState.Authenticated -> auth.user.displayLabel
                    else -> "Guest"
                }
                _uiState.update { it.copy(userName = name) }
            }
            ?.launchIn(scope)

        directSosManager?.state
            ?.onEach { dState ->
                _uiState.update { current ->
                    val nextDialog = if (dState is DirectSosState.Idle && current.activeDialog == ActiveDialog.DirectSos) {
                        ActiveDialog.None
                    } else if (dState !is DirectSosState.Idle) {
                        ActiveDialog.DirectSos
                    } else {
                        current.activeDialog
                    }
                    current.copy(
                        directSosState = dState,
                        activeDialog = nextDialog
                    )
                }
            }
            ?.launchIn(scope)

        sosPreferencesRepository?.preferencesFlow
            ?.onEach { prefs ->
                _uiState.update { it.copy(sosPreferences = prefs) }
            }
            ?.launchIn(scope)

        if (com.safeher.app.BuildConfig.IS_DEMO_DEFAULT) {
            demoModeManager?.isDemoMode
                ?.onEach { isDemo ->
                    _uiState.update { current ->
                        current.copy(
                            isDemoMode = isDemo,
                            sosWorkflow = current.sosWorkflow.copy(isDemoMode = isDemo)
                        )
                    }
                }
                ?.launchIn(scope)
        }

        startClockTicker()
    }

    private fun startClockTicker() {
        clockJob?.cancel()
        clockJob = scope.launch(dispatcher) {
            while (isActive) {
                delay(1000L)
                val now = System.currentTimeMillis()
                val currentActive = _uiState.value.activeJourney

                if (currentActive != null &&
                    currentActive.status == com.safeher.app.data.journey.JourneyStatus.ACTIVE &&
                    now >= currentActive.expectedArrivalTimestamp
                ) {
                    journeyRepository?.refreshJourneyStatus()
                }

                _uiState.update { it.copy(currentTimeMillis = now) }
            }
        }
    }

    private var holdJob: Job? = null
    private val requiredHoldDurationMs = 2000L
    private val tickIntervalMs = 50L

    fun selectTab(tab: HomeTab) {
        _uiState.value = _uiState.value.copy(currentTab = tab)
    }

    fun startSosHold() {
        holdJob?.cancel()
        _uiState.value = _uiState.value.copy(
            isHoldingSos = true,
            sosHoldProgress = 0f,
            sosFeedbackText = "Keep holding…"
        )

        holdJob = scope.launch(dispatcher) {
            val totalTicks = requiredHoldDurationMs / tickIntervalMs
            var currentTick = 0
            while (currentTick < totalTicks) {
                delay(tickIntervalMs)
                currentTick++
                val progress = (currentTick.toFloat() / totalTicks).coerceIn(0f, 1f)
                _uiState.value = _uiState.value.copy(sosHoldProgress = progress)
            }
            // Deliberate hold completed: trigger the user-confirmed emergency workflow!
            triggerSosWorkflow()
        }
    }

    fun cancelSosHold() {
        if (_uiState.value.isHoldingSos) {
            holdJob?.cancel()
            holdJob = null
            _uiState.value = _uiState.value.copy(
                isHoldingSos = false,
                sosHoldProgress = 0f,
                sosFeedbackText = "Released early • Cancelled"
            )
        }
    }

    /**
     * Activates the emergency SOS workflow.
     * When Direct SOS is authorized and configured, initiates the cancellable direct SMS flow.
     * Otherwise prompts the user with setup requirements and offers the manual SMS fallback.
     */
    fun triggerSosWorkflow() {
        holdJob = null
        _uiState.update {
            it.copy(
                isHoldingSos = false,
                sosHoldProgress = 0f,
                sosFeedbackText = "Press and hold for SOS"
            )
        }

        if (directSosManager != null) {
            val prefs = sosPreferencesRepository?.getPreferences() ?: SosPreferences()
            if (prefs.isDirectSmsConsentGiven && prefs.isDirectSmsEnabled) {
                _uiState.update { it.copy(activeDialog = ActiveDialog.DirectSos) }
                directSosManager.startCancellableSos()
            } else {
                // Do not attempt automatic SMS without consent; guide the user to configure SOS.
                openSosSettings()
            }
        } else {
            openManualSosWorkflow()
        }
    }

    fun cancelDirectSosCountdown() {
        directSosManager?.cancelCountdown()
        _uiState.update { it.copy(activeDialog = ActiveDialog.None) }
    }

    fun sendDirectSosImmediately() {
        directSosManager?.triggerImmediateSos()
    }

    fun dismissDirectSos() {
        directSosManager?.reset()
        _uiState.update { it.copy(activeDialog = ActiveDialog.None) }
    }

    fun openSosSettings() {
        directSosManager?.reset()
        _uiState.update { it.copy(activeDialog = ActiveDialog.SosSettings) }
    }

    fun closeSosSettings() {
        _uiState.update { it.copy(activeDialog = ActiveDialog.None) }
    }

    fun openAccessibleSosConfirmation() {
        _uiState.update { it.copy(activeDialog = ActiveDialog.AccessibleSosConfirmation) }
    }

    fun confirmAccessibleSos() {
        if (directSosManager != null) {
            val prefs = sosPreferencesRepository?.getPreferences() ?: SosPreferences()
            if (prefs.isDirectSmsConsentGiven && prefs.isDirectSmsEnabled) {
                _uiState.update { it.copy(activeDialog = ActiveDialog.DirectSos) }
                directSosManager.startCancellableSos()
            } else {
                openSosSettings()
            }
        } else {
            openManualSosWorkflow()
        }
    }

    fun cancelAccessibleSos() {
        _uiState.update { it.copy(activeDialog = ActiveDialog.None) }
    }

    fun openManualSmsFallback() {
        directSosManager?.reset()
        openManualSosWorkflow()
    }

    fun setConsentGiven(consent: Boolean) {
        sosPreferencesRepository?.setConsentGiven(consent)
    }

    fun setDirectSmsEnabled(enabled: Boolean) {
        sosPreferencesRepository?.setDirectSmsEnabled(enabled)
    }

    fun setCustomSosMessage(message: String) {
        sosPreferencesRepository?.setCustomSosMessage(message)
    }

    fun setLocationSharingEnabled(enabled: Boolean) {
        sosPreferencesRepository?.setLocationSharingEnabled(enabled)
    }

    fun setCountdownDurationSeconds(seconds: Int) {
        sosPreferencesRepository?.setCountdownDurationSeconds(seconds)
    }

    fun setBatteryAlertsConsent(consent: Boolean) {
        sosPreferencesRepository?.setBatteryAlertsConsent(consent)
    }

    fun setBatteryAlertsEnabled(enabled: Boolean) {
        sosPreferencesRepository?.setBatteryAlertsEnabled(enabled)
    }

    fun setBatteryAlertLocationEnabled(enabled: Boolean) {
        sosPreferencesRepository?.setBatteryAlertLocationEnabled(enabled)
    }

    fun toggleGuardianSos(guardianId: Long, isEnabled: Boolean) {
        scope.launch(dispatcher) {
            guardianRepository?.updateSosEnabled(guardianId, isEnabled)
        }
    }

    fun toggleGuardianBatteryAlert(guardianId: Long, isEnabled: Boolean) {
        scope.launch(dispatcher) {
            guardianRepository?.updateBatteryAlertEnabled(guardianId, isEnabled)
        }
    }

    /**
     * Activates the manual review-and-handoff Emergency SOS workflow.
     * Loads guardians, acquires an on-demand location fix, and prepares the editable message.
     */
    fun openManualSosWorkflow() {
        _uiState.update {
            it.copy(
                activeDialog = ActiveDialog.EmergencySos,
                sosWorkflow = it.sosWorkflow.copy(
                    isOpen = true,
                    isAcquiringLocation = true,
                    handoffNotice = null,
                    handoffError = null,
                    isCopiedToClipboard = false
                )
            )
        }

        scope.launch(dispatcher) {
            val guardians = guardianRepository?.getGuardians() ?: emptyList()
            val eligibleGuardians = EmergencyMessageBuilder.getEligibleGuardians(guardians)
            val initialSelectedIds = eligibleGuardians.map { it.id }.toSet()

            val remoteGuardians = remoteGuardianRepository?.refreshRemoteGuardians()?.getOrNull() ?: emptyList()
            val activeRemoteUids = remoteGuardians.filter { it.isActive }.map { it.guardianUid }.toSet()
            val defaultChannel = if (activeRemoteUids.isNotEmpty()) SosAlertChannel.REMOTE_FCM else SosAlertChannel.SYSTEM_SMS

            var locData: LocationData? = null
            var locError: String? = null

            if (locationRepository != null) {
                when (val result = locationRepository.getCurrentLocation(isPrecise = true)) {
                    is LocationResult.Success -> {
                        locData = result.location
                    }
                    is LocationResult.PermissionDenied -> {
                        locError = "Location permission not granted. Emergency SMS will proceed without GPS."
                    }
                    is LocationResult.LocationDisabled -> {
                        locError = "Device location (GPS) is turned off."
                    }
                    is LocationResult.Error -> {
                        locError = result.message
                    }
                }
            } else {
                locError = "Location provider not configured."
            }

            val initialMessage = EmergencyMessageBuilder.buildMessage(
                userName = _uiState.value.userName,
                customNote = null,
                location = locData,
                includeLocation = true
            )

            _uiState.update { current ->
                current.copy(
                    sosWorkflow = current.sosWorkflow.copy(
                        alertChannel = defaultChannel,
                        guardians = guardians,
                        selectedGuardianIds = initialSelectedIds,
                        remoteGuardians = remoteGuardians,
                        selectedRemoteGuardianUids = activeRemoteUids,
                        isAcquiringLocation = false,
                        locationData = locData,
                        locationError = locError,
                        includeLocation = true,
                        editableMessage = initialMessage
                    )
                )
            }
        }
    }

    fun setSosAlertChannel(channel: SosAlertChannel) {
        _uiState.update { it.copy(sosWorkflow = it.sosWorkflow.copy(alertChannel = channel)) }
    }

    fun toggleRemoteGuardianSelection(uid: String) {
        _uiState.update { current ->
            val existing = current.sosWorkflow.selectedRemoteGuardianUids
            val updated = if (existing.contains(uid)) existing - uid else existing + uid
            current.copy(sosWorkflow = current.sosWorkflow.copy(selectedRemoteGuardianUids = updated))
        }
    }

    fun selectAllRemoteGuardians() {
        _uiState.update { current ->
            val allUids = current.sosWorkflow.remoteGuardians.filter { it.isActive }.map { it.guardianUid }.toSet()
            current.copy(sosWorkflow = current.sosWorkflow.copy(selectedRemoteGuardianUids = allUids))
        }
    }

    fun deselectAllRemoteGuardians() {
        _uiState.update { current ->
            current.copy(sosWorkflow = current.sosWorkflow.copy(selectedRemoteGuardianUids = emptySet()))
        }
    }

    fun dispatchRemoteSosAlert() {
        val workflow = _uiState.value.sosWorkflow
        val selected = workflow.selectedRemoteGuardians
        if (selected.isEmpty()) {
            _uiState.update {
                it.copy(
                    sosWorkflow = it.sosWorkflow.copy(
                        remoteDispatchError = "Please select at least one active remote guardian."
                    )
                )
            }
            return
        }

        _uiState.update {
            it.copy(
                sosWorkflow = it.sosWorkflow.copy(
                    isDispatchingRemote = true,
                    remoteDispatchError = null
                )
            )
        }

        scope.launch(dispatcher) {
            val idempotencyKey = java.util.UUID.randomUUID().toString()
            val result = remoteSosRepository?.dispatchRemoteSos(
                recipients = selected,
                messageText = workflow.editableMessage,
                location = workflow.locationData,
                includeLocation = workflow.includeLocation,
                idempotencyKey = idempotencyKey
            )

            if (result != null && result.isSuccess) {
                val data = result.getOrThrow()
                _uiState.update { current ->
                    current.copy(
                        sosWorkflow = current.sosWorkflow.copy(
                            isDispatchingRemote = false,
                            activeRemoteAlertId = data.alertId,
                            isRemoteDispatchSuccessful = true
                        )
                    )
                }

                deliveryStatusJob?.cancel()
                deliveryStatusJob = remoteSosRepository.observeDeliveryStatuses(data.alertId)
                    .onEach { statuses ->
                        _uiState.update { current ->
                            current.copy(
                                sosWorkflow = current.sosWorkflow.copy(
                                    remoteDeliveryStatuses = statuses
                                )
                            )
                        }
                    }
                    .launchIn(scope)
            } else {
                val rawMsg = result?.exceptionOrNull()?.localizedMessage
                val errorMsg = if (rawMsg?.contains("NOT_FOUND", ignoreCase = true) == true || rawMsg?.contains("404") == true) {
                    "Remote SOS push alert backend is not configured yet. Please use SMS emergency messaging."
                } else {
                    rawMsg ?: "Failed to dispatch remote alert to Firebase backend. Please use SMS fallback."
                }
                _uiState.update { current ->
                    current.copy(
                        sosWorkflow = current.sosWorkflow.copy(
                            isDispatchingRemote = false,
                            remoteDispatchError = errorMsg
                        )
                    )
                }
            }
        }
    }

    fun toggleGuardianSelection(guardianId: Long) {
        _uiState.update { current ->
            val existing = current.sosWorkflow.selectedGuardianIds
            val updated = if (existing.contains(guardianId)) {
                existing - guardianId
            } else {
                existing + guardianId
            }
            current.copy(sosWorkflow = current.sosWorkflow.copy(selectedGuardianIds = updated))
        }
    }

    fun selectAllGuardians() {
        _uiState.update { current ->
            val allIds = EmergencyMessageBuilder.getEligibleGuardians(current.sosWorkflow.guardians).map { it.id }.toSet()
            current.copy(sosWorkflow = current.sosWorkflow.copy(selectedGuardianIds = allIds))
        }
    }

    fun deselectAllGuardians() {
        _uiState.update { current ->
            current.copy(sosWorkflow = current.sosWorkflow.copy(selectedGuardianIds = emptySet()))
        }
    }

    fun toggleIncludeLocation(include: Boolean) {
        _uiState.update { current ->
            val updatedWorkflow = current.sosWorkflow.copy(includeLocation = include)
            val updatedMessage = EmergencyMessageBuilder.buildMessage(
                userName = current.userName,
                customNote = if (updatedWorkflow.customNote.isNotBlank()) updatedWorkflow.customNote else null,
                location = updatedWorkflow.locationData,
                includeLocation = include
            )
            current.copy(
                sosWorkflow = updatedWorkflow.copy(editableMessage = updatedMessage)
            )
        }
    }

    fun updateEditableMessage(newMessage: String) {
        _uiState.update { current ->
            current.copy(
                sosWorkflow = current.sosWorkflow.copy(editableMessage = newMessage)
            )
        }
    }

    fun cancelSosWorkflow() {
        deliveryStatusJob?.cancel()
        _uiState.update { current ->
            current.copy(
                activeDialog = ActiveDialog.None,
                sosWorkflow = SosWorkflowState()
            )
        }
    }

    fun startComposerLaunch() {
        _uiState.update { current ->
            current.copy(
                sosWorkflow = current.sosWorkflow.copy(isLaunchingComposer = true, handoffError = null)
            )
        }
    }

    fun onComposerLaunched() {
        _uiState.update { current ->
            current.copy(
                sosWorkflow = current.sosWorkflow.copy(
                    isLaunchingComposer = false,
                    handoffNotice = "Messaging app opened. Please press Send in your SMS app to dispatch."
                )
            )
        }
    }

    fun onComposerFailed(error: String) {
        _uiState.update { current ->
            current.copy(
                sosWorkflow = current.sosWorkflow.copy(
                    isLaunchingComposer = false,
                    handoffError = error
                )
            )
        }
    }

    fun onMessageCopied() {
        _uiState.update { current ->
            current.copy(
                sosWorkflow = current.sosWorkflow.copy(isCopiedToClipboard = true)
            )
        }
    }

    fun openDialog(dialog: ActiveDialog) {
        if (dialog == ActiveDialog.EmergencySos) {
            triggerSosWorkflow()
        } else {
            _uiState.value = _uiState.value.copy(activeDialog = dialog)
        }
    }

    fun dismissDialog() {
        _uiState.value = _uiState.value.copy(
            activeDialog = ActiveDialog.None,
            sosHoldProgress = 0f,
            isHoldingSos = false,
            sosWorkflow = SosWorkflowState()
        )
    }

    fun simulateGuardianAcknowledgement(alertId: String) {
        val repo = remoteSosRepository
        if (repo is com.safeher.app.data.demo.DemoRemoteSosRepository) {
            repo.simulateGuardianAcknowledgement(alertId)
        }
    }

    override fun onCleared() {
        super.onCleared()
        clockJob?.cancel()
        holdJob?.cancel()
    }
}
