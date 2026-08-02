package com.dionysus.tv.data.addons

import retrofit2.http.GET
import retrofit2.http.Url

/**
 * Generic Stremio addon transport. Every call takes an absolute [Url] built
 * from the addon's transport (base) URL, so one client serves any addon.
 */
interface AddonApi {
    @GET
    suspend fun manifest(@Url url: String): AddonManifest

    @GET
    suspend fun catalog(@Url url: String): AddonCatalogResponse

    @GET
    suspend fun meta(@Url url: String): AddonMetaResponse

    @GET
    suspend fun streams(@Url url: String): AddonStreamResponse

    companion object {
        /** Placeholder base; every request overrides it with an absolute @Url. */
        const val BASE_URL = "https://addons.invalid/"
        /** Default meta provider for Stremio ids (IMDb-based). */
        const val CINEMETA_BASE = "https://v3-cinemeta.strem.io/"
    }
}
