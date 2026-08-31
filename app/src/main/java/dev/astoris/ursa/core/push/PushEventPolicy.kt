package dev.astoris.ursa.core.push

enum class PushEventCategory { DOWN, RECOVERY, PENDING, MAINTENANCE, OTHER }

data class PushEventPreferences(
    val recoveryEnabled: Boolean = true,
    val maintenanceEnabled: Boolean = true,
    val certificateEnabled: Boolean = true,
    val updateEnabled: Boolean = true,
)

object PushEventPolicy {
    val RECOVERY_ROUTE = PushChannelRoute(
        channelId = "ursa_monitors_recovery",
        highPriority = false,
        sound = true,
        vibration = false,
    )
    val MAINTENANCE_ROUTE = PushChannelRoute(
        channelId = "ursa_monitors_maintenance",
        highPriority = false,
        sound = false,
        vibration = false,
    )
    val UPDATE_ROUTE = PushChannelRoute(
        channelId = "ursa_updates",
        highPriority = false,
        sound = false,
        vibration = false,
    )

    fun category(status: Int?): PushEventCategory = when (status) {
        0 -> PushEventCategory.DOWN
        1 -> PushEventCategory.RECOVERY
        2 -> PushEventCategory.PENDING
        3 -> PushEventCategory.MAINTENANCE
        else -> PushEventCategory.OTHER
    }

    fun shouldNotify(status: Int?, preferences: PushEventPreferences): Boolean = when (category(status)) {
        PushEventCategory.RECOVERY -> preferences.recoveryEnabled
        PushEventCategory.MAINTENANCE -> preferences.maintenanceEnabled
        else -> true
    }

    fun route(status: Int?, severity: PushSeverity): PushChannelRoute = when (category(status)) {
        PushEventCategory.RECOVERY -> RECOVERY_ROUTE
        PushEventCategory.MAINTENANCE -> MAINTENANCE_ROUTE
        else -> PushSeverityPolicy.route(severity)
    }
}

data class PushTransitionRecord(val status: Int?, val atMillis: Long)

data class PushTransitionDecision(
    val deliver: Boolean,
    val next: PushTransitionRecord,
)

object PushTransitionDedup {
    const val WINDOW_MILLIS = 2L * 60L * 1_000L

    fun evaluate(
        previous: PushTransitionRecord?,
        status: Int?,
        nowMillis: Long,
    ): PushTransitionDecision {
        val elapsed = previous?.let { nowMillis - it.atMillis }
        val duplicate = previous?.status == status && elapsed != null && elapsed in 0..WINDOW_MILLIS
        return if (duplicate) {
            PushTransitionDecision(deliver = false, next = previous)
        } else {
            PushTransitionDecision(deliver = true, next = PushTransitionRecord(status, nowMillis))
        }
    }
}
