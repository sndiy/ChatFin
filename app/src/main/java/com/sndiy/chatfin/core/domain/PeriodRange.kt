package com.sndiy.chatfin.core.domain

import java.time.LocalDate

/**
 * Kalkulasi rentang tanggal untuk periode umum (bulan ini, bulan lalu, N bulan
 * terakhir, tahun ini, sejak tanggal tertentu) — murni fungsi tanggal, tidak
 * bergantung pada enum spesifik layar manapun.
 *
 * Diekstrak dari duplikasi hampir identik di DashboardViewModel.periodRange()
 * dan ExportViewModel.periodToRange(). Kedua ViewModel tetap punya enum
 * masing-masing (AnalyticsPeriod, ExportPeriod) — hanya kalkulasi tanggalnya
 * yang disatukan di sini, supaya bug pada logika tanggal (mis. salah hitung
 * akhir bulan) hanya perlu diperbaiki satu tempat.
 *
 * Parameter `today` punya default `LocalDate.now()` tapi bisa dioverride —
 * ini yang membuat fungsi-fungsi ini bisa diuji secara deterministik tanpa
 * bergantung pada tanggal sistem saat test dijalankan.
 */
object PeriodRange {

    fun thisMonth(today: LocalDate = LocalDate.now()): Pair<LocalDate, LocalDate> =
        today.withDayOfMonth(1) to today

    fun lastMonth(today: LocalDate = LocalDate.now()): Pair<LocalDate, LocalDate> {
        val start = today.minusMonths(1).withDayOfMonth(1)
        return start to start.plusMonths(1).minusDays(1)
    }

    /** N bulan terakhir sampai hari ini, mis. lastNMonths(3) = 3 bulan terakhir s/d hari ini. */
    fun lastNMonths(n: Long, today: LocalDate = LocalDate.now()): Pair<LocalDate, LocalDate> =
        today.minusMonths(n).withDayOfMonth(1) to today

    fun thisYear(today: LocalDate = LocalDate.now()): Pair<LocalDate, LocalDate> =
        today.withDayOfYear(1) to today

    fun since(start: LocalDate, today: LocalDate = LocalDate.now()): Pair<LocalDate, LocalDate> =
        start to today
}
