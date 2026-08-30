package com.sndiy.chatfin.feature.finance.transaction.data.repository

import com.sndiy.chatfin.core.data.local.ChatFinDatabase
import com.sndiy.chatfin.core.data.local.withTransaction
import com.sndiy.chatfin.core.data.local.dao.TransactionDao
import com.sndiy.chatfin.core.data.local.dao.WalletDao
import com.sndiy.chatfin.core.data.local.entity.TransactionEntity
import com.sndiy.chatfin.core.data.local.entity.WalletEntity
import com.sndiy.chatfin.core.data.sync.OutboundSync
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.UUID

class WalletRepository(
    private val db: ChatFinDatabase,
    private val walletDao: WalletDao,
    private val transactionDao: TransactionDao,
    private val outboundSync: OutboundSync
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
        val walletId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val wallet = WalletEntity(
            id        = walletId,
            accountId = accountId,
            name      = name,
            type      = type,
            balance   = balance,
            currency  = currency,
            colorHex  = colorHex,
            iconName  = iconName,
            createdAt = now,
            updatedAt = now
        )

        val today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
        val time = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))
        val tx = TransactionEntity(
            id               = UUID.randomUUID().toString(),
            accountId        = accountId,
            type             = "INCOME",
            amount           = balance,
            categoryId       = "inc_other",
            walletId         = walletId,
            note             = "Saldo awal dompet $name",
            date             = today,
            time             = time,
            isInitialBalance = true,
            createdAt        = now,
            updatedAt        = now
        )

        db.withTransaction {
            walletDao.insertWallet(wallet)
            if (balance > 0) {
                transactionDao.insertTransaction(tx)
            }
        }

        outboundSync.pushWallet(wallet)
        if (balance > 0) {
            outboundSync.pushTransaction(tx)
        }
    }

    suspend fun updateWallet(wallet: WalletEntity) {
        val updated = wallet.copy(updatedAt = System.currentTimeMillis())
        walletDao.updateWallet(updated)
        outboundSync.pushWallet(updated)
    }

    suspend fun deleteWallet(wallet: WalletEntity) {
        walletDao.deleteWallet(wallet)
        outboundSync.deleteWallet(wallet.id)
    }
}
