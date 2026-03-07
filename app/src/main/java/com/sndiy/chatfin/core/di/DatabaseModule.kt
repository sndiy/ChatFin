package com.sndiy.chatfin.core.di

import android.content.Context
import androidx.room.Room
import com.sndiy.chatfin.core.data.local.ChatFinDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideChatFinDatabase(
        @ApplicationContext context: Context
    ): ChatFinDatabase = Room.databaseBuilder(
        context,
        ChatFinDatabase::class.java,
        "chatfin_database"
    ).fallbackToDestructiveMigration().build()

    @Provides fun provideAccountDao(db: ChatFinDatabase)     = db.accountDao()
    @Provides fun provideWalletDao(db: ChatFinDatabase)      = db.walletDao()
    @Provides fun provideCategoryDao(db: ChatFinDatabase)    = db.categoryDao()
    @Provides fun provideTransactionDao(db: ChatFinDatabase) = db.transactionDao()
    @Provides fun provideBudgetDao(db: ChatFinDatabase)      = db.budgetDao()
    @Provides fun provideSavingsGoalDao(db: ChatFinDatabase) = db.savingsGoalDao()
}