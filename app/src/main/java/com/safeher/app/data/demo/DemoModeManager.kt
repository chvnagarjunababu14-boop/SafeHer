package com.safeher.app.data.demo

import android.content.Context
import android.content.SharedPreferences
import com.safeher.app.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Manages the explicit Demo Mode state and active fictional demo profile.
 *
 * Ensures clean separation between production Firebase services and local simulated demo mode.
 * SafeHer Demo Mode operates completely locally without requiring Firebase credentials,
 * network calls, or paid cloud resources, while leaving Room database data strictly intact.
 */
class DemoModeManager(
    private val prefs: SharedPreferences,
    private val explicitAllowedMode: Boolean? = null
) {

    private val isDemoAllowed: Boolean = explicitAllowedMode ?: try {
        BuildConfig.IS_DEMO_DEFAULT
    } catch (_: Throwable) {
        false
    }

    private val _isDemoMode: MutableStateFlow<Boolean>
    val isDemoMode: StateFlow<Boolean>

    private val _activeProfile: MutableStateFlow<DemoProfile>
    val activeProfile: StateFlow<DemoProfile>

    private val resetListeners = mutableListOf<() -> Unit>()

    init {
        // Strict separation: Demo Mode is only available in the demo flavor / test environments
        val initialDemoMode = if (isDemoAllowed) {
            val default = explicitAllowedMode ?: true
            prefs.getBoolean(KEY_IS_DEMO_MODE, default)
        } else {
            // In production builds, clear any legacy demo flag in prefs and strictly disable demo mode
            try {
                prefs.edit().remove(KEY_IS_DEMO_MODE).apply()
            } catch (_: Throwable) {
                // Ignore SharedPreferences exceptions
            }
            false
        }
        _isDemoMode = MutableStateFlow(initialDemoMode)
        isDemoMode = _isDemoMode.asStateFlow()

        _activeProfile = MutableStateFlow(DemoProfile.USER)
        activeProfile = _activeProfile.asStateFlow()
    }

    /**
     * Toggles Demo Mode on or off explicitly (only allowed in demo flavor / test environments).
     */
    fun setDemoMode(enabled: Boolean) {
        if (!isDemoAllowed) {
            _isDemoMode.value = false
            return
        }
        prefs.edit().putBoolean(KEY_IS_DEMO_MODE, enabled).apply()
        _isDemoMode.value = enabled
    }

    /**
     * Registers a callback invoked when demo data is reset.
     */
    fun registerResetListener(listener: () -> Unit) {
        synchronized(resetListeners) {
            resetListeners.add(listener)
        }
    }

    /**
     * Safely resets only in-memory and demo preferences data without touching
     * the Room database (local guardians, journeys, event timeline).
     */
    fun resetDemoData() {
        val listenersCopy: List<() -> Unit>
        synchronized(resetListeners) {
            listenersCopy = resetListeners.toList()
        }
        listenersCopy.forEach { it.invoke() }
    }

    companion object {
        private const val PREFS_NAME = "safeher_demo_mode_prefs"
        private const val KEY_IS_DEMO_MODE = "key_is_demo_mode"

        @Volatile
        private var instance: DemoModeManager? = null

        fun getInstance(context: Context): DemoModeManager {
            return instance ?: synchronized(this) {
                instance ?: DemoModeManager(
                    context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                ).also { instance = it }
            }
        }

        // For unit tests
        fun createForTesting(prefs: SharedPreferences, initialDemoMode: Boolean = true): DemoModeManager {
            val manager = DemoModeManager(prefs, initialDemoMode)
            manager.setDemoMode(initialDemoMode)
            return manager
        }
    }
}
