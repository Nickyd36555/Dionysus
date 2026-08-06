package com.dionysus.tv.core.network

import com.dionysus.tv.BuildConfig
import com.dionysus.tv.data.settings.SettingsRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

/**
 * Provides the shared JSON parser and a base OkHttp client. Individual data
 * sources build their own Retrofit instances on top of these (they target
 * different base URLs and auth schemes), which keeps each integration isolated.
 */
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
        explicitNulls = false
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(settings: SettingsRepository): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BASIC
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        }
        val builder = OkHttpClient.Builder()
            .addInterceptor(logging)
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .callTimeout(60, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)

        // Default: the completely stock TLS stack (no custom factory) — no risk to
        // normal HTTPS. Only when the user turns on "Allow insecure connections"
        // (Settings → Connection) do we install a trust-all factory to get past
        // "chain validation failed". Read once at startup; toggling needs a restart.
        val allowInsecure = runCatching {
            kotlinx.coroutines.runBlocking { settings.currentAllowInsecureTls() }
        }.getOrDefault(false)
        if (allowInsecure) {
            applyInsecureTls(builder)
        }
        return builder.build()
    }

    /** Accept-all TLS (cert chain + hostname). Used only behind the opt-in toggle. */
    private fun applyInsecureTls(builder: OkHttpClient.Builder) {
        runCatching {
            val trustAll = object : javax.net.ssl.X509TrustManager {
                override fun checkClientTrusted(chain: Array<out java.security.cert.X509Certificate>?, authType: String?) {}
                override fun checkServerTrusted(chain: Array<out java.security.cert.X509Certificate>?, authType: String?) {}
                override fun getAcceptedIssuers(): Array<java.security.cert.X509Certificate> = arrayOf()
            }
            val ctx = javax.net.ssl.SSLContext.getInstance("TLS")
            ctx.init(null, arrayOf<javax.net.ssl.TrustManager>(trustAll), java.security.SecureRandom())
            builder.sslSocketFactory(ctx.socketFactory, trustAll)
            builder.hostnameVerifier { _, _ -> true }
        }
    }
}
