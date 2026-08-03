package com.dionysus.tv.data.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "dionysus_settings")

/**
 * Persists user configuration: debrid tokens, scraper API keys, the metadata
 * key, the preferred external player, and the customizable home-screen layout.
 *
 * Secrets live only in the app's private DataStore — never in source control.
 */
@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private object Keys {
        val REAL_DEBRID_TOKEN = stringPreferencesKey("real_debrid_token")
        val REAL_DEBRID_REFRESH = stringPreferencesKey("real_debrid_refresh")
        val REAL_DEBRID_CLIENT_ID = stringPreferencesKey("real_debrid_client_id")
        val REAL_DEBRID_CLIENT_SECRET = stringPreferencesKey("real_debrid_client_secret")
        val PREMIUMIZE_API_KEY = stringPreferencesKey("premiumize_api_key")
        val ORION_API_KEY = stringPreferencesKey("orion_api_key")
        val TMDB_API_KEY = stringPreferencesKey("tmdb_api_key")
        val OMDB_API_KEY = stringPreferencesKey("omdb_api_key")
        val TORRENTIO_BASE_URL = stringPreferencesKey("torrentio_base_url")
        val PREFERRED_PLAYER = stringPreferencesKey("preferred_player")
        val HOME_LAYOUT = stringPreferencesKey("home_layout_json")
        val FEATURED_SOURCE = stringPreferencesKey("featured_source")
        val DOWNLOAD_FOLDER_URI = stringPreferencesKey("download_folder_uri")
        val EPG_OFFSET_MINUTES = intPreferencesKey("epg_offset_minutes")
        val ENABLED_SCRAPERS = stringSetPreferencesKey("enabled_scrapers")
        val ONLY_CACHED = booleanPreferencesKey("only_cached")
    }

    val realDebridToken: Flow<String?> = get(Keys.REAL_DEBRID_TOKEN)
    val premiumizeApiKey: Flow<String?> = get(Keys.PREMIUMIZE_API_KEY)
    val orionApiKey: Flow<String?> = get(Keys.ORION_API_KEY)
    val tmdbApiKey: Flow<String?> = get(Keys.TMDB_API_KEY)

    /** OMDb key powers the extra movie/show info (cast, RT/Metacritic, awards). */
    val omdbApiKey: Flow<String> = get(Keys.OMDB_API_KEY).map { it ?: DEFAULT_OMDB_KEY }

    val torrentioBaseUrl: Flow<String> =
        get(Keys.TORRENTIO_BASE_URL).map { it ?: DEFAULT_TORRENTIO_URL }

    val preferredPlayerId: Flow<String> =
        get(Keys.PREFERRED_PLAYER).map { it ?: DEFAULT_PLAYER }

    val enabledScraperIds: Flow<Set<String>> =
        context.dataStore.data.map { it[Keys.ENABLED_SCRAPERS] ?: DEFAULT_SCRAPERS }

    val homeLayoutJson: Flow<String?> = get(Keys.HOME_LAYOUT)

    /** Which row kind feeds the big top carousel (default TRENDING). */
    val featuredSource: Flow<String> = get(Keys.FEATURED_SOURCE).map { it ?: DEFAULT_FEATURED }

    /** SAF tree URI of the user's chosen download folder, or null for app storage. */
    val downloadFolderUri: Flow<String?> = get(Keys.DOWNLOAD_FOLDER_URI)

    /** Manual EPG time shift (minutes) to correct a provider whose guide times are off. */
    val epgOffsetMinutes: Flow<Int> = context.dataStore.data.map { it[Keys.EPG_OFFSET_MINUTES] ?: 0 }

    /** When true, only sources already cached on a debrid service are shown. */
    val onlyCached: Flow<Boolean> =
        context.dataStore.data.map { it[Keys.ONLY_CACHED] ?: false }

    suspend fun setRealDebridToken(token: String?) = put(Keys.REAL_DEBRID_TOKEN, token)

    /** Store the full OAuth credential bundle obtained from the device flow. */
    suspend fun setRealDebridOAuth(
        accessToken: String,
        refreshToken: String,
        clientId: String,
        clientSecret: String,
    ) {
        context.dataStore.edit { prefs ->
            prefs[Keys.REAL_DEBRID_TOKEN] = accessToken
            prefs[Keys.REAL_DEBRID_REFRESH] = refreshToken
            prefs[Keys.REAL_DEBRID_CLIENT_ID] = clientId
            prefs[Keys.REAL_DEBRID_CLIENT_SECRET] = clientSecret
        }
    }

    suspend fun clearRealDebrid() {
        context.dataStore.edit { prefs ->
            prefs.remove(Keys.REAL_DEBRID_TOKEN)
            prefs.remove(Keys.REAL_DEBRID_REFRESH)
            prefs.remove(Keys.REAL_DEBRID_CLIENT_ID)
            prefs.remove(Keys.REAL_DEBRID_CLIENT_SECRET)
        }
    }

    suspend fun currentRealDebridRefresh(): String? = get(Keys.REAL_DEBRID_REFRESH).first()
    suspend fun currentRealDebridClientId(): String? = get(Keys.REAL_DEBRID_CLIENT_ID).first()
    suspend fun currentRealDebridClientSecret(): String? = get(Keys.REAL_DEBRID_CLIENT_SECRET).first()
    suspend fun setPremiumizeApiKey(key: String?) = put(Keys.PREMIUMIZE_API_KEY, key)
    suspend fun setOrionApiKey(key: String?) = put(Keys.ORION_API_KEY, key)
    suspend fun setTmdbApiKey(key: String?) = put(Keys.TMDB_API_KEY, key)
    suspend fun setOmdbApiKey(key: String?) = put(Keys.OMDB_API_KEY, key)
    suspend fun setTorrentioBaseUrl(url: String) = put(Keys.TORRENTIO_BASE_URL, url)
    suspend fun setPreferredPlayer(id: String) = put(Keys.PREFERRED_PLAYER, id)
    suspend fun setHomeLayoutJson(json: String) = put(Keys.HOME_LAYOUT, json)
    suspend fun setFeaturedSource(kind: String) = put(Keys.FEATURED_SOURCE, kind)
    suspend fun currentFeaturedSource(): String = featuredSource.first()
    suspend fun setDownloadFolderUri(uri: String?) = put(Keys.DOWNLOAD_FOLDER_URI, uri)
    suspend fun currentDownloadFolderUri(): String? = downloadFolderUri.first()
    suspend fun setEpgOffsetMinutes(minutes: Int) {
        context.dataStore.edit { it[Keys.EPG_OFFSET_MINUTES] = minutes }
    }
    suspend fun currentEpgOffsetMinutes(): Int = epgOffsetMinutes.first()

    suspend fun setOnlyCached(enabled: Boolean) {
        context.dataStore.edit { it[Keys.ONLY_CACHED] = enabled }
    }

    suspend fun currentOnlyCached(): Boolean = onlyCached.first()

    suspend fun setScraperEnabled(id: String, enabled: Boolean) {
        context.dataStore.edit { prefs ->
            val current = prefs[Keys.ENABLED_SCRAPERS]?.toMutableSet() ?: DEFAULT_SCRAPERS.toMutableSet()
            if (enabled) current.add(id) else current.remove(id)
            prefs[Keys.ENABLED_SCRAPERS] = current
        }
    }

    /** Convenience for one-shot reads inside data sources. */
    suspend fun currentRealDebridToken(): String? = realDebridToken.first()
    suspend fun currentPremiumizeApiKey(): String? = premiumizeApiKey.first()
    suspend fun currentOrionApiKey(): String? = orionApiKey.first()
    suspend fun currentTmdbApiKey(): String? = tmdbApiKey.first()
    suspend fun currentOmdbApiKey(): String = omdbApiKey.first()

    private fun get(key: androidx.datastore.preferences.core.Preferences.Key<String>): Flow<String?> =
        context.dataStore.data.map { it[key]?.takeIf(String::isNotBlank) }

    private suspend fun put(
        key: androidx.datastore.preferences.core.Preferences.Key<String>,
        value: String?,
    ) {
        context.dataStore.edit { prefs ->
            if (value.isNullOrBlank()) prefs.remove(key) else prefs[key] = value
        }
    }

    companion object {
        const val DEFAULT_TORRENTIO_URL = "https://torrentio.strem.fun/"
        const val DEFAULT_PLAYER = "internal"
        // A working default so extra info shows out of the box; overridable in Settings.
        const val DEFAULT_OMDB_KEY = "1cd5b3b3"
        const val DEFAULT_FEATURED = "TRENDING"
        val DEFAULT_SCRAPERS = setOf("torrentio", "stremio_addons")
    }
}
