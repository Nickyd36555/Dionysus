package com.dionysus.tv.data.ai

import com.dionysus.tv.core.model.DataResult
import com.dionysus.tv.core.model.MediaItem
import com.dionysus.tv.core.model.MediaType
import com.dionysus.tv.data.metadata.MetadataRepository
import com.dionysus.tv.data.settings.SettingsRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * AI-powered recommendations via Claude. Asks the model for titles similar to a
 * query (or based on watch history), then resolves each title to a real
 * [MediaItem] — with poster art — through the existing TMDB metadata search so
 * the results render as normal cards and open detail/streams.
 */
@Singleton
class AiRecommendationRepository @Inject constructor(
    private val api: AnthropicApi,
    private val metadata: MetadataRepository,
    private val settings: SettingsRepository,
    private val json: Json,
) {

    /** Movies/shows similar to a free-text title, e.g. "The Town" → The Departed. */
    suspend fun similarTo(query: String): DataResult<List<MediaItem>> {
        val q = query.trim()
        if (q.isBlank()) return DataResult.Success(emptyList())
        val prompt = "Recommend movies and TV shows similar in tone, genre, and " +
            "style to \"$q\". Return up to 15 recommendations. Do not include \"$q\" itself."
        return recommend(prompt, exclude = setOf(q.lowercase()))
    }

    /** Recommendations drawn from what the viewer has already watched. */
    suspend fun becauseYouWatched(watched: List<String>): DataResult<List<MediaItem>> {
        val titles = watched.map { it.trim() }.filter { it.isNotBlank() }.distinct().take(20)
        if (titles.isEmpty()) return DataResult.Success(emptyList())
        val prompt = "A viewer has watched these titles:\n" +
            titles.joinToString("\n") { "- $it" } +
            "\nRecommend up to 15 other movies and TV shows they would enjoy. " +
            "Do not include anything already in the list above."
        return recommend(prompt, exclude = titles.map { it.lowercase() }.toSet())
    }

    private suspend fun recommend(prompt: String, exclude: Set<String>): DataResult<List<MediaItem>> {
        if (settings.currentAnthropicApiKey().isNullOrBlank()) {
            return DataResult.Error("Add an Anthropic API key in Settings → AI to use AI recommendations.")
        }
        val suggestions = DataResult.catching {
            val resp = api.messages(
                AnthropicRequest(
                    model = AnthropicApi.DEFAULT_MODEL,
                    maxTokens = 1024,
                    system = SYSTEM_PROMPT,
                    messages = listOf(AnthropicMessage(role = "user", content = prompt)),
                ),
            )
            parseSuggestions(resp.text())
        }
        val list = when (suggestions) {
            is DataResult.Success -> suggestions.data
            is DataResult.Error -> return DataResult.Error("AI recommendation failed: ${suggestions.message}")
        }
        // Resolve each suggested title to a real MediaItem (with poster) via TMDB.
        val resolved = coroutineScope {
            list.filter { it.title.lowercase() !in exclude }
                .map { s -> async { resolve(s) } }
                .awaitAll()
        }
        return DataResult.Success(resolved.filterNotNull().distinctBy { it.id })
    }

    /** Pick the TMDB result that best matches the AI's title/year/type. */
    private suspend fun resolve(s: Suggestion): MediaItem? {
        val hits = metadata.search(s.title).getOrNull().orEmpty()
        if (hits.isEmpty()) return null
        val wantType = if (s.type.equals("tv", ignoreCase = true)) MediaType.TV_SHOW else MediaType.MOVIE
        return hits.firstOrNull { it.type == wantType && (s.year == null || it.year == s.year) }
            ?: hits.firstOrNull { it.type == wantType }
            ?: hits.firstOrNull { s.year != null && it.year == s.year }
            ?: hits.first()
    }

    private fun parseSuggestions(text: String): List<Suggestion> {
        val start = text.indexOf('[')
        val end = text.lastIndexOf(']')
        if (start < 0 || end <= start) return emptyList()
        val arr = text.substring(start, end + 1)
        return runCatching { json.decodeFromString<List<Suggestion>>(arr) }.getOrDefault(emptyList())
    }

    @Serializable
    private data class Suggestion(
        val title: String = "",
        val year: Int? = null,
        @SerialName("type") val type: String = "movie",
    )

    private companion object {
        const val SYSTEM_PROMPT =
            "You are a film and TV recommendation engine. Respond with ONLY a JSON array — " +
                "no prose, no markdown, no code fences. Each element is an object: " +
                "{\"title\": string, \"year\": number or null, \"type\": \"movie\" or \"tv\"}. " +
                "Use the exact commonly-known title so it can be looked up. Order best matches first."
    }
}
