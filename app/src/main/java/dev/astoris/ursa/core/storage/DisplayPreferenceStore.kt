package dev.astoris.ursa.core.storage

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Non-sensitive device presentation choices. Compact mode never reduces touch targets. */
object DisplayPreferenceStore {
    private const val PREFS = "ursa_display_preferences"
    private const val KEY_COMPACT = "compact"
    private val _compact = MutableStateFlow(false)
    val compact: StateFlow<Boolean> = _compact.asStateFlow()

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun load(context: Context) { _compact.value = prefs(context).getBoolean(KEY_COMPACT, false) }
    fun setCompact(context: Context, enabled: Boolean) {
        prefs(context).edit { putBoolean(KEY_COMPACT, enabled) }
        _compact.value = enabled
    }
}
