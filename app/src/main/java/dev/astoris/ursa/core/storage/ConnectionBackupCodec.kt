package dev.astoris.ursa.core.storage

import dev.astoris.ursa.data.model.RequestHeader
import dev.astoris.ursa.data.model.ServerConnection
import java.net.URI
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

sealed interface BackupDecodeResult {
    data class Success(val data: ConnectionBackupData) : BackupDecodeResult
    data class Error(val reason: BackupError) : BackupDecodeResult
}

enum class BackupError { INVALID_DOCUMENT, WRONG_PASSWORD_OR_DAMAGED, INVALID_CONTENT }

data class ConnectionBackupData(
    val connections: List<ServerConnection>,
    val preferences: PortablePreferences = PortablePreferences(),
)

@Serializable
data class PortablePreferences(
    val dynamicColor: Boolean = false,
    val slowAlertsEnabled: Boolean = false,
    val slowAlertThresholdMs: Int = 1_000,
    val perMonitorThresholds: Map<String, Long> = emptyMap(),
    val favoritesByServer: Map<String, Set<Int>> = emptyMap(),
)

/** Password-encrypted, versioned portable backup for saved server connections. */
object ConnectionBackupCodec {
    private val json = Json { ignoreUnknownKeys = true }
    private val random = SecureRandom()

    fun encrypt(
        data: ConnectionBackupData,
        password: CharArray,
        includeSessions: Boolean = false,
    ): String {
        require(password.size >= MIN_PASSWORD_LENGTH)
        require(data.connections.size <= MAX_CONNECTIONS)
        val portable = PortableBackup(
            connections = data.connections.map { connection ->
                PortableConnection(
                    url = connection.url,
                    username = connection.username,
                    insecure = connection.insecure,
                    alias = connection.alias,
                    headers = connection.headers,
                    sessionToken = connection.jwt.takeIf { includeSessions },
                )
            },
            preferences = data.preferences,
        )
        val plaintext = json.encodeToString(portable).encodeToByteArray()
        val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
        val nonce = ByteArray(NONCE_BYTES).also(random::nextBytes)
        val key = deriveKey(password, salt, ITERATIONS)
        val ciphertext = Cipher.getInstance(CIPHER).run {
            init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_BITS, nonce))
            updateAAD(AAD)
            doFinal(plaintext)
        }
        return json.encodeToString(
            EncryptedBackup(
                iterations = ITERATIONS,
                salt = salt.base64(),
                nonce = nonce.base64(),
                ciphertext = ciphertext.base64(),
            ),
        )
    }

    fun decrypt(document: String, password: CharArray): BackupDecodeResult {
        if (document.length > MAX_DOCUMENT_CHARS || password.size < MIN_PASSWORD_LENGTH) {
            return BackupDecodeResult.Error(BackupError.INVALID_DOCUMENT)
        }
        val envelope = runCatching { json.decodeFromString<EncryptedBackup>(document) }.getOrNull()
            ?: return BackupDecodeResult.Error(BackupError.INVALID_DOCUMENT)
        if (envelope.version != FORMAT_VERSION || envelope.kdf != KDF ||
            envelope.iterations !in MIN_ITERATIONS..MAX_ITERATIONS
        ) {
            return BackupDecodeResult.Error(BackupError.INVALID_DOCUMENT)
        }
        val salt = envelope.salt.decodeBase64()?.takeIf { it.size == SALT_BYTES }
            ?: return BackupDecodeResult.Error(BackupError.INVALID_DOCUMENT)
        val nonce = envelope.nonce.decodeBase64()?.takeIf { it.size == NONCE_BYTES }
            ?: return BackupDecodeResult.Error(BackupError.INVALID_DOCUMENT)
        val ciphertext = envelope.ciphertext.decodeBase64()
            ?: return BackupDecodeResult.Error(BackupError.INVALID_DOCUMENT)
        val plaintext = try {
            val key = deriveKey(password, salt, envelope.iterations)
            Cipher.getInstance(CIPHER).run {
                init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, nonce))
                updateAAD(AAD)
                doFinal(ciphertext)
            }
        } catch (_: AEADBadTagException) {
            return BackupDecodeResult.Error(BackupError.WRONG_PASSWORD_OR_DAMAGED)
        } catch (_: Exception) {
            return BackupDecodeResult.Error(BackupError.INVALID_DOCUMENT)
        }
        val backup = runCatching {
            json.decodeFromString<PortableBackup>(plaintext.decodeToString())
        }.getOrNull() ?: return BackupDecodeResult.Error(BackupError.INVALID_CONTENT)
        if (backup.version != PAYLOAD_VERSION || backup.connections.size > MAX_CONNECTIONS) {
            return BackupDecodeResult.Error(BackupError.INVALID_CONTENT)
        }
        val connections = backup.connections.mapNotNull(::validate)
        if (connections.size != backup.connections.size ||
            connections.map { it.url }.distinct().size != connections.size
        ) {
            return BackupDecodeResult.Error(BackupError.INVALID_CONTENT)
        }
        if (!validPreferences(backup.preferences, connections.map { it.url }.toSet())) {
            return BackupDecodeResult.Error(BackupError.INVALID_CONTENT)
        }
        return BackupDecodeResult.Success(ConnectionBackupData(connections, backup.preferences))
    }

    private fun validate(connection: PortableConnection): ServerConnection? {
        val uri = runCatching { URI(connection.url.trim()) }.getOrNull() ?: return null
        if (uri.scheme?.lowercase() !in setOf("http", "https") || uri.host.isNullOrBlank()) return null
        if (connection.url.length > 2048 || connection.username.length > 256) return null
        if ((connection.alias?.length ?: 0) > 80 || (connection.sessionToken?.length ?: 0) > 8192) return null
        if (connection.headers.size > MAX_HEADERS) return null
        val headers = connection.headers.mapNotNull { it.normalizedOrNull() }
        if (headers.size != connection.headers.size ||
            headers.map { it.name.lowercase() }.distinct().size != headers.size
        ) return null
        return ServerConnection(
            url = connection.url.trim().removeSuffix("/"),
            username = connection.username,
            jwt = connection.sessionToken,
            insecure = connection.insecure,
            alias = connection.alias?.trim()?.takeIf { it.isNotEmpty() },
            headers = headers,
        )
    }

    private fun deriveKey(password: CharArray, salt: ByteArray, iterations: Int): SecretKeySpec {
        val spec = PBEKeySpec(password, salt, iterations, KEY_BITS)
        return try {
            val encoded = SecretKeyFactory.getInstance(KDF).generateSecret(spec).encoded
            SecretKeySpec(encoded, "AES")
        } finally {
            spec.clearPassword()
        }
    }

    private fun validPreferences(preferences: PortablePreferences, serverUrls: Set<String>): Boolean {
        if (preferences.slowAlertThresholdMs !in MIN_THRESHOLD_MS..MAX_THRESHOLD_MS) return false
        if (preferences.perMonitorThresholds.size > MAX_MONITOR_PREFERENCES ||
            preferences.favoritesByServer.size > MAX_CONNECTIONS
        ) return false
        if (preferences.perMonitorThresholds.any { (key, value) ->
                key.length > 2200 || value !in MIN_THRESHOLD_MS.toLong()..MAX_THRESHOLD_MS.toLong() ||
                    serverUrls.none { key.startsWith("$it:") }
            }
        ) return false
        return preferences.favoritesByServer.all { (url, ids) ->
            url in serverUrls && ids.size <= MAX_MONITOR_PREFERENCES && ids.all { it > 0 }
        }
    }

    private fun ByteArray.base64(): String = Base64.getEncoder().encodeToString(this)
    private fun String.decodeBase64(): ByteArray? = runCatching { Base64.getDecoder().decode(this) }.getOrNull()

    @Serializable
    private data class EncryptedBackup(
        val version: Int = FORMAT_VERSION,
        val kdf: String = KDF,
        val iterations: Int,
        val salt: String,
        val nonce: String,
        val ciphertext: String,
    )

    @Serializable
    private data class PortableBackup(
        val version: Int = PAYLOAD_VERSION,
        val connections: List<PortableConnection>,
        val preferences: PortablePreferences = PortablePreferences(),
    )

    @Serializable
    private data class PortableConnection(
        val url: String,
        val username: String,
        val insecure: Boolean,
        val alias: String?,
        val headers: List<RequestHeader>,
        val sessionToken: String? = null,
    )

    const val MIN_PASSWORD_LENGTH = 8
    private const val FORMAT_VERSION = 1
    private const val PAYLOAD_VERSION = 1
    private const val ITERATIONS = 210_000
    private const val MIN_ITERATIONS = 100_000
    private const val MAX_ITERATIONS = 1_000_000
    private const val SALT_BYTES = 16
    private const val NONCE_BYTES = 12
    private const val TAG_BITS = 128
    private const val KEY_BITS = 256
    private const val MAX_CONNECTIONS = 100
    private const val MAX_HEADERS = 10
    private const val MAX_MONITOR_PREFERENCES = 10_000
    private const val MIN_THRESHOLD_MS = 100
    private const val MAX_THRESHOLD_MS = 300_000
    private const val MAX_DOCUMENT_CHARS = 1_000_000
    private const val KDF = "PBKDF2WithHmacSHA256"
    private const val CIPHER = "AES/GCM/NoPadding"
    private val AAD = "URSA_CONNECTION_BACKUP_V1".encodeToByteArray()
}
