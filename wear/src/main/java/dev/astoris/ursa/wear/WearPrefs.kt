package dev.astoris.ursa.wear

import android.content.Context
import androidx.core.content.edit

/**
 * Watch-side settings. Just the public Kuma status-page URL the tile polls (no auth,
 * no Google Play Services). Plain preferences; a URL is not sensitive.
 */
object WearPrefs {

    private const val PREFS = "ursa_wear"
    private const val KEY_STATUS_URL = "status_url"

    fun statusUrl(context: Context): String? =
        prefs(context).getString(KEY_STATUS_URL, null)?.ifBlank { null }

    fun setStatusUrl(context: Context, url: String) {
        prefs(context).edit { putString(KEY_STATUS_URL, url.trim()) }
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
