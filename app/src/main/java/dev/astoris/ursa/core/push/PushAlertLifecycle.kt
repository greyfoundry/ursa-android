package dev.astoris.ursa.core.push

import dev.astoris.ursa.data.model.ManagedPushNotification
import java.security.MessageDigest

data class PushAlertTiming(
    val firstDelayMinutes: Int = 0,
    val repeatMinutes: Int = 0,
    val maxRepeats: Int = 0,
) {
    fun normalized(): PushAlertTiming = PushAlertTiming(
        firstDelayMinutes = firstDelayMinutes.supportedChoice(FIRST_DELAY_CHOICES),
        repeatMinutes = repeatMinutes.supportedChoice(REPEAT_CHOICES),
        maxRepeats = maxRepeats.supportedChoice(REPEAT_COUNT_CHOICES),
    )

    companion object {
        val FIRST_DELAY_CHOICES = listOf(0, 1, 5, 10)
        val REPEAT_CHOICES = listOf(0, 5, 15, 30)
        val REPEAT_COUNT_CHOICES = listOf(0, 1, 3, 5)

        private fun Int.supportedChoice(choices: List<Int>): Int = when {
            this in choices -> this
            this > choices.last() -> choices.last()
            else -> choices.first()
        }
    }
}

sealed interface PushAlertDecision {
    data object Deliver : PushAlertDecision
    data class WaitUntil(val atMillis: Long) : PushAlertDecision
    data object Cancel : PushAlertDecision
    data object Complete : PushAlertDecision
}

object PushAlertLifecycle {
    private const val MINUTE_MILLIS = 60_000L

    fun onDown(
        timing: PushAlertTiming,
        nowMillis: Long,
        snoozedUntilMillis: Long? = null,
    ): PushAlertDecision {
        val safeTiming = timing.normalized()
        val delayedUntil = nowMillis + safeTiming.firstDelayMinutes * MINUTE_MILLIS
        val deliverAt = maxOf(delayedUntil, snoozedUntilMillis ?: nowMillis)
        return if (deliverAt > nowMillis) {
            PushAlertDecision.WaitUntil(deliverAt)
        } else {
            PushAlertDecision.Deliver
        }
    }

    fun afterDelivery(
        timing: PushAlertTiming,
        nowMillis: Long,
        deliveredRepeats: Int,
    ): PushAlertDecision {
        val safeTiming = timing.normalized()
        return if (
            safeTiming.repeatMinutes > 0 &&
            deliveredRepeats.coerceAtLeast(0) < safeTiming.maxRepeats
        ) {
            PushAlertDecision.WaitUntil(nowMillis + safeTiming.repeatMinutes * MINUTE_MILLIS)
        } else {
            PushAlertDecision.Complete
        }
    }

    fun onRecovery(): PushAlertDecision = PushAlertDecision.Cancel

    fun onAcknowledge(): PushAlertDecision = PushAlertDecision.Cancel
}

data class PushAlertWorkIdentity(
    val tag: String,
    val notificationId: Int,
) {
    fun workName(deliveredCount: Int): String = "$tag-${deliveredCount.coerceAtLeast(0)}"
}

object PushAlertWork {
    fun identity(serverId: String?, monitorId: Int?): PushAlertWorkIdentity? {
        if (!ManagedPushNotification.isValidServerId(serverId) || monitorId == null || monitorId <= 0) {
            return null
        }
        val digest = MessageDigest.getInstance("SHA-256")
            .digest("$serverId:$monitorId".toByteArray())
            .take(10)
            .joinToString("") { "%02x".format(it) }
        val tag = "ursa-push-alert-$digest"
        return PushAlertWorkIdentity(tag, tag.hashCode() and Int.MAX_VALUE)
    }
}
