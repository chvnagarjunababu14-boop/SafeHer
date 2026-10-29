package com.safeher.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import com.safeher.app.data.RepositoryProvider
import com.safeher.app.data.journey.JourneyMonitoringScheduler
import com.safeher.app.service.JourneyMonitoringService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Handles explicit user actions from ongoing journey notifications and safety checks:
 * - "I'm Safe": Acknowledges check, cancels escalation, records safe event.
 * - "Need Help": Immediately initiates direct emergency SOS without waiting for 5-minute timeout.
 * - "Stop Tracking": Stops foreground location monitoring service.
 */
class JourneySafetyActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent == null) return

        val journeyId = intent.getLongExtra(JourneyMonitoringScheduler.EXTRA_JOURNEY_ID, 0L)
        val action = intent.action ?: return

        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val journeyRepo = RepositoryProvider.getJourneyRepository(context)
                val scheduler = JourneyMonitoringScheduler(context)
                val notificationManager = NotificationManagerCompat.from(context)

                when (action) {
                    ACTION_USER_SAFE -> {
                        Log.i(TAG, "User tapped 'I'm Safe' for journey #$journeyId")
                        // 1. Dismiss overdue notification
                        notificationManager.cancel(journeyId.toInt() + JourneyMonitoringScheduler.NOTIFICATION_OVERDUE_BASE_ID)

                        // 2. Cancel pending escalation alarm
                        scheduler.cancelEscalationAlarm(journeyId)

                        // 3. Persist acknowledgment in Room
                        journeyRepo.acknowledgeSafetyCheck(journeyId)
                    }

                    ACTION_USER_NEED_HELP -> {
                        Log.i(TAG, "User tapped 'Need Help' for journey #$journeyId")
                        // 1. Dismiss overdue notification
                        notificationManager.cancel(journeyId.toInt() + JourneyMonitoringScheduler.NOTIFICATION_OVERDUE_BASE_ID)

                        // 2. Cancel pending escalation alarm
                        scheduler.cancelEscalationAlarm(journeyId)

                        // 3. Immediately trigger direct SOS
                        val directSosManager = RepositoryProvider.getDirectSosManager(context)
                        directSosManager.triggerImmediateSos()
                    }

                    ACTION_STOP_MONITORING -> {
                        Log.i(TAG, "User stopped tracking for journey #$journeyId")
                        JourneyMonitoringService.stop(context, journeyId)
                        journeyRepo.setMonitoringActive(journeyId, false)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error handling safety action: ${e.message}", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val TAG = "JourneySafetyAction"

        const val ACTION_USER_SAFE = "com.safeher.app.receiver.ACTION_USER_SAFE"
        const val ACTION_USER_NEED_HELP = "com.safeher.app.receiver.ACTION_USER_NEED_HELP"
        const val ACTION_STOP_MONITORING = "com.safeher.app.receiver.ACTION_STOP_MONITORING"
    }
}
