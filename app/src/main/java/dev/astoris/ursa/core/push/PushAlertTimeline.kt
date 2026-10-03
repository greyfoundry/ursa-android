package dev.astoris.ursa.core.push

import dev.astoris.ursa.core.storage.LocalAlertDecision

internal object PushAlertTimeline {
    fun decision(
        outcome: String,
        reason: String? = null,
        mode: PushAlertMode? = null,
        configuredSeverity: PushSeverity? = null,
        effectiveSeverity: PushSeverity? = null,
        timing: PushAlertTiming? = null,
        scheduledAtMillis: Long? = null,
        deliveredCount: Int? = null,
        correlation: PushCorrelationResult? = null,
    ): LocalAlertDecision {
        val normalizedTiming = timing?.normalized()
        return LocalAlertDecision(
            outcome = outcome,
            reason = reason,
            mode = mode?.name,
            configuredSeverity = configuredSeverity?.name,
            effectiveSeverity = effectiveSeverity?.name,
            firstDelayMinutes = normalizedTiming?.firstDelayMinutes,
            repeatMinutes = normalizedTiming?.repeatMinutes,
            maxRepeats = normalizedTiming?.maxRepeats,
            scheduledAtMillis = scheduledAtMillis,
            deliveredCount = deliveredCount,
            correlationBasis = correlation?.basis?.name,
            correlationCount = correlation?.count,
        )
    }

    const val RECEIVED = "RECEIVED"
    const val DELAYED = "DELAYED"
    const val DELIVERED = "DELIVERED"
    const val REPEATED = "REPEATED"
    const val ACKNOWLEDGED = "ACKNOWLEDGED"
    const val SNOOZED = "SNOOZED"
    const val SUPPRESSED = "SUPPRESSED"
    const val CORRELATED = "CORRELATED"
    const val RECOVERED = "RECOVERED"

    const val REASON_POLICY = "POLICY"
    const val REASON_EVENT_PREFERENCE = "EVENT_PREFERENCE"
    const val REASON_DUPLICATE = "DUPLICATE"
    const val REASON_DEPENDENCY = "DEPENDENCY"
    const val REASON_NOTIFICATION_PERMISSION = "NOTIFICATION_PERMISSION"
    const val REASON_APP_NOTIFICATIONS_DISABLED = "APP_NOTIFICATIONS_DISABLED"
    const val REASON_CHANNEL_DISABLED = "CHANNEL_DISABLED"

    fun suppressionReason(result: PushLocalTestResult): String? = when (result) {
        PushLocalTestResult.POSTED -> null
        PushLocalTestResult.PERMISSION_REQUIRED -> REASON_NOTIFICATION_PERMISSION
        PushLocalTestResult.APP_NOTIFICATIONS_DISABLED -> REASON_APP_NOTIFICATIONS_DISABLED
        PushLocalTestResult.CHANNEL_DISABLED -> REASON_CHANNEL_DISABLED
    }
}
