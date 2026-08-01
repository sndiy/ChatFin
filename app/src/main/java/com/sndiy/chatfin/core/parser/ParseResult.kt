package com.sndiy.chatfin.core.parser

/** Field yang masih perlu ditanyakan ke user sebelum transaksi bisa disimpan. */
enum class TransactionField { CATEGORY, WALLET }

/**
 * Hasil ekstraksi TransactionParser dari satu kalimat. `type`/`categoryId`
 * pakai representasi String ("INCOME"/"EXPENSE") mengikuti konvensi yang
 * sudah dipakai di seluruh codebase (TransactionEntity.type, BotModeHandler,
 * ChatOption), supaya tidak perlu lapisan konversi tambahan saat dipasang ke
 * ChatViewModel di M7.
 *
 * `walletHint` disediakan untuk perluasan nanti (mis. deteksi "dari BCA") —
 * TransactionParser saat ini SELALU mengisi null, belum ada logika deteksi
 * dompet dari teks.
 */
data class ParsedDraft(
    val type: String?,
    val amount: Long?,
    val categoryId: String?,
    val categoryName: String?,
    val title: String,
    val walletHint: String? = null
)

sealed interface ParseResult {
    data class Complete(val draft: ParsedDraft, val confidence: Float) : ParseResult
    data class Partial(val draft: ParsedDraft, val missing: List<TransactionField>) : ParseResult
    data object NotATransaction : ParseResult
}
