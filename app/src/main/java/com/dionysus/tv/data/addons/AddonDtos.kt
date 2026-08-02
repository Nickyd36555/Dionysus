package com.dionysus.tv.data.addons

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * Stremio addon protocol DTOs. An addon is described by its manifest and serves
 * some subset of resources (catalog / meta / stream / subtitles) over HTTP:
 *   {transportUrl}/{resource}/{type}/{id}.json
 */
@Serializable
data class AddonManifest(
    val id: String,
    val name: String,
    val version: String = "",
    val description: String = "",
    /** Entries are either a plain string ("catalog") or an object with details. */
    val resources: List<JsonElement> = emptyList(),
    val types: List<String> = emptyList(),
    val catalogs: List<AddonCatalogDef> = emptyList(),
    val idPrefixes: List<String> = emptyList(),
    val logo: String? = null,
)

@Serializable
data class AddonCatalogDef(
    val type: String,
    val id: String,
    val name: String = "",
    val extra: List<AddonExtraDef> = emptyList(),
)

@Serializable
data class AddonExtraDef(
    val name: String,
    val isRequired: Boolean = false,
    val options: List<String> = emptyList(),
)

@Serializable
data class AddonCatalogResponse(val metas: List<AddonMeta> = emptyList())

@Serializable
data class AddonMetaResponse(val meta: AddonMeta? = null)

@Serializable
data class AddonMeta(
    val id: String,
    val type: String = "",
    val name: String = "",
    val poster: String? = null,
    val background: String? = null,
    val description: String = "",
    @SerialName("releaseInfo") val releaseInfo: String? = null,
    @SerialName("imdbRating") val imdbRating: String? = null,
    val genres: List<String> = emptyList(),
    val runtime: String? = null,
    /** Present on series meta: the episode list. */
    val videos: List<AddonVideo> = emptyList(),
)

@Serializable
data class AddonVideo(
    val id: String = "",
    val season: Int? = null,
    val episode: Int? = null,
    val name: String = "",
    val overview: String = "",
    val thumbnail: String? = null,
    val released: String? = null,
)

@Serializable
data class AddonStreamResponse(val streams: List<AddonStream> = emptyList())

@Serializable
data class AddonStream(
    val name: String = "",
    val title: String = "",
    val description: String = "",
    val url: String? = null,
    val infoHash: String? = null,
    val fileIdx: Int? = null,
    val behaviorHints: AddonStreamHints? = null,
)

@Serializable
data class AddonStreamHints(
    val bingeGroup: String? = null,
    val filename: String? = null,
    val videoSize: Long? = null,
)
