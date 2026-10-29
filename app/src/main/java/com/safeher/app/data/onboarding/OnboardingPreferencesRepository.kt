package com.safeher.app.data.onboarding

import android.content.Context
import android.content.SharedPreferences

/**
 * Persists onboarding and first-launch states for SafeHer.
 *
 * Keeps device-level introduction separate from account-scoped setup:
 * 1. [isIntroductionCompleted] / [setIntroductionCompleted]: Tracks whether the 4 swipeable
 *    onboarding slides have been viewed or skipped on this device.
 * 2. [isUserSetupCompleted] / [setUserSetupCompleted]: Tracks whether a specific authenticated user
 *    (by Firebase UID or Demo UID) has completed or deferred their initial safety setup checklist.
 *    Strictly scoped per user ID so newly authenticated accounts never inherit setup state from others.
 */
interface OnboardingPreferencesRepository {
    fun isIntroductionCompleted(): Boolean
    fun setIntroductionCompleted(completed: Boolean)

    fun isUserSetupCompleted(userId: String): Boolean
    fun setUserSetupCompleted(userId: String, completed: Boolean)

    fun clearForUser(userId: String)
}

class OnboardingPreferencesRepositoryImpl(
    private val prefs: SharedPreferences
) : OnboardingPreferencesRepository {

    override fun isIntroductionCompleted(): Boolean {
        return prefs.getBoolean(KEY_INTRO_COMPLETED, false)
    }

    override fun setIntroductionCompleted(completed: Boolean) {
        prefs.edit().putBoolean(KEY_INTRO_COMPLETED, completed).apply()
    }

    override fun isUserSetupCompleted(userId: String): Boolean {
        if (userId.isBlank()) return false
        return prefs.getBoolean(KEY_SETUP_COMPLETED_PREFIX + userId, false)
    }

    override fun setUserSetupCompleted(userId: String, completed: Boolean) {
        if (userId.isBlank()) return
        prefs.edit().putBoolean(KEY_SETUP_COMPLETED_PREFIX + userId, completed).apply()
    }

    override fun clearForUser(userId: String) {
        if (userId.isBlank()) return
        prefs.edit().remove(KEY_SETUP_COMPLETED_PREFIX + userId).apply()
    }

    companion object {
        const val PREFS_NAME = "safeher_onboarding_prefs"
        const val KEY_INTRO_COMPLETED = "key_intro_completed"
        const val KEY_SETUP_COMPLETED_PREFIX = "setup_completed_"

        fun create(context: Context): OnboardingPreferencesRepository {
            val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return OnboardingPreferencesRepositoryImpl(prefs)
        }
    }
}
