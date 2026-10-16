package dev.astoris.ursa.core.storage

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ScreenshotProtectionMode {
    OFF,
    SENSITIVE,
    ALWAYS,
    ;

    companion object {
        fun fromStored(value: String?): ScreenshotProtectionMode =
            entries.firstOrNull { it.name == value } ?: ALWAYS
    }
}

fun ScreenshotProtectionMode.shouldSecure(sensitiveContent: Boolean): Boolean = when (this) {
    ScreenshotProtectionMode.OFF -> false
    ScreenshotProtectionMode.SENSITIVE -> sensitiveContent
    ScreenshotProtectionMode.ALWAYS -> true
}

/** Device presentation and privacy choices. Compact mode never reduces touch targets. */
object DisplayPreferenceStore {
    private const val PREFS = "ursa_display_preferences"
    private const val KEY_COMPACT = "compact"
    private const val KEY_SCREENSHOT_PROTECTION = "screenshot_protection"
    private val _compact = MutableStateFlow(false)
    val compact: StateFlow<Boolean> = _compact.asStateFlow()
    private val _screenshotProtection = MutableStateFlow(ScreenshotProtectionMode.ALWAYS)
    val screenshotProtection: StateFlow<ScreenshotProtectionMode> = _screenshotProtection.asStateFlow()

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun load(context: Context) {
        val preferences = prefs(context)
        _compact.value = preferences.getBoolean(KEY_COMPACT, false)
        _screenshotProtection.value = ScreenshotProtectionMode.fromStored(
            preferences.getString(KEY_SCREENSHOT_PROTECTION, null),
        )
    }

    fun setCompact(context: Context, enabled: Boolean) {
        prefs(context).edit { putBoolean(KEY_COMPACT, enabled) }
        _compact.value = enabled
    }

    fun setScreenshotProtection(context: Context, mode: ScreenshotProtectionMode) {
        prefs(context).edit { putString(KEY_SCREENSHOT_PROTECTION, mode.name) }
        _screenshotProtection.value = mode
    }
}
