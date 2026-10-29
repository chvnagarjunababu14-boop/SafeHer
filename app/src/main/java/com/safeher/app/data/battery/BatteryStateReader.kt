package com.safeher.app.data.battery

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager

/**
 * Abstraction for reading battery state so SMS builders and alert managers stay testable.
 */
fun interface BatteryStateReader {
    fun readBatteryInfo(): BatteryInfo
}

/**
 * Reads the sticky [Intent.ACTION_BATTERY_CHANGED] broadcast. Does not register a permanent loop.
 */
class AndroidBatteryStateReader(
    private val context: Context,
    private val clock: () -> Long = { System.currentTimeMillis() }
) : BatteryStateReader {

    override fun readBatteryInfo(): BatteryInfo {
        val intent = try {
            context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        } catch (_: Exception) {
            null
        } ?: return BatteryInfo(percent = null, chargingState = ChargingState.UNKNOWN, capturedAtMillis = clock())

        return fromBatteryChangedIntent(intent, clock())
    }

    companion object {
        fun fromBatteryChangedIntent(intent: Intent, capturedAtMillis: Long = System.currentTimeMillis()): BatteryInfo {
            val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            val percent = if (level >= 0 && scale > 0) {
                ((level * 100f) / scale).toInt().coerceIn(0, 100)
            } else {
                null
            }

            val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN)
            val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)
            val chargingState = when {
                status == BatteryManager.BATTERY_STATUS_CHARGING ||
                    status == BatteryManager.BATTERY_STATUS_FULL ||
                    plugged != 0 -> ChargingState.CHARGING
                status == BatteryManager.BATTERY_STATUS_DISCHARGING ||
                    status == BatteryManager.BATTERY_STATUS_NOT_CHARGING -> ChargingState.NOT_CHARGING
                else -> ChargingState.UNKNOWN
            }

            return BatteryInfo(
                percent = percent,
                chargingState = chargingState,
                capturedAtMillis = capturedAtMillis
            )
        }
    }
}
