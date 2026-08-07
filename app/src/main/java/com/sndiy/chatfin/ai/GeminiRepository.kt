package com.sndiy.chatfin.ai

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GeminiRepository @Inject constructor(
    private val client: GeminiClient,
    private val parser: ChatOptionsParser
) {
    private var quotaRetryCount = 0

    suspend fun sendMessage(
        userMessage: String,
        chatHistory: List<Pair<String, String>>,
        systemPrompt: String
    ): Result<ParsedMessage> {
        return try {
            val rawText = client.sendMessage(userMessage, chatHistory, systemPrompt)
            quotaRetryCount = 0
            // JANGAN mencetak isi rawText — respons AI berisi analisis keuangan
            // user (nominal, saldo, kategori pengeluaran). Cukup metadata.
            android.util.Log.d("GeminiRepo", "OK dengan ${client.currentModelName} (${rawText.length} char)")
            // Setelah sukses, kembalikan ke primary
            Result.success(parser.parse(rawText)).also {
                // Setelah sukses, pastikan kembali ke primary
                client.resetToPrimary()
            }
        } catch (e: Exception) {
            when {
                e is ApiKeyMissingException -> {
                    quotaRetryCount = 0
                    Result.failure(e)
                }
                (e as? GeminiApiException)?.finishReason != null -> {
                    quotaRetryCount = 0
                    val finishReason = (e as GeminiApiException).finishReason!!
                    // finishReason cuma nama status (MAX_TOKENS/SAFETY/dst), bukan isi respons —
                    // aman dicetak. Pola sama seperti ReceiptAiEnhancer: jangan lempar pesan
                    // mentah Google apa adanya ke user, itu bukan sesuatu yang bisa mereka tindak.
                    android.util.Log.w("GeminiRepo", "Generation berhenti sebelum selesai (finishReason=$finishReason)")
                    Result.failure(GenerationIncompleteException(finishReason))
                }
                client.isQuotaError(e) -> {
                    val failedModel = client.currentModelName
                    quotaRetryCount++

                    return if (quotaRetryCount >= 2) {
                        quotaRetryCount = 0
                        android.util.Log.e("GeminiRepo", "Semua model quota habis")
                        Result.failure(QuotaExhaustedException("semua"))
                    } else {
                        client.rotateModel()
                        android.util.Log.w("GeminiRepo", "$failedModel limit, rotate ke ${client.currentModelName}")
                        Result.failure(QuotaExhaustedException(failedModel))
                    }
                }
                else -> {
                    quotaRetryCount = 0
                    // Jangan cetak e.message — exception dari Google AI SDK kadang
                    // mengandung API key di pesan error (misal "API key not valid: AIza...").
                    // Cukup class name untuk debugging, tanpa risiko bocor key via Logcat.
                    android.util.Log.e("GeminiRepo", "Error: ${e::class.simpleName}")
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
    }
}