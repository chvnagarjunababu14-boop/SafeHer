package com.safeher.app.ui.screens.home.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.safeher.app.R
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
    modifier: Modifier = Modifier
) {
    // Subtle pulsating glow animation when idle
    val infiniteTransition = rememberInfiniteTransition(label = "SosPulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = if (isHolding) 1.0f else 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "PulseScale"
    )

    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = if (isHolding) 0.1f else 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "PulseAlpha"
    )

    // Touch scale feedback while holding down
    val buttonScale by animateFloatAsState(
        targetValue = if (isHolding) 0.94f else 1.0f,
        animationSpec = tween(durationMillis = 120),
        label = "ButtonHoldScale"
    )

    val sosDesc = stringResource(R.string.sos_button_desc)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(190.dp)
                .semantics {
                    role = Role.Button
                    contentDescription = sosDesc
                },
            contentAlignment = Alignment.Center
        ) {
            // Animated ambient glow aura
            Box(
                modifier = Modifier
                    .size(175.dp)
                    .scale(pulseScale)
                    .clip(CircleShape)
                    .background(SafeHerSosGlow.copy(alpha = pulseAlpha))
            )

            // Hold-to-activate circular progress track & bar
            CircularProgressIndicator(
                progress = { 1.0f },
                modifier = Modifier.size(164.dp),
                color = SafeHerSosTrack,
                strokeWidth = 6.dp,
                strokeCap = StrokeCap.Round
            )

            if (holdProgress > 0f) {
                CircularProgressIndicator(
                    progress = { holdProgress },
                    modifier = Modifier.size(164.dp),
                    color = SafeHerSosLight,
                    strokeWidth = 6.dp,
                    strokeCap = StrokeCap.Round
                )
            }

            // Main Interactive SOS Button (Demo trigger)
            Box(
                modifier = Modifier
                    .size(144.dp)
                    .scale(buttonScale)
                    .shadow(elevation = if (isHolding) 4.dp else 10.dp, shape = CircleShape)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            listOf(
                                SafeHerSosRed,
                                SafeHerSosCrimson
                            )
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
                        fontSize = 36.sp,
                        letterSpacing = 2.sp
                    )

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = if (isHolding) "${(holdProgress * 100).toInt()}%" else "HOLD 2S",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.9f),
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Reassuring feedback text
        Text(
            text = feedbackText,
            style = MaterialTheme.typography.bodyMedium,
            color = if (isHolding) SafeHerSosCrimson else SafeHerTextPrimary,
            fontWeight = FontWeight.SemiBold
        )

        Spacer(modifier = Modifier.height(2.dp))

        // Clear non-misleading demo footnote
        Text(
            text = stringResource(R.string.sos_preview_footnote),
            style = MaterialTheme.typography.bodySmall,
            color = SafeHerTextMuted,
            fontSize = 12.sp
        )
    }
}
