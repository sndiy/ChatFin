package com.sndiy.chatfin.ai

import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import com.google.ai.client.generativeai.type.generationConfig
import com.sndiy.chatfin.BuildConfig
import com.sndiy.chatfin.core.data.security.SecureStorage
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GeminiClient @Inject constructor(
    private val secureStorage: SecureStorage
) {
    companion object {
        private const val MODEL_PRIMARY  = "gemini-2.5-flash-preview-04-17"
        private const val MODEL_FALLBACK = "gemini-2.5-flash-lite-preview-06-17"
    }

    fun resolveApiKey(): String {
        val stored = secureStorage.geminiApiKey
        if (!stored.isNullOrBlank()) return stored
        return try { BuildConfig.GEMINI_API_KEY } catch (_: Exception) { "" }
    }

    private fun buildModel(modelName: String, systemPrompt: String): GenerativeModel {
        val apiKey = resolveApiKey()
        require(apiKey.isNotBlank()) { "API Key Gemini belum diset. Buka Setelan → API Key." }
        return GenerativeModel(
            modelName         = modelName,
            apiKey            = apiKey,
            generationConfig  = generationConfig {
                temperature     = 0.85f
                maxOutputTokens = 1024
            },
            systemInstruction = content("system") { text(systemPrompt) }
        )
    }

    private fun isQuotaError(e: Exception): Boolean {
        val msg = e.message ?: return false
        return msg.contains("RESOURCE_EXHAUSTED") ||
                msg.contains("quota", ignoreCase = true) ||
                msg.contains("rate", ignoreCase = true) ||
                msg.contains("429")
    }

    suspend fun sendMessage(
        userMessage: String,
        history: List<Pair<String, String>>,
        systemPrompt: String
    ): String {
        val builtHistory = history.map { (role, text) ->
            content(role = role) { text(text) }
        }

        // Coba model primary dulu
        try {
            android.util.Log.d("GeminiClient", "Mencoba $MODEL_PRIMARY")
            val model    = buildModel(MODEL_PRIMARY, systemPrompt)
            val chat     = model.startChat(history = builtHistory)
            val response = chat.sendMessage(userMessage)
            return response.text ?: ""
        } catch (e: Exception) {
            if (!isQuotaError(e)) throw e
            android.util.Log.w("GeminiClient", "$MODEL_PRIMARY quota habis, fallback ke $MODEL_FALLBACK")
        }

        // Fallback ke model lite
        try {
            android.util.Log.d("GeminiClient", "Mencoba $MODEL_FALLBACK")
            val model    = buildModel(MODEL_FALLBACK, systemPrompt)
            val chat     = model.startChat(history = builtHistory)
            val response = chat.sendMessage(userMessage)
            return response.text ?: ""
        } catch (e: Exception) {
            if (!isQuotaError(e)) throw e
            android.util.Log.e("GeminiClient", "$MODEL_FALLBACK juga quota habis")
        }

        // Kedua model limit
        throw QuotaExhaustedException()
    }
}

class QuotaExhaustedException : Exception(
    "Hmph. Sepertinya aku sedang terlalu sibuk sekarang. Coba lagi nanti ya."
)