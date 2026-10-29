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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.safeher.app.ui.theme.SafeHerBorderLavender
import com.safeher.app.ui.theme.SafeHerPinkContainer
import com.safeher.app.ui.theme.SafeHerPurpleDark
import com.safeher.app.ui.theme.SafeHerPurplePrimary
import com.safeher.app.ui.theme.SafeHerStatusEmergency
import com.safeher.app.ui.theme.SafeHerStatusSafe
import com.safeher.app.ui.theme.SafeHerSurfaceWhite
import com.safeher.app.ui.theme.SafeHerTextMuted
import com.safeher.app.ui.theme.SafeHerTextPrimary
import com.safeher.app.ui.theme.SafeHerTextSecondary
import com.safeher.app.ui.theme.SafeHerTheme

/**
 * Dedicated Guardian Connection screen displayed post-registration or when linking with a Personal Safety User.
 *
 * Implements strict security guarantees:
 * - Requires server-side invitation validation before establishing connections.
 * - Handles Firebase Spark plan (undeployed Cloud Functions) honestly with clear messaging.
 * - Allows the guardian to skip or navigate to their empty-state dashboard and account without fake records.
 */
@Composable
fun GuardianConnectScreen(
    uiState: WelcomeUiState,
    onCodeChanged: (String) -> Unit,
    onConnectClick: () -> Unit,
    onDismissError: () -> Unit,
    onGoToDashboardClick: () -> Unit,
    onSignOutClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val focusManager = LocalFocusManager.current

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // Header Icon
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(SafeHerPurplePrimary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.VerifiedUser,
                    contentDescription = null,
                    tint = SafeHerPurplePrimary,
                    modifier = Modifier.size(38.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Connect to a SafeHer User",
                style = MaterialTheme.typography.headlineMedium,
                color = SafeHerTextPrimary,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "The Personal Safety User generates an invitation phrase from their SafeHer account screen. Enter the exact phrase below to establish an authorized connection.",
                style = MaterialTheme.typography.bodyMedium,
                color = SafeHerTextSecondary,
                textAlign = TextAlign.Center,
                lineHeight = 20.sp
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Step instructions card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = SafeHerPinkContainer.copy(alpha = 0.4f)),
                border = androidx.compose.foundation.BorderStroke(1.dp, SafeHerBorderLavender)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "How connection works:",
                        style = MaterialTheme.typography.labelLarge,
                        color = SafeHerPurpleDark,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "1. Personal Safety User taps 'Invite Guardian' in Account.\n" +
                                "2. They share their temporary invitation phrase.\n" +
                                "3. Enter the code below to authorize your guardian link.\n" +
                                "4. Access only journeys and alerts they choose to share.",
                        style = MaterialTheme.typography.bodySmall,
                        color = SafeHerTextSecondary,
                        lineHeight = 18.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Error Banner (e.g. Spark plan / NOT_FOUND explanation)
            if (uiState.connectGuardianError != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(SafeHerStatusEmergency.copy(alpha = 0.12f))
                        .border(1.dp, SafeHerStatusEmergency.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
                        .padding(14.dp)
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.Top) {
                            Icon(
                                imageVector = Icons.Default.Error,
                                contentDescription = null,
                                tint = SafeHerStatusEmergency,
                                modifier = Modifier
                                    .size(20.dp)
                                    .padding(top = 2.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = uiState.connectGuardianError,
                                style = MaterialTheme.typography.bodySmall,
                                color = SafeHerStatusEmergency,
                                fontWeight = FontWeight.Medium,
                                lineHeight = 18.sp
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(
                                onClick = onConnectClick,
                                enabled = !uiState.isConnectingGuardian
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = null,
                                    tint = SafeHerPurplePrimary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Retry", color = SafeHerPurplePrimary, fontWeight = FontWeight.Bold)
                            }
                            TextButton(onClick = onDismissError) {
                                Text("Dismiss", color = SafeHerTextSecondary)
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Success Banner
            if (uiState.connectGuardianSuccess) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(SafeHerStatusSafe.copy(alpha = 0.12f))
                        .border(1.dp, SafeHerStatusSafe.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
                        .padding(14.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = SafeHerStatusSafe,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Connection successfully authorized! You can now monitor shared journeys and alerts.",
                            style = MaterialTheme.typography.bodySmall,
                            color = SafeHerStatusSafe,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Invitation Code Input Field
            OutlinedTextField(
                value = uiState.inviteCodeInput,
                onValueChange = onCodeChanged,
                label = { Text("8-Character Invitation Code") },
                placeholder = { Text("e.g. SH-A8B9C2") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Key,
                        contentDescription = null,
                        tint = SafeHerPurplePrimary
                    )
                },
                singleLine = true,
                enabled = !uiState.isConnectingGuardian,
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = "Invitation code input" },
                shape = RoundedCornerShape(14.dp),
                textStyle = MaterialTheme.typography.titleMedium.copy(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp
                ),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Characters,
                    keyboardType = KeyboardType.Ascii,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = {
                        focusManager.clearFocus()
                        onConnectClick()
                    }
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = SafeHerPurplePrimary,
                    unfocusedBorderColor = SafeHerBorderLavender,
                    focusedContainerColor = SafeHerSurfaceWhite,
                    unfocusedContainerColor = SafeHerSurfaceWhite
                )
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Primary Connect Button
            Button(
                onClick = {
                    focusManager.clearFocus()
                    onConnectClick()
                },
                enabled = !uiState.isConnectingGuardian && uiState.inviteCodeInput.trim().length >= 10,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .semantics { contentDescription = "Connect guardian button" },
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = SafeHerPurplePrimary)
            ) {
                if (uiState.isConnectingGuardian) {
                    CircularProgressIndicator(
                        color = Color.White,
                        strokeWidth = 2.5.dp,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("Validating Code...", color = Color.White, fontWeight = FontWeight.Bold)
                } else {
                    Icon(
                        imageVector = Icons.Default.VerifiedUser,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Connect with Code",
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Non-blocking navigation option: "Go to Dashboard"
            OutlinedButton(
                onClick = onGoToDashboardClick,
                enabled = !uiState.isConnectingGuardian,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, SafeHerBorderLavender),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = SafeHerSurfaceWhite,
                    contentColor = SafeHerPurplePrimary
                )
            ) {
                Text(
                    text = if (uiState.connectGuardianSuccess) "Open Guardian Dashboard" else "Go to Dashboard (Connect Later)",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
            }

            if (onSignOutClick != null) {
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(
                    onClick = onSignOutClick,
                    enabled = !uiState.isConnectingGuardian
                ) {
                    Text(
                        text = "Sign Out / Switch Account",
                        style = MaterialTheme.typography.bodySmall,
                        color = SafeHerTextMuted
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Preview(showBackground = true)
@Composable
fun GuardianConnectScreenPreview() {
    SafeHerTheme {
        GuardianConnectScreen(
            uiState = WelcomeUiState(
                inviteCodeInput = "SAFE1234"
            ),
            onCodeChanged = {},
            onConnectClick = {},
            onDismissError = {},
            onGoToDashboardClick = {},
            onSignOutClick = {}
        )
    }
}
