package dev.astoris.ursa.core.push

import android.content.Context
import androidx.core.content.edit

class OverallStatusStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun enabled(): Boolean = prefs.getBoolean(KEY_ENABLED, false)

    fun setEnabled(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_ENABLED, enabled) }
    }

    private companion object {
        const val PREFS = "ursa_overall_status"
        const val KEY_ENABLED = "enabled"
    }
}
