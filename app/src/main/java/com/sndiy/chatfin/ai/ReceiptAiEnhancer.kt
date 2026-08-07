package com.sndiy.chatfin.ai

import android.content.Context
import androidx.annotation.StringRes
import com.sndiy.chatfin.R
import com.sndiy.chatfin.core.ocr.ImageUtils
import com.sndiy.chatfin.core.ocr.ParsedReceipt
import com.sndiy.chatfin.core.ocr.ParsedReceiptItem
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

sealed interface ReceiptAiResult {
    data class Success(val receipt: ParsedReceipt) : ReceiptAiResult
    data class Failure(@StringRes val messageRes: Int) : ReceiptAiResult
}

private const val TIMEOUT_MS = 15_000L
private const val MAX_REASONABLE_AMOUNT = 500_000_000L

private const val RECEIPT_PROMPT = """
Kamu membaca gambar struk belanja. Balas HANYA dengan satu objek JSON, tanpa penjelasan,
tanpa markdown, dengan skema persis berikut:
{
  "merchant": string atau null (HANYA nama toko/merchant, jangan gabungkan dengan alamat/item/teks lain),
  "date": string "yyyy-MM-dd" atau null,
  "time": string "HH:mm" atau null,
  "items": [ { "name": string, "price": number bulat rupiah } ],
  "total": number bulat rupiah
}

Aturan penting:
- "total" WAJIB nominal akhir yang benar-benar dibayar: sudah termasuk pajak/PPN/service charge,
  dan SUDAH DIKURANGI diskon/potongan. JANGAN pakai subtotal atau nominal sebelum diskon/pajak.
- Bedakan dengan cermat baris subtotal, diskon/potongan, pajak/PPN/service charge, dan total akhir
  di struk — kalau struk buram atau formatnya tidak standar, cari baris yang paling mirip
  "total akhir yang dibayar", bukan angka terbesar begitu saja.
- Semua nominal berupa angka bulat rupiah tanpa titik/koma/simbol mata uang.
- Field yang benar-benar tidak terbaca diisi null (untuk merchant/date/time) atau dilewati (untuk items).
- Jangan mengarang data yang tidak ada di gambar.
"""

@Singleton
class ReceiptAiEnhancer @Inject constructor(
    private val client: GeminiClient
) {

    suspend fun enhance(
        context: Context,
        imageUri: String,
        baseline: ParsedReceipt
    ): ReceiptAiResult {
        val bitmap = ImageUtils.loadScaledBitmap(context, imageUri)
            ?: return ReceiptAiResult.Failure(R.string.receipt_ai_error_image)

        return try {
            val rawJson = withTimeoutOrNull(TIMEOUT_MS) {
                client.generateReceiptJson(bitmap, RECEIPT_PROMPT)
            } ?: return ReceiptAiResult.Failure(R.string.receipt_ai_error_timeout)

            val parsed = parseAndValidate(rawJson, baseline)
                ?: return ReceiptAiResult.Failure(R.string.receipt_ai_error_parse)

            // Isi respons AI TIDAK PERNAH dicetak — bisa memuat analisis struk/nominal user.
            android.util.Log.d("ReceiptAiEnhancer", "OK (${rawJson.length} char)")
            ReceiptAiResult.Success(parsed)
        } catch (e: TimeoutCancellationException) {
            ReceiptAiResult.Failure(R.string.receipt_ai_error_timeout)
        } catch (e: ApiKeyMissingException) {
            ReceiptAiResult.Failure(R.string.receipt_ai_error_no_key)
        } catch (e: Exception) {
            // Jangan cetak e.message — bisa memuat isi respons AI atau teks error yang
            // menyertakan API key. Nama class cause (bukan pesannya) aman dicetak — berguna
            // membedakan error jaringan dari kegagalan lain yang benar-benar tak terduga.
            android.util.Log.e(
                "ReceiptAiEnhancer",
                "Error: ${e::class.simpleName}, cause=${e.cause?.let { it::class.simpleName }}"
            )
            val finishReason = (e as? GeminiApiException)?.finishReason
            if (finishReason != null) {
                // finishReason (MAX_TOKENS/SAFETY/RECITATION/dst) cuma nama status, bukan isi
                // respons — aman dicetak, dan ini yang paling sering jadi penyebab nyata pesan
                // "gagal membaca struk" (generation berhenti sebelum JSON selesai ditulis).
                android.util.Log.w("ReceiptAiEnhancer", "Gagal: generation berhenti (finishReason=$finishReason)")
                ReceiptAiResult.Failure(R.string.receipt_ai_error_parse)
            } else if (client.isQuotaError(e)) {
                ReceiptAiResult.Failure(R.string.receipt_ai_error_quota)
            } else if (isNetworkError(e)) {
                ReceiptAiResult.Failure(R.string.receipt_ai_error_network)
            } else {
                ReceiptAiResult.Failure(R.string.receipt_ai_error_parse)
            }
        }
    }

    private fun parseAndValidate(rawJson: String, baseline: ParsedReceipt): ParsedReceipt? {
        // Log tahap kegagalan saja (bukan isi rawJson/hasil parsing) — supaya kegagalan validasi
        // bisa didiagnosis dari Logcat tanpa membocorkan data struk/finansial user.
        if (rawJson.isBlank()) {
            android.util.Log.w("ReceiptAiEnhancer", "Gagal: respons AI kosong")
            return null
        }
        val jsonText = extractJsonObject(rawJson) ?: run {
            android.util.Log.w("ReceiptAiEnhancer", "Gagal: tidak ditemukan objek JSON di respons")
            return null
        }
        val obj = try {
            JSONObject(jsonText)
        } catch (e: Exception) {
            android.util.Log.w("ReceiptAiEnhancer", "Gagal: JSON tidak valid (${e::class.simpleName})")
            return null
        }

        val total = obj.optLong("total", -1L)
        if (total <= 0L || total > MAX_REASONABLE_AMOUNT) {
            android.util.Log.w("ReceiptAiEnhancer", "Gagal: field total tidak valid")
            return null
        }

        val date = obj.optCleanString("date")
            ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            ?.toString()
            ?: baseline.date

        val time = obj.optCleanString("time") ?: baseline.time

        val merchant = obj.optCleanString("merchant")?.let { sanitizeMerchant(it) } ?: baseline.merchant

        val items = mutableListOf<ParsedReceiptItem>()
        val itemsArray = obj.optJSONArray("items")
        if (itemsArray != null) {
            for (i in 0 until itemsArray.length()) {
                val itemObj = itemsArray.optJSONObject(i) ?: continue
                val name = itemObj.optCleanString("name") ?: continue
                val price = itemObj.optLong("price", -1L)
                if (price < 0L) continue
                items += ParsedReceiptItem(name = name, price = price)
            }
        }

        return baseline.copy(
            merchant = merchant,
            date = date,
            time = time,
            items = if (items.isNotEmpty()) items else baseline.items,
            totalAmount = total,
            isMerchantLowConfidence = false,
            isDateLowConfidence = false,
            isTotalLowConfidence = false,
            isAiEnhanced = true
        )
    }

    /**
     * SDK membungkus error jaringan level HTTP (timeout koneksi, host tak terjangkau, dst) jadi
     * UnknownException generik — telusuri rantai cause untuk cari java.io.IOException asli,
     * supaya user dapat pesan yang tepat ("periksa koneksi") alih-alih pesan parse yang salah arah.
     */
    private fun isNetworkError(e: Throwable): Boolean {
        var current: Throwable? = e
        var depth = 0
        while (current != null && depth < 5) {
            if (current is java.io.IOException) return true
            current = current.cause
            depth++
        }
        return false
    }

    /**
     * `optString(key, fallback)` bawaan Android TIDAK mengembalikan `fallback` saat value JSON-nya
     * literal `null` — org.json menyimpan null sebagai sentinel `JSONObject.NULL`, dan
     * `.toString()` sentinel itu mengembalikan string `"null"` (4 karakter), bukan Java null.
     * Akibatnya field yang jujur diisi `null` oleh AI (mis. struk tanpa jam tercetak) malah
     * tampil sebagai teks "null" ke user. Helper ini menyatukan key-absen/JSON-null/string-"null"
     * literal jadi satu sinyal null yang konsisten.
     */
    private fun JSONObject.optCleanString(key: String): String? {
        val value = optString(key, "").trim()
        return value.takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) }
    }

    /** Ambil substring objek JSON terluar, menoleransi pagar ```json yang kadang disisipkan model. */
    private fun extractJsonObject(raw: String): String? {
        val start = raw.indexOf('{')
        val end = raw.lastIndexOf('}')
        if (start == -1 || end == -1 || end < start) return null
        return raw.substring(start, end + 1)
    }

    /** Menegakkan aturan "title/merchant hanya nama toko" — tolak kalau berisi gabungan item/teks total. */
    private fun sanitizeMerchant(raw: String): String? {
        val firstLine = raw.lineSequence().firstOrNull()?.trim().orEmpty()
        if (firstLine.isBlank()) return null
        if (firstLine.length > 40) return firstLine.take(40).trim()
        val lower = firstLine.lowercase()
        val rejectedKeywords = listOf("total", "subtotal", "diskon", "pajak", "ppn", "rp", "bayar")
        if (rejectedKeywords.any { lower.contains(it) }) return null
        if (firstLine.none { it.isLetter() }) return null
        return firstLine
    }
}
