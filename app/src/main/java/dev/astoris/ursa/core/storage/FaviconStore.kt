package dev.astoris.ursa.core.storage

import android.content.Context
import android.util.Base64
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Encrypted, bounded on-disk favicon cache. Favicons can reveal monitored hosts. */
class FaviconStore(context: Context) {

    private val crypto = Crypto(context.applicationContext)
    private val directory = File(context.noBackupFilesDir, "favicons")

    suspend fun load(origin: String): ByteArray? = withContext(Dispatchers.IO) {
        val file = fileFor(origin)
        val encoded = file.takeIf(File::isFile)?.readText() ?: return@withContext null
        crypto.decrypt(encoded)?.let { decrypted ->
            runCatching { Base64.decode(decrypted, Base64.NO_WRAP) }.getOrNull()
        }
    }

    suspend fun save(origin: String, bytes: ByteArray) = withContext(Dispatchers.IO) {
        directory.mkdirs()
        val file = fileFor(origin)
        val encrypted = crypto.encrypt(Base64.encodeToString(bytes, Base64.NO_WRAP))
        val temp = File(directory, "${file.name}.tmp")
        temp.writeText(encrypted)
        if (!temp.renameTo(file)) temp.delete()
        directory.listFiles()?.sortedByDescending(File::lastModified)?.drop(32)?.forEach(File::delete)
    }

    private fun fileFor(origin: String): File = File(directory, "${origin.sha256()}.cache")
}

private fun String.sha256(): String = MessageDigest.getInstance("SHA-256")
    .digest(toByteArray(Charsets.UTF_8))
    .joinToString("") { byte -> "%02x".format(byte) }
