package com.safeher.app.data.sos

import android.content.Context
import android.content.SharedPreferences
import com.safeher.app.data.session.UserSessionManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Data class representing the user's SOS configuration.
 */
data class SosPreferences(
    val isDirectSmsConsentGiven: Boolean = false,
    val isDirectSmsEnabled: Boolean = false,
    val customSosMessage: String = DEFAULT_SOS_MESSAGE,
    val isLocationSharingEnabled: Boolean = true,
    val countdownDurationSeconds: Int = DEFAULT_COUNTDOWN_SECONDS,
    val isOverdueEscalationConsentGiven: Boolean = false,
    val isOverdueEscalationEnabled: Boolean = false,
    val overdueEscalationMinutes: Int = DEFAULT_OVERDUE_ESCALATION_MINUTES,
    /** Master switch for automatic low/critical battery SMS. Defaults OFF. */
    val isBatteryAlertsEnabled: Boolean = false,
    /** Explicit consent that battery alerts may send SMS (separate from SOS consent). Defaults OFF. */
    val isBatteryAlertsConsentGiven: Boolean = false,
    /** Separate consent to attach last-known/available location to battery alerts. Defaults OFF. */
    val isBatteryAlertLocationEnabled: Boolean = false,
    val batteryLowThresholdPercent: Int = DEFAULT_BATTERY_LOW_THRESHOLD,
    val batteryCriticalThresholdPercent: Int = DEFAULT_BATTERY_CRITICAL_THRESHOLD
) {
    companion object {
        const val DEFAULT_SOS_MESSAGE = "I NEED HELP. This is an emergency. Please contact me as soon as possible."
        const val DEFAULT_COUNTDOWN_SECONDS = 3
        const val DEFAULT_OVERDUE_ESCALATION_MINUTES = 5
        const val DEFAULT_BATTERY_LOW_THRESHOLD = 15
        const val DEFAULT_BATTERY_CRITICAL_THRESHOLD = 5
    }
}

/**
 * Repository interface for managing SOS preferences.
 */
interface SosPreferencesRepository {
    val preferencesFlow: StateFlow<SosPreferences>
    fun getPreferences(): SosPreferences
    fun setConsentGiven(consentGiven: Boolean)
    fun setDirectSmsEnabled(enabled: Boolean)
    fun setCustomSosMessage(message: String)
    fun setLocationSharingEnabled(enabled: Boolean)
    fun setCountdownDurationSeconds(seconds: Int)
    fun setOverdueEscalationConsent(consentGiven: Boolean)
    fun setOverdueEscalationEnabled(enabled: Boolean)
    fun setBatteryAlertsConsent(consentGiven: Boolean)
    fun setBatteryAlertsEnabled(enabled: Boolean)
    fun setBatteryAlertLocationEnabled(enabled: Boolean)
    fun setBatteryLowThresholdPercent(percent: Int)
    fun setBatteryCriticalThresholdPercent(percent: Int)
}

/**
 * SharedPreferences-backed implementation of [SosPreferencesRepository],
 * scoped to the active user session.
 */
class SosPreferencesRepositoryImpl(
    private val prefs: SharedPreferences,
    private val sessionManager: UserSessionManager? = null
) : SosPreferencesRepository {

    private val _preferencesFlow = MutableStateFlow(loadPreferences())
    override val preferencesFlow: StateFlow<SosPreferences> = _preferencesFlow.asStateFlow()

    private val userPrefix: String
        get() {
            val uid = sessionManager?.currentUserId
            return if (!uid.isNullOrBlank()) "user_${uid}_" else "global_"
        }

    private fun key(name: String): String = "$userPrefix$name"

    private fun loadPreferences(): SosPreferences {
        val consent = prefs.getBoolean(key(KEY_CONSENT_GIVEN), false)
        val enabled = prefs.getBoolean(key(KEY_DIRECT_SMS_ENABLED), false)
        val message = prefs.getString(key(KEY_CUSTOM_MESSAGE), SosPreferences.DEFAULT_SOS_MESSAGE)
            ?.ifBlank { SosPreferences.DEFAULT_SOS_MESSAGE } ?: SosPreferences.DEFAULT_SOS_MESSAGE
        val locSharing = prefs.getBoolean(key(KEY_LOCATION_SHARING), true)
        val countdown = prefs.getInt(key(KEY_COUNTDOWN_SECONDS), SosPreferences.DEFAULT_COUNTDOWN_SECONDS)
        val overdueConsent = prefs.getBoolean(key(KEY_OVERDUE_CONSENT_GIVEN), false)
        val overdueEnabled = prefs.getBoolean(key(KEY_OVERDUE_ENABLED), false)
        val batteryConsent = prefs.getBoolean(key(KEY_BATTERY_CONSENT_GIVEN), false)
        val batteryEnabled = prefs.getBoolean(key(KEY_BATTERY_ENABLED), false)
        val batteryLocation = prefs.getBoolean(key(KEY_BATTERY_LOCATION), false)
        val lowThreshold = prefs.getInt(key(KEY_BATTERY_LOW_THRESHOLD), SosPreferences.DEFAULT_BATTERY_LOW_THRESHOLD)
        val criticalThreshold = prefs.getInt(
            key(KEY_BATTERY_CRITICAL_THRESHOLD),
            SosPreferences.DEFAULT_BATTERY_CRITICAL_THRESHOLD
        )

        return SosPreferences(
            isDirectSmsConsentGiven = consent,
            isDirectSmsEnabled = consent && enabled, // Cannot be enabled without consent
            customSosMessage = message,
            isLocationSharingEnabled = locSharing,
            countdownDurationSeconds = countdown.coerceIn(1, 10),
            isOverdueEscalationConsentGiven = overdueConsent,
            isOverdueEscalationEnabled = overdueConsent && overdueEnabled,
            overdueEscalationMinutes = SosPreferences.DEFAULT_OVERDUE_ESCALATION_MINUTES,
            isBatteryAlertsConsentGiven = batteryConsent,
            isBatteryAlertsEnabled = batteryConsent && batteryEnabled,
            isBatteryAlertLocationEnabled = batteryLocation,
            batteryLowThresholdPercent = lowThreshold.coerceIn(5, 50),
            batteryCriticalThresholdPercent = criticalThreshold.coerceIn(1, 25)
        )
    }

    private fun refresh() {
        _preferencesFlow.value = loadPreferences()
    }

    override fun getPreferences(): SosPreferences = loadPreferences()

    override fun setConsentGiven(consentGiven: Boolean) {
        prefs.edit()
            .putBoolean(key(KEY_CONSENT_GIVEN), consentGiven)
            .apply()
        if (!consentGiven) {
            // Revoking consent automatically disables direct SMS and overdue escalation
            prefs.edit()
                .putBoolean(key(KEY_DIRECT_SMS_ENABLED), false)
                .putBoolean(key(KEY_OVERDUE_ENABLED), false)
                .apply()
        }
        refresh()
    }

    override fun setDirectSmsEnabled(enabled: Boolean) {
        val canEnable = enabled && getPreferences().isDirectSmsConsentGiven
        prefs.edit()
            .putBoolean(key(KEY_DIRECT_SMS_ENABLED), canEnable)
            .apply()
        refresh()
    }

    override fun setCustomSosMessage(message: String) {
        val clean = message.trim().ifBlank { SosPreferences.DEFAULT_SOS_MESSAGE }
        prefs.edit()
            .putString(key(KEY_CUSTOM_MESSAGE), clean)
            .apply()
        refresh()
    }

    override fun setLocationSharingEnabled(enabled: Boolean) {
        prefs.edit()
            .putBoolean(key(KEY_LOCATION_SHARING), enabled)
            .apply()
        refresh()
    }

    override fun setCountdownDurationSeconds(seconds: Int) {
        prefs.edit()
            .putInt(key(KEY_COUNTDOWN_SECONDS), seconds.coerceIn(1, 10))
            .apply()
        refresh()
    }

    override fun setOverdueEscalationConsent(consentGiven: Boolean) {
        prefs.edit()
            .putBoolean(key(KEY_OVERDUE_CONSENT_GIVEN), consentGiven)
            .apply()
        if (!consentGiven) {
            prefs.edit().putBoolean(key(KEY_OVERDUE_ENABLED), false).apply()
        }
        refresh()
    }

    override fun setOverdueEscalationEnabled(enabled: Boolean) {
        val canEnable = enabled && getPreferences().isOverdueEscalationConsentGiven
        prefs.edit()
            .putBoolean(key(KEY_OVERDUE_ENABLED), canEnable)
            .apply()
        refresh()
    }

    override fun setBatteryAlertsConsent(consentGiven: Boolean) {
        prefs.edit()
            .putBoolean(key(KEY_BATTERY_CONSENT_GIVEN), consentGiven)
            .apply()
        if (!consentGiven) {
            prefs.edit().putBoolean(key(KEY_BATTERY_ENABLED), false).apply()
        }
        refresh()
    }

    override fun setBatteryAlertsEnabled(enabled: Boolean) {
        val canEnable = enabled && getPreferences().isBatteryAlertsConsentGiven
        prefs.edit()
            .putBoolean(key(KEY_BATTERY_ENABLED), canEnable)
            .apply()
        refresh()
    }

    override fun setBatteryAlertLocationEnabled(enabled: Boolean) {
        prefs.edit()
            .putBoolean(key(KEY_BATTERY_LOCATION), enabled)
            .apply()
        refresh()
    }

    override fun setBatteryLowThresholdPercent(percent: Int) {
        prefs.edit()
            .putInt(key(KEY_BATTERY_LOW_THRESHOLD), percent.coerceIn(5, 50))
            .apply()
        refresh()
    }

    override fun setBatteryCriticalThresholdPercent(percent: Int) {
        prefs.edit()
            .putInt(key(KEY_BATTERY_CRITICAL_THRESHOLD), percent.coerceIn(1, 25))
            .apply()
        refresh()
    }

    companion object {
        const val PREFS_NAME = "safeher_sos_preferences"
        private const val KEY_CONSENT_GIVEN = "direct_sms_consent_given"
        private const val KEY_DIRECT_SMS_ENABLED = "direct_sms_enabled"
        private const val KEY_CUSTOM_MESSAGE = "direct_sms_custom_message"
        private const val KEY_LOCATION_SHARING = "direct_sms_location_sharing"
        private const val KEY_COUNTDOWN_SECONDS = "direct_sms_countdown_seconds"
        private const val KEY_OVERDUE_CONSENT_GIVEN = "overdue_escalation_consent_given"
        private const val KEY_OVERDUE_ENABLED = "overdue_escalation_enabled"
        private const val KEY_BATTERY_CONSENT_GIVEN = "battery_alerts_consent_given"
        private const val KEY_BATTERY_ENABLED = "battery_alerts_enabled"
        private const val KEY_BATTERY_LOCATION = "battery_alerts_location_enabled"
        private const val KEY_BATTERY_LOW_THRESHOLD = "battery_low_threshold_percent"
        private const val KEY_BATTERY_CRITICAL_THRESHOLD = "battery_critical_threshold_percent"

        fun create(
            context: Context,
            sessionManager: UserSessionManager? = null
        ): SosPreferencesRepository {
            val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return SosPreferencesRepositoryImpl(prefs, sessionManager)
        }
    }
}
