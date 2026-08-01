package com.sndiy.chatfin.core.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class LocalInsightEngineTest {

    // ── randomQuote / shuffledQuotes ─────────────────────────────────────────

    @Test fun `randomQuote tidak pernah kosong`() {
        assertTrue(LocalInsightEngine.randomQuote().isNotBlank())
    }

    @Test fun `shuffledQuotes mengembalikan semua kutipan tanpa duplikat atau hilang`() {
        val shuffled = LocalInsightEngine.shuffledQuotes()
        assertEquals(15, shuffled.size)
        assertEquals(15, shuffled.toSet().size)
    }

    // ── spendingInsight ───────────────────────────────────────────────────────

    @Test fun `belum ada transaksi sama sekali`() {
        val result = LocalInsightEngine.spendingInsight(0L, 0L, 0L)
        assertTrue(result.contains("Belum ada transaksi"))
    }

    @Test fun `rasio di atas 90 persen memicu peringatan keras`() {
        val result = LocalInsightEngine.spendingInsight(0L, 1_000_000L, 950_000L)
        assertTrue(result.contains("95%"))
    }

    @Test fun `rasio tepat 90 persen TIDAK masuk cabang di atas 90`() {
        // Perbatasan: ratio > 90 pakai strict greater-than, jadi 90 tepat jatuh
        // ke cabang "> 70" bukan "> 90" — didokumentasikan eksplisit di sini
        // supaya perilaku batas tidak berubah tanpa sengaja.
        val result = LocalInsightEngine.spendingInsight(0L, 1_000_000L, 900_000L)
        assertTrue(result.contains("90%"))
        assertTrue(result.contains("Hati-hati"))
    }

    @Test fun `rasio 75 persen masuk cabang 70`() {
        val result = LocalInsightEngine.spendingInsight(0L, 1_000_000L, 750_000L)
        assertTrue(result.contains("75%"))
        assertTrue(result.contains("Hati-hati"))
    }

    @Test fun `rasio 60 persen masuk cabang 50`() {
        val result = LocalInsightEngine.spendingInsight(0L, 1_000_000L, 600_000L)
        assertTrue(result.contains("Lumayan terkendali"))
    }

    @Test fun `rasio 40 persen masuk cabang 30`() {
        val result = LocalInsightEngine.spendingInsight(0L, 1_000_000L, 400_000L)
        assertTrue(result.contains("cukup bagus"))
    }

    @Test fun `rasio di bawah 30 persen jatuh ke cabang pengeluaran masih rendah`() {
        val result = LocalInsightEngine.spendingInsight(0L, 1_000_000L, 200_000L)
        assertTrue(result.contains("Pertahankan"))
    }

    @Test fun `income nol tapi expense ada tetap dianggap pengeluaran rendah`() {
        val result = LocalInsightEngine.spendingInsight(0L, 0L, 50_000L)
        assertTrue(result.contains("Pertahankan"))
    }

    @Test fun `income nol dan expense nol berbeda dari income ada expense nol`() {
        val hasIncomeOnly = LocalInsightEngine.spendingInsight(0L, 1_000_000L, 0L)
        assertTrue(hasIncomeOnly.contains("Rajin juga kau"))
    }

    @Test fun `tanggal dipakai untuk menyisipkan hari berjalan di pesan peringatan`() {
        val result = LocalInsightEngine.spendingInsight(
            balance = 0L, income = 1_000_000L, expense = 750_000L,
            today = LocalDate.of(2026, 7, 20)
        )
        assertTrue(result.contains("tanggal 20"))
    }

    // ── monthlySummary ────────────────────────────────────────────────────────

    @Test fun `monthlySummary memformat rupiah dengan pemisah ribuan titik`() {
        val result = LocalInsightEngine.monthlySummary(
            monthYearLabel = "Juli 2026",
            income = 5_000_000L,
            expense = 3_200_000L,
            walletBalances = listOf("Kas" to 1_800_000L)
        )
        assertTrue(result.contains("Rp 5.000.000"))
        assertTrue(result.contains("Rp 3.200.000"))
        assertTrue(result.contains("Rp 1.800.000"))
    }

    @Test fun `monthlySummary menghitung selisih income dikurangi expense`() {
        val result = LocalInsightEngine.monthlySummary(
            monthYearLabel = "Juli 2026",
            income = 5_000_000L,
            expense = 3_200_000L,
            walletBalances = emptyList()
        )
        assertTrue(result.contains("Rp 1.800.000")) // selisih = 1.8jt
    }

    @Test fun `monthlySummary menjumlahkan total saldo dari semua dompet`() {
        val result = LocalInsightEngine.monthlySummary(
            monthYearLabel = "Juli 2026",
            income = 0L,
            expense = 0L,
            walletBalances = listOf("Kas" to 100_000L, "Bank" to 900_000L)
        )
        assertTrue(result.contains("Total: Rp 1.000.000"))
    }

    @Test fun `monthlySummary mencantumkan nama tiap dompet`() {
        val result = LocalInsightEngine.monthlySummary(
            monthYearLabel = "Juli 2026",
            income = 0L,
            expense = 0L,
            walletBalances = listOf("Kas" to 100_000L, "Bank BCA" to 500_000L)
        )
        assertTrue(result.contains("• Kas: Rp 100.000"))
        assertTrue(result.contains("• Bank BCA: Rp 500.000"))
    }

    @Test fun `monthlySummary tanpa dompet tidak melempar exception`() {
        val result = LocalInsightEngine.monthlySummary(
            monthYearLabel = "Juli 2026",
            income = 0L,
            expense = 0L,
            walletBalances = emptyList()
        )
        assertTrue(result.contains("Total: Rp 0"))
    }

    @Test fun `monthlySummary mencantumkan label bulan yang diberikan`() {
        val result = LocalInsightEngine.monthlySummary(
            monthYearLabel = "Desember 2025",
            income = 0L,
            expense = 0L,
            walletBalances = emptyList()
        )
        assertTrue(result.contains("Rangkuman Desember 2025"))
    }
}
