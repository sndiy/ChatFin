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
 * `walletHint` berisi nama dompet mentah yang disebut user lewat kata depan
 * ("dari BCA", "pakai GoPay") — masih berupa teks apa adanya, BELUM dicocokkan
 * ke dompet yang benar-benar ada. Pencocokan ke `WalletEntity` dilakukan
 * pemanggil (BotModeHandler.handleParsed); kalau tidak ada yang cocok, dompet
 * tetap ditanyakan seperti biasa.
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
