package com.safeher.app.ui.screens.home.components

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
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.safeher.app.data.guardian.Guardian
import com.safeher.app.data.guardian.GuardianVerificationState
import com.safeher.app.data.remoteguardian.RemoteGuardian
import com.safeher.app.data.remotesos.RecipientDeliveryInfo
import com.safeher.app.data.remotesos.RemoteAlertDeliveryState
import com.safeher.app.data.sos.EmergencyMessageBuilder
import com.safeher.app.ui.screens.home.SosAlertChannel
import com.safeher.app.ui.screens.home.SosWorkflowState
import com.safeher.app.ui.theme.SafeHerBorderLavender
import com.safeher.app.ui.theme.SafeHerLavenderBackground
import com.safeher.app.ui.theme.SafeHerPinkContainer
import com.safeher.app.ui.theme.SafeHerPurpleContainer
import com.safeher.app.ui.theme.SafeHerPurpleDark
import com.safeher.app.ui.theme.SafeHerPurpleLight
import com.safeher.app.ui.theme.SafeHerPurplePrimary
import com.safeher.app.ui.theme.SafeHerSurfaceWhite
import com.safeher.app.ui.theme.SafeHerTextPrimary
import com.safeher.app.ui.theme.SafeHerTextSecondary
import com.safeher.app.ui.theme.SafeHerSosCrimson
import com.safeher.app.ui.theme.SafeHerSosRed
import com.safeher.app.ui.theme.SafeHerSosTrack
import com.safeher.app.ui.theme.SafeHerTextMuted
import com.safeher.app.ui.theme.SafeHerTextPrimary
import com.safeher.app.ui.theme.SafeHerTextSecondary

@Composable
fun EmergencySosDialog(
    workflow: SosWorkflowState,
    onDismiss: () -> Unit,
    onSelectChannel: (SosAlertChannel) -> Unit = {},
    // Phase 7A local SMS
    onToggleGuardian: (Long) -> Unit,
    onSelectAllGuardians: () -> Unit,
    onDeselectAllGuardians: () -> Unit,
    onOpenMessagingApp: () -> Unit,
    onCopyMessage: () -> Unit,
    // Phase 7B remote FCM
    onToggleRemoteGuardian: (String) -> Unit = {},
    onSelectAllRemoteGuardians: () -> Unit = {},
    onDeselectAllRemoteGuardians: () -> Unit = {},
    onDispatchRemoteAlert: () -> Unit = {},
    onNavigateToAccount: () -> Unit = {},
    onSimulateAcknowledgement: (String) -> Unit = {},
    // Shared
    onToggleIncludeLocation: (Boolean) -> Unit,
    onMessageChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (!workflow.isOpen) return

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        Card(
            modifier = modifier
                .fillMaxWidth(0.95f)
                .heightIn(max = 740.dp)
                .clip(RoundedCornerShape(24.dp)),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // 1. Header with Crimson Emergency Badge
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(SafeHerSosTrack),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Emergency Alert",
                            tint = SafeHerSosCrimson,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Emergency SOS",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = SafeHerSosCrimson
                        )
                        Text(
                            text = "Review alert details before dispatch",
                            style = MaterialTheme.typography.bodySmall,
                            color = SafeHerTextSecondary
                        )
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Cancel and Close",
                            tint = SafeHerTextSecondary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // 2. Channel Selector Tabs (Remote FCM Alert vs SMS Composer)
                TabRow(
                    selectedTabIndex = if (workflow.alertChannel == SosAlertChannel.REMOTE_FCM) 0 else 1,
                    containerColor = SafeHerPurpleContainer,
                    contentColor = SafeHerPurplePrimary,
                    modifier = Modifier.clip(RoundedCornerShape(12.dp))
                ) {
                    Tab(
                        selected = workflow.alertChannel == SosAlertChannel.REMOTE_FCM,
                        onClick = { onSelectChannel(SosAlertChannel.REMOTE_FCM) },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.NotificationsActive, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Remote Alert (FCM)", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    )
                    Tab(
                        selected = workflow.alertChannel == SosAlertChannel.SYSTEM_SMS,
                        onClick = { onSelectChannel(SosAlertChannel.SYSTEM_SMS) },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("SMS Composer", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 3. Channel Body: Remote Alert (Phase 7B) vs SMS Composer (Phase 7A)
                if (workflow.alertChannel == SosAlertChannel.REMOTE_FCM) {
                    RemoteAlertSection(
                        workflow = workflow,
                        onToggleRemoteGuardian = onToggleRemoteGuardian,
                        onSelectAllRemoteGuardians = onSelectAllRemoteGuardians,
                        onDeselectAllRemoteGuardians = onDeselectAllRemoteGuardians,
                        onToggleIncludeLocation = onToggleIncludeLocation,
                        onMessageChange = onMessageChange,
                        onDispatchRemoteAlert = onDispatchRemoteAlert,
                        onSwitchToSms = { onSelectChannel(SosAlertChannel.SYSTEM_SMS) },
                        onNavigateToAccount = onNavigateToAccount,
                        onSimulateAcknowledgement = onSimulateAcknowledgement,
                        onDismiss = onDismiss
                    )
                } else {
                    SmsComposerSection(
                        workflow = workflow,
                        onToggleGuardian = onToggleGuardian,
                        onSelectAllGuardians = onSelectAllGuardians,
                        onDeselectAllGuardians = onDeselectAllGuardians,
                        onToggleIncludeLocation = onToggleIncludeLocation,
                        onMessageChange = onMessageChange,
                        onCopyMessage = onCopyMessage,
                        onOpenMessagingApp = onOpenMessagingApp,
                        onDismiss = onDismiss
                    )
                }
            }
        }
    }
}

@Composable
private fun RemoteAlertSection(
    workflow: SosWorkflowState,
    onToggleRemoteGuardian: (String) -> Unit,
    onSelectAllRemoteGuardians: () -> Unit,
    onDeselectAllRemoteGuardians: () -> Unit,
    onToggleIncludeLocation: (Boolean) -> Unit,
    onMessageChange: (String) -> Unit,
    onDispatchRemoteAlert: () -> Unit,
    onSwitchToSms: () -> Unit,
    onNavigateToAccount: () -> Unit,
    onSimulateAcknowledgement: (String) -> Unit = {},
    onDismiss: () -> Unit
) {
    if (workflow.isRemoteDispatchSuccessful) {
        // Dispatched & Live Tracking Screen
        Column {
            Card(
                colors = CardDefaults.cardColors(containerColor = SafeHerPurpleContainer),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = SafeHerPurplePrimary, modifier = Modifier.size(32.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = if (workflow.isDemoMode) "Remote Alert Dispatched [SIMULATED]" else "Remote Alert Dispatched",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = SafeHerPurpleDark
                        )
                        Text(
                            text = if (workflow.isDemoMode) "Simulating delivery sequence locally on-device" else "Monitoring live recipient delivery status below",
                            fontSize = 12.sp,
                            color = SafeHerTextSecondary
                        )
                    }
                }
            }

            if (workflow.isDemoMode) {
                Spacer(modifier = Modifier.height(10.dp))
                Card(
                    colors = CardDefaults.cardColors(containerColor = SafeHerPurpleContainer.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, SafeHerPurpleLight)
                ) {
                    Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("🧪", fontSize = 18.sp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "DEMO SIMULATION: No real network, SMS, or FCM push notifications were sent. Delivery statuses are simulated locally on this device.",
                            fontSize = 11.sp,
                            color = SafeHerPurpleDark,
                            lineHeight = 15.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = if (workflow.isDemoMode) "Live Delivery Tracking [SIMULATED]" else "Live Delivery Tracking",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = SafeHerTextPrimary
            )
            Spacer(modifier = Modifier.height(8.dp))

            if (workflow.remoteDeliveryStatuses.isEmpty()) {
                Text(
                    text = "Awaiting delivery updates…",
                    fontSize = 12.sp,
                    color = SafeHerTextSecondary
                )
            } else {
                workflow.remoteDeliveryStatuses.forEach { statusInfo ->
                    DeliveryStatusRow(statusInfo = statusInfo, isDemoMode = workflow.isDemoMode)
                    Spacer(modifier = Modifier.height(6.dp))
                }
            }

            if (workflow.isDemoMode && workflow.remoteDeliveryStatuses.any { it.status != RemoteAlertDeliveryState.ACKNOWLEDGED }) {
                Spacer(modifier = Modifier.height(10.dp))
                Button(
                    onClick = { workflow.activeRemoteAlertId?.let { onSimulateAcknowledgement(it) } },
                    colors = ButtonDefaults.buttonColors(containerColor = SafeHerPurplePrimary),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth().height(44.dp)
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Simulate Guardian Acknowledgement", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Row(modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = onSwitchToSms,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f).height(48.dp)
                ) {
                    Text("Send Backup SMS", fontSize = 13.sp)
                }

                Spacer(modifier = Modifier.width(10.dp))

                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = SafeHerPurplePrimary),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f).height(48.dp)
                ) {
                    Text("Done")
                }
            }
        }
    } else {
        // Pre-Dispatch Form
        if (workflow.remoteGuardians.isEmpty()) {
            Card(
                colors = CardDefaults.cardColors(containerColor = SafeHerPurpleContainer.copy(alpha = 0.5f)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Info, contentDescription = null, tint = SafeHerPurplePrimary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "No Remote Guardians Linked",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = SafeHerPurpleDark
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "To send instant push notifications with delivery confirmations, link trusted contacts with SafeHer accounts in Account Settings.",
                        fontSize = 12.sp,
                        color = SafeHerTextSecondary,
                        lineHeight = 16.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Button(
                            onClick = {
                                onDismiss()
                                onNavigateToAccount()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = SafeHerPurplePrimary),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Account Settings", fontSize = 12.sp)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        OutlinedButton(
                            onClick = onSwitchToSms,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Use SMS Instead", fontSize = 12.sp)
                        }
                    }
                }
            }
        } else {
            // Recipient checklist
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Select Remote Guardians",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = SafeHerTextPrimary
                )
                Row {
                    TextButton(onClick = onSelectAllRemoteGuardians) {
                        Text("All", fontSize = 12.sp, color = SafeHerPurplePrimary)
                    }
                    TextButton(onClick = onDeselectAllRemoteGuardians) {
                        Text("None", fontSize = 12.sp, color = SafeHerTextSecondary)
                    }
                }
            }

            workflow.remoteGuardians.filter { it.isActive }.forEach { guardian ->
                val isChecked = workflow.selectedRemoteGuardianUids.contains(guardian.guardianUid)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onToggleRemoteGuardian(guardian.guardianUid) }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = isChecked,
                        onCheckedChange = { onToggleRemoteGuardian(guardian.guardianUid) },
                        colors = CheckboxDefaults.colors(checkedColor = SafeHerPurplePrimary)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = guardian.guardianDisplayName,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                            color = SafeHerTextPrimary
                        )
                        if (guardian.guardianEmail.isNotBlank()) {
                            Text(
                                text = guardian.guardianEmail,
                                fontSize = 12.sp,
                                color = SafeHerTextSecondary
                            )
                        }
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(SafeHerPurpleContainer)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text("FCM LINK", fontSize = 10.sp, color = SafeHerPurplePrimary, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Location sharing toggle
            LocationToggleCard(
                includeLocation = workflow.includeLocation,
                locationData = workflow.locationData,
                isAcquiring = workflow.isAcquiringLocation,
                locationError = workflow.locationError,
                onToggle = onToggleIncludeLocation
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Editable message field
            OutlinedTextField(
                value = workflow.editableMessage,
                onValueChange = onMessageChange,
                label = { Text("Emergency Message Body") },
                modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp),
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Pre-dispatch notice
            Card(
                colors = CardDefaults.cardColors(containerColor = SafeHerPurpleContainer.copy(alpha = 0.4f)),
                shape = RoundedCornerShape(10.dp)
            ) {
                Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.Top) {
                    Icon(Icons.Default.Info, contentDescription = null, tint = SafeHerPurplePrimary, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Notice: Dispatched through SafeHer's trusted server. FCM push alerts will be sent to registered guardian devices. Delivery statuses update live.",
                        fontSize = 11.sp,
                        color = SafeHerPurpleDark,
                        lineHeight = 15.sp
                    )
                }
            }

            if (workflow.remoteDispatchError != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = workflow.remoteDispatchError,
                    color = SafeHerSosCrimson,
                    fontSize = 12.sp
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = onDispatchRemoteAlert,
                enabled = workflow.hasEligibleRemoteRecipients && !workflow.isDispatchingRemote,
                colors = ButtonDefaults.buttonColors(containerColor = SafeHerSosCrimson),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                if (workflow.isDispatchingRemote) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Dispatching Remote SOS…")
                } else {
                    Icon(Icons.Default.NotificationsActive, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Dispatch Remote SOS", fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedButton(
                onClick = onSwitchToSms,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().height(44.dp)
            ) {
                Text("Switch to SMS Composer", fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun SmsComposerSection(
    workflow: SosWorkflowState,
    onToggleGuardian: (Long) -> Unit,
    onSelectAllGuardians: () -> Unit,
    onDeselectAllGuardians: () -> Unit,
    onToggleIncludeLocation: (Boolean) -> Unit,
    onMessageChange: (String) -> Unit,
    onCopyMessage: () -> Unit,
    onOpenMessagingApp: () -> Unit,
    onDismiss: () -> Unit
) {
    // Phase 7A Local SMS content
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Recipients (${workflow.selectedGuardians.size}/${workflow.guardians.size})",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = SafeHerTextPrimary
        )
        Row {
            TextButton(onClick = onSelectAllGuardians) {
                Text("Select All", fontSize = 12.sp, color = SafeHerPurplePrimary)
            }
            TextButton(onClick = onDeselectAllGuardians) {
                Text("Clear", fontSize = 12.sp, color = SafeHerTextSecondary)
            }
        }
    }

    if (workflow.guardians.isEmpty()) {
        Card(
            colors = CardDefaults.cardColors(containerColor = SafeHerSosTrack),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
        ) {
            Text(
                text = "No trusted guardians stored on device. Add guardians in the Contacts tab.",
                fontSize = 12.sp,
                color = SafeHerSosCrimson,
                modifier = Modifier.padding(12.dp)
            )
        }
    } else {
        workflow.guardians.forEach { guardian ->
            val isChecked = workflow.selectedGuardianIds.contains(guardian.id)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggleGuardian(guardian.id) }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = isChecked,
                    onCheckedChange = { onToggleGuardian(guardian.id) },
                    colors = CheckboxDefaults.colors(checkedColor = SafeHerPurplePrimary)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = guardian.name,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        color = SafeHerTextPrimary
                    )
                    Text(
                        text = guardian.phoneNumber,
                        fontSize = 12.sp,
                        color = SafeHerTextSecondary
                    )
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (guardian.isVerified) SafeHerPurpleContainer else SafeHerSosTrack)
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = if (guardian.isVerified) "VERIFIED" else "UNVERIFIED",
                        fontSize = 10.sp,
                        color = if (guardian.isVerified) SafeHerPurplePrimary else SafeHerSosCrimson,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }

    Spacer(modifier = Modifier.height(12.dp))

    // Location toggle
    LocationToggleCard(
        includeLocation = workflow.includeLocation,
        locationData = workflow.locationData,
        isAcquiring = workflow.isAcquiringLocation,
        locationError = workflow.locationError,
        onToggle = onToggleIncludeLocation
    )

    Spacer(modifier = Modifier.height(12.dp))

    // Editable message field
    OutlinedTextField(
        value = workflow.editableMessage,
        onValueChange = onMessageChange,
        label = { Text("Emergency Message Body") },
        modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp),
        shape = RoundedCornerShape(12.dp)
    )

    Spacer(modifier = Modifier.height(12.dp))

    // Pre-handoff notice
    Card(
        colors = CardDefaults.cardColors(containerColor = SafeHerSosTrack),
        shape = RoundedCornerShape(10.dp)
    ) {
        Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.Top) {
            Icon(Icons.Default.Info, contentDescription = null, tint = SafeHerSosCrimson, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "Notice: Tapping below opens your system messaging app. You must press Send in your SMS app to dispatch. SafeHer cannot send SMS automatically or guarantee delivery.",
                fontSize = 11.sp,
                color = SafeHerSosCrimson,
                lineHeight = 15.sp
            )
        }
    }

    if (workflow.handoffError != null) {
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = workflow.handoffError,
            color = SafeHerSosCrimson,
            fontSize = 12.sp
        )
    }

    Spacer(modifier = Modifier.height(16.dp))

    Button(
        onClick = onOpenMessagingApp,
        enabled = workflow.hasEligibleRecipients && !workflow.isLaunchingComposer,
        colors = ButtonDefaults.buttonColors(containerColor = SafeHerSosCrimson),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().height(48.dp)
    ) {
        if (workflow.isLaunchingComposer) {
            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Preparing SMS App…")
        } else {
            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Open Messaging App", fontWeight = FontWeight.Bold)
        }
    }

    Spacer(modifier = Modifier.height(8.dp))

    Row(modifier = Modifier.fillMaxWidth()) {
        OutlinedButton(
            onClick = onCopyMessage,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.weight(1f).height(44.dp)
        ) {
            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(if (workflow.isCopiedToClipboard) "Copied!" else "Copy Text", fontSize = 13.sp)
        }
        Spacer(modifier = Modifier.width(8.dp))
        TextButton(
            onClick = onDismiss,
            modifier = Modifier.weight(1f).height(44.dp)
        ) {
            Text("Cancel SOS", color = SafeHerTextSecondary)
        }
    }
}

@Composable
private fun LocationToggleCard(
    includeLocation: Boolean,
    locationData: com.safeher.app.data.location.LocationData?,
    isAcquiring: Boolean,
    locationError: String?,
    onToggle: (Boolean) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = SafeHerLavenderBackground),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.LocationOn, contentDescription = null, tint = SafeHerPurplePrimary)
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Include Current Location",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    color = SafeHerTextPrimary
                )
                if (isAcquiring) {
                    Text("Acquiring GPS fix…", fontSize = 11.sp, color = SafeHerTextSecondary)
                } else if (locationData != null && !locationData.isStale) {
                    Text("GPS fix active (±${locationData.accuracyMeters.toInt()}m)", fontSize = 11.sp, color = SafeHerPurplePrimary)
                } else {
                    Text(locationError ?: "No location fix", fontSize = 11.sp, color = SafeHerTextSecondary)
                }
            }
            Checkbox(
                checked = includeLocation,
                onCheckedChange = onToggle,
                colors = CheckboxDefaults.colors(checkedColor = SafeHerPurplePrimary)
            )
        }
    }
}

@Composable
private fun DeliveryStatusRow(statusInfo: RecipientDeliveryInfo, isDemoMode: Boolean = false) {
    Card(
        colors = CardDefaults.cardColors(containerColor = SafeHerSurfaceWhite),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, SafeHerPurpleLight.copy(alpha = 0.3f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (isDemoMode) "Recipient: [DEMO] ${statusInfo.guardianName}" else "Recipient: ${statusInfo.guardianName}",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    color = SafeHerTextPrimary
                )
                if (statusInfo.failureReason != null) {
                    Text(
                        text = statusInfo.failureReason,
                        fontSize = 11.sp,
                        color = SafeHerSosCrimson
                    )
                }
            }

            val badgeColor = when (statusInfo.status) {
                RemoteAlertDeliveryState.ACKNOWLEDGED -> Color(0xFF10B981)
                RemoteAlertDeliveryState.DEVICE_RECEIVED -> SafeHerPurplePrimary
                RemoteAlertDeliveryState.FCM_ACCEPTED,
                RemoteAlertDeliveryState.QUEUED -> SafeHerPurpleDark
                RemoteAlertDeliveryState.FAILED -> SafeHerSosCrimson
                else -> SafeHerTextSecondary
            }

            val label = if (isDemoMode) {
                when (statusInfo.status) {
                    RemoteAlertDeliveryState.CREATED -> "[SIMULATED] Created"
                    RemoteAlertDeliveryState.QUEUED -> "[SIMULATED] Queued"
                    RemoteAlertDeliveryState.FCM_ACCEPTED -> "[SIMULATED] FCM Accepted"
                    RemoteAlertDeliveryState.DEVICE_RECEIVED -> "[SIMULATED] App Receipt Simulated"
                    RemoteAlertDeliveryState.ACKNOWLEDGED -> "[SIMULATED] Acknowledgement Simulated"
                    RemoteAlertDeliveryState.FAILED -> "[SIMULATED] Failed"
                    RemoteAlertDeliveryState.UNKNOWN -> "[SIMULATED] Unconfirmed"
                }
            } else {
                statusInfo.status.displayLabel
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(badgeColor.copy(alpha = 0.15f))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = label,
                    color = badgeColor,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
