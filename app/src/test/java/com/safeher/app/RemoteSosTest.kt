package com.safeher.app

import com.safeher.app.data.location.LocationData
import com.safeher.app.data.remoteguardian.GuardianInvite
import com.safeher.app.data.remoteguardian.RemoteGuardian
import com.safeher.app.data.remoteguardian.RemoteGuardianRepository
import com.safeher.app.data.remotesos.RecipientDeliveryInfo
import com.safeher.app.data.remotesos.RemoteAlertDeliveryState
import com.safeher.app.data.remotesos.RemoteAlertDetails
import com.safeher.app.data.remotesos.RemoteSosDispatchResult
import com.safeher.app.data.remotesos.RemoteSosRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FakeRemoteGuardianRepository : RemoteGuardianRepository {
    private val guardians = mutableListOf<RemoteGuardian>()
    private val flow = MutableStateFlow<List<RemoteGuardian>>(emptyList())

    fun addRemoteGuardian(guardian: RemoteGuardian) {
        guardians.add(guardian)
        flow.value = guardians.toList()
    }

    override fun getRemoteGuardiansFlow(): Flow<List<RemoteGuardian>> = flow

    override suspend fun createInvitation(): Result<GuardianInvite> {
        return Result.success(GuardianInvite("inv_1", "SAFE1234", System.currentTimeMillis() + 100000))
    }

    override suspend fun acceptInvitation(code: String): Result<RemoteGuardian> {
        if (code != "VALID123") return Result.failure(IllegalArgumentException("Invalid code"))
        val g = RemoteGuardian("rel_new", "uid_guardian", "New Guardian", "g@test.com", "ACTIVE")
        addRemoteGuardian(g)
        return Result.success(g)
    }

    override suspend fun revokeRelationship(relationshipId: String): Result<Unit> {
        guardians.removeAll { it.relationshipId == relationshipId }
        flow.value = guardians.toList()
        return Result.success(Unit)
    }

    override suspend fun refreshRemoteGuardians(): Result<List<RemoteGuardian>> {
        return Result.success(guardians.toList())
    }
}

class FakeRemoteSosRepository : RemoteSosRepository {
    var lastDispatchedLocation: LocationData? = null
    var lastIdempotencyKey: String? = null
    var dispatchCallCount = 0

    private val deliveryFlow = MutableStateFlow<List<RecipientDeliveryInfo>>(emptyList())

    fun updateDeliveryStatuses(statuses: List<RecipientDeliveryInfo>) {
        deliveryFlow.value = statuses
    }

    override suspend fun dispatchRemoteSos(
        recipients: List<RemoteGuardian>,
        messageText: String,
        location: LocationData?,
        includeLocation: Boolean,
        idempotencyKey: String
    ): Result<RemoteSosDispatchResult> {
        dispatchCallCount++
        lastIdempotencyKey = idempotencyKey
        lastDispatchedLocation = if (includeLocation) location else null

        val active = recipients.filter { it.isActive }
        if (active.isEmpty()) {
            return Result.failure(IllegalArgumentException("No active remote guardians"))
        }

        val statuses = active.map {
            RecipientDeliveryInfo(it.guardianUid, it.guardianDisplayName, RemoteAlertDeliveryState.QUEUED)
        }
        deliveryFlow.value = statuses

        return Result.success(
            RemoteSosDispatchResult(
                alertId = "alert_100",
                isIdempotentReplay = false,
                recipientsCount = active.size,
                fcmAcceptedCount = active.size,
                fcmFailedCount = 0,
                authorizedRecipients = active.map { it.guardianUid }
            )
        )
    }

    override fun observeDeliveryStatuses(alertId: String): Flow<List<RecipientDeliveryInfo>> = deliveryFlow

    override suspend fun getAlertDetails(alertId: String): Result<RemoteAlertDetails> {
        return Result.success(
            RemoteAlertDetails(
                alertId = alertId,
                senderUid = "sender_1",
                senderDisplayName = "Jane",
                messageText = "Help me",
                hasLocation = true,
                latitude = 12.9716,
                longitude = 77.5946,
                accuracyMeters = 10f,
                isAcknowledgedByMe = false
            )
        )
    }

    override suspend fun reportAlertReceived(alertId: String): Result<Unit> {
        return Result.success(Unit)
    }

    override suspend fun acknowledgeAlert(alertId: String, deviceModel: String): Result<Unit> {
        return Result.success(Unit)
    }
}

class RemoteSosTest {

    @Test
    fun deliveryState_displayLabels_areAccurate() {
        assertEquals("Alert Created", RemoteAlertDeliveryState.CREATED.displayLabel)
        assertEquals("Notification Queued", RemoteAlertDeliveryState.QUEUED.displayLabel)
        assertEquals("Dispatched via FCM", RemoteAlertDeliveryState.FCM_ACCEPTED.displayLabel)
        assertEquals("App-Reported Receipt", RemoteAlertDeliveryState.DEVICE_RECEIVED.displayLabel)
        assertEquals("Guardian Acknowledged", RemoteAlertDeliveryState.ACKNOWLEDGED.displayLabel)
        assertEquals("Dispatch Failed", RemoteAlertDeliveryState.FAILED.displayLabel)
        assertEquals("Delivery Unconfirmed", RemoteAlertDeliveryState.UNKNOWN.displayLabel)
    }

    @Test
    fun dispatchRemoteSos_withLocationConsent_capturesLocation() = kotlinx.coroutines.runBlocking {
        val sosRepo = FakeRemoteSosRepository()
        val guardians = listOf(
            RemoteGuardian("rel_1", "g1", "Guardian One", "g1@test.com", "ACTIVE")
        )
        val location = LocationData(
            latitude = 12.9716,
            longitude = 77.5946,
            accuracyMeters = 5f,
            timestampMillis = 1000000L,
            isFresh = true,
            provider = "gps"
        )

        val result = sosRepo.dispatchRemoteSos(
            recipients = guardians,
            messageText = "Test Emergency",
            location = location,
            includeLocation = true,
            idempotencyKey = "key_1"
        )

        assertTrue(result.isSuccess)
        val data = result.getOrThrow()
        assertEquals("alert_100", data.alertId)
        assertEquals(1, data.recipientsCount)
        assertNotNull(sosRepo.lastDispatchedLocation)
        assertEquals(12.9716, sosRepo.lastDispatchedLocation!!.latitude, 0.0001)
    }

    @Test
    fun dispatchRemoteSos_withoutLocationConsent_omitsLocation() = kotlinx.coroutines.runBlocking {
        val sosRepo = FakeRemoteSosRepository()
        val guardians = listOf(
            RemoteGuardian("rel_1", "g1", "Guardian One", "g1@test.com", "ACTIVE")
        )
        val location = LocationData(
            latitude = 12.9716,
            longitude = 77.5946,
            accuracyMeters = 5f,
            timestampMillis = 1000000L,
            isFresh = true,
            provider = "gps"
        )

        val result = sosRepo.dispatchRemoteSos(
            recipients = guardians,
            messageText = "Test Emergency",
            location = location,
            includeLocation = false,
            idempotencyKey = "key_2"
        )

        assertTrue(result.isSuccess)
        assertEquals(null, sosRepo.lastDispatchedLocation)
    }

    @Test
    fun remoteGuardian_isActive_checksStatusProperly() {
        val activeGuardian = RemoteGuardian("r1", "g1", "G1", "g1@test.com", "ACTIVE")
        assertTrue(activeGuardian.isActive)

        val revokedGuardian = RemoteGuardian("r2", "g2", "G2", "g2@test.com", "REVOKED")
        assertFalse(revokedGuardian.isActive)
    }
}
