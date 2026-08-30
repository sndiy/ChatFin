// app/src/main/java/com/sndiy/chatfin/core/data/local/dao/CategoryDao.kt

package com.sndiy.chatfin.core.data.local.dao

import androidx.room.*
import com.sndiy.chatfin.core.data.local.entity.CategoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {

    // Ambil kategori berdasarkan akun dan tipe (INCOME / EXPENSE)
    // Kategori global (accountId = null) ikut ditampilkan
    @Query("""
        SELECT * FROM categories 
        WHERE (accountId = :accountId OR accountId IS NULL)
        AND type = :type
        ORDER BY isCustom ASC, sortOrder ASC
    """)
    fun getCategoriesByAccountAndType(accountId: String, type: String): Flow<List<CategoryEntity>>

    // Ambil satu kategori berdasarkan ID
    @Query("SELECT * FROM categories WHERE id = :id")
    suspend fun getCategoryById(id: String): CategoryEntity?

    // Tambah satu kategori
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategory(category: CategoryEntity)

    // Tambah banyak kategori sekaligus (dipakai saat seeding data awal)
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCategories(categories: List<CategoryEntity>)

    // Update kategori
    @Update
    suspend fun updateCategory(category: CategoryEntity)

    // Hapus kategori
    @Delete
    suspend fun deleteCategory(category: CategoryEntity)
}
