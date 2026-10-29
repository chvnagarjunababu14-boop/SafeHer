package com.safeher.app.ui.screens.map

/**
 * Configuration parameters for the Live Safety Map (OpenStreetMap).
 *
 * Enforces geographic bounds, minimum/maximum zoom levels, and display settings
 * to prevent repeated tile rendering and ensure sharp, responsive map navigation.
 */
object MapConfig {
    /** Minimum zoom level to prevent world-map wallpaper tiling on high-resolution screens. */
    const val MIN_ZOOM_LEVEL: Double = 3.5

    /** Maximum zoom level for OpenStreetMap standard Mapnik tiles. */
    const val MAX_ZOOM_LEVEL: Double = 20.0

    /** Initial default street/neighborhood zoom level. */
    const val INITIAL_ZOOM_LEVEL: Double = 16.0

    /** Zoom level restored when the user taps Recenter on their location. */
    const val RECENTER_ZOOM_LEVEL: Double = 16.0

    /** Default starting center coordinates (central geographic reference). */
    const val DEFAULT_CENTER_LATITUDE: Double = 20.5937
    const val DEFAULT_CENTER_LONGITUDE: Double = 78.9629

    /** Light neutral tile placeholder background color for loading / offline states. */
    const val LOADING_BG_COLOR_HEX: String = "#F1F5F9"

    /** Subtle border line color for loading / offline tile placeholders. */
    const val LOADING_LINE_COLOR_HEX: String = "#CBD5E1"
}
