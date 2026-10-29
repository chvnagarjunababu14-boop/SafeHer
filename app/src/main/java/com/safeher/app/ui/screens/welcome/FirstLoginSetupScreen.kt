package com.safeher.app.ui.screens.welcome

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.safeher.app.R
import com.safeher.app.ui.theme.SafeHerBorderLavender
import com.safeher.app.ui.theme.SafeHerPinkAccent
import com.safeher.app.ui.theme.SafeHerPinkContainer
import com.safeher.app.ui.theme.SafeHerPurpleDark
import com.safeher.app.ui.theme.SafeHerPurplePrimary
import com.safeher.app.ui.theme.SafeHerStatusEmergency
import com.safeher.app.ui.theme.SafeHerStatusSafe
import com.safeher.app.ui.theme.SafeHerSurfaceVariant
import com.safeher.app.ui.theme.SafeHerSurfaceWhite
import com.safeher.app.ui.theme.SafeHerTextMuted
import com.safeher.app.ui.theme.SafeHerTextPrimary
import com.safeher.app.ui.theme.SafeHerTextSecondary
import com.safeher.app.ui.theme.SafeHerTheme

@Composable
fun FirstLoginSetupScreen(
    uiState: WelcomeUiState,
    onCompleteSetupClick: () -> Unit,
    onSkipSetupClick: () -> Unit,
    onOpenAddGuardianClick: () -> Unit,
    onDismissAddGuardianClick: () -> Unit,
    onGuardianNameChanged: (String) -> Unit,
    onGuardianPhoneChanged: (String) -> Unit,
    onGuardianRelationshipChanged: (String) -> Unit,
    onSaveGuardianClick: () -> Unit,
    onDirectSmsConsentChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // Permission state checks
    var hasLocationPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    var hasSmsPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.SEND_SMS
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    var hasNotificationPermission by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            } else {
                true
            }
        )
    }

    var showSmsConsentDialog by remember { mutableStateOf(false) }

    // Launchers
    val locationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        hasLocationPermission = perms[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                perms[Manifest.permission.ACCESS_COARSE_LOCATION] == true
    }

    val smsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasSmsPermission = granted
        if (granted) {
            onDirectSmsConsentChanged(true)
        }
    }

    val notificationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasNotificationPermission = granted
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(12.dp))

            // Header Icon
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

            // Title
            Text(
                text = stringResource(R.string.setup_title),
                style = MaterialTheme.typography.headlineSmall,
                color = SafeHerTextPrimary,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Subtitle
            Text(
                text = stringResource(R.string.setup_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = SafeHerTextSecondary,
                textAlign = TextAlign.Center,
                lineHeight = 20.sp,
                modifier = Modifier.padding(horizontal = 8.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))

            // 1. Guardian Setup Card
            SetupCard(
                icon = Icons.Default.People,
                title = stringResource(R.string.setup_step1_title),
                description = stringResource(R.string.setup_step1_desc),
                isCompleted = uiState.hasAddedGuardian,
                completedLabel = stringResource(R.string.setup_step1_done),
                actionLabel = stringResource(R.string.setup_step1_action),
                onActionClick = onOpenAddGuardianClick,
                color = SafeHerPurplePrimary
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 2. Location Access Card
            SetupCard(
                icon = Icons.Default.LocationOn,
                title = stringResource(R.string.setup_step2_title),
                description = stringResource(R.string.setup_step2_desc),
                isCompleted = hasLocationPermission,
                completedLabel = stringResource(R.string.setup_step2_done),
                actionLabel = stringResource(R.string.setup_step2_action),
                onActionClick = {
                    locationLauncher.launch(
                        arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        )
                    )
                },
                color = SafeHerPinkAccent
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 3. Direct SMS & Consent Card
            SetupCard(
                icon = Icons.Default.Sms,
                title = stringResource(R.string.setup_step3_title),
                description = stringResource(R.string.setup_step3_desc),
                isCompleted = hasSmsPermission && uiState.isDirectSmsConsentGiven,
                completedLabel = stringResource(R.string.setup_step3_done),
                actionLabel = stringResource(R.string.setup_step3_action),
                onActionClick = { showSmsConsentDialog = true },
                color = SafeHerPurpleDark
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 4. Notifications & Alerts Card
            SetupCard(
                icon = Icons.Default.Notifications,
                title = stringResource(R.string.setup_step4_title),
                description = stringResource(R.string.setup_step4_desc),
                isCompleted = hasNotificationPermission,
                completedLabel = stringResource(R.string.setup_step4_done),
                actionLabel = stringResource(R.string.setup_step4_action),
                onActionClick = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        hasNotificationPermission = true
                    }
                },
                color = SafeHerStatusSafe
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Bottom Buttons: Complete Setup & Skip
            Button(
                onClick = onCompleteSetupClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .semantics { contentDescription = "Complete safety setup" },
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = SafeHerPurplePrimary)
            ) {
                Text(
                    text = stringResource(R.string.setup_complete_button),
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            TextButton(
                onClick = onSkipSetupClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .semantics { contentDescription = "Skip setup for now" }
            ) {
                Text(
                    text = stringResource(R.string.setup_skip_button),
                    style = MaterialTheme.typography.labelMedium,
                    color = SafeHerTextSecondary,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    // Add Guardian Dialog
    if (uiState.showGuardianAddDialog) {
        AlertDialog(
            onDismissRequest = onDismissAddGuardianClick,
            title = {
                Text(
                    text = stringResource(R.string.setup_add_guardian_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column {
                    if (uiState.errorMessage != null) {
                        Text(
                            text = uiState.errorMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = SafeHerStatusEmergency,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }

                    OutlinedTextField(
                        value = uiState.guardianNameInput,
                        onValueChange = onGuardianNameChanged,
                        label = { Text(stringResource(R.string.setup_guardian_name)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = SafeHerPurplePrimary,
                            unfocusedBorderColor = SafeHerBorderLavender
                        )
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = uiState.guardianPhoneInput,
                        onValueChange = onGuardianPhoneChanged,
                        label = { Text(stringResource(R.string.setup_guardian_phone)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = SafeHerPurplePrimary,
                            unfocusedBorderColor = SafeHerBorderLavender
                        )
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = uiState.guardianRelationshipInput,
                        onValueChange = onGuardianRelationshipChanged,
                        label = { Text(stringResource(R.string.setup_guardian_relationship)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = SafeHerPurplePrimary,
                            unfocusedBorderColor = SafeHerBorderLavender
                        )
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = onSaveGuardianClick,
                    enabled = !uiState.isAddingGuardian,
                    colors = ButtonDefaults.buttonColors(containerColor = SafeHerPurplePrimary)
                ) {
                    if (uiState.isAddingGuardian) {
                        CircularProgressIndicator(
                            color = Color.White,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(16.dp)
                        )
                    } else {
                        Text(stringResource(R.string.setup_guardian_save))
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissAddGuardianClick) {
                    Text(stringResource(R.string.auth_cancel), color = SafeHerTextSecondary)
                }
            },
            containerColor = SafeHerSurfaceWhite,
            shape = RoundedCornerShape(20.dp)
        )
    }

    // Direct SMS Consent Review Dialog
    if (showSmsConsentDialog) {
        AlertDialog(
            onDismissRequest = { showSmsConsentDialog = false },
            title = {
                Text(
                    text = "Direct SMS & SOS Consent",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column {
                    Text(
                        text = "SafeHer can send automatic emergency SMS messages with your GPS coordinates directly to your designated guardians when an SOS is triggered or when an active journey overdue timer expires.",
                        style = MaterialTheme.typography.bodySmall,
                        color = SafeHerTextSecondary,
                        lineHeight = 18.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "• Messages are sent directly from your SIM card.\n• Standard carrier SMS rates apply.\n• Sending an SMS does not guarantee delivery due to carrier networks.\n• If permission is not granted, a manual messaging app option is always available.",
                        style = MaterialTheme.typography.bodySmall,
                        color = SafeHerTextPrimary,
                        lineHeight = 18.sp
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showSmsConsentDialog = false
                        onDirectSmsConsentChanged(true)
                        if (!hasSmsPermission) {
                            smsLauncher.launch(Manifest.permission.SEND_SMS)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SafeHerPurplePrimary)
                ) {
                    Text("I Consent & Enable SMS")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSmsConsentDialog = false }) {
                    Text("Not Now", color = SafeHerTextSecondary)
                }
            },
            containerColor = SafeHerSurfaceWhite,
            shape = RoundedCornerShape(20.dp)
        )
    }
}

@Composable
private fun SetupCard(
    icon: ImageVector,
    title: String,
    description: String,
    isCompleted: Boolean,
    completedLabel: String,
    actionLabel: String,
    onActionClick: () -> Unit,
    color: Color
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                1.dp,
                if (isCompleted) SafeHerStatusSafe.copy(alpha = 0.4f) else SafeHerBorderLavender,
                RoundedCornerShape(18.dp)
            ),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = SafeHerSurfaceWhite),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(if (isCompleted) SafeHerStatusSafe.copy(alpha = 0.12f) else color.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isCompleted) Icons.Default.Check else icon,
                        contentDescription = null,
                        tint = if (isCompleted) SafeHerStatusSafe else color,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = SafeHerTextPrimary
                    )
                }
                if (isCompleted) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(SafeHerStatusSafe.copy(alpha = 0.12f))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "Done",
                            style = MaterialTheme.typography.labelSmall,
                            color = SafeHerStatusSafe,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = SafeHerTextSecondary,
                lineHeight = 18.sp
            )

            Spacer(modifier = Modifier.height(12.dp))

            if (!isCompleted) {
                OutlinedButton(
                    onClick = onActionClick,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = color),
                    border = ButtonDefaults.outlinedButtonBorder.copy(
                        brush = androidx.compose.ui.graphics.SolidColor(color)
                    )
                ) {
                    Text(
                        text = actionLabel,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(vertical = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = SafeHerStatusSafe,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = completedLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = SafeHerStatusSafe,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun FirstLoginSetupScreenPreview() {
    SafeHerTheme {
        FirstLoginSetupScreen(
            uiState = WelcomeUiState(hasAddedGuardian = false),
            onCompleteSetupClick = {},
            onSkipSetupClick = {},
            onOpenAddGuardianClick = {},
            onDismissAddGuardianClick = {},
            onGuardianNameChanged = {},
            onGuardianPhoneChanged = {},
            onGuardianRelationshipChanged = {},
            onSaveGuardianClick = {},
            onDirectSmsConsentChanged = {}
        )
    }
}
