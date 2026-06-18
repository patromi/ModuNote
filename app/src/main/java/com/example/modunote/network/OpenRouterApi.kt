package com.example.modunote.network

import com.google.gson.annotations.SerializedName
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import java.util.concurrent.TimeUnit

data class ChatMessage(
    val role: String,
    val content: String
)

data class ChatRequest(
    val model: String,
    val messages: List<ChatMessage>
)

data class ChatChoice(
    val message: ChatMessage,
    @SerializedName("finish_reason") val finishReason: String?
)

data class ChatResponse(
    val choices: List<ChatChoice>
)

data class OpenRouterError(
    val error: ErrorBody?
) {
    data class ErrorBody(val message: String?, val code: Int?)
}

interface OpenRouterApi {
    @POST("v1/chat/completions")
    suspend fun complete(
        @Header("Authorization") authorization: String,
        @Header("HTTP-Referer") referer: String = "https://modunote.app",
        @Header("X-Title") xTitle: String = "ModuNote",
        @Body request: ChatRequest
    ): ChatResponse
}

object OpenRouterClient {
    private val http = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    val api: OpenRouterApi = Retrofit.Builder()
        .baseUrl("https://openrouter.ai/api/")
        .client(http)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(OpenRouterApi::class.java)

    val models = listOf(
        "openai/gpt-4o-mini" to "GPT-4o Mini",
        "openai/gpt-4o" to "GPT-4o",
        "anthropic/claude-3.5-haiku" to "Claude 3.5 Haiku",
        "anthropic/claude-sonnet-4-5" to "Claude Sonnet 4.5",
        "google/gemini-flash-1.5" to "Gemini Flash 1.5",
        "meta-llama/llama-3.1-8b-instruct:free" to "Llama 3.1 8B (Free)"
    )
}
