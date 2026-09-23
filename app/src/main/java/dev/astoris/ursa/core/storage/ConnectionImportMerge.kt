package dev.astoris.ursa.core.storage

import dev.astoris.ursa.data.model.ServerConnection

/** Pure merge policy shared by backup import and its compatibility tests. */
object ConnectionImportMerge {
    fun merge(
        existing: List<ServerConnection>,
        imported: List<ServerConnection>,
        restoreAccessProfiles: Boolean = false,
    ): List<ServerConnection> {
        val merged = existing.toMutableList()
        imported.forEach { incoming ->
            val index = merged.indexOfFirst { it.url == incoming.url }
            if (index < 0) {
                merged += incoming
            } else {
                val local = merged[index]
                merged[index] = incoming.copy(
                    jwt = incoming.jwt ?: local.jwt,
                    accessProfile = if (restoreAccessProfiles) {
                        incoming.accessProfile
                    } else {
                        local.accessProfile
                    },
                    customCapabilities = if (restoreAccessProfiles) {
                        incoming.customCapabilities
                    } else {
                        local.customCapabilities
                    },
                    cleartextPolicy = local.cleartextPolicy,
                )
            }
        }
        return merged
    }
}
