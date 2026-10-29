package com.safeher.app.data.guardian

import kotlinx.coroutines.flow.Flow

/**
 * Repository interface isolating local guardian persistence from UI.
 */
interface GuardianRepository {
    /**
     * Observes the active list of guardians in real-time.
     */
    fun getGuardiansFlow(): Flow<List<Guardian>>

    /**
     * Observes the active guardian count in real-time.
     */
    fun getGuardianCountFlow(): Flow<Int>

    /**
     * One-shot retrieval of all guardians.
     */
    suspend fun getGuardians(): List<Guardian>

    /**
     * One-shot retrieval of guardian count.
     */
    suspend fun getGuardianCount(): Int

    /**
     * Adds a new guardian after validation and duplicate phone number checks.
     */
    suspend fun addGuardian(
        name: String,
        phoneNumber: String,
        relationship: String? = null
    ): GuardianOperationResult

    /**
     * Updates an existing guardian after validation.
     */
    suspend fun updateGuardian(
        id: Long,
        name: String,
        phoneNumber: String,
        relationship: String? = null
    ): GuardianOperationResult

    /**
     * Deletes a guardian by their ID.
     */
    suspend fun deleteGuardian(id: Long): Boolean

    /**
     * Retrieves a single guardian by ID.
     */
    suspend fun getGuardianById(id: Long): Guardian?

    /**
     * Updates the SOS inclusion preference for a guardian.
     */
    suspend fun updateSosEnabled(id: Long, isSosEnabled: Boolean): Boolean

    /**
     * One-shot retrieval of guardians enabled for SOS.
     */
    suspend fun getSosEnabledGuardians(): List<Guardian>

    /**
     * Observes the list of guardians enabled for SOS.
     */
    fun getSosEnabledGuardiansFlow(): Flow<List<Guardian>>

    /**
     * Updates the battery-alert inclusion preference for a guardian (defaults OFF).
     */
    suspend fun updateBatteryAlertEnabled(id: Long, isBatteryAlertEnabled: Boolean): Boolean

    /**
     * One-shot retrieval of guardians opted in for battery alerts.
     */
    suspend fun getBatteryAlertEnabledGuardians(): List<Guardian>
}
