package dev.astoris.ursa.core.storage

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dev.astoris.ursa.core.work.ResponseAlertUtil
import kotlinx.coroutines.flow.first

private val Context.responseAlertDataStore by preferencesDataStore(name = "ursa_response_alert")

/**
 * Settings and debounce state for local slow-response alerting (#1813). Thresholds are
 * not sensitive, so this is plain (unencrypted) preferences. Per-monitor thresholds and
 * the last-alerted timestamps are stored as compact maps keyed by
 * [ResponseAlertUtil.monitorKey].
 */
class ResponseAlertStore(context: Context) {

    private val appContext = context.applicationContext

    private val enabledKey = booleanPreferencesKey("enabled")
    private val globalKey = intPreferencesKey("global_threshold_ms")
    private val perMonitorKey = stringPreferencesKey("per_monitor_thresholds")
    private val lastAlertedKey = stringPreferencesKey("last_alerted")

    suspend fun isEnabled(): Boolean =
        appContext.responseAlertDataStore.data.first()[enabledKey] ?: false

    suspend fun setEnabled(enabled: Boolean) {
        appContext.responseAlertDataStore.edit { it[enabledKey] = enabled }
    }

    suspend fun globalThresholdMs(): Int =
        appContext.responseAlertDataStore.data.first()[globalKey]
            ?: ResponseAlertUtil.DEFAULT_GLOBAL_THRESHOLD_MS

    suspend fun setGlobalThresholdMs(ms: Int) {
        appContext.responseAlertDataStore.edit { it[globalKey] = ms }
    }

    /** Per-monitor override in ms, or null when the global default applies. */
    suspend fun thresholdFor(monitorKey: String): Int? =
        perMonitorThresholds()[monitorKey]?.toInt()

    suspend fun setThresholdFor(monitorKey: String, ms: Int?) {
        val next = perMonitorThresholds().toMutableMap()
        if (ms == null || ms <= 0) next.remove(monitorKey) else next[monitorKey] = ms.toLong()
        appContext.responseAlertDataStore.edit {
            it[perMonitorKey] = ResponseAlertUtil.encodeMap(next)
        }
    }

    suspend fun perMonitorThresholds(): Map<String, Long> =
        ResponseAlertUtil.decodeMap(appContext.responseAlertDataStore.data.first()[perMonitorKey] ?: "")

    suspend fun mergePerMonitorThresholds(imported: Map<String, Long>) {
        if (imported.isEmpty()) return
        val next = perMonitorThresholds() + imported.filterValues { it > 0 }
        appContext.responseAlertDataStore.edit {
            it[perMonitorKey] = ResponseAlertUtil.encodeMap(next)
        }
    }

    suspend fun lastAlerted(): Map<String, Long> =
        ResponseAlertUtil.decodeMap(appContext.responseAlertDataStore.data.first()[lastAlertedKey] ?: "")

    suspend fun markAlerted(monitorKey: String, atMillis: Long) {
        val next = lastAlerted().toMutableMap().apply { this[monitorKey] = atMillis }
        appContext.responseAlertDataStore.edit {
            it[lastAlertedKey] = ResponseAlertUtil.encodeMap(next)
        }
    }
}
