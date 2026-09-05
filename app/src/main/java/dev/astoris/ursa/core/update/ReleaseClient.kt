package dev.astoris.ursa.core.update

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import java.io.IOException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Manual, read-only GitHub release lookup. It sends no device or installation identifier. */
class ReleaseClient {
    private val http = HttpClient(OkHttp) {
        install(HttpTimeout) { requestTimeoutMillis = 15_000 }
    }

    suspend fun latest(currentVersion: String): AvailableRelease? {
        val current = ReleaseVersion.parse(currentVersion) ?: return null
        val response = http.get(LATEST_RELEASE_API) {
            header("Accept", "application/vnd.github+json")
            header("User-Agent", "URSA/$currentVersion")
            header("X-GitHub-Api-Version", "2022-11-28")
        }
        if (!response.status.isSuccess()) throw IOException("Release service returned ${response.status.value}")
        val raw = response.bodyAsText()
        val tag = runCatching {
            Json.parseToJsonElement(raw).jsonObject["tag_name"]?.jsonPrimitive?.contentOrNull
        }.getOrNull()
        if (ReleaseVersion.parse(tag) == null) throw IOException("Release response was incompatible")
        return ReleaseUpdate.parse(raw, current)
    }

    fun close() = http.close()

    private companion object {
        const val LATEST_RELEASE_API =
            "https://api.github.com/repos/greyfoundry/ursa-android/releases/latest"
    }
}
