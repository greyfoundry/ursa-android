package dev.astoris.ursa.core.update

import java.net.URI
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

data class ReleaseVersion(val major: Int, val minor: Int, val patch: Int) : Comparable<ReleaseVersion> {
    override fun compareTo(other: ReleaseVersion): Int =
        compareValuesBy(this, other, ReleaseVersion::major, ReleaseVersion::minor, ReleaseVersion::patch)

    override fun toString() = "$major.$minor.$patch"

    companion object {
        private val pattern = Regex("^v?(0|[1-9]\\d*)\\.(0|[1-9]\\d*)\\.(0|[1-9]\\d*)$")
        fun parse(value: String?): ReleaseVersion? {
            val match = pattern.matchEntire(value.orEmpty().trim()) ?: return null
            return runCatching { ReleaseVersion(match.groupValues[1].toInt(), match.groupValues[2].toInt(), match.groupValues[3].toInt()) }
                .getOrNull()
        }
    }
}

data class AvailableRelease(
    val version: ReleaseVersion,
    val title: String,
    val notes: String,
    val webUrl: String,
)

object ReleaseUpdate {
    fun parse(raw: String, current: ReleaseVersion): AvailableRelease? {
        val obj = runCatching { Json.parseToJsonElement(raw).jsonObject }.getOrNull() ?: return null
        if (obj["draft"]?.jsonPrimitive?.booleanOrNull == true || obj["prerelease"]?.jsonPrimitive?.booleanOrNull == true) return null
        val version = ReleaseVersion.parse(obj["tag_name"]?.jsonPrimitive?.contentOrNull) ?: return null
        if (version <= current) return null
        val webUrl = obj["html_url"]?.jsonPrimitive?.contentOrNull ?: return null
        val uri = runCatching { URI(webUrl) }.getOrNull() ?: return null
        if (uri.scheme != "https" || uri.host != "github.com" || !uri.path.startsWith("/greyfoundry/ursa-android/releases/")) return null
        return AvailableRelease(
            version = version,
            title = obj["name"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty().take(100).ifBlank { "URSA $version" },
            notes = obj["body"]?.jsonPrimitive?.contentOrNull.orEmpty().trim().take(4_000),
            webUrl = webUrl,
        )
    }
}
