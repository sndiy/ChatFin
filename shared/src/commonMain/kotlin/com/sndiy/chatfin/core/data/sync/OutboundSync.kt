package com.sndiy.chatfin.core.data.sync

import com.sndiy.chatfin.core.data.local.entity.BudgetEntity
import com.sndiy.chatfin.core.data.local.entity.CategoryEntity
import com.sndiy.chatfin.core.data.local.entity.FinanceAccountEntity
import com.sndiy.chatfin.core.data.local.entity.TransactionEntity
import com.sndiy.chatfin.core.data.local.entity.TransactionItemEntity
import com.sndiy.chatfin.core.data.local.entity.WalletEntity

/**
 * Kontrak sinkronisasi data keluar (outbound sync) dari lokal Room ke Firestore/cloud.
 */
interface OutboundSync {
    suspend fun pushTransaction(tx: TransactionEntity, items: List<TransactionItemEntity> = emptyList())
    suspend fun deleteTransaction(txId: String, walletId: String? = null, toWalletId: String? = null)
    suspend fun pushWallet(wallet: WalletEntity)
    suspend fun pushWalletById(walletId: String)
    suspend fun deleteWallet(walletId: String)
    suspend fun pushCategory(category: CategoryEntity)
    suspend fun deleteCategory(categoryId: String)
    suspend fun pushAccount(account: FinanceAccountEntity)
    suspend fun deleteAccount(accountId: String)
    suspend fun pushBudget(budget: BudgetEntity)
    suspend fun deleteBudget(budgetId: String)
}

/**
 * Fallback no-op sync implementation (100% offline).
 */
class NoOpOutboundSync : OutboundSync {
    override suspend fun pushTransaction(tx: TransactionEntity, items: List<TransactionItemEntity>) {}
    override suspend fun deleteTransaction(txId: String, walletId: String?, toWalletId: String?) {}
    override suspend fun pushWallet(wallet: WalletEntity) {}
    override suspend fun pushWalletById(walletId: String) {}
    override suspend fun deleteWallet(walletId: String) {}
    override suspend fun pushCategory(category: CategoryEntity) {}
    override suspend fun deleteCategory(categoryId: String) {}
    override suspend fun pushAccount(account: FinanceAccountEntity) {}
    override suspend fun deleteAccount(accountId: String) {}
    override suspend fun pushBudget(budget: BudgetEntity) {}
    override suspend fun deleteBudget(budgetId: String) {}
}
