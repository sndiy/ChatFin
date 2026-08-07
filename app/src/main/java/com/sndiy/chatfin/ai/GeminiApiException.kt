package com.sndiy.chatfin.ai

/**
 * Satu tipe exception untuk semua kegagalan panggilan REST Gemini (menggantikan hierarki
 * exception SDK lama `com.google.ai.client.generativeai.type.*` sejak migrasi ke OkHttp —
 * lihat komentar migrasi di GeminiClient.kt).
 *
 * [message] SELALU teks mentah dari body error JSON Google (`error.message`) kalau tersedia —
 * ini penting karena tiga tempat lain (GeminiRepository, ChatViewModel, ApiKeyViewModel)
 * men-sniff kata kunci di dalam pesan ini ("API key not valid", "RESOURCE_EXHAUSTED", dst).
 * Jangan menulis ulang pesan ini jadi generik.
 *
 * @property httpStatusCode kode HTTP asli dari response (429 dipakai [GeminiClient.isQuotaError]
 *   sebagai deteksi kuota yang sungguhan, bukan tebak-tebak substring pesan).
 * @property finishReason nilai `finishReason` dari kandidat pertama kalau generation berhenti
 *   sebelum selesai (mis. "MAX_TOKENS", "SAFETY") — dicek [ReceiptAiEnhancer] untuk membedakan
 *   kegagalan ini dari kegagalan lain.
 */
class GeminiApiException(
    message: String,
    val httpStatusCode: Int? = null,
    val finishReason: String? = null,
    cause: Throwable? = null
) : Exception(message, cause)
