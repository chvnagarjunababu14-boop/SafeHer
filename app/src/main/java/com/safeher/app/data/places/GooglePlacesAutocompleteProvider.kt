package com.safeher.app.data.places

import android.content.Context
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.AutocompleteSessionToken
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.model.RectangularBounds
import com.google.android.libraries.places.api.net.FetchPlaceRequest
import com.google.android.libraries.places.api.net.FindAutocompletePredictionsRequest
import com.google.android.libraries.places.api.net.PlacesClient
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Concrete implementation of [PlacesAutocompleteProvider] backed by the official
 * Google Places SDK for Android.
 *
 * Cost & Security Practices:
 * - Employs [AutocompleteSessionToken] to bundle per-character keystrokes under a single
 *   Autocomplete (Per-Session) SKU billing event.
 * - Requests only the necessary place fields: [Place.Field.ID, Place.Field.NAME, Place.Field.ADDRESS, Place.Field.LAT_LNG].
 * - Supports cooperative request cancellation when queries are superseded.
 */
class GooglePlacesAutocompleteProvider(
    private val context: Context,
    private val apiKey: String
) : PlacesAutocompleteProvider {

    override val isConfigured: Boolean
        get() = apiKey.isNotBlank() && apiKey != "null"

    private var sessionToken: AutocompleteSessionToken? = null

    private val placesClient: PlacesClient? by lazy {
        if (!isConfigured) return@lazy null
        try {
            if (!Places.isInitialized()) {
                Places.initialize(context.applicationContext, apiKey)
            }
            Places.createClient(context.applicationContext)
        } catch (e: Exception) {
            null
        }
    }

    override fun resetSession() {
        sessionToken = null
    }

    private fun getOrCreateSessionToken(): AutocompleteSessionToken {
        var token = sessionToken
        if (token == null) {
            token = AutocompleteSessionToken.newInstance()
            sessionToken = token
        }
        return token
    }

    override suspend fun getAutocompleteSuggestions(
        query: String,
        biasLatitude: Double?,
        biasLongitude: Double?
    ): AutocompleteResult {
        if (!isConfigured) {
            return AutocompleteResult.NotConfigured(
                "Google Places API key is not configured. Add PLACES_API_KEY to local.properties."
            )
        }

        val client = placesClient
            ?: return AutocompleteResult.Error("Google Places client initialization failed.")

        val trimmed = query.trim()
        if (trimmed.length < 3) {
            return AutocompleteResult.Empty
        }

        return suspendCancellableCoroutine { continuation ->
            val cancellationTokenSource = CancellationTokenSource()

            val token = getOrCreateSessionToken()
            val requestBuilder = FindAutocompletePredictionsRequest.builder()
                .setQuery(trimmed)
                .setSessionToken(token)
                .setCancellationToken(cancellationTokenSource.token)

            // Bias search towards user's current foreground location if available (~50km bounds)
            if (biasLatitude != null && biasLongitude != null) {
                val delta = 0.45
                requestBuilder.setLocationBias(
                    RectangularBounds.newInstance(
                        LatLng(biasLatitude - delta, biasLongitude - delta),
                        LatLng(biasLatitude + delta, biasLongitude + delta)
                    )
                )
            }

            val task = client.findAutocompletePredictions(requestBuilder.build())

            task.addOnSuccessListener { response ->
                val predictions = response.autocompletePredictions
                if (predictions.isEmpty()) {
                    continuation.resume(AutocompleteResult.Empty)
                } else {
                    val list = predictions.map { p ->
                        PlaceSuggestion(
                            placeId = p.placeId,
                            primaryText = p.getPrimaryText(null).toString(),
                            secondaryText = p.getSecondaryText(null).toString(),
                            fullText = p.getFullText(null).toString()
                        )
                    }
                    continuation.resume(AutocompleteResult.Success(list))
                }
            }

            task.addOnFailureListener { exception ->
                continuation.resume(
                    AutocompleteResult.Error(
                        exception.localizedMessage ?: "Failed to retrieve place predictions."
                    )
                )
            }

            continuation.invokeOnCancellation {
                cancellationTokenSource.cancel()
            }
        }
    }

    override suspend fun fetchPlaceDetails(placeId: String): PlaceDetailsResult {
        if (!isConfigured) {
            return PlaceDetailsResult.Error("Google Places API key is not configured.")
        }

        val client = placesClient
            ?: return PlaceDetailsResult.Error("Google Places client is not initialized.")

        val placeFields = listOf(
            Place.Field.ID,
            Place.Field.NAME,
            Place.Field.ADDRESS,
            Place.Field.LAT_LNG
        )

        return suspendCancellableCoroutine { continuation ->
            val cancellationTokenSource = CancellationTokenSource()

            val request = FetchPlaceRequest.builder(placeId, placeFields)
                .setSessionToken(sessionToken)
                .setCancellationToken(cancellationTokenSource.token)
                .build()

            val task = client.fetchPlace(request)

            task.addOnSuccessListener { response ->
                // Complete session token lifecycle on place selection
                resetSession()

                val place = response.place
                val latLng = place.latLng
                if (latLng != null) {
                    val resolved = ResolvedPlace(
                        placeId = place.id ?: placeId,
                        name = place.name ?: place.address ?: "Selected Destination",
                        address = place.address ?: "",
                        latitude = latLng.latitude,
                        longitude = latLng.longitude
                    )
                    continuation.resume(PlaceDetailsResult.Success(resolved))
                } else {
                    continuation.resume(PlaceDetailsResult.Error("Place does not contain coordinates."))
                }
            }

            task.addOnFailureListener { exception ->
                continuation.resume(
                    PlaceDetailsResult.Error(
                        exception.localizedMessage ?: "Failed to resolve place details."
                    )
                )
            }

            continuation.invokeOnCancellation {
                cancellationTokenSource.cancel()
            }
        }
    }
}
