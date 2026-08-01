package com.sndiy.chatfin.core.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AmountParserTest {

    // ── Angka polos ──────────────────────────────────────────────────────────

    @Test fun `angka polos tanpa pemisah`() {
        assertEquals(50000L, AmountParser.parse("50000"))
    }

    @Test fun `angka polos satu digit`() {
        assertEquals(5L, AmountParser.parse("5"))
    }

    @Test fun `angka besar`() {
        assertEquals(999_999_999L, AmountParser.parse("999999999"))
    }

    @Test fun `dengan prefiks Rp`() {
        assertEquals(50000L, AmountParser.parse("Rp50000"))
    }

    @Test fun `dengan prefiks rp huruf kecil dan spasi`() {
        assertEquals(50000L, AmountParser.parse("rp 50000"))
    }

    @Test fun `dengan underscore sebagai pemisah`() {
        assertEquals(50000L, AmountParser.parse("50_000"))
    }

    @Test fun `dengan spasi di tengah`() {
        assertEquals(50000L, AmountParser.parse("50 000"))
    }

    // ── Titik/koma TANPA akhiran — SELALU pemisah ribuan, bukan desimal ───────
    // Ini bagian paling penting: rupiah tidak pernah desimal dalam pemakaian
    // sehari-hari, jadi baik titik maupun koma di sini dibuang, bukan diubah
    // jadi pemisah desimal.

    @Test fun `titik tanpa akhiran adalah pemisah ribuan`() {
        assertEquals(50000L, AmountParser.parse("50.000"))
    }

    @Test fun `koma tanpa akhiran JUGA pemisah ribuan, bukan desimal`() {
        // Sebelum diekstrak & diperbaiki: kode lama mengubah koma jadi titik lalu
        // menafsirkannya sebagai desimal, sehingga "50,000" terbaca 50 (lima
        // puluh) — bug nyata. Sekarang konsisten dengan titik: dibuang, jadi 50000.
        assertEquals(50000L, AmountParser.parse("50,000"))
    }

    @Test fun `titik ganda gaya ribuan internasional`() {
        assertEquals(1500000L, AmountParser.parse("1.500.000"))
    }

    @Test fun `titik tunggal tanpa akhiran dari kasus audit awal`() {
        // "50.5" terbaca 505 — ini BUKAN bug, ini konsekuensi konsisten dari
        // aturan "titik tanpa akhiran = pemisah ribuan". Didokumentasikan di
        // sini secara eksplisit supaya tidak berubah tanpa sengaja.
        assertEquals(505L, AmountParser.parse("50.5"))
    }

    @Test fun `koma tunggal tanpa akhiran setara dengan titik tunggal`() {
        assertEquals(505L, AmountParser.parse("50,5"))
    }

    // ── Akhiran ribu (rb / ribu / k) ────────────────────────────────────────

    @Test fun `akhiran rb`() {
        assertEquals(50000L, AmountParser.parse("50rb"))
    }

    @Test fun `akhiran ribu penuh`() {
        assertEquals(50000L, AmountParser.parse("50ribu"))
    }

    @Test fun `akhiran k`() {
        assertEquals(50000L, AmountParser.parse("50k"))
    }

    @Test fun `akhiran rb dengan desimal titik`() {
        assertEquals(1500L, AmountParser.parse("1.5rb"))
    }

    @Test fun `akhiran rb dengan desimal koma`() {
        assertEquals(1500L, AmountParser.parse("1,5rb"))
    }

    @Test fun `akhiran rb huruf besar`() {
        assertEquals(50000L, AmountParser.parse("50RB"))
    }

    // ── Akhiran juta (jt / juta) ─────────────────────────────────────────────

    @Test fun `akhiran jt`() {
        assertEquals(1_000_000L, AmountParser.parse("1jt"))
    }

    @Test fun `akhiran juta penuh`() {
        assertEquals(1_000_000L, AmountParser.parse("1juta"))
    }

    @Test fun `akhiran jt dengan desimal titik`() {
        assertEquals(1_500_000L, AmountParser.parse("1.5jt"))
    }

    @Test fun `akhiran jt dengan desimal koma`() {
        assertEquals(1_500_000L, AmountParser.parse("1,5jt"))
    }

    @Test fun `akhiran jt dengan angka besar`() {
        assertEquals(15_000_000L, AmountParser.parse("15jt"))
    }

    // ── Input tidak valid ─────────────────────────────────────────────────────

    @Test fun `string kosong menghasilkan null`() {
        assertNull(AmountParser.parse(""))
    }

    @Test fun `string blank menghasilkan null`() {
        assertNull(AmountParser.parse("   "))
    }

    @Test fun `teks non-angka menghasilkan null`() {
        assertNull(AmountParser.parse("abc"))
    }

    @Test fun `nol menghasilkan null karena tidak positif`() {
        assertNull(AmountParser.parse("0"))
    }

    @Test fun `angka negatif menghasilkan null`() {
        assertNull(AmountParser.parse("-5000"))
    }

    @Test fun `akhiran tanpa angka menghasilkan null`() {
        assertNull(AmountParser.parse("rb"))
    }

    @Test fun `campuran huruf dan angka tidak dikenali menghasilkan null`() {
        assertNull(AmountParser.parse("lima ribu"))
    }
}
