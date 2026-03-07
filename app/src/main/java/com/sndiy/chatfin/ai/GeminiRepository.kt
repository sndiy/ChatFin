package com.sndiy.chatfin.ai

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GeminiRepository @Inject constructor(
    private val client: GeminiClient,
    private val parser: ChatOptionsParser
) {
    suspend fun sendMessage(
        userMessage: String,
        chatHistory: List<Pair<String, String>>,
        systemPrompt: String
    ): Result<ParsedMessage> {
        return try {
            val rawText = client.sendMessage(userMessage, chatHistory, systemPrompt)
            android.util.Log.d("GeminiRepo", "OK: ${rawText.take(200)}")
            Result.success(parser.parse(rawText))
        } catch (e: QuotaExhaustedException) {
            android.util.Log.w("GeminiRepo", "Semua model quota habis")
            Result.failure(e)
        } catch (e: IllegalArgumentException) {
            android.util.Log.e("GeminiRepo", "Config: ${e.message}")
            Result.failure(e)
        } catch (e: Exception) {
            android.util.Log.e("GeminiRepo", "Error: ${e::class.simpleName}: ${e.message}", e)
            val msg = when {
                e.message?.contains("API_KEY", ignoreCase = true) == true ||
                        e.message?.contains("API key", ignoreCase = true) == true ->
                    "API Key tidak valid. Periksa di Setelan."
                e.message?.contains("NOT_FOUND") == true ->
                    "Model tidak tersedia."
                e.message?.contains("PERMISSION_DENIED") == true ->
                    "API Key tidak punya akses model ini."
                e.message?.contains("network", ignoreCase = true) == true ||
                        e.message?.contains("Unable to resolve") == true ->
                    "Tidak ada koneksi internet."
                else -> "Terjadi kesalahan: ${e.message}"
            }
            Result.failure(Exception(msg))
        }
    }
}