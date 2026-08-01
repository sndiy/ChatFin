package com.sndiy.chatfin.core.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class PeriodRangeTest {

    // ── thisMonth() ───────────────────────────────────────────────────────────

    @Test fun `thisMonth dari tanggal 31 Januari`() {
        val today = LocalDate.of(2026, 1, 31)
        val (start, end) = PeriodRange.thisMonth(today)
        assertEquals(LocalDate.of(2026, 1, 1), start)
        assertEquals(today, end)
    }

    @Test fun `thisMonth dari 29 Februari tahun kabisat`() {
        val today = LocalDate.of(2024, 2, 29)
        val (start, end) = PeriodRange.thisMonth(today)
        assertEquals(LocalDate.of(2024, 2, 1), start)
        assertEquals(today, end)
    }

    // ── lastMonth() — titik paling rawan salah hitung ───────────────────────

    @Test fun `lastMonth dari Maret ke Februari non-kabisat berakhir 28`() {
        val today = LocalDate.of(2026, 3, 15)
        val (start, end) = PeriodRange.lastMonth(today)
        assertEquals(LocalDate.of(2026, 2, 1), start)
        assertEquals(LocalDate.of(2026, 2, 28), end)
    }

    @Test fun `lastMonth dari Maret ke Februari kabisat berakhir 29`() {
        val today = LocalDate.of(2024, 3, 15)
        val (start, end) = PeriodRange.lastMonth(today)
        assertEquals(LocalDate.of(2024, 2, 1), start)
        assertEquals(LocalDate.of(2024, 2, 29), end)
    }

    @Test fun `lastMonth dari Januari mundur ke Desember tahun sebelumnya`() {
        val today = LocalDate.of(2026, 1, 15)
        val (start, end) = PeriodRange.lastMonth(today)
        assertEquals(LocalDate.of(2025, 12, 1), start)
        assertEquals(LocalDate.of(2025, 12, 31), end)
    }

    @Test fun `lastMonth dari tanggal 31 ke bulan yang lebih pendek`() {
        // today = 31 Maret -> bulan lalu Februari. withDayOfMonth(1) menghindari
        // overflow tanggal (tidak ada 31 Februari).
        val today = LocalDate.of(2026, 3, 31)
        val (start, end) = PeriodRange.lastMonth(today)
        assertEquals(LocalDate.of(2026, 2, 1), start)
        assertEquals(LocalDate.of(2026, 2, 28), end)
    }

    // ── lastNMonths() ─────────────────────────────────────────────────────────

    @Test fun `lastNMonths 3 bulan mundur lintas tahun`() {
        val today = LocalDate.of(2026, 2, 10)
        val (start, end) = PeriodRange.lastNMonths(3, today)
        assertEquals(LocalDate.of(2025, 11, 1), start)
        assertEquals(today, end)
    }

    @Test fun `lastNMonths 6 bulan mundur lintas tahun`() {
        val today = LocalDate.of(2026, 3, 1)
        val (start, end) = PeriodRange.lastNMonths(6, today)
        assertEquals(LocalDate.of(2025, 9, 1), start)
        assertEquals(today, end)
    }

    @Test fun `lastNMonths dengan today tanggal 31 tidak overflow ke bulan target`() {
        val today = LocalDate.of(2026, 5, 31)
        val (start, end) = PeriodRange.lastNMonths(3, today)
        assertEquals(LocalDate.of(2026, 2, 1), start) // Feb tidak overflow karena withDayOfMonth(1)
        assertEquals(today, end)
    }

    // ── thisYear() ────────────────────────────────────────────────────────────

    @Test fun `thisYear dari pertengahan tahun`() {
        val today = LocalDate.of(2026, 7, 29)
        val (start, end) = PeriodRange.thisYear(today)
        assertEquals(LocalDate.of(2026, 1, 1), start)
        assertEquals(today, end)
    }

    @Test fun `thisYear dari 1 Januari`() {
        val today = LocalDate.of(2026, 1, 1)
        val (start, end) = PeriodRange.thisYear(today)
        assertEquals(today, start)
        assertEquals(today, end)
    }

    // ── since() ───────────────────────────────────────────────────────────────

    @Test fun `since mengembalikan pasangan persis start dan today`() {
        val start = LocalDate.of(2020, 1, 1)
        val today = LocalDate.of(2026, 7, 29)
        val (resultStart, resultEnd) = PeriodRange.since(start, today)
        assertEquals(start, resultStart)
        assertEquals(today, resultEnd)
    }
}
