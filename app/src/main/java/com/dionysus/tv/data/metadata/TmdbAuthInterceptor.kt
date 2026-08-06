package com.dionysus.tv.data.metadata

import com.dionysus.tv.data.settings.SettingsRepository
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Makes TMDB auth work no matter which credential the user pastes.
 *
 * TMDB offers two keys and their site pushes the wrong one for this app:
 *  - a **v3 API key** (short, ~32 hex chars) sent as `?api_key=…`, and
 *  - a **v4 "API Read Access Token"** (a long `eyJ…` JWT) sent as
 *    `Authorization: Bearer …`.
 *
 * The API methods put whatever key is stored into the `api_key` query param.
 * That's correct for v3, but a v4 token there is rejected with 401 on every
 * call — which looks like "no TMDB key" even though one is configured. This
 * interceptor detects a v4 token and moves it to a Bearer header (dropping the
 * bad query param), so either key type just works. It also trims the stored key
 * so a stray space or newline from pasting doesn't break auth.
 */
@Singleton
class TmdbAuthInterceptor @Inject constructor(
    private val settings: SettingsRepository,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val key = runBlocking { settings.currentTmdbApiKey() }?.trim()
        val req = chain.request()
        if (key.isNullOrBlank()) return chain.proceed(req)

        val looksV4 = key.startsWith("eyJ") || (key.length > 45 && key.contains('.'))
        val builder = req.newBuilder()
        val url = if (looksV4) {
            // v4: authenticate via header, and strip the (wrong) api_key query param.
            builder.header("Authorization", "Bearer $key")
            req.url.newBuilder().removeAllQueryParameters("api_key").build()
        } else {
            // v3: ensure the trimmed key is the api_key param (methods already add it,
            // but re-setting guarantees the trimmed value is used).
            req.url.newBuilder().setQueryParameter("api_key", key).build()
        }
        return chain.proceed(builder.url(url).build())
    }
}
