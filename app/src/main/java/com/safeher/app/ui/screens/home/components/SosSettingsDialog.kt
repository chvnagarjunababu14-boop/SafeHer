package com.safeher.app.ui.screens.home.components

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Message
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.safeher.app.data.guardian.Guardian
import com.safeher.app.data.sos.SosPreferences
import com.safeher.app.ui.theme.SafeHerBorderLavender
import com.safeher.app.ui.theme.SafeHerLavenderBackground
import com.safeher.app.ui.theme.SafeHerPinkAccent
import com.safeher.app.ui.theme.SafeHerPinkContainer
import com.safeher.app.ui.theme.SafeHerPurpleContainer
import com.safeher.app.ui.theme.SafeHerPurpleDark
import com.safeher.app.ui.theme.SafeHerPurpleLight
import com.safeher.app.ui.theme.SafeHerPurplePrimary
import com.safeher.app.ui.theme.SafeHerSosCrimson
import com.safeher.app.ui.theme.SafeHerSosRed
import com.safeher.app.ui.theme.SafeHerSurfaceWhite
import com.safeher.app.ui.theme.SafeHerTextMuted
import com.safeher.app.ui.theme.SafeHerTextPrimary
import com.safeher.app.ui.theme.SafeHerTextSecondary

/**
 * Settings dialog for configuring user-authorized Direct Automatic SOS SMS.
 *
 * Implements strict consent, permission requests, recipient management,
 * custom message customization, and location toggle.
 */
@Composable
fun SosSettingsDialog(
    isOpen: Boolean,
    preferences: SosPreferences,
    guardians: List<Guardian>,
    onDismiss: () -> Unit,
    onConsentChanged: (Boolean) -> Unit,
    onDirectSmsEnabledChanged: (Boolean) -> Unit,
    onCustomMessageChanged: (String) -> Unit,
    onLocationSharingChanged: (Boolean) -> Unit,
    onCountdownChanged: (Int) -> Unit,
    onToggleGuardianSos: (guardianId: Long, isEnabled: Boolean) -> Unit,
    onBatteryAlertsConsentChanged: (Boolean) -> Unit = {},
    onBatteryAlertsEnabledChanged: (Boolean) -> Unit = {},
    onBatteryAlertLocationChanged: (Boolean) -> Unit = {},
    onToggleGuardianBatteryAlert: (guardianId: Long, isEnabled: Boolean) -> Unit = { _, _ -> }
) {
    if (!isOpen) return

    val context = LocalContext.current
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.SEND_SMS
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission = granted
        if (granted) {
            onConsentChanged(true)
            onDirectSmsEnabledChanged(true)
        } else {
            onDirectSmsEnabledChanged(false)
        }
    }

    var editableMessage by remember(preferences.customSosMessage) {
        mutableStateOf(preferences.customSosMessage)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = SafeHerSurfaceWhite),
            border = BorderStroke(1.dp, SafeHerBorderLavender),
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .heightIn(max = 680.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(SafeHerPurpleContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Security,
                                contentDescription = null,
                                tint = SafeHerPurplePrimary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Emergency SOS Settings",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = SafeHerTextPrimary
                            )
                            Text(
                                text = "Configure automatic emergency alerts",
                                style = MaterialTheme.typography.labelSmall,
                                color = SafeHerTextSecondary
                            )
                        }
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = SafeHerTextSecondary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Card 1: Consent & Direct SMS Toggle
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (preferences.isDirectSmsEnabled) SafeHerPurpleContainer else SafeHerLavenderBackground
                    ),
                    border = BorderStroke(1.dp, SafeHerPurpleLight),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Automatic Direct SMS",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = SafeHerPurpleDark
                                )
                                Text(
                                    text = "Send SMS directly in emergencies without opening messaging app",
                                    fontSize = 12.sp,
                                    color = SafeHerTextSecondary
                                )
                            }
                            Switch(
                                checked = preferences.isDirectSmsEnabled && hasPermission,
                                onCheckedChange = { enable ->
                                    if (enable) {
                                        if (!hasPermission) {
                                            permissionLauncher.launch(Manifest.permission.SEND_SMS)
                                        } else {
                                            onConsentChanged(true)
                                            onDirectSmsEnabledChanged(true)
                                        }
                                    } else {
                                        onDirectSmsEnabledChanged(false)
                                    }
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = SafeHerPurplePrimary
                                )
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Permission status notice
                        if (!hasPermission) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(SafeHerPinkContainer, RoundedCornerShape(8.dp))
                                    .padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = SafeHerSosCrimson,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "SMS Permission required for automatic sending.",
                                    fontSize = 12.sp,
                                    color = SafeHerSosCrimson,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedButton(
                                    onClick = { permissionLauncher.launch(Manifest.permission.SEND_SMS) },
                                    shape = RoundedCornerShape(8.dp),
                                    border = BorderStroke(1.dp, SafeHerSosCrimson),
                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                        horizontal = 8.dp,
                                        vertical = 2.dp
                                    )
                                ) {
                                    Text("Grant", fontSize = 11.sp, color = SafeHerSosCrimson)
                                }
                            }
                        } else {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFFE8F5E9), RoundedCornerShape(8.dp))
                                    .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = Color(0xFF2E7D32),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "SMS Permission granted. Direct SOS is ready.",
                                    fontSize = 12.sp,
                                    color = Color(0xFF2E7D32),
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Explicit Consent Checkbox
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    val newConsent = !preferences.isDirectSmsConsentGiven
                                    onConsentChanged(newConsent)
                                }
                        ) {
                            Checkbox(
                                checked = preferences.isDirectSmsConsentGiven,
                                onCheckedChange = { onConsentChanged(it) },
                                colors = CheckboxDefaults.colors(checkedColor = SafeHerPurplePrimary)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "I authorize SafeHer to send automated SMS to my selected guardians during an emergency. Standard carrier messaging rates may apply.",
                                fontSize = 11.sp,
                                color = SafeHerTextSecondary,
                                lineHeight = 15.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Card 2: SOS Recipients Management
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = SafeHerSurfaceWhite),
                    border = BorderStroke(1.dp, SafeHerBorderLavender),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.People,
                                contentDescription = null,
                                tint = SafeHerPurplePrimary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Eligible SOS Guardians",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = SafeHerTextPrimary
                            )
                        }
                        Text(
                            text = "Only enabled guardians will receive direct emergency SMS.",
                            fontSize = 11.sp,
                            color = SafeHerTextSecondary
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        if (guardians.isEmpty()) {
                            Text(
                                text = "No guardians registered. Add guardians in Contacts tab first.",
                                fontSize = 12.sp,
                                color = SafeHerTextMuted,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        } else {
                            guardians.forEach { guardian ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = guardian.name,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 13.sp,
                                            color = SafeHerTextPrimary
                                        )
                                        Text(
                                            text = guardian.phoneNumber,
                                            fontSize = 11.sp,
                                            color = SafeHerTextSecondary
                                        )
                                    }
                                    Checkbox(
                                        checked = guardian.isSosEnabled,
                                        onCheckedChange = { checked ->
                                            onToggleGuardianSos(guardian.id, checked)
                                        },
                                        colors = CheckboxDefaults.colors(checkedColor = SafeHerPurplePrimary)
                                    )
                                }
                                HorizontalDivider(color = SafeHerLavenderBackground, thickness = 1.dp)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Card 3: Emergency Message Customization
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = SafeHerSurfaceWhite),
                    border = BorderStroke(1.dp, SafeHerBorderLavender),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Message,
                                contentDescription = null,
                                tint = SafeHerPurplePrimary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Emergency Message",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = SafeHerTextPrimary
                            )
                        }
                        Text(
                            text = "Pre-configured text dispatched automatically in emergencies.",
                            fontSize = 11.sp,
                            color = SafeHerTextSecondary
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        OutlinedTextField(
                            value = editableMessage,
                            onValueChange = {
                                editableMessage = it
                                onCustomMessageChanged(it)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            textStyle = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = SafeHerPurplePrimary,
                                unfocusedBorderColor = SafeHerBorderLavender
                            ),
                            maxLines = 4
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // Reset to default button
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            OutlinedButton(
                                onClick = {
                                    editableMessage = SosPreferences.DEFAULT_SOS_MESSAGE
                                    onCustomMessageChanged(SosPreferences.DEFAULT_SOS_MESSAGE)
                                },
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                    horizontal = 10.dp,
                                    vertical = 2.dp
                                )
                            ) {
                                Text("Reset to default", fontSize = 11.sp, color = SafeHerPurplePrimary)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Card 4: Location Sharing & Countdown
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = SafeHerSurfaceWhite),
                    border = BorderStroke(1.dp, SafeHerBorderLavender),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.LocationOn,
                                    contentDescription = null,
                                    tint = SafeHerPurplePrimary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = "Include Live GPS Location",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = SafeHerTextPrimary
                                    )
                                    Text(
                                        text = "Appends Google Maps link if fresh fix is available",
                                        fontSize = 11.sp,
                                        color = SafeHerTextSecondary
                                    )
                                }
                            }
                            Switch(
                                checked = preferences.isLocationSharingEnabled,
                                onCheckedChange = { onLocationSharingChanged(it) },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = SafeHerPurplePrimary
                                )
                            )
                        }

                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 10.dp),
                            color = SafeHerLavenderBackground
                        )

                        // Countdown selector
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "Pre-dispatch Cancel Window",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = SafeHerTextPrimary
                                )
                                Text(
                                    text = "Cancellable countdown before SMS transmission",
                                    fontSize = 11.sp,
                                    color = SafeHerTextSecondary
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                listOf(2, 3, 5).forEach { seconds ->
                                    val isSelected = preferences.countdownDurationSeconds == seconds
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(
                                                if (isSelected) SafeHerPurplePrimary else SafeHerLavenderBackground
                                            )
                                            .clickable { onCountdownChanged(seconds) }
                                            .padding(horizontal = 10.dp, vertical = 6.dp)
                                    ) {
                                        Text(
                                            text = "${seconds}s",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp,
                                            color = if (isSelected) Color.White else SafeHerTextPrimary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Card 5: Battery Safety Alerts (defaults OFF)
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = SafeHerSurfaceWhite),
                    border = BorderStroke(1.dp, SafeHerBorderLavender),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "Battery Safety Alerts",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = SafeHerTextPrimary
                        )
                        Text(
                            text = "Optional SMS when battery drops to ${preferences.batteryLowThresholdPercent}% or ${preferences.batteryCriticalThresholdPercent}%. Defaults off. Not an SOS. SMS may incur carrier charges. Delivery is not guaranteed, especially near power-off.",
                            fontSize = 11.sp,
                            color = SafeHerTextSecondary
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Enable battery alerts",
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp,
                                    color = SafeHerTextPrimary
                                )
                                Text(
                                    text = "Requires explicit consent and SEND_SMS permission",
                                    fontSize = 11.sp,
                                    color = SafeHerTextSecondary
                                )
                            }
                            Switch(
                                checked = preferences.isBatteryAlertsEnabled && hasPermission,
                                onCheckedChange = { enable ->
                                    if (enable) {
                                        if (!hasPermission) {
                                            permissionLauncher.launch(Manifest.permission.SEND_SMS)
                                        } else {
                                            onBatteryAlertsConsentChanged(true)
                                            onBatteryAlertsEnabledChanged(true)
                                        }
                                    } else {
                                        onBatteryAlertsEnabledChanged(false)
                                    }
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = SafeHerPurplePrimary
                                )
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Include location in battery alerts",
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp,
                                    color = SafeHerTextPrimary
                                )
                                Text(
                                    text = "Separate consent; uses last available fix only — no continuous tracking",
                                    fontSize = 11.sp,
                                    color = SafeHerTextSecondary
                                )
                            }
                            Switch(
                                checked = preferences.isBatteryAlertLocationEnabled,
                                onCheckedChange = onBatteryAlertLocationChanged,
                                enabled = preferences.isBatteryAlertsEnabled,
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = SafeHerPurplePrimary
                                )
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Text(
                            text = "Battery alert recipients (opt-in per contact)",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp,
                            color = SafeHerTextPrimary
                        )
                        Text(
                            text = "SOS recipients are not auto-included. Enable each guardian below.",
                            fontSize = 11.sp,
                            color = SafeHerTextSecondary
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        if (guardians.isEmpty()) {
                            Text(
                                text = "No local guardians yet. Add contacts first.",
                                fontSize = 12.sp,
                                color = SafeHerTextMuted
                            )
                        } else {
                            guardians.forEach { guardian ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = guardian.name,
                                        fontSize = 13.sp,
                                        color = SafeHerTextPrimary,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Checkbox(
                                        checked = guardian.isBatteryAlertEnabled,
                                        onCheckedChange = { checked ->
                                            onToggleGuardianBatteryAlert(guardian.id, checked)
                                        },
                                        enabled = preferences.isBatteryAlertsEnabled,
                                        colors = CheckboxDefaults.colors(checkedColor = SafeHerPurplePrimary)
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Done button
                Button(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = SafeHerPurplePrimary)
                ) {
                    Text(
                        text = "Save & Close",
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }
    }
}
