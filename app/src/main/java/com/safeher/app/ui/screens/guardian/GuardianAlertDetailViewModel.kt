package com.safeher.app.ui.screens.guardian

import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.safeher.app.data.remotesos.RemoteAlertDetails
import com.safeher.app.data.remotesos.RemoteSosRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AlertDetailUiState(
    val alertId: String = "",
    val isLoading: Boolean = true,
    val alertDetails: RemoteAlertDetails? = null,
    val isAcknowledging: Boolean = false,
    val errorMessage: String? = null,
    val acknowledgeSuccess: Boolean = false
)

class GuardianAlertDetailViewModel(
    private val alertId: String,
    private val remoteSosRepository: RemoteSosRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AlertDetailUiState(alertId = alertId))
    val uiState: StateFlow<AlertDetailUiState> = _uiState.asStateFlow()

    init {
        loadDetails()
    }

    fun loadDetails() {
        if (alertId.isBlank()) {
            _uiState.update { it.copy(isLoading = false, errorMessage = "Invalid alert identifier.") }
            return
        }

        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            val result = remoteSosRepository.getAlertDetails(alertId)
            result.fold(
                onSuccess = { details ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            alertDetails = details,
                            acknowledgeSuccess = details.isAcknowledgedByMe
                        )
                    }
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = err.localizedMessage ?: "Failed to retrieve alert details."
                        )
                    }
                }
            )
        }
    }

    fun acknowledgeAlert() {
        val currentAlertId = _uiState.value.alertId
        if (currentAlertId.isBlank() || _uiState.value.isAcknowledging) return

        _uiState.update { it.copy(isAcknowledging = true, errorMessage = null) }
        viewModelScope.launch {
            val deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}"
            val result = remoteSosRepository.acknowledgeAlert(currentAlertId, deviceModel)
            result.fold(
                onSuccess = {
                    _uiState.update {
                        it.copy(
                            isAcknowledging = false,
                            acknowledgeSuccess = true,
                            alertDetails = it.alertDetails?.copy(isAcknowledgedByMe = true)
                        )
                    }
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(
                            isAcknowledging = false,
                            errorMessage = err.localizedMessage ?: "Failed to record acknowledgement."
                        )
                    }
                }
            )
        }
    }
}
