package com.safeher.app.data.places

/**
 * Contract for querying real-time place suggestions and resolving coordinates.
 * Supports swappable providers and test fakes without making network calls during testing.
 */
interface PlacesAutocompleteProvider {
    /** True if an API key / credentials are appropriately configured. */
    val isConfigured: Boolean

    /**
     * Queries matching autocomplete predictions for the given text.
     *
     * @param query Search string entered by user (at least 3 characters).
     * @param biasLatitude Optional latitude to bias search results towards nearby places.
     * @param biasLongitude Optional longitude to bias search results towards nearby places.
     */
    suspend fun getAutocompleteSuggestions(
        query: String,
        biasLatitude: Double? = null,
        biasLongitude: Double? = null
    ): AutocompleteResult

    /**
     * Resolves canonical place details and coordinates for a selected place.
     */
    suspend fun fetchPlaceDetails(placeId: String): PlaceDetailsResult

    /**
     * Resets the autocomplete billing session token.
     */
    fun resetSession()
}
