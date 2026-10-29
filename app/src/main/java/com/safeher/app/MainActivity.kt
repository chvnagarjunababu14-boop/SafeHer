package com.safeher.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.safeher.app.data.RepositoryProvider
import com.safeher.app.data.auth.AuthState
import com.safeher.app.data.session.UserRole
import com.safeher.app.ui.screens.guardian.GuardianDashboardScreen
import com.safeher.app.ui.screens.home.HomeScreen
import com.safeher.app.ui.screens.welcome.WelcomeRootScreen
import com.safeher.app.ui.theme.SafeHerPurplePrimary
import com.safeher.app.ui.theme.SafeHerTheme

class MainActivity : ComponentActivity() {

    private var batteryChangeReceiver: com.safeher.app.receiver.BatteryChangeReceiver? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SafeHerTheme {
                SafeHerApp()
            }
        }
    }

    override fun onStart() {
        super.onStart()
        if (batteryChangeReceiver == null) {
            batteryChangeReceiver = com.safeher.app.receiver.BatteryChangeReceiver.register(this)
            // Seed evaluator with current sticky battery state (no fire on first observation).
            val reader = com.safeher.app.data.battery.AndroidBatteryStateReader(applicationContext)
            RepositoryProvider.getBatteryAlertManager(this).onBatteryChanged(reader.readBatteryInfo())
        }
    }

    override fun onStop() {
        batteryChangeReceiver?.let {
            try {
                unregisterReceiver(it)
            } catch (_: Exception) {
                // Already unregistered
            }
        }
        batteryChangeReceiver = null
        super.onStop()
    }
}

@Composable
fun SafeHerApp() {
    val context = LocalContext.current
    val authRepo = remember(context) { RepositoryProvider.getAuthRepository(context) }
    val sessionManager = remember(context) { RepositoryProvider.getUserSessionManager(context) }
    val onboardingRepo = remember(context) { RepositoryProvider.getOnboardingPreferencesRepository(context) }

    val authState by authRepo.authState.collectAsState()
    val activeUserId by sessionManager.activeUserId.collectAsState()

    var setupUpdateKey by remember { mutableIntStateOf(0) }

    when {
        authState is AuthState.Loading -> {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(SafeHerPurplePrimary.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = null,
                            tint = SafeHerPurplePrimary,
                            modifier = Modifier.size(38.dp)
                        )
                    }
                    CircularProgressIndicator(
                        modifier = Modifier.size(88.dp),
                        color = SafeHerPurplePrimary,
                        strokeWidth = 3.dp
                    )
                }
            }
        }

        authState is AuthState.Authenticated && !activeUserId.isNullOrBlank() -> {
            val uid = activeUserId ?: ""
            // roleRevision invalidates Compose remember after setUserRole() so User vs Guardian
            // routing cannot stick on a pre-auth default while auth state has already flipped.
            val roleRevision by sessionManager.roleRevision.collectAsState()
            val role = remember(uid, setupUpdateKey, roleRevision) {
                sessionManager.getUserRole(uid)
            }
            val isSetupDone = remember(uid, setupUpdateKey, roleRevision) {
                onboardingRepo.isUserSetupCompleted(uid)
            }
            val isNewRegistration = remember(uid, setupUpdateKey, roleRevision) {
                sessionManager.isNewRegistration(uid)
            }

            // Both roles complete first-run setup (User checklist / Guardian connect) before dashboards.
            if (!isSetupDone && isNewRegistration) {
                WelcomeRootScreen(
                    onSetupOrAuthFinished = {
                        setupUpdateKey++
                    }
                )
            } else if (role == UserRole.GUARDIAN) {
                GuardianDashboardScreen()
            } else {
                HomeScreen()
            }
        }

        else -> {
            // Unauthenticated: Always show Welcome/Onboarding/Auth flow.
            // Private screens (Home, Map, Journeys, Contacts, More) are never exposed.
            WelcomeRootScreen(
                onSetupOrAuthFinished = {
                    setupUpdateKey++
                }
            )
        }
    }
}
