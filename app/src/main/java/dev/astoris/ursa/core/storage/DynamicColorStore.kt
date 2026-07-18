package dev.astoris.ursa.core.storage

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Whether to use Material You wallpaper colors instead of Kuma's palette (opt-in, off by
 * default so the default look stays Kuma-matched). A plain boolean preference mirrored in
 * a process-wide StateFlow so the theme reacts immediately when toggled.
 */
object DynamicColorStore {

    private const val PREFS = "ursa_dynamic_color"
    private const val KEY_ENABLED = "enabled"

    private val _enabled = MutableStateFlow(false)
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun load(context: Context) {
        _enabled.value = prefs(context).getBoolean(KEY_ENABLED, false)
    }

    fun setEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit { putBoolean(KEY_ENABLED, enabled) }
        _enabled.value = enabled
    }
}
