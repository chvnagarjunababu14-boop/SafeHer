package com.safeher.app.data.battery

import android.content.Context
import android.content.SharedPreferences
import com.safeher.app.data.session.UserSessionManager

/**
 * Persists duplicate-prevention state for battery alerts across process restarts.
 * Separate from user preference toggles so alert history cannot re-arm incorrectly.
 */
class BatteryAlertStateStore(
    private val prefs: SharedPreferences,
    private val sessionManager: UserSessionManager? = null
) {
    private val userPrefix: String
        get() {
            val uid = sessionManager?.currentUserId
            return if (!uid.isNullOrBlank()) "user_${uid}_" else "global_"
        }

    private fun key(name: String): String = "$userPrefix$name"

    var lastObservedPercent: Int?
        get() {
            if (!prefs.contains(key(KEY_LAST_PERCENT))) return null
            return prefs.getInt(key(KEY_LAST_PERCENT), -1).takeIf { it in 0..100 }
        }
        set(value) {
            if (value == null) {
                prefs.edit().remove(key(KEY_LAST_PERCENT)).apply()
            } else {
                prefs.edit().putInt(key(KEY_LAST_PERCENT), value.coerceIn(0, 100)).apply()
            }
        }

    var firedLowForCycle: Boolean
        get() = prefs.getBoolean(key(KEY_FIRED_LOW), false)
        set(value) {
            prefs.edit().putBoolean(key(KEY_FIRED_LOW), value).apply()
        }

    var firedCriticalForCycle: Boolean
        get() = prefs.getBoolean(key(KEY_FIRED_CRITICAL), false)
        set(value) {
            prefs.edit().putBoolean(key(KEY_FIRED_CRITICAL), value).apply()
        }

    companion object {
        const val PREFS_NAME = "safeher_battery_alert_state"
        private const val KEY_LAST_PERCENT = "last_observed_percent"
        private const val KEY_FIRED_LOW = "fired_low_for_cycle"
        private const val KEY_FIRED_CRITICAL = "fired_critical_for_cycle"

        fun create(context: Context, sessionManager: UserSessionManager? = null): BatteryAlertStateStore {
            val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return BatteryAlertStateStore(prefs, sessionManager)
        }
    }
}
