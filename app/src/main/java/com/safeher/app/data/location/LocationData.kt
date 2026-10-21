package com.safeher.app.data.location

/**
 * Enumeration representing the recency/freshness of a location fix.
 */
enum class LocationFreshness {
    /** Retrieved directly from GPS/fused sensors within the last 60 seconds */
    FRESH,
    /** Retrieved from sensor cache within the last 15 minutes */
    CACHED,
    /** Older than 15 minutes; must not be represented as a live position */
    STALE
}

/**
 * Clean data model representing an on-device location fix.
 *
 * @param latitude Latitude in decimal degrees
 * @param longitude Longitude in decimal degrees
 * @param accuracyMeters Reported 68% confidence radius in meters
 * @param timestampMillis Epoch timestamp when this fix was generated
 * @param isFresh True if location was retrieved within the last 60 seconds
 * @param isApproximate True if only coarse location permission was granted
 * @param provider Location provider name (e.g. "fused", "gps", "network")
 * @param freshness High-level recency classification (FRESH, CACHED, or STALE)
 */
data class LocationData(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float,
    val timestampMillis: Long,
    val isFresh: Boolean,
    val isApproximate: Boolean = false,
    val provider: String? = null,
    val freshness: LocationFreshness = if (isFresh) LocationFreshness.FRESH else LocationFreshness.CACHED
) {
    val isStale: Boolean get() = freshness == LocationFreshness.STALE
}

/**
 * Sealed result abstraction for location retrieval operations.
 */
sealed interface LocationResult {
    data class Success(val location: LocationData) : LocationResult
    data class PermissionDenied(val isPermanentlyDenied: Boolean = false) : LocationResult
    data object LocationDisabled : LocationResult
    data class Error(val message: String) : LocationResult
}
