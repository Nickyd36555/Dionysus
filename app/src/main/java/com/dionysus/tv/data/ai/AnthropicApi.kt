package com.dionysus.tv.data.ai

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.POST

/**
 * Minimal Anthropic (Claude) Messages API client — used for AI-powered "Similar
 * To" and "Because you watched" recommendations. Auth headers (x-api-key,
 * anthropic-version) are injected by [AnthropicAuthInterceptor] on the
 * @AnthropicClient OkHttp client, so the key never lives in this interface.
 */
interface AnthropicApi {

    @POST("v1/messages")
    suspend fun messages(@Body request: AnthropicRequest): AnthropicResponse

    companion object {
        const val BASE_URL = "https://api.anthropic.com/"
        const val VERSION = "2023-06-01"
        /** Fast, low-cost model — recommendations run per-search, so speed/price win. */
        const val DEFAULT_MODEL = "claude-haiku-4-5"
    }
}

@Serializable
data class AnthropicRequest(
    val model: String,
    @SerialName("max_tokens") val maxTokens: Int,
    val system: String? = null,
    val messages: List<AnthropicMessage>,
)

@Serializable
data class AnthropicMessage(
    val role: String,
    val content: String,
)

@Serializable
data class AnthropicResponse(
    val content: List<AnthropicContentBlock> = emptyList(),
    @SerialName("stop_reason") val stopReason: String? = null,
) {
    /** The concatenated text of all text blocks in the response. */
    fun text(): String = content.filter { it.type == "text" }.joinToString("") { it.text }
}

@Serializable
data class AnthropicContentBlock(
    val type: String = "text",
    val text: String = "",
)
