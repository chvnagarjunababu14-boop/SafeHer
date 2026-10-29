package com.safeher.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import com.safeher.app.data.RepositoryProvider
import com.safeher.app.data.battery.AndroidBatteryStateReader
import com.safeher.app.data.session.UserRole

/**
 * Forwards battery change broadcasts to [com.safeher.app.data.battery.BatteryAlertManager].
 *
 * Must be registered dynamically (ACTION_BATTERY_CHANGED cannot be manifest-registered on
 * modern Android). Does not poll and does not request new permissions.
 */
class BatteryChangeReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent == null) return
        val action = intent.action ?: return
        if (action != Intent.ACTION_BATTERY_CHANGED &&
            action != Intent.ACTION_BATTERY_LOW &&
            action != Intent.ACTION_BATTERY_OKAY
        ) {
            return
        }

        val appContext = context.applicationContext
        val session = RepositoryProvider.getUserSessionManager(appContext)
        val uid = session.currentUserId ?: return
        if (session.getUserRole(uid) != UserRole.PERSONAL_SAFETY_USER) return

        val prefs = RepositoryProvider.getSosPreferencesRepository(appContext).getPreferences()
        if (!prefs.isBatteryAlertsEnabled || !prefs.isBatteryAlertsConsentGiven) return

        val info = if (action == Intent.ACTION_BATTERY_CHANGED) {
            AndroidBatteryStateReader.fromBatteryChangedIntent(intent)
        } else {
            AndroidBatteryStateReader(appContext).readBatteryInfo()
        }
        RepositoryProvider.getBatteryAlertManager(appContext).onBatteryChanged(info)
    }

    companion object {
        /**
         * Registers a process-local receiver. Caller must unregister symmetrically.
         */
        fun register(context: Context): BatteryChangeReceiver {
            val receiver = BatteryChangeReceiver()
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_BATTERY_CHANGED)
                addAction(Intent.ACTION_BATTERY_LOW)
                addAction(Intent.ACTION_BATTERY_OKAY)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                @Suppress("UnspecifiedRegisterReceiverFlag")
                context.registerReceiver(receiver, filter)
            }
            return receiver
        }
    }
}
