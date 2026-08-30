package com.sndiy.chatfin.core.parser

/**
 * Sumber kamus kata-kunci → kategori. Implementasi murni (mis. DefaultKeywords
 * untuk isi bawaan) atau ter-backing Room (RoomKeywordSource, milestone
 * berikutnya) supaya user bisa menambah kata kunci sendiri tanpa update
 * aplikasi.
 */
interface KeywordSource {
    /**
     * Cari kategori yang paling cocok untuk sepotong teks (biasanya judul
     * hasil ekstraksi TransactionParser). Kalau lebih dari satu kata kunci
     * cocok, yang TERPANJANG menang — supaya "kopi susu gula aren" match ke
     * "kopi" bukan match parsial yang salah.
     */
    fun findCategory(text: String, type: String): CategoryMatch?
}

data class CategoryMatch(
    val categoryId: String,
    val categoryName: String,
    val keyword: String
)
