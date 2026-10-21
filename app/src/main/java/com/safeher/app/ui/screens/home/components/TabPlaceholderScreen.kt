package com.safeher.app.ui.screens.home.components

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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.safeher.app.ui.screens.home.HomeTab
import com.safeher.app.ui.theme.SafeHerBorderLavender
import com.safeher.app.ui.theme.SafeHerPinkAccent
import com.safeher.app.ui.theme.SafeHerPinkContainer
import com.safeher.app.ui.theme.SafeHerPurpleContainer
import com.safeher.app.ui.theme.SafeHerPurpleDark
import com.safeher.app.ui.theme.SafeHerPurplePrimary
import com.safeher.app.ui.theme.SafeHerSurfaceWhite
import com.safeher.app.ui.theme.SafeHerTextMuted
import com.safeher.app.ui.theme.SafeHerTextPrimary
import com.safeher.app.ui.theme.SafeHerTextSecondary

@Composable
fun TabPlaceholderScreen(
    tab: HomeTab,
    onBackToHomeClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tabMeta = when (tab) {
        HomeTab.MAP -> TabMeta(
            title = "Live Safety Map",
            phaseTag = "Phase 5 • Live Location Architecture",
            summary = "Real-time geographical safety map showing verified safe hubs, emergency stations, and battery-conscious live location sharing.",
            icon = Icons.Default.Map
        )
        HomeTab.JOURNEYS -> TabMeta(
            title = "Safe Journeys & Trip Timers",
            phaseTag = "Phase 5 • Travel Protection",
            summary = "Plan trips with estimated travel durations, periodic check-in prompts, and automated escalation to your trusted circle if arrival is delayed.",
            icon = Icons.AutoMirrored.Filled.DirectionsWalk
        )
        HomeTab.CONTACTS -> TabMeta(
            title = "Trusted Guardians & Circle",
            phaseTag = "Phase 4 • Guardian Management",
            summary = "Configure emergency contacts stored locally with SQLCipher database encryption. No remote telemetry or contact syncing occurs in Phase 2.",
            icon = Icons.Default.People
        )
        HomeTab.MORE -> TabMeta(
            title = "Safety Tools & Settings",
            phaseTag = "Phases 6 & 8 • Security & Utilities",
            summary = "Emergency siren deterrent, incoming fake call simulator, background audio incident recorder, and privacy/camouflage settings.",
            icon = Icons.Default.MoreHoriz
        )
        HomeTab.HOME -> TabMeta("", "", "", Icons.Default.MoreHoriz)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Top Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(SafeHerPurpleContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = tabMeta.icon,
                        contentDescription = null,
                        tint = SafeHerPurplePrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                Text(
                    text = tabMeta.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = SafeHerTextPrimary,
                    fontWeight = FontWeight.Bold
                )
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(SafeHerPinkContainer)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "Preview",
                    style = MaterialTheme.typography.labelSmall,
                    color = SafeHerPinkAccent,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Phase & Readiness Overview Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, SafeHerBorderLavender, RoundedCornerShape(20.dp)),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = SafeHerSurfaceWhite),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(SafeHerPurpleContainer)
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = tabMeta.phaseTag,
                        style = MaterialTheme.typography.labelSmall,
                        color = SafeHerPurpleDark,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = tabMeta.summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = SafeHerTextSecondary,
                    lineHeight = 20.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Tab-specific structured preview content (not empty!)
        when (tab) {
            HomeTab.CONTACTS -> ContactsTabPreviewContent()
            HomeTab.MAP -> MapTabPreviewContent()
            HomeTab.JOURNEYS -> JourneysTabPreviewContent()
            HomeTab.MORE -> MoreTabPreviewContent()
            HomeTab.HOME -> {}
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Back to Home Dashboard Action
        Button(
            onClick = onBackToHomeClick,
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = SafeHerPurplePrimary),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
        ) {
            Text(
                text = "Back to Home Dashboard",
                color = Color.White,
                fontWeight = FontWeight.SemiBold
            )
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun ContactsTabPreviewContent() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = "Emergency Guardians Architecture (Phase 4)",
            style = MaterialTheme.typography.titleSmall,
            color = SafeHerTextPrimary,
            fontWeight = FontWeight.Bold
        )

        listOf(
            "Primary Guardian 1" to "Unconfigured • Will be loaded from local Room DB",
            "Primary Guardian 2" to "Unconfigured • Will be loaded from local Room DB",
            "Emergency Alternate" to "Unconfigured • Will receive fallback SMS in Phase 3"
        ).forEach { (slot, desc) ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, SafeHerBorderLavender, RoundedCornerShape(16.dp)),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = SafeHerSurfaceWhite)
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(SafeHerPurpleContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PersonAdd,
                            contentDescription = null,
                            tint = SafeHerPurplePrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Text(
                            text = slot,
                            style = MaterialTheme.typography.titleSmall,
                            color = SafeHerTextPrimary,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp
                        )
                        Text(
                            text = desc,
                            style = MaterialTheme.typography.bodySmall,
                            color = SafeHerTextMuted,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MapTabPreviewContent() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = "Planned Map Capabilities (Phase 5)",
            style = MaterialTheme.typography.titleSmall,
            color = SafeHerTextPrimary,
            fontWeight = FontWeight.Bold
        )

        listOf(
            "Live Path Tracing" to "Encrypted, battery-optimized location streaming during active SOS or trips",
            "Verified Safe Hubs" to "Directory of 24/7 hospitals, verified shelters, and police kiosks",
            "Offline Zone Caching" to "Cached offline emergency maps when cellular data is disconnected"
        ).forEach { (feature, details) ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, SafeHerBorderLavender, RoundedCornerShape(16.dp)),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = SafeHerSurfaceWhite)
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(SafeHerPinkContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = null,
                            tint = SafeHerPinkAccent,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Text(
                            text = feature,
                            style = MaterialTheme.typography.titleSmall,
                            color = SafeHerTextPrimary,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp
                        )
                        Text(
                            text = details,
                            style = MaterialTheme.typography.bodySmall,
                            color = SafeHerTextMuted,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun JourneysTabPreviewContent() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = "Journey Protection Workflow (Phase 5)",
            style = MaterialTheme.typography.titleSmall,
            color = SafeHerTextPrimary,
            fontWeight = FontWeight.Bold
        )

        listOf(
            "Step 1: Set Destination & ETA" to "Choose expected travel time with adaptive route tracking",
            "Step 2: Check-In Countdown" to "Prompts for quick 1-tap confirmation upon arriving safely",
            "Step 3: Automated Escalation" to "Alerts designated guardians via SMS if check-in is missed"
        ).forEach { (step, desc) ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, SafeHerBorderLavender, RoundedCornerShape(16.dp)),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = SafeHerSurfaceWhite)
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(SafeHerPurpleContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Timer,
                            contentDescription = null,
                            tint = SafeHerPurplePrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Text(
                            text = step,
                            style = MaterialTheme.typography.titleSmall,
                            color = SafeHerTextPrimary,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp
                        )
                        Text(
                            text = desc,
                            style = MaterialTheme.typography.bodySmall,
                            color = SafeHerTextMuted,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MoreTabPreviewContent() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = "Planned Safety Utilities (Phases 6 & 8)",
            style = MaterialTheme.typography.titleSmall,
            color = SafeHerTextPrimary,
            fontWeight = FontWeight.Bold
        )

        listOf(
            "Audible Deterrent Siren" to "High-decibel alert with screen strobe (Phase 6)",
            "Realistic Fake Call Simulator" to "Custom caller name & voice prompts for uncomfortable situations (Phase 6)",
            "Discreet Audio Recorder" to "Encrypted on-device evidentiary recording upon SOS (Phase 6)",
            "Camouflage Launcher Mode" to "Disguise app as a calculator or note utility (Phase 8)",
            "Biometric & Duress PIN" to "Hardware-backed Keystore security and false-cancel protection (Phase 8)"
        ).forEach { (tool, note) ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, SafeHerBorderLavender, RoundedCornerShape(16.dp)),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = SafeHerSurfaceWhite)
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(SafeHerPinkContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            tint = SafeHerPinkAccent,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Text(
                            text = tool,
                            style = MaterialTheme.typography.titleSmall,
                            color = SafeHerTextPrimary,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp
                        )
                        Text(
                            text = note,
                            style = MaterialTheme.typography.bodySmall,
                            color = SafeHerTextMuted,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }
    }
}

private data class TabMeta(
    val title: String,
    val phaseTag: String,
    val summary: String,
    val icon: ImageVector
)
