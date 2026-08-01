package com.sndiy.chatfin.core.domain

/**
 * Perubahan saldo satu dompet. `amount` bisa negatif (kurangi) atau positif
 * (tambah) — TransactionRepository yang menerjemahkan ini ke pemanggilan
 * WalletDao.addToBalance/subtractFromBalance.
 */
data class WalletDelta(val walletId: String, val amount: Long)

/**
 * Menentukan efek sebuah transaksi terhadap saldo dompet — murni kalkulasi,
 * tanpa I/O, supaya bisa diuji tanpa Room/DAO sama sekali.
 *
 * Diekstrak dari TransactionRepository.applyBalanceEffect/rollbackBalanceEffect
 * yang sebelumnya langsung memanggil WalletDao di dalam badan fungsi — logika
 * KEPUTUSAN (dompet mana yang berubah, berapa, tanda apa) tercampur dengan
 * EKSEKUSI (panggilan DAO), sehingga tidak bisa diuji tanpa database nyata.
 */
object BalanceEffect {

    /** Delta saldo saat sebuah transaksi BARU dicatat (INCOME/EXPENSE/TRANSFER). */
    fun apply(type: String, walletId: String, toWalletId: String?, amount: Long): List<WalletDelta> =
        when (type) {
            "INCOME"   -> listOf(WalletDelta(walletId, amount))
            "EXPENSE"  -> listOf(WalletDelta(walletId, -amount))
            "TRANSFER" -> buildList {
                add(WalletDelta(walletId, -amount))
                toWalletId?.let { add(WalletDelta(it, amount)) }
            }
            else -> emptyList()
        }

    /**
     * Delta saldo saat sebuah transaksi DIBATALKAN — matematis persis
     * kebalikan dari apply(), jadi cukup membalik tanda tiap delta alih-alih
     * menduplikasi percabangan type yang sama.
     */
    fun rollback(type: String, walletId: String, toWalletId: String?, amount: Long): List<WalletDelta> =
        apply(type, walletId, toWalletId, amount).map { it.copy(amount = -it.amount) }
}
