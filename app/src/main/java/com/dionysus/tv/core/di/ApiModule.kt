package com.dionysus.tv.core.di

import com.dionysus.tv.data.debrid.premiumize.PremiumizeApi
import com.dionysus.tv.data.debrid.realdebrid.RealDebridApi
import com.dionysus.tv.data.debrid.realdebrid.RealDebridAuthApi
import com.dionysus.tv.data.debrid.realdebrid.RealDebridAuthInterceptor
import com.dionysus.tv.data.addons.AddonApi
import com.dionysus.tv.data.debrid.realdebrid.RealDebridAuthenticator
import com.dionysus.tv.data.metadata.TmdbApi
import com.dionysus.tv.data.scraper.orion.OrionApi
import com.dionysus.tv.data.scraper.torrentio.TorrentioApi
import com.dionysus.tv.data.update.GitHubApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import javax.inject.Singleton

/**
 * Builds one Retrofit instance per integration. They share the base OkHttp
 * client and JSON parser but target different base URLs; Real-Debrid gets a
 * dedicated client that injects and refreshes its bearer token.
 */
@Module
@InstallIn(SingletonComponent::class)
object ApiModule {

    private val JSON_MEDIA_TYPE = "application/json".toMediaType()

    private fun retrofit(baseUrl: String, client: OkHttpClient, json: Json): Retrofit =
        Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(json.asConverterFactory(JSON_MEDIA_TYPE))
            .build()

    @Provides
    @Singleton
    @RealDebridClient
    fun provideRealDebridClient(
        base: OkHttpClient,
        interceptor: RealDebridAuthInterceptor,
        authenticator: RealDebridAuthenticator,
    ): OkHttpClient = base.newBuilder()
        .addInterceptor(interceptor)
        .authenticator(authenticator)
        .build()

    @Provides
    @Singleton
    fun provideTmdbApi(client: OkHttpClient, json: Json): TmdbApi =
        retrofit(TmdbApi.BASE_URL, client, json).create(TmdbApi::class.java)

    @Provides
    @Singleton
    fun provideTorrentioApi(client: OkHttpClient, json: Json): TorrentioApi =
        retrofit("https://torrentio.strem.fun/", client, json).create(TorrentioApi::class.java)

    @Provides
    @Singleton
    fun provideOrionApi(client: OkHttpClient, json: Json): OrionApi =
        retrofit(OrionApi.BASE_URL, client, json).create(OrionApi::class.java)

    @Provides
    @Singleton
    fun providePremiumizeApi(client: OkHttpClient, json: Json): PremiumizeApi =
        retrofit(PremiumizeApi.BASE_URL, client, json).create(PremiumizeApi::class.java)

    @Provides
    @Singleton
    fun provideGitHubApi(client: OkHttpClient, json: Json): GitHubApi =
        retrofit(GitHubApi.BASE_URL, client, json).create(GitHubApi::class.java)

    @Provides
    @Singleton
    fun provideAddonApi(client: OkHttpClient, json: Json): AddonApi =
        retrofit(AddonApi.BASE_URL, client, json).create(AddonApi::class.java)

    @Provides
    @Singleton
    fun provideRealDebridAuthApi(client: OkHttpClient, json: Json): RealDebridAuthApi =
        retrofit(RealDebridAuthApi.BASE_URL, client, json).create(RealDebridAuthApi::class.java)

    @Provides
    @Singleton
    fun provideRealDebridApi(
        @RealDebridClient client: OkHttpClient,
        json: Json,
    ): RealDebridApi =
        retrofit(RealDebridApi.BASE_URL, client, json).create(RealDebridApi::class.java)
}
