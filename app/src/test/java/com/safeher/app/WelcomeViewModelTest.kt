package com.safeher.app

import com.safeher.app.ui.screens.welcome.WelcomeViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WelcomeViewModelTest {
    @Test
    fun defaultState_isCorrect() {
        val viewModel = WelcomeViewModel()
        val state = viewModel.uiState.value

        assertEquals("SafeHer", state.appName)
        assertTrue(state.isFoundationReady)
        assertFalse(state.acknowledged)
    }

    @Test
    fun acknowledgeToggle_updatesState() {
        val viewModel = WelcomeViewModel()
        assertFalse(viewModel.uiState.value.acknowledged)

        viewModel.onAcknowledgeClicked()
        assertTrue(viewModel.uiState.value.acknowledged)

        viewModel.onAcknowledgeClicked()
        assertFalse(viewModel.uiState.value.acknowledged)
    }
}
