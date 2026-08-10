package com.sndiy.chatfin.ai

import com.sndiy.chatfin.core.parser.TransactionQueryParser
import kotlinx.serialization.Serializable
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * @Serializable supaya opsi bisa ikut disimpan bersama pesannya di Room.
 * Tanpa itu, tombol pilihan hilang setiap layar chat dibuka ulang — pesannya
 * pulih dari database tapi opsinya tidak, jadi percakapan yang sedang berjalan
 * kehilangan satu-satunya cara melanjutkannya.
 */
@Serializable
sealed class ChatOption {
    @Serializable
    data class CategoryOptions(val options: List<String>) : ChatOption()
    @Serializable
    data class WalletOptions(val options: List<String>) : ChatOption()
    @Serializable
    data class TransactionConfirm(
        val type: String,
        val amount: Long,
        val category: String,
        val wallet: String,
        val title: String = ""
    ) : ChatOption()
    @Serializable
    data class YesNo(val question: String) : ChatOption()
    @Serializable
    data class VisualizationRequest(
        val title: String = "Grafik Keuangan",
        val initialType: String = "BAR",
        val initialPeriod: String = "THIS_MONTH",
        // List (bukan comma-separated) — konsisten dengan CategoryOptions/WalletOptions
        // di file ini. Default emptyList() aman untuk baris chat lama (Room) yang
        // tersimpan sebelum field ini ada, lewat ignoreUnknownKeys.
        val categoryNames: List<String> = emptyList(),
        val walletNames: List<String> = emptyList(),
        /** EXPENSE | INCOME; null = default EXPENSE saat dirender (lihat InteractiveChartCard). */
        val txType: String? = null
    ) : ChatOption()
    @Serializable
    data class TableRequest(
        val title: String = "Tabel Keuangan",
        val initialTemplate: String = "CATEGORY_SUMMARY",
        val categoryNames: List<String> = emptyList(),
        val walletNames: List<String> = emptyList(),
        val txType: String? = null
    ) : ChatOption()
    /** Kartu daftar transaksi periode tertentu. Dibuat dari parser lokal
     *  ([com.sndiy.chatfin.core.parser.TransactionQueryParser]), bukan dari AI —
     *  tanggal disimpan sebagai String "yyyy-MM-dd" supaya tetap serializable. */
    @Serializable
    data class TransactionListResult(
        val periodLabel: String,
        val startDate: String,
        val endDate: String,
        /** null = pakai batas default kartu. Punya default supaya baris chat
         *  lama (yang tersimpan sebelum field ini ada) tetap bisa didecode. */
        val limit: Int? = null,
        /** Filter opsional; null = tidak disaring pada dimensi itu. */
        val categoryName: String? = null,
        val walletName: String? = null,
        val type: String? = null
    ) : ChatOption()

    companion object {
        private val json = kotlinx.serialization.json.Json {
            // Skema opsi bisa bertambah; baris lama tidak boleh bikin crash.
            ignoreUnknownKeys = true
            // WAJIB diganti dari default "type": TransactionConfirm punya field
            // bernama `type` (INCOME/EXPENSE), dan bentrok dengan penanda varian
            // membuat seluruh proses encode gagal — kartu konfirmasi diam-diam
            // tidak pernah tersimpan.
            classDiscriminator = "optionKind"
        }

        fun encode(option: ChatOption?): String? =
            option?.let { runCatching { json.encodeToString(serializer(), it) }.getOrNull() }

        /** Baris lama / format tak dikenal cukup jadi null — pesannya tetap tampil. */
        fun decode(raw: String?): ChatOption? =
            raw?.takeIf { it.isNotBlank() }
                ?.let { runCatching { json.decodeFromString(serializer(), it) }.getOrNull() }
    }
}

data class ParsedMessage(
    val text: String,
    val option: ChatOption? = null
)

@Singleton
class ChatOptionsParser @Inject constructor() {

    // Tag normal
    private val tagPattern = Regex(
        """\[CHATFIN_OPTIONS\](.*?)\[/CHATFIN_OPTIONS\]""",
        RegexOption.DOT_MATCHES_ALL
    )

    // Fallback: JSON mentah yang nyasar di teks (tanpa tag)
    private val rawConfirmPattern = Regex(
        """\{[^{}]*"type"\s*:\s*"confirm"[^{}]*"transaction"\s*:\s*\{[^{}]*\}[^{}]*\}""",
        setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
    )
    private val rawCategoryPattern = Regex(
        """\{[^{}]*"type"\s*:\s*"category"[^{}]*"options"\s*:\s*\[[^\]]*\][^{}]*\}""",
        setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
    )
    private val rawWalletPattern = Regex(
        """\{[^{}]*"type"\s*:\s*"wallet"[^{}]*"options"\s*:\s*\[[^\]]*\][^{}]*\}""",
        setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
    )
    private val rawChartPattern = Regex(
        """\{[^{}]*"type"\s*:\s*"chart"[^{}]*\}""",
        setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
    )
    private val rawTablePattern = Regex(
        """\{[^{}]*"type"\s*:\s*"table"[^{}]*\}""",
        setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
    )
    private val rawTransactionsPattern = Regex(
        """\{[^{}]*"type"\s*:\s*"transactions"[^{}]*\}""",
        setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
    )

    fun parse(rawMessage: String): ParsedMessage {
        // 1. Coba tag normal dulu
        val tagMatch = tagPattern.find(rawMessage)
        if (tagMatch != null) {
            val text   = rawMessage.replace(tagMatch.value, "").trim()
            val json   = tagMatch.groupValues[1].trim()
            val option = parseOption(json)
            return ParsedMessage(text = text, option = option)
        }

        // 2. Fallback: detect JSON mentah di teks biasa
        val fallbackMatch = rawConfirmPattern.find(rawMessage)
            ?: rawCategoryPattern.find(rawMessage)
            ?: rawWalletPattern.find(rawMessage)
            ?: rawChartPattern.find(rawMessage)
            ?: rawTablePattern.find(rawMessage)
            ?: rawTransactionsPattern.find(rawMessage)

        if (fallbackMatch != null) {
            val text   = rawMessage.replace(fallbackMatch.value, "").trim()
            val option = parseOption(fallbackMatch.value.trim())
            return ParsedMessage(text = text, option = option)
        }

        // 3. Tidak ada options sama sekali
        return ParsedMessage(text = rawMessage.trim())
    }

    /** Array JSON opsional → List<String>; field tidak ada/bukan array → kosong
     *  (= tidak difilter), bukan error — permintaan grafik/tabel tanpa filter
     *  tetap valid. */
    private fun JSONObject.optJsonStringArray(key: String): List<String> {
        val arr = optJSONArray(key) ?: return emptyList()
        return (0 until arr.length()).mapNotNull { arr.optString(it, null) }
    }

    private fun parseOption(json: String): ChatOption? {
        return try {
            val obj  = JSONObject(json)
            val type = obj.getString("type")
            when (type) {
                "category" -> {
                    val list = obj.getJSONArray("options")
                    ChatOption.CategoryOptions((0 until list.length()).map { list.getString(it) })
                }
                "wallet" -> {
                    val list = obj.getJSONArray("options")
                    ChatOption.WalletOptions((0 until list.length()).map { list.getString(it) })
                }
                "confirm" -> {
                    val tx = obj.getJSONObject("transaction")
                    ChatOption.TransactionConfirm(
                        type     = tx.getString("type"),
                        amount   = tx.getLong("amount"),
                        category = tx.getString("category"),
                        wallet   = tx.getString("wallet"),
                        title    = tx.optString("title", "")
                    )
                }
                "yesno" -> ChatOption.YesNo(question = obj.getString("question"))
                "chart" -> ChatOption.VisualizationRequest(
                    title = obj.optString("title", "Grafik Keuangan"),
                    initialType = obj.optString("chart_type", "BAR").uppercase(),
                    initialPeriod = obj.optString("period", "THIS_MONTH").uppercase(),
                    categoryNames = obj.optJsonStringArray("categories"),
                    walletNames   = obj.optJsonStringArray("wallets"),
                    txType        = obj.optString("txType").ifBlank { null }?.uppercase()
                )
                "table" -> ChatOption.TableRequest(
                    title = obj.optString("title", "Tabel Keuangan"),
                    initialTemplate = obj.optString("template", "CATEGORY_SUMMARY").uppercase(),
                    categoryNames = obj.optJsonStringArray("categories"),
                    walletNames   = obj.optJsonStringArray("wallets"),
                    txType        = obj.optString("txType").ifBlank { null }?.uppercase()
                )
                // Kartu daftar transaksi yang diminta AI sendiri. Ini jalur untuk
                // kalimat yang cuma bisa dipahami dari konteks percakapan
                // ("oke tampilkan" sesudah Mai menawarkannya) — pencocokan kata
                // kunci di TransactionQueryParser mustahil menangkap itu.
                "transactions" -> {
                    val q = TransactionQueryParser.fromKeyword(obj.optString("period", "THIS_MONTH"))
                    ChatOption.TransactionListResult(
                        periodLabel  = q.periodLabel,
                        startDate    = q.startDate.toString(),
                        endDate      = q.endDate.toString(),
                        limit        = q.limit,
                        categoryName = obj.optString("category").ifBlank { null },
                        walletName   = obj.optString("wallet").ifBlank { null },
                        type         = obj.optString("txType").ifBlank { null }?.uppercase()
                    )
                }
                else -> null
            }
        } catch (e: Exception) {
            null
        }
    }
}