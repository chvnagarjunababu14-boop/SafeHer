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
    val status: JourneyStatus = JourneyStatus.ACTIVE,
    val lastCheckInTimestamp: Long? = null,
    val checkInCount: Int = 0,
    val checkInHistory: List<Long> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val isMonitoringActive: Boolean = false,
    val lastLatitude: Double? = null,
    val lastLongitude: Double? = null,
    val lastLocationTimestamp: Long? = null,
    val overdueSafetyCheckTimestamp: Long? = null,
    val safetyCheckAcknowledged: Boolean = false,
    val overdueEscalationDeadline: Long? = null,
    val overdueEscalationAttempted: Boolean = false
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

    /** True if overdue and awaiting user safety check confirmation. */
    val isSafetyCheckPending: Boolean
        get() = isOverdue() && !safetyCheckAcknowledged && !isTerminal

    /** Milliseconds remaining until automatic overdue SOS escalation dispatch. */
    fun escalationRemainingMillis(nowMillis: Long = System.currentTimeMillis()): Long {
        val deadline = overdueEscalationDeadline ?: return 0L
        if (safetyCheckAcknowledged || isTerminal) return 0L
        return (deadline - nowMillis).coerceAtLeast(0L)
    }

    /** True if last tracked location was updated within the last 3 minutes. */
    val isLocationFresh: Boolean
        get() {
            val ts = lastLocationTimestamp ?: return false
            return ts > 0L && (System.currentTimeMillis() - ts) < 3 * 60 * 1000L
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
