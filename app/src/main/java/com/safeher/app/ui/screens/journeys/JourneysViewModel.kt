package com.safeher.app.ui.screens.journeys

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.safeher.app.data.journey.Journey
import com.safeher.app.data.journey.JourneyOperationResult
import com.safeher.app.data.journey.JourneyRepository
import com.safeher.app.data.journey.JourneyStatus
import com.safeher.app.data.location.LocationRepository
import com.safeher.app.data.location.LocationResult
import com.safeher.app.data.places.AutocompleteResult
import com.safeher.app.data.places.PlaceDetailsResult
import com.safeher.app.data.places.PlaceSuggestion
import com.safeher.app.data.places.PlacesAutocompleteProvider
import com.safeher.app.data.places.ResolvedPlace
import com.safeher.app.data.journey.JourneyEvent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class CreateJourneyFormState(
    val isOpen: Boolean = false,
    val title: String = "",
    val startLocation: String = "Current location",
    val destination: String = "",
    val expectedArrivalMillis: Long = 0L,
    val startLatitude: Double? = null,
    val startLongitude: Double? = null,
    val destinationLatitude: Double? = null,
    val destinationLongitude: Double? = null,
    val selectedPlaceId: String? = null,
    val selectedPlace: ResolvedPlace? = null,
    val isSearchingPlaces: Boolean = false,
    val placeSuggestions: List<PlaceSuggestion> = emptyList(),
    val placesErrorMessage: String? = null,
    val isPlacesConfigured: Boolean = false,
    val isManualFallbackSelected: Boolean = false,
    val destinationError: String? = null,
    val timeError: String? = null,
    val isAcquiringLocation: Boolean = false,
    val locationNote: String? = null
)

data class ExtendJourneyFormState(
    val isOpen: Boolean = false,
    val journeyId: Long = 0L,
    val currentExpectedArrivalMillis: Long = 0L,
    val newExpectedArrivalMillis: Long = 0L,
    val timeError: String? = null
)

data class JourneysUiState(
    val journeys: List<Journey> = emptyList(),
    val activeJourney: Journey? = null,
    val activeJourneyEvents: List<JourneyEvent> = emptyList(),
    val historyJourneys: List<Journey> = emptyList(),
    val isLoading: Boolean = true,
    val isActionInProgress: Boolean = false,
    val currentTimeMillis: Long = System.currentTimeMillis(),
    val createForm: CreateJourneyFormState = CreateJourneyFormState(),
    val extendForm: ExtendJourneyFormState = ExtendJourneyFormState(),
    val finishConfirmJourney: Journey? = null,
    val cancelConfirmJourney: Journey? = null,
    val selectedJourneyForDetails: Journey? = null,
    val selectedJourneyEvents: List<JourneyEvent> = emptyList(),
    val showCheckInInfo: Boolean = false,
    val feedbackMessage: String? = null,
    val errorMessage: String? = null
)

class JourneysViewModel(
    private val journeyRepository: JourneyRepository,
    private val locationRepository: LocationRepository? = null,
    private val placesProvider: PlacesAutocompleteProvider? = null,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Main,
    coroutineScope: kotlinx.coroutines.CoroutineScope? = null,
    private val searchDebounceMs: Long = 350L
) : ViewModel() {

    private val scope: kotlinx.coroutines.CoroutineScope = coroutineScope ?: viewModelScope

    private val _uiState = MutableStateFlow(JourneysUiState())
    val uiState: StateFlow<JourneysUiState> = _uiState.asStateFlow()

    private var tickerJob: Job? = null
    private var searchDebounceJob: Job? = null

    init {
        observeJourneys()
        startClockTicker()
    }

    private fun observeJourneys() {
        journeyRepository.getJourneysFlow()
            .onEach { allJourneys ->
                val now = System.currentTimeMillis()
                val active = allJourneys.firstOrNull { it.isActiveOrOverdue }
                val history = allJourneys.filter { it.isTerminal }
                val events = if (active != null) {
                    journeyRepository.getEventsForJourney(active.id)
                } else {
                    emptyList()
                }

                _uiState.update {
                    it.copy(
                        journeys = allJourneys,
                        activeJourney = active,
                        activeJourneyEvents = events,
                        historyJourneys = history,
                        isLoading = false,
                        currentTimeMillis = now
                    )
                }
            }
            .launchIn(scope)
    }

    private fun startClockTicker() {
        tickerJob?.cancel()
        tickerJob = scope.launch(dispatcher) {
            while (isActive) {
                delay(1000L)
                val now = System.currentTimeMillis()
                val currentActive = _uiState.value.activeJourney

                if (currentActive != null &&
                    currentActive.status == JourneyStatus.ACTIVE &&
                    now >= currentActive.expectedArrivalTimestamp
                ) {
                    journeyRepository.refreshJourneyStatus()
                }

                _uiState.update { current ->
                    val active = current.activeJourney
                    val updatedActive = if (active != null &&
                        active.status == JourneyStatus.ACTIVE &&
                        now >= active.expectedArrivalTimestamp
                    ) {
                        active.copy(status = JourneyStatus.OVERDUE)
                    } else {
                        active
                    }

                    current.copy(
                        currentTimeMillis = now,
                        activeJourney = updatedActive
                    )
                }
            }
        }
    }

    // --- Create Journey Flow ---

    fun openCreateDialog() {
        placesProvider?.resetSession()
        val now = System.currentTimeMillis()
        val defaultEta = now + 30 * 60 * 1000L // Default +30 minutes
        _uiState.update {
            it.copy(
                createForm = CreateJourneyFormState(
                    isOpen = true,
                    title = "",
                    startLocation = "Current location",
                    destination = "",
                    expectedArrivalMillis = defaultEta,
                    startLatitude = null,
                    startLongitude = null,
                    destinationLatitude = null,
                    destinationLongitude = null,
                    selectedPlaceId = null,
                    selectedPlace = null,
                    isSearchingPlaces = false,
                    placeSuggestions = emptyList(),
                    placesErrorMessage = null,
                    isPlacesConfigured = placesProvider?.isConfigured ?: false,
                    isManualFallbackSelected = false,
                    destinationError = null,
                    timeError = null,
                    isAcquiringLocation = false,
                    locationNote = null
                )
            )
        }
    }

    fun dismissCreateDialog() {
        searchDebounceJob?.cancel()
        placesProvider?.resetSession()
        _uiState.update { it.copy(createForm = CreateJourneyFormState(isOpen = false)) }
    }

    fun updateCreateTitle(title: String) {
        _uiState.update {
            it.copy(createForm = it.createForm.copy(title = title))
        }
    }

    fun updateCreateStartLocation(startLocation: String) {
        _uiState.update {
            it.copy(createForm = it.createForm.copy(startLocation = startLocation, locationNote = null))
        }
    }

    fun updateCreateDestination(query: String) {
        val trimmed = query.trim()
        val currentForm = _uiState.value.createForm

        // If user alters destination text after picking a place, invalidate previous place coordinates
        val invalidatedPlace = if (currentForm.selectedPlace != null && currentForm.destination != query) null else currentForm.selectedPlace
        val invalidatedLat = if (invalidatedPlace == null) null else currentForm.destinationLatitude
        val invalidatedLng = if (invalidatedPlace == null) null else currentForm.destinationLongitude
        val invalidatedPlaceId = if (invalidatedPlace == null) null else currentForm.selectedPlaceId

        _uiState.update {
            it.copy(
                createForm = it.createForm.copy(
                    destination = query,
                    selectedPlace = invalidatedPlace,
                    destinationLatitude = invalidatedLat,
                    destinationLongitude = invalidatedLng,
                    selectedPlaceId = invalidatedPlaceId,
                    isManualFallbackSelected = false,
                    destinationError = if (query.isBlank()) "Destination is required" else null
                )
            )
        }

        searchDebounceJob?.cancel()

        if (placesProvider == null || !placesProvider.isConfigured) {
            _uiState.update {
                it.copy(
                    createForm = it.createForm.copy(
                        isSearchingPlaces = false,
                        placeSuggestions = emptyList(),
                        placesErrorMessage = if (trimmed.length >= 3) {
                            "Google Places API key is not configured. Manual entry will be used."
                        } else null
                    )
                )
            }
            return
        }

        if (trimmed.length < 3) {
            _uiState.update {
                it.copy(
                    createForm = it.createForm.copy(
                        isSearchingPlaces = false,
                        placeSuggestions = emptyList(),
                        placesErrorMessage = null
                    )
                )
            }
            return
        }

        searchDebounceJob = scope.launch(dispatcher) {
            if (searchDebounceMs > 0) {
                delay(searchDebounceMs)
            }
            _uiState.update {
                it.copy(createForm = it.createForm.copy(isSearchingPlaces = true, placesErrorMessage = null))
            }

            val form = _uiState.value.createForm
            when (val res = placesProvider.getAutocompleteSuggestions(
                query = trimmed,
                biasLatitude = form.startLatitude,
                biasLongitude = form.startLongitude
            )) {
                is AutocompleteResult.Success -> {
                    _uiState.update {
                        it.copy(
                            createForm = it.createForm.copy(
                                isSearchingPlaces = false,
                                placeSuggestions = res.suggestions,
                                placesErrorMessage = null
                            )
                        )
                    }
                }
                is AutocompleteResult.Empty -> {
                    _uiState.update {
                        it.copy(
                            createForm = it.createForm.copy(
                                isSearchingPlaces = false,
                                placeSuggestions = emptyList(),
                                placesErrorMessage = "No matching places found."
                            )
                        )
                    }
                }
                is AutocompleteResult.NotConfigured -> {
                    _uiState.update {
                        it.copy(
                            createForm = it.createForm.copy(
                                isSearchingPlaces = false,
                                placeSuggestions = emptyList(),
                                placesErrorMessage = res.message,
                                isPlacesConfigured = false
                            )
                        )
                    }
                }
                is AutocompleteResult.Error -> {
                    _uiState.update {
                        it.copy(
                            createForm = it.createForm.copy(
                                isSearchingPlaces = false,
                                placeSuggestions = emptyList(),
                                placesErrorMessage = res.message
                            )
                        )
                    }
                }
            }
        }
    }

    fun selectPlaceSuggestion(suggestion: PlaceSuggestion) {
        searchDebounceJob?.cancel()
        if (placesProvider == null) return

        scope.launch(dispatcher) {
            _uiState.update {
                it.copy(createForm = it.createForm.copy(isSearchingPlaces = true, placesErrorMessage = null))
            }

            when (val res = placesProvider.fetchPlaceDetails(suggestion.placeId)) {
                is PlaceDetailsResult.Success -> {
                    val place = res.place
                    val canonicalText = if (place.address.isNotBlank() && !place.name.equals(place.address, ignoreCase = true)) {
                        "${place.name}, ${place.address}"
                    } else {
                        place.name
                    }

                    _uiState.update {
                        it.copy(
                            createForm = it.createForm.copy(
                                destination = canonicalText,
                                selectedPlace = place,
                                destinationLatitude = place.latitude,
                                destinationLongitude = place.longitude,
                                selectedPlaceId = place.placeId,
                                isSearchingPlaces = false,
                                placeSuggestions = emptyList(),
                                placesErrorMessage = null,
                                destinationError = null,
                                isManualFallbackSelected = false
                            )
                        )
                    }
                }
                is PlaceDetailsResult.Error -> {
                    _uiState.update {
                        it.copy(
                            createForm = it.createForm.copy(
                                isSearchingPlaces = false,
                                placesErrorMessage = res.message
                            )
                        )
                    }
                }
            }
        }
    }

    fun selectManualDestinationFallback() {
        searchDebounceJob?.cancel()
        placesProvider?.resetSession()
        _uiState.update {
            it.copy(
                createForm = it.createForm.copy(
                    selectedPlace = null,
                    destinationLatitude = null,
                    destinationLongitude = null,
                    selectedPlaceId = null,
                    placeSuggestions = emptyList(),
                    isSearchingPlaces = false,
                    isManualFallbackSelected = true,
                    placesErrorMessage = null,
                    destinationError = if (it.createForm.destination.isBlank()) "Destination is required" else null
                )
            )
        }
    }

    fun clearDestinationQuery() {
        searchDebounceJob?.cancel()
        placesProvider?.resetSession()
        _uiState.update {
            it.copy(
                createForm = it.createForm.copy(
                    destination = "",
                    selectedPlace = null,
                    destinationLatitude = null,
                    destinationLongitude = null,
                    selectedPlaceId = null,
                    placeSuggestions = emptyList(),
                    isSearchingPlaces = false,
                    placesErrorMessage = null,
                    isManualFallbackSelected = false,
                    destinationError = null
                )
            )
        }
    }

    fun updateCreateExpectedArrival(arrivalMillis: Long) {
        val now = System.currentTimeMillis()
        val error = if (arrivalMillis <= now) "Arrival time must be in the future" else null
        _uiState.update {
            it.copy(
                createForm = it.createForm.copy(
                    expectedArrivalMillis = arrivalMillis,
                    timeError = error
                )
            )
        }
    }

    fun addMinutesToCreateEta(minutes: Int) {
        val now = System.currentTimeMillis()
        val base = if (_uiState.value.createForm.expectedArrivalMillis > now) {
            _uiState.value.createForm.expectedArrivalMillis
        } else {
            now
        }
        val newTime = base + (minutes * 60 * 1000L)
        updateCreateExpectedArrival(newTime)
    }

    fun setQuickEtaMinutes(minutes: Int) {
        val now = System.currentTimeMillis()
        val newTime = now + (minutes * 60 * 1000L)
        updateCreateExpectedArrival(newTime)
    }

    fun acquireCurrentLocationForStart() {
        if (locationRepository == null) {
            _uiState.update {
                it.copy(createForm = it.createForm.copy(locationNote = "Location service is not available."))
            }
            return
        }

        scope.launch(dispatcher) {
            _uiState.update {
                it.copy(createForm = it.createForm.copy(isAcquiringLocation = true, locationNote = null))
            }

            when (val result = locationRepository.getCurrentLocation(isPrecise = false)) {
                is LocationResult.Success -> {
                    val lat = result.location.latitude
                    val lng = result.location.longitude
                    val desc = String.format(Locale.US, "GPS Location (%.4f, %.4f)", lat, lng)
                    _uiState.update {
                        it.copy(
                            createForm = it.createForm.copy(
                                isAcquiringLocation = false,
                                startLocation = desc,
                                startLatitude = lat,
                                startLongitude = lng,
                                locationNote = "Acquired current location fix."
                            )
                        )
                    }
                }
                is LocationResult.PermissionDenied -> {
                    _uiState.update {
                        it.copy(
                            createForm = it.createForm.copy(
                                isAcquiringLocation = false,
                                locationNote = "Location permission not granted. You can enter starting location manually."
                            )
                        )
                    }
                }
                is LocationResult.LocationDisabled -> {
                    _uiState.update {
                        it.copy(
                            createForm = it.createForm.copy(
                                isAcquiringLocation = false,
                                locationNote = "Device location is disabled. You can enter starting location manually."
                            )
                        )
                    }
                }
                is LocationResult.Error -> {
                    _uiState.update {
                        it.copy(
                            createForm = it.createForm.copy(
                                isAcquiringLocation = false,
                                locationNote = "Could not retrieve GPS fix. Enter location manually."
                            )
                        )
                    }
                }
            }
        }
    }

    fun submitCreateJourney() {
        val form = _uiState.value.createForm
        val trimmedDest = form.destination.trim()
        val now = System.currentTimeMillis()

        if (trimmedDest.isEmpty()) {
            _uiState.update {
                it.copy(createForm = it.createForm.copy(destinationError = "Destination is required to start a safety journey."))
            }
            return
        }

        // Prevent accidental submission of unselected autocomplete query when places provider is configured
        if (placesProvider != null && placesProvider.isConfigured && form.selectedPlace == null && !form.isManualFallbackSelected) {
            _uiState.update {
                it.copy(
                    createForm = it.createForm.copy(
                        destinationError = "Please select a suggestion from the list or tap 'Use as manual destination'."
                    )
                )
            }
            return
        }

        if (form.expectedArrivalMillis <= now) {
            _uiState.update {
                it.copy(createForm = it.createForm.copy(timeError = "Expected arrival time must be set in the future."))
            }
            return
        }

        if (_uiState.value.isActionInProgress) return
        _uiState.update { it.copy(isActionInProgress = true) }

        scope.launch(dispatcher) {
            try {
                when (val res = journeyRepository.startJourney(
                    title = form.title.ifBlank { null },
                    startLocation = form.startLocation.ifBlank { "Current location" },
                    destination = trimmedDest,
                    expectedArrivalTimestamp = form.expectedArrivalMillis,
                    startLatitude = form.startLatitude,
                    startLongitude = form.startLongitude,
                    destinationLatitude = form.destinationLatitude,
                    destinationLongitude = form.destinationLongitude
                )) {
                    is JourneyOperationResult.Success -> {
                        _uiState.update {
                            it.copy(
                                createForm = CreateJourneyFormState(isOpen = false),
                                feedbackMessage = "Safety journey started. Keep your phone accessible.",
                                errorMessage = null
                            )
                        }
                    }
                    is JourneyOperationResult.ActiveJourneyExists -> {
                        _uiState.update {
                            it.copy(
                                createForm = it.createForm.copy(destinationError = res.message),
                                errorMessage = res.message
                            )
                        }
                    }
                    is JourneyOperationResult.ValidationError -> {
                        if (res.field == "destination") {
                            _uiState.update { it.copy(createForm = it.createForm.copy(destinationError = res.message)) }
                        } else {
                            _uiState.update { it.copy(createForm = it.createForm.copy(timeError = res.message)) }
                        }
                    }
                    is JourneyOperationResult.TerminalStateError,
                    is JourneyOperationResult.Error -> {
                        _uiState.update { it.copy(errorMessage = res.message) }
                    }
                }
            } finally {
                _uiState.update { it.copy(isActionInProgress = false) }
            }
        }
    }

    // --- Active Journey Actions ---

    fun performCheckIn(journeyId: Long) {
        if (_uiState.value.isActionInProgress) return
        _uiState.update { it.copy(isActionInProgress = true) }

        scope.launch(dispatcher) {
            try {
                when (val res = journeyRepository.checkIn(journeyId)) {
                    is JourneyOperationResult.Success -> {
                        val timeStr = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date())
                        val events = journeyRepository.getEventsForJourney(journeyId)
                        _uiState.update {
                            it.copy(
                                activeJourneyEvents = events,
                                feedbackMessage = "Local check-in recorded at $timeStr. (Local only; no guardian alerts sent)",
                                errorMessage = null
                            )
                        }
                    }
                    is JourneyOperationResult.TerminalStateError,
                    is JourneyOperationResult.ValidationError,
                    is JourneyOperationResult.ActiveJourneyExists,
                    is JourneyOperationResult.Error -> {
                        _uiState.update { it.copy(errorMessage = res.message) }
                    }
                }
            } finally {
                _uiState.update { it.copy(isActionInProgress = false) }
            }
        }
    }

    fun openExtendDialog(journey: Journey) {
        val now = System.currentTimeMillis()
        val baseTime = maxOf(journey.expectedArrivalTimestamp, now)
        val defaultNewEta = baseTime + 15 * 60 * 1000L // +15 mins
        _uiState.update {
            it.copy(
                extendForm = ExtendJourneyFormState(
                    isOpen = true,
                    journeyId = journey.id,
                    currentExpectedArrivalMillis = journey.expectedArrivalTimestamp,
                    newExpectedArrivalMillis = defaultNewEta,
                    timeError = null
                )
            )
        }
    }

    fun dismissExtendDialog() {
        _uiState.update { it.copy(extendForm = ExtendJourneyFormState(isOpen = false)) }
    }

    fun updateExtendArrival(newMillis: Long) {
        val now = System.currentTimeMillis()
        val error = if (newMillis <= now) "New arrival time must be in the future" else null
        _uiState.update {
            it.copy(
                extendForm = it.extendForm.copy(
                    newExpectedArrivalMillis = newMillis,
                    timeError = error
                )
            )
        }
    }

    fun addMinutesToExtendEta(minutes: Int) {
        val current = _uiState.value.extendForm.newExpectedArrivalMillis
        val now = System.currentTimeMillis()
        val base = if (current > now) current else now
        updateExtendArrival(base + (minutes * 60 * 1000L))
    }

    fun submitExtendJourney() {
        if (_uiState.value.isActionInProgress) return
        val form = _uiState.value.extendForm
        val now = System.currentTimeMillis()

        if (form.newExpectedArrivalMillis <= now) {
            _uiState.update {
                it.copy(extendForm = it.extendForm.copy(timeError = "New arrival time must be in the future"))
            }
            return
        }

        _uiState.update { it.copy(isActionInProgress = true) }

        scope.launch(dispatcher) {
            try {
                when (val res = journeyRepository.extendJourney(form.journeyId, form.newExpectedArrivalMillis)) {
                    is JourneyOperationResult.Success -> {
                        val events = journeyRepository.getEventsForJourney(form.journeyId)
                        _uiState.update {
                            it.copy(
                                extendForm = ExtendJourneyFormState(isOpen = false),
                                activeJourneyEvents = events,
                                feedbackMessage = "Journey extended. Expected arrival updated.",
                                errorMessage = null
                            )
                        }
                    }
                    is JourneyOperationResult.ValidationError -> {
                        _uiState.update {
                            it.copy(extendForm = it.extendForm.copy(timeError = res.message))
                        }
                    }
                    is JourneyOperationResult.TerminalStateError,
                    is JourneyOperationResult.ActiveJourneyExists,
                    is JourneyOperationResult.Error -> {
                        _uiState.update { it.copy(errorMessage = res.message) }
                    }
                }
            } finally {
                _uiState.update { it.copy(isActionInProgress = false) }
            }
        }
    }

    fun promptFinishJourney(journey: Journey) {
        _uiState.update { it.copy(finishConfirmJourney = journey) }
    }

    fun dismissFinishConfirm() {
        _uiState.update { it.copy(finishConfirmJourney = null) }
    }

    fun confirmFinishJourney() {
        if (_uiState.value.isActionInProgress) return
        val journey = _uiState.value.finishConfirmJourney ?: return
        _uiState.update { it.copy(isActionInProgress = true) }

        scope.launch(dispatcher) {
            try {
                when (val res = journeyRepository.completeJourney(journey.id)) {
                    is JourneyOperationResult.Success -> {
                        _uiState.update {
                            it.copy(
                                finishConfirmJourney = null,
                                activeJourneyEvents = emptyList(),
                                feedbackMessage = "Arrived safely! Journey completed.",
                                errorMessage = null
                            )
                        }
                    }
                    is JourneyOperationResult.TerminalStateError,
                    is JourneyOperationResult.ValidationError,
                    is JourneyOperationResult.ActiveJourneyExists,
                    is JourneyOperationResult.Error -> {
                        _uiState.update {
                            it.copy(
                                finishConfirmJourney = null,
                                errorMessage = res.message
                            )
                        }
                    }
                }
            } finally {
                _uiState.update { it.copy(isActionInProgress = false) }
            }
        }
    }

    fun promptCancelJourney(journey: Journey) {
        _uiState.update { it.copy(cancelConfirmJourney = journey) }
    }

    fun dismissCancelConfirm() {
        _uiState.update { it.copy(cancelConfirmJourney = null) }
    }

    fun confirmCancelJourney() {
        if (_uiState.value.isActionInProgress) return
        val journey = _uiState.value.cancelConfirmJourney ?: return
        _uiState.update { it.copy(isActionInProgress = true) }

        scope.launch(dispatcher) {
            try {
                when (val res = journeyRepository.cancelJourney(journey.id)) {
                    is JourneyOperationResult.Success -> {
                        _uiState.update {
                            it.copy(
                                cancelConfirmJourney = null,
                                activeJourneyEvents = emptyList(),
                                feedbackMessage = "Journey cancelled and moved to history.",
                                errorMessage = null
                            )
                        }
                    }
                    is JourneyOperationResult.TerminalStateError,
                    is JourneyOperationResult.ValidationError,
                    is JourneyOperationResult.ActiveJourneyExists,
                    is JourneyOperationResult.Error -> {
                        _uiState.update {
                            it.copy(
                                cancelConfirmJourney = null,
                                errorMessage = res.message
                            )
                        }
                    }
                }
            } finally {
                _uiState.update { it.copy(isActionInProgress = false) }
            }
        }
    }

    fun openJourneyDetails(journey: Journey) {
        scope.launch(dispatcher) {
            val events = journeyRepository.getEventsForJourney(journey.id)
            _uiState.update {
                it.copy(
                    selectedJourneyForDetails = journey,
                    selectedJourneyEvents = events
                )
            }
        }
    }

    fun dismissJourneyDetails() {
        _uiState.update {
            it.copy(
                selectedJourneyForDetails = null,
                selectedJourneyEvents = emptyList()
            )
        }
    }

    fun refreshStatus() {
        scope.launch(dispatcher) {
            journeyRepository.refreshJourneyStatus()
        }
    }

    fun toggleCheckInInfo(show: Boolean) {
        _uiState.update { it.copy(showCheckInInfo = show) }
    }

    fun clearFeedback() {
        _uiState.update { it.copy(feedbackMessage = null, errorMessage = null) }
    }

    override fun onCleared() {
        super.onCleared()
        searchDebounceJob?.cancel()
        tickerJob?.cancel()
    }
}
