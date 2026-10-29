package com.safeher.app.ui.screens.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.safeher.app.ui.screens.home.ActiveDialog
import com.safeher.app.ui.theme.SafeHerPinkAccent
import com.safeher.app.ui.theme.SafeHerPinkContainer
import com.safeher.app.ui.theme.SafeHerPurpleDark
import com.safeher.app.ui.theme.SafeHerPurplePrimary
import com.safeher.app.ui.theme.SafeHerSosCrimson
import com.safeher.app.ui.theme.SafeHerSosTrack
import com.safeher.app.ui.theme.SafeHerSurfaceWhite
import com.safeher.app.ui.theme.SafeHerTextPrimary
import com.safeher.app.ui.theme.SafeHerTextSecondary

@Composable
fun FeaturePreviewDialog(
    activeDialog: ActiveDialog,
    onDismiss: () -> Unit
) {
    if (activeDialog == ActiveDialog.None ||
        activeDialog == ActiveDialog.EmergencySos ||
        activeDialog == ActiveDialog.DirectSos ||
        activeDialog == ActiveDialog.SosSettings ||
        activeDialog == ActiveDialog.AccessibleSosConfirmation
    ) return

    val content = when (activeDialog) {
        ActiveDialog.SosSimulation -> DialogContent(
            title = "🚨 SOS Simulation (UI Demo Only)",
            badge = "Non-Operational Demo • No Emergency Sent",
            description = "You completed the 2-second press-and-hold interaction.\n\nIMPORTANT CLARIFICATION:\n• No emergency services were called.\n• No GPS coordinates were transmitted.\n• No SMS alerts were dispatched.\n\nThis demonstration verifies the hold-to-activate gesture. Actual emergency dispatch mechanisms are scheduled for implementation and testing in Phase 3.",
            isEmergencyDemo = true
        )
        ActiveDialog.EmergencyCallPreview -> DialogContent(
            title = "Emergency Call (112) Preview",
            badge = "UI Demo • No Call Placed",
            description = "Demonstration of the Emergency 112 shortcut.\n\nNo actual telephone calls are initiated in this UI phase.\n\nIn Phase 3, this shortcut will be configured to launch the device's native phone dialer with emergency 112 pre-filled, requiring explicit user confirmation to avoid accidental dialing.",
            isEmergencyDemo = true
        )
        ActiveDialog.LiveTrackingPreview -> DialogContent(
            title = "Live Tracking (Phase 5 Planned Feature)",
            badge = "Planned Architecture",
            description = "Planned capability for encrypted real-time location sharing with selected guardians during active trips or emergency escalation.",
            isEmergencyDemo = false
        )
        ActiveDialog.TrustedContactsPreview -> DialogContent(
            title = "Trusted Contacts (Phase 4 Planned Feature)",
            badge = "Planned Architecture",
            description = "Planned local contact management using encrypted on-device database storage. No contact data has been configured yet.",
            isEmergencyDemo = false
        )
        ActiveDialog.SafeJourneyPreview -> DialogContent(
            title = "Safe Journey (Phase 5 Planned Feature)",
            badge = "Planned Architecture",
            description = "Planned travel timer and check-in escrow. Designed to trigger guardian notifications if arrival is delayed without confirmation.",
            isEmergencyDemo = false
        )
        ActiveDialog.ShareStatusPreview -> DialogContent(
            title = "Share Status (Phase 7 Planned Feature)",
            badge = "Planned Architecture",
            description = "Planned quick status broadcasts ('I\'m Safe', 'Heading Home', 'Need Pickup') through secure messaging channels.",
            isEmergencyDemo = false
        )
        ActiveDialog.CheckInPreview -> DialogContent(
            title = "Quick Check-In (Phase 4 Planned Feature)",
            badge = "Planned Architecture",
            description = "Planned one-tap check-in ping to notify configured emergency contacts that you have arrived safely.",
            isEmergencyDemo = false
        )
        ActiveDialog.SafetyTipsPreview -> DialogContent(
            title = "Safety Tips & Guidelines",
            badge = "Informational Resource",
            description = "Planned repository of offline-accessible personal safety guidance, legal protections, and certified emergency support helplines.",
            isEmergencyDemo = false
        )
        ActiveDialog.ProfileSettingsPreview -> DialogContent(
            title = "Profile & Settings (Phase 8 Planned Feature)",
            badge = "Planned Architecture",
            description = "Planned configuration options including camouflage app icon mode, biometric authentication, and hardware trigger settings.",
            isEmergencyDemo = false
        )
        ActiveDialog.EmergencySos,
        ActiveDialog.DirectSos,
        ActiveDialog.SosSettings,
        ActiveDialog.AccessibleSosConfirmation,
        ActiveDialog.None -> DialogContent("", "", "", false)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        containerColor = SafeHerSurfaceWhite,
        title = {
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(if (content.isEmergencyDemo) SafeHerSosTrack else SafeHerPinkContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (content.isEmergencyDemo) Icons.Default.WarningAmber else Icons.Default.Info,
                            contentDescription = null,
                            tint = if (content.isEmergencyDemo) SafeHerSosCrimson else SafeHerPurplePrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Text(
                        text = content.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = SafeHerTextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (content.isEmergencyDemo) SafeHerSosTrack else SafeHerPinkContainer)
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = content.badge,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (content.isEmergencyDemo) SafeHerSosCrimson else SafeHerPinkAccent,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        },
        text = {
            Text(
                text = content.description,
                style = MaterialTheme.typography.bodyMedium,
                color = SafeHerTextSecondary,
                lineHeight = 20.sp
            )
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (content.isEmergencyDemo) SafeHerPurpleDark else SafeHerPurplePrimary
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "I Understand",
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    )
}

private data class DialogContent(
    val title: String,
    val badge: String,
    val description: String,
    val isEmergencyDemo: Boolean
)
