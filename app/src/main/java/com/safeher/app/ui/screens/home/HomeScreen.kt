package com.safeher.app.ui.screens.home

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PhoneInTalk
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import android.app.Activity
import android.content.ActivityNotFoundException
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import com.safeher.app.R
import com.safeher.app.data.RepositoryProvider
import com.safeher.app.data.journey.Journey
import com.safeher.app.data.journey.JourneyStatus
import com.safeher.app.data.sos.EmergencyMessageBuilder
import com.safeher.app.service.SafeHerMessagingService
import com.safeher.app.ui.screens.account.AccountScreen
import com.safeher.app.ui.screens.account.AccountViewModel
import com.safeher.app.ui.screens.contacts.ContactsScreen
import com.safeher.app.ui.screens.guardian.GuardianAlertDetailScreen
import com.safeher.app.ui.screens.guardian.GuardianAlertDetailViewModel
import com.safeher.app.ui.screens.home.components.EmergencySosDialog
import com.safeher.app.ui.screens.home.components.FeaturePreviewDialog
import com.safeher.app.ui.screens.home.components.HomeHeader
import com.safeher.app.ui.screens.home.components.QuickActionsRow
import com.safeher.app.ui.screens.home.components.SafeHerBottomBar
import com.safeher.app.ui.screens.home.components.SafetyHeroBanner
import com.safeher.app.ui.screens.home.components.SafetyShortcutCard
import com.safeher.app.ui.screens.home.components.SosButtonControl
import com.safeher.app.ui.screens.home.components.TabPlaceholderScreen
import com.safeher.app.ui.screens.journeys.JourneysScreen
import com.safeher.app.ui.screens.map.MapScreen
import com.safeher.app.ui.theme.SafeHerPinkAccent
import com.safeher.app.ui.theme.SafeHerPinkContainer
import com.safeher.app.ui.theme.SafeHerPurpleContainer
import com.safeher.app.ui.theme.SafeHerPurpleLight
import com.safeher.app.ui.theme.SafeHerPurplePrimary
import com.safeher.app.ui.theme.SafeHerSosCrimson
import com.safeher.app.ui.theme.SafeHerSosTrack
import com.safeher.app.ui.theme.SafeHerTextPrimary
import com.safeher.app.ui.theme.SafeHerTextSecondary
import com.safeher.app.ui.theme.SafeHerTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HomeScreen() {
    val context = LocalContext.current
    @Suppress("DEPRECATION")
    val clipboardManager = LocalClipboardManager.current

    // Check if an alert was passed via push notification intent
    var viewingAlertId by remember {
        mutableStateOf((context as? Activity)?.intent?.getStringExtra(SafeHerMessagingService.EXTRA_ALERT_ID))
    }

    if (viewingAlertId != null) {
        val alertDetailViewModel: GuardianAlertDetailViewModel = viewModel(
            key = viewingAlertId,
            factory = remember(context, viewingAlertId) {
                object : ViewModelProvider.Factory {
                    override fun <T : ViewModel> create(modelClass: Class<T>): T {
                        val remoteSosRepo = RepositoryProvider.getRemoteSosRepository(context)
                        @Suppress("UNCHECKED_CAST")
                        return GuardianAlertDetailViewModel(viewingAlertId!!, remoteSosRepo) as T
                    }
                }
            }
        )
        GuardianAlertDetailScreen(
            viewModel = alertDetailViewModel,
            onBackClick = { viewingAlertId = null }
        )
        return
    }

    val viewModel: HomeViewModel = viewModel(
        factory = remember(context) {
            object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    val guardianRepo = RepositoryProvider.getGuardianRepository(context)
                    val journeyRepo = RepositoryProvider.getJourneyRepository(context)
                    val locationRepo = RepositoryProvider.getLocationRepository(context)
                    val authRepo = RepositoryProvider.getAuthRepository(context)
                    val remoteGuardianRepo = RepositoryProvider.getRemoteGuardianRepository(context)
                    val remoteSosRepo = RepositoryProvider.getRemoteSosRepository(context)
                    val demoModeManager = RepositoryProvider.getDemoModeManager(context)
                    @Suppress("UNCHECKED_CAST")
                    return HomeViewModel(
                        guardianRepository = guardianRepo,
                        journeyRepository = journeyRepo,
                        locationRepository = locationRepo,
                        authRepository = authRepo,
                        remoteGuardianRepository = remoteGuardianRepo,
                        remoteSosRepository = remoteSosRepo,
                        demoModeManager = demoModeManager
                    ) as T
                }
            }
        }
    )
    val uiState by viewModel.uiState.collectAsState()

    HomeScreenContent(
        uiState = uiState,
        onTabSelected = { viewModel.selectTab(it) },
        onSosHoldStart = { viewModel.startSosHold() },
        onSosHoldCancel = { viewModel.cancelSosHold() },
        onProfileClick = { viewModel.selectTab(HomeTab.MORE) },
        onShortcutLiveTrackingClick = { viewModel.selectTab(HomeTab.MAP) },
        onShortcutContactsClick = { viewModel.selectTab(HomeTab.CONTACTS) },
        onShortcutSafeJourneyClick = { viewModel.selectTab(HomeTab.JOURNEYS) },
        onShortcutEmergencyCallClick = { viewModel.openDialog(ActiveDialog.EmergencyCallPreview) },
        onShareStatusClick = { viewModel.openDialog(ActiveDialog.ShareStatusPreview) },
        onCheckInClick = { viewModel.openDialog(ActiveDialog.CheckInPreview) },
        onSafetyTipsClick = { viewModel.openDialog(ActiveDialog.SafetyTipsPreview) },
        onDismissDialog = { viewModel.dismissDialog() },
        onSelectSosChannel = { viewModel.setSosAlertChannel(it) },
        onToggleSosGuardian = { viewModel.toggleGuardianSelection(it) },
        onSelectAllSosGuardians = { viewModel.selectAllGuardians() },
        onDeselectAllSosGuardians = { viewModel.deselectAllGuardians() },
        onToggleRemoteSosGuardian = { viewModel.toggleRemoteGuardianSelection(it) },
        onSelectAllRemoteSosGuardians = { viewModel.selectAllRemoteGuardians() },
        onDeselectAllRemoteSosGuardians = { viewModel.deselectAllRemoteGuardians() },
        onDispatchRemoteSos = { viewModel.dispatchRemoteSosAlert() },
        onNavigateToAccount = { viewModel.selectTab(HomeTab.MORE) },
        onSimulateAcknowledgement = { viewModel.simulateGuardianAcknowledgement(it) },
        onToggleIncludeSosLocation = { viewModel.toggleIncludeLocation(it) },
        onUpdateSosMessage = { viewModel.updateEditableMessage(it) },
        onCancelSosWorkflow = { viewModel.cancelSosWorkflow() },
        onCopySosMessage = {
            clipboardManager.setText(AnnotatedString(uiState.sosWorkflow.editableMessage))
            viewModel.onMessageCopied()
        },
        onOpenMessagingApp = {
            if (uiState.sosWorkflow.isLaunchingComposer) return@HomeScreenContent
            viewModel.startComposerLaunch()
            try {
                val intent = EmergencyMessageBuilder.createSmsIntent(
                    recipients = uiState.sosWorkflow.selectedRecipients,
                    messageBody = uiState.sosWorkflow.editableMessage
                )
                context.startActivity(intent)
                viewModel.onComposerLaunched()
            } catch (e: ActivityNotFoundException) {
                viewModel.onComposerFailed("No compatible SMS messaging application found on this device.")
            } catch (e: Exception) {
                viewModel.onComposerFailed("Unable to open messaging app: ${e.localizedMessage ?: "Unknown error"}")
            }
        }
    )
}

@Composable
fun HomeScreenContent(
    uiState: HomeUiState,
    onTabSelected: (HomeTab) -> Unit,
    onSosHoldStart: () -> Unit,
    onSosHoldCancel: () -> Unit,
    onProfileClick: () -> Unit,
    onShortcutLiveTrackingClick: () -> Unit,
    onShortcutContactsClick: () -> Unit,
    onShortcutSafeJourneyClick: () -> Unit,
    onShortcutEmergencyCallClick: () -> Unit,
    onShareStatusClick: () -> Unit,
    onCheckInClick: () -> Unit,
    onSafetyTipsClick: () -> Unit,
    onDismissDialog: () -> Unit,
    onSelectSosChannel: (com.safeher.app.ui.screens.home.SosAlertChannel) -> Unit = {},
    onToggleSosGuardian: (Long) -> Unit = {},
    onSelectAllSosGuardians: () -> Unit = {},
    onDeselectAllSosGuardians: () -> Unit = {},
    onToggleRemoteSosGuardian: (String) -> Unit = {},
    onSelectAllRemoteSosGuardians: () -> Unit = {},
    onDeselectAllRemoteSosGuardians: () -> Unit = {},
    onDispatchRemoteSos: () -> Unit = {},
    onNavigateToAccount: () -> Unit = {},
    onSimulateAcknowledgement: (String) -> Unit = {},
    onToggleIncludeSosLocation: (Boolean) -> Unit = {},
    onUpdateSosMessage: (String) -> Unit = {},
    onCancelSosWorkflow: () -> Unit = {},
    onCopySosMessage: () -> Unit = {},
    onOpenMessagingApp: () -> Unit = {}
) {
    val context = LocalContext.current
    Scaffold(
        topBar = {
            if (uiState.currentTab == HomeTab.HOME) {
                HomeHeader(
                    onProfileClick = onProfileClick
                )
            }
        },
        bottomBar = {
            SafeHerBottomBar(
                selectedTab = uiState.currentTab,
                onTabSelected = onTabSelected
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        if (uiState.currentTab == HomeTab.HOME) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .verticalScroll(rememberScrollState())
            ) {
                // 0. Demo Mode Indicator Banner
                if (uiState.isDemoMode) {
                    DemoModeBanner(onOpenAccountClick = onProfileClick)
                }

                // 1. Safety Hero Section
                SafetyHeroBanner(guardianCount = uiState.guardianCount)

                // 2. Active Journey Summary Banner (if active journey exists)
                if (uiState.activeJourney != null) {
                    HomeActiveJourneyBanner(
                        journey = uiState.activeJourney,
                        nowMillis = uiState.currentTimeMillis,
                        onViewJourneyClick = onShortcutSafeJourneyClick
                    )
                }

                // 3. Interactive SOS Section
                SosButtonControl(
                    isHolding = uiState.isHoldingSos,
                    holdProgress = uiState.sosHoldProgress,
                    feedbackText = uiState.sosFeedbackText,
                    onHoldStart = onSosHoldStart,
                    onHoldCancel = onSosHoldCancel
                )

                // 4. Safety Shortcuts Section (2x2 Grid)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = stringResource(R.string.shortcuts_section_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = SafeHerTextPrimary,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        SafetyShortcutCard(
                            title = stringResource(R.string.shortcut_live_tracking),
                            subtitle = stringResource(R.string.shortcut_live_tracking_desc),
                            icon = Icons.Default.LocationOn,
                            iconTint = SafeHerPurplePrimary,
                            iconContainerColor = SafeHerPurpleContainer,
                            onClick = onShortcutLiveTrackingClick,
                            modifier = Modifier.weight(1f)
                        )

                        SafetyShortcutCard(
                            title = stringResource(R.string.shortcut_contacts),
                            subtitle = stringResource(R.string.shortcut_contacts_desc),
                            icon = Icons.Default.People,
                            iconTint = SafeHerPinkAccent,
                            iconContainerColor = SafeHerPinkContainer,
                            onClick = onShortcutContactsClick,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        SafetyShortcutCard(
                            title = stringResource(R.string.shortcut_safe_journey),
                            subtitle = stringResource(R.string.shortcut_safe_journey_desc),
                            icon = Icons.AutoMirrored.Filled.DirectionsWalk,
                            iconTint = SafeHerPurplePrimary,
                            iconContainerColor = SafeHerPurpleContainer,
                            onClick = onShortcutSafeJourneyClick,
                            modifier = Modifier.weight(1f)
                        )

                        SafetyShortcutCard(
                            title = stringResource(R.string.shortcut_emergency_call),
                            subtitle = stringResource(R.string.shortcut_emergency_call_desc),
                            icon = Icons.Default.PhoneInTalk,
                            iconTint = SafeHerSosCrimson,
                            iconContainerColor = SafeHerSosTrack,
                            onClick = onShortcutEmergencyCallClick,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // 5. Quick Actions Section (Responsive 3-Column)
                QuickActionsRow(
                    onShareStatusClick = onShareStatusClick,
                    onCheckInClick = onCheckInClick,
                    onSafetyTipsClick = onSafetyTipsClick
                )

                Spacer(modifier = Modifier.height(16.dp))
            }
        } else if (uiState.currentTab == HomeTab.MAP) {
            MapScreen(
                onBackToHomeClick = { onTabSelected(HomeTab.HOME) },
                modifier = Modifier.padding(paddingValues)
            )
        } else if (uiState.currentTab == HomeTab.JOURNEYS) {
            JourneysScreen(
                onBackToHomeClick = { onTabSelected(HomeTab.HOME) },
                modifier = Modifier.padding(paddingValues)
            )
        } else if (uiState.currentTab == HomeTab.CONTACTS) {
            ContactsScreen(
                onBackToHomeClick = { onTabSelected(HomeTab.HOME) },
                modifier = Modifier.padding(paddingValues)
            )
        } else if (uiState.currentTab == HomeTab.MORE) {
            val accountViewModel: AccountViewModel = viewModel(
                factory = remember(context) {
                    object : ViewModelProvider.Factory {
                        override fun <T : ViewModel> create(modelClass: Class<T>): T {
                            val authRepo = RepositoryProvider.getAuthRepository(context)
                            val remoteGuardianRepo = RepositoryProvider.getRemoteGuardianRepository(context)
                            val demoModeManager = RepositoryProvider.getDemoModeManager(context)
                            @Suppress("UNCHECKED_CAST")
                            return AccountViewModel(authRepo, remoteGuardianRepo, demoModeManager) as T
                        }
                    }
                }
            )
            AccountScreen(
                viewModel = accountViewModel,
                onBackClick = { onTabSelected(HomeTab.HOME) },
                modifier = Modifier.padding(paddingValues)
            )
        } else {
            TabPlaceholderScreen(
                tab = uiState.currentTab,
                onBackToHomeClick = { onTabSelected(HomeTab.HOME) },
                modifier = Modifier.padding(paddingValues)
            )
        }
    }

    // Emergency SOS Review & Handoff Dialog
    EmergencySosDialog(
        workflow = uiState.sosWorkflow,
        onDismiss = onCancelSosWorkflow,
        onSelectChannel = onSelectSosChannel,
        onToggleGuardian = onToggleSosGuardian,
        onSelectAllGuardians = onSelectAllSosGuardians,
        onDeselectAllGuardians = onDeselectAllSosGuardians,
        onToggleRemoteGuardian = onToggleRemoteSosGuardian,
        onSelectAllRemoteGuardians = onSelectAllRemoteSosGuardians,
        onDeselectAllRemoteGuardians = onDeselectAllRemoteSosGuardians,
        onDispatchRemoteAlert = onDispatchRemoteSos,
        onNavigateToAccount = onNavigateToAccount,
        onSimulateAcknowledgement = onSimulateAcknowledgement,
        onToggleIncludeLocation = onToggleIncludeSosLocation,
        onMessageChange = onUpdateSosMessage,
        onCopyMessage = onCopySosMessage,
        onOpenMessagingApp = onOpenMessagingApp
    )

    // Modal dialogs & previews
    if (uiState.activeDialog != ActiveDialog.EmergencySos) {
        FeaturePreviewDialog(
            activeDialog = uiState.activeDialog,
            onDismiss = onDismissDialog
        )
    }
}

@Composable
private fun HomeActiveJourneyBanner(
    journey: Journey,
    nowMillis: Long,
    onViewJourneyClick: () -> Unit
) {
    val effectiveStatus = journey.getEffectiveStatus(nowMillis)
    val isOverdue = effectiveStatus == JourneyStatus.OVERDUE
    val borderColor = if (isOverdue) SafeHerSosCrimson else SafeHerPurpleLight
    val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())

    val remainingMs = journey.remainingMillis(nowMillis)
    val overdueMs = journey.overdueMillis(nowMillis)
    val timeLabel = if (isOverdue) {
        val mins = (overdueMs / 60000L).coerceAtLeast(1L)
        "+${mins}m overdue"
    } else {
        val mins = (remainingMs / 60000L).coerceAtLeast(0L)
        "${mins}m remaining"
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp)
            .border(width = 1.dp, color = borderColor, shape = RoundedCornerShape(16.dp))
            .clickable(onClick = onViewJourneyClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(if (isOverdue) SafeHerSosTrack else SafeHerPurpleContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isOverdue) Icons.Default.Warning else Icons.AutoMirrored.Filled.DirectionsWalk,
                    contentDescription = null,
                    tint = if (isOverdue) SafeHerSosCrimson else SafeHerPurplePrimary,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (isOverdue) "OVERDUE TRIP" else "ACTIVE TRIP",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isOverdue) SafeHerSosCrimson else SafeHerPurplePrimary,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = timeLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isOverdue) SafeHerSosCrimson else SafeHerPurplePrimary,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Text(
                    text = journey.displayTitle,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = SafeHerTextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                val guidanceText = if (isOverdue) {
                    "Expected arrival passed • Please check in or extend"
                } else if (journey.lastCheckInTimestamp != null) {
                    "Checked in at ${timeFormat.format(Date(journey.lastCheckInTimestamp))}"
                } else {
                    "ETA: ${timeFormat.format(Date(journey.expectedArrivalTimestamp))}"
                }

                Text(
                    text = guidanceText,
                    style = MaterialTheme.typography.labelSmall,
                    color = SafeHerTextSecondary
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Text(
                text = "View",
                style = MaterialTheme.typography.labelMedium,
                color = SafeHerPurplePrimary,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun HomeScreenPreview() {
    SafeHerTheme {
        HomeScreenContent(
            uiState = HomeUiState(),
            onTabSelected = {},
            onSosHoldStart = {},
            onSosHoldCancel = {},
            onProfileClick = {},
            onShortcutLiveTrackingClick = {},
            onShortcutContactsClick = {},
            onShortcutSafeJourneyClick = {},
            onShortcutEmergencyCallClick = {},
            onShareStatusClick = {},
            onCheckInClick = {},
            onSafetyTipsClick = {},
            onDismissDialog = {}
        )
    }
}

@Composable
private fun DemoModeBanner(
    onOpenAccountClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = com.safeher.app.ui.theme.SafeHerPurpleContainer),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, com.safeher.app.ui.theme.SafeHerPurpleLight),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp)
            .clickable { onOpenAccountClick() }
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "🧪",
                fontSize = 20.sp
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "SafeHer Demo Mode Active",
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = com.safeher.app.ui.theme.SafeHerPurpleDark
                )
                Text(
                    text = "Local simulation enabled. Tap to switch demo roles or exit.",
                    fontSize = 11.sp,
                    color = com.safeher.app.ui.theme.SafeHerTextSecondary
                )
            }
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = "Demo Info",
                tint = com.safeher.app.ui.theme.SafeHerPurplePrimary,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
