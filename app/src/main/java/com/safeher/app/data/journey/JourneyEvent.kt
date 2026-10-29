package com.safeher.app.data.journey

import com.safeher.app.data.local.db.JourneyEventEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Meaningful lifecycle and safety events tracked for a local safety journey.
 */
enum class JourneyEventType {
    STARTED,
    CHECK_IN,
    ETA_EXTENDED,
    OVERDUE_OBSERVED,
    OVERDUE_SAFETY_CHECK_SENT,
    SAFETY_ACKNOWLEDGED,
    OVERDUE_ESCALATION_SOS,
    COMPLETED,
    CANCELLED
}

/**
 * Domain model representing a persistent journey event in the timeline.
 *
 * Privacy Guarantees:
 * - Persisted exclusively on device in local SQLite.
 * - Zero network uploads, no cloud logging.
 * - Never contains raw coordinates, destinations, or personally identifying details in metadata.
 */
data class JourneyEvent(
    val id: Long,
    val journeyId: Long,
    val eventType: JourneyEventType,
    val timestamp: Long,
    val metadata: String? = null
) {
    /** Human-readable title for the event in the UI timeline. */
    val displayTitle: String
        get() = when (eventType) {
            JourneyEventType.STARTED -> "Journey Started"
            JourneyEventType.CHECK_IN -> "Local Check-in Recorded"
            JourneyEventType.ETA_EXTENDED -> "Expected Arrival Extended"
            JourneyEventType.OVERDUE_OBSERVED -> "Marked Overdue"
            JourneyEventType.OVERDUE_SAFETY_CHECK_SENT -> "Safety Check Sent"
            JourneyEventType.SAFETY_ACKNOWLEDGED -> "User Confirmed Safe"
            JourneyEventType.OVERDUE_ESCALATION_SOS -> "Overdue SOS Triggered"
            JourneyEventType.COMPLETED -> "Arrived Safely"
            JourneyEventType.CANCELLED -> "Journey Cancelled"
        }

    /** Contextual description / guidance for the event. */
    val displayDescription: String
        get() = when (eventType) {
            JourneyEventType.STARTED -> "Journey tracking started on device with check-in protection."
            JourneyEventType.CHECK_IN -> metadata ?: "Safety confirmed locally on device. No guardian alerts transmitted."
            JourneyEventType.ETA_EXTENDED -> metadata ?: "Expected arrival time was extended by the user."
            JourneyEventType.OVERDUE_OBSERVED -> "Observed by SafeHer app when expected arrival time elapsed. (Local observation; not verified external data)"
            JourneyEventType.OVERDUE_SAFETY_CHECK_SENT -> "Prompted user with a 5-minute safety confirmation window."
            JourneyEventType.SAFETY_ACKNOWLEDGED -> "User confirmed they are safe. Automatic SOS escalation cancelled."
            JourneyEventType.OVERDUE_ESCALATION_SOS -> metadata ?: "Emergency SOS alerts sent to guardians after safety check timed out or help requested."
            JourneyEventType.COMPLETED -> "Safely arrived and ended the journey."
            JourneyEventType.CANCELLED -> "Journey cancelled by user."
        }

    /** Formatted local time string for display. */
    fun formattedTime(locale: Locale = Locale.getDefault()): String {
        return SimpleDateFormat("h:mm a", locale).format(Date(timestamp))
    }

    /** Formatted date and time string for display. */
    fun formattedDateTime(locale: Locale = Locale.getDefault()): String {
        return SimpleDateFormat("MMM d, h:mm a", locale).format(Date(timestamp))
    }
}

fun JourneyEventEntity.toDomain(): JourneyEvent {
    val type = try {
        JourneyEventType.valueOf(eventType)
    } catch (e: Exception) {
        JourneyEventType.CHECK_IN
    }
    return JourneyEvent(
        id = id,
        journeyId = journeyId,
        eventType = type,
        timestamp = timestamp,
        metadata = metadata
    )
}

fun JourneyEvent.toEntity(): JourneyEventEntity {
    return JourneyEventEntity(
        id,
        journeyId,
        eventType.name,
        timestamp,
        metadata
    )
}
