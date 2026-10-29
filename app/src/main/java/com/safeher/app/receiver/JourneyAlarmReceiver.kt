package com.safeher.app.receiver

import android.annotation.SuppressLint
import android.app.Notification
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.safeher.app.MainActivity
import com.safeher.app.R
import com.safeher.app.data.RepositoryProvider
import com.safeher.app.data.journey.Journey
import com.safeher.app.data.journey.JourneyMonitoringScheduler
import com.safeher.app.data.journey.JourneyStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * BroadcastReceiver triggered by AlarmManager at:
 * 1. Expected Arrival Time (ACTION_CHECK_OVERDUE) -> displays 5-minute safety check alert.
 * 2. 5-Minute Escalation Deadline (ACTION_ESCALATE_OVERDUE_SOS) -> triggers direct SMS if unacknowledged.
 *
 * Guarantees:
 * - Strictly idempotent; guards against duplicate triggers.
 * - Respects user terminal state (COMPLETED / CANCELLED).
 * - Honors "I'm Safe" acknowledgment before sending emergency alerts.
 * - Dispatches via existing DirectSosManager without duplicate engines.
 */
class JourneyAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent == null) return

        val journeyId = intent.getLongExtra(JourneyMonitoringScheduler.EXTRA_JOURNEY_ID, 0L)
        if (journeyId <= 0L) return

        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                when (intent.action) {
                    ACTION_CHECK_OVERDUE -> handleCheckOverdue(context, journeyId)
                    ACTION_ESCALATE_OVERDUE_SOS -> handleEscalateOverdue(context, journeyId)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error handling journey alarm: ${e.message}", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun handleCheckOverdue(context: Context, journeyId: Long) {
        val journeyRepo = RepositoryProvider.getJourneyRepository(context)
        val journey = journeyRepo.getJourneyById(journeyId) ?: return

        // 1. Guard against terminal or already acknowledged journeys
        if (journey.isTerminal || journey.safetyCheckAcknowledged) {
            Log.d(TAG, "Journey #$journeyId already terminal or acknowledged; ignoring overdue check.")
            return
        }

        val now = System.currentTimeMillis()
        val deadline = now + JourneyMonitoringScheduler.DEFAULT_ESCALATION_WINDOW_MILLIS

        // 2. Record safety check sent in Room database & event timeline
        journeyRepo.recordSafetyCheckSent(journeyId, checkTime = now, escalationDeadline = deadline)

        // 3. Post high-priority notification to user
        showSafetyCheckNotification(context, journey, deadline)

        // 4. Ensure escalation alarm is scheduled for deadline
        val scheduler = JourneyMonitoringScheduler(context)
        scheduler.scheduleJourneyAlarms(
            journeyId = journeyId,
            expectedArrivalTimestamp = journey.expectedArrivalTimestamp,
            escalationDeadline = deadline
        )
    }

    private suspend fun handleEscalateOverdue(context: Context, journeyId: Long) {
        val journeyRepo = RepositoryProvider.getJourneyRepository(context)
        val journey = journeyRepo.getJourneyById(journeyId) ?: return

        // 1. Pre-flight verification
        if (journey.isTerminal) {
            Log.d(TAG, "Journey #$journeyId is already ${journey.status}; cancelling escalation.")
            return
        }
        if (journey.safetyCheckAcknowledged) {
            Log.d(TAG, "User acknowledged safety for journey #$journeyId; cancelling escalation.")
            return
        }
        if (journey.overdueEscalationAttempted) {
            Log.d(TAG, "Escalation already attempted for journey #$journeyId; skipping duplicate.")
            return
        }

        // 2. Mark attempted in DB immediately to prevent duplicate runs
        journeyRepo.markOverdueEscalationAttempted(journeyId)

        // 3. Dispatch overdue SOS through DirectSosManager
        val directSosManager = RepositoryProvider.getDirectSosManager(context)
        val result = directSosManager.dispatchOverdueSos(journey)

        // 4. Dismiss safety check notification
        val notificationManager = NotificationManagerCompat.from(context)
        notificationManager.cancel(journeyId.toInt() + JourneyMonitoringScheduler.NOTIFICATION_OVERDUE_BASE_ID)

        // 5. Post escalation summary notification
        showEscalationResultNotification(context, journey, result)
    }

    @SuppressLint("MissingPermission")
    private fun showSafetyCheckNotification(context: Context, journey: Journey, escalationDeadline: Long) {
        val notificationManager = NotificationManagerCompat.from(context)
        if (!notificationManager.areNotificationsEnabled()) {
            Log.w(TAG, "Notifications are disabled by user. Cannot display safety check.")
            return
        }

        val notificationId = journey.id.toInt() + JourneyMonitoringScheduler.NOTIFICATION_OVERDUE_BASE_ID

        // Action 1: "I'm Safe"
        val safeIntent = Intent(context, JourneySafetyActionReceiver::class.java).apply {
            action = JourneySafetyActionReceiver.ACTION_USER_SAFE
            putExtra(JourneyMonitoringScheduler.EXTRA_JOURNEY_ID, journey.id)
        }
        val safePendingIntent = PendingIntent.getBroadcast(
            context,
            journey.id.toInt() * 100 + 1,
            safeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Action 2: "Need Help"
        val helpIntent = Intent(context, JourneySafetyActionReceiver::class.java).apply {
            action = JourneySafetyActionReceiver.ACTION_USER_NEED_HELP
            putExtra(JourneyMonitoringScheduler.EXTRA_JOURNEY_ID, journey.id)
        }
        val helpPendingIntent = PendingIntent.getBroadcast(
            context,
            journey.id.toInt() * 100 + 2,
            helpIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Content intent: Open Journeys Screen
        val contentIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            journey.id.toInt(),
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, JourneyMonitoringScheduler.CHANNEL_SAFETY_ALERTS)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("SafeHer Safety Check")
            .setContentText("You're past your expected arrival time at ${journey.destination}. Are you safe?")
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    "You're past your expected arrival time at ${journey.destination}.\n" +
                    "Please tap 'I'm Safe' within 5 minutes. If you do not respond, SafeHer will automatically alert your guardians via SMS."
                )
            )
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setOngoing(true)
            .setAutoCancel(false)
            .setContentIntent(contentPendingIntent)
            .addAction(R.mipmap.ic_launcher, "I'm Safe", safePendingIntent)
            .addAction(R.mipmap.ic_launcher, "Need Help", helpPendingIntent)
            .addAction(R.mipmap.ic_launcher, "Open Journey", contentPendingIntent)
            .build()

        notificationManager.notify(notificationId, notification)
    }

    @SuppressLint("MissingPermission")
    private fun showEscalationResultNotification(
        context: Context,
        journey: Journey,
        result: com.safeher.app.data.sos.DirectSosState
    ) {
        val notificationManager = NotificationManagerCompat.from(context)
        if (!notificationManager.areNotificationsEnabled()) return

        val title = when (result) {
            is com.safeher.app.data.sos.DirectSosState.Completed -> "Overdue SOS Dispatched"
            is com.safeher.app.data.sos.DirectSosState.Error -> "Overdue SOS Alert Failed"
            else -> "SafeHer Overdue Notice"
        }

        val text = when (result) {
            is com.safeher.app.data.sos.DirectSosState.Completed ->
                "Emergency alerts sent to ${result.submittedCount + result.deliveryConfirmedCount} guardians for overdue arrival at ${journey.destination}."
            is com.safeher.app.data.sos.DirectSosState.Error ->
                "Could not send overdue emergency SMS: ${result.reason}"
            else -> "Automatic overdue check processed."
        }

        val contentIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            journey.id.toInt() + 500,
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, JourneyMonitoringScheduler.CHANNEL_SAFETY_ALERTS)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(contentPendingIntent)
            .build()

        notificationManager.notify(journey.id.toInt() + 40000, notification)
    }

    companion object {
        const val TAG = "JourneyAlarmReceiver"

        const val ACTION_CHECK_OVERDUE = "com.safeher.app.receiver.ACTION_CHECK_OVERDUE"
        const val ACTION_ESCALATE_OVERDUE_SOS = "com.safeher.app.receiver.ACTION_ESCALATE_OVERDUE_SOS"
    }
}
