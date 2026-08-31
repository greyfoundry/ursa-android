package dev.astoris.ursa.core.push

import android.content.Context
import androidx.core.content.edit

class PushQuietHoursStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun load(): PushQuietHours = PushQuietHours(
        enabled = prefs.getBoolean(KEY_ENABLED, false),
        startMinute = prefs.getInt(KEY_START, DEFAULT.startMinute),
        endMinute = prefs.getInt(KEY_END, DEFAULT.endMinute),
        daysMask = prefs.getInt(KEY_DAYS, DEFAULT.daysMask),
    ).normalized()

    fun save(schedule: PushQuietHours) {
        val safe = schedule.normalized()
        prefs.edit {
            putBoolean(KEY_ENABLED, safe.enabled)
            putInt(KEY_START, safe.startMinute)
            putInt(KEY_END, safe.endMinute)
            putInt(KEY_DAYS, safe.daysMask)
        }
    }

    private companion object {
        const val PREFS = "ursa_push_quiet_hours"
        const val KEY_ENABLED = "enabled"
        const val KEY_START = "start_minute"
        const val KEY_END = "end_minute"
        const val KEY_DAYS = "days_mask"
        val DEFAULT = PushQuietHours()
    }
}
