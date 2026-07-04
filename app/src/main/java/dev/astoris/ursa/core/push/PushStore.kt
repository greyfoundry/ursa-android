package dev.astoris.ursa.core.push

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Holds the UnifiedPush endpoint (and the last-chosen distributor) for the app.
 *
 * Backed by plain SharedPreferences and mirrored in a process-wide StateFlow so the
 * push service (which produces the endpoint) and the UI (which displays it) stay in
 * sync within the single app process. Only the public endpoint URL is stored - it is
 * a delivery address, not a credential, so it does not need the encrypted store.
 */
object PushStore {

    private const val PREFS = "ursa_push"
    private const val KEY_ENDPOINT = "endpoint"
    private const val KEY_DISTRIBUTOR = "distributor"

    private val _endpoint = MutableStateFlow<String?>(null)
    val endpoint: StateFlow<String?> = _endpoint.asStateFlow()

    private val _distributor = MutableStateFlow<String?>(null)
    val distributor: StateFlow<String?> = _distributor.asStateFlow()

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Load persisted values into the flows. Safe to call repeatedly (e.g. app start). */
    fun load(context: Context) {
        val p = prefs(context)
        _endpoint.value = p.getString(KEY_ENDPOINT, null)
        _distributor.value = p.getString(KEY_DISTRIBUTOR, null)
    }

    fun setEndpoint(context: Context, url: String?) {
        prefs(context).edit().putString(KEY_ENDPOINT, url).apply()
        _endpoint.value = url
    }

    fun setDistributor(context: Context, distributor: String?) {
        prefs(context).edit().putString(KEY_DISTRIBUTOR, distributor).apply()
        _distributor.value = distributor
    }

    /** Clear everything on unregister. */
    fun clear(context: Context) {
        prefs(context).edit().clear().apply()
        _endpoint.value = null
        _distributor.value = null
    }
}
