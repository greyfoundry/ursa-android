package dev.astoris.ursa.wear

import android.content.Context
import androidx.core.content.edit

/** Watch-side public-page settings and encrypted phone-paired private session. */
object WearPrefs {

    private const val PREFS = "ursa_wear"
    private const val KEY_STATUS_URL = "status_url"
    private const val KEY_ACTION_CIPHER = "action_cipher"

    fun statusUrl(context: Context): String? =
        prefs(context).getString(KEY_STATUS_URL, null)?.ifBlank { null }

    fun setStatusUrl(context: Context, url: String) {
        prefs(context).edit { putString(KEY_STATUS_URL, url.trim()) }
    }

    fun pairedSession(context: Context): WearPairingPayload? {
        val cipher = prefs(context).getString(KEY_ACTION_CIPHER, null) ?: return null
        val plain = WearCrypto(context).decrypt(cipher)
        val payload = plain?.encodeToByteArray()?.let(WearPairingPayload::parse)
        if (payload == null) clearPairedSession(context)
        return payload
    }

    fun setPairedSession(context: Context, payload: WearPairingPayload) {
        val normalized = WearPairingPayload.parse(payload.encode()) ?: return
        val cipher = WearCrypto(context).encrypt(normalized.encode().decodeToString())
        prefs(context).edit { putString(KEY_ACTION_CIPHER, cipher) }
    }

    fun clearPairedSession(context: Context) {
        prefs(context).edit { remove(KEY_ACTION_CIPHER) }
    }

    fun actionConfig(context: Context): WearActionConfig? {
        val paired = pairedSession(context) ?: return null
        val config = WearActionConfig(paired.serverUrl, paired.sessionToken, paired.headers)
        return config.takeIf(WearActionConfig::isReady)
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
