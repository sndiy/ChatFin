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

    // CATATAN: "jajan" SENGAJA tidak ada di sini. Kata itu juga terdaftar
    // sebagai kata kunci kategori exp_food di DefaultKeywords, jadi kalau ikut
    // dihitung sebagai kata kerja pengeluaran, kalimat pemasukan yang wajar
    // seperti "saya mendapatkan uang jajan 15rb" akan terdeteksi EXPENSE dengan
    // confidence 1.0 — dan karena kategorinya ikut ketemu, user tidak pernah
    // ditawari daftar kategori untuk mengoreksinya. Sebagai kata benda, "jajan"
    // cukup berperan lewat kamus kategori saja.
    private val expenseVerbs = setOf(
        "beli", "bayar", "habis", "keluar", "belanja", "spend"
    )
    private val incomeVerbs = setOf(
        "gaji", "gajian", "terima", "nerima", "dapat", "dapet", "bonus", "masuk",
        "setor", "nyetor", "untung", "cuan"
    )

    // Imbuhan yang hanya mengubah bentuk, bukan arah uangnya: "mendapatkan" →
    // "dapat", "membeli" → "beli", "dapetin" → "dapet". Awalan pasif "di-" dan
    // "ter-" SENGAJA tidak ikut karena justru membalik arah ("dibayar" =
    // menerima uang, bukan mengeluarkan).
    private val verbPrefixes = listOf("meng", "meny", "mem", "men", "nge", "me")
    private val verbSuffixes = listOf("kan", "in", "nya")
    private val nasalRestore = mapOf("meng" to "k", "meny" to "s", "mem" to "p", "men" to "t")

    // Kata depan yang lazim mendahului nama dompet ("dari BCA", "pakai GoPay").
    // "dengan" sengaja tidak ikut karena terlalu sering dipakai untuk hal lain.
    private val walletPrepositions = setOf("dari", "pakai", "pake", "via", "lewat")

    private enum class VerbSignal { EXPENSE, INCOME, AMBIGUOUS, NONE }

    fun parse(input: String, keywordSource: KeywordSource = DefaultKeywords): ParseResult {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return ParseResult.NotATransaction

        val tokens = trimmed.split(Regex("\\s+"))
        val amountMatch = extractAmount(tokens) ?: return ParseResult.NotATransaction

        val afterAmount = tokens.filterIndexed { idx, _ ->
            idx !in amountMatch.startTokenIdx..amountMatch.endTokenIdx
        }

        // Frasa dompet dibuang dari sisa teks supaya nama dompet tidak ikut
        // dicocokkan sebagai kata kunci kategori dan tidak mengotori judul.
        val walletMatch     = extractWalletHint(afterAmount)
        val remainingTokens = if (walletMatch == null) afterAmount else {
            afterAmount.filterIndexed { idx, _ -> idx !in walletMatch.startTokenIdx..walletMatch.endTokenIdx }
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
            title = title,
            walletHint = walletMatch?.hint
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

    private data class WalletMatch(val hint: String, val startTokenIdx: Int, val endTokenIdx: Int)

    /**
     * Dua lintasan, bukan satu: lintasan pertama hanya menerima token yang
     * JELAS nominal uang (berawalan "rp", berakhiran rb/ribu/jt/juta/k, atau
     * nilainya >= 1000), lintasan kedua baru menerima angka telanjang apa pun.
     *
     * Sebelumnya hanya ada satu lintasan "angka pertama yang ketemu menang",
     * sehingga "beli 2 kopi 30rb" terbaca sebagai transaksi Rp 2 — kuantitas
     * dikira nominal. Dengan urutan ini, "30rb" menang atas "2" tanpa perlu
     * mengorbankan kalimat yang memang hanya menyebut angka polos ("kopi 15000").
     *
     * Di dalam tiap lintasan, pasangan token berdekatan dicoba lebih dulu
     * (menangkap "50 rb", "1.5 jt", "rp 50000" yang tertulis dengan spasi) baru
     * token tunggal. Karena AmountParser hanya mengembalikan angka kalau ada
     * digit yang valid, penggabungan token yang salah tidak pernah menghasilkan
     * false-positive (kata+kata tidak pernah punya digit).
     */
    private fun extractAmount(tokens: List<String>): AmountMatch? =
        findAmount(tokens, explicitOnly = true) ?: findAmount(tokens, explicitOnly = false)

    private fun findAmount(tokens: List<String>, explicitOnly: Boolean): AmountMatch? {
        for (i in tokens.indices) {
            if (i + 1 < tokens.size) {
                val merged = tokens[i] + tokens[i + 1]
                val value  = AmountParser.parse(merged)
                if (value != null && (!explicitOnly || isExplicitMoney(merged, value))) {
                    return AmountMatch(value, i, i + 1)
                }
            }
        }
        for (i in tokens.indices) {
            val value = AmountParser.parse(tokens[i])
            if (value != null && (!explicitOnly || isExplicitMoney(tokens[i], value))) {
                return AmountMatch(value, i, i)
            }
        }
        return null
    }

    /** Hanya dipanggil untuk token yang AmountParser sudah berhasil baca. */
    private fun isExplicitMoney(token: String, value: Long): Boolean {
        val t = token.lowercase().trim()
        return t.startsWith("rp") ||
            t.endsWith("rb") || t.endsWith("ribu") ||
            t.endsWith("jt") || t.endsWith("juta") || t.endsWith("k") ||
            value >= 1000L
    }

    /**
     * Ambil nama dompet yang disebut lewat kata depan ("... dari BCA",
     * "... pakai GoPay"). Kata depan TERAKHIR yang dipakai, supaya kalimat
     * seperti "beli kopi dari warung pakai GoPay" mengambil "GoPay".
     * Maksimal 3 token supaya tidak menelan sisa kalimat.
     */
    private fun extractWalletHint(tokens: List<String>): WalletMatch? {
        val prepIdx = tokens.indexOfLast { it.lowercase() in walletPrepositions }
        if (prepIdx == -1 || prepIdx == tokens.lastIndex) return null
        val endIdx = minOf(prepIdx + 3, tokens.lastIndex)
        val hint   = tokens.subList(prepIdx + 1, endIdx + 1).joinToString(" ").trim()
        return if (hint.isBlank()) null else WalletMatch(hint, prepIdx, endIdx)
    }

    private fun detectVerbSignal(tokens: List<String>): VerbSignal {
        val roots = tokens.flatMap { verbForms(it) }.toSet()
        val hasExpenseVerb = roots.any { it in expenseVerbs }
        val hasIncomeVerb = roots.any { it in incomeVerbs }
        return when {
            hasExpenseVerb && hasIncomeVerb -> VerbSignal.AMBIGUOUS
            hasExpenseVerb -> VerbSignal.EXPENSE
            hasIncomeVerb -> VerbSignal.INCOME
            else -> VerbSignal.NONE
        }
    }

    /**
     * Semua bentuk dasar yang mungkin dari satu token, hasil pengupasan awalan
     * lalu akhiran. Bahasa Indonesia sehari-hari menempelkan imbuhan dengan
     * bebas ("mendapatkan", "dapetin", "membeli"), sementara pencocokan lama
     * menuntut token persis sama dengan isi kamus — sehingga "mendapatkan"
     * tidak pernah terbaca sebagai pemasukan.
     */
    private fun verbForms(token: String): Set<String> {
        val base   = token.lowercase()
        val stems  = mutableSetOf(base)
        for (prefix in verbPrefixes) {
            if (base.length > prefix.length + 2 && base.startsWith(prefix)) {
                val rest = base.removePrefix(prefix)
                stems += rest
                // Peluluhan huruf awal: keluar → mengeluarkan, terima → menerima,
                // pakai → memakai, setor → menyetor. Kandidat tambahan saja —
                // bentuk yang tidak masuk akal tidak akan cocok dengan kamus.
                nasalRestore[prefix]?.let { stems += it + rest }
            }
        }
        val forms = stems.toMutableSet()
        for (stem in stems) {
            for (suffix in verbSuffixes) {
                if (stem.length > suffix.length + 2 && stem.endsWith(suffix)) {
                    forms += stem.removeSuffix(suffix)
                }
            }
        }
        return forms
    }
}
