// app/src/main/java/com/sndiy/chatfin/core/data/local/dao/AccountDao.kt

package com.sndiy.chatfin.core.data.local.dao

import androidx.room.*
import com.sndiy.chatfin.core.data.local.entity.FinanceAccountEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AccountDao {

    // Ambil semua akun, diurutkan berdasarkan sortOrder
    @Query("SELECT * FROM finance_accounts ORDER BY sortOrder ASC")
    fun getAllAccounts(): Flow<List<FinanceAccountEntity>>

    // Ambil akun yang sedang aktif (hanya 1)
    @Query("SELECT * FROM finance_accounts WHERE isActive = 1 LIMIT 1")
    fun getActiveAccount(): Flow<FinanceAccountEntity?>

    // Ambil satu akun berdasarkan ID
    @Query("SELECT * FROM finance_accounts WHERE id = :id")
    suspend fun getAccountById(id: String): FinanceAccountEntity?

    // Tambah atau update akun
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAccount(account: FinanceAccountEntity)

    // Update akun yang sudah ada
    @Update
    suspend fun updateAccount(account: FinanceAccountEntity)

    // Hapus akun
    @Delete
    suspend fun deleteAccount(account: FinanceAccountEntity)

    // Set semua akun jadi tidak aktif
    @Query("UPDATE finance_accounts SET isActive = 0")
    suspend fun deactivateAllAccounts()

    // Set satu akun jadi aktif dan lainnya nonaktif dalam satu operasi SQL atomik
    @Query("UPDATE finance_accounts SET isActive = (CASE WHEN id = :id THEN 1 ELSE 0 END)")
    suspend fun switchActiveAccount(id: String)
}