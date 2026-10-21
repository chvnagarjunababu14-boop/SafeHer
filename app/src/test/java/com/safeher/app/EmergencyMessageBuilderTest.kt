package com.safeher.app

import com.safeher.app.data.guardian.Guardian
import com.safeher.app.data.location.LocationData
import com.safeher.app.data.location.LocationFreshness
import com.safeher.app.data.sos.EmergencyMessageBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EmergencyMessageBuilderTest {

    @Test
    fun buildMessage_withoutLocation_containsDistressHeaderAndFooter() {
        val message = EmergencyMessageBuilder.buildMessage(
            userName = "Alice",
            customNote = null,
            location = null,
            includeLocation = false
        )

        assertTrue(message.contains(EmergencyMessageBuilder.DEFAULT_EMERGENCY_PREFIX))
        assertTrue(message.contains("Sender: Alice"))
        assertTrue(message.contains(EmergencyMessageBuilder.SMS_FOOTER))
        assertFalse(message.contains("maps.google.com"))
        assertFalse(message.contains(EmergencyMessageBuilder.LOCATION_UNAVAILABLE_FALLBACK))
    }

    @Test
    fun buildMessage_withGuestOrBlankUserName_omitsSender() {
        val msgGuest = EmergencyMessageBuilder.buildMessage(userName = "Guest", includeLocation = false)
        assertFalse("Guest user should not produce a Sender line", msgGuest.contains("Sender:"))

        val msgBlank = EmergencyMessageBuilder.buildMessage(userName = "   ", includeLocation = false)
        assertFalse("Blank user should not produce a Sender line", msgBlank.contains("Sender:"))
    }

    @Test
    fun buildMessage_withCustomNote_includesNote() {
        val message = EmergencyMessageBuilder.buildMessage(
            userName = "Priya",
            customNote = "I am at the north metro entrance near the red car.",
            includeLocation = false
        )

        assertTrue(message.contains("I am at the north metro entrance near the red car."))
    }

    @Test
    fun buildMessage_withFreshLocation_includesGoogleMapsLinkAndAccuracy() {
        val location = LocationData(
            latitude = 12.9715987,
            longitude = 77.5945627,
            accuracyMeters = 15f,
            timestampMillis = System.currentTimeMillis(),
            isFresh = true,
            freshness = LocationFreshness.FRESH
        )

        val message = EmergencyMessageBuilder.buildMessage(
            userName = "Priya",
            customNote = null,
            location = location,
            includeLocation = true
        )

        assertTrue("Must contain google maps link", message.contains("https://maps.google.com/?q=12.971599,77.594563"))
        assertTrue("Must contain accuracy", message.contains("±15m"))
    }

    @Test
    fun buildMessage_withStaleLocation_usesHonestFallback() {
        val staleLocation = LocationData(
            latitude = 12.9716,
            longitude = 77.5946,
            accuracyMeters = 50f,
            timestampMillis = System.currentTimeMillis() - 30 * 60 * 1000L,
            isFresh = false,
            freshness = LocationFreshness.STALE
        )

        val message = EmergencyMessageBuilder.buildMessage(
            userName = "Priya",
            location = staleLocation,
            includeLocation = true
        )

        assertFalse("Must never represent stale coordinates as current", message.contains("maps.google.com"))
        assertTrue("Must use honest fallback", message.contains(EmergencyMessageBuilder.LOCATION_UNAVAILABLE_FALLBACK))
    }

    @Test
    fun buildMessage_withLocationDeniedOrNull_usesHonestFallback() {
        val message = EmergencyMessageBuilder.buildMessage(
            userName = "Priya",
            location = null,
            includeLocation = true
        )

        assertFalse(message.contains("maps.google.com"))
        assertTrue(message.contains(EmergencyMessageBuilder.LOCATION_UNAVAILABLE_FALLBACK))
    }

    @Test
    fun cleanAndDeduplicateRecipients_normalizesInternationalNumbers_andRemovesDuplicates() {
        val raw = listOf(
            "+1 (555) 123-4567",
            "+15551234567",       // Duplicate of first
            " +91 98765-43210 ",
            "+919876543210",       // Duplicate of third
            "   ",                 // Blank
            "+44 20 7946 0991"
        )

        val cleaned = EmergencyMessageBuilder.cleanAndDeduplicateRecipients(raw)

        assertEquals(3, cleaned.size)
        assertEquals("+15551234567", cleaned[0])
        assertEquals("+919876543210", cleaned[1])
        assertEquals("+442079460991", cleaned[2])
    }

    @Test
    fun getEligibleGuardians_filtersOutGuardiansWithBlankOrInvalidNumbers() {
        val guardians = listOf(
            Guardian(id = 1, name = "Alice", phoneNumber = "+15551234567"),
            Guardian(id = 2, name = "Bob", phoneNumber = "   "),
            Guardian(id = 3, name = "Charlie", phoneNumber = "+442079460991"),
            Guardian(id = 4, name = "Dan", phoneNumber = "invalid-no-digits")
        )

        val eligible = EmergencyMessageBuilder.getEligibleGuardians(guardians)
        assertEquals(2, eligible.size)
        assertEquals("Alice", eligible[0].name)
        assertEquals("Charlie", eligible[1].name)
    }

    @Test
    fun cleanAndDeduplicateRecipients_normalizesAndRemovesDuplicates() {
        val input = listOf("+1 (555) 123-4567", "+15551234567", "  +91 98765-43210  ", "invalid", "")
        val result = EmergencyMessageBuilder.cleanAndDeduplicateRecipients(input)
        assertEquals(2, result.size)
        assertEquals("+15551234567", result[0])
        assertEquals("+919876543210", result[1])
    }

    @Test
    fun formatSmstoUriString_singleAndMultipleRecipients_formatsProperly() {
        val single = listOf("+15551234567")
        val uriSingle = EmergencyMessageBuilder.formatSmstoUriString(single)
        assertEquals("smsto:+15551234567", uriSingle)

        val multiple = listOf("+15551234567", "+919876543210")
        val uriMultiple = EmergencyMessageBuilder.formatSmstoUriString(multiple)
        assertEquals("smsto:+15551234567;+919876543210", uriMultiple)

        val empty = emptyList<String>()
        val uriEmpty = EmergencyMessageBuilder.formatSmstoUriString(empty)
        assertEquals("smsto:", uriEmpty)
    }
}
