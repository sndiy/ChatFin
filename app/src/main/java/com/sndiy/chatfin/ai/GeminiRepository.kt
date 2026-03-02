// app/src/main/java/com/sndiy/chatfin/ai/GeminiRepository.kt

package com.sndiy.chatfin.ai

import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import com.google.ai.client.generativeai.type.generationConfig
import com.sndiy.chatfin.BuildConfig
import com.sndiy.chatfin.core.data.security.SecureStorage
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GeminiRepository @Inject constructor(
    private val secureStorage: SecureStorage,
    private val parser: ChatOptionsParser
) {

    private fun getApiKey(): String {
        val stored = secureStorage.geminiApiKey
        if (!stored.isNullOrBlank()) return stored
        return try { BuildConfig.GEMINI_API_KEY } catch (_: Exception) { "" }
    }

    // Buat model fresh setiap call — tidak di-inject Hilt
    private fun getModel(systemPrompt: String): GenerativeModel {
        val apiKey = getApiKey()
        if (apiKey.isBlank()) throw IllegalStateException(
            "API Key belum diset. Buka Setelan → API Key Gemini."
        )
        return GenerativeModel(
            modelName         = "gemini-2.0-flash",
            apiKey            = apiKey,
            generationConfig  = generationConfig {
                temperature     = 0.9f
                maxOutputTokens = 1024
            },
            systemInstruction = content("system") { text(systemPrompt) }
        )
    }

    suspend fun sendMessage(
        userMessage: String,
        chatHistory: List<Pair<String, String>>,
        systemPrompt: String
    ): Result<ParsedMessage> {
        return try {
            val model   = getModel(systemPrompt)
            val history = chatHistory.map { (role, text) ->
                content(role = role) { text(text) }
            }
            val chat     = model.startChat(history = history)
            val response = chat.sendMessage(userMessage)
            val rawText  = response.text ?: ""

            android.util.Log.d("GeminiRepo", "OK: ${rawText.take(200)}")
            Result.success(parser.parse(rawText))

        } catch (e: IllegalStateException) {
            android.util.Log.e("GeminiRepo", "IllegalState: ${e.message}")
            Result.failure(e)
        } catch (e: Exception) {
            android.util.Log.e("GeminiRepo", "Error: ${e::class.simpleName}: ${e.message}", e)
            val msg = when {
                e.message?.contains("API_KEY", ignoreCase = true) == true ||
                        e.message?.contains("API key", ignoreCase = true) == true ->
                    "API Key tidak valid."
                e.message?.contains("RESOURCE_EXHAUSTED") == true ->
                    "Kuota Gemini habis. Coba lagi besok."
                e.message?.contains("NOT_FOUND") == true ->
                    "Model tidak tersedia."
                e.message?.contains("PERMISSION_DENIED") == true ->
                    "API Key tidak punya akses."
                e.message?.contains("network") == true ||
                        e.message?.contains("Unable to resolve") == true ->
                    "Tidak ada koneksi internet."
                else -> "${e::class.simpleName}: ${e.message}"
            }
            Result.failure(Exception(msg))
        }
    }
}