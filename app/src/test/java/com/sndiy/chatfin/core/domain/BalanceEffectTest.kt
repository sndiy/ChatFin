package com.sndiy.chatfin.core.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class BalanceEffectTest {

    // ── apply() — efek saat transaksi BARU dicatat ──────────────────────────

    @Test fun `apply INCOME menambah saldo dompet tujuan`() {
        val result = BalanceEffect.apply("INCOME", "wallet-1", null, 50_000L)
        assertEquals(listOf(WalletDelta("wallet-1", 50_000L)), result)
    }

    @Test fun `apply EXPENSE mengurangi saldo dompet sumber`() {
        val result = BalanceEffect.apply("EXPENSE", "wallet-1", null, 30_000L)
        assertEquals(listOf(WalletDelta("wallet-1", -30_000L)), result)
    }

    @Test fun `apply TRANSFER mengurangi sumber dan menambah tujuan`() {
        val result = BalanceEffect.apply("TRANSFER", "wallet-1", "wallet-2", 100_000L)
        assertEquals(
            listOf(
                WalletDelta("wallet-1", -100_000L),
                WalletDelta("wallet-2", 100_000L)
            ),
            result
        )
    }

    @Test fun `apply TRANSFER tanpa toWalletId hanya mengurangi sumber`() {
        // Data rusak/tidak lengkap (mis. dompet tujuan sudah dihapus) — jangan
        // sampai melempar exception, cukup tidak ada efek di sisi tujuan.
        val result = BalanceEffect.apply("TRANSFER", "wallet-1", null, 100_000L)
        assertEquals(listOf(WalletDelta("wallet-1", -100_000L)), result)
    }

    @Test fun `apply tipe tidak dikenal menghasilkan daftar kosong`() {
        val result = BalanceEffect.apply("UNKNOWN", "wallet-1", null, 10_000L)
        assertEquals(emptyList<WalletDelta>(), result)
    }

    // ── rollback() — kebalikan matematis dari apply() ───────────────────────

    @Test fun `rollback INCOME mengurangi saldo yang sebelumnya ditambahkan`() {
        val result = BalanceEffect.rollback("INCOME", "wallet-1", null, 50_000L)
        assertEquals(listOf(WalletDelta("wallet-1", -50_000L)), result)
    }

    @Test fun `rollback EXPENSE mengembalikan saldo yang sebelumnya dikurangi`() {
        val result = BalanceEffect.rollback("EXPENSE", "wallet-1", null, 30_000L)
        assertEquals(listOf(WalletDelta("wallet-1", 30_000L)), result)
    }

    @Test fun `rollback TRANSFER membalik kedua sisi`() {
        val result = BalanceEffect.rollback("TRANSFER", "wallet-1", "wallet-2", 100_000L)
        assertEquals(
            listOf(
                WalletDelta("wallet-1", 100_000L),
                WalletDelta("wallet-2", -100_000L)
            ),
            result
        )
    }

    @Test fun `rollback TRANSFER tanpa toWalletId hanya membalik sumber`() {
        val result = BalanceEffect.rollback("TRANSFER", "wallet-1", null, 100_000L)
        assertEquals(listOf(WalletDelta("wallet-1", 100_000L)), result)
    }

    // ── Invarian: apply lalu rollback harus kembali nol bersih ──────────────

    @Test fun `apply diikuti rollback saling meniadakan untuk semua tipe`() {
        for (type in listOf("INCOME", "EXPENSE", "TRANSFER")) {
            val applied = BalanceEffect.apply(type, "wallet-1", "wallet-2", 77_000L)
            val rolledBack = BalanceEffect.rollback(type, "wallet-1", "wallet-2", 77_000L)

            val netByWallet = (applied + rolledBack)
                .groupBy { it.walletId }
                .mapValues { (_, deltas) -> deltas.sumOf { it.amount } }

            netByWallet.values.forEach { net -> assertEquals("tipe=$type", 0L, net) }
        }
    }
}
