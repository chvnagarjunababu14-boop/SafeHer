package com.safeher.app.data.battery

/**
 * Snapshot of device battery state captured near alert dispatch time.
 * Never invent values: [percent] is null when Android does not report a level.
 */
data class BatteryInfo(
    val percent: Int?,
    val chargingState: ChargingState,
    val capturedAtMillis: Long = System.currentTimeMillis()
) {
    val isPercentKnown: Boolean get() = percent != null
}

enum class ChargingState {
    CHARGING,
    NOT_CHARGING,
    UNKNOWN
}

/**
 * Pure formatting helpers for SMS bodies and settings UI. Fully unit-testable.
 */
object BatteryInfoFormatter {

    fun formatPercent(info: BatteryInfo): String =
        info.percent?.coerceIn(0, 100)?.let { "$it%" } ?: "Unknown"

    fun formatCharging(info: BatteryInfo): String = when (info.chargingState) {
        ChargingState.CHARGING -> "Charging"
        ChargingState.NOT_CHARGING -> "Not charging"
        ChargingState.UNKNOWN -> "Unknown"
    }

    fun formatPercentLine(info: BatteryInfo): String = "Battery: ${formatPercent(info)}"

    fun formatChargingLine(info: BatteryInfo): String = "Charging: ${formatCharging(info)}"

    /**
     * Returns true when [current] has crossed from strictly above [threshold]
     * down to at-or-below [threshold] relative to [previous].
     * Unknown percentages never count as a crossing.
     */
    fun crossedThresholdDown(previous: Int?, current: Int?, threshold: Int): Boolean {
        if (previous == null || current == null) return false
        return previous > threshold && current <= threshold
    }

    /**
     * Whether a previously fired threshold should reset after charging or rising above it.
     */
    fun shouldResetThreshold(percent: Int?, isCharging: Boolean, threshold: Int): Boolean {
        if (isCharging) return true
        if (percent == null) return false
        return percent > threshold
    }
}
