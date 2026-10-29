package com.safeher.app

import android.content.Context
import android.content.SharedPreferences
import com.safeher.app.data.auth.AuthResult
import com.safeher.app.data.auth.AuthState
import com.safeher.app.data.demo.DemoAuthRepository
import com.safeher.app.data.demo.DemoModeManager
import com.safeher.app.data.demo.DemoProfile
import com.safeher.app.data.demo.DemoRemoteGuardianRepository
import com.safeher.app.data.demo.DemoRemoteSosRepository
import com.safeher.app.data.location.LocationData
import com.safeher.app.data.remoteguardian.RemoteGuardian
import com.safeher.app.data.remotesos.RemoteAlertDeliveryState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.lang.reflect.Proxy

class DemoModeTest {

    private val testDispatcher = Dispatchers.Unconfined
    private lateinit var demoModeManager: DemoModeManager

    @Before
    fun setUp() {
        val memoryPrefs = mutableMapOf<String, Any>()
        val editorProxy = Proxy.newProxyInstance(
            SharedPreferences.Editor::class.java.classLoader,
            arrayOf(SharedPreferences.Editor::class.java)
        ) { _, method, args ->
            when (method.name) {
                "putBoolean" -> {
                    memoryPrefs[args[0] as String] = args[1] as Boolean
                }
                "putString" -> {
                    memoryPrefs[args[0] as String] = args[1] as String
                }
                "apply", "commit" -> {
                    // no-op
                }
            }
            Proxy.newProxyInstance(
                SharedPreferences.Editor::class.java.classLoader,
                arrayOf(SharedPreferences.Editor::class.java)
            ) { _, _, _ -> null }
        } as SharedPreferences.Editor

        val prefsProxy = Proxy.newProxyInstance(
            SharedPreferences::class.java.classLoader,
            arrayOf(SharedPreferences::class.java)
        ) { _, method, args ->
            when (method.name) {
                "getBoolean" -> memoryPrefs[args[0] as String] as? Boolean ?: args[1] as Boolean
                "getString" -> memoryPrefs[args[0] as String] as? String ?: args[1] as? String
                "edit" -> editorProxy
                else -> null
            }
        } as SharedPreferences

        demoModeManager = DemoModeManager.createForTesting(prefsProxy, initialDemoMode = true)
    }

    @Test
    fun demoModeManager_initialStateAndToggle() {
        assertTrue(demoModeManager.isDemoMode.value)
        assertEquals(DemoProfile.USER, demoModeManager.activeProfile.value)

        demoModeManager.setDemoMode(false)
        assertFalse(demoModeManager.isDemoMode.value)
    }

    @Test
    fun demoAuthRepository_emitsFictionalAuthenticatedUser() = runBlocking {
        val authRepo = DemoAuthRepository(demoModeManager, testDispatcher)

        val user = authRepo.getCurrentUser()
        assertNotNull(user)
        assertEquals("demo_user_sender_101", user?.uid)
        assertEquals("demo.user@safeher.local", user?.email)
        assertEquals("Demo User", user?.displayName)
        assertTrue(user?.isEmailVerified == true)

        // Explicit Sign-out
        authRepo.signOut()
        val authState = authRepo.authState.first()
        assertTrue(authState is AuthState.Unauthenticated)
        assertNull(authRepo.getCurrentUser())

        // Re-sign in with mock email
        val signInResult = authRepo.signInWithEmail("demo.user@safeher.local", "password123")
        assertTrue(signInResult is AuthResult.Success)
        val reAuthUser = authRepo.getCurrentUser()
        assertNotNull(reAuthUser)
    }

    @Test
    fun demoRemoteGuardianRepository_managesSimulatedCircle() = runBlocking {
        val guardianRepo = DemoRemoteGuardianRepository(demoModeManager, testDispatcher)

        // 1. Initial list has Demo Guardian (Priya)
        val initialGuardians = guardianRepo.getRemoteGuardiansFlow().first()
        assertEquals(1, initialGuardians.size)
        assertEquals("demo_guardian_priya_202", initialGuardians[0].guardianUid)
        assertEquals("Demo Guardian (Priya)", initialGuardians[0].guardianDisplayName)

        // 2. Create Invitation returns a word-phrase invite
        val inviteResult = guardianRepo.createInvitation()
        assertTrue(inviteResult.isSuccess)
        val invite = inviteResult.getOrThrow()
        assertTrue(invite.code.contains("-"))
        assertTrue(invite.code.length >= 10)
        assertTrue(invite.expiresAtMillis > System.currentTimeMillis())

        // 3. Accept Invitation adds linked guardian
        val acceptResult = guardianRepo.acceptInvitation(invite.code)
        assertTrue(acceptResult.isSuccess)
        val acceptedGuardian = acceptResult.getOrThrow()
        assertEquals("Demo Guardian (Ananya)", acceptedGuardian.guardianDisplayName)

        val updatedList = guardianRepo.getRemoteGuardiansFlow().first()
        assertEquals(2, updatedList.size)

        // 4. Revoke relationship
        val revokeResult = guardianRepo.revokeRelationship(acceptedGuardian.relationshipId)
        assertTrue(revokeResult.isSuccess)

        val afterRevoke = guardianRepo.getRemoteGuardiansFlow().first()
        assertEquals(1, afterRevoke.size)
    }

    @Test
    fun demoRemoteSosRepository_dispatchesAndProgressesDeliveryStates() = runBlocking {
        val sosRepo = DemoRemoteSosRepository(demoModeManager, testDispatcher)
        val guardians = listOf(
            RemoteGuardian(
                relationshipId = "demo_rel_priya",
                guardianUid = DemoProfile.GUARDIAN.uid,
                guardianDisplayName = DemoProfile.GUARDIAN.displayName,
                guardianEmail = DemoProfile.GUARDIAN.email,
                status = "ACTIVE"
            )
        )
        val location = LocationData(
            latitude = 12.9716,
            longitude = 77.5946,
            accuracyMeters = 8.5f,
            timestampMillis = 1000000L,
            isFresh = true,
            provider = "gps"
        )

        val dispatchResult = sosRepo.dispatchRemoteSos(
            recipients = guardians,
            messageText = "EMERGENCY: Test SOS [SIMULATED]",
            location = location,
            includeLocation = true,
            idempotencyKey = "key_demo_1"
        )

        assertTrue(dispatchResult.isSuccess)
        val alert = dispatchResult.getOrThrow()
        assertTrue(alert.alertId.startsWith("demo_alert_"))
        assertEquals(1, alert.recipientsCount)

        // Guardian acknowledges alert
        sosRepo.simulateGuardianAcknowledgement(alert.alertId)
        val ackStatuses = sosRepo.observeDeliveryStatuses(alert.alertId).first()
        assertEquals(RemoteAlertDeliveryState.ACKNOWLEDGED, ackStatuses[0].status)

        // Verify alert details
        val detailsResult = sosRepo.getAlertDetails(alert.alertId)
        assertTrue(detailsResult.isSuccess)
        val details = detailsResult.getOrThrow()
        assertTrue(details.isAcknowledgedByMe)
        assertEquals(12.9716, details.latitude!!, 0.001)
    }

    @Test
    fun demoMode_resetDemoDataRestoresDefaults() = runBlocking {
        val authRepo = DemoAuthRepository(demoModeManager, testDispatcher)
        val guardianRepo = DemoRemoteGuardianRepository(demoModeManager, testDispatcher)
        val sosRepo = DemoRemoteSosRepository(demoModeManager, testDispatcher)

        // Mutate states
        authRepo.signOut()
        guardianRepo.acceptInvitation("DEMO1234")

        // Trigger Reset
        demoModeManager.resetDemoData()

        assertEquals(DemoProfile.USER, demoModeManager.activeProfile.value)
        val user = authRepo.getCurrentUser()
        assertEquals("demo_user_sender_101", user?.uid)
        val guardians = guardianRepo.getRemoteGuardiansFlow().first()
        assertEquals(1, guardians.size)
        assertEquals("demo_guardian_priya_202", guardians[0].guardianUid)
    }
}
