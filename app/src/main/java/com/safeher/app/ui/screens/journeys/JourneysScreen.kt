package com.safeher.app.ui.screens.journeys

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.GpsNotFixed
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.ui.window.Dialog
import com.safeher.app.data.journey.JourneyEvent
import com.safeher.app.data.journey.JourneyEventType
import com.safeher.app.data.sos.DirectSosState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.safeher.app.BuildConfig
import com.safeher.app.R
import com.safeher.app.data.journey.Journey
import com.safeher.app.data.journey.JourneyRepositoryImpl
import com.safeher.app.data.journey.JourneyStatus
import com.safeher.app.data.local.db.SafeHerDatabase
import com.safeher.app.data.location.FusedLocationRepository
import com.safeher.app.data.places.GooglePlacesAutocompleteProvider
import com.safeher.app.data.places.PlaceSuggestion
import com.safeher.app.ui.theme.SafeHerBorderLavender
import com.safeher.app.ui.theme.SafeHerLavenderBackground
import com.safeher.app.ui.theme.SafeHerSurfaceWhite
import com.safeher.app.ui.theme.SafeHerPinkAccent
import com.safeher.app.ui.theme.SafeHerPinkContainer
import com.safeher.app.ui.theme.SafeHerPurpleContainer
import com.safeher.app.ui.theme.SafeHerPurpleDark
import com.safeher.app.ui.theme.SafeHerPurpleLight
import com.safeher.app.ui.theme.SafeHerPurplePrimary
import com.safeher.app.ui.theme.SafeHerSosCrimson
import com.safeher.app.ui.theme.SafeHerSosTrack
import com.safeher.app.ui.theme.SafeHerTextPrimary
import com.safeher.app.ui.theme.SafeHerTextSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun JourneysScreen(
    onBackToHomeClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val viewModel: JourneysViewModel = viewModel(
        factory = remember(context) {
            object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    val journeyRepo = com.safeher.app.data.RepositoryProvider.getJourneyRepository(context)
                    val locationRepo = com.safeher.app.data.RepositoryProvider.getLocationRepository(context)
                    val sosPrefsRepo = com.safeher.app.data.RepositoryProvider.getSosPreferencesRepository(context)
                    val directSosManager = com.safeher.app.data.RepositoryProvider.getDirectSosManager(context)
                    val placesRepo = GooglePlacesAutocompleteProvider(
                        context.applicationContext,
                        BuildConfig.PLACES_API_KEY
                    )
                    @Suppress("UNCHECKED_CAST")
                    return JourneysViewModel(
                        journeyRepository = journeyRepo,
                        sosPreferencesRepository = sosPrefsRepo,
                        directSosManager = directSosManager,
                        locationRepository = locationRepo,
                        placesProvider = placesRepo
                    ) as T
                }
            }
        }
    )

    val uiState by viewModel.uiState.collectAsState()

    // Service lifecycle triggers
    LaunchedEffect(uiState.pendingServiceStart) {
        uiState.pendingServiceStart?.let { req ->
            com.safeher.app.service.JourneyMonitoringService.start(
                context = context,
                journeyId = req.journeyId,
                destination = req.destination,
                expectedArrivalTimestamp = req.expectedArrivalTimestamp
            )
            viewModel.onServiceStartHandled()
        }
    }

    LaunchedEffect(uiState.pendingServiceStop) {
        uiState.pendingServiceStop?.let { req ->
            com.safeher.app.service.JourneyMonitoringService.stop(context, req.journeyId)
            viewModel.onServiceStopHandled()
        }
    }

    JourneysScreenContent(
        uiState = uiState,
        onBackToHomeClick = onBackToHomeClick,
        onStartJourneyClick = { viewModel.openCreateDialog() },
        onCheckInClick = { id -> viewModel.performCheckIn(id) },
        onSafetyAcknowledgeClick = { id -> viewModel.acknowledgeSafetyCheck(id) },
        onNeedHelpClick = { id -> viewModel.triggerNeedHelpSos(id) },
        onExtendClick = { journey -> viewModel.openExtendDialog(journey) },
        onFinishClick = { journey -> viewModel.promptFinishJourney(journey) },
        onCancelClick = { journey -> viewModel.promptCancelJourney(journey) },
        onSafetySettingsClick = { viewModel.openSafetySettingsDialog() },
        onToggleMonitoring = { id, active -> viewModel.toggleMonitoring(id, active) },
        onCheckInInfoClick = { viewModel.toggleCheckInInfo(true) },
        onHistoryItemClick = { journey -> viewModel.openJourneyDetails(journey) },
        onDismissFeedback = { viewModel.clearFeedback() },
        modifier = modifier
    )

    // Modal dialogs
    if (uiState.createForm.isOpen) {
        CreateJourneyDialog(
            formState = uiState.createForm,
            onTitleChange = { viewModel.updateCreateTitle(it) },
            onStartLocationChange = { viewModel.updateCreateStartLocation(it) },
            onDestinationChange = { viewModel.updateCreateDestination(it) },
            onSelectPlaceSuggestion = { viewModel.selectPlaceSuggestion(it) },
            onSelectManualFallback = { viewModel.selectManualDestinationFallback() },
            onClearDestination = { viewModel.clearDestinationQuery() },
            onQuickEtaMinutes = { viewModel.setQuickEtaMinutes(it) },
            onAcquireLocation = { viewModel.acquireCurrentLocationForStart() },
            onSubmit = { viewModel.submitCreateJourney() },
            onDismiss = { viewModel.dismissCreateDialog() }
        )
    }

    if (uiState.extendForm.isOpen) {
        ExtendJourneyDialog(
            formState = uiState.extendForm,
            onAddMinutes = { viewModel.addMinutesToExtendEta(it) },
            onSubmit = { viewModel.submitExtendJourney() },
            onDismiss = { viewModel.dismissExtendDialog() }
        )
    }

    if (uiState.finishConfirmJourney != null) {
        FinishJourneyConfirmDialog(
            journey = uiState.finishConfirmJourney!!,
            onConfirm = { viewModel.confirmFinishJourney() },
            onDismiss = { viewModel.dismissFinishConfirm() }
        )
    }

    if (uiState.cancelConfirmJourney != null) {
        CancelJourneyConfirmDialog(
            journey = uiState.cancelConfirmJourney!!,
            onConfirm = { viewModel.confirmCancelJourney() },
            onDismiss = { viewModel.dismissCancelConfirm() }
        )
    }

    if (uiState.selectedJourneyForDetails != null) {
        JourneyDetailsDialog(
            journey = uiState.selectedJourneyForDetails!!,
            events = uiState.selectedJourneyEvents,
            onDismiss = { viewModel.dismissJourneyDetails() }
        )
    }

    if (uiState.showCheckInInfo) {
        CheckInTransparencyDialog(
            onDismiss = { viewModel.toggleCheckInInfo(false) }
        )
    }

    if (uiState.showSafetySettingsDialog) {
        JourneySafetySettingsDialog(
            isOverdueEnabled = uiState.sosPreferences.isOverdueEscalationEnabled,
            isConsentGiven = uiState.sosPreferences.isOverdueEscalationConsentGiven,
            onToggleEnabled = { viewModel.setOverdueEscalationConsent(it) },
            onDismiss = { viewModel.dismissSafetySettingsDialog() }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JourneysScreenContent(
    uiState: JourneysUiState,
    onBackToHomeClick: () -> Unit,
    onStartJourneyClick: () -> Unit,
    onCheckInClick: (Long) -> Unit,
    onSafetyAcknowledgeClick: (Long) -> Unit,
    onNeedHelpClick: (Long) -> Unit,
    onExtendClick: (Journey) -> Unit,
    onFinishClick: (Journey) -> Unit,
    onCancelClick: (Journey) -> Unit,
    onSafetySettingsClick: () -> Unit,
    onToggleMonitoring: (Long, Boolean) -> Unit,
    onCheckInInfoClick: () -> Unit,
    onHistoryItemClick: (Journey) -> Unit,
    onDismissFeedback: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = stringResource(R.string.journeys_screen_title),
                            style = MaterialTheme.typography.titleLarge,
                            color = SafeHerTextPrimary,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = stringResource(R.string.journeys_screen_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = SafeHerTextSecondary
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackToHomeClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back to Home",
                            tint = SafeHerPurplePrimary
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onSafetySettingsClick) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = "Journey Safety Settings",
                            tint = SafeHerPurplePrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = SafeHerLavenderBackground
                )
            )
        },
        containerColor = SafeHerLavenderBackground
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            // 1. Privacy badge banner
            PrivacyBanner()

            Spacer(modifier = Modifier.height(12.dp))

            // 2. Feedback / Alert banners
            if (!uiState.feedbackMessage.isNullOrBlank()) {
                FeedbackBanner(
                    message = uiState.feedbackMessage,
                    isError = false,
                    onDismiss = onDismissFeedback
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            if (!uiState.errorMessage.isNullOrBlank()) {
                FeedbackBanner(
                    message = uiState.errorMessage,
                    isError = true,
                    onDismiss = onDismissFeedback
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            // 3. Active / Overdue Journey Card
            if (uiState.activeJourney != null) {
                ActiveJourneyCard(
                    journey = uiState.activeJourney,
                    events = uiState.activeJourneyEvents,
                    isActionInProgress = uiState.isActionInProgress,
                    nowMillis = uiState.currentTimeMillis,
                    directSosState = uiState.directSosState,
                    onCheckInClick = { onCheckInClick(uiState.activeJourney.id) },
                    onSafetyAcknowledgeClick = { onSafetyAcknowledgeClick(uiState.activeJourney.id) },
                    onNeedHelpClick = { onNeedHelpClick(uiState.activeJourney.id) },
                    onExtendClick = { onExtendClick(uiState.activeJourney) },
                    onFinishClick = { onFinishClick(uiState.activeJourney) },
                    onCancelClick = { onCancelClick(uiState.activeJourney) },
                    onToggleMonitoring = { active -> onToggleMonitoring(uiState.activeJourney.id, active) },
                    onCheckInInfoClick = onCheckInInfoClick,
                    onSafetySettingsClick = onSafetySettingsClick
                )
                Spacer(modifier = Modifier.height(20.dp))
            } else {
                // No active journey: show Start Journey CTA Card
                StartJourneyCtaCard(onClick = onStartJourneyClick)
                Spacer(modifier = Modifier.height(20.dp))
            }

            // 4. Journey History Section
            JourneyHistorySection(
                history = uiState.historyJourneys,
                onHistoryItemClick = onHistoryItemClick
            )

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun PrivacyBanner() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(SafeHerPurpleContainer.copy(alpha = 0.6f))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.Info,
            contentDescription = null,
            tint = SafeHerPurplePrimary,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = stringResource(R.string.journeys_privacy_notice),
            style = MaterialTheme.typography.bodySmall,
            color = SafeHerPurpleDark,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun FeedbackBanner(
    message: String,
    isError: Boolean,
    onDismiss: () -> Unit
) {
    val bgColor = if (isError) SafeHerSosTrack else Color(0xFFDCFCE7)
    val textColor = if (isError) SafeHerSosCrimson else Color(0xFF166534)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (isError) Icons.Default.Warning else Icons.Default.CheckCircle,
            contentDescription = null,
            tint = textColor,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = textColor,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f)
        )
        IconButton(
            onClick = onDismiss,
            modifier = Modifier.size(24.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Dismiss",
                tint = textColor,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
private fun StartJourneyCtaCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(SafeHerPurpleContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    tint = SafeHerPurplePrimary,
                    modifier = Modifier.size(28.dp)
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.journeys_start_action),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = SafeHerPurpleDark
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Track your expected arrival time locally with safety check-ins",
                    style = MaterialTheme.typography.bodySmall,
                    color = SafeHerTextSecondary
                )
            }
        }
    }
}

@Composable
fun ActiveJourneyCard(
    journey: Journey,
    events: List<JourneyEvent> = emptyList(),
    isActionInProgress: Boolean = false,
    nowMillis: Long,
    directSosState: DirectSosState? = null,
    onCheckInClick: () -> Unit,
    onSafetyAcknowledgeClick: () -> Unit,
    onNeedHelpClick: () -> Unit,
    onExtendClick: () -> Unit,
    onFinishClick: () -> Unit,
    onCancelClick: () -> Unit,
    onToggleMonitoring: (Boolean) -> Unit,
    onCheckInInfoClick: () -> Unit,
    onSafetySettingsClick: () -> Unit
) {
    val effectiveStatus = journey.getEffectiveStatus(nowMillis)
    val isOverdue = effectiveStatus == JourneyStatus.OVERDUE
    val isSafetyCheckPending = journey.isSafetyCheckPending
    val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
    var showTimeline by remember { mutableStateOf(false) }

    val cardBorderColor = if (journey.overdueEscalationAttempted || isSafetyCheckPending) {
        SafeHerSosCrimson
    } else if (isOverdue) {
        SafeHerSosCrimson
    } else {
        SafeHerPurpleLight
    }
    val cardBg = Color.White

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(width = 1.5.dp, color = cardBorderColor, shape = RoundedCornerShape(20.dp)),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
        ) {
            // Header: Status badge & Remaining/Overdue time
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Status Pill
                val badgeBg: Color
                val badgeColor: Color
                val badgeText: String
                val badgeIcon = when {
                    journey.overdueEscalationAttempted -> {
                        badgeBg = SafeHerSosTrack
                        badgeColor = SafeHerSosCrimson
                        badgeText = "SOS DISPATCHED"
                        Icons.Default.Warning
                    }
                    isSafetyCheckPending -> {
                        badgeBg = SafeHerSosTrack
                        badgeColor = SafeHerSosCrimson
                        badgeText = "SAFETY CHECK PENDING"
                        Icons.Default.Warning
                    }
                    isOverdue && journey.safetyCheckAcknowledged -> {
                        badgeBg = SafeHerPurpleContainer
                        badgeColor = SafeHerPurplePrimary
                        badgeText = "OVERDUE (SAFE)"
                        Icons.Default.CheckCircle
                    }
                    isOverdue -> {
                        badgeBg = SafeHerSosTrack
                        badgeColor = SafeHerSosCrimson
                        badgeText = "OVERDUE"
                        Icons.Default.Warning
                    }
                    else -> {
                        badgeBg = SafeHerPurpleContainer
                        badgeColor = SafeHerPurplePrimary
                        badgeText = "ACTIVE JOURNEY"
                        Icons.Default.Navigation
                    }
                }

                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(badgeBg)
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = badgeIcon,
                        contentDescription = null,
                        tint = badgeColor,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = badgeText,
                        style = MaterialTheme.typography.labelSmall,
                        color = badgeColor,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Countdown Pill
                val remainingMs = journey.remainingMillis(nowMillis)
                val overdueMs = journey.overdueMillis(nowMillis)
                val timeLabel = if (isOverdue) {
                    val mins = (overdueMs / 60000L).coerceAtLeast(1L)
                    "+${mins}m overdue"
                } else {
                    val mins = (remainingMs / 60000L).coerceAtLeast(0L)
                    "${mins}m remaining"
                }

                Text(
                    text = timeLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isOverdue) SafeHerSosCrimson else SafeHerPurplePrimary,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Destination and Title
            Text(
                text = journey.displayTitle,
                style = MaterialTheme.typography.titleLarge,
                color = SafeHerTextPrimary,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Start & Destination path
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.MyLocation,
                    contentDescription = "From",
                    tint = SafeHerTextSecondary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "From: ${journey.startLocation}",
                    style = MaterialTheme.typography.bodySmall,
                    color = SafeHerTextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Flag,
                    contentDescription = "To",
                    tint = SafeHerPurplePrimary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "To: ${journey.destination}",
                    style = MaterialTheme.typography.bodySmall,
                    color = SafeHerTextPrimary,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Background Location Monitoring & Freshness indicator
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (journey.isMonitoringActive) SafeHerPurpleContainer.copy(alpha = 0.5f) else SafeHerLavenderBackground)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (journey.isMonitoringActive) Icons.Default.GpsFixed else Icons.Default.GpsNotFixed,
                        contentDescription = null,
                        tint = if (journey.isMonitoringActive) Color(0xFF16A34A) else SafeHerTextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = if (journey.isMonitoringActive) "Foreground Tracking Active" else "Foreground Tracking Paused",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (journey.isMonitoringActive) SafeHerPurpleDark else SafeHerTextSecondary
                        )
                        val lastLoc = journey.lastLocationTimestamp
                        if (lastLoc != null) {
                            val elapsedMin = ((nowMillis - lastLoc) / 60000L).coerceAtLeast(0L)
                            val freshness = if (journey.isLocationFresh) "Fresh" else "Stale"
                            Text(
                                text = "GPS fix: ${elapsedMin}m ago ($freshness)",
                                style = MaterialTheme.typography.labelSmall,
                                color = SafeHerTextSecondary
                            )
                        } else {
                            Text(
                                text = "Awaiting initial GPS fix",
                                style = MaterialTheme.typography.labelSmall,
                                color = SafeHerTextSecondary
                            )
                        }
                    }
                }
                Switch(
                    checked = journey.isMonitoringActive,
                    onCheckedChange = onToggleMonitoring,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = SafeHerPurplePrimary,
                        checkedTrackColor = SafeHerPurpleLight
                    )
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Timestamps row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(SafeHerLavenderBackground)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "Started",
                        style = MaterialTheme.typography.labelSmall,
                        color = SafeHerTextSecondary
                    )
                    Text(
                        text = timeFormat.format(Date(journey.startTimestamp)),
                        style = MaterialTheme.typography.bodySmall,
                        color = SafeHerTextPrimary,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "Expected by",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isOverdue) SafeHerSosCrimson else SafeHerTextSecondary
                    )
                    Text(
                        text = timeFormat.format(Date(journey.expectedArrivalTimestamp)),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isOverdue) SafeHerSosCrimson else SafeHerPurpleDark,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Safety Check Pending Banner (Overdue window)
            if (isSafetyCheckPending) {
                val remainingEscMs = journey.escalationRemainingMillis(nowMillis)
                val remainingSec = (remainingEscMs / 1000L).coerceAtLeast(0L)
                val remainingMinPart = remainingSec / 60L
                val remainingSecPart = remainingSec % 60L
                val countdownText = String.format(Locale.getDefault(), "%d:%02d", remainingMinPart, remainingSecPart)

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.5.dp, SafeHerSosCrimson, RoundedCornerShape(12.dp)),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = SafeHerSosTrack)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = SafeHerSosCrimson,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Safety Check: Are You Safe?",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = SafeHerSosCrimson
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "You are past your expected arrival time. If you do not respond within $countdownText, an automatic SOS SMS will be sent to your guardians.",
                            style = MaterialTheme.typography.bodySmall,
                            color = SafeHerTextPrimary
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = onSafetyAcknowledgeClick,
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A)),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("I'm Safe", fontWeight = FontWeight.Bold)
                            }
                            Button(
                                onClick = onNeedHelpClick,
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = SafeHerSosCrimson),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.Warning, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Need Help", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            // Automatic Overdue SOS Result Banner
            if (journey.overdueEscalationAttempted) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, SafeHerSosCrimson, RoundedCornerShape(12.dp)),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = SafeHerSosTrack)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = SafeHerSosCrimson,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Automatic Overdue SOS Dispatched",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = SafeHerSosCrimson
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "The 5-minute safety response window elapsed. SafeHer attempted direct emergency SMS dispatch to your enabled guardians.",
                            style = MaterialTheme.typography.bodySmall,
                            color = SafeHerTextPrimary
                        )
                        if (directSosState is DirectSosState.Completed) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Recipient Dispatches: ${directSosState.submittedCount} sent, ${directSosState.failedCount} failed",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = SafeHerTextPrimary
                            )
                        } else if (directSosState is DirectSosState.Dispatching) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Dispatching emergency SMS alerts...",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = SafeHerTextPrimary
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            // Contextual Safety Guidance Notice
            if (isOverdue && !isSafetyCheckPending && !journey.overdueEscalationAttempted) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = SafeHerSosTrack)
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = SafeHerSosCrimson,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Trip has elapsed expected arrival time. Tap \"I'm Safe\" or \"Add Time\" if you need more time. (All tracking is local on device).",
                            style = MaterialTheme.typography.bodySmall,
                            color = SafeHerSosCrimson,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            } else if (journey.remainingMillis(nowMillis) <= 5 * 60 * 1000L && !isOverdue) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = SafeHerPurpleContainer)
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = SafeHerPurplePrimary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Approaching expected arrival time. Check in or add time if you need more time to arrive safely.",
                            style = MaterialTheme.typography.bodySmall,
                            color = SafeHerPurpleDark,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            // Check-in status info
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (journey.lastCheckInTimestamp != null) {
                            "Last check-in: ${timeFormat.format(Date(journey.lastCheckInTimestamp))} (${journey.checkInCount})"
                        } else {
                            "No check-ins recorded yet"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = SafeHerTextSecondary
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    IconButton(
                        onClick = onCheckInInfoClick,
                        modifier = Modifier.size(20.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Check-in info",
                            tint = SafeHerPurpleLight,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                // Event timeline expand/collapse toggle
                TextButton(
                    onClick = { showTimeline = !showTimeline }
                ) {
                    Text(
                        text = if (showTimeline) "Hide Timeline ▲" else "Timeline (${events.size}) ▼",
                        style = MaterialTheme.typography.labelSmall,
                        color = SafeHerPurplePrimary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            AnimatedVisibility(visible = showTimeline) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (events.isEmpty()) {
                        Text(
                            text = "No events recorded yet for this active trip.",
                            style = MaterialTheme.typography.bodySmall,
                            color = SafeHerTextSecondary
                        )
                    } else {
                        events.forEach { event ->
                            TimelineEventItem(event = event)
                        }
                    }
                    Text(
                        text = "* Overdue events are recorded when the app observes elapsed time locally; does not verify external events.",
                        style = MaterialTheme.typography.labelSmall,
                        color = SafeHerTextSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Primary Actions: Check In & Extend
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onCheckInClick,
                    enabled = !isActionInProgress,
                    modifier = Modifier.weight(1.3f),
                    colors = ButtonDefaults.buttonColors(containerColor = SafeHerPurplePrimary),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "I'm Safe",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                }

                OutlinedButton(
                    onClick = onExtendClick,
                    enabled = !isActionInProgress,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Alarm,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = SafeHerPurplePrimary
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Add Time",
                        style = MaterialTheme.typography.labelMedium,
                        color = SafeHerPurplePrimary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Secondary Actions: Arrived Safely & Cancel
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = onFinishClick,
                    enabled = !isActionInProgress
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = Color(0xFF16A34A),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Arrived Safely",
                        color = Color(0xFF16A34A),
                        fontWeight = FontWeight.Bold
                    )
                }

                TextButton(
                    onClick = onCancelClick,
                    enabled = !isActionInProgress
                ) {
                    Text(
                        text = "Cancel Trip",
                        color = SafeHerTextSecondary,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

@Composable
private fun JourneyHistorySection(
    history: List<Journey>,
    onHistoryItemClick: (Journey) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.journeys_history_title),
                style = MaterialTheme.typography.titleMedium,
                color = SafeHerTextPrimary,
                fontWeight = FontWeight.Bold
            )

            if (history.isNotEmpty()) {
                Text(
                    text = "${history.size} trips",
                    style = MaterialTheme.typography.bodySmall,
                    color = SafeHerTextSecondary
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (history.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.8f))
            ) {
                Text(
                    text = "Completed and cancelled journeys will be saved here in local history.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = SafeHerTextSecondary,
                    modifier = Modifier.padding(16.dp)
                )
            }
        } else {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                history.forEach { item ->
                    JourneyHistoryCard(
                        journey = item,
                        onClick = { onHistoryItemClick(item) }
                    )
                }
            }
        }
    }
}

@Composable
private fun JourneyHistoryCard(
    journey: Journey,
    onClick: () -> Unit
) {
    val timeFormat = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())
    val isCompleted = journey.status == JourneyStatus.COMPLETED

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = journey.displayTitle,
                    style = MaterialTheme.typography.bodyLarge,
                    color = SafeHerTextPrimary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                // Status chip
                val statusBg = if (isCompleted) Color(0xFFDCFCE7) else SafeHerLavenderBackground
                val statusColor = if (isCompleted) Color(0xFF166534) else SafeHerTextSecondary

                Text(
                    text = if (isCompleted) "Completed" else "Cancelled",
                    style = MaterialTheme.typography.labelSmall,
                    color = statusColor,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(statusBg)
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Started: ${timeFormat.format(Date(journey.startTimestamp))}",
                style = MaterialTheme.typography.bodySmall,
                color = SafeHerTextSecondary
            )

            if (journey.completedTimestamp != null && journey.completedTimestamp > 0) {
                Text(
                    text = "Finished: ${timeFormat.format(Date(journey.completedTimestamp))}",
                    style = MaterialTheme.typography.bodySmall,
                    color = SafeHerTextSecondary
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${journey.checkInCount} check-in(s)",
                    style = MaterialTheme.typography.labelSmall,
                    color = SafeHerTextSecondary
                )
                Text(
                    text = "View Timeline & Details →",
                    style = MaterialTheme.typography.labelSmall,
                    color = SafeHerPurplePrimary,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun TimelineEventItem(event: JourneyEvent) {
    val (icon, iconTint, bgTint) = when (event.eventType) {
        JourneyEventType.STARTED -> Triple(Icons.AutoMirrored.Filled.DirectionsWalk, SafeHerPurplePrimary, SafeHerPurpleContainer)
        JourneyEventType.CHECK_IN -> Triple(Icons.Default.CheckCircle, Color(0xFF16A34A), Color(0xFFDCFCE7))
        JourneyEventType.ETA_EXTENDED -> Triple(Icons.Default.Alarm, SafeHerPurplePrimary, SafeHerPurpleContainer)
        JourneyEventType.OVERDUE_OBSERVED -> Triple(Icons.Default.Warning, SafeHerSosCrimson, SafeHerSosTrack)
        JourneyEventType.OVERDUE_SAFETY_CHECK_SENT -> Triple(Icons.Default.Warning, SafeHerSosCrimson, SafeHerSosTrack)
        JourneyEventType.SAFETY_ACKNOWLEDGED -> Triple(Icons.Default.CheckCircle, Color(0xFF16A34A), Color(0xFFDCFCE7))
        JourneyEventType.OVERDUE_ESCALATION_SOS -> Triple(Icons.Default.Warning, SafeHerSosCrimson, SafeHerSosTrack)
        JourneyEventType.COMPLETED -> Triple(Icons.Default.CheckCircle, Color(0xFF16A34A), Color(0xFFDCFCE7))
        JourneyEventType.CANCELLED -> Triple(Icons.Default.Cancel, SafeHerTextSecondary, SafeHerLavenderBackground)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(SafeHerLavenderBackground.copy(alpha = 0.5f))
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(bgTint),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(18.dp)
            )
        }

        Spacer(modifier = Modifier.width(10.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = event.displayTitle,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = SafeHerTextPrimary
                )
                Text(
                    text = event.formattedTime(),
                    style = MaterialTheme.typography.labelSmall,
                    color = SafeHerTextSecondary
                )
            }
            Text(
                text = event.displayDescription,
                style = MaterialTheme.typography.bodySmall,
                color = SafeHerTextSecondary
            )
        }
    }
}

@Composable
fun JourneyDetailsDialog(
    journey: Journey,
    events: List<JourneyEvent>,
    onDismiss: () -> Unit
) {
    val isCompleted = journey.status == JourneyStatus.COMPLETED
    val timeFormat = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Trip Details",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = SafeHerTextPrimary
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = SafeHerTextSecondary)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = journey.displayTitle,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = SafeHerPurpleDark
                )

                Text(
                    text = "From: ${journey.startLocation} • To: ${journey.destination}",
                    style = MaterialTheme.typography.bodySmall,
                    color = SafeHerTextSecondary
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Status info chip row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(SafeHerLavenderBackground)
                        .padding(10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("Status", style = MaterialTheme.typography.labelSmall, color = SafeHerTextSecondary)
                        Text(
                            text = journey.status.name,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (isCompleted) Color(0xFF166534) else SafeHerPurplePrimary
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("Check-ins", style = MaterialTheme.typography.labelSmall, color = SafeHerTextSecondary)
                        Text(
                            text = "${journey.checkInCount} recorded",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = SafeHerTextPrimary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Event Timeline (${events.size})",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = SafeHerTextPrimary
                )

                Spacer(modifier = Modifier.height(8.dp))

                if (events.isEmpty()) {
                    Text(
                        text = "No events recorded for this trip.",
                        style = MaterialTheme.typography.bodySmall,
                        color = SafeHerTextSecondary,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 240.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        events.forEach { event ->
                            TimelineEventItem(event = event)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "* All journey history and check-ins are stored privately on your device. Overdue events reflect local app observations when ETA elapsed.",
                    style = MaterialTheme.typography.labelSmall,
                    color = SafeHerTextSecondary
                )

                Spacer(modifier = Modifier.height(14.dp))

                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = SafeHerPurplePrimary),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Close", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// --- Dialogs ---

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateJourneyDialog(
    formState: CreateJourneyFormState,
    onTitleChange: (String) -> Unit,
    onStartLocationChange: (String) -> Unit,
    onDestinationChange: (String) -> Unit,
    onSelectPlaceSuggestion: (PlaceSuggestion) -> Unit,
    onSelectManualFallback: () -> Unit,
    onClearDestination: () -> Unit,
    onQuickEtaMinutes: (Int) -> Unit,
    onAcquireLocation: () -> Unit,
    onSubmit: () -> Unit,
    onDismiss: () -> Unit
) {
    val timeFormat = SimpleDateFormat("h:mm a (MMM d)", Locale.getDefault())

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Start Safe Journey",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = SafeHerTextPrimary
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "Search a live destination with verified coordinates or enter details manually.",
                    style = MaterialTheme.typography.bodySmall,
                    color = SafeHerTextSecondary
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Destination Search Field (Required)
                OutlinedTextField(
                    value = formState.destination,
                    onValueChange = onDestinationChange,
                    label = { Text("Destination *") },
                    placeholder = { Text("Search place, address or landmark") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = SafeHerPurplePrimary
                        )
                    },
                    trailingIcon = {
                        if (formState.isSearchingPlaces) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = SafeHerPurplePrimary
                            )
                        } else if (formState.destination.isNotEmpty()) {
                            IconButton(onClick = onClearDestination) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Clear search",
                                    tint = SafeHerTextSecondary
                                )
                            }
                        }
                    },
                    isError = formState.destinationError != null,
                    supportingText = {
                        if (formState.destinationError != null) {
                            Text(text = formState.destinationError, color = SafeHerSosCrimson)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                // 1. Verified Place Selected Card
                if (formState.selectedPlace != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF0FDF4)),
                        border = BorderStroke(1.dp, Color(0xFFBBF7D0))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = Color(0xFF16A34A),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = formState.selectedPlace.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF14532D)
                                )
                                if (formState.selectedPlace.address.isNotBlank()) {
                                    Text(
                                        text = formState.selectedPlace.address,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color(0xFF15803D),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Text(
                                    text = String.format(Locale.US, "GPS: %.4f, %.4f", formState.selectedPlace.latitude, formState.selectedPlace.longitude),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFF166534)
                                )
                            }
                            TextButton(onClick = onClearDestination) {
                                Text("Change", color = SafeHerPurplePrimary, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }

                // 2. Suggestions Dropdown List
                if (formState.placeSuggestions.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 200.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
                        border = BorderStroke(1.dp, SafeHerBorderLavender)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState())
                        ) {
                            formState.placeSuggestions.forEachIndexed { index, suggestion ->
                                if (index > 0) {
                                    HorizontalDivider(color = SafeHerLavenderBackground, thickness = 1.dp)
                                }
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onSelectPlaceSuggestion(suggestion) }
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.LocationOn,
                                        contentDescription = null,
                                        tint = SafeHerPurplePrimary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = suggestion.primaryText,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = SafeHerTextPrimary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        if (suggestion.secondaryText.isNotBlank()) {
                                            Text(
                                                text = suggestion.secondaryText,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = SafeHerTextSecondary,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // 3. Fallback / Unconfigured Notice / Manual Entry Button
                if (formState.destination.trim().length >= 3 && formState.selectedPlace == null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    if (!formState.isPlacesConfigured) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            colors = CardDefaults.cardColors(containerColor = SafeHerPurpleContainer.copy(alpha = 0.5f))
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Info,
                                        contentDescription = null,
                                        tint = SafeHerPurplePrimary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Google Places API key is not configured.",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = SafeHerPurpleDark,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Add PLACES_API_KEY to local.properties to enable live Google autocomplete. You can proceed using manual entry.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = SafeHerTextSecondary
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                OutlinedButton(
                                    onClick = onSelectManualFallback,
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        text = "Use \"${formState.destination.trim()}\" (Manual Entry)",
                                        style = MaterialTheme.typography.labelSmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    } else if (formState.placeSuggestions.isEmpty() && !formState.isSearchingPlaces) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = formState.placesErrorMessage ?: "Place not in suggestions?",
                                style = MaterialTheme.typography.bodySmall,
                                color = SafeHerTextSecondary,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = onSelectManualFallback) {
                                Text("Use text manually", color = SafeHerPurplePrimary, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }

                if (formState.isManualFallbackSelected) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFFFEF3C7))
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = Color(0xFFB45309),
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Manual destination selected (unverified coordinates)",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF92400E)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Starting Point
                OutlinedTextField(
                    value = formState.startLocation,
                    onValueChange = onStartLocationChange,
                    label = { Text("Starting From") },
                    trailingIcon = {
                        IconButton(onClick = onAcquireLocation) {
                            if (formState.isAcquiringLocation) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = SafeHerPurplePrimary
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.MyLocation,
                                    contentDescription = "Use GPS Fix",
                                    tint = SafeHerPurplePrimary
                                )
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                if (!formState.locationNote.isNullOrBlank()) {
                    Text(
                        text = formState.locationNote,
                        style = MaterialTheme.typography.bodySmall,
                        color = SafeHerTextSecondary,
                        modifier = Modifier.padding(top = 4.dp, start = 4.dp)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Optional Title
                OutlinedTextField(
                    value = formState.title,
                    onValueChange = onTitleChange,
                    label = { Text("Trip Title (Optional)") },
                    placeholder = { Text("e.g. Evening Commute") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Estimated Travel Time & Quick Chips
                Text(
                    text = "Expected Arrival Time",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = SafeHerTextPrimary
                )

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(15, 30, 45, 60).forEach { mins ->
                        FilterChip(
                            selected = false,
                            onClick = { onQuickEtaMinutes(mins) },
                            label = { Text("+$mins m") },
                            colors = FilterChipDefaults.filterChipColors(
                                labelColor = SafeHerPurplePrimary
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(SafeHerPurpleContainer.copy(alpha = 0.5f))
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Schedule,
                            contentDescription = null,
                            tint = SafeHerPurplePrimary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Arrival: ${timeFormat.format(Date(formState.expectedArrivalMillis))}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = SafeHerPurpleDark
                        )
                    }
                }

                if (formState.timeError != null) {
                    Text(
                        text = formState.timeError,
                        style = MaterialTheme.typography.bodySmall,
                        color = SafeHerSosCrimson,
                        modifier = Modifier.padding(top = 4.dp, start = 4.dp)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onSubmit,
                colors = ButtonDefaults.buttonColors(containerColor = SafeHerPurplePrimary),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Start Journey", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = SafeHerTextSecondary)
            }
        },
        shape = RoundedCornerShape(20.dp),
        containerColor = Color.White
    )
}

@Composable
fun ExtendJourneyDialog(
    formState: ExtendJourneyFormState,
    onAddMinutes: (Int) -> Unit,
    onSubmit: () -> Unit,
    onDismiss: () -> Unit
) {
    val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Extend Journey Time",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = SafeHerTextPrimary
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Need more time to arrive? Choose an extension below. If your trip was overdue, this will restore it to active.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = SafeHerTextSecondary
                )

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(10, 15, 30, 45).forEach { mins ->
                        FilterChip(
                            selected = false,
                            onClick = { onAddMinutes(mins) },
                            label = { Text("+$mins m") },
                            colors = FilterChipDefaults.filterChipColors(
                                labelColor = SafeHerPurplePrimary
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(SafeHerPurpleContainer.copy(alpha = 0.5f))
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    Text(
                        text = "New Expected Arrival: ${timeFormat.format(Date(formState.newExpectedArrivalMillis))}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = SafeHerPurpleDark
                    )
                }

                if (formState.timeError != null) {
                    Text(
                        text = formState.timeError,
                        style = MaterialTheme.typography.bodySmall,
                        color = SafeHerSosCrimson,
                        modifier = Modifier.padding(top = 4.dp, start = 4.dp)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onSubmit,
                colors = ButtonDefaults.buttonColors(containerColor = SafeHerPurplePrimary),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Confirm Extension", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = SafeHerTextSecondary)
            }
        },
        shape = RoundedCornerShape(20.dp),
        containerColor = Color.White
    )
}

@Composable
fun FinishJourneyConfirmDialog(
    journey: Journey,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Arrived Safely?",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = SafeHerTextPrimary
            )
        },
        text = {
            Text(
                text = "Completing this journey marks your trip to \"${journey.destination}\" as safely finished and records your completion time locally.",
                style = MaterialTheme.typography.bodyMedium,
                color = SafeHerTextSecondary
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A)),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Yes, I've Arrived", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Keep Active", color = SafeHerTextSecondary)
            }
        },
        shape = RoundedCornerShape(20.dp),
        containerColor = Color.White
    )
}

@Composable
fun CancelJourneyConfirmDialog(
    journey: Journey,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Cancel Safety Journey?",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = SafeHerTextPrimary
            )
        },
        text = {
            Text(
                text = "Are you sure you want to cancel your journey to \"${journey.destination}\"? It will be recorded as cancelled in your local history.",
                style = MaterialTheme.typography.bodyMedium,
                color = SafeHerTextSecondary
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = SafeHerSosCrimson),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Cancel Journey", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Keep Active", color = SafeHerTextSecondary)
            }
        },
        shape = RoundedCornerShape(20.dp),
        containerColor = Color.White
    )
}

@Composable
fun CheckInTransparencyDialog(
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = SafeHerPurplePrimary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Local Check-in Record",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = SafeHerTextPrimary
                )
            }
        },
        text = {
            Column {
                Text(
                    text = "When you tap \"I'm Safe\", SafeHer records an on-device timestamp and updates your trip check-in counter.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = SafeHerTextPrimary
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Important Transparency Notice:\nCheck-ins and safety events are stored strictly on your local device. Tapping \"I'm Safe\" confirms you are safe and cancels any pending overdue escalation. Emergency SMS is only sent if a monitored journey becomes overdue, 5 minutes pass without response, and automatic escalation is enabled in Safety Settings.",
                    style = MaterialTheme.typography.bodySmall,
                    color = SafeHerTextSecondary
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = SafeHerPurplePrimary),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Understood")
            }
        },
        shape = RoundedCornerShape(20.dp),
        containerColor = Color.White
    )
}

@Composable
fun JourneySafetySettingsDialog(
    isOverdueEnabled: Boolean,
    isConsentGiven: Boolean,
    onToggleEnabled: (Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Security,
                    contentDescription = null,
                    tint = SafeHerPurplePrimary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Journey Safety Settings",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = SafeHerTextPrimary
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "How SafeHer Protects Your Journey",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = SafeHerPurpleDark
                )
                Spacer(modifier = Modifier.height(8.dp))

                SafetyExplainerItem(
                    title = "1. Background Location Tracking",
                    description = "When a journey is active, SafeHer monitors your GPS coordinates via a foreground service with an ongoing notification. No locations are uploaded to remote servers."
                )

                SafetyExplainerItem(
                    title = "2. Overdue Safety Check",
                    description = "If you do not complete your journey by the expected arrival time, SafeHer issues a high-priority safety notification asking if you are safe."
                )

                SafetyExplainerItem(
                    title = "3. 5-Minute SOS Escalation",
                    description = "If you do not respond to the safety check within 5 minutes, SafeHer automatically attempts to send a direct SOS SMS to your selected guardians."
                )

                SafetyExplainerItem(
                    title = "4. Carrier Charges",
                    description = "Emergency SMS messages are sent directly from your device using your cellular plan. Standard carrier messaging charges apply."
                )

                SafetyExplainerItem(
                    title = "5. Device & Android Limitations",
                    description = "Android battery optimization, Doze mode, or task killing may delay notifications or background dispatch. Delivery cannot be guaranteed under extreme OS restrictions."
                )

                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider(color = SafeHerBorderLavender)
                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Automatic Overdue SOS",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = SafeHerTextPrimary
                        )
                        Text(
                            text = "Send direct SMS to guardians if 5-minute safety check expires without response",
                            style = MaterialTheme.typography.bodySmall,
                            color = SafeHerTextSecondary
                        )
                    }
                    Switch(
                        checked = isOverdueEnabled,
                        onCheckedChange = onToggleEnabled,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = SafeHerPurplePrimary,
                            checkedTrackColor = SafeHerPurpleLight
                        )
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = SafeHerPurplePrimary),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Done", fontWeight = FontWeight.Bold)
            }
        },
        shape = RoundedCornerShape(20.dp),
        containerColor = Color.White
    )
}

@Composable
private fun SafetyExplainerItem(
    title: String,
    description: String
) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = SafeHerTextPrimary
        )
        Text(
            text = description,
            style = MaterialTheme.typography.bodySmall,
            color = SafeHerTextSecondary
        )
    }
}

