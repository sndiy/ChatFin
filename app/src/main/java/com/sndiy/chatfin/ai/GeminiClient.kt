package com.sndiy.chatfin.ai

import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import com.google.ai.client.generativeai.type.generationConfig
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

    fun resetToPrimary() {
        if (currentModelIndex != 0) {
            currentModelIndex = 0
            android.util.Log.d("GeminiClient", "Reset ke primary: ${models[0]}")
        }
    }

    // Satu-satunya sumber API key yang sah: input user lewat Setelan → API Key,
    // disimpan di SecureStorage (EncryptedSharedPreferences). Tidak ada lagi
    // fallback ke BuildConfig — key bawaan developer di BuildConfig TIDAK
    // diobfuscate R8 dan bisa diambil dari APK mana pun dalam hitungan menit
    // (lihat CLAUDE.md §2.8). Kosong di sini berarti AI memang belum diaktifkan
    // user, bukan kondisi darurat — buildModel() di bawah menerjemahkannya jadi
    // pesan yang mengarahkan ke Setelan, dan pemanggil (ChatViewModel) sudah
    // degradasi ke Mode Bot untuk semua kegagalan AI.
    suspend fun resolveApiKey(): String = secureStorage.getGeminiApiKey().orEmpty()

    private suspend fun buildModel(modelName: String, systemPrompt: String): GenerativeModel {
        val apiKey = resolveApiKey()
        if (apiKey.isBlank()) throw ApiKeyMissingException()
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

    // Tes key SUNGGUHAN terhadap API Gemini (bukan cuma cek format/non-kosong).
    // Sengaja pakai MODEL_PRIMARY, BUKAN MODEL_FALLBACK: saat implementasi ini
    // ditulis, "gemini-2.5-flash-lite" (MODEL_FALLBACK) mengembalikan 404
    // "no longer available to new users" dari generateContent walau modelnya
    // masih ada di daftar model — kemungkinan model ini sedang dideprekasi
    // Google untuk sebagian akun. MODEL_PRIMARY terverifikasi bekerja normal.
    // Ini indikasi MODEL_FALLBACK mungkin juga bermasalah di alur
    // rotateModel() saat chat sungguhan — di luar scope layar ini, dilaporkan
    // terpisah.
    //
    // maxOutputTokens TIDAK boleh dibuat kecil (dicoba 8, 32, 256 — semuanya
    // gagal dengan ResponseStoppedException/MAX_TOKENS): model Gemini 2.5
    // punya "thinking budget" yang ikut memotong maxOutputTokens SEBELUM teks
    // jawaban sungguhan diproduksi, jadi budget kecil habis duluan oleh
    // reasoning internal sebelum ada satu kata jawaban. SDK 0.9.0 yang dipakai
    // di sini belum punya API untuk mematikan thinking secara eksplisit, jadi
    // solusinya samakan dengan budget yang SUDAH terbukti jalan di alur chat
    // sungguhan (buildModel() di bawah, 1024) — bukan angka baru yang ditebak.
    suspend fun validateApiKey(apiKey: String): Result<Unit> = try {
        val model = GenerativeModel(
            modelName        = MODEL_PRIMARY,
            apiKey            = apiKey,
            generationConfig  = generationConfig { maxOutputTokens = 1024 }
        )
        model.startChat().sendMessage("hi")
        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(e)
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

// Sinyal ketik supaya ChatViewModel bisa membedakan "AI belum diaktifkan"
// dari kegagalan AI lainnya, dan degradasi ke Mode Bot alih-alih menampilkan
// bubble error buntu (CLAUDE.md §3.4 — fitur AI yang gagal wajib fallback).
class ApiKeyMissingException : Exception("API key belum diset.")