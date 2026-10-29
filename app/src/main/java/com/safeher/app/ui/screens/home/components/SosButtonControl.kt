package com.safeher.app.ui.screens.home.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.safeher.app.R
import com.safeher.app.ui.theme.SafeHerDimens
import com.safeher.app.ui.theme.SafeHerPinkContainer
import com.safeher.app.ui.theme.SafeHerPurpleContainer
import com.safeher.app.ui.theme.SafeHerPurplePrimary
import com.safeher.app.ui.theme.SafeHerSosCrimson
import com.safeher.app.ui.theme.SafeHerSosGlow
import com.safeher.app.ui.theme.SafeHerSosLight
import com.safeher.app.ui.theme.SafeHerSosRed
import com.safeher.app.ui.theme.SafeHerSosTrack
import com.safeher.app.ui.theme.SafeHerTextMuted
import com.safeher.app.ui.theme.SafeHerTextPrimary

@Composable
fun SosButtonControl(
    isHolding: Boolean,
    holdProgress: Float,
    feedbackText: String,
    onHoldStart: () -> Unit,
    onHoldCancel: () -> Unit,
    onAccessibleSosClick: () -> Unit = {},
    onOpenSosSettingsClick: () -> Unit = {},
    isDirectSmsEnabled: Boolean = false,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "SosPulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = if (isHolding) 1.0f else 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "PulseScale"
    )

    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = if (isHolding) 0.12f else 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "PulseAlpha"
    )

    val buttonScale by animateFloatAsState(
        targetValue = if (isHolding) 0.94f else 1.0f,
        animationSpec = tween(durationMillis = 120),
        label = "ButtonHoldScale"
    )

    val sosDesc = stringResource(R.string.sos_button_desc)
    val holdHint = stringResource(R.string.sos_hold_hint)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = SafeHerDimens.spaceSm),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(196.dp)
                .semantics {
                    role = Role.Button
                    contentDescription = sosDesc
                },
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(178.dp)
                    .scale(pulseScale)
                    .clip(CircleShape)
                    .background(SafeHerSosGlow.copy(alpha = pulseAlpha))
            )

            CircularProgressIndicator(
                progress = { 1.0f },
                modifier = Modifier.size(SafeHerDimens.sosButton + 12.dp),
                color = SafeHerSosTrack,
                strokeWidth = 6.dp,
                strokeCap = StrokeCap.Round
            )

            if (holdProgress > 0f) {
                CircularProgressIndicator(
                    progress = { holdProgress },
                    modifier = Modifier.size(SafeHerDimens.sosButton + 12.dp),
                    color = SafeHerSosLight,
                    strokeWidth = 6.dp,
                    strokeCap = StrokeCap.Round
                )
            }

            Box(
                modifier = Modifier
                    .size(SafeHerDimens.sosButton)
                    .scale(buttonScale)
                    .shadow(
                        elevation = if (isHolding) SafeHerDimens.elevationMid else SafeHerDimens.elevationHigh,
                        shape = CircleShape
                    )
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            listOf(SafeHerSosRed, SafeHerSosCrimson)
                        )
                    )
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onPress = {
                                onHoldStart()
                                val released = tryAwaitRelease()
                                if (released) {
                                    onHoldCancel()
                                }
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = stringResource(R.string.sos_title),
                        style = MaterialTheme.typography.headlineLarge,
                        color = Color.White,
                        fontWeight = FontWeight.Black,
                        fontSize = 34.sp,
                        letterSpacing = 2.sp
                    )
                    Spacer(modifier = Modifier.height(SafeHerDimens.spaceXxxs))
                    Text(
                        text = if (isHolding) "${(holdProgress * 100).toInt()}%" else holdHint,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.92f),
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(SafeHerDimens.spaceXs))

        Text(
            text = feedbackText,
            style = MaterialTheme.typography.bodyMedium,
            color = if (isHolding) SafeHerSosCrimson else SafeHerTextPrimary,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = SafeHerDimens.screenHorizontal)
        )

        Spacer(modifier = Modifier.height(SafeHerDimens.spaceXxs))

        Text(
            text = stringResource(
                if (isDirectSmsEnabled) R.string.sos_direct_enabled_footnote
                else R.string.sos_preview_footnote
            ),
            style = MaterialTheme.typography.bodySmall,
            color = SafeHerTextMuted,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = SafeHerDimens.screenHorizontal)
        )

        Spacer(modifier = Modifier.height(SafeHerDimens.spaceSm))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = SafeHerDimens.screenHorizontal),
            horizontalArrangement = Arrangement.spacedBy(SafeHerDimens.spaceSm, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SosAuxAction(
                label = stringResource(R.string.sos_quick_label),
                icon = Icons.Default.Warning,
                background = SafeHerPinkContainer,
                foreground = SafeHerSosCrimson,
                onClick = onAccessibleSosClick,
                modifier = Modifier.weight(1f)
            )
            SosAuxAction(
                label = stringResource(R.string.sos_settings_label),
                icon = Icons.Default.Settings,
                background = SafeHerPurpleContainer,
                foreground = SafeHerPurplePrimary,
                onClick = onOpenSosSettingsClick,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun SosAuxAction(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    background: Color,
    foreground: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .heightIn(min = SafeHerDimens.touchMin)
            .clip(RoundedCornerShape(14.dp))
            .background(background)
            .border(1.dp, foreground.copy(alpha = 0.18f), RoundedCornerShape(14.dp))
            .clickable(role = Role.Button, onClickLabel = label, onClick = onClick)
            .padding(horizontal = SafeHerDimens.spaceSm, vertical = SafeHerDimens.spaceXs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = foreground,
            modifier = Modifier.size(SafeHerDimens.iconSm)
        )
        Spacer(modifier = Modifier.width(SafeHerDimens.spaceXxs))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = foreground,
            maxLines = 1
        )
    }
}
