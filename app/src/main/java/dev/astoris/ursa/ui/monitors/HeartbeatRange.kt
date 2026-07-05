package dev.astoris.ursa.ui.monitors

/**
 * Selectable window for the monitor detail heartbeat bar (upstream #1888). Maps to the
 * `hours` argument of Kuma's `getMonitorBeats`. Kept short and homelab-practical rather
 * than mirroring Kuma's full 7/30/.../365-day set, which for raw beats would be huge.
 */
enum class HeartbeatRange(val hours: Int, val label: String) {
    SIX_HOURS(6, "6h"),
    DAY(24, "24h"),
    WEEK(24 * 7, "7d"),
    MONTH(24 * 30, "30d"),
}
