package com.safeher.app.data.remotesos

import com.safeher.app.data.location.LocationData
import com.safeher.app.data.remoteguardian.RemoteGuardian
import kotlinx.coroutines.flow.Flow

interface RemoteSosRepository {
    suspend fun dispatchRemoteSos(
        recipients: List<RemoteGuardian>,
        messageText: String,
        location: LocationData?,
        includeLocation: Boolean,
        idempotencyKey: String
    ): Result<RemoteSosDispatchResult>

    fun observeDeliveryStatuses(alertId: String): Flow<List<RecipientDeliveryInfo>>

    suspend fun getAlertDetails(alertId: String): Result<RemoteAlertDetails>

    suspend fun reportAlertReceived(alertId: String): Result<Unit>

    suspend fun acknowledgeAlert(alertId: String, deviceModel: String): Result<Unit>
}
