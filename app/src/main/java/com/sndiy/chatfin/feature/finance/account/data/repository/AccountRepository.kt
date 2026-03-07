package com.sndiy.chatfin.feature.finance.account.data.repository

import com.sndiy.chatfin.core.data.local.dao.AccountDao
import com.sndiy.chatfin.core.data.local.entity.FinanceAccountEntity
import kotlinx.coroutines.flow.Flow
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AccountRepository @Inject constructor(
    private val accountDao: AccountDao
) {
    fun getAllAccounts(): Flow<List<FinanceAccountEntity>> =
        accountDao.getAllAccounts()

    fun getActiveAccount(): Flow<FinanceAccountEntity?> =
        accountDao.getActiveAccount()

    suspend fun getAccountById(id: String): FinanceAccountEntity? =
        accountDao.getAccountById(id)

    suspend fun createAccount(
        name: String,
        currency: String = "IDR",
        color: String = "#5B6EF5"
    ): String {
        val id = UUID.randomUUID().toString()
        accountDao.insertAccount(
            FinanceAccountEntity(
                id       = id,
                name     = name,
                currency = currency,
                color    = color,
                isActive = false
            )
        )
        return id
    }

    suspend fun updateAccount(account: FinanceAccountEntity) =
        accountDao.updateAccount(account)

    suspend fun deleteAccount(account: FinanceAccountEntity) =
        accountDao.deleteAccount(account)

    suspend fun switchActiveAccount(id: String) =
        accountDao.switchActiveAccount(id)
}