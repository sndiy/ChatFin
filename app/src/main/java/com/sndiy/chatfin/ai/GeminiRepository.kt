// app/src/main/java/com/sndiy/chatfin/ai/GeminiRepository.kt

package com.sndiy.chatfin.ai

import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.BlockThreshold
import com.google.ai.client.generativeai.type.HarmCategory
import com.google.ai.client.generativeai.type.SafetySetting
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

    private fun getModel(systemPrompt: String): GenerativeModel {
        val apiKey = getApiKey()
        if (apiKey.isBlank()) throw IllegalStateException(
            "API Key belum diset. Buka Setelan → API Key Gemini."
        )

        // Safety settings longgar agar percakapan keuangan tidak diblok
        val safetySettings = listOf(
            SafetySetting(HarmCategory.HARASSMENT, BlockThreshold.NONE),
            SafetySetting(HarmCategory.HATE_SPEECH, BlockThreshold.NONE),
            SafetySetting(HarmCategory.SEXUALLY_EXPLICIT, BlockThreshold.MEDIUM_AND_ABOVE),
            SafetySetting(HarmCategory.DANGEROUS_CONTENT, BlockThreshold.MEDIUM_AND_ABOVE),
        )

        return GenerativeModel(
            modelName        = "gemini-1.5-flash",
            apiKey           = apiKey,
            generationConfig = generationConfig {
                temperature     = 0.9f
                maxOutputTokens = 1024
            },
            safetySettings   = safetySettings,
            // Pakai lambda DSL "content {}" — cara yang benar di 0.9.0
            systemInstruction = content("system") { text(systemPrompt) }
        )
    }

    suspend fun sendMessage(
        userMessage: String,
        chatHistory: List<Pair<String, String>>,
        systemPrompt: String
    ): Result<ParsedMessage> {
        return try {
            val model = getModel(systemPrompt)

            // Bangun history dengan DSL content {} yang benar
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
            android.util.Log.e("GeminiRepo", "Error ${e::class.simpleName}: ${e.message}", e)
            val msg = when {
                e.message?.contains("API_KEY", ignoreCase = true) == true ||
                        e.message?.contains("API key", ignoreCase = true) == true ->
                    "API Key tidak valid."
                e.message?.contains("RESOURCE_EXHAUSTED") == true ->
                    "Kuota Gemini habis. Coba lagi besok."
                e.message?.contains("NOT_FOUND") == true ->
                    "Model tidak tersedia di API key ini."
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