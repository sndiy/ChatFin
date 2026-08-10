package com.sndiy.chatfin.core.parser

import java.time.DayOfWeek
import java.time.LocalDate

/**
 * Parser rule-based murni untuk mengenali PERMINTAAN MELIHAT transaksi
 * ("tampilkan transaksi bulan ini", "kasih tahu transaksi terakhir") — bukan
 * mencatat transaksi baru (lihat [TransactionParser] untuk itu).
 *
 * Tidak ada dependency Android/AI, jadi hasilnya SAMA PERSIS di jalur online
 * (AI) maupun offline (bot mode) — dipanggil langsung dari
 * ChatViewModel.routeMessage sebelum percabangan online/offline.
 *
 * DUA JALUR, SATU SUMBER KEBENARAN:
 *  - [parse] menerjemahkan kalimat bebas user (jalur cepat/offline).
 *  - [fromKeyword] menerjemahkan kode periode yang DIMINTA AI lewat
 *    [CHATFIN_OPTIONS] (lihat SystemPromptBuilder) — dipakai untuk kalimat yang
 *    cuma bisa dipahami dari konteks percakapan ("oke tampilkan", "iya boleh"),
 *    yang mustahil ditangkap pencocokan kata kunci.
 * Keduanya bermuara ke [resolve] supaya rentang tanggalnya tidak pernah beda
 * tafsir antara jalur lokal dan jalur AI.
 */
object TransactionQueryParser {

    data class QueryResult(
        val periodLabel: String,
        val startDate: LocalDate,
        val endDate: LocalDate,
        /** Batas jumlah baris yang ditampilkan; null = pakai default kartu. */
        val limit: Int? = null,
        /** Filter opsional. null = tidak disaring pada dimensi itu. */
        val categoryName: String? = null,
        val walletName: String? = null,
        /** INCOME | EXPENSE */
        val type: String? = null
    )

    /** Kode periode baku — nilai `period` yang boleh dikirim AI. */
    enum class Period {
        TODAY, YESTERDAY, THIS_WEEK, LAST_WEEK,
        THIS_MONTH, LAST_MONTH, THIS_YEAR, LAST_YEAR,
        LATEST, ALL
    }

    private val viewVerbs = setOf(
        "tampilkan", "tunjukkan", "lihat", "liat", "cek", "tampilin", "kasih lihat",
        "beritahu", "kasih tau", "kasih tahu", "kabari", "kabarin", "info", "infoin",
        "sebutkan", "sebutin", "daftar", "list", "mana", "apa aja", "apa saja"
    )
    private val txNouns = setOf(
        "transaksi", "transaksinya", "riwayat", "catatan",
        "pengeluaran", "pemasukan", "belanja"
    )

    /** "3 hari terakhir", "2 minggu terakhir", "6 bulan belakangan". */
    private val lastNPattern = Regex("""(\d+)\s*(hari|minggu|bulan)\s+(terakhir|belakangan)""")

    /** Dipakai untuk "terakhir"/"semua" yang tidak punya batas awal alami. */
    private val EPOCH: LocalDate = LocalDate.of(1970, 1, 1)

    /** Jumlah baris untuk permintaan "transaksi terakhir" tanpa angka eksplisit. */
    private const val DEFAULT_LATEST_LIMIT = 5

    /** Kata yang menunjuk sesuatu dari giliran percakapan SEBELUMNYA. */
    private val contextRefPattern = Regex(
        """\b(itu|tersebut|tsb|tadi|barusan|khusus|sebelumnya|yang kamu sebut)\b"""
    )

    /**
     * Kata pertama nama kategori/dompet yang terlalu umum untuk dijadikan
     * penanda — "Dompet Harian" tidak boleh cocok hanya karena kalimatnya
     * menyebut "dompet".
     */
    private val genericNameHeads = setOf(
        "dompet", "kas", "bank", "rekening", "kartu", "uang", "saldo",
        "lainnya", "transaksi", "biaya", "dana"
    )

    /**
     * true kalau kalimatnya menunjuk sesuatu yang cuma bisa dipahami dari
     * percakapan sebelumnya ("tampilkan khusus transaksi ITU"). Pencocokan kata
     * kunci tidak akan pernah bisa menyelesaikan ini — rujukannya ada di giliran
     * sebelumnya, yang cuma dipegang AI. Dipakai ChatViewModel untuk memutuskan
     * mundur dan membiarkan AI yang menafsirkan (selama masih online).
     */
    fun refersToContext(input: String): Boolean =
        contextRefPattern.containsMatchIn(input.trim().lowercase())

    // Sama persis daftar yang dijanjikan ke AI di SystemPromptBuilder ("grafik,
    // chart, diagram, visualisasi, atau tabel") — satu sumber kebenaran supaya
    // definisi "eksplisit minta viz" tidak mencong antara guard ini dan prompt.
    private val vizWordPattern = Regex("""\b(grafik|chart|diagram|visualisasi|tabel|table)\b""")

    /**
     * true kalau kalimatnya EKSPLISIT minta grafik/tabel. Dicek di ChatViewModel
     * SEBELUM [parse] — tanpa ini, kalimat seperti "lihat grafik untuk kategori
     * belanja" ikut cocok [parse] (viewVerb "lihat" + txNoun "belanja") dan
     * dibajak jadi kartu daftar transaksi, padahal kata "grafik" tidak pernah
     * dicek sama sekali oleh parser ini — AI/BotModeHandler yang sebenarnya
     * paham permintaan grafik tidak pernah kebagian giliran.
     */
    fun isExplicitVisualizationRequest(input: String): Boolean =
        vizWordPattern.containsMatchIn(input.trim().lowercase())

    fun resolve(period: Period, today: LocalDate = LocalDate.now()): QueryResult = when (period) {
        Period.TODAY      -> QueryResult("Hari Ini", today, today)
        Period.YESTERDAY  -> QueryResult("Kemarin", today.minusDays(1), today.minusDays(1))
        Period.THIS_WEEK  -> QueryResult("Minggu Ini", today.with(DayOfWeek.MONDAY), today)
        Period.LAST_WEEK  -> today.minusWeeks(1).let {
            QueryResult("Minggu Lalu", it.with(DayOfWeek.MONDAY), it.with(DayOfWeek.SUNDAY))
        }
        Period.THIS_MONTH -> QueryResult("Bulan Ini", today.withDayOfMonth(1), today)
        Period.LAST_MONTH -> today.minusMonths(1).let {
            QueryResult("Bulan Lalu", it.withDayOfMonth(1), it.withDayOfMonth(it.lengthOfMonth()))
        }
        Period.THIS_YEAR  -> QueryResult("Tahun Ini", today.withDayOfYear(1), today)
        Period.LAST_YEAR  -> today.minusYears(1).let {
            QueryResult("Tahun Lalu", it.withDayOfYear(1), it.withDayOfYear(it.lengthOfYear()))
        }
        Period.LATEST     -> QueryResult("Terakhir", EPOCH, today, limit = DEFAULT_LATEST_LIMIT)
        Period.ALL        -> QueryResult("Keseluruhan", EPOCH, today)
    }

    /** Kode tak dikenal dari AI sengaja jatuh ke THIS_MONTH, bukan null — kartu
     *  dengan periode wajar jauh lebih berguna daripada tidak ada kartu. */
    fun fromKeyword(raw: String, today: LocalDate = LocalDate.now()): QueryResult {
        val match = Period.values().firstOrNull { it.name.equals(raw.trim(), ignoreCase = true) }
        return resolve(match ?: Period.THIS_MONTH, today)
    }

    /**
     * [knownCategories]/[knownWallets] dikirim pemanggil (ChatViewModel) supaya
     * parser bisa mengenali "tampilkan transaksi makanan bulan ini" tanpa
     * menanam nama kategori apa pun di kode — daftarnya milik akun user.
     */
    fun parse(
        input: String,
        today: LocalDate = LocalDate.now(),
        knownCategories: List<String> = emptyList(),
        knownWallets: List<String> = emptyList()
    ): QueryResult? {
        val lower = input.trim().lowercase()
        if (lower.isBlank()) return null

        if (txNouns.none { lower.contains(it) }) return null
        if (viewVerbs.none { lower.contains(it) }) return null

        val filters: QueryResult.() -> QueryResult = {
            copy(
                categoryName = matchName(lower, knownCategories),
                walletName   = matchName(lower, knownWallets),
                type         = detectType(lower)
            )
        }

        // Pola berangka dicek DULUAN: "3 bulan terakhir" juga mengandung kata
        // "terakhir", jadi kalau cabang LATEST jalan lebih dulu, rentang 3 bulan
        // itu diam-diam menyusut jadi 5 transaksi terakhir.
        lastNPattern.find(lower)?.let { match ->
            val n = match.groupValues[1].toIntOrNull()
            if (n != null && n > 0) {
                val capped = n.coerceAtMost(3650)
                val unit   = match.groupValues[2]
                val start  = when (unit) {
                    "hari"   -> today.minusDays((capped - 1).toLong())
                    "minggu" -> today.minusWeeks(capped.toLong()).plusDays(1)
                    else     -> today.minusMonths(capped.toLong()).plusDays(1)
                }
                val unitLabel = unit.replaceFirstChar { it.uppercase() }
                return QueryResult("$capped $unitLabel Terakhir", start, today).filters()
            }
        }

        val period = when {
            lower.contains("hari ini")     -> Period.TODAY
            lower.contains("kemarin")      -> Period.YESTERDAY
            lower.contains("minggu lalu")  -> Period.LAST_WEEK
            lower.contains("minggu ini")   -> Period.THIS_WEEK
            lower.contains("bulan lalu")   -> Period.LAST_MONTH
            lower.contains("bulan ini")    -> Period.THIS_MONTH
            lower.contains("tahun lalu")   -> Period.LAST_YEAR
            lower.contains("tahun ini")    -> Period.THIS_YEAR
            lower.contains("semua") ||
                lower.contains("keseluruhan") -> Period.ALL
            lower.contains("terakhir") ||
                lower.contains("terbaru")     -> Period.LATEST
            // Periode TIDAK disebut sama sekali ("tampilkan transaksi").
            // Dulu ini `null` dan permintaannya diteruskan ke AI, yang lalu cuma
            // menyuruh user mengetik ulang dengan periode — padahal maksudnya
            // sudah jelas. Bulan berjalan adalah tebakan paling masuk akal.
            else -> Period.THIS_MONTH
        }
        return resolve(period, today).filters()
    }

    /** Satu nama saja — dipakai [parse] untuk [QueryResult.categoryName]/[QueryResult.walletName]
     *  (String tunggal, sudah teruji). Untuk chart/tabel yang mendukung banyak
     *  filter sekaligus ("belanja dan makanan"), pakai [matchAllNames]. */
    private fun matchName(lower: String, names: List<String>): String? =
        matchAllNames(lower, names).firstOrNull()

    /**
     * Semua nama yang cocok, bukan cuma yang pertama — dipakai ChatOptionsParser
     * (jalur AI) dan BotModeHandler (jalur offline) untuk mengisi
     * `categoryNames`/`walletNames` di permintaan grafik/tabel, supaya
     * "kategori belanja dan makanan" menghasilkan DUA nama, bukan satu.
     *
     * Tiap nama dicek SENDIRI-SENDIRI (persis dulu, kata pertama sebagai
     * cadangan) — BUKAN "kalau ada satu yang cocok persis, matikan cadangan
     * untuk semua nama lain". Versi awal fungsi ini memang begitu (warisan dari
     * [matchName] yang cuma butuh SATU hasil terbaik), dan itu bikin
     * "belanja dan makanan" cuma ketemu "Belanja" — begitu "Belanja" cocok
     * persis, "Makanan & Minuman" (yang perlu jalur cadangan karena namanya
     * "Makanan & Minuman", bukan cuma "Makanan") ikut kena matikan.
     */
    fun matchAllNames(lower: String, names: List<String>): List<String> = names.filter { name ->
        lower.contains(name.lowercase()) || run {
            val head = name.lowercase().substringBefore(' ').trim()
            head.length >= 4 && head !in genericNameHeads && lower.contains(head)
        }
    }

    /** Non-private supaya BotModeHandler (jalur offline) memakai aturan
     *  "pengeluaran"/"pemasukan" yang SAMA PERSIS dengan jalur AI — kalau
     *  didefinisikan ulang di dua tempat, keduanya bisa diam-diam berbeda
     *  setelah salah satu diedit. */
    fun detectType(lower: String): String? = when {
        lower.contains("pengeluaran") || lower.contains("pemborosan") -> "EXPENSE"
        lower.contains("pemasukan") || lower.contains("penghasilan")  -> "INCOME"
        else -> null
    }
}
