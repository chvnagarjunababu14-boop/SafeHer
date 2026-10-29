package com.safeher.app.ui.screens.home.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.safeher.app.data.sos.DirectSosState
import com.safeher.app.data.sos.RecipientDispatchState
import com.safeher.app.data.sos.RecipientDispatchStatus
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
import com.safeher.app.ui.theme.SafeHerSosTrack
import com.safeher.app.ui.theme.SafeHerSurfaceWhite
import com.safeher.app.ui.theme.SafeHerTextMuted
import com.safeher.app.ui.theme.SafeHerTextPrimary
import com.safeher.app.ui.theme.SafeHerTextSecondary

/**
 * Dialog displaying active Direct Automatic SOS progress:
 * - Pre-dispatch cancellable countdown
 * - Real-time independent recipient status tracking
 * - Honest delivery/submission confirmations
 * - Instant fallback to manual SMS composer if needed
 */
@Composable
fun DirectSosDialog(
    state: DirectSosState,
    onCancelCountdown: () -> Unit,
    onSendNow: () -> Unit,
    onOpenManualSmsFallback: () -> Unit,
    onOpenSosSettings: () -> Unit,
    onDismiss: () -> Unit
) {
    if (state is DirectSosState.Idle) return

    Dialog(
        onDismissRequest = {
            if (state is DirectSosState.Countdown) {
                onCancelCountdown()
            } else if (state is DirectSosState.Completed || state is DirectSosState.Error) {
                onDismiss()
            }
        },
        properties = DialogProperties(
            dismissOnBackPress = state !is DirectSosState.Dispatching,
            dismissOnClickOutside = state !is DirectSosState.Dispatching,
            usePlatformDefaultWidth = false
        )
    ) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = SafeHerSurfaceWhite),
            border = BorderStroke(
                1.dp,
                if (state is DirectSosState.Countdown) SafeHerSosCrimson else SafeHerBorderLavender
            ),
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .heightIn(max = 660.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(22.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                when (state) {
                    is DirectSosState.Countdown -> {
                        CountdownContent(
                            secondsRemaining = state.secondsRemaining,
                            totalSeconds = state.totalSeconds,
                            onCancel = onCancelCountdown,
                            onSendNow = onSendNow
                        )
                    }
                    is DirectSosState.Dispatching -> {
                        DispatchingContent(
                            statuses = state.statuses,
                            locationAttached = state.locationAttached,
                            locationNote = state.locationNote
                        )
                    }
                    is DirectSosState.Completed -> {
                        CompletedContent(
                            statuses = state.statuses,
                            submittedCount = state.submittedCount,
                            deliveryConfirmedCount = state.deliveryConfirmedCount,
                            failedCount = state.failedCount,
                            locationAttached = state.locationAttached,
                            locationNote = state.locationNote,
                            onOpenManualFallback = onOpenManualSmsFallback,
                            onClose = onDismiss
                        )
                    }
                    is DirectSosState.Error -> {
                        ErrorContent(
                            reason = state.reason,
                            canFallbackToManual = state.canFallbackToManual,
                            onOpenManualFallback = onOpenManualSmsFallback,
                            onOpenSettings = onOpenSosSettings,
                            onClose = onDismiss
                        )
                    }
                    DirectSosState.Idle -> {}
                }
            }
        }
    }
}

@Composable
private fun CountdownContent(
    secondsRemaining: Int,
    totalSeconds: Int,
    onCancel: () -> Unit,
    onSendNow: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(80.dp)
            .clip(CircleShape)
            .background(SafeHerSosTrack),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "$secondsRemaining",
            fontSize = 42.sp,
            fontWeight = FontWeight.Black,
            color = SafeHerSosCrimson
        )
    }

    Spacer(modifier = Modifier.height(16.dp))

    Text(
        text = "ACTIVATING EMERGENCY SOS",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Black,
        color = SafeHerSosCrimson,
        letterSpacing = 1.sp
    )

    Spacer(modifier = Modifier.height(6.dp))

    Text(
        text = "Sending emergency SMS directly to your trusted guardians in $secondsRemaining seconds…",
        style = MaterialTheme.typography.bodyMedium,
        color = SafeHerTextSecondary,
        textAlign = TextAlign.Center
    )

    Spacer(modifier = Modifier.height(24.dp))

    // High visibility Cancel button
    Button(
        onClick = onCancel,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(containerColor = SafeHerSosCrimson)
    ) {
        Icon(imageVector = Icons.Default.Close, contentDescription = null, tint = Color.White)
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "CANCEL EMERGENCY SOS",
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
            color = Color.White
        )
    }

    Spacer(modifier = Modifier.height(10.dp))

    OutlinedButton(
        onClick = onSendNow,
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, SafeHerBorderLavender)
    ) {
        Icon(imageVector = Icons.Default.Send, contentDescription = null, tint = SafeHerPurplePrimary, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = "Send Immediately (Skip Timer)",
            fontSize = 13.sp,
            color = SafeHerPurplePrimary,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun DispatchingContent(
    statuses: List<RecipientDispatchStatus>,
    locationAttached: Boolean,
    locationNote: String?
) {
    CircularProgressIndicator(
        modifier = Modifier.size(44.dp),
        color = SafeHerSosCrimson,
        strokeWidth = 4.dp
    )

    Spacer(modifier = Modifier.height(16.dp))

    Text(
        text = "Dispatching Emergency SMS",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = SafeHerTextPrimary
    )

    Text(
        text = "Submitting alerts to cellular radio for each guardian…",
        style = MaterialTheme.typography.bodySmall,
        color = SafeHerTextSecondary
    )

    Spacer(modifier = Modifier.height(16.dp))

    // Location note
    LocationNoticeBanner(locationAttached = locationAttached, locationNote = locationNote)

    Spacer(modifier = Modifier.height(16.dp))

    // Per-recipient live status list
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        statuses.forEach { status ->
            RecipientStatusRow(status = status)
        }
    }
}

@Composable
private fun CompletedContent(
    statuses: List<RecipientDispatchStatus>,
    submittedCount: Int,
    deliveryConfirmedCount: Int,
    failedCount: Int,
    locationAttached: Boolean,
    locationNote: String?,
    onOpenManualFallback: () -> Unit,
    onClose: () -> Unit
) {
    val allSucceeded = failedCount == 0 && (submittedCount + deliveryConfirmedCount) > 0

    Box(
        modifier = Modifier
            .size(54.dp)
            .clip(CircleShape)
            .background(if (allSucceeded) Color(0xFFE8F5E9) else SafeHerPinkContainer),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = if (allSucceeded) Icons.Default.CheckCircle else Icons.Default.Warning,
            contentDescription = null,
            tint = if (allSucceeded) Color(0xFF2E7D32) else SafeHerSosCrimson,
            modifier = Modifier.size(32.dp)
        )
    }

    Spacer(modifier = Modifier.height(12.dp))

    Text(
        text = if (allSucceeded) "Emergency SOS Sent" else "SOS Dispatch Completed",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = SafeHerTextPrimary
    )

    Text(
        text = "Submitted: ${submittedCount + deliveryConfirmedCount} • Confirmed: $deliveryConfirmedCount • Failed: $failedCount",
        style = MaterialTheme.typography.labelMedium,
        color = if (failedCount > 0) SafeHerSosCrimson else Color(0xFF2E7D32),
        fontWeight = FontWeight.SemiBold
    )

    Spacer(modifier = Modifier.height(12.dp))

    LocationNoticeBanner(locationAttached = locationAttached, locationNote = locationNote)

    Spacer(modifier = Modifier.height(14.dp))

    // Status items
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        statuses.forEach { status ->
            RecipientStatusRow(status = status)
        }
    }

    Spacer(modifier = Modifier.height(18.dp))

    // If any failed, offer manual fallback
    if (failedCount > 0) {
        Button(
            onClick = onOpenManualFallback,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = SafeHerSosCrimson)
        ) {
            Icon(imageVector = Icons.Default.Send, contentDescription = null, tint = Color.White)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Open Manual SMS Fallback",
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
    }

    OutlinedButton(
        onClick = onClose,
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, SafeHerBorderLavender)
    ) {
        Text("Done", fontWeight = FontWeight.Bold, color = SafeHerTextPrimary)
    }
}

@Composable
private fun ErrorContent(
    reason: String,
    canFallbackToManual: Boolean,
    onOpenManualFallback: () -> Unit,
    onOpenSettings: () -> Unit,
    onClose: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(54.dp)
            .clip(CircleShape)
            .background(SafeHerPinkContainer),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.Error,
            contentDescription = null,
            tint = SafeHerSosCrimson,
            modifier = Modifier.size(32.dp)
        )
    }

    Spacer(modifier = Modifier.height(12.dp))

    Text(
        text = "Automatic SMS Unavailable",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = SafeHerTextPrimary
    )

    Spacer(modifier = Modifier.height(8.dp))

    Text(
        text = reason,
        style = MaterialTheme.typography.bodyMedium,
        color = SafeHerTextSecondary,
        textAlign = TextAlign.Center
    )

    Spacer(modifier = Modifier.height(20.dp))

    if (canFallbackToManual) {
        Button(
            onClick = onOpenManualFallback,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = SafeHerSosCrimson)
        ) {
            Icon(imageVector = Icons.Default.Send, contentDescription = null, tint = Color.White)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Use Manual SMS Fallback",
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
    }

    OutlinedButton(
        onClick = onOpenSettings,
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, SafeHerPurplePrimary)
    ) {
        Text("Open SOS Settings", fontWeight = FontWeight.Bold, color = SafeHerPurplePrimary)
    }

    Spacer(modifier = Modifier.height(6.dp))

    OutlinedButton(
        onClick = onClose,
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, SafeHerBorderLavender)
    ) {
        Text("Dismiss", color = SafeHerTextSecondary)
    }
}

@Composable
private fun RecipientStatusRow(status: RecipientDispatchStatus) {
    val (badgeBg, badgeText, badgeColor) = when (status.state) {
        RecipientDispatchState.PENDING -> Triple(SafeHerLavenderBackground, "Queued…", SafeHerTextSecondary)
        RecipientDispatchState.SUBMITTED_TO_RADIO -> Triple(SafeHerPurpleContainer, "Radio Submitted", SafeHerPurplePrimary)
        RecipientDispatchState.DELIVERY_CONFIRMED -> Triple(Color(0xFFE8F5E9), "Delivered", Color(0xFF2E7D32))
        RecipientDispatchState.FAILED -> Triple(SafeHerPinkContainer, "Failed", SafeHerSosCrimson)
    }

    Card(
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = SafeHerLavenderBackground),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = status.guardianName,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    color = SafeHerTextPrimary
                )
                if (!status.statusMessage.isNullOrBlank()) {
                    Text(
                        text = status.statusMessage,
                        fontSize = 10.sp,
                        color = if (status.state == RecipientDispatchState.FAILED) SafeHerSosCrimson else SafeHerTextMuted
                    )
                }
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(badgeBg)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = badgeText,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = badgeColor
                )
            }
        }
    }
}

@Composable
private fun LocationNoticeBanner(
    locationAttached: Boolean,
    locationNote: String?
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (locationAttached) Color(0xFFE8F5E9) else SafeHerLavenderBackground,
                RoundedCornerShape(8.dp)
            )
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (locationAttached) Icons.Default.LocationOn else Icons.Default.Info,
            contentDescription = null,
            tint = if (locationAttached) Color(0xFF2E7D32) else SafeHerTextSecondary,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = if (locationAttached) "Live GPS location link included" else (locationNote ?: "Location unavailable"),
            fontSize = 11.sp,
            color = if (locationAttached) Color(0xFF2E7D32) else SafeHerTextSecondary,
            fontWeight = FontWeight.Medium
        )
    }
}
