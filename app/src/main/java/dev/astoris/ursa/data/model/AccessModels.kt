package dev.astoris.ursa.data.model

import kotlinx.serialization.Serializable

/** Client-side safety profile for remote actions performed through URSA. */
@Serializable
enum class AccessProfile {
    VIEW_ONLY,
    MANAGE,
    CUSTOM,
}

/** Remote action groups that can be granted to a saved connection. */
@Serializable
enum class AccessCapability {
    MONITOR_STATE,
    MONITOR_CREATE,
    MONITOR_EDIT,
    MONITOR_DELETE,
    BULK_WRITE,
    MAINTENANCE_WRITE,
    PUSH_SETUP,
    STATUS_INCIDENT_WRITE,
}
