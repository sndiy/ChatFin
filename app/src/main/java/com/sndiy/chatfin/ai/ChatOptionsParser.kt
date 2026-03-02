// app/src/main/java/com/sndiy/chatfin/ai/ChatOptionsParser.kt

package com.sndiy.chatfin.ai

import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tipe-tipe pilihan yang bisa ditampilkan AI di chat
 */
sealed class ChatOption {
    // Pilihan kategori
    data class CategoryOptions(val options: List<String>) : ChatOption()

    // Pilihan dompet
    data class WalletOptions(val options: List<String>) : ChatOption()

    // Konfirmasi transaksi sebelum disimpan
    data class TransactionConfirm(
        val type: String,       // INCOME | EXPENSE | TRANSFER
        val amount: Long,
        val category: String,
        val wallet: String
    ) : ChatOption()

    // Pilihan ya/tidak
    data class YesNo(val question: String) : ChatOption()
}

/**
 * Hasil parse pesan AI:
 * text = bagian teks biasa (ucapan karakter)
 * option = pilihan yang perlu ditampilkan (null jika tidak ada)
 */
data class ParsedMessage(
    val text: String,
    val option: ChatOption? = null
)

/**
 * Parser untuk memisahkan teks karakter dari blok [CHATFIN_OPTIONS]
 */
@Singleton
class ChatOptionsParser @Inject constructor() {

    private val optionsPattern = Regex(
        """\[CHATFIN_OPTIONS\](.*?)\[/CHATFIN_OPTIONS\]""",
        RegexOption.DOT_MATCHES_ALL
    )

    fun parse(rawMessage: String): ParsedMessage {
        val match = optionsPattern.find(rawMessage)
            ?: return ParsedMessage(text = rawMessage.trim())

        // Pisahkan teks biasa dan JSON options
        val text   = rawMessage.replace(match.value, "").trim()
        val json   = match.groupValues[1].trim()
        val option = parseOption(json)

        return ParsedMessage(text = text, option = option)
    }

    private fun parseOption(json: String): ChatOption? {
        return try {
            val obj  = JSONObject(json)
            val type = obj.getString("type")

            when (type) {
                "category" -> {
                    val list = obj.getJSONArray("options")
                    val opts = (0 until list.length()).map { list.getString(it) }
                    ChatOption.CategoryOptions(opts)
                }
                "wallet" -> {
                    val list = obj.getJSONArray("options")
                    val opts = (0 until list.length()).map { list.getString(it) }
                    ChatOption.WalletOptions(opts)
                }
                "confirm" -> {
                    val tx = obj.getJSONObject("transaction")
                    ChatOption.TransactionConfirm(
                        type     = tx.getString("type"),
                        amount   = tx.getLong("amount"),
                        category = tx.getString("category"),
                        wallet   = tx.getString("wallet")
                    )
                }
                "yesno" -> {
                    ChatOption.YesNo(question = obj.getString("question"))
                }
                else -> null
            }
        } catch (e: Exception) {
            null
        }
    }
}