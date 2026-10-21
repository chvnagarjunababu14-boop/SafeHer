package com.safeher.app

import com.safeher.app.data.location.LocationData
import com.safeher.app.data.location.LocationFreshness
import com.safeher.app.data.location.LocationRepository
import com.safeher.app.data.location.LocationResult
import com.safeher.app.ui.screens.map.MapUiState
import com.safeher.app.ui.screens.map.MapViewModel
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FakeLocationRepository : LocationRepository {
    var isEnabled: Boolean = true
    var resultToReturn: LocationResult = LocationResult.Success(
        LocationData(
            latitude = 12.9716,
            longitude = 77.5946,
            accuracyMeters = 15.0f,
            timestampMillis = 1700000000000L,
            isFresh = true,
            isApproximate = false,
            provider = "fused"
        )
    )

    override suspend fun getCurrentLocation(isPrecise: Boolean): LocationResult {
        return resultToReturn
    }

    override fun isLocationServiceEnabled(): Boolean {
        return isEnabled
    }
}

class MapViewModelTest {

    private fun createViewModel(repo: LocationRepository) = MapViewModel(
        locationRepository = repo,
        dispatcher = Dispatchers.Unconfined
    )

    @Test
    fun defaultState_isInitial() {
        val repo = FakeLocationRepository()
        val viewModel = createViewModel(repo)

        assertEquals(MapUiState.Initial, viewModel.uiState.value)
    }

    @Test
    fun permissionDenied_emitsPermissionRequired() {
        val repo = FakeLocationRepository()
        val viewModel = createViewModel(repo)

        viewModel.onPermissionResult(hasFine = false, hasCoarse = false, isPermanentlyDenied = true)

        val state = viewModel.uiState.value
        assertTrue(state is MapUiState.PermissionRequired)
        assertTrue((state as MapUiState.PermissionRequired).isPermanentlyDenied)
    }

    @Test
    fun locationDisabled_emitsLocationDisabled() {
        val repo = FakeLocationRepository().apply {
            isEnabled = false
        }
        val viewModel = createViewModel(repo)

        viewModel.fetchLocation(isPrecise = true)

        assertEquals(MapUiState.LocationDisabled, viewModel.uiState.value)
    }

    @Test
    fun fetchLocationSuccess_emitsSuccessWithLocation() {
        val repo = FakeLocationRepository()
        val viewModel = createViewModel(repo)

        viewModel.onPermissionResult(hasFine = true, hasCoarse = true)

        val state = viewModel.uiState.value
        assertTrue(state is MapUiState.Success)
        val success = state as MapUiState.Success
        assertEquals(12.9716, success.location.latitude, 0.0001)
        assertEquals(77.5946, success.location.longitude, 0.0001)
        assertTrue(success.location.isFresh)
    }

    @Test
    fun recenterMap_incrementsRecenterCounter() {
        val repo = FakeLocationRepository()
        val viewModel = createViewModel(repo)

        viewModel.fetchLocation(isPrecise = true)
        val initialCounter = (viewModel.uiState.value as MapUiState.Success).recenterCounter

        viewModel.recenterMap()
        val nextCounter = (viewModel.uiState.value as MapUiState.Success).recenterCounter
        assertEquals(initialCounter + 1, nextCounter)
    }

    @Test
    fun stopLocation_resetsToInitial() {
        val repo = FakeLocationRepository()
        val viewModel = createViewModel(repo)

        viewModel.fetchLocation(isPrecise = true)
        assertTrue(viewModel.uiState.value is MapUiState.Success)

        viewModel.stopLocation()
        assertEquals(MapUiState.Initial, viewModel.uiState.value)
    }

    @Test
    fun locationError_emitsErrorState() {
        val repo = FakeLocationRepository().apply {
            resultToReturn = LocationResult.Error("GPS signal lost")
        }
        val viewModel = createViewModel(repo)

        viewModel.fetchLocation(isPrecise = true)

        val state = viewModel.uiState.value
        assertTrue(state is MapUiState.Error)
        assertEquals("GPS signal lost", (state as MapUiState.Error).message)
    }

    @Test
    fun permissionRevoked_whenInSuccess_emitsPermissionRequired() {
        val repo = FakeLocationRepository()
        val viewModel = createViewModel(repo)

        viewModel.fetchLocation(isPrecise = true)
        assertTrue(viewModel.uiState.value is MapUiState.Success)

        viewModel.onPermissionRevoked()
        val state = viewModel.uiState.value
        assertTrue(state is MapUiState.PermissionRequired)
        assertFalse((state as MapUiState.PermissionRequired).isPermanentlyDenied)
    }

    @Test
    fun locationDisabled_whenInSuccess_emitsLocationDisabled() {
        val repo = FakeLocationRepository()
        val viewModel = createViewModel(repo)

        viewModel.fetchLocation(isPrecise = true)
        assertTrue(viewModel.uiState.value is MapUiState.Success)

        viewModel.onLocationDisabled()
        assertEquals(MapUiState.LocationDisabled, viewModel.uiState.value)
    }

    @Test
    fun approximateLocation_emitsSuccessWithApproximateFlag() {
        val repo = FakeLocationRepository().apply {
            resultToReturn = LocationResult.Success(
                LocationData(
                    latitude = 12.9716,
                    longitude = 77.5946,
                    accuracyMeters = 500.0f,
                    timestampMillis = System.currentTimeMillis(),
                    isFresh = true,
                    isApproximate = true,
                    provider = "network",
                    freshness = LocationFreshness.FRESH
                )
            )
        }
        val viewModel = createViewModel(repo)

        viewModel.fetchLocation(isPrecise = false)

        val state = viewModel.uiState.value as MapUiState.Success
        assertTrue("Approximate location flag must be true", state.location.isApproximate)
        assertEquals(500.0f, state.location.accuracyMeters, 0.01f)
    }

    @Test
    fun staleLocation_emitsSuccessWithStaleFreshness() {
        val repo = FakeLocationRepository().apply {
            resultToReturn = LocationResult.Success(
                LocationData(
                    latitude = 12.9716,
                    longitude = 77.5946,
                    accuracyMeters = 25.0f,
                    timestampMillis = System.currentTimeMillis() - 20 * 60_000L, // 20 mins ago
                    isFresh = false,
                    isApproximate = false,
                    provider = "cached",
                    freshness = LocationFreshness.STALE
                )
            )
        }
        val viewModel = createViewModel(repo)

        viewModel.fetchLocation(isPrecise = true)

        val state = viewModel.uiState.value as MapUiState.Success
        assertFalse(state.location.isFresh)
        assertTrue(state.location.isStale)
        assertEquals(LocationFreshness.STALE, state.location.freshness)
    }

    @Test
    fun isLocationServiceEnabled_delegatesToRepository() {
        val repo = FakeLocationRepository()
        val viewModel = createViewModel(repo)

        repo.isEnabled = true
        assertTrue(viewModel.isLocationServiceEnabled())

        repo.isEnabled = false
        assertFalse(viewModel.isLocationServiceEnabled())
    }

    @Test
    fun permissionDenied_notPermanent_emitsPermissionRequiredWithFlagFalse() {
        val repo = FakeLocationRepository()
        val viewModel = createViewModel(repo)

        viewModel.onPermissionResult(hasFine = false, hasCoarse = false, isPermanentlyDenied = false)

        val state = viewModel.uiState.value
        assertTrue(state is MapUiState.PermissionRequired)
        assertFalse((state as MapUiState.PermissionRequired).isPermanentlyDenied)
    }
}
