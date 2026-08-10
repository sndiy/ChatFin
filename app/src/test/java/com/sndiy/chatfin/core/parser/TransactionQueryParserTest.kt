package com.sndiy.chatfin.core.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

class TransactionQueryParserTest {

    // Rabu, supaya "minggu ini" (Senin) beda dari "hari ini" — kasus paling
    // gampang salah kalau start-of-week dihitung ngawur.
    private val today = LocalDate.of(2026, 8, 5)

    @Test fun `hari ini`() {
        val result = TransactionQueryParser.parse("tampilkan transaksi hari ini", today)
        assertEquals(TransactionQueryParser.QueryResult("Hari Ini", today, today), result)
    }

    @Test fun `kemarin`() {
        val result = TransactionQueryParser.parse("lihat transaksi kemarin", today)
        assertEquals(
            TransactionQueryParser.QueryResult("Kemarin", today.minusDays(1), today.minusDays(1)),
            result
        )
    }

    @Test fun `minggu ini dimulai dari senin`() {
        val result = TransactionQueryParser.parse("cek transaksi minggu ini", today)
        assertEquals(DayOfWeek.MONDAY, result?.startDate?.dayOfWeek)
        assertEquals(today, result?.endDate)
    }

    @Test fun `bulan ini dimulai dari tanggal 1`() {
        val result = TransactionQueryParser.parse("tampilkan transaksi bulan ini", today)
        assertEquals(1, result?.startDate?.dayOfMonth)
        assertEquals(today, result?.endDate)
    }

    @Test fun `tahun ini dimulai dari 1 Januari`() {
        val result = TransactionQueryParser.parse("tunjukkan transaksi tahun ini", today)
        assertEquals(LocalDate.of(2026, 1, 1), result?.startDate)
        assertEquals(today, result?.endDate)
    }

    @Test fun `beritahu dikenali sebagai kata kerja melihat`() {
        val result = TransactionQueryParser.parse("beritahu transaksi minggu ini", today)
        assertEquals(DayOfWeek.MONDAY, result?.startDate?.dayOfWeek)
        assertEquals(today, result?.endDate)
    }

    @Test fun `kasih tau dikenali sebagai kata kerja melihat`() {
        assertEquals(
            TransactionQueryParser.QueryResult("Hari Ini", today, today),
            TransactionQueryParser.parse("kasih tau transaksi hari ini", today)
        )
    }

    // ── Periode lampau ───────────────────────────────────────────────────────

    @Test fun `minggu lalu adalah senin sampai minggu pekan sebelumnya`() {
        val result = TransactionQueryParser.parse("lihat transaksi minggu lalu", today)
        assertEquals(LocalDate.of(2026, 7, 27), result?.startDate) // Senin
        assertEquals(LocalDate.of(2026, 8, 2), result?.endDate)    // Minggu
    }

    @Test fun `bulan lalu adalah tanggal 1 sampai akhir bulan sebelumnya`() {
        val result = TransactionQueryParser.parse("lihat transaksi bulan lalu", today)
        assertEquals(LocalDate.of(2026, 7, 1), result?.startDate)
        assertEquals(LocalDate.of(2026, 7, 31), result?.endDate)
    }

    @Test fun `tahun lalu adalah 1 Jan sampai 31 Des tahun sebelumnya`() {
        val result = TransactionQueryParser.parse("lihat transaksi tahun lalu", today)
        assertEquals(LocalDate.of(2025, 1, 1), result?.startDate)
        assertEquals(LocalDate.of(2025, 12, 31), result?.endDate)
    }

    // ── "N satuan terakhir" ──────────────────────────────────────────────────

    @Test fun `7 hari terakhir termasuk hari ini`() {
        val result = TransactionQueryParser.parse("tampilkan transaksi 7 hari terakhir", today)
        assertEquals(today.minusDays(6), result?.startDate)
        assertEquals(today, result?.endDate)
    }

    /** "3 bulan terakhir" juga mengandung kata "terakhir" — pastikan tidak
     *  tertelan cabang "terakhir" polos yang cuma mengambil 5 baris. */
    @Test fun `3 bulan terakhir tidak tertukar dengan terakhir polos`() {
        val result = TransactionQueryParser.parse("tampilkan transaksi 3 bulan terakhir", today)
        assertEquals(today.minusMonths(3).plusDays(1), result?.startDate)
        assertNull(result?.limit)
    }

    // ── "terakhir" / "semua" ─────────────────────────────────────────────────

    @Test fun `transaksi terakhir dibatasi jumlahnya`() {
        val result = TransactionQueryParser.parse(
            "tolong kasih tahu transaksi terakhir di kolom chat ini untuk aku edit", today
        )
        assertEquals("Terakhir", result?.periodLabel)
        assertEquals(5, result?.limit)
        assertEquals(today, result?.endDate)
    }

    @Test fun `transaksi terbaru diperlakukan sama dengan terakhir`() {
        assertEquals(5, TransactionQueryParser.parse("lihat transaksi terbaru", today)?.limit)
    }

    @Test fun `semua transaksi memakai rentang penuh`() {
        val result = TransactionQueryParser.parse("tampilkan semua transaksi", today)
        assertEquals("Keseluruhan", result?.periodLabel)
        assertEquals(today, result?.endDate)
        assertNull(result?.limit)
    }

    // ── Kata benda alternatif ────────────────────────────────────────────────

    @Test fun `pengeluaran dikenali sebagai kata benda transaksi`() {
        assertEquals(
            "Bulan Ini",
            TransactionQueryParser.parse("lihat pengeluaran bulan ini", today)?.periodLabel
        )
    }

    @Test fun `tanpa kata kerja melihat bukan query`() {
        assertNull(TransactionQueryParser.parse("catat transaksi hari ini", today))
    }

    @Test fun `tanpa kata transaksi bukan query`() {
        assertNull(TransactionQueryParser.parse("tampilkan saldo hari ini", today))
    }

    /** Dulu ini `null` lalu diteruskan ke AI, yang cuma menyuruh user mengetik
     *  ulang lengkap dengan periode — padahal maksudnya sudah jelas. */
    @Test fun `tanpa periode jatuh ke bulan ini bukan ditolak`() {
        val result = TransactionQueryParser.parse("tampilkan transaksi", today)
        assertEquals("Bulan Ini", result?.periodLabel)
        assertEquals(1, result?.startDate?.dayOfMonth)
    }

    // ── Kode periode dari AI (fromKeyword) ───────────────────────────────────

    @Test fun `fromKeyword memberi rentang yang sama dengan parse`() {
        assertEquals(
            TransactionQueryParser.parse("tampilkan transaksi bulan ini", today),
            TransactionQueryParser.fromKeyword("THIS_MONTH", today)
        )
    }

    @Test fun `fromKeyword tidak peduli huruf besar kecil`() {
        assertEquals("Hari Ini", TransactionQueryParser.fromKeyword("today", today).periodLabel)
    }

    /** Kode ngawur dari AI harus tetap menghasilkan kartu — tanpa kartu, user
     *  balik ke keadaan lama: disuruh mengetik ulang. */
    @Test fun `fromKeyword kode tak dikenal jatuh ke bulan ini`() {
        assertEquals("Bulan Ini", TransactionQueryParser.fromKeyword("NGAWUR", today).periodLabel)
    }

    @Test fun `string kosong bukan query`() {
        assertNull(TransactionQueryParser.parse("", today))
    }

    // ── Filter kategori / dompet / tipe ──────────────────────────────────────

    private val categories = listOf("Makanan & Minuman", "Transportasi", "Belanja")
    private val wallets    = listOf("Dompet Harian", "BCA")

    @Test fun `nama kategori sebagian tetap cocok`() {
        val result = TransactionQueryParser.parse(
            "tampilkan transaksi makanan bulan ini", today, categories, wallets
        )
        assertEquals("Makanan & Minuman", result?.categoryName)
    }

    @Test fun `nama dompet persis dikenali`() {
        val result = TransactionQueryParser.parse(
            "lihat transaksi bulan ini di BCA", today, categories, wallets
        )
        assertEquals("BCA", result?.walletName)
    }

    /** "Dompet Harian" tidak boleh cocok hanya karena kalimatnya menyebut
     *  "dompet" — kata itu terlalu umum. */
    @Test fun `kata dompet yang generik tidak memicu filter dompet`() {
        val result = TransactionQueryParser.parse(
            "lihat transaksi dompet bulan ini", today, categories, wallets
        )
        assertNull(result?.walletName)
    }

    @Test fun `pengeluaran menyaring tipe EXPENSE`() {
        assertEquals(
            "EXPENSE",
            TransactionQueryParser.parse("lihat pengeluaran bulan ini", today, categories, wallets)?.type
        )
    }

    @Test fun `tanpa penyebutan filter semuanya null`() {
        val result = TransactionQueryParser.parse(
            "tampilkan transaksi bulan ini", today, categories, wallets
        )
        assertNull(result?.categoryName)
        assertNull(result?.walletName)
        assertNull(result?.type)
    }

    // ── Rujukan ke percakapan sebelumnya ─────────────────────────────────────

    // ── Guard grafik/tabel (Bagian 10) ───────────────────────────────────────

    @Test fun `permintaan grafik terdeteksi eksplisit`() {
        assertTrue(TransactionQueryParser.isExplicitVisualizationRequest(
            "lihat grafik hanya untuk kategori belanja dan makanan"
        ))
        assertTrue(TransactionQueryParser.isExplicitVisualizationRequest("buatkan tabel pengeluaran"))
        assertTrue(TransactionQueryParser.isExplicitVisualizationRequest("mau lihat diagram"))
    }

    @Test fun `permintaan transaksi biasa bukan permintaan visualisasi`() {
        assertFalse(TransactionQueryParser.isExplicitVisualizationRequest("tampilkan transaksi bulan ini"))
        assertFalse(TransactionQueryParser.isExplicitVisualizationRequest("beritahu transaksi minggu ini"))
    }

    @Test fun `matchAllNames mengembalikan lebih dari satu nama sekaligus`() {
        val result = TransactionQueryParser.matchAllNames(
            "lihat grafik hanya untuk kategori belanja dan makanan",
            listOf("Makanan & Minuman", "Transportasi", "Belanja")
        )
        assertEquals(setOf("Makanan & Minuman", "Belanja"), result.toSet())
    }

    @Test fun `matchAllNames kosong kalau tidak ada nama yang disebut`() {
        assertTrue(
            TransactionQueryParser.matchAllNames(
                "tampilkan grafik bulan ini",
                listOf("Makanan & Minuman", "Transportasi")
            ).isEmpty()
        )
    }

    @Test fun `kalimat yang menunjuk giliran sebelumnya terdeteksi`() {
        assertTrue(TransactionQueryParser.refersToContext("tampilkan khusus transaksi itu"))
        assertTrue(TransactionQueryParser.refersToContext("yang tadi saja"))
        assertTrue(TransactionQueryParser.refersToContext("rincian kategori tersebut"))
    }

    /** "bulan ini" mengandung kata "ini" — jangan sampai dikira rujukan konteks,
     *  karena itu justru perintah paling umum dan harus tetap ditangani lokal. */
    @Test fun `perintah biasa tidak dianggap merujuk konteks`() {
        assertFalse(TransactionQueryParser.refersToContext("tampilkan transaksi bulan ini"))
        assertFalse(TransactionQueryParser.refersToContext("lihat transaksi hari ini"))
    }
}
