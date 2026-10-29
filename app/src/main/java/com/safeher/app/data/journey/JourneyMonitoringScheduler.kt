package com.safeher.app.data.journey

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import com.safeher.app.receiver.JourneyAlarmReceiver

/**
 * Handles durable scheduling and cancellation of journey ETA arrival checks
 * and the 5-minute overdue emergency SOS escalation deadline.
 *
 * Reliability Guarantees:
 * - Uses exact alarms with allow-while-idle or AlarmClockInfo to fire reliably through Doze.
 * - Idempotent scheduling using deterministic request codes keyed to the journey ID.
 * - Manages dedicated Android notification channels for ongoing monitoring and high-priority safety alerts.
 * - Transparently handles Android 12+ exact alarm capabilities.
 */
open class JourneyMonitoringScheduler(protected val context: Context? = null) {

    private val alarmManager = context?.getSystemService(Context.ALARM_SERVICE) as? AlarmManager

    init {
        if (context != null) {
            createNotificationChannels()
        }
    }

    open fun createNotificationChannels() {
        val ctx = context ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                ?: return

            // Channel 1: Ongoing background location monitoring (Low importance, non-intrusive)
            val monitoringChannel = NotificationChannel(
                CHANNEL_MONITORING,
                "Journey Monitoring",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Ongoing location monitoring while a safety journey is active."
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(monitoringChannel)

            // Channel 2: High priority overdue safety check & SOS escalation alerts
            val alertsChannel = NotificationChannel(
                CHANNEL_SAFETY_ALERTS,
                "Safety Checks & Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Overdue safety check alerts and automatic emergency escalation notices."
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 500, 200, 500)
                setShowBadge(true)
            }
            notificationManager.createNotificationChannel(alertsChannel)
        }
    }

    /**
     * Schedules the overdue safety check at expected arrival time,
     * and the 5-minute escalation alarm at ETA + 5 minutes.
     */
    open fun scheduleJourneyAlarms(
        journeyId: Long,
        expectedArrivalTimestamp: Long,
        escalationDeadline: Long = expectedArrivalTimestamp + DEFAULT_ESCALATION_WINDOW_MILLIS
    ) {
        val ctx = context ?: return
        if (alarmManager == null) {
            Log.w(TAG, "AlarmManager unavailable on device; cannot schedule journey alarms.")
            return
        }

        val now = System.currentTimeMillis()

        // 1. Schedule Safety Check at ETA (or immediately if ETA has already passed)
        val checkTriggerTime = expectedArrivalTimestamp.coerceAtLeast(now + 1000L)
        val checkIntent = createAlarmIntent(JourneyAlarmReceiver.ACTION_CHECK_OVERDUE, journeyId)
        val checkPendingIntent = PendingIntent.getBroadcast(
            ctx,
            getCheckRequestCode(journeyId),
            checkIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        scheduleExactOrFallback(checkTriggerTime, checkPendingIntent)

        // 2. Schedule Escalation Alarm at ETA + 5 minutes
        val escalationTriggerTime = escalationDeadline.coerceAtLeast(checkTriggerTime + 5000L)
        val escalateIntent = createAlarmIntent(JourneyAlarmReceiver.ACTION_ESCALATE_OVERDUE_SOS, journeyId)
        val escalatePendingIntent = PendingIntent.getBroadcast(
            ctx,
            getEscalateRequestCode(journeyId),
            escalateIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        scheduleExactOrFallback(escalationTriggerTime, escalatePendingIntent)
    }

    /**
     * Cancels the 5-minute overdue escalation alarm when the user confirms they are safe ("I'm Safe").
     */
    open fun cancelEscalationAlarm(journeyId: Long) {
        val ctx = context ?: return
        if (alarmManager == null) return
        val escalateIntent = createAlarmIntent(JourneyAlarmReceiver.ACTION_ESCALATE_OVERDUE_SOS, journeyId)
        val pendingIntent = PendingIntent.getBroadcast(
            ctx,
            getEscalateRequestCode(journeyId),
            escalateIntent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
        }
    }

    /**
     * Cancels all pending alarms for a completed or cancelled journey.
     */
    open fun cancelAllJourneyAlarms(journeyId: Long) {
        val ctx = context ?: return
        if (alarmManager == null) return

        // Cancel Safety Check
        val checkIntent = createAlarmIntent(JourneyAlarmReceiver.ACTION_CHECK_OVERDUE, journeyId)
        val checkPending = PendingIntent.getBroadcast(
            ctx,
            getCheckRequestCode(journeyId),
            checkIntent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (checkPending != null) {
            alarmManager.cancel(checkPending)
            checkPending.cancel()
        }

        // Cancel Escalation Alarm
        cancelEscalationAlarm(journeyId)

        // Dismiss safety check notification if active
        val notificationManager = NotificationManagerCompat.from(ctx)
        notificationManager.cancel(journeyId.toInt() + NOTIFICATION_OVERDUE_BASE_ID)
    }

    private fun scheduleExactOrFallback(triggerAtMillis: Long, pendingIntent: PendingIntent) {
        val am = alarmManager ?: return

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (am.canScheduleExactAlarms()) {
                    am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
                } else {
                    am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
                }
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
            } else {
                am.setExact(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "Exact alarm permission denied; falling back to standard alarm: ${e.message}")
            try {
                am.set(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
            } catch (fallbackEx: Exception) {
                Log.e(TAG, "Failed to schedule alarm fallback: ${fallbackEx.message}")
            }
        }
    }

    private fun createAlarmIntent(action: String, journeyId: Long): Intent {
        val ctx = context ?: return Intent()
        return Intent(ctx, JourneyAlarmReceiver::class.java).apply {
            this.action = action
            putExtra(EXTRA_JOURNEY_ID, journeyId)
        }
    }

    companion object {
        const val TAG = "JourneyScheduler"

        const val CHANNEL_MONITORING = "safeher_journey_monitoring"
        const val CHANNEL_SAFETY_ALERTS = "safeher_safety_alerts"

        const val EXTRA_JOURNEY_ID = "com.safeher.app.EXTRA_JOURNEY_ID"
        const val DEFAULT_ESCALATION_WINDOW_MILLIS = 5 * 60 * 1000L // 5 minutes

        const val NOTIFICATION_MONITORING_BASE_ID = 20000
        const val NOTIFICATION_OVERDUE_BASE_ID = 30000

        fun getCheckRequestCode(journeyId: Long): Int = (journeyId.toInt() * 10) + 1
        fun getEscalateRequestCode(journeyId: Long): Int = (journeyId.toInt() * 10) + 2
    }
}
