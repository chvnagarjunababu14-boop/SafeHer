package com.safeher.app.data.places

/**
 * Clean data model representing an individual place autocomplete suggestion.
 */
data class PlaceSuggestion(
    val placeId: String,
    val primaryText: String,
    val secondaryText: String,
    val fullText: String
)

/**
 * Resolved canonical place details retrieved after the user taps a suggestion.
 */
data class ResolvedPlace(
    val placeId: String,
    val name: String,
    val address: String,
    val latitude: Double,
    val longitude: Double,
    val isManualFallback: Boolean = false
)

/**
 * Result abstraction for autocomplete query operations.
 */
sealed interface AutocompleteResult {
    data class Success(val suggestions: List<PlaceSuggestion>) : AutocompleteResult
    data object Empty : AutocompleteResult
    data class Error(val message: String) : AutocompleteResult
    data class NotConfigured(val message: String) : AutocompleteResult
}

/**
 * Result abstraction for place details resolution.
 */
sealed interface PlaceDetailsResult {
    data class Success(val place: ResolvedPlace) : PlaceDetailsResult
    data class Error(val message: String) : PlaceDetailsResult
}
