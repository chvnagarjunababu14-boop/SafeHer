package com.safeher.app.data.location

/**
 * Abstraction for on-device location retrieval to ensure clean separation
 * from Compose UI and high unit-testability.
 */
interface LocationRepository {
    /**
     * Retrieves the current location fix on demand.
     *
     * @param isPrecise Whether precise location (FINE) is granted vs approximate (COARSE).
     * @return [LocationResult] describing the outcome.
     */
    suspend fun getCurrentLocation(isPrecise: Boolean): LocationResult

    /**
     * Checks whether location services (GPS or Network) are enabled on the device.
     */
    fun isLocationServiceEnabled(): Boolean
}
