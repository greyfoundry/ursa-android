package dev.astoris.ursa.core.network

/** A strict stable Uptime Kuma version. Pre-release and abbreviated versions are unverified. */
data class KumaVersion(
    val major: Int,
    val minor: Int,
    val patch: Int,
) : Comparable<KumaVersion> {
    override fun compareTo(other: KumaVersion): Int =
        compareValuesBy(this, other, KumaVersion::major, KumaVersion::minor, KumaVersion::patch)

    override fun toString(): String = "$major.$minor.$patch"

    companion object {
        private val STABLE_VERSION = Regex("^(0|[1-9]\\d*)\\.(0|[1-9]\\d*)\\.(0|[1-9]\\d*)$")

        fun parse(raw: String?): KumaVersion? {
            val match = STABLE_VERSION.matchEntire(raw?.trim().orEmpty()) ?: return null
            val values = match.groupValues.drop(1).map { it.toIntOrNull() ?: return null }
            return KumaVersion(values[0], values[1], values[2])
        }
    }
}

enum class KumaCompatibilityTier {
    AWAITING_VERSION,
    VERIFIED,
    UNVERIFIED_OLDER,
    UNVERIFIED_NEWER,
    UNRECOGNIZED,
}

enum class KumaFeature {
    CORE_READS,
    MONITOR_STATE_WRITE,
    MONITOR_COMMON_WRITE,
    MONITOR_DELETE,
    MAINTENANCE_WRITE,
    MANAGED_PUSH_WRITE,
    NTP_PM2_SCHEMA,
    SFTP_SCHEMA,
}

data class KumaCompatibility(
    val reportedVersion: String?,
    val version: KumaVersion?,
    val tier: KumaCompatibilityTier,
    val features: Set<KumaFeature>,
) {
    val writesVerified: Boolean get() = tier == KumaCompatibilityTier.VERIFIED

    fun supports(feature: KumaFeature): Boolean = feature in features

    fun supportsMonitorSchema(type: String): Boolean = when (type) {
        "ntp", "pm2" -> supports(KumaFeature.NTP_PM2_SCHEMA)
        "sftp" -> supports(KumaFeature.SFTP_SCHEMA)
        else -> true
    }
}

/**
 * One source of truth for version-sensitive Kuma behavior. Reads remain available
 * for diagnostics on unverified servers; writes are only advertised for the live-
 * tested stable range.
 */
object KumaCapabilities {
    val VERIFIED_MIN = KumaVersion(2, 4, 0)
    val VERIFIED_MAX = KumaVersion(2, 5, 5)

    private val BASELINE_FEATURES = setOf(
        KumaFeature.CORE_READS,
        KumaFeature.MONITOR_STATE_WRITE,
        KumaFeature.MONITOR_COMMON_WRITE,
        KumaFeature.MONITOR_DELETE,
        KumaFeature.MAINTENANCE_WRITE,
        KumaFeature.MANAGED_PUSH_WRITE,
    )

    fun evaluate(rawVersion: String?): KumaCompatibility {
        val reported = rawVersion?.trim()?.takeIf(String::isNotEmpty)
            ?: return KumaCompatibility(
                reportedVersion = null,
                version = null,
                tier = KumaCompatibilityTier.AWAITING_VERSION,
                features = setOf(KumaFeature.CORE_READS),
            )
        val version = KumaVersion.parse(reported)
            ?: return KumaCompatibility(
                reportedVersion = reported,
                version = null,
                tier = KumaCompatibilityTier.UNRECOGNIZED,
                features = setOf(KumaFeature.CORE_READS),
            )
        val tier = when {
            version < VERIFIED_MIN -> KumaCompatibilityTier.UNVERIFIED_OLDER
            version > VERIFIED_MAX -> KumaCompatibilityTier.UNVERIFIED_NEWER
            else -> KumaCompatibilityTier.VERIFIED
        }
        if (tier != KumaCompatibilityTier.VERIFIED) {
            return KumaCompatibility(reported, version, tier, setOf(KumaFeature.CORE_READS))
        }
        val features = buildSet {
            addAll(BASELINE_FEATURES)
            if (version >= KumaVersion(2, 5, 0)) add(KumaFeature.NTP_PM2_SCHEMA)
            if (version >= KumaVersion(2, 5, 4)) add(KumaFeature.SFTP_SCHEMA)
        }
        return KumaCompatibility(reported, version, tier, features)
    }
}
