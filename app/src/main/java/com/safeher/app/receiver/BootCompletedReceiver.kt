package com.safeher.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.safeher.app.data.RepositoryProvider
import com.safeher.app.data.journey.JourneyMonitoringScheduler
import com.safeher.app.service.JourneyMonitoringService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Reconciles active journeys and re-schedules overdue safety alarms
 * after device reboot or app update.
 */
class BootCompletedReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_MY_PACKAGE_REPLACED) {
            return
        }

        Log.i(TAG, "Reconciling active journeys after system event: $action")
        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val journeyRepo = RepositoryProvider.getJourneyRepository(context)
                val scheduler = JourneyMonitoringScheduler(context)

                val activeJourneys = journeyRepo.getActiveMonitoredJourneys()
                val now = System.currentTimeMillis()

                for (journey in activeJourneys) {
                    if (journey.isTerminal) continue

                    // Re-schedule alarms
                    val deadline = journey.overdueEscalationDeadline ?: (journey.expectedArrivalTimestamp + JourneyMonitoringScheduler.DEFAULT_ESCALATION_WINDOW_MILLIS)
                    scheduler.scheduleJourneyAlarms(
                        journeyId = journey.id,
                        expectedArrivalTimestamp = journey.expectedArrivalTimestamp,
                        escalationDeadline = deadline
                    )

                    // Restart background location monitoring service if active
                    if (journey.isMonitoringActive && !journey.isTerminal) {
                        try {
                            JourneyMonitoringService.start(
                                context = context,
                                journeyId = journey.id,
                                destination = journey.destination,
                                expectedArrivalTimestamp = journey.expectedArrivalTimestamp
                            )
                        } catch (e: Exception) {
                            Log.w(TAG, "Could not restart JourneyMonitoringService on boot: ${e.message}")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error reconciling journeys on boot: ${e.message}", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val TAG = "BootCompletedReceiver"
    }
}
