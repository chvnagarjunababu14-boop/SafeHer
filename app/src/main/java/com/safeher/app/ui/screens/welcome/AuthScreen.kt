package com.safeher.app.ui.screens.welcome

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import com.safeher.app.data.session.UserRole
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
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
import com.safeher.app.ui.theme.SafeHerPurplePrimary
import com.safeher.app.ui.theme.SafeHerStatusEmergency
import com.safeher.app.ui.theme.SafeHerStatusSafe
import com.safeher.app.ui.theme.SafeHerSurfaceVariant
import com.safeher.app.ui.theme.SafeHerSurfaceWhite
import com.safeher.app.ui.theme.SafeHerTextMuted
import com.safeher.app.ui.theme.SafeHerTextPrimary
import com.safeher.app.ui.theme.SafeHerTextSecondary
import com.safeher.app.ui.theme.SafeHerTheme

@Composable
fun AuthScreen(
    uiState: WelcomeUiState,
    onToggleAuthMode: () -> Unit,
    onEmailChanged: (String) -> Unit,
    onPasswordChanged: (String) -> Unit,
    onConfirmPasswordChanged: (String) -> Unit,
    onDisplayNameChanged: (String) -> Unit,
    onTogglePasswordVisibility: () -> Unit,
    onToggleConfirmPasswordVisibility: () -> Unit,
    onSignInClick: () -> Unit,
    onSignUpClick: () -> Unit,
    onGoogleSignInClick: () -> Unit,
    onForgotPasswordClick: () -> Unit,
    onDismissForgotPassword: () -> Unit,
    onResetEmailChanged: (String) -> Unit,
    onSendPasswordReset: () -> Unit,
    onDemoModeClick: (() -> Unit)?,
    onBackClick: () -> Unit,
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
            // Top Navigation Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBackClick,
                    modifier = Modifier.semantics { contentDescription = "Go back to welcome screen" }
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = SafeHerTextPrimary
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                TextButton(
                    onClick = onBackClick,
                    enabled = !uiState.isLoading
                ) {
                    Text(
                        text = "Switch Role",
                        style = MaterialTheme.typography.labelMedium,
                        color = SafeHerPurplePrimary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Active Role Badge Card
            val isGuardian = uiState.selectedRole == UserRole.GUARDIAN
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (isGuardian) SafeHerPinkContainer.copy(alpha = 0.5f) else SafeHerPurplePrimary.copy(alpha = 0.08f))
                    .border(
                        1.dp,
                        if (isGuardian) SafeHerPurpleDark.copy(alpha = 0.25f) else SafeHerPurplePrimary.copy(alpha = 0.25f),
                        RoundedCornerShape(16.dp)
                    )
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(if (isGuardian) SafeHerPurpleDark.copy(alpha = 0.15f) else SafeHerPurplePrimary.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isGuardian) Icons.Default.VerifiedUser else Icons.Default.Shield,
                            contentDescription = null,
                            tint = if (isGuardian) SafeHerPurpleDark else SafeHerPurplePrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        com.safeher.app.ui.components.RoleBadge(
                            tone = if (isGuardian) {
                                com.safeher.app.ui.components.SafeHerRoleTone.GUARDIAN
                            } else {
                                com.safeher.app.ui.components.SafeHerRoleTone.USER
                            }
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (isGuardian) "Authorized access to shared journeys & alerts" else "Protect your safety, journeys & circles",
                            style = MaterialTheme.typography.bodySmall,
                            color = SafeHerTextSecondary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Logo & Title Header
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .clip(CircleShape)
                    .background(SafeHerPurplePrimary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isGuardian) Icons.Default.VerifiedUser else Icons.Default.Shield,
                    contentDescription = null,
                    tint = SafeHerPurplePrimary,
                    modifier = Modifier.size(32.dp)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = if (uiState.isSignInMode) {
                    stringResource(R.string.auth_sign_in_title)
                } else {
                    stringResource(R.string.auth_sign_up_title)
                },
                style = MaterialTheme.typography.headlineMedium,
                color = SafeHerTextPrimary,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = if (uiState.isSignInMode) {
                    if (isGuardian) "Sign in as a Guardian to monitor authorized journeys and alerts"
                    else "Sign in to access your trusted circle and safe journeys"
                } else {
                    if (isGuardian) "Create your Guardian account to connect with users"
                    else "Create your account with privacy-first protection"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = SafeHerTextSecondary,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Tab Selector: Sign In / Sign Up
            TabRow(
                selectedTabIndex = if (uiState.isSignInMode) 0 else 1,
                containerColor = SafeHerSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp)),
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        Modifier.tabIndicatorOffset(tabPositions[if (uiState.isSignInMode) 0 else 1]),
                        color = SafeHerPurplePrimary,
                        height = 3.dp
                    )
                },
                divider = {}
            ) {
                Tab(
                    selected = uiState.isSignInMode,
                    onClick = { if (!uiState.isSignInMode) onToggleAuthMode() },
                    text = {
                        Text(
                            text = stringResource(R.string.auth_sign_in_tab),
                            fontWeight = if (uiState.isSignInMode) FontWeight.Bold else FontWeight.Medium,
                            color = if (uiState.isSignInMode) SafeHerPurplePrimary else SafeHerTextSecondary
                        )
                    }
                )
                Tab(
                    selected = !uiState.isSignInMode,
                    onClick = { if (uiState.isSignInMode) onToggleAuthMode() },
                    text = {
                        Text(
                            text = stringResource(R.string.auth_sign_up_tab),
                            fontWeight = if (!uiState.isSignInMode) FontWeight.Bold else FontWeight.Medium,
                            color = if (!uiState.isSignInMode) SafeHerPurplePrimary else SafeHerTextSecondary
                        )
                    }
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Error Message Banner
            if (uiState.errorMessage != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(SafeHerStatusEmergency.copy(alpha = 0.12f))
                        .border(1.dp, SafeHerStatusEmergency.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Error,
                            contentDescription = null,
                            tint = SafeHerStatusEmergency,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = uiState.errorMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = SafeHerStatusEmergency,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Info Message Banner
            if (uiState.infoMessage != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(SafeHerStatusSafe.copy(alpha = 0.12f))
                        .border(1.dp, SafeHerStatusSafe.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = SafeHerStatusSafe,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = uiState.infoMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = SafeHerStatusSafe,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Registration: Display Name Field (Required for Guardian, optional for Personal User)
            if (!uiState.isSignInMode) {
                OutlinedTextField(
                    value = uiState.displayNameInput,
                    onValueChange = onDisplayNameChanged,
                    label = {
                        Text(
                            if (isGuardian) "Full Name (Required for Guardian)"
                            else stringResource(R.string.auth_name_label)
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            tint = SafeHerPurplePrimary
                        )
                    },
                    singleLine = true,
                    enabled = !uiState.isLoading,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Next
                    ),
                    keyboardActions = KeyboardActions(
                        onNext = { focusManager.moveFocus(FocusDirection.Down) }
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = SafeHerPurplePrimary,
                        unfocusedBorderColor = SafeHerBorderLavender,
                        focusedContainerColor = SafeHerSurfaceWhite,
                        unfocusedContainerColor = SafeHerSurfaceWhite
                    )
                )
                Spacer(modifier = Modifier.height(14.dp))
            }

            // Email Field
            OutlinedTextField(
                value = uiState.emailInput,
                onValueChange = onEmailChanged,
                label = { Text(stringResource(R.string.auth_email_label)) },
                placeholder = { Text(stringResource(R.string.auth_email_placeholder)) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Email,
                        contentDescription = null,
                        tint = SafeHerPurplePrimary
                    )
                },
                singleLine = true,
                enabled = !uiState.isLoading,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Next
                ),
                keyboardActions = KeyboardActions(
                    onNext = { focusManager.moveFocus(FocusDirection.Down) }
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = SafeHerPurplePrimary,
                    unfocusedBorderColor = SafeHerBorderLavender,
                    focusedContainerColor = SafeHerSurfaceWhite,
                    unfocusedContainerColor = SafeHerSurfaceWhite
                )
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Password Field
            OutlinedTextField(
                value = uiState.passwordInput,
                onValueChange = onPasswordChanged,
                label = { Text(stringResource(R.string.auth_password_label)) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        tint = SafeHerPurplePrimary
                    )
                },
                trailingIcon = {
                    IconButton(
                        onClick = onTogglePasswordVisibility,
                        modifier = Modifier.semantics {
                            contentDescription = if (uiState.isPasswordVisible) "Hide password" else "Show password"
                        }
                    ) {
                        Icon(
                            imageVector = if (uiState.isPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = null,
                            tint = SafeHerTextMuted
                        )
                    }
                },
                visualTransformation = if (uiState.isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                singleLine = true,
                enabled = !uiState.isLoading,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = if (uiState.isSignInMode) ImeAction.Done else ImeAction.Next
                ),
                keyboardActions = KeyboardActions(
                    onNext = { focusManager.moveFocus(FocusDirection.Down) },
                    onDone = {
                        focusManager.clearFocus()
                        if (uiState.isSignInMode) onSignInClick()
                    }
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = SafeHerPurplePrimary,
                    unfocusedBorderColor = SafeHerBorderLavender,
                    focusedContainerColor = SafeHerSurfaceWhite,
                    unfocusedContainerColor = SafeHerSurfaceWhite
                )
            )

            // Registration: Confirm Password Field
            if (!uiState.isSignInMode) {
                Spacer(modifier = Modifier.height(14.dp))
                OutlinedTextField(
                    value = uiState.confirmPasswordInput,
                    onValueChange = onConfirmPasswordChanged,
                    label = { Text(stringResource(R.string.auth_confirm_password_label)) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            tint = SafeHerPurplePrimary
                        )
                    },
                    trailingIcon = {
                        IconButton(
                            onClick = onToggleConfirmPasswordVisibility,
                            modifier = Modifier.semantics {
                                contentDescription = if (uiState.isConfirmPasswordVisible) "Hide confirm password" else "Show confirm password"
                            }
                        ) {
                            Icon(
                                imageVector = if (uiState.isConfirmPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = null,
                                tint = SafeHerTextMuted
                            )
                        }
                    },
                    visualTransformation = if (uiState.isConfirmPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    singleLine = true,
                    enabled = !uiState.isLoading,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            focusManager.clearFocus()
                            onSignUpClick()
                        }
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = SafeHerPurplePrimary,
                        unfocusedBorderColor = SafeHerBorderLavender,
                        focusedContainerColor = SafeHerSurfaceWhite,
                        unfocusedContainerColor = SafeHerSurfaceWhite
                    )
                )
            }

            // Forgot Password (in Sign In mode)
            if (uiState.isSignInMode) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        onClick = onForgotPasswordClick,
                        enabled = !uiState.isLoading
                    ) {
                        Text(
                            text = stringResource(R.string.auth_forgot_password),
                            style = MaterialTheme.typography.bodySmall,
                            color = SafeHerPurplePrimary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            } else {
                Spacer(modifier = Modifier.height(20.dp))
            }

            // Primary Action Button (Sign In / Sign Up)
            Button(
                onClick = {
                    focusManager.clearFocus()
                    if (uiState.isSignInMode) onSignInClick() else onSignUpClick()
                },
                enabled = !uiState.isLoading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .semantics {
                        contentDescription = if (uiState.isSignInMode) "Sign in button" else "Create account button"
                    },
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = SafeHerPurplePrimary)
            ) {
                if (uiState.isLoading) {
                    CircularProgressIndicator(
                        color = Color.White,
                        strokeWidth = 2.5.dp,
                        modifier = Modifier.size(24.dp)
                    )
                } else {
                    Text(
                        text = if (uiState.isSignInMode) {
                            stringResource(R.string.auth_sign_in_button)
                        } else {
                            stringResource(R.string.auth_sign_up_button)
                        },
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Divider: "or"
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                HorizontalDivider(modifier = Modifier.weight(1f), color = SafeHerBorderLavender)
                Text(
                    text = stringResource(R.string.auth_or_divider),
                    style = MaterialTheme.typography.bodySmall,
                    color = SafeHerTextMuted,
                    modifier = Modifier.padding(horizontal = 14.dp)
                )
                HorizontalDivider(modifier = Modifier.weight(1f), color = SafeHerBorderLavender)
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Google Sign-In Button
            OutlinedButton(
                onClick = onGoogleSignInClick,
                enabled = !uiState.isLoading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .semantics { contentDescription = "Continue with Google" },
                shape = RoundedCornerShape(16.dp),
                border = ButtonDefaults.outlinedButtonBorder.copy(
                    brush = androidx.compose.ui.graphics.SolidColor(SafeHerBorderLavender)
                ),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = SafeHerSurfaceWhite,
                    contentColor = SafeHerTextPrimary
                )
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    // Google Icon G representation
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(SafeHerPurplePrimary.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "G",
                            fontWeight = FontWeight.ExtraBold,
                            color = SafeHerPurplePrimary,
                            fontSize = 14.sp
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = stringResource(R.string.auth_google_button),
                        style = MaterialTheme.typography.labelLarge,
                        color = SafeHerTextPrimary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // Demo Mode Entry (if demo flavor)
            if (onDemoModeClick != null && BuildConfig.IS_DEMO_DEFAULT) {
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = onDemoModeClick,
                    enabled = !uiState.isLoading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
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

            Spacer(modifier = Modifier.height(24.dp))

            // Switch Mode Text Link
            Text(
                text = if (uiState.isSignInMode) {
                    stringResource(R.string.auth_switch_to_signup)
                } else {
                    stringResource(R.string.auth_switch_to_signin)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = SafeHerPurplePrimary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clickable { onToggleAuthMode() }
                    .padding(8.dp)
            )
        }
    }

    // Forgot Password Dialog
    if (uiState.showForgotPasswordDialog) {
        AlertDialog(
            onDismissRequest = onDismissForgotPassword,
            title = {
                Text(
                    text = stringResource(R.string.auth_reset_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column {
                    Text(
                        text = stringResource(R.string.auth_reset_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = SafeHerTextSecondary
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    OutlinedTextField(
                        value = uiState.resetEmailInput,
                        onValueChange = onResetEmailChanged,
                        label = { Text(stringResource(R.string.auth_email_label)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = SafeHerPurplePrimary,
                            unfocusedBorderColor = SafeHerBorderLavender
                        )
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = onSendPasswordReset,
                    enabled = !uiState.isResettingPassword,
                    colors = ButtonDefaults.buttonColors(containerColor = SafeHerPurplePrimary)
                ) {
                    if (uiState.isResettingPassword) {
                        CircularProgressIndicator(
                            color = Color.White,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(16.dp)
                        )
                    } else {
                        Text(stringResource(R.string.auth_reset_send))
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissForgotPassword) {
                    Text(stringResource(R.string.auth_cancel), color = SafeHerTextSecondary)
                }
            },
            containerColor = SafeHerSurfaceWhite,
            shape = RoundedCornerShape(20.dp)
        )
    }
}

@Preview(showBackground = true)
@Composable
fun AuthScreenPreview() {
    SafeHerTheme {
        AuthScreen(
            uiState = WelcomeUiState(),
            onToggleAuthMode = {},
            onEmailChanged = {},
            onPasswordChanged = {},
            onConfirmPasswordChanged = {},
            onDisplayNameChanged = {},
            onTogglePasswordVisibility = {},
            onToggleConfirmPasswordVisibility = {},
            onSignInClick = {},
            onSignUpClick = {},
            onGoogleSignInClick = {},
            onForgotPasswordClick = {},
            onDismissForgotPassword = {},
            onResetEmailChanged = {},
            onSendPasswordReset = {},
            onDemoModeClick = null,
            onBackClick = {}
        )
    }
}
