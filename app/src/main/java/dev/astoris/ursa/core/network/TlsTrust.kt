package dev.astoris.ursa.core.network

import okhttp3.OkHttpClient
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager

/**
 * Builds an OkHttpClient that accepts ANY TLS certificate and hostname.
 *
 * This is a deliberate security downgrade. It is used ONLY for a connection the
 * user has explicitly opted into "trust self-signed" — the common case for a
 * homelab Uptime Kuma instance behind a self-signed or internal-CA cert. Never
 * the default. Certificate pinning would be the stronger upgrade if needed.
 */
object TlsTrust {

    fun insecureClient(): OkHttpClient {
        val trustManager = object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
            override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
        }
        val sslContext = SSLContext.getInstance("TLS").apply {
            init(null, arrayOf<X509TrustManager>(trustManager), SecureRandom())
        }
        return OkHttpClient.Builder()
            .sslSocketFactory(sslContext.socketFactory, trustManager)
            .hostnameVerifier { _, _ -> true }
            .build()
    }
}
