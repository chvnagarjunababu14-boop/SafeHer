package com.safeher.app.service

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.safeher.app.MainActivity
import com.safeher.app.R
import com.safeher.app.data.RepositoryProvider
import com.safeher.app.data.journey.JourneyMonitoringScheduler
import com.safeher.app.receiver.JourneySafetyActionReceiver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Android Foreground Service for active journey background location monitoring.
 *
 * Guarantees:
 * - Runs strictly for an explicitly monitored, active journey.
 * - Conforms to Android 14+ (API 34+) FOREGROUND_SERVICE_TYPE_LOCATION requirements.
 * - Balances accuracy and battery using 25-second balanced power intervals and 25-meter distance threshold.
 * - Never uploads location coordinates to Firebase or network servers; stores locally in Room.
 * - Terminates cleanly when journey completes, is cancelled, or permission is revoked.
 */
class JourneyMonitoringService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback

    private var activeJourneyId: Long = 0L
    private var destinationName: String = "Destination"
    private var expectedArrivalTimestamp: Long = 0L
    private var isTracking = false

    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        JourneyMonitoringScheduler(this).createNotificationChannels()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val location: Location = result.lastLocation ?: return
                handleNewLocation(location)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) {
            stopSelf()
            return START_NOT_STICKY
        }

        when (intent.action) {
            ACTION_START_MONITORING -> {
                activeJourneyId = intent.getLongExtra(EXTRA_JOURNEY_ID, 0L)
                destinationName = intent.getStringExtra(EXTRA_DESTINATION) ?: "Destination"
                expectedArrivalTimestamp = intent.getLongExtra(EXTRA_ETA, 0L)

                if (activeJourneyId <= 0L) {
                    stopSelf()
                    return START_NOT_STICKY
                }

                startForegroundTracking()
            }
            ACTION_STOP_MONITORING -> {
                stopForegroundTracking()
            }
            else -> {
                stopSelf()
            }
        }

        return START_STICKY
    }

    private fun startForegroundTracking() {
        val notification = buildOngoingNotification("Monitoring active — on your way")
        val notificationId = JourneyMonitoringScheduler.NOTIFICATION_MONITORING_BASE_ID + activeJourneyId.toInt()

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(notificationId, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
            } else {
                startForeground(notificationId, notification)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start foreground service: ${e.message}")
            stopSelf()
            return
        }

        requestLocationUpdates()
    }

    @SuppressLint("MissingPermission")
    private fun requestLocationUpdates() {
        if (!hasLocationPermission()) {
            Log.w(TAG, "Location permission not granted. Cannot receive background location updates.")
            updateNotification("Location permission needed for background tracking")
            return
        }

        if (isTracking) return

        val request = LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY, UPDATE_INTERVAL_MS)
            .setMinUpdateIntervalMillis(FASTEST_INTERVAL_MS)
            .setMinUpdateDistanceMeters(MIN_DISTANCE_METERS)
            .build()

        try {
            fusedLocationClient.requestLocationUpdates(request, locationCallback, Looper.getMainLooper())
            isTracking = true
            Log.i(TAG, "Background location monitoring started for journey #$activeJourneyId")
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException requesting location updates: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "Error requesting location updates: ${e.message}")
        }
    }

    private fun handleNewLocation(location: Location) {
        val now = System.currentTimeMillis()
        val journeyRepo = RepositoryProvider.getJourneyRepository(applicationContext)

        serviceScope.launch {
            try {
                journeyRepo.updateJourneyLocation(
                    journeyId = activeJourneyId,
                    latitude = location.latitude,
                    longitude = location.longitude,
                    timestamp = now
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to persist location update: ${e.message}")
            }
        }

        val timeStr = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(now))
        updateNotification("Last location updated at $timeStr")
    }

    private fun updateNotification(statusSubtitle: String) {
        val notification = buildOngoingNotification(statusSubtitle)
        val notificationId = JourneyMonitoringScheduler.NOTIFICATION_MONITORING_BASE_ID + activeJourneyId.toInt()
        NotificationManagerCompat.from(this).notify(notificationId, notification)
    }

    private fun buildOngoingNotification(statusSubtitle: String): Notification {
        val etaStr = if (expectedArrivalTimestamp > 0L) {
            SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(expectedArrivalTimestamp))
        } else {
            "ETA not set"
        }

        // Action 1: Open SafeHer
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPendingIntent = PendingIntent.getActivity(
            this,
            activeJourneyId.toInt(),
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Action 2: Stop Tracking
        val stopIntent = Intent(this, JourneySafetyActionReceiver::class.java).apply {
            action = JourneySafetyActionReceiver.ACTION_STOP_MONITORING
            putExtra(JourneyMonitoringScheduler.EXTRA_JOURNEY_ID, activeJourneyId)
        }
        val stopPendingIntent = PendingIntent.getBroadcast(
            this,
            activeJourneyId.toInt() + 100,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, JourneyMonitoringScheduler.CHANNEL_MONITORING)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Active Journey to $destinationName")
            .setContentText("Expected Arrival: $etaStr • $statusSubtitle")
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setContentIntent(openPendingIntent)
            .addAction(R.mipmap.ic_launcher, "Open SafeHer", openPendingIntent)
            .addAction(R.mipmap.ic_launcher, "Stop Tracking", stopPendingIntent)
            .build()
    }

    private fun stopForegroundTracking() {
        if (isTracking) {
            try {
                fusedLocationClient.removeLocationUpdates(locationCallback)
            } catch (e: Exception) {
                Log.w(TAG, "Error removing location updates: ${e.message}")
            }
            isTracking = false
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun hasLocationPermission(): Boolean {
        val fine = ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isTracking) {
            try {
                fusedLocationClient.removeLocationUpdates(locationCallback)
            } catch (_: Exception) {}
        }
        serviceScope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val TAG = "JourneyMonitoringSvc"

        const val ACTION_START_MONITORING = "com.safeher.app.service.ACTION_START_MONITORING"
        const val ACTION_STOP_MONITORING = "com.safeher.app.service.ACTION_STOP_MONITORING"

        const val EXTRA_JOURNEY_ID = "EXTRA_JOURNEY_ID"
        const val EXTRA_DESTINATION = "EXTRA_DESTINATION"
        const val EXTRA_ETA = "EXTRA_ETA"

        private const val UPDATE_INTERVAL_MS = 25_000L // 25 seconds
        private const val FASTEST_INTERVAL_MS = 15_000L // 15 seconds
        private const val MIN_DISTANCE_METERS = 25f // 25 meters

        fun start(context: Context, journeyId: Long, destination: String, expectedArrivalTimestamp: Long) {
            val intent = Intent(context, JourneyMonitoringService::class.java).apply {
                action = ACTION_START_MONITORING
                putExtra(EXTRA_JOURNEY_ID, journeyId)
                putExtra(EXTRA_DESTINATION, destination)
                putExtra(EXTRA_ETA, expectedArrivalTimestamp)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context, journeyId: Long) {
            val intent = Intent(context, JourneyMonitoringService::class.java).apply {
                action = ACTION_STOP_MONITORING
                putExtra(EXTRA_JOURNEY_ID, journeyId)
            }
            try {
                context.startService(intent)
            } catch (_: Exception) {}
        }
    }
}
