package com.sndiy.chatfin.feature.finance.account.data.repository

import com.sndiy.chatfin.core.data.local.dao.AccountDao
import com.sndiy.chatfin.core.data.local.dao.CategoryDao
import com.sndiy.chatfin.core.data.local.dao.WalletDao
import com.sndiy.chatfin.core.data.local.entity.FinanceAccountEntity
import com.sndiy.chatfin.core.data.local.entity.WalletEntity
import com.sndiy.chatfin.core.data.security.SecureStorage
import com.sndiy.chatfin.core.data.local.DefaultCategories
import kotlinx.coroutines.flow.Flow
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AccountRepository @Inject constructor(
    private val accountDao: AccountDao,
    private val walletDao: WalletDao,
    private val categoryDao: CategoryDao,
    private val secureStorage: SecureStorage
) {
    fun getAllAccounts(): Flow<List<FinanceAccountEntity>> =
        accountDao.getAllAccounts()

    fun getActiveAccount(): Flow<FinanceAccountEntity?> =
        accountDao.getActiveAccount()

    suspend fun getAccountById(id: String): FinanceAccountEntity? =
        accountDao.getAccountById(id)

    suspend fun createAccount(
        name: String,
        iconName: String = "account_balance_wallet",
        colorHex: String = "#0061A4",
        currency: String = "IDR",
        description: String? = null
    ): String {
        val accountId = UUID.randomUUID().toString()
        accountDao.insertAccount(
            FinanceAccountEntity(
                id          = accountId,
                name        = name,
                iconName    = iconName,
                colorHex    = colorHex,
                currency    = currency,
                description = description,
                isActive    = false
            )
        )
        walletDao.insertWallet(
            WalletEntity(
                id        = UUID.randomUUID().toString(),
                accountId = accountId,
                name      = "Kas",
                type      = "CASH",
                balance   = 0L,
                currency  = currency,
                colorHex  = "#1B8A4C",
                iconName  = "payments",
                sortOrder = 0
            )
        )
        categoryDao.insertCategories(DefaultCategories.all)
        return accountId
    }

    suspend fun updateAccount(account: FinanceAccountEntity) =
        accountDao.updateAccount(account)

    suspend fun deleteAccount(account: FinanceAccountEntity) =
        accountDao.deleteAccount(account)

    suspend fun switchActiveAccount(accountId: String) {
        accountDao.switchActiveAccount(accountId)
        secureStorage.activeAccountId = accountId
    }

    fun getSavedActiveAccountId(): String? =
        secureStorage.activeAccountId
}