// app/src/main/java/com/sndiy/chatfin/core/data/local/dao/SavingsGoalDao.kt

package com.sndiy.chatfin.core.data.local.dao

import androidx.room.*
import com.sndiy.chatfin.core.data.local.entity.SavingsGoalEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SavingsGoalDao {

    // Ambil semua savings goal dalam satu akun (terbaru di atas)
    @Query("SELECT * FROM savings_goals WHERE accountId = :accountId ORDER BY createdAt DESC")
    fun getSavingsGoalsByAccount(accountId: String): Flow<List<SavingsGoalEntity>>

    // Ambil hanya savings goal yang belum selesai
    @Query("SELECT * FROM savings_goals WHERE accountId = :accountId AND isCompleted = 0 ORDER BY createdAt DESC")
    fun getActiveSavingsGoals(accountId: String): Flow<List<SavingsGoalEntity>>

    // Ambil satu savings goal berdasarkan ID
    @Query("SELECT * FROM savings_goals WHERE id = :id")
    suspend fun getSavingsGoalById(id: String): SavingsGoalEntity?

    // Tambah savings goal baru
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSavingsGoal(goal: SavingsGoalEntity)

    // Update savings goal
    @Update
    suspend fun updateSavingsGoal(goal: SavingsGoalEntity)

    // Hapus savings goal
    @Delete
    suspend fun deleteSavingsGoal(goal: SavingsGoalEntity)

    // Tambah dana ke savings goal (saat user nabung)
    @Query("UPDATE savings_goals SET currentAmount = currentAmount + :amount WHERE id = :id")
    suspend fun addToSavings(id: String, amount: Long)

    // Tandai savings goal sebagai selesai
    @Query("UPDATE savings_goals SET isCompleted = 1 WHERE id = :id")
    suspend fun markAsCompleted(id: String)
}