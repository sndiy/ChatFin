package com.sndiy.chatfin.ai

import android.graphics.Bitmap
import android.util.Base64
import com.sndiy.chatfin.core.data.security.SecureStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GeminiClient @Inject constructor(
    private val secureStorage: SecureStorage,
    private val okHttpClient: OkHttpClient
) {
    companion object {
        // gemini-2.5-flash/-flash-lite dijadwalkan Google shutdown 16 Okt 2026 — pindah ke
        // lini 3.x sebelum tanggal itu. Kalau butuh ganti lagi nanti, cek daftar model resmi:
        // https://ai.google.dev/gemini-api/docs/models (kolom deprecated/shutdown).
        private const val MODEL_PRIMARY  = "gemini-3.6-flash"
        private const val MODEL_FALLBACK = "gemini-3.5-flash-lite"

        // MIGRASI: sebelumnya pakai SDK com.google.ai.client.generativeai:0.9.0 — SDK itu sudah
        // resmi deprecated Google (repo GitHub-nya sekarang literal bernama
        // "deprecated-generative-ai-android"). Jalur migrasi resmi Google adalah Firebase AI
        // Logic, TAPI itu tidak dipakai di sini dengan sengaja: Firebase AI Logic memanggil
        // Gemini lewat project Firebase milik DEVELOPER, artinya developer yang menanggung
        // tagihan semua user — bertentangan langsung dengan prinsip "app harus jalan tanpa API
        // key developer, user bawa key sendiri sendiri" (AGENTS.md §1, §2.8). Solusinya panggil
        // REST API Gemini langsung lewat OkHttp: key tetap dikirim sebagai query param, tetap
        // dibaca dari SecureStorage yang sama persis seperti sebelumnya — audit keamanan key
        // yang sudah pernah dilakukan tetap berlaku penuh, tidak ada jalur baru pengambilan key.
        private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models"
        private val JSON_MEDIA_TYPE = "application/json".toMediaType()
    }

    private val json = Json { ignoreUnknownKeys = true }

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
    // (lihat AGENTS.md §2.8). Kosong di sini berarti AI memang belum diaktifkan
    // user, bukan kondisi darurat — sendMessage()/generateReceiptJson() di bawah
    // menerjemahkannya jadi ApiKeyMissingException, dan pemanggil (ChatViewModel)
    // sudah degradasi ke Mode Bot untuk semua kegagalan AI.
    suspend fun resolveApiKey(): String = secureStorage.getGeminiApiKey().orEmpty()

    /**
     * Fungsi inti — satu-satunya titik yang benar-benar bicara ke jaringan. `sendMessage`,
     * `generateReceiptJson`, dan `validateApiKey` semuanya lewat sini.
     */
    private suspend fun callGenerateContent(
        modelName: String,
        apiKey: String,
        contents: List<GeminiContent>,
        systemInstruction: GeminiContent? = null,
        generationConfig: GeminiGenerationConfig? = null
    ): GeminiResponse = withContext(Dispatchers.IO) {
        val requestJson = json.encodeToString(
            GeminiRequest(
                contents = contents,
                systemInstruction = systemInstruction,
                generationConfig = generationConfig
            )
        )
        val request = Request.Builder()
            .url("$BASE_URL/$modelName:generateContent?key=$apiKey")
            .post(requestJson.toRequestBody(JSON_MEDIA_TYPE))
            .build()

        val bodyText = try {
            executeAsync(request).use { response ->
                val text = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    // detail?.message adalah teks mentah dari Google — DIJAGA apa adanya karena
                    // GeminiRepository/ChatViewModel/ApiKeyViewModel men-sniff kata kunci di
                    // dalamnya ("API key not valid", "RESOURCE_EXHAUSTED", dst).
                    val detail = runCatching {
                        json.decodeFromString<GeminiErrorEnvelope>(text).error
                    }.getOrNull()
                    throw GeminiApiException(
                        message = detail?.message ?: "HTTP ${response.code}",
                        httpStatusCode = response.code
                    )
                }
                text
            }
        } catch (e: IOException) {
            throw wrapNetworkError(e)
        }

        val parsed = try {
            json.decodeFromString<GeminiResponse>(bodyText)
        } catch (e: Exception) {
            throw GeminiApiException("Gagal membaca respons Gemini (format tidak dikenali).", cause = e)
        }

        parsed.promptFeedback?.blockReason?.let { reason ->
            throw GeminiApiException("Prompt was blocked: $reason")
        }
        val finishReason = parsed.candidates?.firstOrNull()?.finishReason
        if (finishReason != null && finishReason != "STOP") {
            throw GeminiApiException(
                message = "Content generation stopped. Reason: $finishReason",
                finishReason = finishReason
            )
        }

        parsed
    }

    /** Jembatan OkHttp `Call` → coroutine yang benar-benar bisa dibatalkan (bukan `.execute()` blocking). */
    private suspend fun executeAsync(request: Request): Response =
        suspendCancellableCoroutine { cont ->
            val call = okHttpClient.newCall(request)
            cont.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    cont.resumeWith(Result.failure(e))
                }
                override fun onResponse(call: Call, response: Response) {
                    cont.resumeWith(Result.success(response))
                }
            })
        }

    /**
     * OkHttp/JVM melempar berbagai subtipe IOException dengan pesan yang bentuknya tidak selalu
     * bisa ditebak (mis. SocketTimeoutException tidak selalu memuat kata "timeout" di pesannya).
     * Tiga pemanggil (GeminiRepository, ChatViewModel, ApiKeyViewModel) men-sniff kata
     * "internet"/"network"/"timeout" di exception.message — jadi di sini pesan itu DIJAMIN
     * memuat kata kunci itu secara eksplisit, bukan berharap kebetulan cocok. Tetap dikembalikan
     * sebagai IOException (bukan dibungkus tipe lain) supaya
     * ReceiptAiEnhancer.isNetworkError() — yang menelusuri rantai cause mencari IOException —
     * tetap kompatibel tanpa perubahan.
     */
    private fun wrapNetworkError(e: IOException): IOException = when (e) {
        is UnknownHostException ->
            IOException("Tidak ada koneksi internet (host tidak ditemukan/network unreachable).", e)
        is SocketTimeoutException ->
            IOException("Waktu koneksi ke server Gemini habis (network timeout).", e)
        else ->
            IOException("Gangguan jaringan/koneksi internet: ${e.message}", e)
    }

    // Tes key SUNGGUHAN terhadap API Gemini (bukan cuma cek format/non-kosong).
    // Sengaja pakai MODEL_PRIMARY, BUKAN MODEL_FALLBACK: histori nyata di model generasi
    // sebelumnya (gemini-2.5-flash-lite) pernah 404 "no longer available to new users"
    // untuk sebagian akun walau masih terdaftar di daftar model resmi — jadi model fallback
    // tidak bisa diasumsikan otomatis hidup. MODEL_PRIMARY yang rutin dipakai di
    // sendMessage() adalah pilihan paling aman untuk validasi. Kalau MODEL_FALLBACK yang
    // sekarang ternyata bermasalah lagi di masa depan, itu akan kelihatan dari kegagalan
    // rotateModel() saat chat sungguhan, bukan di layar ini.
    //
    // maxOutputTokens TIDAK boleh dibuat kecil (dicoba 8, 32, 256 — semuanya
    // gagal dengan finishReason MAX_TOKENS): model "thinking" Gemini (2.5 & 3.x)
    // ikut memotong maxOutputTokens SEBELUM teks jawaban sungguhan diproduksi,
    // jadi budget kecil habis duluan oleh reasoning internal sebelum ada satu
    // kata jawaban. Samakan dengan budget yang SUDAH terbukti jalan di alur chat
    // sungguhan (sendMessage() di bawah, 1024) — bukan angka baru yang ditebak.
    suspend fun validateApiKey(apiKey: String): Result<Unit> = try {
        callGenerateContent(
            modelName = MODEL_PRIMARY,
            apiKey = apiKey,
            contents = listOf(GeminiContent(role = "user", parts = listOf(GeminiPart(text = "hi")))),
            generationConfig = GeminiGenerationConfig(maxOutputTokens = 1024)
        )
        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(e)
    }

    fun isQuotaError(e: Exception): Boolean {
        // Deteksi utama: status HTTP 429 sungguhan — bukan tebak-tebak substring pesan.
        if ((e as? GeminiApiException)?.httpStatusCode == 429) return true
        // Fallback untuk bentuk error lain yang belum terantisipasi (mis. isi pesan masih
        // menyebut RESOURCE_EXHAUSTED walau kodenya bukan 429).
        val msg = e.message ?: return false
        return msg.contains("RESOURCE_EXHAUSTED") ||
                msg.contains("quota", ignoreCase = true) ||
                msg.contains("rate", ignoreCase = true) ||
                msg.contains("429")
    }

    // Dipakai khusus alur "Perbaiki dengan AI" (scan struk) — SENGAJA selalu MODEL_PRIMARY,
    // tidak menyentuh currentModelIndex/rotateModel(): itu adalah state milik alur chat.
    // Kalau struk kena 429 lalu diam-diam menggeser model chat (atau sebaliknya), user chat
    // yang tidak sedang scan struk bisa ikut ke-throttle tanpa alasan yang terlihat.
    suspend fun generateReceiptJson(bitmap: Bitmap, prompt: String): String {
        val apiKey = resolveApiKey()
        if (apiKey.isBlank()) throw ApiKeyMissingException()

        val jpegBytes = ByteArrayOutputStream().use { stream ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, stream)
            stream.toByteArray()
        }
        val base64Image = Base64.encodeToString(jpegBytes, Base64.NO_WRAP)

        val response = callGenerateContent(
            modelName = MODEL_PRIMARY,
            apiKey = apiKey,
            contents = listOf(
                GeminiContent(
                    role = "user",
                    parts = listOf(
                        GeminiPart(inlineData = GeminiInlineData(mimeType = "image/jpeg", data = base64Image)),
                        GeminiPart(text = prompt)
                    )
                )
            ),
            generationConfig = GeminiGenerationConfig(
                temperature = 0.1f,
                // Sama alasan dengan validateApiKey(): thinking budget model Gemini memotong
                // maxOutputTokens SEBELUM teks jawaban diproduksi. Tugas ini (vision + baca tata
                // letak struk + format JSON) jauh lebih berat dari chat teks biasa yang cukup
                // 1024 — budget kecil di sini terbukti membuat generation berhenti dengan
                // finishReason MAX_TOKENS sebelum JSON sempat selesai ditulis.
                maxOutputTokens = 8192,
                responseMimeType = "application/json"
            )
        )
        return response.candidates?.firstOrNull()?.content?.parts
            ?.firstOrNull { it.text != null }?.text ?: ""
    }

    // Kirim dengan model yang sedang aktif — tidak auto-fallback lagi
    suspend fun sendMessage(
        userMessage: String,
        history: List<Pair<String, String>>,
        systemPrompt: String
    ): String {
        val apiKey = resolveApiKey()
        if (apiKey.isBlank()) throw ApiKeyMissingException()

        val modelName = models[currentModelIndex]
        android.util.Log.d("GeminiClient", "Kirim dengan model: $modelName")

        val contents = history.map { (role, text) ->
            GeminiContent(role = role, parts = listOf(GeminiPart(text = text)))
        } + GeminiContent(role = "user", parts = listOf(GeminiPart(text = userMessage)))

        val response = callGenerateContent(
            modelName = modelName,
            apiKey = apiKey,
            contents = contents,
            systemInstruction = GeminiContent(parts = listOf(GeminiPart(text = systemPrompt))),
            // 1024 TERBUKTI TIDAK CUKUP untuk prompt chat sungguhan (lihat catatan di
            // validateApiKey/generateReceiptJson soal thinking budget) — system prompt di sini
            // memuat persona + konteks finansial + alur pencatatan bernomor + riwayat percakapan,
            // jauh lebih berat dari "hi" di validateApiKey(). Model menghabiskan reasoning-nya
            // duluan lalu berhenti dengan finishReason MAX_TOKENS sebelum sempat menulis jawaban
            // sama sekali — user melihatnya sebagai chat yang selalu gagal.
            generationConfig = GeminiGenerationConfig(temperature = 0.85f, maxOutputTokens = 4096)
        )
        return response.candidates?.firstOrNull()?.content?.parts
            ?.firstOrNull { it.text != null }?.text ?: ""
    }
}

class QuotaExhaustedException(modelName: String = "") : Exception(
    "Model $modelName sedang limit."
)

// Sinyal ketik supaya ChatViewModel bisa membedakan "AI belum diaktifkan"
// dari kegagalan AI lainnya, dan degradasi ke Mode Bot alih-alih menampilkan
// bubble error buntu (AGENTS.md §3 — fitur AI yang gagal wajib fallback).
class ApiKeyMissingException : Exception("API key belum diset.")

/**
 * Generation Gemini berhenti sebelum selesai (finishReason MAX_TOKENS/SAFETY/RECITATION/dst) —
 * BUKAN kegagalan jaringan atau kuota. Tipe terpisah supaya ChatViewModel bisa kasih pesan ramah
 * alih-alih menampilkan teks mentah "Content generation stopped. Reason: ..." ke user (pola yang
 * sama sudah dipakai ReceiptAiEnhancer untuk alur scan struk).
 */
class GenerationIncompleteException(val finishReason: String) : Exception(
    "Generation berhenti: $finishReason"
)
