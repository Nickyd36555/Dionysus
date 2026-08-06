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
        // The trust manager is strict by default and only relaxes the cert-chain
        // check when the user opts into "Allow insecure connections" (Settings).
        val trustManager = LenientTrustManager(settings)
        return OkHttpClient.Builder()
            .addInterceptor(logging)
            .sslSocketFactory(trustManager.socketFactory(), trustManager)
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .callTimeout(60, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }
}
