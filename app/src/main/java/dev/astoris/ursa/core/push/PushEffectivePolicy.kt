package dev.astoris.ursa.core.push

import java.time.ZoneId

enum class PushPolicyScope(val precedence: Int) {
    DEFAULT(0),
    SERVER(1),
    GROUP(2),
    TAG(3),
    MONITOR(4),
}

data class PushPolicyLayer(
    val scope: PushPolicyScope,
    val stableKey: String,
    val mode: PushAlertMode? = null,
    val severity: PushSeverity? = null,
    val timing: PushAlertTiming? = null,
)

data class SourcedPushValue<T>(
    val value: T,
    val source: PushPolicyScope,
    val sourceKey: String? = null,
)

enum class PushDownDelivery {
    SETUP_REQUIRED,
    NOT_ASSIGNED,
    DEVICE_BLOCKED,
    MODE_BLOCKED,
    DELAYED,
    SNOOZED,
    IMMEDIATE,
}

data class PushEffectivePolicy(
    val mode: SourcedPushValue<PushAlertMode>,
    val severity: SourcedPushValue<PushSeverity>,
    val timing: SourcedPushValue<PushAlertTiming>,
    val effectiveSeverity: PushSeverity,
    val quietHoursActive: Boolean,
    val snoozedUntilMillis: Long?,
    val downDelivery: PushDownDelivery,
    val scheduledAtMillis: Long?,
    val recoveryWillNotify: Boolean,
    val maintenanceWillNotify: Boolean,
)

object PushEffectivePolicyResolver {
    fun resolve(
        layers: List<PushPolicyLayer>,
        quietHours: PushQuietHours,
        eventPreferences: PushEventPreferences,
        setupCurrent: Boolean,
        providerAssigned: Boolean,
        notificationsAllowed: Boolean,
        snoozedUntilMillis: Long?,
        nowMillis: Long = System.currentTimeMillis(),
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): PushEffectivePolicy {
        val mode = resolveValue(layers, PushAlertMode.ALL_TRANSITIONS, PushPolicyLayer::mode)
        val severity = resolveValue(layers, PushSeverity.CRITICAL, PushPolicyLayer::severity)
        val timing = resolveValue(layers, PushAlertTiming(), PushPolicyLayer::timing)
            .let { it.copy(value = it.value.normalized()) }
        val quiet = PushQuietHoursPolicy.isQuietNow(quietHours, nowMillis, zoneId)
        val effectiveSeverity = if (quiet) PushSeverity.SILENT else severity.value
        val eligible = setupCurrent && providerAssigned && notificationsAllowed
        val decision = PushAlertLifecycle.onDown(timing.value, nowMillis, snoozedUntilMillis)
        val downDelivery = when {
            !setupCurrent -> PushDownDelivery.SETUP_REQUIRED
            !providerAssigned -> PushDownDelivery.NOT_ASSIGNED
            !notificationsAllowed -> PushDownDelivery.DEVICE_BLOCKED
            !PushAlertPolicy.shouldNotify(mode.value, 0) -> PushDownDelivery.MODE_BLOCKED
            decision is PushAlertDecision.WaitUntil &&
                snoozedUntilMillis != null && decision.atMillis == snoozedUntilMillis -> PushDownDelivery.SNOOZED
            decision is PushAlertDecision.WaitUntil -> PushDownDelivery.DELAYED
            else -> PushDownDelivery.IMMEDIATE
        }
        return PushEffectivePolicy(
            mode = mode,
            severity = severity,
            timing = timing,
            effectiveSeverity = effectiveSeverity,
            quietHoursActive = quiet,
            snoozedUntilMillis = snoozedUntilMillis?.takeIf { it > nowMillis },
            downDelivery = downDelivery,
            scheduledAtMillis = (decision as? PushAlertDecision.WaitUntil)?.atMillis
                ?.takeIf { downDelivery == PushDownDelivery.DELAYED || downDelivery == PushDownDelivery.SNOOZED },
            recoveryWillNotify = eligible &&
                PushAlertPolicy.shouldNotify(mode.value, 1) &&
                PushEventPolicy.shouldNotify(1, eventPreferences),
            maintenanceWillNotify = eligible &&
                PushAlertPolicy.shouldNotify(mode.value, 3) &&
                PushEventPolicy.shouldNotify(3, eventPreferences),
        )
    }

    private fun <T> resolveValue(
        layers: List<PushPolicyLayer>,
        default: T,
        value: (PushPolicyLayer) -> T?,
    ): SourcedPushValue<T> {
        val winner = layers.asSequence()
            .filter { it.scope != PushPolicyScope.DEFAULT && value(it) != null }
            .maxWithOrNull(compareBy<PushPolicyLayer>({ it.scope.precedence }, { it.stableKey }))
        return winner?.let { SourcedPushValue(value(it)!!, it.scope, it.stableKey) }
            ?: SourcedPushValue(default, PushPolicyScope.DEFAULT)
    }
}
