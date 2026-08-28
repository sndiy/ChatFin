package com.sndiy.chatfin.feature.finance.transaction.data.repository

import androidx.room.withTransaction
import com.sndiy.chatfin.core.data.local.ChatFinDatabase
import com.sndiy.chatfin.core.data.local.dao.TransactionDao
import com.sndiy.chatfin.core.data.local.dao.WalletDao
import com.sndiy.chatfin.core.data.local.entity.TransactionEntity
import com.sndiy.chatfin.core.data.local.entity.WalletEntity
import com.sndiy.chatfin.core.data.sync.FirestoreOutboundSync
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WalletRepository @Inject constructor(
    private val db: ChatFinDatabase,
    private val walletDao: WalletDao,
    private val transactionDao: TransactionDao,
    private val outboundSync: FirestoreOutboundSync
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
            id              = "init_bal_$walletId",  // deterministik, anti-duplikat
            accountId       = accountId,
            type            = "INCOME",
            amount          = balance,
            categoryId      = "inc_other",
            walletId        = walletId,
            note            = "Saldo awal",
            date            = today,
            time            = time,
            isInitialBalance = true,
            createdAt       = now,
            updatedAt       = now
        )

        db.withTransaction {
            walletDao.insertWallet(wallet)
            // Selalu buat transaksi saldo awal (termasuk saat balance == 0)
            // agar SUM(transaksi) == saldo wallet — invariant yang dijaga konsisten.
            transactionDao.insertTransaction(tx)
        }

        // Push real-time ke Firestore
        outboundSync.pushWallet(wallet)
        outboundSync.pushTransaction(tx)
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

    suspend fun addToBalance(walletId: String, amount: Long) {
        walletDao.addToBalance(walletId, amount)
        outboundSync.pushWalletById(walletId)
    }

    suspend fun subtractFromBalance(walletId: String, amount: Long) {
        walletDao.subtractFromBalance(walletId, amount)
        outboundSync.pushWalletById(walletId)
    }
}