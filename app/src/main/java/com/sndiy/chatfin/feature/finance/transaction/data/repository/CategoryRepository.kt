// app/src/main/java/com/sndiy/chatfin/feature/finance/transaction/data/repository/CategoryRepository.kt

package com.sndiy.chatfin.feature.finance.transaction.data.repository

import com.sndiy.chatfin.core.data.local.dao.CategoryDao
import com.sndiy.chatfin.core.data.local.entity.CategoryEntity
import kotlinx.coroutines.flow.Flow
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CategoryRepository @Inject constructor(
    private val categoryDao: CategoryDao
) {
    fun getCategoriesByAccountAndType(accountId: String, type: String): Flow<List<CategoryEntity>> =
        categoryDao.getCategoriesByAccountAndType(accountId, type)

    suspend fun getCategoryById(id: String): CategoryEntity? =
        categoryDao.getCategoryById(id)

    suspend fun createCategory(
        accountId: String,
        name: String,
        type: String,
        iconName: String,
        colorHex: String
    ) {
        categoryDao.insertCategory(
            CategoryEntity(
                id        = UUID.randomUUID().toString(),
                accountId = accountId,
                name      = name,
                type      = type,
                iconName  = iconName,
                colorHex  = colorHex,
                isCustom  = true
            )
        )
    }

    suspend fun updateCategory(category: CategoryEntity) =
        categoryDao.updateCategory(category)

    suspend fun deleteCategory(category: CategoryEntity) =
        categoryDao.deleteCategory(category)
}