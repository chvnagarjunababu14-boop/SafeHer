package com.safeher.app.data.journey

/**
 * Explicit statuses for safety journeys.
 */
enum class JourneyStatus {
    /** Journey is actively in progress and within expected arrival time. */
    ACTIVE,
    /** Expected arrival time has passed without completion; requires user check-in or extension. */
    OVERDUE,
    /** Successfully arrived and completed safely by the user. Terminal state. */
    COMPLETED,
    /** Manually cancelled by the user. Terminal state. */
    CANCELLED
}

/**
 * Domain model representing a local-only safety journey.
 *
 * Privacy Guarantees:
 * - Persisted exclusively in the app's private SQLite database.
 * - Zero network uploads, no cloud synchronization.
 * - Does not transmit alerts or SMS to guardians or emergency services in this phase.
 */
data class Journey(
    val id: Long,
    val title: String? = null,
    val startLocation: String,
    val destination: String,
    val startLatitude: Double? = null,
    val startLongitude: Double? = null,
    val destinationLatitude: Double? = null,
    val destinationLongitude: Double? = null,
    val startTimestamp: Long,
    val expectedArrivalTimestamp: Long,
    val completedTimestamp: Long? = null,
    val status: JourneyStatus,
    val lastCheckInTimestamp: Long? = null,
    val checkInCount: Int = 0,
    val checkInHistory: List<Long> = emptyList(),
    val createdAt: Long,
    val updatedAt: Long
) {
    /** True if the journey has reached a permanent terminal status (COMPLETED or CANCELLED). */
    val isTerminal: Boolean
        get() = status == JourneyStatus.COMPLETED || status == JourneyStatus.CANCELLED

    /** True if the journey is either active or overdue. */
    val isActiveOrOverdue: Boolean
        get() = status == JourneyStatus.ACTIVE || status == JourneyStatus.OVERDUE

    /**
     * Derives the effective status from the persisted timestamps and current device clock.
     * If the journey was marked ACTIVE but expected arrival has elapsed, it is dynamically OVERDUE.
     */
    fun getEffectiveStatus(nowMillis: Long = System.currentTimeMillis()): JourneyStatus {
        return when {
            status == JourneyStatus.COMPLETED -> JourneyStatus.COMPLETED
            status == JourneyStatus.CANCELLED -> JourneyStatus.CANCELLED
            nowMillis >= expectedArrivalTimestamp -> JourneyStatus.OVERDUE
            else -> JourneyStatus.ACTIVE
        }
    }

    /** Milliseconds remaining until expected arrival, capped at 0. */
    fun remainingMillis(nowMillis: Long = System.currentTimeMillis()): Long {
        return (expectedArrivalTimestamp - nowMillis).coerceAtLeast(0L)
    }

    /** Milliseconds past expected arrival if overdue, else 0. */
    fun overdueMillis(nowMillis: Long = System.currentTimeMillis()): Long {
        return if (nowMillis > expectedArrivalTimestamp) {
            nowMillis - expectedArrivalTimestamp
        } else {
            0L
        }
    }

    /** True if the journey is currently overdue. */
    fun isOverdue(nowMillis: Long = System.currentTimeMillis()): Boolean {
        return getEffectiveStatus(nowMillis) == JourneyStatus.OVERDUE
    }

    /** Display title falling back to destination description if title is not provided. */
    val displayTitle: String
        get() = if (!title.isNullOrBlank()) title.trim() else destination.trim()
}


/**
 * Result abstraction for journey lifecycle and repository operations.
 */
sealed interface JourneyOperationResult {
    val message: String?

    data class Success(val journeyId: Long, override val message: String? = null) : JourneyOperationResult
    data class ValidationError(val field: String, override val message: String) : JourneyOperationResult
    data class ActiveJourneyExists(val activeJourneyId: Long, override val message: String) : JourneyOperationResult
    data class TerminalStateError(override val message: String) : JourneyOperationResult
    data class Error(override val message: String) : JourneyOperationResult
}
