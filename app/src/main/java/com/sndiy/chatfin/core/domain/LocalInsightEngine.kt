package com.sndiy.chatfin.core.domain

import java.text.NumberFormat
import java.time.LocalDate
import java.util.Locale

/**
 * Komentar & insight finansial murni lokal, tanpa AI — konsolidasi dari tiga
 * tempat yang sebelumnya masing-masing punya salinan sendiri:
 * DashboardViewModel.generateMaiInsight, SplashScreen.MAI_QUOTES, dan bagian
 * pemformatan teks di ChatViewModel.handleRangkuman. Tidak ada dependency
 * Android — bisa dites tanpa emulator, sama seperti core/parser/.
 */
object LocalInsightEngine {

    private val quotes = listOf(
        "Aturan 50/30/20: 50% kebutuhan, 30% keinginan, 20% simpanan.",
        "Dana darurat ideal adalah 3-6 bulan pengeluaran rutin.",
        "Catat pengeluaran 30 hari. Kau akan kaget sendiri.",
        "Bayar dirimu sendiri dulu, baru sisanya untuk yang lain.",
        "Investasi Rp 500rb/bulan selama 20 tahun bisa jadi Rp 400 juta.",
        "Disiplin keuangan itu seperti otot. Makin dilatih, makin kuat.",
        "Jangan beli karena diskon. Beli karena memang butuh.",
        "Uang diam itu menyusut. Inflasi tidak pernah libur.",
        "Net worth lebih penting dari gaji besar.",
        "Mulai dari sekarang. Besok sudah terlambat.",
        "Kopi 30rb sehari = Rp 10,9 juta per tahun. Kau sadar, kan?",
        "Cicilan rumah idealnya di bawah 30% pendapatan bulanan.",
        "80% masalah keuangan itu soal gaya hidup, bukan pendapatan.",
        "Diversifikasi aset itu bukan saran, itu keharusan.",
        "15 menit review keuangan per minggu sudah cukup."
    )

    fun randomQuote(): String = quotes.random()

    /** Dipakai SplashScreen untuk siklus kutipan tanpa pengulangan berdekatan. */
    fun shuffledQuotes(): List<String> = quotes.shuffled()

    /**
     * Komentar bergaya Mai atas kondisi pemasukan/pengeluaran bulan berjalan.
     * `balance` sengaja dipertahankan di signature walau tidak dipakai di
     * cabang manapun saat ini — perilaku persis sama dengan
     * DashboardViewModel.generateMaiInsight yang dikonsolidasi ke sini
     * (parameter tak terpakai ini sudah ada sebelum M7, dilaporkan terpisah,
     * bukan dibersihkan diam-diam di tengah konsolidasi).
     */
    fun spendingInsight(
        balance: Long,
        income: Long,
        expense: Long,
        today: LocalDate = LocalDate.now()
    ): String {
        val day = today.dayOfMonth
        val ratio = if (income > 0) (expense.toFloat() / income * 100).toInt() else 0

        return when {
            income == 0L && expense == 0L ->
                "*melirik* Belum ada transaksi bulan ini. ...kau serius mau begini terus?"
            ratio > 90 ->
                "*menghela napas* Pengeluaranmu $ratio% dari pemasukan. Aku khawatir... bukan berarti peduli, ya."
            ratio > 70 ->
                "*melipat tangan* $ratio% pemasukan sudah habis. Hati-hati, tanggal $day belum akhir bulan."
            ratio > 50 ->
                "*mengangkat alis* Setengah lebih pemasukanmu sudah terpakai. Lumayan terkendali."
            ratio > 30 ->
                "*tersenyum tipis* Baru $ratio% terpakai di tanggal $day. ...cukup bagus."
            expense > 0 ->
                "*membalik rambut* Pengeluaranmu masih rendah. Pertahankan."
            else ->
                "*melirik* Belum ada pengeluaran. Rajin juga kau membuka app ini."
        }
    }

    /** Teks ringkasan bulanan siap-tampil — pemanggil bertanggung jawab mengambil datanya (I/O tetap di ViewModel). */
    fun monthlySummary(
        monthYearLabel: String,
        income: Long,
        expense: Long,
        walletBalances: List<Pair<String, Long>>
    ): String {
        val fmt = NumberFormat.getNumberInstance(Locale("id", "ID"))
        fun rp(v: Long) = "Rp ${fmt.format(v)}"
        return buildString {
            appendLine("📊 *Rangkuman $monthYearLabel*")
            appendLine()
            appendLine("💚 Pemasukan  : ${rp(income)}")
            appendLine("❤️ Pengeluaran: ${rp(expense)}")
            appendLine("📈 Selisih    : ${rp(income - expense)}")
            appendLine()
            appendLine("💼 *Saldo per Dompet*")
            walletBalances.forEach { (name, balance) -> appendLine("• $name: ${rp(balance)}") }
            appendLine()
            append("Total: ${rp(walletBalances.sumOf { it.second })}")
        }.trim()
    }
}
