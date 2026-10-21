package com.safeher.app.data.sos

import android.content.Intent
import android.net.Uri
import com.safeher.app.data.guardian.Guardian
import com.safeher.app.data.location.LocationData

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
