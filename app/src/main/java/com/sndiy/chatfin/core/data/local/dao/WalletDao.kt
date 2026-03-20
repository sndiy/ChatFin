// app/src/main/java/com/sndiy/chatfin/core/data/local/dao/WalletDao.kt

package com.sndiy.chatfin.core.data.local.dao

import androidx.room.*
import com.sndiy.chatfin.core.data.local.entity.WalletEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WalletDao {

    // Ambil semua dompet milik satu akun
    @Query("SELECT * FROM wallets WHERE accountId = :accountId ORDER BY sortOrder ASC")
    fun getWalletsByAccount(accountId: String): Flow<List<WalletEntity>>

    // Ambil satu dompet berdasarkan ID
    @Query("SELECT * FROM wallets WHERE id = :id")
    suspend fun getWalletById(id: String): WalletEntity?

    // Total saldo semua dompet dalam satu akun
    @Query("SELECT SUM(balance) FROM wallets WHERE accountId = :accountId")
    fun getTotalBalanceByAccount(accountId: String): Flow<Long?>

    // Tambah dompet baru
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWallet(wallet: WalletEntity)

    // Update dompet
    @Update
    suspend fun updateWallet(wallet: WalletEntity)

    // Hapus dompet
    @Delete
    suspend fun deleteWallet(wallet: WalletEntity)

    // Tambah saldo (dipakai saat ada pemasukan atau transfer masuk)
    @Query("UPDATE wallets SET balance = balance + :amount WHERE id = :walletId")
    suspend fun addToBalance(walletId: String, amount: Long)

    // Kurangi saldo (dipakai saat ada pengeluaran atau transfer keluar)
    @Query("UPDATE wallets SET balance = balance - :amount WHERE id = :walletId")
    suspend fun subtractFromBalance(walletId: String, amount: Long)
}