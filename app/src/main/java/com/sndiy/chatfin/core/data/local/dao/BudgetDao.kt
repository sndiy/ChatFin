// app/src/main/java/com/sndiy/chatfin/core/data/local/dao/BudgetDao.kt

package com.sndiy.chatfin.core.data.local.dao

import androidx.room.*
import com.sndiy.chatfin.core.data.local.entity.BudgetEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BudgetDao {

    // Ambil semua budget dalam satu akun untuk bulan dan tahun tertentu
    @Query("""
        SELECT * FROM budgets 
        WHERE accountId = :accountId 
        AND year = :year 
        AND (month = :month OR month IS NULL)
    """)
    fun getBudgetsByAccountAndPeriod(
        accountId: String,
        year: Int,
        month: Int
    ): Flow<List<BudgetEntity>>

    // Ambil satu budget berdasarkan ID
    @Query("SELECT * FROM budgets WHERE id = :id")
    suspend fun getBudgetById(id: String): BudgetEntity?

    // Cek apakah budget untuk kategori tertentu sudah ada di bulan ini
    @Query("""
        SELECT * FROM budgets 
        WHERE accountId = :accountId 
        AND categoryId = :categoryId 
        AND year = :year 
        AND month = :month
        LIMIT 1
    """)
    suspend fun getBudgetByCategoryAndPeriod(
        accountId: String,
        categoryId: String,
        year: Int,
        month: Int
    ): BudgetEntity?

    // Tambah budget baru
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBudget(budget: BudgetEntity)

    // Update budget
    @Update
    suspend fun updateBudget(budget: BudgetEntity)

    // Hapus budget
    @Delete
    suspend fun deleteBudget(budget: BudgetEntity)
}