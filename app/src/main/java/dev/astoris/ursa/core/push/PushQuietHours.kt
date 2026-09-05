package dev.astoris.ursa.core.push

import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId

data class PushQuietHours(
    val enabled: Boolean = false,
    val startMinute: Int = 22 * 60,
    val endMinute: Int = 7 * 60,
    val daysMask: Int = ALL_DAYS_MASK,
) {
    fun normalized(): PushQuietHours = copy(
        startMinute = startMinute.coerceIn(0, MINUTES_PER_DAY - 1),
        endMinute = endMinute.coerceIn(0, MINUTES_PER_DAY - 1),
        daysMask = if (daysMask in 0..ALL_DAYS_MASK) daysMask else ALL_DAYS_MASK,
    )

    fun includes(day: DayOfWeek): Boolean = daysMask and dayMask(day) != 0

    companion object {
        const val MINUTES_PER_DAY = 24 * 60
        const val ALL_DAYS_MASK = 0b111_1111

        fun dayMask(vararg days: DayOfWeek): Int =
            days.fold(0) { mask, day -> mask or (1 shl (day.value - 1)) }
    }
}

object PushQuietHoursPolicy {
    fun isQuiet(schedule: PushQuietHours, day: DayOfWeek, minuteOfDay: Int): Boolean {
        val safe = schedule.normalized()
        if (!safe.enabled || safe.daysMask == 0 || safe.startMinute == safe.endMinute) return false
        if (minuteOfDay !in 0 until PushQuietHours.MINUTES_PER_DAY) return false
        return if (safe.startMinute < safe.endMinute) {
            safe.includes(day) && minuteOfDay in safe.startMinute until safe.endMinute
        } else {
            when {
                minuteOfDay >= safe.startMinute -> safe.includes(day)
                minuteOfDay < safe.endMinute -> safe.includes(day.minus(1))
                else -> false
            }
        }
    }

    fun isQuietNow(
        schedule: PushQuietHours,
        nowMillis: Long = System.currentTimeMillis(),
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): Boolean {
        val local = Instant.ofEpochMilli(nowMillis).atZone(zoneId)
        return isQuiet(schedule, local.dayOfWeek, local.hour * 60 + local.minute)
    }

    fun effectiveSeverity(
        configured: PushSeverity,
        schedule: PushQuietHours,
        nowMillis: Long = System.currentTimeMillis(),
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): PushSeverity = if (isQuietNow(schedule, nowMillis, zoneId)) PushSeverity.SILENT else configured
}
