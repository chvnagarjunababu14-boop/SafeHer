package com.safeher.app.ui.screens.welcome

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class WelcomeUiState(
    val appName: String = "SafeHer",
    val subtitle: String = "Your personal safety & rapid assistance companion",
    val phaseTitle: String = "Phase 1: Fresh Foundation Setup",
    val isFoundationReady: Boolean = true,
    val securityHighlights: List<String> = listOf(
        "Privacy-by-Design Architecture",
        "Offline-First Emergency Fallbacks",
        "Zero Non-Essential Data Collection",
        "Material 3 & Jetpack Compose Native"
    ),
    val acknowledged: Boolean = false
)

class WelcomeViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(WelcomeUiState())
    val uiState: StateFlow<WelcomeUiState> = _uiState.asStateFlow()

    fun onAcknowledgeClicked() {
        _uiState.value = _uiState.value.copy(
            acknowledged = !_uiState.value.acknowledged
        )
    }
}
