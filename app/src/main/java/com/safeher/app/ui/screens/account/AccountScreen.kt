package com.safeher.app.ui.screens.account

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mail
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.safeher.app.data.auth.AuthState
import com.safeher.app.data.auth.AuthUser
import com.safeher.app.data.remoteguardian.GuardianInvite
import com.safeher.app.data.remoteguardian.RemoteGuardian
import com.safeher.app.ui.theme.SafeHerBorderLavender
import com.safeher.app.ui.theme.SafeHerPinkAccent
import com.safeher.app.ui.theme.SafeHerPinkContainer
import com.safeher.app.ui.theme.SafeHerPurpleContainer
import com.safeher.app.ui.theme.SafeHerPurpleDark
import com.safeher.app.ui.theme.SafeHerPurpleLight
import com.safeher.app.ui.theme.SafeHerPurplePrimary
import com.safeher.app.ui.theme.SafeHerSosCrimson
import com.safeher.app.ui.theme.SafeHerSosTrack
import com.safeher.app.ui.theme.SafeHerSurfaceWhite
import com.safeher.app.ui.theme.SafeHerTextPrimary
import com.safeher.app.ui.theme.SafeHerTextSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountScreen(
    viewModel: AccountViewModel,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Account",
                            fontWeight = FontWeight.Bold,
                            color = SafeHerPurpleDark
                        )
                        Text(
                            text = "Profile, remote circle & session",
                            style = MaterialTheme.typography.labelSmall,
                            color = SafeHerTextSecondary
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = SafeHerPurpleDark
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = SafeHerSurfaceWhite
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            // Error banner
            AnimatedVisibility(visible = uiState.errorMessage != null) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = SafeHerSosTrack),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = SafeHerSosCrimson)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = uiState.errorMessage ?: "",
                            color = SafeHerSosCrimson,
                            fontSize = 13.sp,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { viewModel.clearMessages() }, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Dismiss", tint = SafeHerSosCrimson)
                        }
                    }
                }
            }

            // Info banner
            AnimatedVisibility(visible = uiState.infoMessage != null) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = SafeHerPurpleContainer),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = SafeHerPurplePrimary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = uiState.infoMessage ?: "",
                            color = SafeHerPurpleDark,
                            fontSize = 13.sp,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { viewModel.clearMessages() }, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Dismiss", tint = SafeHerPurpleDark)
                        }
                    }
                }
            }

            // Demo Mode Controller Card (Only shown in demo build flavor)
            if (com.safeher.app.BuildConfig.IS_DEMO_DEFAULT && uiState.isDemoMode) {
                DemoModeControllerCard(
                    isDemoMode = uiState.isDemoMode,
                    onSetDemoMode = { viewModel.setDemoMode(it) },
                    onResetDemoData = { viewModel.resetDemoData() }
                )
            }

            when (val auth = uiState.authState) {
                is AuthState.Loading -> {
                    Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = SafeHerPurplePrimary)
                    }
                }
                is AuthState.Unauthenticated -> {
                    UnauthenticatedSection(
                        uiState = uiState,
                        onToggleMode = { viewModel.toggleAuthMode() },
                        onEmailChange = { viewModel.updateEmail(it) },
                        onPasswordChange = { viewModel.updatePassword(it) },
                        onDisplayNameChange = { viewModel.updateDisplayName(it) },
                        onSignIn = { viewModel.signInWithEmail() },
                        onSignUp = { viewModel.signUpWithEmail() },
                        onForgotPassword = { viewModel.sendPasswordReset() },
                        onGoogleSignIn = {
                            viewModel.signInWithGoogle(context, com.safeher.app.BuildConfig.FIREBASE_WEB_CLIENT_ID)
                        }
                    )
                }
                is AuthState.Authenticated -> {
                    AuthenticatedSection(
                        user = auth.user,
                        guardians = uiState.remoteGuardians,
                        onSendVerification = { viewModel.sendEmailVerification() },
                        onInviteClick = { viewModel.openInviteDialog() },
                        onAcceptInviteClick = { viewModel.openAcceptInviteDialog() },
                        onRevokeRelationship = { viewModel.revokeRelationship(it) },
                        onSignOut = { viewModel.signOut() }
                    )
                }
            }
        }
    }

    // Invite code dialog
    if (uiState.showInviteDialog) {
        InviteGuardianDialog(
            invite = uiState.generatedInvite,
            isGenerating = uiState.isGeneratingInvite,
            error = uiState.inviteGenerationError,
            onRetry = { viewModel.retryGenerateInvite() },
            onDismiss = { viewModel.dismissInviteDialog() },
            onCopy = { code ->
                clipboardManager.setText(AnnotatedString(code))
            },
            onShare = { code ->
                val sendIntent = Intent().apply {
                    action = Intent.ACTION_SEND
                    putExtra(
                        Intent.EXTRA_TEXT,
                        "Join my SafeHer remote guardian circle. Enter this temporary invitation phrase in SafeHer: $code (single-use, expires in 48 hours). Share only with the intended guardian."
                    )
                    type = "text/plain"
                }
                val shareIntent = Intent.createChooser(sendIntent, "Share SafeHer invitation phrase")
                context.startActivity(shareIntent)
            }
        )
    }

    // Accept invite dialog
    if (uiState.showAcceptInviteDialog) {
        AcceptGuardianDialog(
            code = uiState.inviteCodeInput,
            isLoading = uiState.isAcceptingInvite,
            error = uiState.acceptInviteError,
            onCodeChange = { viewModel.updateInviteCode(it) },
            onDismiss = { viewModel.dismissAcceptInviteDialog() },
            onSubmit = { viewModel.submitAcceptInvite() }
        )
    }
}

@Composable
private fun UnauthenticatedSection(
    uiState: AccountUiState,
    onToggleMode: () -> Unit,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onDisplayNameChange: (String) -> Unit,
    onSignIn: () -> Unit,
    onSignUp: () -> Unit,
    onForgotPassword: () -> Unit,
    onGoogleSignIn: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = SafeHerSurfaceWhite),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = "SafeHer Cloud Services",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = SafeHerPurpleDark
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Sign in to enable push-based remote guardian alerts, live delivery confirmation, and real-time circle sync. All local features (SMS SOS, Room contacts, and journeys) remain available offline.",
                fontSize = 13.sp,
                color = SafeHerTextSecondary,
                lineHeight = 18.sp
            )

            Spacer(modifier = Modifier.height(16.dp))

            TabRow(
                selectedTabIndex = if (uiState.isSignInMode) 0 else 1,
                containerColor = SafeHerPurpleContainer,
                contentColor = SafeHerPurplePrimary,
                modifier = Modifier.clip(RoundedCornerShape(12.dp))
            ) {
                Tab(
                    selected = uiState.isSignInMode,
                    onClick = { if (!uiState.isSignInMode) onToggleMode() },
                    text = { Text("Sign In", fontWeight = FontWeight.SemiBold) }
                )
                Tab(
                    selected = !uiState.isSignInMode,
                    onClick = { if (uiState.isSignInMode) onToggleMode() },
                    text = { Text("Create Account", fontWeight = FontWeight.SemiBold) }
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            if (!uiState.isSignInMode) {
                OutlinedTextField(
                    value = uiState.displayNameInput,
                    onValueChange = onDisplayNameChange,
                    label = { Text("Display Name") },
                    leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            OutlinedTextField(
                value = uiState.emailInput,
                onValueChange = onEmailChange,
                label = { Text("Email Address") },
                leadingIcon = { Icon(Icons.Default.Mail, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = uiState.passwordInput,
                onValueChange = onPasswordChange,
                label = { Text("Password (min 6 characters)") },
                leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )

            if (uiState.isSignInMode) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onForgotPassword) {
                        Text("Forgot Password?", fontSize = 12.sp, color = SafeHerPurplePrimary)
                    }
                }
            } else {
                Spacer(modifier = Modifier.height(16.dp))
            }

            Button(
                onClick = { if (uiState.isSignInMode) onSignIn() else onSignUp() },
                enabled = !uiState.isLoading,
                colors = ButtonDefaults.buttonColors(containerColor = SafeHerPurplePrimary),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                if (uiState.isLoading) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Text(
                        text = if (uiState.isSignInMode) "Sign In" else "Create Account",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedButton(
                onClick = onGoogleSignIn,
                enabled = !uiState.isLoading,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                Text("Continue with Google", color = SafeHerTextPrimary, fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
private fun AuthenticatedSection(
    user: AuthUser,
    guardians: List<RemoteGuardian>,
    onSendVerification: () -> Unit,
    onInviteClick: () -> Unit,
    onAcceptInviteClick: () -> Unit,
    onRevokeRelationship: (String) -> Unit,
    onSignOut: () -> Unit
) {
    // 1. User Profile Card
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = SafeHerSurfaceWhite),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(SafeHerPurpleContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Person, contentDescription = null, tint = SafeHerPurplePrimary, modifier = Modifier.size(28.dp))
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = user.displayLabel,
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                        color = SafeHerTextPrimary
                    )
                    Text(
                        text = user.email ?: "No email associated",
                        fontSize = 13.sp,
                        color = SafeHerTextSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Verification status row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                val isVerified = user.isEmailVerified
                val badgeColor = if (isVerified) SafeHerPurplePrimary else SafeHerSosCrimson
                val badgeText = if (isVerified) "Email Verified" else "Email Unverified"

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (isVerified) SafeHerPurpleContainer else SafeHerSosTrack)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(badgeText, color = badgeColor, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.weight(1f))

                if (!isVerified) {
                    TextButton(onClick = onSendVerification) {
                        Text("Send Verification", fontSize = 12.sp, color = SafeHerPurplePrimary)
                    }
                }
            }
        }
    }

    Spacer(modifier = Modifier.height(20.dp))

    // 2. Remote Guardian Management Section
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = SafeHerSurfaceWhite),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = "Remote Guardian Circle",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = SafeHerPurpleDark
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Authenticated SafeHer users who receive push notifications with live delivery tracking when you trigger an emergency alert. (Separate from local address book contacts).",
                fontSize = 12.sp,
                color = SafeHerTextSecondary,
                lineHeight = 16.sp
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Honest Availability State Notice
            Card(
                colors = CardDefaults.cardColors(containerColor = SafeHerPurpleContainer.copy(alpha = 0.45f)),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = SafeHerPurplePrimary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Share invitation phrases only with your intended guardian. Phrases expire in 48 hours and work once. Local SMS SOS and on-device guardians remain separate from remote links.",
                        fontSize = 11.sp,
                        color = SafeHerPurpleDark,
                        lineHeight = 15.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = onInviteClick,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = SafeHerPurplePrimary),
                    modifier = Modifier.weight(1f).height(44.dp)
                ) {
                    Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Invite Guardian", fontSize = 13.sp)
                }

                Spacer(modifier = Modifier.width(10.dp))

                OutlinedButton(
                    onClick = onAcceptInviteClick,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f).height(44.dp)
                ) {
                    Icon(Icons.Default.QrCode, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Enter Code", fontSize = 13.sp)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (guardians.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(SafeHerPurpleContainer.copy(alpha = 0.4f))
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No remote guardians linked yet.\nInvite a trusted friend or family member who also uses SafeHer to receive push alerts.",
                        fontSize = 12.sp,
                        color = SafeHerPurpleDark,
                        textAlign = TextAlign.Center,
                        lineHeight = 16.sp
                    )
                }
            } else {
                guardians.forEach { guardian ->
                    RemoteGuardianRow(
                        guardian = guardian,
                        onRevoke = { onRevokeRelationship(guardian.relationshipId) }
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = SafeHerPurpleLight.copy(alpha = 0.3f))
                }
            }
        }
    }

    Spacer(modifier = Modifier.height(24.dp))

    // 3. Sign Out Button
    OutlinedButton(
        onClick = onSignOut,
        colors = ButtonDefaults.outlinedButtonColors(contentColor = SafeHerSosCrimson),
        border = BorderStroke(1.dp, SafeHerSosCrimson.copy(alpha = 0.5f)),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().height(48.dp)
    ) {
        Text("Sign Out of Account", fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun RemoteGuardianRow(
    guardian: RemoteGuardian,
    onRevoke: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = guardian.guardianDisplayName,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
                color = SafeHerTextPrimary
            )
            if (guardian.guardianEmail.isNotBlank()) {
                Text(
                    text = guardian.guardianEmail,
                    fontSize = 12.sp,
                    color = SafeHerTextSecondary
                )
            }
            Text(
                text = "Status: ${if (guardian.isActive) "Active Link" else "Revoked"}",
                fontSize = 11.sp,
                color = if (guardian.isActive) SafeHerPurplePrimary else SafeHerSosCrimson
            )
        }

        TextButton(onClick = onRevoke) {
            Text("Revoke", color = SafeHerSosCrimson, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun InviteGuardianDialog(
    invite: GuardianInvite?,
    isGenerating: Boolean,
    error: String?,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
    onCopy: (String) -> Unit,
    onShare: (String) -> Unit
) {
    var isCopied by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Guardian Invitation Phrase",
                fontWeight = FontWeight.Bold,
                color = SafeHerPurpleDark
            )
        },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Share this temporary phrase only with your intended Guardian. It is single-use, expires after 48 hours, and is shown only after Firestore confirms creation. Do not post it publicly.",
                    fontSize = 13.sp,
                    color = SafeHerTextSecondary,
                    lineHeight = 18.sp
                )

                Spacer(modifier = Modifier.height(16.dp))

                when {
                    isGenerating -> {
                        CircularProgressIndicator(color = SafeHerPurplePrimary)
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Creating invitation in Firestore...",
                            fontSize = 12.sp,
                            color = SafeHerTextSecondary
                        )
                    }

                    error != null -> {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = SafeHerSosTrack),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Warning,
                                        contentDescription = null,
                                        tint = SafeHerSosCrimson,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Generation Failed",
                                        fontWeight = FontWeight.Bold,
                                        color = SafeHerSosCrimson,
                                        fontSize = 13.sp
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = error,
                                    fontSize = 12.sp,
                                    color = SafeHerSosCrimson,
                                    lineHeight = 16.sp
                                )
                            }
                        }
                    }

                    invite != null -> {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(SafeHerPurpleContainer)
                                .padding(horizontal = 24.dp, vertical = 12.dp)
                        ) {
                            Text(
                                text = invite.code,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = SafeHerPurplePrimary,
                                letterSpacing = 1.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        val expiryDate = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())
                            .format(Date(invite.expiresAtMillis))
                        Text(
                            text = "Expires: $expiryDate (48 hours)",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = SafeHerTextSecondary
                        )

                        if (isCopied) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Code copied to clipboard!",
                                fontSize = 11.sp,
                                color = SafeHerPurplePrimary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            when {
                error != null -> {
                    Button(
                        onClick = onRetry,
                        colors = ButtonDefaults.buttonColors(containerColor = SafeHerPurplePrimary)
                    ) {
                        Text("Retry")
                    }
                }
                invite != null -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = {
                                onCopy(invite.code)
                                isCopied = true
                            }
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Copy")
                        }
                        Button(
                            onClick = { onShare(invite.code) },
                            colors = ButtonDefaults.buttonColors(containerColor = SafeHerPurplePrimary)
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Share")
                        }
                    }
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(if (error != null) "Cancel" else "Close")
            }
        },
        shape = RoundedCornerShape(20.dp)
    )
}

@Composable
private fun AcceptGuardianDialog(
    code: String,
    isLoading: Boolean,
    error: String? = null,
    onCodeChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onSubmit: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Accept Guardian Invitation",
                fontWeight = FontWeight.Bold,
                color = SafeHerPurpleDark
            )
        },
        text = {
            Column {
                Text(
                    text = "Enter the exact invitation phrase shared by the Personal Safety User (example format: BLUE-TIGER-42-MAPLE).",
                    fontSize = 13.sp,
                    color = SafeHerTextSecondary,
                    lineHeight = 18.sp
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = code,
                    onValueChange = onCodeChange,
                    label = { Text("Invitation phrase") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                if (error != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = error,
                        color = SafeHerSosCrimson,
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Card(
                    colors = CardDefaults.cardColors(containerColor = SafeHerPurpleContainer.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.Top) {
                        Icon(Icons.Default.Info, contentDescription = null, tint = SafeHerPurplePrimary, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Consent notice: By accepting, you agree to receive push notifications when this user triggers an emergency alert.",
                            fontSize = 11.sp,
                            color = SafeHerPurpleDark,
                            lineHeight = 15.sp
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onSubmit,
                enabled = code.length >= 10 && !isLoading,
                colors = ButtonDefaults.buttonColors(containerColor = SafeHerPurplePrimary)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                } else {
                    Text("Accept Link")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
        shape = RoundedCornerShape(20.dp)
    )
}

@Composable
private fun DemoModeControllerCard(
    isDemoMode: Boolean,
    onSetDemoMode: (Boolean) -> Unit,
    onResetDemoData: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isDemoMode) SafeHerPurpleContainer.copy(alpha = 0.7f) else SafeHerSurfaceWhite
        ),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(
            1.5.dp,
            if (isDemoMode) SafeHerPurplePrimary else SafeHerBorderLavender
        ),
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(if (isDemoMode) SafeHerPurplePrimary else SafeHerPurpleContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (isDemoMode) "🧪" else "🚀",
                        fontSize = 18.sp
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isDemoMode) "SafeHer Demo Mode (Active)" else "Evaluator Demo Mode",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = SafeHerPurpleDark
                    )
                    Text(
                        text = if (isDemoMode) {
                            "Self-contained local simulation. No Firebase or network required."
                        } else {
                            "Test remote guardian features without Firebase credentials."
                        },
                        fontSize = 11.sp,
                        color = SafeHerTextSecondary
                    )
                }
            }

            if (isDemoMode) {
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = SafeHerPurpleLight.copy(alpha = 0.4f))
                Spacer(modifier = Modifier.height(12.dp))

                // Demo Actions: Reset and Exit
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onResetDemoData,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Reset Demo Data", fontSize = 11.sp)
                    }
                    TextButton(
                        onClick = { onSetDemoMode(false) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Exit Demo Mode", fontSize = 11.sp, color = SafeHerTextSecondary)
                    }
                }
            } else {
                Spacer(modifier = Modifier.height(10.dp))
                Button(
                    onClick = { onSetDemoMode(true) },
                    colors = ButtonDefaults.buttonColors(containerColor = SafeHerPurplePrimary),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Enter Demo Mode", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
