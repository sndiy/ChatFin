// app/src/main/java/com/sndiy/chatfin/feature/finance/transaction/data/repository/WalletRepository.kt

package com.sndiy.chatfin.feature.finance.transaction.data.repository

import com.sndiy.chatfin.core.data.local.dao.WalletDao
import com.sndiy.chatfin.core.data.local.entity.WalletEntity
import kotlinx.coroutines.flow.Flow
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WalletRepository @Inject constructor(
    private val walletDao: WalletDao
) {
    fun getWalletsByAccount(accountId: String): Flow<List<WalletEntity>> =
        walletDao.getWalletsByAccount(accountId)

    fun getTotalBalanceByAccount(accountId: String): Flow<Long?> =
        walletDao.getTotalBalanceByAccount(accountId)

    suspend fun getWalletById(id: String): WalletEntity? =
        walletDao.getWalletById(id)

    suspend fun createWallet(
        accountId: String,
        name: String,
        type: String,
        balance: Long,
        currency: String,
        colorHex: String,
        iconName: String
    ) {
        walletDao.insertWallet(
            WalletEntity(
                id        = UUID.randomUUID().toString(),
                accountId = accountId,
                name      = name,
                type      = type,
                balance   = balance,
                currency  = currency,
                colorHex  = colorHex,
                iconName  = iconName
            )
        )
    }

    suspend fun updateWallet(wallet: WalletEntity) =
        walletDao.updateWallet(wallet)

    suspend fun deleteWallet(wallet: WalletEntity) =
        walletDao.deleteWallet(wallet)

    suspend fun addToBalance(walletId: String, amount: Long) =
        walletDao.addToBalance(walletId, amount)

    suspend fun subtractFromBalance(walletId: String, amount: Long) =
        walletDao.subtractFromBalance(walletId, amount)
}