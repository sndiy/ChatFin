package com.sndiy.chatfin.core.parser

/**
 * Parser rule-based murni untuk mengenali transaksi dari satu kalimat chat
 * (mis. "jajan 15rb", "gaji 5jt"). Tidak ada dependency Android/AI — bisa
 * dites tanpa emulator, dan menjadi jalur utama Target 1 (aplikasi jalan
 * tanpa API key). BELUM dipasang ke ChatViewModel (rencananya di M7); parser
 * juga TIDAK PERNAH menyimpan apa pun sendiri — hanya menghasilkan draft yang
 * wajib dikonfirmasi user sebelum disimpan.
 *
 * Pipeline:
 *  1. Tokenisasi berdasarkan spasi.
 *  2. Ekstrak nominal (lihat [extractAmount]) — kalau tidak ketemu sama
 *     sekali, kalimat dianggap BUKAN transaksi (kemungkinan besar pertanyaan).
 *  3. Deteksi kata kerja eksplisit (beli/bayar → EXPENSE, gaji/terima →
 *     INCOME). Tiga kemungkinan: (a) satu sisi jelas → tipe itu yang dipakai
 *     DAN pencarian kategori dibatasi ke tipe itu saja; (b) dua-duanya
 *     ketemu sekaligus (bentrok, mis. "bayar gaji karyawan") → default
 *     EXPENSE TANPA mencoba menebak kategori sama sekali; (c) tidak ada kata
 *     kerja sama sekali → coba kategori di kedua tipe, biarkan kategori yang
 *     ketemu menentukan tipe (lihat [resolveByCategory]).
 *  4. Judul = sisa teks setelah token nominal dibuang.
 *  5. Skor keyakinan: 0.5 dasar (nominal ketemu) + 0.25 kalau kata kerja
 *     eksplisit dan tidak bentrok + 0.25 kalau kategori cocok.
 *
 * Keterbatasan yang disengaja (bukan bug): tidak membedakan nominal transaksi
 * dari nominal lain dalam kalimat (target tabungan, tanggal, dsb) — itu
 * sebabnya kategori yang tidak cocok jatuh ke [ParseResult.Partial], bukan
 * ditebak, supaya kartu konfirmasi tetap meminta persetujuan user.
 */
object TransactionParser {

    private val expenseVerbs = setOf(
        "beli", "bayar", "jajan", "habis", "keluar", "keluarin", "belanja", "bayarin", "beliin"
    )
    private val incomeVerbs = setOf(
        "gaji", "gajian", "terima", "nerima", "dapat", "dapet", "bonus", "masuk", "setor", "nyetor"
    )

    private enum class VerbSignal { EXPENSE, INCOME, AMBIGUOUS, NONE }

    fun parse(input: String, keywordSource: KeywordSource = DefaultKeywords): ParseResult {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return ParseResult.NotATransaction

        val tokens = trimmed.split(Regex("\\s+"))
        val amountMatch = extractAmount(tokens) ?: return ParseResult.NotATransaction

        val remainingTokens = tokens.filterIndexed { idx, _ ->
            idx !in amountMatch.startTokenIdx..amountMatch.endTokenIdx
        }
        val remainingText = remainingTokens.joinToString(" ").trim()

        // Kata kerja eksplisit dan JELAS (tidak bentrok) menentukan tipe DAN
        // membatasi pencarian kategori ke tipe itu saja. Kalau bentrok (mis.
        // "bayar gaji" — "bayar" = expense, "gaji" = income) sengaja TIDAK
        // mencoba menebak kategori sama sekali, supaya kata kunci kategori
        // dari tipe yang salah tidak pernah "bocor" jadi jawaban yang salah.
        val verbSignal = detectVerbSignal(tokens)
        val (type, categoryMatch) = when (verbSignal) {
            VerbSignal.EXPENSE -> "EXPENSE" to keywordSource.findCategory(remainingText, "EXPENSE")
            VerbSignal.INCOME -> "INCOME" to keywordSource.findCategory(remainingText, "INCOME")
            VerbSignal.AMBIGUOUS -> "EXPENSE" to null
            VerbSignal.NONE -> resolveByCategory(remainingText, keywordSource)
        }

        val title = remainingText
            .ifBlank { if (type == "EXPENSE") "Pengeluaran" else "Pemasukan" }
            .replaceFirstChar { it.titlecase() }

        var confidence = 0.5f
        if (verbSignal == VerbSignal.EXPENSE || verbSignal == VerbSignal.INCOME) confidence += 0.25f
        if (categoryMatch != null) confidence += 0.25f

        val draft = ParsedDraft(
            type = type,
            amount = amountMatch.amount,
            categoryId = categoryMatch?.categoryId,
            categoryName = categoryMatch?.categoryName,
            title = title
        )

        return if (categoryMatch != null) {
            ParseResult.Complete(draft, confidence)
        } else {
            ParseResult.Partial(draft, listOf(TransactionField.CATEGORY))
        }
    }

    /**
     * Dipakai kalau tidak ada kata kerja sama sekali (mis. "freelance project
     * 3jt", "jualan online 500rb") — coba dua tipe lewat kategori, biarkan
     * kategori yang ketemu menentukan tipe. Kalau dua-duanya ketemu (ambigu)
     * atau dua-duanya tidak ketemu, default ke EXPENSE tanpa kategori.
     */
    private fun resolveByCategory(text: String, keywordSource: KeywordSource): Pair<String, CategoryMatch?> {
        val expenseMatch = keywordSource.findCategory(text, "EXPENSE")
        val incomeMatch = keywordSource.findCategory(text, "INCOME")
        return when {
            expenseMatch != null && incomeMatch == null -> "EXPENSE" to expenseMatch
            incomeMatch != null && expenseMatch == null -> "INCOME" to incomeMatch
            else -> "EXPENSE" to null
        }
    }

    private data class AmountMatch(val amount: Long, val startTokenIdx: Int, val endTokenIdx: Int)

    /**
     * Coba pasangan token berdekatan dulu (menangkap "50 rb", "1.5 jt", "rp 50000"
     * yang tertulis dengan spasi), baru token tunggal. Karena AmountParser hanya
     * mengembalikan angka kalau ada digit yang valid, penggabungan token yang
     * salah tidak akan pernah menghasilkan false-positive (kata+kata tidak
     * pernah punya digit).
     */
    private fun extractAmount(tokens: List<String>): AmountMatch? {
        for (i in tokens.indices) {
            if (i + 1 < tokens.size) {
                val merged = tokens[i] + tokens[i + 1]
                AmountParser.parse(merged)?.let { return AmountMatch(it, i, i + 1) }
            }
        }
        for (i in tokens.indices) {
            AmountParser.parse(tokens[i])?.let { return AmountMatch(it, i, i) }
        }
        return null
    }

    private fun detectVerbSignal(tokens: List<String>): VerbSignal {
        val lowerTokens = tokens.map { it.lowercase() }
        val hasExpenseVerb = lowerTokens.any { it in expenseVerbs }
        val hasIncomeVerb = lowerTokens.any { it in incomeVerbs }
        return when {
            hasExpenseVerb && hasIncomeVerb -> VerbSignal.AMBIGUOUS
            hasExpenseVerb -> VerbSignal.EXPENSE
            hasIncomeVerb -> VerbSignal.INCOME
            else -> VerbSignal.NONE
        }
    }
}
