package dev.astoris.ursa.core.storage

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Whether the app requires a biometric / device-credential unlock before showing
 * content. A plain boolean preference (not a secret), mirrored in a process-wide
 * StateFlow so the setting and the lock gate stay in sync.
 */
object LockStore {

    private const val PREFS = "ursa_lock"
    private const val KEY_ENABLED = "enabled"

    private val _enabled = MutableStateFlow(false)
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun load(context: Context) {
        _enabled.value = prefs(context).getBoolean(KEY_ENABLED, false)
    }

    fun setEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply()
        _enabled.value = enabled
    }
}
