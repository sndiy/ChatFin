package com.sndiy.chatfin.feature.finance.transaction.data.repository

import com.sndiy.chatfin.core.data.local.dao.CategoryDao
import com.sndiy.chatfin.core.data.local.entity.CategoryEntity
import com.sndiy.chatfin.core.data.sync.OutboundSync
import kotlinx.coroutines.flow.Flow
import java.util.UUID

class CategoryRepository(
    private val categoryDao: CategoryDao,
    private val outboundSync: OutboundSync
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
        val category = CategoryEntity(
            id        = UUID.randomUUID().toString(),
            accountId = accountId,
            name      = name,
            type      = type,
            iconName  = iconName,
            colorHex  = colorHex,
            isCustom  = true,
            updatedAt = System.currentTimeMillis()
        )
        categoryDao.insertCategory(category)
        outboundSync.pushCategory(category)
    }

    suspend fun updateCategory(category: CategoryEntity) {
        val updated = category.copy(updatedAt = System.currentTimeMillis())
        categoryDao.updateCategory(updated)
        outboundSync.pushCategory(updated)
    }

    suspend fun deleteCategory(category: CategoryEntity) {
        categoryDao.deleteCategory(category)
        outboundSync.deleteCategory(category.id)
    }
}
