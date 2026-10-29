package com.safeher.app.ui.screens.welcome

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.safeher.app.BuildConfig
import com.safeher.app.data.RepositoryProvider

@Composable
fun WelcomeRootScreen(
    onSetupOrAuthFinished: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    val viewModel: WelcomeViewModel = viewModel(
        factory = remember(context) {
            object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    val authRepo = RepositoryProvider.getAuthRepository(context)
                    val onboardingRepo = RepositoryProvider.getOnboardingPreferencesRepository(context)
                    val guardianRepo = RepositoryProvider.getGuardianRepository(context)
                    val sessionManager = RepositoryProvider.getUserSessionManager(context)
                    val sosPrefsRepo = RepositoryProvider.getSosPreferencesRepository(context)
                    val remoteGuardianRepo = RepositoryProvider.getRemoteGuardianRepository(context)
                    val demoModeManager = if (BuildConfig.IS_DEMO_DEFAULT) {
                        RepositoryProvider.getDemoModeManager(context)
                    } else {
                        null
                    }
                    @Suppress("UNCHECKED_CAST")
                    return WelcomeViewModel(
                        authRepository = authRepo,
                        onboardingPreferencesRepository = onboardingRepo,
                        guardianRepository = guardianRepo,
                        sessionManager = sessionManager,
                        sosPreferencesRepository = sosPrefsRepo,
                        demoModeManager = demoModeManager,
                        remoteGuardianRepository = remoteGuardianRepo
                    ) as T
                }
            }
        }
    )

    val uiState by viewModel.uiState.collectAsState()

    // Back handling
    BackHandler(enabled = uiState.currentStep != WelcomeStep.WELCOME) {
        when (uiState.currentStep) {
            WelcomeStep.ONBOARDING -> viewModel.onSlidePrevious()
            WelcomeStep.AUTH,
            WelcomeStep.USER_LOGIN,
            WelcomeStep.USER_SIGN_UP,
            WelcomeStep.GUARDIAN_LOGIN,
            WelcomeStep.GUARDIAN_SIGN_UP -> viewModel.onBackFromAuth()
            WelcomeStep.FIRST_LOGIN_SETUP -> {
                // If the user presses back during setup checklist, defer setup so they aren't trapped
                val uid = RepositoryProvider.getUserSessionManager(context).currentUserId ?: ""
                viewModel.skipSetup(uid)
                onSetupOrAuthFinished()
            }
            WelcomeStep.GUARDIAN_CONNECT -> {
                val uid = RepositoryProvider.getUserSessionManager(context).currentUserId ?: ""
                viewModel.skipGuardianConnect(uid)
                onSetupOrAuthFinished()
            }
            WelcomeStep.WELCOME -> {
                // Let default system back occur
            }
        }
    }

    when (uiState.currentStep) {
        WelcomeStep.WELCOME -> {
            WelcomeScreen(
                onContinueAsUser = { viewModel.onContinueAsUser() },
                onContinueAsGuardian = { viewModel.onContinueAsGuardian() },
                onRoleSlideChanged = { page -> viewModel.onSlideChangedInWelcome(page) },
                onGetStartedClick = { viewModel.onGetStartedClicked() },
                onAlreadyHaveAccountClick = { viewModel.onAlreadyHaveAccountClicked() },
                onDemoModeClick = if (BuildConfig.IS_DEMO_DEFAULT) {
                    { viewModel.continueAsDemoUser() }
                } else {
                    null
                },
                modifier = modifier
            )
        }
        WelcomeStep.ONBOARDING -> {
            OnboardingSlidesScreen(
                currentSlideIndex = uiState.slideIndex,
                onSlideChanged = { viewModel.setSlideIndex(it) },
                onSkipClick = { viewModel.onSkipOnboarding() },
                onFinishClick = { viewModel.onFinishOnboarding() },
                onBackClick = { viewModel.onSlidePrevious() },
                modifier = modifier
            )
        }
        WelcomeStep.AUTH,
        WelcomeStep.USER_LOGIN,
        WelcomeStep.USER_SIGN_UP,
        WelcomeStep.GUARDIAN_LOGIN,
        WelcomeStep.GUARDIAN_SIGN_UP -> {
            AuthScreen(
                uiState = uiState,
                onToggleAuthMode = { viewModel.toggleAuthMode() },
                onEmailChanged = { viewModel.updateEmail(it) },
                onPasswordChanged = { viewModel.updatePassword(it) },
                onConfirmPasswordChanged = { viewModel.updateConfirmPassword(it) },
                onDisplayNameChanged = { viewModel.updateDisplayName(it) },
                onTogglePasswordVisibility = { viewModel.togglePasswordVisibility() },
                onToggleConfirmPasswordVisibility = { viewModel.toggleConfirmPasswordVisibility() },
                onSignInClick = { viewModel.signInWithEmail() },
                onSignUpClick = { viewModel.signUpWithEmail() },
                onGoogleSignInClick = {
                    val activity = context as? Activity ?: context
                    viewModel.signInWithGoogle(activity, BuildConfig.FIREBASE_WEB_CLIENT_ID)
                },
                onForgotPasswordClick = { viewModel.openForgotPasswordDialog() },
                onDismissForgotPassword = { viewModel.dismissForgotPasswordDialog() },
                onResetEmailChanged = { viewModel.updateResetEmail(it) },
                onSendPasswordReset = { viewModel.sendPasswordReset() },
                onDemoModeClick = if (BuildConfig.IS_DEMO_DEFAULT) {
                    { viewModel.continueAsDemoUser() }
                } else {
                    null
                },
                onBackClick = { viewModel.onBackFromAuth() },
                modifier = modifier
            )
        }
        WelcomeStep.FIRST_LOGIN_SETUP -> {
            val uid = RepositoryProvider.getUserSessionManager(context).currentUserId ?: ""
            FirstLoginSetupScreen(
                uiState = uiState,
                onCompleteSetupClick = {
                    viewModel.completeSetup(uid)
                    onSetupOrAuthFinished()
                },
                onSkipSetupClick = {
                    viewModel.skipSetup(uid)
                    onSetupOrAuthFinished()
                },
                onOpenAddGuardianClick = { viewModel.openAddGuardianDialog() },
                onDismissAddGuardianClick = { viewModel.dismissAddGuardianDialog() },
                onGuardianNameChanged = { viewModel.updateGuardianName(it) },
                onGuardianPhoneChanged = { viewModel.updateGuardianPhone(it) },
                onGuardianRelationshipChanged = { viewModel.updateGuardianRelationship(it) },
                onSaveGuardianClick = { viewModel.saveQuickGuardian() },
                onDirectSmsConsentChanged = { viewModel.setDirectSmsConsent(it) },
                modifier = modifier
            )
        }
        WelcomeStep.GUARDIAN_CONNECT -> {
            val uid = RepositoryProvider.getUserSessionManager(context).currentUserId ?: ""
            GuardianConnectScreen(
                uiState = uiState,
                onCodeChanged = { viewModel.updateInviteCode(it) },
                onConnectClick = {
                    viewModel.submitGuardianConnectCode(
                        onSuccess = {
                            viewModel.skipGuardianConnect(uid)
                            onSetupOrAuthFinished()
                        }
                    )
                },
                onDismissError = { viewModel.dismissGuardianConnectError() },
                onGoToDashboardClick = {
                    viewModel.skipGuardianConnect(uid)
                    onSetupOrAuthFinished()
                },
                onSignOutClick = {
                    viewModel.signOut()
                },
                modifier = modifier
            )
        }
    }
}
