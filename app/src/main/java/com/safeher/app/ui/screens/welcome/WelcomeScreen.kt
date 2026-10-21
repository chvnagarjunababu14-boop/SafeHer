package com.safeher.app.ui.screens.welcome

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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.safeher.app.R
import com.safeher.app.ui.theme.SafeHerBorderLavender
import com.safeher.app.ui.theme.SafeHerLavenderBackground
import com.safeher.app.ui.theme.SafeHerPinkAccent
import com.safeher.app.ui.theme.SafeHerPinkContainer
import com.safeher.app.ui.theme.SafeHerPurpleContainer
import com.safeher.app.ui.theme.SafeHerPurpleDark
import com.safeher.app.ui.theme.SafeHerPurplePrimary
import com.safeher.app.ui.theme.SafeHerStatusSafe
import com.safeher.app.ui.theme.SafeHerSurfaceWhite
import com.safeher.app.ui.theme.SafeHerTextMuted
import com.safeher.app.ui.theme.SafeHerTextPrimary
import com.safeher.app.ui.theme.SafeHerTextSecondary
import com.safeher.app.ui.theme.SafeHerTheme

@Composable
fun WelcomeScreen(
    viewModel: WelcomeViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    WelcomeContent(
        uiState = uiState,
        onAcknowledgeClick = { viewModel.onAcknowledgeClicked() }
    )
}

@Composable
fun WelcomeContent(
    uiState: WelcomeUiState,
    onAcknowledgeClick: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Spacer(modifier = Modifier.height(20.dp))

                // SafeHer Shield Icon with gradient aura
                Box(
                    modifier = Modifier
                        .size(96.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                listOf(SafeHerPurplePrimary, SafeHerPinkAccent)
                            )
                        )
                        .shadow(12.dp, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(86.dp)
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

                Spacer(modifier = Modifier.height(24.dp))

                // Phase Badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(SafeHerPinkContainer)
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(SafeHerPinkAccent)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = uiState.phaseTitle,
                            style = MaterialTheme.typography.labelMedium,
                            color = SafeHerPurpleDark,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // App Title
                Text(
                    text = uiState.appName,
                    style = MaterialTheme.typography.headlineLarge,
                    color = SafeHerTextPrimary,
                    fontWeight = FontWeight.ExtraBold
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Subtitle
                Text(
                    text = uiState.subtitle,
                    style = MaterialTheme.typography.bodyLarge,
                    color = SafeHerTextSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )

                Spacer(modifier = Modifier.height(32.dp))

                // Architecture & Foundation Card
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, SafeHerBorderLavender, RoundedCornerShape(24.dp)),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = SafeHerSurfaceWhite
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Security,
                                contentDescription = null,
                                tint = SafeHerPurplePrimary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Foundation Readiness",
                                style = MaterialTheme.typography.titleMedium,
                                color = SafeHerTextPrimary,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        uiState.securityHighlights.forEach { highlight ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = SafeHerStatusSafe,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = highlight,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = SafeHerTextSecondary
                                )
                            }
                        }
                    }
                }
            }

            // Bottom Action & Trust Note
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 28.dp)
            ) {
                Button(
                    onClick = onAcknowledgeClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (uiState.acknowledged) SafeHerPurpleDark else SafeHerPurplePrimary
                    )
                ) {
                    Text(
                        text = if (uiState.acknowledged) "Foundation Verified ✓" else "Confirm Foundation Ready",
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White,
                        fontSize = 16.sp
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = stringResource(R.string.privacy_badge),
                    style = MaterialTheme.typography.bodySmall,
                    color = SafeHerTextMuted,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun WelcomeScreenPreview() {
    SafeHerTheme {
        WelcomeContent(
            uiState = WelcomeUiState(),
            onAcknowledgeClick = {}
        )
    }
}
