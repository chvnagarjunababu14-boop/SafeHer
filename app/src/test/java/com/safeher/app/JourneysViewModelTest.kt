package com.safeher.app

import com.safeher.app.data.journey.Journey
import com.safeher.app.data.journey.JourneyOperationResult
import com.safeher.app.data.journey.JourneyRepository
import com.safeher.app.data.journey.JourneyStatus
import com.safeher.app.data.location.LocationData
import com.safeher.app.data.location.LocationFreshness
import com.safeher.app.data.location.LocationRepository
import com.safeher.app.data.location.LocationResult
import com.safeher.app.data.places.AutocompleteResult
import com.safeher.app.data.places.PlaceDetailsResult
import com.safeher.app.data.places.PlaceSuggestion
import com.safeher.app.data.places.PlacesAutocompleteProvider
import com.safeher.app.data.places.ResolvedPlace
import com.safeher.app.ui.screens.journeys.JourneysViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import com.safeher.app.data.journey.JourneyEvent
import com.safeher.app.data.journey.JourneyEventType
import org.junit.Test

class FakeJourneyRepository : JourneyRepository {
    private val journeysList = mutableListOf<Journey>()
    private val trigger = MutableStateFlow<List<Journey>>(emptyList())
    private var nextId = 1L

    private val eventsList = mutableListOf<JourneyEvent>()
    private val eventsTrigger = MutableStateFlow<List<JourneyEvent>>(emptyList())
    private var nextEventId = 1L

    private fun emit() {
        trigger.value = journeysList.toList()
    }

    private fun emitEvents() {
        eventsTrigger.value = eventsList.toList()
    }

    override fun getJourneysFlow(): Flow<List<Journey>> = trigger.asStateFlow()

    override fun getActiveJourneyFlow(): Flow<Journey?> {
        return trigger.map { list ->
            list.firstOrNull { it.isActiveOrOverdue }
        }
    }

    override suspend fun getJourneys(): List<Journey> = journeysList.toList()

    override suspend fun getActiveJourney(): Journey? {
        return journeysList.firstOrNull { it.isActiveOrOverdue }
    }

    override suspend fun getJourneyById(id: Long): Journey? {
        return journeysList.firstOrNull { it.id == id }
    }

    override suspend fun startJourney(
        title: String?,
        startLocation: String,
        destination: String,
        expectedArrivalTimestamp: Long,
        startLatitude: Double?,
        startLongitude: Double?,
        destinationLatitude: Double?,
        destinationLongitude: Double?,
        enableMonitoring: Boolean
    ): JourneyOperationResult {
        if (destination.isBlank()) {
            return JourneyOperationResult.ValidationError("destination", "Destination is required")
        }
        val now = System.currentTimeMillis()
        if (expectedArrivalTimestamp <= now) {
            return JourneyOperationResult.ValidationError("expectedArrivalTimestamp", "Arrival must be in future")
        }
        val active = getActiveJourney()
        if (active != null) {
            return JourneyOperationResult.ActiveJourneyExists(active.id, "Active journey exists")
        }

        val id = nextId++
        val journey = Journey(
            id = id,
            title = title,
            startLocation = startLocation,
            destination = destination,
            startLatitude = startLatitude,
            startLongitude = startLongitude,
            destinationLatitude = destinationLatitude,
            destinationLongitude = destinationLongitude,
            startTimestamp = now,
            expectedArrivalTimestamp = expectedArrivalTimestamp,
            status = JourneyStatus.ACTIVE,
            createdAt = now,
            updatedAt = now,
            isMonitoringActive = enableMonitoring
        )
        journeysList.add(journey)
        eventsList.add(
            JourneyEvent(
                id = nextEventId++,
                journeyId = id,
                eventType = JourneyEventType.STARTED,
                timestamp = now,
                metadata = "Destination: $destination"
            )
        )
        emit()
        emitEvents()
        return JourneyOperationResult.Success(id)
    }

    override suspend fun checkIn(id: Long): JourneyOperationResult {
        val idx = journeysList.indexOfFirst { it.id == id }
        if (idx == -1) return JourneyOperationResult.Error("Not found")
        val item = journeysList[idx]
        if (item.isTerminal) return JourneyOperationResult.TerminalStateError("Already terminal")

        val now = System.currentTimeMillis()
        journeysList[idx] = item.copy(
            lastCheckInTimestamp = now,
            checkInCount = item.checkInCount + 1,
            checkInHistory = item.checkInHistory + now,
            updatedAt = now
        )
        eventsList.add(
            JourneyEvent(
                id = nextEventId++,
                journeyId = id,
                eventType = JourneyEventType.CHECK_IN,
                timestamp = now,
                metadata = "Check-in #${item.checkInCount + 1}"
            )
        )
        emit()
        emitEvents()
        return JourneyOperationResult.Success(id)
    }

    override suspend fun extendJourney(id: Long, newExpectedArrivalTimestamp: Long): JourneyOperationResult {
        val idx = journeysList.indexOfFirst { it.id == id }
        if (idx == -1) return JourneyOperationResult.Error("Not found")
        val item = journeysList[idx]
        if (item.isTerminal) return JourneyOperationResult.TerminalStateError("Already terminal")

        val now = System.currentTimeMillis()
        if (newExpectedArrivalTimestamp <= now) {
            return JourneyOperationResult.ValidationError("expectedArrivalTimestamp", "Must be in future")
        }

        journeysList[idx] = item.copy(
            expectedArrivalTimestamp = newExpectedArrivalTimestamp,
            status = JourneyStatus.ACTIVE,
            updatedAt = now
        )
        eventsList.add(
            JourneyEvent(
                id = nextEventId++,
                journeyId = id,
                eventType = JourneyEventType.ETA_EXTENDED,
                timestamp = now,
                metadata = "Extended ETA"
            )
        )
        emit()
        emitEvents()
        return JourneyOperationResult.Success(id)
    }

    override suspend fun completeJourney(id: Long): JourneyOperationResult {
        val idx = journeysList.indexOfFirst { it.id == id }
        if (idx == -1) return JourneyOperationResult.Error("Not found")
        val item = journeysList[idx]
        if (item.isTerminal) return JourneyOperationResult.TerminalStateError("Already terminal")

        val now = System.currentTimeMillis()
        journeysList[idx] = item.copy(
            status = JourneyStatus.COMPLETED,
            completedTimestamp = now,
            updatedAt = now
        )
        eventsList.add(
            JourneyEvent(
                id = nextEventId++,
                journeyId = id,
                eventType = JourneyEventType.COMPLETED,
                timestamp = now,
                metadata = "Completed safely"
            )
        )
        emit()
        emitEvents()
        return JourneyOperationResult.Success(id)
    }

    override suspend fun cancelJourney(id: Long): JourneyOperationResult {
        val idx = journeysList.indexOfFirst { it.id == id }
        if (idx == -1) return JourneyOperationResult.Error("Not found")
        val item = journeysList[idx]
        if (item.isTerminal) return JourneyOperationResult.TerminalStateError("Already terminal")

        val now = System.currentTimeMillis()
        journeysList[idx] = item.copy(
            status = JourneyStatus.CANCELLED,
            updatedAt = now
        )
        eventsList.add(
            JourneyEvent(
                id = nextEventId++,
                journeyId = id,
                eventType = JourneyEventType.CANCELLED,
                timestamp = now,
                metadata = "User cancelled"
            )
        )
        emit()
        emitEvents()
        return JourneyOperationResult.Success(id)
    }

    override suspend fun deleteJourney(id: Long): Boolean {
        val removed = journeysList.removeIf { it.id == id }
        eventsList.removeIf { it.journeyId == id }
        if (removed) {
            emit()
            emitEvents()
        }
        return removed
    }

    override suspend fun getEventsForJourney(journeyId: Long): List<JourneyEvent> {
        return eventsList.filter { it.journeyId == journeyId }
            .sortedWith(compareByDescending<JourneyEvent> { it.timestamp }.thenByDescending { it.id })
    }

    override fun getEventsFlowForJourney(journeyId: Long): Flow<List<JourneyEvent>> {
        return eventsTrigger.map { list ->
            list.filter { it.journeyId == journeyId }
                .sortedWith(compareByDescending<JourneyEvent> { it.timestamp }.thenByDescending { it.id })
        }
    }

    override suspend fun refreshJourneyStatus(): Journey? {
        val now = System.currentTimeMillis()
        var updatedJourney: Journey? = null
        for (i in journeysList.indices) {
            val j = journeysList[i]
            if (j.isTerminal) continue
            if (j.status == JourneyStatus.ACTIVE && now >= j.expectedArrivalTimestamp) {
                val newJ = j.copy(status = JourneyStatus.OVERDUE, updatedAt = now)
                journeysList[i] = newJ
                eventsList.add(
                    JourneyEvent(
                        id = nextEventId++,
                        journeyId = j.id,
                        eventType = JourneyEventType.OVERDUE_OBSERVED,
                        timestamp = now,
                        metadata = "App observed arrival time elapsed"
                    )
                )
                updatedJourney = newJ
            } else if (j.status == JourneyStatus.OVERDUE && now < j.expectedArrivalTimestamp) {
                val newJ = j.copy(status = JourneyStatus.ACTIVE, updatedAt = now)
                journeysList[i] = newJ
                updatedJourney = newJ
            }
        }
        if (updatedJourney != null) {
            emit()
            emitEvents()
        }
        return updatedJourney ?: getActiveJourney()
    }

    override suspend fun acknowledgeSafetyCheck(id: Long): JourneyOperationResult {
        val idx = journeysList.indexOfFirst { it.id == id }
        if (idx == -1) return JourneyOperationResult.Error("Not found")
        val item = journeysList[idx]
        val now = System.currentTimeMillis()
        journeysList[idx] = item.copy(safetyCheckAcknowledged = true, updatedAt = now)
        eventsList.add(
            JourneyEvent(
                id = nextEventId++,
                journeyId = id,
                eventType = JourneyEventType.SAFETY_ACKNOWLEDGED,
                timestamp = now,
                metadata = "Safety confirmed by user"
            )
        )
        emit()
        emitEvents()
        return JourneyOperationResult.Success(id)
    }

    override suspend fun updateJourneyLocation(journeyId: Long, latitude: Double, longitude: Double, timestamp: Long): Boolean {
        val idx = journeysList.indexOfFirst { it.id == journeyId }
        if (idx == -1) return false
        val item = journeysList[idx]
        journeysList[idx] = item.copy(lastLatitude = latitude, lastLongitude = longitude, lastLocationTimestamp = timestamp, updatedAt = timestamp)
        emit()
        return true
    }

    override suspend fun recordSafetyCheckSent(journeyId: Long, checkTime: Long, escalationDeadline: Long): Boolean {
        val idx = journeysList.indexOfFirst { it.id == journeyId }
        if (idx == -1) return false
        val item = journeysList[idx]
        journeysList[idx] = item.copy(overdueSafetyCheckTimestamp = checkTime, overdueEscalationDeadline = escalationDeadline, updatedAt = checkTime)
        eventsList.add(
            JourneyEvent(
                id = nextEventId++,
                journeyId = journeyId,
                eventType = JourneyEventType.OVERDUE_SAFETY_CHECK_SENT,
                timestamp = checkTime,
                metadata = "Safety check notification sent"
            )
        )
        emit()
        emitEvents()
        return true
    }

    override suspend fun markOverdueEscalationAttempted(journeyId: Long): Boolean {
        val idx = journeysList.indexOfFirst { it.id == journeyId }
        if (idx == -1) return false
        val item = journeysList[idx]
        val now = System.currentTimeMillis()
        journeysList[idx] = item.copy(overdueEscalationAttempted = true, updatedAt = now)
        eventsList.add(
            JourneyEvent(
                id = nextEventId++,
                journeyId = journeyId,
                eventType = JourneyEventType.OVERDUE_ESCALATION_SOS,
                timestamp = now,
                metadata = "Overdue SOS dispatched to guardians"
            )
        )
        emit()
        emitEvents()
        return true
    }

    override suspend fun setMonitoringActive(journeyId: Long, isActive: Boolean): Boolean {
        val idx = journeysList.indexOfFirst { it.id == journeyId }
        if (idx == -1) return false
        val item = journeysList[idx]
        journeysList[idx] = item.copy(isMonitoringActive = isActive, updatedAt = System.currentTimeMillis())
        emit()
        return true
    }

    override suspend fun getActiveMonitoredJourneys(): List<Journey> {
        return journeysList.filter { it.isMonitoringActive && it.isActiveOrOverdue }
    }
}

class FakePlacesAutocompleteProvider(
    override var isConfigured: Boolean = true
) : PlacesAutocompleteProvider {

    var lastQuery: String? = null
    var queryCallCount = 0
    var sessionResetCallCount = 0

    var autocompleteResultToReturn: AutocompleteResult = AutocompleteResult.Success(
        listOf(
            PlaceSuggestion("p1", "Central Station", "Station Road, Bengaluru", "Central Station, Station Road, Bengaluru"),
            PlaceSuggestion("p2", "Central Mall", "MG Road, Bengaluru", "Central Mall, MG Road, Bengaluru")
        )
    )

    var detailsResultToReturn: PlaceDetailsResult = PlaceDetailsResult.Success(
        ResolvedPlace(
            placeId = "p1",
            name = "Central Station",
            address = "Station Road, Bengaluru",
            latitude = 12.9783,
            longitude = 77.5694
        )
    )

    override suspend fun getAutocompleteSuggestions(
        query: String,
        biasLatitude: Double?,
        biasLongitude: Double?
    ): AutocompleteResult {
        queryCallCount++
        lastQuery = query
        return if (!isConfigured) {
            AutocompleteResult.NotConfigured("Google Places API key is not configured.")
        } else {
            autocompleteResultToReturn
        }
    }

    override suspend fun fetchPlaceDetails(placeId: String): PlaceDetailsResult {
        return detailsResultToReturn
    }

    override fun resetSession() {
        sessionResetCallCount++
    }
}

class JourneysViewModelTest {

    private lateinit var journeyRepository: FakeJourneyRepository
    private lateinit var locationRepository: FakeLocationRepository
    private lateinit var placesProvider: FakePlacesAutocompleteProvider
    private lateinit var viewModel: JourneysViewModel

    @Before
    fun setUp() {
        journeyRepository = FakeJourneyRepository()
        locationRepository = FakeLocationRepository()
        placesProvider = FakePlacesAutocompleteProvider()
        viewModel = JourneysViewModel(
            journeyRepository = journeyRepository,
            locationRepository = locationRepository,
            placesProvider = placesProvider,
            dispatcher = Dispatchers.Unconfined,
            coroutineScope = kotlinx.coroutines.CoroutineScope(Dispatchers.Unconfined),
            searchDebounceMs = 0L
        )
    }

    @Test
    fun destinationSearch_debouncingAndCancellation_onlySearchesLatestQuery() = runBlocking {
        val debouncedPlacesProvider = FakePlacesAutocompleteProvider()
        val debouncedViewModel = JourneysViewModel(
            journeyRepository = journeyRepository,
            locationRepository = locationRepository,
            placesProvider = debouncedPlacesProvider,
            dispatcher = Dispatchers.Default,
            coroutineScope = kotlinx.coroutines.CoroutineScope(Dispatchers.Default),
            searchDebounceMs = 50L
        )
        debouncedViewModel.openCreateDialog()

        // Rapid typing: 3 queries within <50ms
        debouncedViewModel.updateCreateDestination("Query1")
        debouncedViewModel.updateCreateDestination("Query2")
        debouncedViewModel.updateCreateDestination("QueryFinal")

        // Wait for debounce window to elapse
        delay(120L)

        // Only the final query should have been queried
        assertEquals("QueryFinal", debouncedPlacesProvider.lastQuery)
        assertEquals(1, debouncedPlacesProvider.queryCallCount)
    }

    @Test
    fun initialState_loadsEmptyStateCorrectly() {
        val state = viewModel.uiState.value
        assertNull(state.activeJourney)
        assertTrue(state.historyJourneys.isEmpty())
        assertFalse(state.createForm.isOpen)
        assertFalse(state.extendForm.isOpen)
    }

    @Test
    fun openCreateDialog_setsDefaultValuesAndResetsSession() {
        viewModel.openCreateDialog()
        val form = viewModel.uiState.value.createForm

        assertTrue(form.isOpen)
        assertEquals("Current location", form.startLocation)
        assertEquals("", form.destination)
        assertTrue(form.expectedArrivalMillis > System.currentTimeMillis())
        assertNull(form.destinationError)
        assertTrue(placesProvider.sessionResetCallCount > 0)
    }

    @Test
    fun destinationSearch_shortQueryUnderThreeCharacters_doesNotSearch() {
        viewModel.openCreateDialog()
        viewModel.updateCreateDestination("Me")

        val form = viewModel.uiState.value.createForm
        assertFalse(form.isSearchingPlaces)
        assertTrue(form.placeSuggestions.isEmpty())
        assertEquals(0, placesProvider.queryCallCount)
    }

    @Test
    fun destinationSearch_validQuery_populatesSuggestions() {
        viewModel.openCreateDialog()
        viewModel.updateCreateDestination("Central")

        val form = viewModel.uiState.value.createForm
        assertEquals(1, placesProvider.queryCallCount)
        assertEquals("Central", placesProvider.lastQuery)
        assertEquals(2, form.placeSuggestions.size)
        assertEquals("Central Station", form.placeSuggestions.first().primaryText)
        assertNull(form.placesErrorMessage)
    }

    @Test
    fun destinationSearch_emptyResults_showsEmptyMessage() {
        placesProvider.autocompleteResultToReturn = AutocompleteResult.Empty
        viewModel.openCreateDialog()
        viewModel.updateCreateDestination("Unknown Place XYZ")

        val form = viewModel.uiState.value.createForm
        assertTrue(form.placeSuggestions.isEmpty())
        assertNotNull(form.placesErrorMessage)
        assertTrue(form.placesErrorMessage!!.contains("No matching places"))
    }

    @Test
    fun destinationSearch_providerError_showsErrorMessage() {
        placesProvider.autocompleteResultToReturn = AutocompleteResult.Error("Network failure")
        viewModel.openCreateDialog()
        viewModel.updateCreateDestination("Market")

        val form = viewModel.uiState.value.createForm
        assertTrue(form.placeSuggestions.isEmpty())
        assertEquals("Network failure", form.placesErrorMessage)
    }

    @Test
    fun destinationSearch_unconfiguredProvider_showsNotConfiguredNote() {
        placesProvider.isConfigured = false
        viewModel.openCreateDialog()
        viewModel.updateCreateDestination("Airport")

        val form = viewModel.uiState.value.createForm
        assertFalse(form.isSearchingPlaces)
        assertTrue(form.placeSuggestions.isEmpty())
        assertNotNull(form.placesErrorMessage)
        assertTrue(form.placesErrorMessage!!.contains("not configured"))
    }

    @Test
    fun selectPlaceSuggestion_resolvesCoordinatesAndPopulatesForm() {
        viewModel.openCreateDialog()
        viewModel.updateCreateDestination("Central")

        val suggestion = viewModel.uiState.value.createForm.placeSuggestions.first()
        viewModel.selectPlaceSuggestion(suggestion)

        val form = viewModel.uiState.value.createForm
        assertNotNull(form.selectedPlace)
        assertEquals("Central Station", form.selectedPlace!!.name)
        assertEquals(12.9783, form.destinationLatitude!!, 0.0001)
        assertEquals(77.5694, form.destinationLongitude!!, 0.0001)
        assertTrue(form.placeSuggestions.isEmpty())
    }

    @Test
    fun editingDestinationAfterPlaceSelection_invalidatesPreviousCoordinates() {
        viewModel.openCreateDialog()
        viewModel.updateCreateDestination("Central")
        val suggestion = viewModel.uiState.value.createForm.placeSuggestions.first()
        viewModel.selectPlaceSuggestion(suggestion)

        assertNotNull(viewModel.uiState.value.createForm.selectedPlace)
        assertNotNull(viewModel.uiState.value.createForm.destinationLatitude)

        // User edits text manually
        viewModel.updateCreateDestination("Central Station Platform 2")

        val form = viewModel.uiState.value.createForm
        assertNull(form.selectedPlace)
        assertNull(form.destinationLatitude)
        assertNull(form.destinationLongitude)
    }

    @Test
    fun selectManualFallback_marksManualWithoutCoordinates() {
        viewModel.openCreateDialog()
        viewModel.updateCreateDestination("Custom Village Road")
        viewModel.selectManualDestinationFallback()

        val form = viewModel.uiState.value.createForm
        assertTrue(form.isManualFallbackSelected)
        assertNull(form.selectedPlace)
        assertNull(form.destinationLatitude)
        assertNull(form.destinationLongitude)
        assertTrue(form.placeSuggestions.isEmpty())
    }

    @Test
    fun submitCreateJourney_blankDestination_setsError() {
        viewModel.openCreateDialog()
        viewModel.updateCreateDestination("")
        viewModel.submitCreateJourney()

        val form = viewModel.uiState.value.createForm
        assertNotNull(form.destinationError)
        assertNull(viewModel.uiState.value.activeJourney)
    }

    @Test
    fun submitCreateJourney_whenProviderConfiguredAndPlaceUnselected_blocksAccidentalSubmit() {
        viewModel.openCreateDialog()
        viewModel.updateCreateDestination("Metro Station") // typed text without selecting suggestion or manual fallback
        viewModel.submitCreateJourney()

        val form = viewModel.uiState.value.createForm
        assertNotNull(form.destinationError)
        assertTrue(form.destinationError!!.contains("Please select a suggestion"))
        assertNull(viewModel.uiState.value.activeJourney)
    }

    @Test
    fun submitCreateJourney_withSelectedPlace_persistsCoordinates() {
        viewModel.openCreateDialog()
        viewModel.updateCreateDestination("Central")
        val suggestion = viewModel.uiState.value.createForm.placeSuggestions.first()
        viewModel.selectPlaceSuggestion(suggestion)

        viewModel.submitCreateJourney()

        val state = viewModel.uiState.value
        assertFalse(state.createForm.isOpen)
        val active = state.activeJourney
        assertNotNull(active)
        assertEquals(12.9783, active!!.destinationLatitude!!, 0.0001)
        assertEquals(77.5694, active.destinationLongitude!!, 0.0001)
    }

    @Test
    fun submitCreateJourney_withManualFallback_startsJourneyWithoutCoordinates() {
        viewModel.openCreateDialog()
        viewModel.updateCreateDestination("Near Old Temple")
        viewModel.selectManualDestinationFallback()

        viewModel.submitCreateJourney()

        val state = viewModel.uiState.value
        assertFalse(state.createForm.isOpen)
        val active = state.activeJourney
        assertNotNull(active)
        assertEquals("Near Old Temple", active!!.destination)
        assertNull(active.destinationLatitude)
        assertNull(active.destinationLongitude)
    }

    @Test
    fun clearDestinationQuery_resetsQueryAndState() {
        viewModel.openCreateDialog()
        viewModel.updateCreateDestination("Central")
        viewModel.clearDestinationQuery()

        val form = viewModel.uiState.value.createForm
        assertEquals("", form.destination)
        assertNull(form.selectedPlace)
        assertTrue(form.placeSuggestions.isEmpty())
    }

    @Test
    fun performCheckIn_recordsCheckInAndSetsFeedback() {
        viewModel.openCreateDialog()
        viewModel.updateCreateDestination("Campus")
        viewModel.selectManualDestinationFallback()
        viewModel.submitCreateJourney()

        val active = viewModel.uiState.value.activeJourney
        assertNotNull(active)

        viewModel.performCheckIn(active!!.id)

        val updated = viewModel.uiState.value.activeJourney
        assertNotNull(updated)
        assertEquals(1, updated!!.checkInCount)
        assertNotNull(viewModel.uiState.value.feedbackMessage)
    }

    @Test
    fun openExtendDialog_initializesExpectedArrival() {
        viewModel.openCreateDialog()
        viewModel.updateCreateDestination("Market")
        viewModel.selectManualDestinationFallback()
        viewModel.submitCreateJourney()

        val active = requireNotNull(viewModel.uiState.value.activeJourney)

        viewModel.openExtendDialog(active)
        val extendForm = viewModel.uiState.value.extendForm

        assertTrue(extendForm.isOpen)
        assertEquals(active.id, extendForm.journeyId)
        assertTrue(extendForm.newExpectedArrivalMillis > active.expectedArrivalTimestamp)
    }

    @Test
    fun submitExtendJourney_updatesArrivalAndClosesDialog() {
        viewModel.openCreateDialog()
        viewModel.updateCreateDestination("Office")
        viewModel.selectManualDestinationFallback()
        viewModel.submitCreateJourney()

        val active = viewModel.uiState.value.activeJourney!!
        viewModel.openExtendDialog(active)
        viewModel.addMinutesToExtendEta(20)
        viewModel.submitExtendJourney()

        val state = viewModel.uiState.value
        assertFalse(state.extendForm.isOpen)
        assertTrue(state.activeJourney!!.expectedArrivalTimestamp > active.expectedArrivalTimestamp)
    }

    @Test
    fun finishJourney_movesActiveToHistoryAsCompleted() {
        viewModel.openCreateDialog()
        viewModel.updateCreateDestination("Home")
        viewModel.selectManualDestinationFallback()
        viewModel.submitCreateJourney()

        val active = viewModel.uiState.value.activeJourney!!
        viewModel.promptFinishJourney(active)
        assertNotNull(viewModel.uiState.value.finishConfirmJourney)

        viewModel.confirmFinishJourney()

        val state = viewModel.uiState.value
        assertNull(state.activeJourney)
        assertEquals(1, state.historyJourneys.size)
        assertEquals(JourneyStatus.COMPLETED, state.historyJourneys.first().status)
    }

    @Test
    fun cancelJourney_movesActiveToHistoryAsCancelled() {
        viewModel.openCreateDialog()
        viewModel.updateCreateDestination("Gym")
        viewModel.selectManualDestinationFallback()
        viewModel.submitCreateJourney()

        val active = viewModel.uiState.value.activeJourney!!
        viewModel.promptCancelJourney(active)
        assertNotNull(viewModel.uiState.value.cancelConfirmJourney)

        viewModel.confirmCancelJourney()

        val state = viewModel.uiState.value
        assertNull(state.activeJourney)
        assertEquals(1, state.historyJourneys.size)
        assertEquals(JourneyStatus.CANCELLED, state.historyJourneys.first().status)
    }

    @Test
    fun acquireLocation_populatesStartLocationWithCoordinates() {
        viewModel.openCreateDialog()
        viewModel.acquireCurrentLocationForStart()

        val form = viewModel.uiState.value.createForm
        assertFalse(form.isAcquiringLocation)
        assertTrue(form.startLocation.contains("12.9716"))
        assertEquals(12.9716, form.startLatitude!!, 0.0001)
    }

    @Test
    fun acquireLocation_permissionDenied_displaysHelpfulNote() {
        locationRepository.resultToReturn = LocationResult.PermissionDenied(false)
        viewModel.openCreateDialog()
        viewModel.acquireCurrentLocationForStart()

        val form = viewModel.uiState.value.createForm
        assertNotNull(form.locationNote)
        assertTrue(form.locationNote!!.contains("permission not granted"))
    }

    @Test
    fun activeJourney_loadsEventsTimelineFlow() {
        viewModel.openCreateDialog()
        viewModel.updateCreateDestination("Library")
        viewModel.selectManualDestinationFallback()
        viewModel.submitCreateJourney()

        val active = viewModel.uiState.value.activeJourney
        assertNotNull(active)

        // Initial active journey should have the STARTED event
        var events = viewModel.uiState.value.activeJourneyEvents
        assertEquals(1, events.size)
        assertEquals(JourneyEventType.STARTED, events.first().eventType)

        // Perform check-in
        viewModel.performCheckIn(active!!.id)

        events = viewModel.uiState.value.activeJourneyEvents
        assertEquals(2, events.size)
        assertEquals(JourneyEventType.CHECK_IN, events.first().eventType)
    }

    @Test
    fun journeyDetailsDialog_opensAndLoadsEvents_andDismissesCorrectly() {
        viewModel.openCreateDialog()
        viewModel.updateCreateDestination("Coffee Shop")
        viewModel.selectManualDestinationFallback()
        viewModel.submitCreateJourney()

        val active = viewModel.uiState.value.activeJourney!!
        viewModel.promptFinishJourney(active)
        viewModel.confirmFinishJourney()

        val historyJourney = viewModel.uiState.value.historyJourneys.first()
        viewModel.openJourneyDetails(historyJourney)

        val stateAfterOpen = viewModel.uiState.value
        assertEquals(historyJourney.id, stateAfterOpen.selectedJourneyForDetails?.id)
        assertTrue(stateAfterOpen.selectedJourneyEvents.isNotEmpty())
        assertEquals(JourneyEventType.COMPLETED, stateAfterOpen.selectedJourneyEvents.first().eventType)

        // Dismiss dialog
        viewModel.dismissJourneyDetails()
        val stateAfterDismiss = viewModel.uiState.value
        assertNull(stateAfterDismiss.selectedJourneyForDetails)
        assertTrue(stateAfterDismiss.selectedJourneyEvents.isEmpty())
    }

    @Test
    fun refreshStatus_triggersReconciliationSafely() {
        viewModel.refreshStatus()
        assertFalse(viewModel.uiState.value.isActionInProgress)
    }
}

