package com.safeher.app.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val SafeHerColorScheme = lightColorScheme(
    primary = SafeHerPurplePrimary,
    onPrimary = SafeHerSurfaceWhite,
    primaryContainer = SafeHerPurpleContainer,
    onPrimaryContainer = SafeHerOnPurpleContainer,
    secondary = SafeHerPinkAccent,
    onSecondary = SafeHerSurfaceWhite,
    secondaryContainer = SafeHerPinkContainer,
    onSecondaryContainer = SafeHerOnPinkContainer,
    tertiary = SafeHerPurpleLight,
    background = SafeHerLavenderBackground,
    onBackground = SafeHerTextPrimary,
    surface = SafeHerSurfaceWhite,
    onSurface = SafeHerTextPrimary,
    surfaceVariant = SafeHerSurfaceVariant,
    onSurfaceVariant = SafeHerTextSecondary,
    outline = SafeHerBorderLavender,
    error = SafeHerStatusEmergency,
    onError = SafeHerSurfaceWhite
)

@Composable
fun SafeHerTheme(
    darkTheme: Boolean = false, // Consistently enforce SafeHer Light Lavender design across all devices
    content: @Composable () -> Unit
) {
    val colorScheme = SafeHerColorScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                window.statusBarColor = SafeHerLavenderBackground.toArgb()
                window.navigationBarColor = SafeHerSurfaceWhite.toArgb()
                val insetsController = WindowCompat.getInsetsController(window, view)
                // Dark icons on light status and navigation bars for accessible contrast
                insetsController.isAppearanceLightStatusBars = true
                insetsController.isAppearanceLightNavigationBars = true
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = SafeHerTypography,
        shapes = SafeHerShapes,
        content = content
    )
}
