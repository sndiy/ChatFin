// app/src/main/java/com/sndiy/chatfin/feature/finance/budget/data/repository/BudgetRepository.kt

package com.sndiy.chatfin.feature.finance.budget.data.repository

import com.sndiy.chatfin.core.data.local.dao.BudgetDao
import com.sndiy.chatfin.core.data.local.dao.TransactionDao
import com.sndiy.chatfin.core.data.local.entity.BudgetEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

data class BudgetWithSpent(
    val budget: BudgetEntity,
    val spent: Long,
    val categoryName: String,
    val categoryColor: String
) {
    val remaining: Long get() = budget.limitAmount - spent
    val percentage: Float get() = if (budget.limitAmount > 0) (spent.toFloat() / budget.limitAmount * 100f).coerceIn(0f, 100f) else 0f
    val isOverBudget: Boolean get() = spent > budget.limitAmount
    val isNearLimit: Boolean get() = percentage >= 80f && !isOverBudget
}

@Singleton
class BudgetRepository @Inject constructor(
    private val budgetDao: BudgetDao,
    private val transactionDao: TransactionDao
) {
    private val dateFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    fun getBudgetsByAccountAndPeriod(
        accountId: String,
        year: Int,
        month: Int
    ): Flow<List<BudgetEntity>> =
        budgetDao.getBudgetsByAccountAndPeriod(accountId, year, month)

    suspend fun getBudgetByCategoryAndPeriod(
        accountId: String,
        categoryId: String,
        year: Int,
        month: Int
    ): BudgetEntity? =
        budgetDao.getBudgetByCategoryAndPeriod(accountId, categoryId, year, month)

    suspend fun getSpentForCategory(
        accountId: String,
        categoryId: String,
        startDate: LocalDate,
        endDate: LocalDate
    ): Long {
        val sums = transactionDao.getExpenseSumByCategory(
            accountId,
            startDate.format(dateFmt),
            endDate.format(dateFmt)
        ).first()
        return sums.find { it.categoryId == categoryId }?.total ?: 0L
    }

    suspend fun createBudget(
        accountId: String,
        categoryId: String,
        limitAmount: Long,
        year: Int,
        month: Int
    ) {
        // Cek duplikat
        val existing = budgetDao.getBudgetByCategoryAndPeriod(accountId, categoryId, year, month)
        if (existing != null) {
            // Update yang ada
            budgetDao.updateBudget(existing.copy(limitAmount = limitAmount))
        } else {
            budgetDao.insertBudget(
                BudgetEntity(
                    id          = UUID.randomUUID().toString(),
                    accountId   = accountId,
                    categoryId  = categoryId,
                    limitAmount = limitAmount,
                    period      = "MONTHLY",
                    month       = month,
                    year        = year
                )
            )
        }
    }

    suspend fun updateBudget(budget: BudgetEntity) =
        budgetDao.updateBudget(budget)

    suspend fun deleteBudget(budget: BudgetEntity) =
        budgetDao.deleteBudget(budget)
}
