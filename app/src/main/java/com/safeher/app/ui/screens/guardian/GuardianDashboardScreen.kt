package com.safeher.app.ui.screens.guardian

import androidx.compose.animation.animateColorAsState
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.safeher.app.data.RepositoryProvider
import com.safeher.app.data.remoteguardian.RemoteGuardian
import com.safeher.app.data.remotesos.RemoteAlertDetails
import com.safeher.app.ui.theme.SafeHerBorderLavender
import com.safeher.app.ui.theme.SafeHerPinkContainer
import com.safeher.app.ui.theme.SafeHerPurpleContainer
import com.safeher.app.ui.theme.SafeHerPurpleDark
import com.safeher.app.ui.theme.SafeHerPurplePrimary
import com.safeher.app.ui.theme.SafeHerSosCrimson
import com.safeher.app.ui.theme.SafeHerSosTrack
import com.safeher.app.ui.theme.SafeHerStatusEmergency
import com.safeher.app.ui.theme.SafeHerStatusSafe
import com.safeher.app.ui.theme.SafeHerSurfaceVariant
import com.safeher.app.ui.theme.SafeHerSurfaceWhite
import com.safeher.app.ui.theme.SafeHerTextMuted
import com.safeher.app.ui.theme.SafeHerTextPrimary
import com.safeher.app.ui.theme.SafeHerTextSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Separate Guardian interface implementing the 5 primary sections:
 * - Home: Genuine connected-person summaries and authorized updates.
 * - People: Invitation-code entry, connection state, and authorized relationship management.
 * - Journeys: Authorized journeys genuinely shared with this Guardian.
 * - Alerts: Real emergency alerts authorized for this Guardian.
 * - Account: Authenticated guardian account settings, email verification, and sign-out.
 */
@Composable
fun GuardianDashboardScreen(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val authRepo = remember(context) { RepositoryProvider.getAuthRepository(context) }
    val remoteGuardianRepo = remember(context) { RepositoryProvider.getRemoteGuardianRepository(context) }
    val remoteSosRepo = remember(context) { RepositoryProvider.getRemoteSosRepository(context) }
    val sessionManager = remember(context) { RepositoryProvider.getUserSessionManager(context) }
    val demoModeManager = if (com.safeher.app.BuildConfig.IS_DEMO_DEFAULT) {
        remember(context) { RepositoryProvider.getDemoModeManager(context) }
    } else {
        null
    }

    val viewModel: GuardianDashboardViewModel = viewModel(
        factory = remember(context) {
            object : androidx.lifecycle.ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                    return GuardianDashboardViewModel(
                        authRepository = authRepo,
                        remoteGuardianRepository = remoteGuardianRepo,
                        remoteSosRepository = remoteSosRepo,
                        sessionManager = sessionManager,
                        demoModeManager = demoModeManager
                    ) as T
                }
            }
        }
    )

    val uiState by viewModel.uiState.collectAsState()

    // If viewing a specific alert detail, show GuardianAlertDetailScreen
    if (uiState.selectedAlertId != null) {
        val alertId = uiState.selectedAlertId ?: ""
        val alertDetailViewModel: GuardianAlertDetailViewModel = viewModel(
            key = alertId,
            factory = remember(alertId) {
                object : androidx.lifecycle.ViewModelProvider.Factory {
                    @Suppress("UNCHECKED_CAST")
                    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                        return GuardianAlertDetailViewModel(alertId, remoteSosRepo) as T
                    }
                }
            }
        )

        GuardianAlertDetailScreen(
            viewModel = alertDetailViewModel,
            onBackClick = { viewModel.selectAlert(null) },
            modifier = modifier
        )
        return
    }

    Scaffold(
        bottomBar = {
            GuardianBottomBar(
                selectedTab = uiState.selectedTab,
                onTabSelected = { viewModel.selectTab(it) }
            )
        },
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (uiState.selectedTab) {
                GuardianTab.HOME -> GuardianHomeScreen(
                    uiState = uiState,
                    onOpenConnectDialog = { viewModel.openConnectDialog() },
                    onNavigateToPeople = { viewModel.selectTab(GuardianTab.PEOPLE) },
                    onNavigateToAlerts = { viewModel.selectTab(GuardianTab.ALERTS) },
                    onAlertClick = { viewModel.selectAlert(it) }
                )
                GuardianTab.PEOPLE -> GuardianPeopleScreen(
                    uiState = uiState,
                    onOpenConnectDialog = { viewModel.openConnectDialog() },
                    onRevokeClick = { viewModel.promptRevokeRelationship(it) }
                )
                GuardianTab.JOURNEYS -> GuardianJourneysScreen(
                    uiState = uiState,
                    onOpenConnectDialog = { viewModel.openConnectDialog() }
                )
                GuardianTab.ALERTS -> GuardianAlertsScreen(
                    uiState = uiState,
                    onAlertClick = { viewModel.selectAlert(it) }
                )
                GuardianTab.ACCOUNT -> GuardianAccountScreen(
                    uiState = uiState,
                    onOpenConnectDialog = { viewModel.openConnectDialog() },
                    onSendVerification = { viewModel.sendEmailVerification() },
                    onSignOut = { viewModel.signOut() }
                )
            }
        }
    }

    // Connect User Dialog
    if (uiState.showConnectDialog) {
        ConnectUserDialog(
            inviteCode = uiState.inviteCodeInput,
            isConnecting = uiState.isConnecting,
            error = uiState.connectError,
            success = uiState.connectSuccess,
            onCodeChanged = { viewModel.updateInviteCode(it) },
            onSubmit = { viewModel.submitConnectCode() },
            onDismiss = { viewModel.dismissConnectDialog() }
        )
    }

    // Revoke Confirmation Dialog
    uiState.revokingRelationship?.let { relationship ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissRevokePrompt() },
            title = {
                Text(
                    text = "Revoke Connection?",
                    fontWeight = FontWeight.Bold,
                    color = SafeHerTextPrimary
                )
            },
            text = {
                Text(
                    text = "Are you sure you want to stop guarding ${relationship.guardianDisplayName}? You will no longer receive their emergency alerts or shared journeys.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = SafeHerTextSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.confirmRevokeRelationship() },
                    colors = ButtonDefaults.buttonColors(containerColor = SafeHerSosCrimson),
                    enabled = !uiState.isRevoking
                ) {
                    if (uiState.isRevoking) {
                        CircularProgressIndicator(
                            color = Color.White,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(16.dp)
                        )
                    } else {
                        Text("Revoke", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissRevokePrompt() }) {
                    Text("Cancel", color = SafeHerTextSecondary)
                }
            },
            containerColor = SafeHerSurfaceWhite,
            shape = RoundedCornerShape(20.dp)
        )
    }
}

// ======================================================================================
// SECTION 1: HOME
// ======================================================================================

@Composable
private fun GuardianHomeScreen(
    uiState: GuardianDashboardUiState,
    onOpenConnectDialog: () -> Unit,
    onNavigateToPeople: () -> Unit,
    onNavigateToAlerts: () -> Unit,
    onAlertClick: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        // Top Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "SafeHer",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = SafeHerTextPrimary
                )
                Text(
                    text = "Welcome, ${uiState.currentUser?.displayName?.ifBlank { "Guardian" } ?: "Guardian"}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = SafeHerTextSecondary
                )
                Spacer(modifier = Modifier.height(8.dp))
                com.safeher.app.ui.components.RoleBadge(
                    tone = com.safeher.app.ui.components.SafeHerRoleTone.GUARDIAN
                )
            }
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(SafeHerPurplePrimary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.VerifiedUser,
                    contentDescription = null,
                    tint = SafeHerPurplePrimary,
                    modifier = Modifier.size(26.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        com.safeher.app.ui.components.InfoBanner(
            text = "Enter an invitation phrase from a Personal Safety User to connect. Phrases are single-use and expire in 48 hours. Remote SOS push alerts still require Cloud Functions and remain unavailable on Spark.",
            tone = com.safeher.app.ui.components.SafeHerStatusTone.INFO
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Active Emergency SOS Alert Banner (if any)
        val activeAlerts = uiState.alerts
        if (activeAlerts.isNotEmpty()) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onNavigateToAlerts() },
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = SafeHerSosCrimson.copy(alpha = 0.12f)),
                border = BorderStroke(1.5.dp, SafeHerSosCrimson.copy(alpha = 0.5f))
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(SafeHerSosCrimson.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = SafeHerSosCrimson,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "${activeAlerts.size} Active Emergency Alert${if (activeAlerts.size > 1) "s" else ""}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = SafeHerSosCrimson
                        )
                        Text(
                            text = "Tap to review details and record acknowledgement.",
                            style = MaterialTheme.typography.bodySmall,
                            color = SafeHerTextPrimary
                        )
                    }
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        tint = SafeHerSosCrimson,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        } else {
            // All Safe Status Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = SafeHerStatusSafe.copy(alpha = 0.1f)),
                border = BorderStroke(1.dp, SafeHerStatusSafe.copy(alpha = 0.3f))
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(SafeHerStatusSafe.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = SafeHerStatusSafe,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column {
                        Text(
                            text = "All Protected Circles Safe",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = SafeHerStatusSafe
                        )
                        Text(
                            text = "No active SOS alerts broadcasted to you.",
                            style = MaterialTheme.typography.bodySmall,
                            color = SafeHerTextSecondary
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Quick Stats Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Card(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onNavigateToPeople() },
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = SafeHerSurfaceWhite),
                border = BorderStroke(1.dp, SafeHerBorderLavender)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Guarded", style = MaterialTheme.typography.labelMedium, color = SafeHerTextSecondary)
                        Icon(Icons.Default.People, contentDescription = null, tint = SafeHerPurplePrimary, modifier = Modifier.size(20.dp))
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "${uiState.guardedUsers.size}",
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                        color = SafeHerTextPrimary
                    )
                    Text("Protected users", style = MaterialTheme.typography.bodySmall, color = SafeHerTextMuted)
                }
            }

            Card(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onNavigateToAlerts() },
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = SafeHerSurfaceWhite),
                border = BorderStroke(1.dp, SafeHerBorderLavender)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Alerts", style = MaterialTheme.typography.labelMedium, color = SafeHerTextSecondary)
                        Icon(Icons.Default.NotificationsActive, contentDescription = null, tint = SafeHerPurplePrimary, modifier = Modifier.size(20.dp))
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "${uiState.alerts.size}",
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (uiState.alerts.isNotEmpty()) SafeHerSosCrimson else SafeHerTextPrimary
                    )
                    Text("Emergency events", style = MaterialTheme.typography.bodySmall, color = SafeHerTextMuted)
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Protected Persons Section Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Protected Persons",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = SafeHerTextPrimary
            )
            TextButton(onClick = onOpenConnectDialog) {
                Icon(Icons.Default.Key, contentDescription = null, tint = SafeHerPurplePrimary, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Connect Code", color = SafeHerPurplePrimary, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (uiState.guardedUsers.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = SafeHerSurfaceWhite),
                border = BorderStroke(1.dp, SafeHerBorderLavender)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(SafeHerPurplePrimary.copy(alpha = 0.1f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.People, contentDescription = null, tint = SafeHerPurplePrimary, modifier = Modifier.size(28.dp))
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "No Connected Persons Yet",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = SafeHerTextPrimary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Ask a SafeHer user to generate an invitation phrase from their Account screen.",
                        style = MaterialTheme.typography.bodySmall,
                        color = SafeHerTextSecondary,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = onOpenConnectDialog,
                        colors = ButtonDefaults.buttonColors(containerColor = SafeHerPurplePrimary),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Connect SafeHer User", fontWeight = FontWeight.Bold)
                    }
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                uiState.guardedUsers.take(3).forEach { person ->
                    PersonSummaryCard(person = person, onClick = onNavigateToPeople)
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

// ======================================================================================
// SECTION 2: PEOPLE
// ======================================================================================

@Composable
private fun GuardianPeopleScreen(
    uiState: GuardianDashboardUiState,
    onOpenConnectDialog: () -> Unit,
    onRevokeClick: (RemoteGuardian) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Protected People",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = SafeHerTextPrimary
                )
                Text(
                    text = "${uiState.guardedUsers.size} authorized connection${if (uiState.guardedUsers.size != 1) "s" else ""}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = SafeHerTextSecondary
                )
            }
            Button(
                onClick = onOpenConnectDialog,
                colors = ButtonDefaults.buttonColors(containerColor = SafeHerPurplePrimary),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Key, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Enter Code", fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (uiState.guardedUsers.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 60.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(24.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(SafeHerPurplePrimary.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = null,
                            tint = SafeHerPurplePrimary,
                            modifier = Modifier.size(38.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "No Connected Persons",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = SafeHerTextPrimary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "To safeguard someone, ask them to tap Invite Guardian in their SafeHer app and share their temporary invitation phrase with you.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = SafeHerTextSecondary,
                        textAlign = TextAlign.Center,
                        lineHeight = 20.sp
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    Button(
                        onClick = onOpenConnectDialog,
                        colors = ButtonDefaults.buttonColors(containerColor = SafeHerPurplePrimary),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text("Connect with Invitation Code", fontWeight = FontWeight.Bold)
                    }
                }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(uiState.guardedUsers, key = { it.relationshipId }) { person ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = SafeHerSurfaceWhite),
                        border = BorderStroke(1.dp, SafeHerBorderLavender)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(46.dp)
                                        .clip(CircleShape)
                                        .background(SafeHerPurplePrimary.copy(alpha = 0.12f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = person.guardianDisplayName.take(1).uppercase(),
                                        fontWeight = FontWeight.Bold,
                                        color = SafeHerPurplePrimary,
                                        fontSize = 18.sp
                                    )
                                }
                                Spacer(modifier = Modifier.width(14.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = person.guardianDisplayName,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = SafeHerTextPrimary
                                    )
                                    if (person.guardianEmail.isNotBlank()) {
                                        Text(
                                            text = person.guardianEmail,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = SafeHerTextSecondary
                                        )
                                    }
                                }
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(SafeHerStatusSafe.copy(alpha = 0.15f))
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = "Active",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = SafeHerStatusSafe,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))
                            HorizontalDivider(color = SafeHerBorderLavender)
                            Spacer(modifier = Modifier.height(10.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val dateStr = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
                                    .format(Date(person.createdAtMillis))
                                Text(
                                    text = "Connected $dateStr",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = SafeHerTextMuted
                                )
                                TextButton(
                                    onClick = { onRevokeClick(person) }
                                ) {
                                    Text("Unlink", color = SafeHerSosCrimson, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ======================================================================================
// SECTION 3: JOURNEYS
// ======================================================================================

@Composable
private fun GuardianJourneysScreen(
    uiState: GuardianDashboardUiState,
    onOpenConnectDialog: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        Text(
            text = "Shared Journeys",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = SafeHerTextPrimary
        )
        Text(
            text = "Live journeys shared with you by protected users",
            style = MaterialTheme.typography.bodyMedium,
            color = SafeHerTextSecondary
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Honest empty state: no fake journeys
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 60.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(24.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(SafeHerPurplePrimary.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.DirectionsWalk,
                        contentDescription = null,
                        tint = SafeHerPurplePrimary,
                        modifier = Modifier.size(38.dp)
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "No Active Shared Journeys",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = SafeHerTextPrimary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "When a user you protect starts a monitored journey and chooses to share live updates with you, the live route, destination, and check-in timeline will appear here.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = SafeHerTextSecondary,
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp
                )
                Spacer(modifier = Modifier.height(20.dp))
                if (uiState.guardedUsers.isEmpty()) {
                    Button(
                        onClick = onOpenConnectDialog,
                        colors = ButtonDefaults.buttonColors(containerColor = SafeHerPurplePrimary),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text("Connect a SafeHer User", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// ======================================================================================
// SECTION 4: ALERTS
// ======================================================================================

@Composable
private fun GuardianAlertsScreen(
    uiState: GuardianDashboardUiState,
    onAlertClick: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        Text(
            text = "Emergency SOS Alerts",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = SafeHerTextPrimary
        )
        Text(
            text = "Emergency broadcasts authorized for your guardian account",
            style = MaterialTheme.typography.bodyMedium,
            color = SafeHerTextSecondary
        )

        Spacer(modifier = Modifier.height(16.dp))

        if (uiState.alerts.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 60.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(24.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(SafeHerStatusSafe.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = SafeHerStatusSafe,
                            modifier = Modifier.size(38.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "No Emergency Alerts",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = SafeHerTextPrimary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Your circle has not reported any active emergencies. All protected users are safe.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = SafeHerTextSecondary,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(uiState.alerts, key = { it.alertId }) { alert ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onAlertClick(alert.alertId) },
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = SafeHerSurfaceWhite),
                        border = BorderStroke(1.5.dp, SafeHerSosCrimson.copy(alpha = 0.4f))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(42.dp)
                                        .clip(CircleShape)
                                        .background(SafeHerSosCrimson.copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Warning,
                                        contentDescription = null,
                                        tint = SafeHerSosCrimson,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = alert.senderDisplayName,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = SafeHerTextPrimary
                                    )
                                    val dateStr = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())
                                        .format(Date(alert.createdAtMillis))
                                    Text(
                                        text = dateStr,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = SafeHerTextSecondary
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (alert.isAcknowledgedByMe) SafeHerStatusSafe.copy(alpha = 0.15f) else SafeHerSosCrimson.copy(alpha = 0.15f))
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = if (alert.isAcknowledgedByMe) "Acknowledged" else "Active SOS",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (alert.isAcknowledgedByMe) SafeHerStatusSafe else SafeHerSosCrimson,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            if (alert.messageText.isNotBlank()) {
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = alert.messageText,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = SafeHerTextPrimary
                                )
                            }

                            if (alert.hasLocation && alert.latitude != null && alert.longitude != null) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.LocationOn,
                                        contentDescription = null,
                                        tint = SafeHerPurplePrimary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Location: %.5f, %.5f".format(alert.latitude, alert.longitude),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = SafeHerPurplePrimary
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))
                            Button(
                                onClick = { onAlertClick(alert.alertId) },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = SafeHerPurplePrimary),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Open Alert Details", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ======================================================================================
// SECTION 5: ACCOUNT
// ======================================================================================

@Composable
private fun GuardianAccountScreen(
    uiState: GuardianDashboardUiState,
    onOpenConnectDialog: () -> Unit,
    onSendVerification: () -> Unit,
    onSignOut: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        Text(
            text = "Guardian Account",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = SafeHerTextPrimary
        )
        Text(
            text = "Profile and connection preferences",
            style = MaterialTheme.typography.bodyMedium,
            color = SafeHerTextSecondary
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Profile Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = SafeHerSurfaceWhite),
            border = BorderStroke(1.dp, SafeHerBorderLavender)
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .clip(CircleShape)
                            .background(SafeHerPurplePrimary.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            tint = SafeHerPurplePrimary,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = uiState.currentUser?.displayName?.ifBlank { "Guardian" } ?: "Guardian",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = SafeHerTextPrimary
                        )
                        Text(
                            text = uiState.currentUser?.email ?: "",
                            style = MaterialTheme.typography.bodyMedium,
                            color = SafeHerTextSecondary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = SafeHerBorderLavender)
                Spacer(modifier = Modifier.height(14.dp))

                // Role badge
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Role", style = MaterialTheme.typography.bodyMedium, color = SafeHerTextSecondary)
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(SafeHerPinkContainer)
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "Guardian",
                            style = MaterialTheme.typography.labelMedium,
                            color = SafeHerPurpleDark,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Email verification status
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Email Verification", style = MaterialTheme.typography.bodyMedium, color = SafeHerTextSecondary)
                    val isVerified = uiState.currentUser?.isEmailVerified == true
                    if (isVerified) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = SafeHerStatusSafe, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Verified", style = MaterialTheme.typography.labelMedium, color = SafeHerStatusSafe, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        TextButton(
                            onClick = onSendVerification,
                            enabled = !uiState.isSendingVerification
                        ) {
                            Text(
                                text = if (uiState.verificationSent) "Email Sent!" else "Send Verification",
                                color = SafeHerPurplePrimary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Actions Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = SafeHerSurfaceWhite),
            border = BorderStroke(1.dp, SafeHerBorderLavender)
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Button(
                    onClick = onOpenConnectDialog,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = SafeHerPurplePrimary),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Key, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Connect to SafeHer User", fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedButton(
                    onClick = onSignOut,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = SafeHerSosCrimson),
                    border = BorderStroke(1.dp, SafeHerSosCrimson.copy(alpha = 0.4f)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Sign Out", fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

// ======================================================================================
// HELPER COMPONENTS & DIALOGS
// ======================================================================================

@Composable
private fun PersonSummaryCard(
    person: RemoteGuardian,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = SafeHerSurfaceWhite),
        border = BorderStroke(1.dp, SafeHerBorderLavender)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(SafeHerPurplePrimary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = person.guardianDisplayName.take(1).uppercase(),
                    fontWeight = FontWeight.Bold,
                    color = SafeHerPurplePrimary
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = person.guardianDisplayName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = SafeHerTextPrimary
                )
                Text(
                    text = "Active Guardian Circle",
                    style = MaterialTheme.typography.bodySmall,
                    color = SafeHerTextSecondary
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = SafeHerTextMuted,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun ConnectUserDialog(
    inviteCode: String,
    isConnecting: Boolean,
    error: String?,
    success: Boolean,
    onCodeChanged: (String) -> Unit,
    onSubmit: () -> Unit,
    onDismiss: () -> Unit
) {
    val focusManager = LocalFocusManager.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Connect to SafeHer User",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column {
                Text(
                    text = "Enter the exact invitation phrase generated by the Personal Safety User (example: BLUE-TIGER-42-MAPLE).",
                    style = MaterialTheme.typography.bodySmall,
                    color = SafeHerTextSecondary
                )

                Spacer(modifier = Modifier.height(14.dp))

                if (error != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(SafeHerStatusEmergency.copy(alpha = 0.12f))
                            .border(1.dp, SafeHerStatusEmergency.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                            .padding(10.dp)
                    ) {
                        Text(
                            text = error,
                            style = MaterialTheme.typography.bodySmall,
                            color = SafeHerStatusEmergency,
                            lineHeight = 16.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }

                if (success) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(SafeHerStatusSafe.copy(alpha = 0.12f))
                            .border(1.dp, SafeHerStatusSafe.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                            .padding(10.dp)
                    ) {
                        Text(
                            text = "Connection authorized! Relationship is now active.",
                            style = MaterialTheme.typography.bodySmall,
                            color = SafeHerStatusSafe,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }

                OutlinedTextField(
                    value = inviteCode,
                    onValueChange = onCodeChanged,
                    label = { Text("Invitation Code") },
                    placeholder = { Text("e.g. SH-8ABC2D") },
                    singleLine = true,
                    enabled = !isConnecting && !success,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp
                    ),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Characters,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            focusManager.clearFocus()
                            onSubmit()
                        }
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = SafeHerPurplePrimary,
                        unfocusedBorderColor = SafeHerBorderLavender
                    )
                )
            }
        },
        confirmButton = {
            if (success) {
                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = SafeHerPurplePrimary)
                ) {
                    Text("Done")
                }
            } else {
                Button(
                    onClick = {
                        focusManager.clearFocus()
                        onSubmit()
                    },
                    enabled = !isConnecting && inviteCode.trim().length >= 10,
                    colors = ButtonDefaults.buttonColors(containerColor = SafeHerPurplePrimary)
                ) {
                    if (isConnecting) {
                        CircularProgressIndicator(
                            color = Color.White,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(16.dp)
                        )
                    } else {
                        Text("Connect")
                    }
                }
            }
        },
        dismissButton = {
            if (!success) {
                TextButton(onClick = onDismiss, enabled = !isConnecting) {
                    Text("Cancel", color = SafeHerTextSecondary)
                }
            }
        },
        containerColor = SafeHerSurfaceWhite,
        shape = RoundedCornerShape(20.dp)
    )
}

data class GuardianNavItem(
    val tab: GuardianTab,
    val label: String,
    val icon: ImageVector
)

@Composable
private fun GuardianBottomBar(
    selectedTab: GuardianTab,
    onTabSelected: (GuardianTab) -> Unit,
    modifier: Modifier = Modifier
) {
    val items = listOf(
        GuardianNavItem(GuardianTab.HOME, "Home", Icons.Default.Home),
        GuardianNavItem(GuardianTab.PEOPLE, "People", Icons.Default.People),
        GuardianNavItem(GuardianTab.JOURNEYS, "Journeys", Icons.AutoMirrored.Filled.DirectionsWalk),
        GuardianNavItem(GuardianTab.ALERTS, "Alerts", Icons.Default.NotificationsActive),
        GuardianNavItem(GuardianTab.ACCOUNT, "Account", Icons.Default.AccountCircle)
    )

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .border(
                1.dp,
                SafeHerBorderLavender,
                RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
            ),
        color = SafeHerSurfaceWhite,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        shadowElevation = com.safeher.app.ui.theme.SafeHerDimens.elevationHigh
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .height(com.safeher.app.ui.theme.SafeHerDimens.bottomBarHeight)
                .padding(horizontal = com.safeher.app.ui.theme.SafeHerDimens.spaceXs),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            items.forEach { item ->
                val isSelected = selectedTab == item.tab

                val iconColor by animateColorAsState(
                    targetValue = if (isSelected) SafeHerPurplePrimary else SafeHerTextMuted,
                    label = "GuardianNavIconColor"
                )

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(MaterialTheme.shapes.small)
                        .clickable(
                            role = Role.Tab,
                            onClickLabel = item.label,
                            onClick = { onTabSelected(item.tab) }
                        )
                        .padding(vertical = com.safeher.app.ui.theme.SafeHerDimens.spaceXxs),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(if (isSelected) SafeHerPurpleContainer else SafeHerSurfaceWhite),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = item.icon,
                                contentDescription = null,
                                tint = iconColor,
                                modifier = Modifier.size(com.safeher.app.ui.theme.SafeHerDimens.iconMd)
                            )
                        }

                        Text(
                            text = item.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = iconColor,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}
