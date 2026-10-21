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
    defaultMode: Boolean? = null
) {

    private val _isDemoMode: MutableStateFlow<Boolean>
    val isDemoMode: StateFlow<Boolean>

    private val _activeProfile: MutableStateFlow<DemoProfile>
    val activeProfile: StateFlow<DemoProfile>

    private val resetListeners = mutableListOf<() -> Unit>()

    init {
        // Initial state determined by build config or user toggle in prefs
        val default = defaultMode ?: try {
            BuildConfig.IS_DEMO_DEFAULT
        } catch (_: Throwable) {
            true
        }
        val initialDemoMode = prefs.getBoolean(KEY_IS_DEMO_MODE, default)
        _isDemoMode = MutableStateFlow(initialDemoMode)
        isDemoMode = _isDemoMode.asStateFlow()

        val savedProfileName = prefs.getString(KEY_ACTIVE_PROFILE, DemoProfile.USER.name)
        val initialProfile = try {
            DemoProfile.valueOf(savedProfileName ?: DemoProfile.USER.name)
        } catch (_: Exception) {
            DemoProfile.USER
        }
        _activeProfile = MutableStateFlow(initialProfile)
        activeProfile = _activeProfile.asStateFlow()
    }

    /**
     * Toggles Demo Mode on or off explicitly.
     */
    fun setDemoMode(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_IS_DEMO_MODE, enabled).apply()
        _isDemoMode.value = enabled
    }

    /**
     * Switches between fictional Demo User and Demo Guardian profiles.
     */
    fun switchProfile(profile: DemoProfile) {
        prefs.edit().putString(KEY_ACTIVE_PROFILE, profile.name).apply()
        _activeProfile.value = profile
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
        switchProfile(DemoProfile.USER)
        val listenersCopy: List<() -> Unit>
        synchronized(resetListeners) {
            listenersCopy = resetListeners.toList()
        }
        listenersCopy.forEach { it.invoke() }
    }

    companion object {
        private const val PREFS_NAME = "safeher_demo_mode_prefs"
        private const val KEY_IS_DEMO_MODE = "key_is_demo_mode"
        private const val KEY_ACTIVE_PROFILE = "key_active_profile"

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
            manager.switchProfile(DemoProfile.USER)
            return manager
        }
    }
}
