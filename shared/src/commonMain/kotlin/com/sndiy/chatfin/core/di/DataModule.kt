package com.sndiy.chatfin.core.di

import com.sndiy.chatfin.core.data.local.ChatFinDatabase
import com.sndiy.chatfin.core.parser.RoomKeywordSource
import com.sndiy.chatfin.feature.finance.account.data.repository.AccountRepository
import com.sndiy.chatfin.feature.finance.budget.data.repository.BudgetRepository
import com.sndiy.chatfin.feature.finance.transaction.data.repository.CategoryRepository
import com.sndiy.chatfin.feature.finance.transaction.data.repository.TransactionRepository
import com.sndiy.chatfin.feature.finance.transaction.data.repository.WalletRepository
import org.koin.dsl.module

val dataModule = module {
    single { get<ChatFinDatabase>().accountDao() }
    single { get<ChatFinDatabase>().walletDao() }
    single { get<ChatFinDatabase>().categoryDao() }
    single { get<ChatFinDatabase>().transactionDao() }
    single { get<ChatFinDatabase>().budgetDao() }
    single { get<ChatFinDatabase>().chatDao() }
    single { get<ChatFinDatabase>().categoryKeywordDao() }
    single { RoomKeywordSource(get()) }
}

val repositoryModule = module {
    single { AccountRepository(get(), get(), get(), get()) }
    single { WalletRepository(get(), get(), get(), get()) }
    single { CategoryRepository(get(), get()) }
    single { BudgetRepository(get(), get(), get()) }
    single { TransactionRepository(get(), get(), get(), get()) }
}
