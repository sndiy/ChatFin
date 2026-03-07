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
        private const val MODEL_PRIMARY  = "gemini-2.5-flash"
        private const val MODEL_FALLBACK = "gemini-2.5-flash-lite"
    }

    // Track model aktif saat ini — bisa di-rotate dari luar
    private var currentModelIndex = 0
    private val models = listOf(MODEL_PRIMARY, MODEL_FALLBACK)

    val currentModelName get() = models[currentModelIndex]

    fun rotateModel() {
        currentModelIndex = (currentModelIndex + 1) % models.size
        android.util.Log.d("GeminiClient", "Rotate ke model: ${models[currentModelIndex]}")
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

    fun isQuotaError(e: Exception): Boolean {
        val msg = e.message ?: return false
        return msg.contains("RESOURCE_EXHAUSTED") ||
                msg.contains("quota", ignoreCase = true) ||
                msg.contains("rate", ignoreCase = true) ||
                msg.contains("429")
    }

    // Kirim dengan model yang sedang aktif — tidak auto-fallback lagi
    suspend fun sendMessage(
        userMessage: String,
        history: List<Pair<String, String>>,
        systemPrompt: String
    ): String {
        val modelName = models[currentModelIndex]
        android.util.Log.d("GeminiClient", "Kirim dengan model: $modelName")

        val builtHistory = history.map { (role, text) ->
            content(role = role) { text(text) }
        }

        val model    = buildModel(modelName, systemPrompt)
        val chat     = model.startChat(history = builtHistory)
        val response = chat.sendMessage(userMessage)
        return response.text ?: ""
    }
}

class QuotaExhaustedException(modelName: String = "") : Exception(
    "Model $modelName sedang limit."
)