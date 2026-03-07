// app/src/main/java/com/sndiy/chatfin/ai/GeminiClient.kt

package com.sndiy.chatfin.ai

import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import com.google.ai.client.generativeai.type.generationConfig
import com.sndiy.chatfin.BuildConfig
import com.sndiy.chatfin.core.data.security.SecureStorage
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Low-level wrapper untuk Gemini API.
 * GeminiRepository menggunakan class ini untuk melakukan panggilan API.
 */
@Singleton
class GeminiClient @Inject constructor(
    private val secureStorage: SecureStorage
) {
    fun resolveApiKey(): String {
        val stored = secureStorage.geminiApiKey
        if (!stored.isNullOrBlank()) return stored
        return try { BuildConfig.GEMINI_API_KEY } catch (_: Exception) { "" }
    }

    fun buildModel(systemPrompt: String): GenerativeModel {
        val apiKey = resolveApiKey()
        require(apiKey.isNotBlank()) {
            "API Key Gemini belum diset. Buka Setelan → API Key."
        }
        return GenerativeModel(
            modelName        = "gemini-2.0-flash",
            apiKey           = apiKey,
            generationConfig = generationConfig {
                temperature     = 0.85f
                maxOutputTokens = 1024
            },
            systemInstruction = content("system") { text(systemPrompt) }
        )
    }

    suspend fun sendMessage(
        userMessage: String,
        history: List<Pair<String, String>>,
        systemPrompt: String
    ): String {
        val model = buildModel(systemPrompt)
        val builtHistory = history.map { (role, text) ->
            content(role = role) { text(text) }
        }
        val chat     = model.startChat(history = builtHistory)
        val response = chat.sendMessage(userMessage)
        return response.text ?: ""
    }
}
