package com.dionysus.tv.data.ai

import com.dionysus.tv.data.settings.SettingsRepository
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

/** Adds the user's Anthropic API key and the required API-version header. */
@Singleton
class AnthropicAuthInterceptor @Inject constructor(
    private val settings: SettingsRepository,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val key = runBlocking { settings.currentAnthropicApiKey() }
        val builder = chain.request().newBuilder()
            .header("anthropic-version", AnthropicApi.VERSION)
            .header("content-type", "application/json")
        if (!key.isNullOrBlank()) builder.header("x-api-key", key)
        return chain.proceed(builder.build())
    }
}
