package com.dionysus.tv.data.debrid.realdebrid

import com.dionysus.tv.data.settings.SettingsRepository
import kotlinx.coroutines.runBlocking
import okhttp3.Authenticator
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import javax.inject.Inject
import javax.inject.Singleton

/** Attaches the current Real-Debrid bearer token to every request. */
@Singleton
class RealDebridAuthInterceptor @Inject constructor(
    private val settings: SettingsRepository,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val token = runBlocking { settings.currentRealDebridToken() }
        val request = if (token != null) {
            chain.request().newBuilder()
                .header("Authorization", "Bearer $token")
                .build()
        } else {
            chain.request()
        }
        return chain.proceed(request)
    }
}

/** On a 401, refreshes the token once and retries the request. */
@Singleton
class RealDebridAuthenticator @Inject constructor(
    private val auth: RealDebridAuth,
) : Authenticator {
    override fun authenticate(route: Route?, response: Response): Request? {
        if (responseCount(response) >= 2) return null // already retried; give up
        val newToken = runBlocking { auth.refresh() } ?: return null
        return response.request.newBuilder()
            .header("Authorization", "Bearer $newToken")
            .build()
    }

    private fun responseCount(response: Response): Int {
        var count = 1
        var prior = response.priorResponse
        while (prior != null) {
            count++
            prior = prior.priorResponse
        }
        return count
    }
}
