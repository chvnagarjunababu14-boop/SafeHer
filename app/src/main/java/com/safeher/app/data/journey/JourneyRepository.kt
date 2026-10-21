package com.safeher.app.data.journey

import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for managing local-first safety journeys.
 */
interface JourneyRepository {
    /** Reactive stream of all journeys sorted by start timestamp descending. */
    fun getJourneysFlow(): Flow<List<Journey>>

    /** Reactive stream of the single currently active or overdue journey, if any. */
    fun getActiveJourneyFlow(): Flow<Journey?>

    /** Synchronous retrieval of all journeys on IO dispatcher. */
    suspend fun getJourneys(): List<Journey>

    /** Synchronous retrieval of current active or overdue journey, if any. */
    suspend fun getActiveJourney(): Journey?

    /** Retrieves a single journey by ID. */
    suspend fun getJourneyById(id: Long): Journey?

    /**
     * Starts a new safety journey.
     * Enforces single active journey limit and input validation.
     */
    suspend fun startJourney(
        title: String?,
        startLocation: String,
        destination: String,
        expectedArrivalTimestamp: Long,
        startLatitude: Double? = null,
        startLongitude: Double? = null,
        destinationLatitude: Double? = null,
        destinationLongitude: Double? = null
    ): JourneyOperationResult

    /**
     * Records a local check-in confirming the user is safe.
     * Updates check-in count and timestamp.
     */
    suspend fun checkIn(id: Long): JourneyOperationResult

    /**
     * Extends the expected arrival time for an active or overdue journey.
     * If the journey was overdue, returns it to ACTIVE status.
     */
    suspend fun extendJourney(id: Long, newExpectedArrivalTimestamp: Long): JourneyOperationResult

    /**
     * Completes an active or overdue journey safely.
     * Moves the journey to the terminal COMPLETED state.
     */
    suspend fun completeJourney(id: Long): JourneyOperationResult

    /**
     * Cancels an active or overdue journey.
     * Moves the journey to the terminal CANCELLED state.
     */
    suspend fun cancelJourney(id: Long): JourneyOperationResult

    /**
     * Deletes a journey record from local storage.
     */
    suspend fun deleteJourney(id: Long): Boolean

    /**
     * Retrieves the persistent local event timeline for a specific journey.
     */
    suspend fun getEventsForJourney(journeyId: Long): List<JourneyEvent>

    /**
     * Reactive stream of persistent events for a specific journey.
     */
    fun getEventsFlowForJourney(journeyId: Long): Flow<List<JourneyEvent>>

    /**
     * Reconciles journey states with the current clock, transitioning active to overdue
     * when ETA has passed, and recording exactly one overdue event per observed transition.
     */
    suspend fun refreshJourneyStatus(): Journey?
}
