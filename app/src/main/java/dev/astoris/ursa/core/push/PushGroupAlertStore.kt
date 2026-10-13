package dev.astoris.ursa.core.push

import android.content.Context
import androidx.core.content.edit
import dev.astoris.ursa.R
import dev.astoris.ursa.core.storage.Crypto
import dev.astoris.ursa.core.storage.EventLogStore
import dev.astoris.ursa.core.storage.LocalEventKind
import dev.astoris.ursa.data.model.ManagedPushNotification
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

data class PushGroupAlertRule(
    val groupId: Int,
    val memberIds: Set<Int>,
    val requiredDown: Int,
)

data class PushGroupAlertRules(
    val byGroupId: Map<Int, PushGroupAlertRule> = emptyMap(),
)

data class PushGroupAlertEvaluation(
    val groupId: Int,
    val requiredDown: Int,
    val memberCount: Int,
    val downCount: Int,
) {
    val suppressed: Boolean get() = downCount < requiredDown
}

object PushGroupAlertPolicy {
    fun updated(
        rules: PushGroupAlertRules,
        groupId: Int,
        memberIds: Set<Int>,
        requiredDown: Int?,
    ): PushGroupAlertRules? {
        val updated = rules.byGroupId.toMutableMap().apply {
            if (requiredDown == null) remove(groupId)
            else put(groupId, PushGroupAlertRule(groupId, memberIds, requiredDown))
        }
        return validated(updated)
    }

    fun validated(rules: Map<Int, PushGroupAlertRule>): PushGroupAlertRules? {
        if (rules.size > MAX_GROUPS) return null
        val normalized = linkedMapOf<Int, PushGroupAlertRule>()
        val claimedMembers = mutableSetOf<Int>()
        for ((key, raw) in rules.toSortedMap()) {
            val members = raw.memberIds.toSortedSet()
            if (
                key <= 0 || key != raw.groupId ||
                members.size !in 2..MAX_MEMBERS_PER_GROUP ||
                members.any { it <= 0 || it == key } ||
                raw.requiredDown !in 2..members.size ||
                members.any { it in claimedMembers }
            ) return null
            claimedMembers += members
            normalized[key] = raw.copy(memberIds = members)
        }
        return PushGroupAlertRules(normalized)
    }

    fun reconciled(
        rules: PushGroupAlertRules,
        currentMembersByGroup: Map<Int, Set<Int>>,
    ): PushGroupAlertRules {
        val retained = rules.byGroupId.mapNotNull { (groupId, rule) ->
            val members = currentMembersByGroup[groupId].orEmpty()
            if (rule.requiredDown !in 2..members.size) null
            else groupId to rule.copy(memberIds = members)
        }.toMap()
        return validated(retained) ?: PushGroupAlertRules()
    }

    fun evaluate(
        rules: PushGroupAlertRules,
        monitorId: Int,
        statusByMonitor: Map<Int, Int?>,
    ): PushGroupAlertEvaluation? {
        val safe = validated(rules.byGroupId) ?: return null
        val rule = safe.byGroupId.values.firstOrNull { monitorId in it.memberIds } ?: return null
        return PushGroupAlertEvaluation(
            groupId = rule.groupId,
            requiredDown = rule.requiredDown,
            memberCount = rule.memberIds.size,
            downCount = rule.memberIds.count { statusByMonitor[it] == 0 },
        )
    }

    private const val MAX_GROUPS = 100
    private const val MAX_MEMBERS_PER_GROUP = 1_000
}

object PushGroupAlertRulesCodec {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun encode(rules: PushGroupAlertRules): String = json.encodeToString(
        PersistedRules(
            version = VERSION,
            rules = rules.byGroupId.values.map {
                PersistedRule(it.groupId, it.memberIds.sorted(), it.requiredDown)
            },
        ),
    )

    fun decode(raw: String?): PushGroupAlertRules? {
        val saved = raw?.let { runCatching { json.decodeFromString<PersistedRules>(it) }.getOrNull() }
            ?: return null
        if (saved.version != VERSION) return null
        val rows = linkedMapOf<Int, PushGroupAlertRule>()
        for (row in saved.rules) {
            if (row.groupId in rows) return null
            rows[row.groupId] = PushGroupAlertRule(row.groupId, row.memberIds.toSet(), row.requiredDown)
        }
        return PushGroupAlertPolicy.validated(rows)
    }

    @Serializable
    private data class PersistedRules(val version: Int, val rules: List<PersistedRule>)

    @Serializable
    private data class PersistedRule(
        val groupId: Int,
        val memberIds: List<Int>,
        val requiredDown: Int,
    )

    private const val VERSION = 1
}

class PushGroupAlertStore(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val crypto = Crypto(appContext)

    fun load(serverId: String?): PushGroupAlertRules {
        val valid = serverId?.takeIf(ManagedPushNotification::isValidServerId)
            ?: return PushGroupAlertRules()
        val cipher = prefs.getString(key(valid), null) ?: return PushGroupAlertRules()
        return crypto.decrypt(cipher)?.let(PushGroupAlertRulesCodec::decode) ?: PushGroupAlertRules()
    }

    fun save(serverId: String, rules: PushGroupAlertRules): Boolean {
        if (!ManagedPushNotification.isValidServerId(serverId)) return false
        val safe = PushGroupAlertPolicy.validated(rules.byGroupId) ?: return false
        return runCatching {
            prefs.edit {
                if (safe.byGroupId.isEmpty()) remove(key(serverId))
                else putString(key(serverId), crypto.encrypt(PushGroupAlertRulesCodec.encode(safe)))
            }
            true
        }.getOrDefault(false)
    }

    fun evaluation(
        serverId: String,
        monitorId: Int,
        statusStore: PushTransitionStore = PushTransitionStore(appContext),
    ): PushGroupAlertEvaluation? {
        val rules = load(serverId)
        val memberIds = rules.byGroupId.values.firstOrNull { monitorId in it.memberIds }?.memberIds
            ?: return null
        return PushGroupAlertPolicy.evaluate(rules, monitorId, statusStore.statuses(serverId, memberIds))
    }

    fun clearServer(serverId: String?) {
        val valid = serverId?.takeIf(ManagedPushNotification::isValidServerId) ?: return
        prefs.edit { remove(key(valid)) }
    }

    private companion object {
        const val PREFS = "ursa_push_group_alerts"
        fun key(serverId: String) = "groups:$serverId"
    }
}

suspend fun recordPushGroupAlertSuppression(
    context: Context,
    scope: ManagedPushScope,
    monitorId: Int,
    monitorName: String,
    evaluation: PushGroupAlertEvaluation,
    alertId: String? = null,
) {
    val groupName = scope.monitor(evaluation.groupId)?.name
        ?: context.getString(R.string.monitor_fallback_name, evaluation.groupId)
    EventLogStore(context).append(
        serverUrl = scope.serverUrl,
        monitorId = monitorId,
        monitorName = monitorName,
        kind = LocalEventKind.PUSH_SUPPRESSED,
        detail = context.getString(
            R.string.push_group_alert_suppressed_detail,
            evaluation.downCount,
            evaluation.memberCount,
            groupName,
            evaluation.requiredDown,
        ),
        alertId = alertId,
        alertDecision = PushAlertTimeline.decision(
            outcome = PushAlertTimeline.SUPPRESSED,
            reason = PushAlertTimeline.REASON_GROUP_THRESHOLD,
        ),
    )
}
