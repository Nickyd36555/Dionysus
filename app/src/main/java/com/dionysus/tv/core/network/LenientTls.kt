package com.dionysus.tv.core.network

import com.dionysus.tv.data.settings.SettingsRepository
import kotlinx.coroutines.runBlocking
import java.security.KeyStore
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

/**
 * Trust manager that normally delegates to the platform default (strict), but
 * skips certificate-chain validation when the user has enabled "Allow insecure
 * connections" in Settings. This targets the "chain validation failed" error
 * that appears on devices with a wrong clock or providers with an incomplete
 * certificate chain — without touching hostname verification.
 */
class LenientTrustManager(
    private val settings: SettingsRepository,
) : X509TrustManager {

    private val default: X509TrustManager = run {
        val tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        tmf.init(null as KeyStore?)
        tmf.trustManagers.first { it is X509TrustManager } as X509TrustManager
    }

    private fun insecure(): Boolean =
        runCatching { runBlocking { settings.currentAllowInsecureTls() } }.getOrDefault(false)

    override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
        if (insecure()) return
        default.checkServerTrusted(chain, authType)
    }

    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {
        default.checkClientTrusted(chain, authType)
    }

    override fun getAcceptedIssuers(): Array<X509Certificate> = default.acceptedIssuers

    /** An SSL socket factory backed by this trust manager. */
    fun socketFactory(): SSLSocketFactory =
        SSLContext.getInstance("TLS").apply { init(null, arrayOf(this@LenientTrustManager), SecureRandom()) }
            .socketFactory
}
