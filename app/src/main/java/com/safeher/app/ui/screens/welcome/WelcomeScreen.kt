package com.safeher.app.ui.screens.welcome

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.safeher.app.BuildConfig
import com.safeher.app.R
import com.safeher.app.ui.theme.SafeHerBorderLavender
import com.safeher.app.ui.theme.SafeHerPinkAccent
import com.safeher.app.ui.theme.SafeHerPinkContainer
import com.safeher.app.ui.theme.SafeHerPurpleDark
import com.safeher.app.ui.theme.SafeHerPurpleLight
import com.safeher.app.ui.theme.SafeHerPurplePrimary
import com.safeher.app.ui.theme.SafeHerStatusSafe
import com.safeher.app.ui.theme.SafeHerSurfaceWhite
import com.safeher.app.ui.theme.SafeHerTextMuted
import com.safeher.app.ui.theme.SafeHerTextPrimary
import com.safeher.app.ui.theme.SafeHerTextSecondary
import com.safeher.app.ui.theme.SafeHerTheme
import kotlinx.coroutines.launch

/**
 * Opening Welcome screen presenting a polished, swipeable two-role selection carousel:
 *
 * Slide 0: Personal Safety User — Manage trusted guardians, safe journeys, check-ins, location, and SOS.
 * Slide 1: Guardian — Connect via invitation code to view authorized journeys, alerts, and location info.
 */
@Composable
fun WelcomeScreen(
    onContinueAsUser: () -> Unit,
    onContinueAsGuardian: () -> Unit,
    onRoleSlideChanged: ((Int) -> Unit)? = null,
    onGetStartedClick: (() -> Unit)? = null,
    onAlreadyHaveAccountClick: (() -> Unit)? = null,
    onDemoModeClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { 2 })
    val coroutineScope = rememberCoroutineScope()

    androidx.compose.runtime.LaunchedEffect(pagerState.currentPage) {
        onRoleSlideChanged?.invoke(pagerState.currentPage)
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top Role Selection Page Indicator
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 28.dp, bottom = 4.dp)
            ) {
                (0..1).forEach { index ->
                    val isSelected = pagerState.currentPage == index
                    val width by animateDpAsState(
                        targetValue = if (isSelected) 32.dp else 10.dp,
                        label = "roleIndicatorWidth"
                    )
                    val color by animateColorAsState(
                        targetValue = if (isSelected) SafeHerPurplePrimary else SafeHerBorderLavender,
                        label = "roleIndicatorColor"
                    )

                    Box(
                        modifier = Modifier
                            .padding(horizontal = 4.dp)
                            .height(8.dp)
                            .width(width)
                            .clip(RoundedCornerShape(4.dp))
                            .background(color)
                    )
                }
            }

            // Swipeable Role Carousel
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) { page ->
                when (page) {
                    0 -> PersonalUserSlide(
                        onPrimaryClick = onContinueAsUser,
                        onSwitchToGuardian = {
                            coroutineScope.launch {
                                pagerState.animateScrollToPage(1)
                            }
                        },
                        onDemoModeClick = onDemoModeClick
                    )
                    1 -> GuardianSlide(
                        onPrimaryClick = onContinueAsGuardian,
                        onSwitchToUser = {
                            coroutineScope.launch {
                                pagerState.animateScrollToPage(0)
                            }
                        },
                        onDemoModeClick = onDemoModeClick
                    )
                }
            }
        }
    }
}

/**
 * Slide 1 — Personal Safety User
 * SafeHer branding and tagline: "Your safety, your journey, your circle."
 */
@Composable
private fun PersonalUserSlide(
    onPrimaryClick: () -> Unit,
    onSwitchToGuardian: () -> Unit,
    onDemoModeClick: (() -> Unit)?
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            Spacer(modifier = Modifier.height(12.dp))

            // Brand Icon with Aura
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            listOf(SafeHerPurplePrimary, SafeHerPinkAccent)
                        )
                    )
                    .shadow(14.dp, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(84.dp)
                        .clip(CircleShape)
                        .background(SafeHerSurfaceWhite),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = stringResource(R.string.app_name),
                        tint = SafeHerPurplePrimary,
                        modifier = Modifier.size(48.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // App Name & Role Headline
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineLarge,
                color = SafeHerPurplePrimary,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 0.5.sp
            )

            Spacer(modifier = Modifier.height(8.dp))

            com.safeher.app.ui.components.RoleBadge(
                tone = com.safeher.app.ui.components.SafeHerRoleTone.USER
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Tagline
            Text(
                text = "Your safety, your journey, your circle.",
                style = MaterialTheme.typography.titleMedium,
                color = SafeHerPurpleDark,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Role Description
            Text(
                text = "Manage your trusted guardians, track safe journeys with local check-ins, share live coordinates, and dispatch instant SOS protection.",
                style = MaterialTheme.typography.bodyMedium,
                color = SafeHerTextSecondary,
                textAlign = TextAlign.Center,
                lineHeight = 21.sp,
                modifier = Modifier.padding(horizontal = 8.dp)
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Feature Highlights Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, SafeHerBorderLavender, RoundedCornerShape(20.dp)),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = SafeHerSurfaceWhite),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 16.dp)
                ) {
                    HighlightRow(
                        icon = Icons.Default.Shield,
                        tint = SafeHerPurplePrimary,
                        title = "Instant SOS Protection",
                        subtitle = "Deliberate 2-second hold emergency dispatch"
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    HighlightRow(
                        icon = Icons.Default.People,
                        tint = SafeHerPinkAccent,
                        title = "Trusted Circle",
                        subtitle = "Direct emergency signaling to your personal guardians"
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    HighlightRow(
                        icon = Icons.Default.Lock,
                        tint = SafeHerStatusSafe,
                        title = "Privacy-by-Design",
                        subtitle = "On-device coordinates and zero data brokers"
                    )
                }
            }
        }

        // Slide Actions Section
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 24.dp, bottom = 12.dp)
        ) {
            // Primary Action: Continue as User
            Button(
                onClick = onPrimaryClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .semantics { contentDescription = "Continue as Personal Safety User" },
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = SafeHerPurplePrimary)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "Continue as User",
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Secondary Action: I'm a Guardian
            OutlinedButton(
                onClick = onSwitchToGuardian,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .semantics { contentDescription = "Switch slide to Guardian role" },
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = SafeHerPurplePrimary
                ),
                border = ButtonDefaults.outlinedButtonBorder.copy(
                    brush = Brush.linearGradient(listOf(SafeHerPurplePrimary, SafeHerPurpleLight))
                )
            ) {
                Text(
                    text = "I’m a Guardian",
                    style = MaterialTheme.typography.labelLarge,
                    color = SafeHerPurplePrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            // Demo Mode button (if supported in flavor)
            if (onDemoModeClick != null && BuildConfig.IS_DEMO_DEFAULT) {
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = onDemoModeClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = SafeHerPinkContainer,
                        contentColor = SafeHerPurpleDark
                    )
                ) {
                    Text(
                        text = stringResource(R.string.auth_demo_button),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Privacy Trust Badge
            Text(
                text = stringResource(R.string.privacy_badge),
                style = MaterialTheme.typography.bodySmall,
                color = SafeHerTextMuted,
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * Slide 2 — Guardian
 * Explains connecting using an invitation code to view authorized journeys, alerts, and location info.
 */
@Composable
private fun GuardianSlide(
    onPrimaryClick: () -> Unit,
    onSwitchToUser: () -> Unit,
    onDemoModeClick: (() -> Unit)?
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            Spacer(modifier = Modifier.height(12.dp))

            // Brand Icon with Guardian Shield Aura
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            listOf(SafeHerPurpleDark, SafeHerPurplePrimary)
                        )
                    )
                    .shadow(14.dp, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(84.dp)
                        .clip(CircleShape)
                        .background(SafeHerSurfaceWhite),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.VerifiedUser,
                        contentDescription = "SafeHer Guardian",
                        tint = SafeHerPurplePrimary,
                        modifier = Modifier.size(48.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Role Title
            Text(
                text = "SafeHer Guardian",
                style = MaterialTheme.typography.headlineLarge,
                color = SafeHerPurpleDark,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 0.5.sp
            )

            Spacer(modifier = Modifier.height(8.dp))

            com.safeher.app.ui.components.RoleBadge(
                tone = com.safeher.app.ui.components.SafeHerRoleTone.GUARDIAN
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Tagline
            Text(
                text = "Protecting the circle that matters most.",
                style = MaterialTheme.typography.titleMedium,
                color = SafeHerPurplePrimary,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Guardian Description
            Text(
                text = "Connect to a SafeHer user using an invitation code. View only the journeys, emergency alerts, and location information the user has authorized you to access.",
                style = MaterialTheme.typography.bodyMedium,
                color = SafeHerTextSecondary,
                textAlign = TextAlign.Center,
                lineHeight = 21.sp,
                modifier = Modifier.padding(horizontal = 8.dp)
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Guardian Highlights Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, SafeHerBorderLavender, RoundedCornerShape(20.dp)),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = SafeHerSurfaceWhite),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 16.dp)
                ) {
                    HighlightRow(
                        icon = Icons.Default.QrCodeScanner,
                        tint = SafeHerPurplePrimary,
                        title = "Invitation-Code Authorization",
                        subtitle = "Connect via single-use code generated by the user"
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    HighlightRow(
                        icon = Icons.Default.NotificationsActive,
                        tint = SafeHerPinkAccent,
                        title = "Real-Time Emergency Alerts",
                        subtitle = "Instant distress alerts with consented live coordinates"
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    HighlightRow(
                        icon = Icons.Default.Lock,
                        tint = SafeHerStatusSafe,
                        title = "Privacy-First Scoping",
                        subtitle = "Access only authorized alerts with zero tracking brokers"
                    )
                }
            }
        }

        // Slide Actions Section
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 24.dp, bottom = 12.dp)
        ) {
            // Primary Action: Continue as Guardian
            Button(
                onClick = onPrimaryClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .semantics { contentDescription = "Continue as Guardian" },
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = SafeHerPurpleDark)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "Continue as Guardian",
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Secondary Action: I'm a User
            OutlinedButton(
                onClick = onSwitchToUser,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .semantics { contentDescription = "Switch slide to Personal Safety User role" },
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = SafeHerPurplePrimary
                ),
                border = ButtonDefaults.outlinedButtonBorder.copy(
                    brush = Brush.linearGradient(listOf(SafeHerPurplePrimary, SafeHerPurpleLight))
                )
            ) {
                Text(
                    text = "I’m a User",
                    style = MaterialTheme.typography.labelLarge,
                    color = SafeHerPurplePrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            // Demo Mode button (if supported in flavor)
            if (onDemoModeClick != null && BuildConfig.IS_DEMO_DEFAULT) {
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = onDemoModeClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = SafeHerPinkContainer,
                        contentColor = SafeHerPurpleDark
                    )
                ) {
                    Text(
                        text = stringResource(R.string.auth_demo_button),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Privacy Trust Badge
            Text(
                text = stringResource(R.string.privacy_badge),
                style = MaterialTheme.typography.bodySmall,
                color = SafeHerTextMuted,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun HighlightRow(
    icon: ImageVector,
    tint: Color,
    title: String,
    subtitle: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(tint.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = SafeHerTextPrimary
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = SafeHerTextSecondary
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun WelcomeScreenPreview() {
    SafeHerTheme {
        WelcomeScreen(
            onContinueAsUser = {},
            onContinueAsGuardian = {}
        )
    }
}
