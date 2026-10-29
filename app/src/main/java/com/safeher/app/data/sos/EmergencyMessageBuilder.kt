package com.safeher.app.data.sos

import android.content.Intent
import android.net.Uri
import com.safeher.app.data.battery.BatteryInfo
import com.safeher.app.data.battery.BatteryInfoFormatter
import com.safeher.app.data.guardian.Guardian
import com.safeher.app.data.location.LocationData
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Pure, testable helper responsible for formatting emergency messages,
 * cleaning/deduplicating recipient phone numbers, and creating Android SMS intents.
 *
 * Privacy Guarantees:
 * - Operates entirely locally in memory.
 * - Never transmits or logs phone numbers, coordinates, or message content.
 * - Coordinates are only included if fresh and explicitly consented to by the user.
 */
object EmergencyMessageBuilder {

    const val DEFAULT_EMERGENCY_PREFIX = "EMERGENCY: I need help right now."
    const val LOCATION_UNAVAILABLE_FALLBACK = "Location: Unavailable (signal unavailable or permission denied)."
    const val SMS_FOOTER = "[Sent via SafeHer user-confirmed SMS]"
    const val BATTERY_ALERT_FOOTER = "[SafeHer battery safety alert — not an SOS]"

    private fun formatTimestamp(millis: Long): String {
        return SimpleDateFormat("yyyy-MM-dd h:mm a", Locale.getDefault()).format(Date(millis))
    }

    private fun formatLocationAge(location: LocationData, nowMillis: Long): String {
        val ageMinutes = ((nowMillis - location.timestampMillis).coerceAtLeast(0L) / 60_000L)
        return when {
            ageMinutes < 1L -> "just now"
            ageMinutes == 1L -> "1 minute ago"
            ageMinutes < 60L -> "$ageMinutes minutes ago"
            else -> formatTimestamp(location.timestampMillis)
        }
    }

    private fun appendBatteryAndTime(
        parts: MutableList<String>,
        batteryInfo: BatteryInfo?,
        alertTimestampMillis: Long
    ) {
        if (batteryInfo != null) {
            parts.add(BatteryInfoFormatter.formatPercentLine(batteryInfo))
            parts.add(BatteryInfoFormatter.formatChargingLine(batteryInfo))
        }
        parts.add("Time: ${formatTimestamp(alertTimestampMillis)}")
    }

    private fun appendLocationBlock(
        parts: MutableList<String>,
        location: LocationData?,
        includeLocation: Boolean,
        nowMillis: Long,
        allowStaleLastKnown: Boolean = false
    ) {
        if (!includeLocation) return
        val usable = when {
            location == null -> null
            !location.isStale -> location
            allowStaleLastKnown -> location
            else -> null
        }
        if (usable != null) {
            val lat = String.format(Locale.US, "%.6f", usable.latitude)
            val lng = String.format(Locale.US, "%.6f", usable.longitude)
            val label = if (usable.isStale) "Last known location" else "Location"
            parts.add("$label: https://maps.google.com/?q=$lat,$lng")
            parts.add("Location updated: ${formatLocationAge(usable, nowMillis)}")
        } else {
            parts.add(LOCATION_UNAVAILABLE_FALLBACK)
        }
    }

    /**
     * Builds a concise, clear emergency SMS body.
     *
     * @param userName Name of the user (omitted if null, blank, or "Guest").
     * @param customNote Optional user-provided message text.
     * @param location Fresh on-demand location data, if available.
     * @param includeLocation Whether the user consented to share coordinates.
     */
    fun buildMessage(
        userName: String? = null,
        customNote: String? = null,
        location: LocationData? = null,
        includeLocation: Boolean = true
    ): String {
        val parts = mutableListOf<String>()

        // 1. Clear distress header
        parts.add(DEFAULT_EMERGENCY_PREFIX)

        // 2. Sender identification (if meaningful)
        val trimmedName = userName?.trim()
        if (!trimmedName.isNullOrEmpty() && !trimmedName.equals("Guest", ignoreCase = true)) {
            parts.add("Sender: $trimmedName")
        }

        // 3. User custom note (if any)
        val trimmedNote = customNote?.trim()
        if (!trimmedNote.isNullOrEmpty()) {
            parts.add(trimmedNote)
        }

        // 4. Location link or explicit honest fallback
        if (includeLocation) {
            if (location != null && !location.isStale) {
                val lat = String.format(java.util.Locale.US, "%.6f", location.latitude)
                val lng = String.format(java.util.Locale.US, "%.6f", location.longitude)
                val acc = location.accuracyMeters.toInt()
                val accuracyInfo = if (acc > 0) " (±${acc}m)" else ""
                parts.add("Location: https://maps.google.com/?q=$lat,$lng$accuracyInfo")
            } else {
                parts.add(LOCATION_UNAVAILABLE_FALLBACK)
            }
        }

        // 5. Honest disclaimer footer
        parts.add(SMS_FOOTER)

        return parts.joinToString("\n\n")
    }

    /**
     * Builds a direct SOS SMS text using the user's configured message,
     * battery state captured at dispatch, timestamp, and optional location link.
     */
    fun buildDirectSosMessage(
        customMessage: String? = null,
        location: LocationData? = null,
        includeLocation: Boolean = true,
        batteryInfo: BatteryInfo? = null,
        alertTimestampMillis: Long = System.currentTimeMillis()
    ): String {
        val baseMessage = customMessage?.trim()?.ifBlank { null }
            ?: "I NEED HELP. This is an emergency. Please contact me as soon as possible."

        val parts = mutableListOf<String>()
        parts.add("SAFEHER SOS ALERT")
        parts.add(baseMessage)
        appendBatteryAndTime(parts, batteryInfo, alertTimestampMillis)
        appendLocationBlock(parts, location, includeLocation, alertTimestampMillis, allowStaleLastKnown = false)
        return parts.joinToString("\n")
    }

    /**
     * Builds a low/critical battery warning SMS that is distinct from emergency SOS.
     */
    fun buildBatteryAlertMessage(
        kind: BatteryAlertKind,
        batteryInfo: BatteryInfo,
        location: LocationData? = null,
        includeLocation: Boolean = false,
        alertTimestampMillis: Long = System.currentTimeMillis()
    ): String {
        val parts = mutableListOf<String>()
        parts.add(
            when (kind) {
                BatteryAlertKind.LOW -> "SAFEHER LOW BATTERY ALERT"
                BatteryAlertKind.CRITICAL -> "SAFEHER CRITICAL BATTERY ALERT"
            }
        )
        parts.add(
            when (kind) {
                BatteryAlertKind.LOW ->
                    "My phone battery is low. This is a safety check-in, not an SOS."
                BatteryAlertKind.CRITICAL ->
                    "My phone battery is critically low and may power off soon. " +
                        "This is a best-effort warning — delivery is not guaranteed if the device shuts down."
            }
        )
        appendBatteryAndTime(parts, batteryInfo, alertTimestampMillis)
        if (includeLocation) {
            appendLocationBlock(
                parts,
                location,
                includeLocation = true,
                nowMillis = alertTimestampMillis,
                allowStaleLastKnown = true
            )
        }
        parts.add(BATTERY_ALERT_FOOTER)
        return parts.joinToString("\n")
    }

    enum class BatteryAlertKind {
        LOW,
        CRITICAL
    }

    /**
     * Builds an emergency SMS body specifically for automated overdue journey escalation.
     * Includes destination, original ETA, overdue duration, and fresh location link if available.
     */
    fun buildOverdueSosMessage(
        destination: String,
        expectedArrivalTimestamp: Long,
        overdueMinutes: Long = 5,
        location: LocationData? = null,
        includeLocation: Boolean = true
    ): String {
        val timeFormat = java.text.SimpleDateFormat("h:mm a", java.util.Locale.getDefault())
        val etaStr = timeFormat.format(java.util.Date(expectedArrivalTimestamp))
        val parts = mutableListOf<String>()

        parts.add("EMERGENCY: SafeHer Overdue Alert.")
        parts.add("I have not checked in and am $overdueMinutes+ minutes overdue arriving at $destination (Expected: $etaStr). Please contact me immediately.")

        if (includeLocation) {
            if (location != null && !location.isStale) {
                val lat = String.format(java.util.Locale.US, "%.6f", location.latitude)
                val lng = String.format(java.util.Locale.US, "%.6f", location.longitude)
                val acc = location.accuracyMeters.toInt()
                val accuracyInfo = if (acc > 0) " (±${acc}m)" else ""
                parts.add("Last Known Location: https://maps.google.com/?q=$lat,$lng$accuracyInfo")
            } else {
                parts.add(LOCATION_UNAVAILABLE_FALLBACK)
            }
        }

        parts.add("[Automated SafeHer Overdue Escalation SMS]")
        return parts.joinToString("\n\n")
    }

    /**
     * Cleans, validates, and deduplicates phone numbers.
     * Preserves '+' for international dialing while removing spaces and dashes.
     */
    fun cleanAndDeduplicateRecipients(phoneNumbers: List<String>): List<String> {
        val seen = mutableSetOf<String>()
        val result = mutableListOf<String>()

        for (raw in phoneNumbers) {
            val cleaned = sanitizePhoneNumber(raw)
            if (cleaned.isNotEmpty()) {
                // Deduplicate based on digits (and leading +)
                val normalizedKey = cleaned.lowercase()
                if (seen.add(normalizedKey)) {
                    result.add(cleaned)
                }
            }
        }
        return result
    }

    /**
     * Sanitizes a raw phone number, preserving leading '+' and numeric digits.
     */
    fun sanitizePhoneNumber(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return ""

        val hasLeadingPlus = trimmed.startsWith("+")
        val digitsOnly = trimmed.filter { it.isDigit() }
        if (digitsOnly.isEmpty()) return ""

        return if (hasLeadingPlus) "+$digitsOnly" else digitsOnly
    }

    /**
     * Filters a list of guardians to those with valid, non-blank phone numbers.
     */
    fun getEligibleGuardians(guardians: List<Guardian>): List<Guardian> {
        return guardians.filter { sanitizePhoneNumber(it.phoneNumber).isNotEmpty() }
    }

    /**
     * Formats the smsto: URI scheme string.
     * Semicolon is the platform standard for separating multiple SMS recipients on Android.
     */
    fun formatSmstoUriString(recipients: List<String>): String {
        val cleanRecipients = cleanAndDeduplicateRecipients(recipients)
        val ssp = cleanRecipients.joinToString(";")
        return "smsto:$ssp"
    }

    /**
     * Formats the smsto: URI safely using Uri.fromParts to avoid unescaped URI errors.
     * Semicolon is the platform standard for separating multiple SMS recipients on Android.
     */
    fun formatSmstoUri(recipients: List<String>): Uri {
        val cleanRecipients = cleanAndDeduplicateRecipients(recipients)
        val ssp = cleanRecipients.joinToString(";")
        return Uri.fromParts("smsto", ssp, null)
    }

    /**
     * Creates an Android ACTION_SENDTO intent for opening the system SMS composer.
     * Does NOT send the message automatically.
     */
    fun createSmsIntent(recipients: List<String>, messageBody: String): Intent {
        val uri = formatSmstoUri(recipients)
        return Intent(Intent.ACTION_SENDTO, uri).apply {
            putExtra("sms_body", messageBody)
            putExtra(Intent.EXTRA_TEXT, messageBody)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }
}
