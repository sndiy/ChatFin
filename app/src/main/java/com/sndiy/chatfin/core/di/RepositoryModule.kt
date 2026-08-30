package com.sndiy.chatfin.core.di

import com.sndiy.chatfin.core.data.local.ChatFinDatabase
import com.sndiy.chatfin.core.data.local.dao.AccountDao
import com.sndiy.chatfin.core.data.local.dao.BudgetDao
import com.sndiy.chatfin.core.data.local.dao.CategoryDao
import com.sndiy.chatfin.core.data.local.dao.TransactionDao
import com.sndiy.chatfin.core.data.local.dao.WalletDao
import com.sndiy.chatfin.core.data.security.SecureStorage
import com.sndiy.chatfin.core.data.sync.OutboundSync
import com.sndiy.chatfin.core.data.sync.SyncEventBus
import com.sndiy.chatfin.core.data.sync.SyncStatusRepository
import com.sndiy.chatfin.feature.finance.account.data.repository.AccountRepository
import com.sndiy.chatfin.feature.finance.budget.data.repository.BudgetRepository
import com.sndiy.chatfin.feature.finance.transaction.data.repository.CategoryRepository
import com.sndiy.chatfin.feature.finance.transaction.data.repository.TransactionRepository
import com.sndiy.chatfin.feature.finance.transaction.data.repository.WalletRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule {

    @Provides
    @Singleton
    fun provideSyncStatusRepository(): SyncStatusRepository = SyncStatusRepository()

    @Provides
    @Singleton
    fun provideSyncEventBus(): SyncEventBus = SyncEventBus()

    @Provides
    @Singleton
    fun provideAccountRepository(
        accountDao: AccountDao,
        walletDao: WalletDao,
        secureStorage: SecureStorage,
        outboundSync: OutboundSync
    ): AccountRepository = AccountRepository(accountDao, walletDao, secureStorage, outboundSync)

    @Provides
    @Singleton
    fun provideWalletRepository(
        db: ChatFinDatabase,
        walletDao: WalletDao,
        transactionDao: TransactionDao,
        outboundSync: OutboundSync
    ): WalletRepository = WalletRepository(db, walletDao, transactionDao, outboundSync)

    @Provides
    @Singleton
    fun provideCategoryRepository(
        categoryDao: CategoryDao,
        outboundSync: OutboundSync
    ): CategoryRepository = CategoryRepository(categoryDao, outboundSync)

    @Provides
    @Singleton
    fun provideBudgetRepository(
        budgetDao: BudgetDao,
        transactionDao: TransactionDao,
        outboundSync: OutboundSync
    ): BudgetRepository = BudgetRepository(budgetDao, transactionDao, outboundSync)

    @Provides
    @Singleton
    fun provideTransactionRepository(
        db: ChatFinDatabase,
        transactionDao: TransactionDao,
        walletDao: WalletDao,
        outboundSync: OutboundSync
    ): TransactionRepository = TransactionRepository(db, transactionDao, walletDao, outboundSync)
}
