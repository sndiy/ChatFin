package com.sndiy.chatfin.core.domain

import com.sndiy.chatfin.core.data.local.dao.*
import com.sndiy.chatfin.core.data.local.entity.*
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FinanceRepository @Inject constructor(
    private val accountDao: AccountDao,
    private val walletDao: WalletDao,
    private val categoryDao: CategoryDao,
    private val transactionDao: TransactionDao,
    private val savingsGoalDao: SavingsGoalDao,
    private val budgetDao: BudgetDao
) {
    // Account
    fun getActiveAccount(): Flow<FinanceAccountEntity?> = accountDao.getActiveAccount()
    fun getAllAccounts(): Flow<List<FinanceAccountEntity>> = accountDao.getAllAccounts()
    suspend fun insertAccount(account: FinanceAccountEntity) = accountDao.insertAccount(account)
    suspend fun switchActiveAccount(id: String) = accountDao.switchActiveAccount(id)

    // Wallet
    fun getWalletsByAccount(accountId: String): Flow<List<WalletEntity>> =
        walletDao.getWalletsByAccount(accountId)
    fun getTotalBalance(accountId: String): Flow<Long?> =
        walletDao.getTotalBalanceByAccount(accountId)
    suspend fun insertWallet(wallet: WalletEntity) = walletDao.insertWallet(wallet)
    suspend fun updateWallet(wallet: WalletEntity) = walletDao.updateWallet(wallet)
    suspend fun deleteWallet(wallet: WalletEntity) = walletDao.deleteWallet(wallet)
    suspend fun addToBalance(walletId: String, amount: Long) = walletDao.addToBalance(walletId, amount)
    suspend fun subtractFromBalance(walletId: String, amount: Long) = walletDao.subtractFromBalance(walletId, amount)
    suspend fun getWalletById(id: String): WalletEntity? = walletDao.getWalletById(id)

    // Category
    fun getCategories(accountId: String, type: String): Flow<List<CategoryEntity>> =
        categoryDao.getCategoriesByAccountAndType(accountId, type)
    suspend fun insertCategories(cats: List<CategoryEntity>) = categoryDao.insertCategories(cats)
    suspend fun insertCategory(cat: CategoryEntity) = categoryDao.insertCategory(cat)

    // Transaction
    fun getTransactionsByAccount(accountId: String): Flow<List<TransactionEntity>> =
        transactionDao.getTransactionsByAccount(accountId)
    fun getTransactionsByPeriod(accountId: String, start: String, end: String): Flow<List<TransactionEntity>> =
        transactionDao.getTransactionsByPeriod(accountId, start, end)
    fun getTotalByTypeAndPeriod(accountId: String, type: String, start: String, end: String): Flow<Long?> =
        transactionDao.getTotalByTypeAndPeriod(accountId, type, start, end)
    fun getExpenseSumByCategory(accountId: String, start: String, end: String): Flow<List<CategorySum>> =
        transactionDao.getExpenseSumByCategory(accountId, start, end)
    suspend fun insertTransaction(tx: TransactionEntity) = transactionDao.insertTransaction(tx)
    suspend fun deleteTransaction(tx: TransactionEntity) = transactionDao.deleteTransaction(tx)

    // Savings Goal
    fun getActiveSavingsGoals(accountId: String): Flow<List<SavingsGoalEntity>> =
        savingsGoalDao.getActiveSavingsGoals(accountId)
    suspend fun insertSavingsGoal(goal: SavingsGoalEntity) = savingsGoalDao.insertSavingsGoal(goal)
    suspend fun addToSavings(id: String, amount: Long) = savingsGoalDao.addToSavings(id, amount)
    suspend fun updateSavingsGoal(goal: SavingsGoalEntity) = savingsGoalDao.updateSavingsGoal(goal)
    suspend fun deleteSavingsGoal(goal: SavingsGoalEntity) = savingsGoalDao.deleteSavingsGoal(goal)
}
