package com.safeher.app.ui.screens.welcome

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PhoneInTalk
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.safeher.app.R
import com.safeher.app.ui.theme.SafeHerBorderLavender
import com.safeher.app.ui.theme.SafeHerPinkAccent
import com.safeher.app.ui.theme.SafeHerPinkContainer
import com.safeher.app.ui.theme.SafeHerPurpleDark
import com.safeher.app.ui.theme.SafeHerPurpleLight
import com.safeher.app.ui.theme.SafeHerPurplePrimary
import com.safeher.app.ui.theme.SafeHerSosCrimson
import com.safeher.app.ui.theme.SafeHerSosTrack
import com.safeher.app.ui.theme.SafeHerStatusSafe
import com.safeher.app.ui.theme.SafeHerSurfaceWhite
import com.safeher.app.ui.theme.SafeHerTextPrimary
import com.safeher.app.ui.theme.SafeHerTextSecondary
import com.safeher.app.ui.theme.SafeHerTheme
import kotlinx.coroutines.launch

data class OnboardingSlideData(
    val title: String,
    val subtitle: String,
    val description: String,
    val badge: String,
    val icon: ImageVector,
    val primaryColor: Color,
    val detailNotes: List<String>
)

@Composable
fun OnboardingSlidesScreen(
    currentSlideIndex: Int,
    onSlideChanged: (Int) -> Unit,
    onSkipClick: () -> Unit,
    onFinishClick: () -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val slides = listOf(
        OnboardingSlideData(
            title = stringResource(R.string.onboarding_slide1_title),
            subtitle = stringResource(R.string.onboarding_slide1_subtitle),
            description = stringResource(R.string.onboarding_slide1_desc),
            badge = stringResource(R.string.onboarding_slide1_badge),
            icon = Icons.Default.People,
            primaryColor = SafeHerPurplePrimary,
            detailNotes = listOf(
                "Add trusted friends and family members.",
                "Stored safely on your device with account scoping.",
                "Instantly reachable when safety pings are dispatched."
            )
        ),
        OnboardingSlideData(
            title = stringResource(R.string.onboarding_slide2_title),
            subtitle = stringResource(R.string.onboarding_slide2_subtitle),
            description = stringResource(R.string.onboarding_slide2_desc),
            badge = stringResource(R.string.onboarding_slide2_badge),
            icon = Icons.AutoMirrored.Filled.DirectionsWalk,
            primaryColor = SafeHerPinkAccent,
            detailNotes = listOf(
                "Set destination & estimated arrival time before heading out.",
                "Trip progress monitored locally without remote tracking.",
                "Overdue check-in notification lets you confirm your safety."
            )
        ),
        OnboardingSlideData(
            title = stringResource(R.string.onboarding_slide3_title),
            subtitle = stringResource(R.string.onboarding_slide3_subtitle),
            description = stringResource(R.string.onboarding_slide3_desc),
            badge = stringResource(R.string.onboarding_slide3_badge),
            icon = Icons.Default.Shield,
            primaryColor = SafeHerSosCrimson,
            detailNotes = listOf(
                "Deliberate 2-second press and hold prevents accidental SOS.",
                "Sends direct SMS to guardians with live coordinates when granted.",
                "Manual messaging app fallback available if SMS permission is declined.",
                "Note: SMS delivery relies on carrier network availability."
            )
        ),
        OnboardingSlideData(
            title = stringResource(R.string.onboarding_slide4_title),
            subtitle = stringResource(R.string.onboarding_slide4_subtitle),
            description = stringResource(R.string.onboarding_slide4_desc),
            badge = stringResource(R.string.onboarding_slide4_badge),
            icon = Icons.Default.Lock,
            primaryColor = SafeHerStatusSafe,
            detailNotes = listOf(
                "Zero continuous background location tracking.",
                "GPS accessed in foreground for map or during active trips.",
                "Your contacts, trip logs, and preferences stay on your device.",
                "Full user control to grant or revoke permissions at any time."
            )
        )
    )

    val pagerState = rememberPagerState(initialPage = currentSlideIndex, pageCount = { slides.size })
    val coroutineScope = rememberCoroutineScope()

    // Sync external slide index updates to pager
    LaunchedEffect(currentSlideIndex) {
        if (pagerState.currentPage != currentSlideIndex) {
            pagerState.animateScrollToPage(currentSlideIndex)
        }
    }

    // Sync user swipes to parent
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.collect { page ->
            onSlideChanged(page)
        }
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top App Bar Controls (Back & Skip)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = {
                        if (pagerState.currentPage > 0) {
                            coroutineScope.launch {
                                pagerState.animateScrollToPage(pagerState.currentPage - 1)
                            }
                        } else {
                            onBackClick()
                        }
                    },
                    modifier = Modifier.semantics { contentDescription = "Go back" }
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = SafeHerTextPrimary
                    )
                }

                TextButton(
                    onClick = onSkipClick,
                    modifier = Modifier.semantics { contentDescription = "Skip onboarding" }
                ) {
                    Text(
                        text = stringResource(R.string.onboarding_skip),
                        style = MaterialTheme.typography.labelLarge,
                        color = SafeHerPurplePrimary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Swipeable Slides Content
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) { page ->
                val slide = slides[page]
                SlideContent(slide = slide)
            }

            // Bottom Navigation & Indicators
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Page Indicator Dots
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(vertical = 16.dp)
                ) {
                    slides.indices.forEach { index ->
                        val isSelected = pagerState.currentPage == index
                        val width by animateDpAsState(
                            targetValue = if (isSelected) 28.dp else 8.dp,
                            label = "indicatorWidth"
                        )
                        val color by animateColorAsState(
                            targetValue = if (isSelected) SafeHerPurplePrimary else SafeHerBorderLavender,
                            label = "indicatorColor"
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

                Spacer(modifier = Modifier.height(8.dp))

                // Bottom Action Buttons
                val isLastSlide = pagerState.currentPage == slides.size - 1

                Button(
                    onClick = {
                        if (isLastSlide) {
                            onFinishClick()
                        } else {
                            coroutineScope.launch {
                                pagerState.animateScrollToPage(pagerState.currentPage + 1)
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .semantics {
                            contentDescription = if (isLastSlide) "Get Started" else "Next slide"
                        },
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isLastSlide) SafeHerPurplePrimary else SafeHerPurplePrimary
                    )
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = if (isLastSlide) {
                                stringResource(R.string.onboarding_finish)
                            } else {
                                stringResource(R.string.onboarding_next)
                            },
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
            }
        }
    }
}

@Composable
private fun SlideContent(
    slide: OnboardingSlideData
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 4.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Icon Aura
        Box(
            modifier = Modifier
                .size(96.dp)
                .clip(CircleShape)
                .background(slide.primaryColor.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(SafeHerSurfaceWhite),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = slide.icon,
                    contentDescription = slide.title,
                    tint = slide.primaryColor,
                    modifier = Modifier.size(44.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Badge
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(SafeHerPinkContainer)
                .padding(horizontal = 14.dp, vertical = 5.dp)
        ) {
            Text(
                text = slide.badge,
                style = MaterialTheme.typography.labelMedium,
                color = SafeHerPurpleDark,
                fontWeight = FontWeight.SemiBold
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Title
        Text(
            text = slide.title,
            style = MaterialTheme.typography.headlineSmall,
            color = SafeHerTextPrimary,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(6.dp))

        // Subtitle
        Text(
            text = slide.subtitle,
            style = MaterialTheme.typography.titleSmall,
            color = SafeHerPurplePrimary,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Description
        Text(
            text = slide.description,
            style = MaterialTheme.typography.bodyMedium,
            color = SafeHerTextSecondary,
            textAlign = TextAlign.Center,
            lineHeight = 22.sp,
            modifier = Modifier.padding(horizontal = 8.dp)
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Key Points Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, SafeHerBorderLavender, RoundedCornerShape(20.dp)),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = SafeHerSurfaceWhite),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                slide.detailNotes.forEach { note ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Box(
                            modifier = Modifier
                                .padding(top = 4.dp)
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(slide.primaryColor)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = note,
                            style = MaterialTheme.typography.bodySmall,
                            color = SafeHerTextSecondary,
                            lineHeight = 18.sp
                        )
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun OnboardingSlidesScreenPreview() {
    SafeHerTheme {
        OnboardingSlidesScreen(
            currentSlideIndex = 0,
            onSlideChanged = {},
            onSkipClick = {},
            onFinishClick = {},
            onBackClick = {}
        )
    }
}
