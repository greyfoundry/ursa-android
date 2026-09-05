package dev.astoris.ursa.core.push

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class PushRegistrationError { INTERNAL_ERROR, NETWORK, ACTION_REQUIRED, VAPID_REQUIRED }

enum class PushLocalTestResult {
    POSTED,
    PERMISSION_REQUIRED,
    APP_NOTIFICATIONS_DISABLED,
    CHANNEL_DISABLED,
}

data class PushDiagnostics(
    val lastRegistrationAtMs: Long? = null,
    val lastMessageAtMs: Long? = null,
    val lastErrorAtMs: Long? = null,
    val lastError: PushRegistrationError? = null,
    val lastLocalTestAtMs: Long? = null,
    val lastLocalTestResult: PushLocalTestResult? = null,
    val deliveryTestRequestedAtMs: Long? = null,
    val deliveryTestReceivedAtMs: Long? = null,
    val deliveryTestRejectedAtMs: Long? = null,
)

/**
 * Holds the UnifiedPush endpoint (and the last-chosen distributor) for the app.
 *
 * Backed by plain SharedPreferences and mirrored in a process-wide StateFlow so the
 * push service (which produces the endpoint) and the UI (which displays it) stay in
 * sync within the single app process. The public delivery endpoint is stored with
 * privacy-safe diagnostic timestamps, normalized result categories, and one short
 * pending test marker. Message bodies, monitor names, and server URLs are never kept.
 */
object PushStore {

    private const val PREFS = "ursa_push"
    private const val KEY_ENDPOINT = "endpoint"
    private const val KEY_DISTRIBUTOR = "distributor"
    private const val KEY_LAST_REGISTRATION_AT = "last_registration_at"
    private const val KEY_LAST_MESSAGE_AT = "last_message_at"
    private const val KEY_LAST_ERROR_AT = "last_error_at"
    private const val KEY_LAST_ERROR = "last_error"
    private const val KEY_LAST_LOCAL_TEST_AT = "last_local_test_at"
    private const val KEY_LAST_LOCAL_TEST_RESULT = "last_local_test_result"
    private const val KEY_DELIVERY_TEST_TOKEN = "delivery_test_token"
    private const val KEY_DELIVERY_TEST_REQUESTED_AT = "delivery_test_requested_at"
    private const val KEY_DELIVERY_TEST_RECEIVED_AT = "delivery_test_received_at"
    private const val KEY_DELIVERY_TEST_REJECTED_AT = "delivery_test_rejected_at"

    private val _endpoint = MutableStateFlow<String?>(null)
    val endpoint: StateFlow<String?> = _endpoint.asStateFlow()

    private val _distributor = MutableStateFlow<String?>(null)
    val distributor: StateFlow<String?> = _distributor.asStateFlow()

    private val _diagnostics = MutableStateFlow(PushDiagnostics())
    val diagnostics: StateFlow<PushDiagnostics> = _diagnostics.asStateFlow()

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Load persisted values into the flows. Safe to call repeatedly (e.g. app start). */
    fun load(context: Context) {
        val p = prefs(context)
        _endpoint.value = p.getString(KEY_ENDPOINT, null)
        _distributor.value = p.getString(KEY_DISTRIBUTOR, null)
        _diagnostics.value = readDiagnostics(p)
    }

    fun recordRegistered(context: Context, url: String) {
        val p = prefs(context)
        p.edit {
            putString(KEY_ENDPOINT, url)
            putLong(KEY_LAST_REGISTRATION_AT, System.currentTimeMillis())
        }
        _endpoint.value = url
        _diagnostics.value = readDiagnostics(p)
    }

    fun setDistributor(context: Context, distributor: String?) {
        prefs(context).edit { putString(KEY_DISTRIBUTOR, distributor) }
        _distributor.value = distributor
    }

    fun recordMessage(context: Context, message: String): Boolean {
        val p = prefs(context)
        val token = p.getString(KEY_DELIVERY_TEST_TOKEN, null)
        val matchedDeliveryTest = token?.let { PushDeliveryTest.matches(message, it) } == true
        val now = System.currentTimeMillis()
        p.edit {
            putLong(KEY_LAST_MESSAGE_AT, now)
            if (matchedDeliveryTest) {
                putLong(KEY_DELIVERY_TEST_RECEIVED_AT, now)
                remove(KEY_DELIVERY_TEST_TOKEN)
            }
        }
        _diagnostics.value = readDiagnostics(p)
        return matchedDeliveryTest
    }

    fun recordRegistrationError(context: Context, error: PushRegistrationError) {
        val p = prefs(context)
        p.edit {
            putLong(KEY_LAST_ERROR_AT, System.currentTimeMillis())
            putString(KEY_LAST_ERROR, error.name)
        }
        _diagnostics.value = readDiagnostics(p)
    }

    fun recordLocalTest(context: Context, result: PushLocalTestResult) {
        val p = prefs(context)
        p.edit {
            putLong(KEY_LAST_LOCAL_TEST_AT, System.currentTimeMillis())
            putString(KEY_LAST_LOCAL_TEST_RESULT, result.name)
        }
        _diagnostics.value = readDiagnostics(p)
    }

    fun beginDeliveryTest(context: Context, token: String): Boolean {
        if (PushDeliveryTest.notificationName(token) == null) return false
        val p = prefs(context)
        p.edit {
            putString(KEY_DELIVERY_TEST_TOKEN, token)
            putLong(KEY_DELIVERY_TEST_REQUESTED_AT, System.currentTimeMillis())
            remove(KEY_DELIVERY_TEST_RECEIVED_AT)
            remove(KEY_DELIVERY_TEST_REJECTED_AT)
        }
        _diagnostics.value = readDiagnostics(p)
        return true
    }

    fun recordDeliveryTestRejected(context: Context) {
        val p = prefs(context)
        p.edit {
            remove(KEY_DELIVERY_TEST_TOKEN)
            putLong(KEY_DELIVERY_TEST_REJECTED_AT, System.currentTimeMillis())
        }
        _diagnostics.value = readDiagnostics(p)
    }

    fun recordUnregistered(context: Context) {
        val p = prefs(context)
        p.edit {
            remove(KEY_ENDPOINT)
            clearDeliveryTest()
        }
        _endpoint.value = null
        _diagnostics.value = readDiagnostics(p)
    }

    /** Clear registration state on unregister while retaining privacy-safe history. */
    fun clear(context: Context) {
        clearRegistration(context)
    }

    private fun clearRegistration(context: Context) {
        val p = prefs(context)
        p.edit {
            remove(KEY_ENDPOINT)
            remove(KEY_DISTRIBUTOR)
            clearDeliveryTest()
        }
        _endpoint.value = null
        _distributor.value = null
        _diagnostics.value = readDiagnostics(p)
    }

    private fun android.content.SharedPreferences.Editor.clearDeliveryTest() {
        remove(KEY_DELIVERY_TEST_TOKEN)
        remove(KEY_DELIVERY_TEST_REQUESTED_AT)
        remove(KEY_DELIVERY_TEST_RECEIVED_AT)
        remove(KEY_DELIVERY_TEST_REJECTED_AT)
    }

    private fun readDiagnostics(prefs: android.content.SharedPreferences): PushDiagnostics =
        PushDiagnostics(
            lastRegistrationAtMs = prefs.timestamp(KEY_LAST_REGISTRATION_AT),
            lastMessageAtMs = prefs.timestamp(KEY_LAST_MESSAGE_AT),
            lastErrorAtMs = prefs.timestamp(KEY_LAST_ERROR_AT),
            lastError = prefs.getString(KEY_LAST_ERROR, null)?.let { value ->
                runCatching { PushRegistrationError.valueOf(value) }.getOrNull()
            },
            lastLocalTestAtMs = prefs.timestamp(KEY_LAST_LOCAL_TEST_AT),
            lastLocalTestResult = prefs.getString(KEY_LAST_LOCAL_TEST_RESULT, null)?.let { value ->
                runCatching { PushLocalTestResult.valueOf(value) }.getOrNull()
            },
            deliveryTestRequestedAtMs = prefs.timestamp(KEY_DELIVERY_TEST_REQUESTED_AT),
            deliveryTestReceivedAtMs = prefs.timestamp(KEY_DELIVERY_TEST_RECEIVED_AT),
            deliveryTestRejectedAtMs = prefs.timestamp(KEY_DELIVERY_TEST_REJECTED_AT),
        )

    private fun android.content.SharedPreferences.timestamp(key: String): Long? =
        getLong(key, 0L).takeIf { it > 0L }
}
