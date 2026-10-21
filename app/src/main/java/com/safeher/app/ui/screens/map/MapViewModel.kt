package com.safeher.app.ui.screens.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.safeher.app.data.location.LocationData
import com.safeher.app.data.location.LocationRepository
import com.safeher.app.data.location.LocationResult
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface MapUiState {
    data object Initial : MapUiState
    data object Loading : MapUiState
    data class Success(
        val location: LocationData,
        val recenterCounter: Int = 0
    ) : MapUiState
    data class PermissionRequired(
        val isPermanentlyDenied: Boolean = false
    ) : MapUiState
    data object LocationDisabled : MapUiState
    data class Error(val message: String) : MapUiState
}

class MapViewModel(
    private val locationRepository: LocationRepository,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Main
) : ViewModel() {

    private val _uiState = MutableStateFlow<MapUiState>(MapUiState.Initial)
    val uiState: StateFlow<MapUiState> = _uiState.asStateFlow()

    fun onPermissionResult(
        hasFine: Boolean,
        hasCoarse: Boolean,
        isPermanentlyDenied: Boolean = false
    ) {
        if (!hasFine && !hasCoarse) {
            _uiState.value = MapUiState.PermissionRequired(isPermanentlyDenied = isPermanentlyDenied)
        } else {
            fetchLocation(isPrecise = hasFine)
        }
    }

    fun fetchLocation(isPrecise: Boolean) {
        viewModelScope.launch(dispatcher) {
            if (!locationRepository.isLocationServiceEnabled()) {
                _uiState.value = MapUiState.LocationDisabled
                return@launch
            }

            _uiState.value = MapUiState.Loading

            when (val result = locationRepository.getCurrentLocation(isPrecise = isPrecise)) {
                is LocationResult.Success -> {
                    val currentSuccess = _uiState.value as? MapUiState.Success
                    val newCounter = (currentSuccess?.recenterCounter ?: 0) + 1
                    _uiState.value = MapUiState.Success(
                        location = result.location,
                        recenterCounter = newCounter
                    )
                }
                is LocationResult.LocationDisabled -> {
                    _uiState.value = MapUiState.LocationDisabled
                }
                is LocationResult.PermissionDenied -> {
                    _uiState.value = MapUiState.PermissionRequired(
                        isPermanentlyDenied = result.isPermanentlyDenied
                    )
                }
                is LocationResult.Error -> {
                    _uiState.value = MapUiState.Error(result.message)
                }
            }
        }
    }

    fun recenterMap() {
        val current = _uiState.value
        if (current is MapUiState.Success) {
            _uiState.value = current.copy(recenterCounter = current.recenterCounter + 1)
        }
    }

    fun onPermissionRevoked() {
        _uiState.value = MapUiState.PermissionRequired(isPermanentlyDenied = false)
    }

    fun onLocationDisabled() {
        _uiState.value = MapUiState.LocationDisabled
    }

    fun isLocationServiceEnabled(): Boolean = locationRepository.isLocationServiceEnabled()

    fun stopLocation() {
        _uiState.value = MapUiState.Initial
    }
}
