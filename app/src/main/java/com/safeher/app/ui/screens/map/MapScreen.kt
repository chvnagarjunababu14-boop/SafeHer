package com.safeher.app.ui.screens.map

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.GpsNotFixed
import androidx.compose.material.icons.filled.GpsOff
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import android.location.LocationManager
import androidx.core.location.LocationManagerCompat
import com.safeher.app.R
import com.safeher.app.data.location.FusedLocationRepository
import com.safeher.app.data.location.LocationData
import com.safeher.app.data.location.LocationFreshness
import com.safeher.app.ui.theme.SafeHerBorderLavender
import com.safeher.app.ui.theme.SafeHerPinkAccent
import com.safeher.app.ui.theme.SafeHerPinkContainer
import com.safeher.app.ui.theme.SafeHerPurpleContainer
import com.safeher.app.ui.theme.SafeHerPurpleDark
import com.safeher.app.ui.theme.SafeHerPurplePrimary
import com.safeher.app.ui.theme.SafeHerSosCrimson
import com.safeher.app.ui.theme.SafeHerSurfaceWhite
import com.safeher.app.ui.theme.SafeHerTextMuted
import com.safeher.app.ui.theme.SafeHerTextPrimary
import com.safeher.app.ui.theme.SafeHerTextSecondary
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MapViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        val repo = FusedLocationRepository(context.applicationContext)
        @Suppress("UNCHECKED_CAST")
        return MapViewModel(repo) as T
    }
}

@Composable
fun MapScreen(
    onBackToHomeClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val viewModel: MapViewModel = viewModel(
        factory = remember(context) { MapViewModelFactory(context) }
    )
    val uiState by viewModel.uiState.collectAsState()

    // Permission launcher supporting fine and coarse foreground location
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val hasFine = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
        val hasCoarse = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true

        val isPermanentlyDenied = !hasFine && !hasCoarse &&
            (context as? Activity)?.let { activity ->
                !ActivityCompat.shouldShowRequestPermissionRationale(
                    activity,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) && !ActivityCompat.shouldShowRequestPermissionRationale(
                    activity,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            } ?: false

        viewModel.onPermissionResult(
            hasFine = hasFine,
            hasCoarse = hasCoarse,
            isPermanentlyDenied = isPermanentlyDenied
        )
    }

    // Auto-check on first open: if permission is already granted, load location
    LaunchedEffect(Unit) {
        val hasFine = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (hasFine || hasCoarse) {
            viewModel.fetchLocation(isPrecise = hasFine)
        }
    }

    // Initialize OSMDroid MapView with safe internal cache configuration
    val mapView = remember {
        Configuration.getInstance().load(context, context.getSharedPreferences("osmdroid", Context.MODE_PRIVATE))
        Configuration.getInstance().userAgentValue = context.packageName
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
            controller.setZoom(16.0)
            // Default safe starting point (central view)
            controller.setCenter(GeoPoint(20.5937, 78.9629))
        }
    }

    // Lifecycle observer for MapView to prevent memory leaks and track permission/location toggles
    DisposableEffect(lifecycleOwner, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    mapView.onResume()
                    val hasFine = ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.ACCESS_FINE_LOCATION
                    ) == PackageManager.PERMISSION_GRANTED
                    val hasCoarse = ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    ) == PackageManager.PERMISSION_GRANTED
                    val isLocEnabled = viewModel.isLocationServiceEnabled()

                    if (!hasFine && !hasCoarse) {
                        if (uiState is MapUiState.Success || uiState is MapUiState.Loading) {
                            viewModel.onPermissionRevoked()
                        }
                    } else if (!isLocEnabled) {
                        if (uiState is MapUiState.Success || uiState is MapUiState.Loading) {
                            viewModel.onLocationDisabled()
                        }
                    } else {
                        if (uiState is MapUiState.LocationDisabled) {
                            viewModel.fetchLocation(isPrecise = hasFine)
                        }
                    }
                }
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_DESTROY -> mapView.onDetach()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView.onDetach()
        }
    }

    // Marker for user's position styled with SafeHer branding
    val userMarker = remember(mapView) {
        Marker(mapView).apply {
            title = "My Position"
            icon = ContextCompat.getDrawable(context, R.drawable.ic_map_user_marker)
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
        }
    }

    // Synchronize Map state with Location updates
    LaunchedEffect(uiState) {
        when (val state = uiState) {
            is MapUiState.Success -> {
                val point = GeoPoint(state.location.latitude, state.location.longitude)
                userMarker.position = point
                userMarker.snippet = "Accuracy: ±${state.location.accuracyMeters.toInt()}m"
                if (!mapView.overlays.contains(userMarker)) {
                    mapView.overlays.add(userMarker)
                }
                mapView.controller.animateTo(point)
                mapView.invalidate()
            }
            is MapUiState.Initial -> {
                mapView.overlays.remove(userMarker)
                mapView.invalidate()
            }
            else -> {}
        }
    }

    // Handle recenter requests
    LaunchedEffect((uiState as? MapUiState.Success)?.recenterCounter) {
        val current = uiState as? MapUiState.Success
        if (current != null) {
            val point = GeoPoint(current.location.latitude, current.location.longitude)
            mapView.controller.animateTo(point)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // 1. Interactive OpenStreetMap View
        AndroidView(
            factory = { mapView },
            modifier = Modifier.fillMaxSize()
        )

        // 2. Top Header with Back Navigation & Refresh Action
        MapTopHeader(
            onBackClick = onBackToHomeClick,
            onRefreshClick = {
                val hasFine = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
                val hasCoarse = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED

                if (hasFine || hasCoarse) {
                    viewModel.fetchLocation(isPrecise = hasFine)
                } else {
                    permissionLauncher.launch(
                        arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        )
                    )
                }
            },
            isLoading = uiState is MapUiState.Loading,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
        )

        // 3. Status Overlay / Location Details Card (When Success)
        AnimatedVisibility(
            visible = uiState is MapUiState.Success,
            enter = fadeIn() + slideInVertically(),
            exit = fadeOut() + slideOutVertically(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 64.dp, start = 16.dp, end = 16.dp)
        ) {
            val successState = uiState as? MapUiState.Success
            if (successState != null) {
                LocationDetailsCard(
                    location = successState.location,
                    onStopClick = { viewModel.stopLocation() }
                )
            }
        }

        // 4. Loading Overlay Pill
        if (uiState is MapUiState.Loading) {
            Card(
                colors = CardDefaults.cardColors(containerColor = SafeHerSurfaceWhite),
                shape = RoundedCornerShape(20.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 72.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = SafeHerPurplePrimary,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = stringResource(R.string.map_loading_gps),
                        style = MaterialTheme.typography.bodySmall,
                        color = SafeHerPurpleDark,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        // 5. Interactive Permission & Error Cards (Overlaid when action is required)
        when (val state = uiState) {
            is MapUiState.Initial -> {
                PermissionRationaleCard(
                    onGrantClick = {
                        permissionLauncher.launch(
                            arrayOf(
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION
                            )
                        )
                    },
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(horizontal = 24.dp)
                )
            }
            is MapUiState.PermissionRequired -> {
                PermissionDeniedCard(
                    isPermanentlyDenied = state.isPermanentlyDenied,
                    onRetryClick = {
                        permissionLauncher.launch(
                            arrayOf(
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION
                            )
                        )
                    },
                    onOpenSettingsClick = {
                        val intent = Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.fromParts("package", context.packageName, null)
                        )
                        context.startActivity(intent)
                    },
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(horizontal = 24.dp)
                )
            }
            is MapUiState.LocationDisabled -> {
                LocationDisabledCard(
                    onOpenLocationSettingsClick = {
                        val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                        context.startActivity(intent)
                    },
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(horizontal = 24.dp)
                )
            }
            is MapUiState.Error -> {
                LocationErrorCard(
                    message = state.message,
                    onRetryClick = {
                        val hasFine = ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.ACCESS_FINE_LOCATION
                        ) == PackageManager.PERMISSION_GRANTED
                        viewModel.fetchLocation(isPrecise = hasFine)
                    },
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(horizontal = 24.dp)
                )
            }
            else -> {}
        }

        // 6. Recenter on My Location Floating Action Button
        if (uiState is MapUiState.Success) {
            FloatingActionButton(
                onClick = { viewModel.recenterMap() },
                containerColor = SafeHerPurplePrimary,
                contentColor = Color.White,
                shape = CircleShape,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 20.dp, bottom = 32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.MyLocation,
                    contentDescription = stringResource(R.string.map_recenter_desc)
                )
            }
        }

        // 7. OpenStreetMap Attribution Banner (Legal & Policy Compliance)
        Card(
            colors = CardDefaults.cardColors(containerColor = SafeHerSurfaceWhite.copy(alpha = 0.85f)),
            shape = RoundedCornerShape(topEnd = 8.dp),
            modifier = Modifier
                .align(Alignment.BottomStart)
        ) {
            Text(
                text = stringResource(R.string.map_attribution),
                style = MaterialTheme.typography.labelSmall,
                color = SafeHerTextMuted,
                fontSize = 10.sp,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
    }
}

@Composable
private fun MapTopHeader(
    onBackClick: () -> Unit,
    onRefreshClick: () -> Unit,
    isLoading: Boolean,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = SafeHerSurfaceWhite),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        shape = RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBackClick) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back to Home",
                        tint = SafeHerPurpleDark
                    )
                }

                Spacer(modifier = Modifier.width(4.dp))

                Column {
                    Text(
                        text = stringResource(R.string.map_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = SafeHerPurpleDark
                    )
                    Text(
                        text = stringResource(R.string.map_subtitle),
                        style = MaterialTheme.typography.labelSmall,
                        color = SafeHerTextMuted
                    )
                }
            }

            IconButton(
                onClick = onRefreshClick,
                enabled = !isLoading
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = stringResource(R.string.map_refresh_desc),
                    tint = SafeHerPurplePrimary
                )
            }
        }
    }
}

@Composable
private fun LocationDetailsCard(
    location: LocationData,
    onStopClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = SafeHerSurfaceWhite),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        shape = RoundedCornerShape(16.dp),
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, SafeHerBorderLavender, RoundedCornerShape(16.dp))
    ) {
        Column(
            modifier = Modifier.padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Accuracy & Freshness Badges
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Freshness pill
                    val (badgeColor, badgeText) = when (location.freshness) {
                        LocationFreshness.FRESH -> Color(0xFF10B981) to "● Live GPS"
                        LocationFreshness.CACHED -> Color(0xFFF59E0B) to "● Cached Fix"
                        LocationFreshness.STALE -> Color(0xFFD97706) to "● Stale (>15m)"
                    }
                    Box(
                        modifier = Modifier
                            .background(badgeColor.copy(alpha = 0.12f), RoundedCornerShape(12.dp))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = badgeText,
                            style = MaterialTheme.typography.labelSmall,
                            color = badgeColor,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Accuracy pill
                    val precisionText = if (location.isApproximate) "Approximate" else "Precise (±${location.accuracyMeters.toInt()}m)"
                    Box(
                        modifier = Modifier
                            .background(SafeHerPurpleContainer, RoundedCornerShape(12.dp))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = precisionText,
                            style = MaterialTheme.typography.labelSmall,
                            color = SafeHerPurpleDark,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                // Stop / Clear Action
                OutlinedButton(
                    onClick = onStopClick,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = SafeHerTextMuted),
                    border = ButtonDefaults.outlinedButtonBorder(enabled = true).copy(width = 0.5.dp),
                    contentPadding = ButtonDefaults.ContentPadding
                ) {
                    Text(
                        text = "Clear Fix",
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Coordinates Display
            val latText = String.format(Locale.US, "%.5f", location.latitude)
            val lonText = String.format(Locale.US, "%.5f", location.longitude)
            val timeFormatter = SimpleDateFormat("h:mm:ss a", Locale.getDefault())
            val formattedTime = timeFormatter.format(Date(location.timestampMillis))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "Current Coordinates",
                        style = MaterialTheme.typography.labelSmall,
                        color = SafeHerTextMuted
                    )
                    Text(
                        text = "$latText°, $lonText°",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = SafeHerTextPrimary
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "Fix Timestamp",
                        style = MaterialTheme.typography.labelSmall,
                        color = SafeHerTextMuted
                    )
                    Text(
                        text = formattedTime,
                        style = MaterialTheme.typography.bodySmall,
                        color = SafeHerTextSecondary
                    )
                }
            }

            // Stale Fix Alert Banner
            if (location.isStale) {
                Spacer(modifier = Modifier.height(8.dp))
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF3C7)),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = Color(0xFFD97706),
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Fix recorded over 15 min ago. Tap refresh for live position.",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF92400E)
                        )
                    }
                }
            }

            // Approximate Positioning Note
            if (location.isApproximate) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Approximate positioning • Grant precise permission for exact GPS coordinates",
                    style = MaterialTheme.typography.labelSmall,
                    color = SafeHerTextMuted,
                    fontSize = 10.sp
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Privacy Guarantee Notice
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    tint = SafeHerPurplePrimary,
                    modifier = Modifier.size(12.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.map_privacy_guarantee),
                    style = MaterialTheme.typography.labelSmall,
                    color = SafeHerTextMuted,
                    fontSize = 11.sp
                )
            }
        }
    }
}

@Composable
private fun PermissionRationaleCard(
    onGrantClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = SafeHerSurfaceWhite),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        shape = RoundedCornerShape(20.dp),
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, SafeHerBorderLavender, RoundedCornerShape(20.dp))
    ) {
        Column(
            modifier = Modifier.padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(SafeHerPurpleContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Security,
                    contentDescription = null,
                    tint = SafeHerPurplePrimary,
                    modifier = Modifier.size(28.dp)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = stringResource(R.string.map_permission_rationale_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = SafeHerTextPrimary
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = stringResource(R.string.map_permission_rationale_desc),
                style = MaterialTheme.typography.bodySmall,
                color = SafeHerTextSecondary,
                lineHeight = 18.sp
            )

            Spacer(modifier = Modifier.height(18.dp))

            Button(
                onClick = onGrantClick,
                colors = ButtonDefaults.buttonColors(containerColor = SafeHerPurplePrimary),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.MyLocation,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.map_permission_grant),
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun PermissionDeniedCard(
    isPermanentlyDenied: Boolean,
    onRetryClick: () -> Unit,
    onOpenSettingsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = SafeHerSurfaceWhite),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        shape = RoundedCornerShape(20.dp),
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, SafeHerBorderLavender, RoundedCornerShape(20.dp))
    ) {
        Column(
            modifier = Modifier.padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(SafeHerPinkContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.GpsNotFixed,
                    contentDescription = null,
                    tint = SafeHerPinkAccent,
                    modifier = Modifier.size(28.dp)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = stringResource(R.string.map_permission_denied_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = SafeHerTextPrimary
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = stringResource(R.string.map_permission_denied_desc),
                style = MaterialTheme.typography.bodySmall,
                color = SafeHerTextSecondary,
                lineHeight = 18.sp
            )

            Spacer(modifier = Modifier.height(18.dp))

            if (isPermanentlyDenied) {
                Button(
                    onClick = onOpenSettingsClick,
                    colors = ButtonDefaults.buttonColors(containerColor = SafeHerPurplePrimary),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = stringResource(R.string.map_open_settings),
                        fontWeight = FontWeight.Bold
                    )
                }
            } else {
                Button(
                    onClick = onRetryClick,
                    colors = ButtonDefaults.buttonColors(containerColor = SafeHerPurplePrimary),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Retry Permission",
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun LocationDisabledCard(
    onOpenLocationSettingsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = SafeHerSurfaceWhite),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        shape = RoundedCornerShape(20.dp),
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, SafeHerBorderLavender, RoundedCornerShape(20.dp))
    ) {
        Column(
            modifier = Modifier.padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFFEF3C7)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.GpsOff,
                    contentDescription = null,
                    tint = Color(0xFFD97706),
                    modifier = Modifier.size(28.dp)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = stringResource(R.string.map_location_disabled_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = SafeHerTextPrimary
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = stringResource(R.string.map_location_disabled_desc),
                style = MaterialTheme.typography.bodySmall,
                color = SafeHerTextSecondary,
                lineHeight = 18.sp
            )

            Spacer(modifier = Modifier.height(18.dp))

            Button(
                onClick = onOpenLocationSettingsClick,
                colors = ButtonDefaults.buttonColors(containerColor = SafeHerPurplePrimary),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.GpsFixed,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.map_enable_location_services),
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun LocationErrorCard(
    message: String,
    onRetryClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = SafeHerSurfaceWhite),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        shape = RoundedCornerShape(20.dp),
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, SafeHerBorderLavender, RoundedCornerShape(20.dp))
    ) {
        Column(
            modifier = Modifier.padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(SafeHerPinkContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = SafeHerSosCrimson,
                    modifier = Modifier.size(28.dp)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "Location Error",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = SafeHerTextPrimary
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = SafeHerTextSecondary,
                lineHeight = 18.sp
            )

            Spacer(modifier = Modifier.height(18.dp))

            Button(
                onClick = onRetryClick,
                colors = ButtonDefaults.buttonColors(containerColor = SafeHerPurplePrimary),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Retry",
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
