package com.safeher.app.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.safeher.app.MainActivity
import com.safeher.app.R
import com.safeher.app.data.RepositoryProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class SafeHerMessagingService : FirebaseMessagingService() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    companion object {
        const val CHANNEL_ID = "safeher_emergency_alerts"
        const val CHANNEL_NAME = "Emergency SOS Alerts"
        const val EXTRA_ALERT_ID = "extra_alert_id"
        private const val NOTIFICATION_ID_BASE = 9000
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        serviceScope.launch {
            registerFcmToken(token)
        }
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)

        val data = remoteMessage.data
        val alertId = data["alertId"] ?: return
        val senderName = data["senderName"] ?: "Someone"

        // Asynchronously report that device has received the alert
        serviceScope.launch {
            try {
                val repo = RepositoryProvider.getRemoteSosRepository(applicationContext)
                repo.reportAlertReceived(alertId)
            } catch (_: Exception) {
                // Ignore background report error
            }
        }

        // Display high-priority emergency notification
        displayEmergencyNotification(alertId, senderName)
    }

    private fun displayEmergencyNotification(alertId: String, senderName: String) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Create high importance notification channel on Android 8+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Urgent incoming alerts from trusted contacts"
                enableVibration(true)
                enableLights(true)
            }
            notificationManager.createNotificationChannel(channel)
        }

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_ALERT_ID, alertId)
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            alertId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        // Privacy: lock screen payload contains only minimal opaque alert notice
        val notificationBuilder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("🚨 SafeHer Emergency Alert")
            .setContentText("$senderName has triggered an SOS alert. Tap to view and acknowledge.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setSound(defaultSoundUri)
            .setContentIntent(pendingIntent)

        val notificationId = NOTIFICATION_ID_BASE + (alertId.hashCode() % 1000)
        notificationManager.notify(notificationId, notificationBuilder.build())
    }

    private suspend fun registerFcmToken(token: String) {
        if (RepositoryProvider.isDemoMode(applicationContext)) return
        try {
            val auth = FirebaseAuth.getInstance()
            val currentUid = auth.currentUser?.uid ?: return
            val firestore = FirebaseFirestore.getInstance()

            val tokenInfo = mapOf(
                "deviceModel" to "${Build.MANUFACTURER} ${Build.MODEL}",
                "updatedAt" to FieldValue.serverTimestamp()
            )

            firestore.collection("users").document(currentUid).set(
                mapOf("fcmTokens" to mapOf(token to tokenInfo)),
                com.google.firebase.firestore.SetOptions.merge()
            )
        } catch (_: Exception) {
            // Safe ignore if offline or Firebase not initialized
        }
    }
}
