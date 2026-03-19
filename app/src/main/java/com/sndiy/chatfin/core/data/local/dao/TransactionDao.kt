// app/src/main/java/com/sndiy/chatfin/core/data/local/dao/TransactionDao.kt

package com.sndiy.chatfin.core.data.local.dao

import androidx.room.*
import com.sndiy.chatfin.core.data.local.entity.TransactionEntity
import kotlinx.coroutines.flow.Flow

// Data class bantu untuk query agregasi per kategori
data class CategorySum(
    val categoryId: String,
    val total: Long
)

@Dao
interface TransactionDao {

    // Ambil semua transaksi dalam satu akun (terbaru di atas)
    @Query("""
        SELECT * FROM transactions 
        WHERE accountId = :accountId 
        ORDER BY date DESC, time DESC
    """)
    fun getTransactionsByAccount(accountId: String): Flow<List<TransactionEntity>>

    // Ambil transaksi berdasarkan rentang tanggal (untuk filter periode)
    @Query("""
        SELECT * FROM transactions 
        WHERE accountId = :accountId 
        AND date BETWEEN :startDate AND :endDate
        ORDER BY date DESC, time DESC
    """)
    fun getTransactionsByPeriod(
        accountId: String,
        startDate: String,  // format: "yyyy-MM-dd"
        endDate: String     // format: "yyyy-MM-dd"
    ): Flow<List<TransactionEntity>>

    // Total pemasukan atau pengeluaran dalam periode tertentu
    @Query("""
        SELECT SUM(amount) FROM transactions 
        WHERE accountId = :accountId 
        AND type = :type
        AND date BETWEEN :startDate AND :endDate
    """)
    fun getTotalByTypeAndPeriod(
        accountId: String,
        type: String,       // INCOME | EXPENSE
        startDate: String,
        endDate: String
    ): Flow<Long?>

    // Total pengeluaran per kategori dalam periode (untuk pie chart)
    @Query("""
        SELECT categoryId, SUM(amount) as total 
        FROM transactions 
        WHERE accountId = :accountId 
        AND type = 'EXPENSE'
        AND date BETWEEN :startDate AND :endDate
        GROUP BY categoryId
        ORDER BY total DESC
    """)
    fun getExpenseSumByCategory(
        accountId: String,
        startDate: String,
        endDate: String
    ): Flow<List<CategorySum>>

    // Total pengeluaran per hari dalam periode (untuk heatmap kalender)
    @Query("""
        SELECT date, SUM(amount) as total 
        FROM transactions 
        WHERE accountId = :accountId 
        AND type = 'EXPENSE'
        AND date BETWEEN :startDate AND :endDate
        GROUP BY date
        ORDER BY date ASC
    """)
    fun getDailyExpenseInPeriod(
        accountId: String,
        startDate: String,
        endDate: String
    ): Flow<List<DailyTotal>>

    // Ambil satu transaksi berdasarkan ID
    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun getTransactionById(id: String): TransactionEntity?

    // Tambah transaksi baru
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTransaction(transaction: TransactionEntity)

    // Update transaksi
    @Update
    suspend fun updateTransaction(transaction: TransactionEntity)

    // Hapus transaksi berdasarkan object
    @Delete
    suspend fun deleteTransaction(transaction: TransactionEntity)

    // Hapus transaksi berdasarkan ID
    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun deleteTransactionById(id: String)
}

// Data class bantu untuk query total per hari
data class DailyTotal(
    val date: String,
    val total: Long
)