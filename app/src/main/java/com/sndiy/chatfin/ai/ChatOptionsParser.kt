package com.sndiy.chatfin.ai

import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

sealed class ChatOption {
    data class CategoryOptions(val options: List<String>) : ChatOption()
    data class WalletOptions(val options: List<String>) : ChatOption()
    data class TransactionConfirm(
        val type: String,
        val amount: Long,
        val category: String,
        val wallet: String,
        val title: String = ""
    ) : ChatOption()
    data class YesNo(val question: String) : ChatOption()
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

        if (fallbackMatch != null) {
            val text   = rawMessage.replace(fallbackMatch.value, "").trim()
            val option = parseOption(fallbackMatch.value.trim())
            return ParsedMessage(text = text, option = option)
        }

        // 3. Tidak ada options sama sekali
        return ParsedMessage(text = rawMessage.trim())
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
                else -> null
            }
        } catch (e: Exception) {
            null
        }
    }
}