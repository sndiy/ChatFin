package com.sndiy.chatfin.core.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.sndiy.chatfin.core.data.local.entity.CategoryKeywordEntity

/** Hasil join category_keywords + categories — nama kategori selalu terbaru walau user mengganti nama kategori. */
data class CategoryKeywordWithCategoryName(
    val keyword: String,
    val categoryId: String,
    val categoryName: String,
    val type: String
)

@Dao
interface CategoryKeywordDao {

    @Query("""
        SELECT k.keyword AS keyword, k.categoryId AS categoryId, c.name AS categoryName, k.type AS type
        FROM category_keywords k
        INNER JOIN categories c ON c.id = k.categoryId
    """)
    suspend fun getAllWithCategoryName(): List<CategoryKeywordWithCategoryName>

    @Query("SELECT COUNT(*) FROM category_keywords")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertKeyword(keyword: CategoryKeywordEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertKeywords(keywords: List<CategoryKeywordEntity>)

    @Delete
    suspend fun deleteKeyword(keyword: CategoryKeywordEntity)
}
