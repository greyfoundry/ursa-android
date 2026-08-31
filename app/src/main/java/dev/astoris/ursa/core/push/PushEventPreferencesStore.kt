package dev.astoris.ursa.core.push

import android.content.Context
import androidx.core.content.edit

class PushEventPreferencesStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun load(): PushEventPreferences = PushEventPreferences(
        recoveryEnabled = prefs.getBoolean(KEY_RECOVERY, true),
        maintenanceEnabled = prefs.getBoolean(KEY_MAINTENANCE, true),
        certificateEnabled = prefs.getBoolean(KEY_CERTIFICATE, true),
        updateEnabled = prefs.getBoolean(KEY_UPDATE, true),
    )

    fun save(settings: PushEventPreferences) {
        prefs.edit {
            putBoolean(KEY_RECOVERY, settings.recoveryEnabled)
            putBoolean(KEY_MAINTENANCE, settings.maintenanceEnabled)
            putBoolean(KEY_CERTIFICATE, settings.certificateEnabled)
            putBoolean(KEY_UPDATE, settings.updateEnabled)
        }
    }

    private companion object {
        const val PREFS = "ursa_push_event_preferences"
        const val KEY_RECOVERY = "recovery"
        const val KEY_MAINTENANCE = "maintenance"
        const val KEY_CERTIFICATE = "certificate"
        const val KEY_UPDATE = "update"
    }
}
