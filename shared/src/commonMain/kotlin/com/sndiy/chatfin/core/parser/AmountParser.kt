package com.sndiy.chatfin.core.parser

/**
 * Parser nominal rupiah dari teks bebas pengguna — mendukung angka polos,
 * pemisah ribuan, underscore, dan singkatan Indonesia (rb/ribu/k, jt/juta).
 *
 * Diekstrak dari BotModeHandler.parseAmount() supaya bisa diuji tanpa
 * dependency Android/Hilt, dan dipakai bersama oleh Mode Bot (BotModeHandler)
 * dan parser transaksi rule-based (TransactionParser, milestone berikutnya).
 *
 * ATURAN (disengaja, bukan kebetulan — didokumentasikan di sini karena rupiah
 * tidak punya notasi desimal baku dalam pemakaian sehari-hari):
 * - Dengan akhiran "jt"/"juta" atau "rb"/"ribu"/"k": titik ATAU koma di depan
 *   akhiran diperlakukan sebagai PEMISAH DESIMAL. "1.5jt" = "1,5jt" = 1.500.000.
 * - TANPA akhiran: rupiah tidak pernah desimal. Titik ATAU koma diperlakukan
 *   SAMA sebagai pemisah ribuan (dibuang), tidak pernah sebagai desimal.
 *   "50.000" = "50,000" = 50000. Sebelum diekstrak ke sini, jalur ini secara
 *   keliru mengubah koma jadi titik lalu menafsirkannya sebagai desimal —
 *   membuat "50,000" terbaca 50 (lima puluh) alih-alih 50000. Diperbaiki di
 *   sini supaya kedua separator konsisten sebagai pemisah ribuan.
 */
object AmountParser {

    private val jutaRegex = Regex("""^([\d.,]+)\s*j(?:t|uta)?$""")
    private val ribuRegex = Regex("""^([\d.,]+)\s*(?:rb|ribu|k)$""")

    fun parse(input: String): Long? {
        if (input.isBlank()) return null
        var clean = input.trim().lowercase()
            .replace("_", "")
            .replace(" ", "")
            .replace("rp", "")

        jutaRegex.find(clean)?.let { match ->
            val num = parseDecimal(match.groupValues[1]) ?: return null
            return (num * 1_000_000).toLong().takeIf { it > 0 }
        }

        ribuRegex.find(clean)?.let { match ->
            val num = parseDecimal(match.groupValues[1]) ?: return null
            return (num * 1_000).toLong().takeIf { it > 0 }
        }

        // Tanpa akhiran jt/rb/k: titik dan koma SELALU pemisah ribuan, tidak
        // pernah desimal — lihat penjelasan di dokumentasi kelas.
        clean = clean.replace(".", "").replace(",", "")
        return clean.toLongOrNull()?.takeIf { it > 0 }
    }

    private fun parseDecimal(input: String): Double? =
        input.replace(",", ".").toDoubleOrNull()
}
