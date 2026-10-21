package com.safeher.app.data.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * Concrete implementation of [LocationRepository] backed by Google Play Services
 * FusedLocationProviderClient.
 *
 * Privacy Guarantees:
 * - Operates strictly on-demand in the foreground.
 * - Coordinates are never transmitted over network, persisted to unencrypted files,
 *   or logged to release logs.
 */
class FusedLocationRepository(
    private val context: Context,
    private val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)
) : LocationRepository {

    override fun isLocationServiceEnabled(): Boolean {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return false
        return LocationManagerCompat.isLocationEnabled(locationManager)
    }

    @SuppressLint("MissingPermission")
    override suspend fun getCurrentLocation(isPrecise: Boolean): LocationResult {
        // 1. Verify system location service is enabled
        if (!isLocationServiceEnabled()) {
            return LocationResult.LocationDisabled
        }

        // 2. Verify runtime permissions
        val hasFine = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val hasCoarse = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasFine && !hasCoarse) {
            return LocationResult.PermissionDenied(isPermanentlyDenied = false)
        }

        val priority = if (isPrecise && hasFine) {
            Priority.PRIORITY_HIGH_ACCURACY
        } else {
            Priority.PRIORITY_BALANCED_POWER_ACCURACY
        }

        return try {
            // 3. Try to acquire a fresh location with a 10-second timeout
            val freshLocation: Location? = withTimeoutOrNull(10_000L) {
                requestFreshLocation(priority)
            }

            if (freshLocation != null) {
                val ageMillis = System.currentTimeMillis() - freshLocation.time
                val freshness = when {
                    ageMillis < 60_000L -> LocationFreshness.FRESH
                    ageMillis < 15 * 60_000L -> LocationFreshness.CACHED
                    else -> LocationFreshness.STALE
                }
                LocationResult.Success(
                    LocationData(
                        latitude = freshLocation.latitude,
                        longitude = freshLocation.longitude,
                        accuracyMeters = freshLocation.accuracy,
                        timestampMillis = freshLocation.time,
                        isFresh = freshness == LocationFreshness.FRESH,
                        isApproximate = !hasFine,
                        provider = freshLocation.provider ?: "fused",
                        freshness = freshness
                    )
                )
            } else {
                // 4. Fallback to cached last known location if fresh fix timed out
                val cachedLocation: Location? = requestLastLocation()
                if (cachedLocation != null) {
                    val ageMillis = System.currentTimeMillis() - cachedLocation.time
                    // Guard against presenting dangerously outdated fixes (e.g. > 2 hours old)
                    if (ageMillis > 2 * 60 * 60 * 1000L) {
                        LocationResult.Error(
                            "Fresh GPS fix timed out and cached location is too outdated to display safely. Please move to an open area and tap refresh."
                        )
                    } else {
                        val freshness = when {
                            ageMillis < 60_000L -> LocationFreshness.FRESH
                            ageMillis < 15 * 60_000L -> LocationFreshness.CACHED
                            else -> LocationFreshness.STALE
                        }
                        LocationResult.Success(
                            LocationData(
                                latitude = cachedLocation.latitude,
                                longitude = cachedLocation.longitude,
                                accuracyMeters = cachedLocation.accuracy,
                                timestampMillis = cachedLocation.time,
                                isFresh = freshness == LocationFreshness.FRESH,
                                isApproximate = !hasFine,
                                provider = cachedLocation.provider ?: "cached",
                                freshness = freshness
                            )
                        )
                    }
                } else {
                    LocationResult.Error(
                        "Unable to acquire location fix. Please ensure location services are enabled and you have a clear view of the sky."
                    )
                }
            }
        } catch (e: SecurityException) {
            LocationResult.PermissionDenied(isPermanentlyDenied = false)
        } catch (e: Exception) {
            LocationResult.Error(e.localizedMessage ?: "Unexpected location error occurred")
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun requestFreshLocation(priority: Int): Location? =
        suspendCancellableCoroutine { continuation ->
            val cancellationTokenSource = CancellationTokenSource()

            continuation.invokeOnCancellation {
                cancellationTokenSource.cancel()
            }

            fusedLocationClient.getCurrentLocation(priority, cancellationTokenSource.token)
                .addOnSuccessListener { location: Location? ->
                    if (continuation.isActive) {
                        continuation.resume(location)
                    }
                }
                .addOnFailureListener {
                    if (continuation.isActive) {
                        continuation.resume(null)
                    }
                }
        }

    @SuppressLint("MissingPermission")
    private suspend fun requestLastLocation(): Location? =
        suspendCancellableCoroutine { continuation ->
            fusedLocationClient.lastLocation
                .addOnSuccessListener { location: Location? ->
                    if (continuation.isActive) {
                        continuation.resume(location)
                    }
                }
                .addOnFailureListener {
                    if (continuation.isActive) {
                        continuation.resume(null)
                    }
                }
        }
}
