package dev.astoris.ursa.core.network

import android.annotation.SuppressLint
import okhttp3.OkHttpClient
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager

/**
 * Builds an OkHttpClient for an explicitly trusted self-signed endpoint.
 *
 * The first valid certificate seen by the client is pinned for the client's
 * lifetime. Reconnects must present the same certificate, and OkHttp's normal
 * hostname verification remains enabled. A new client is created only when the
 * user starts a new connection attempt.
 */
object TlsTrust {

    fun sessionPinnedClient(): OkHttpClient {
        val trustManager = SessionPinnedTrustManager()
        val sslContext = SSLContext.getInstance("TLS").apply {
            init(null, arrayOf<X509TrustManager>(trustManager), SecureRandom())
        }
        return OkHttpClient.Builder()
            .sslSocketFactory(sslContext.socketFactory, trustManager)
            .build()
    }

    @SuppressLint("CustomX509TrustManager")
    private class SessionPinnedTrustManager : X509TrustManager {
        private val pin = SessionCertificatePin()

        override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {
            throw CertificateException("Client certificates are not accepted")
        }

        override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
            if (authType.isNullOrBlank()) throw CertificateException("Missing TLS authentication type")
            val leaf = chain?.firstOrNull() ?: throw CertificateException("Server sent no certificate")
            leaf.checkValidity()
            val fingerprint = MessageDigest.getInstance("SHA-256").digest(leaf.encoded)
            pin.verifyOrPin(fingerprint)
        }

        override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
    }
}

internal class SessionCertificatePin {
    private var expected: ByteArray? = null

    @Synchronized
    fun verifyOrPin(fingerprint: ByteArray) {
        val current = expected
        if (current == null) {
            expected = fingerprint.copyOf()
        } else if (!MessageDigest.isEqual(current, fingerprint)) {
            throw CertificateException("Server certificate changed during this session")
        }
    }
}
