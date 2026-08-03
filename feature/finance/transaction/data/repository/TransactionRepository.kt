package com.sndiy.chatfin.feature.finance.transaction.data.repository

import androidx.room.withTransaction
import com.sndiy.chatfin.core.data.local.ChatFinDatabase
import com.sndiy.chatfin.core.data.local.dao.CategorySum
import com.sndiy.chatfin.core.data.local.dao.DailyTotal
import com.sndiy.chatfin.core.data.local.dao.TransactionDao
import com.sndiy.chatfin.core.data.local.dao.WalletDao
import com.sndiy.chatfin.core.data.local.entity.TransactionEntity
import com.sndiy.chatfin.core.data.local.entity.TransactionItemEntity
import com.sndiy.chatfin.core.data.local.entity.TransactionWithItems
import com.sndiy.chatfin.core.domain.BalanceEffect
import com.sndiy.chatfin.core.domain.WalletDelta
import com.sndiy.chatfin.core.ocr.ParsedReceiptItem
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TransactionRepository @Inject constructor(
    private val db: ChatFinDatabase,
    private val transactionDao: TransactionDao,
    private val walletDao: WalletDao
) {
    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    fun getTransactionsByAccount(accountId: String): Flow<List<TransactionEntity>> =
        transactionDao.getTransactionsByAccount(accountId)

    fun getTransactionsWithItemsByAccount(accountId: String): Flow<List<TransactionWithItems>> =
        transactionDao.getTransactionsWithItemsByAccount(accountId)

    fun getTransactionsByPeriod(
        accountId: String,
        startDate: LocalDate,
        endDate: LocalDate
    ): Flow<List<TransactionEntity>> = transactionDao.getTransactionsByPeriod(
        accountId  = accountId,
        startDate  = startDate.format(dateFormatter),
        endDate    = endDate.format(dateFormatter)
    )

    fun getTotalIncome(accountId: String, startDate: LocalDate, endDate: LocalDate): Flow<Long?> =
        transactionDao.getTotalByTypeAndPeriod(
            accountId, "INCOME",
            startDate.format(dateFormatter),
            endDate.format(dateFormatter)
        )

    fun getTotalExpense(accountId: String, startDate: LocalDate, endDate: LocalDate): Flow<Long?> =
        transactionDao.getTotalByTypeAndPeriod(
            accountId, "EXPENSE",
            startDate.format(dateFormatter),
            endDate.format(dateFormatter)
        )

    fun getExpenseSumByCategory(
        accountId: String,
        startDate: LocalDate,
        endDate: LocalDate
    ): Flow<List<CategorySum>> = transactionDao.getExpenseSumByCategory(
        accountId,
        startDate.format(dateFormatter),
        endDate.format(dateFormatter)
    )

    fun getDailyExpense(
        accountId: String,
        startDate: LocalDate,
        endDate: LocalDate
    ): Flow<List<DailyTotal>> = transactionDao.getDailyExpenseInPeriod(
        accountId,
        startDate.format(dateFormatter),
        endDate.format(dateFormatter)
    )

    suspend fun getTransactionById(id: String): TransactionEntity? =
        transactionDao.getTransactionById(id)

    suspend fun getTransactionWithItemsById(id: String): TransactionWithItems? =
        transactionDao.getTransactionWithItemsById(id)

    // ── Tambah transaksi biasa ───────────────────────────────────────────────
    suspend fun addTransaction(
        accountId: String,
        type: String,
        amount: Long,
        categoryId: String,
        walletId: String,
        toWalletId: String? = null,
        note: String? = null,
        receiptImageUri: String? = null,
        date: LocalDate = LocalDate.now(),
        time: LocalTime = LocalTime.now()
    ) {
        addTransactionWithItems(
            accountId = accountId,
            type = type,
            amount = amount,
            categoryId = categoryId,
            walletId = walletId,
            toWalletId = toWalletId,
            note = note,
            receiptImageUri = receiptImageUri,
            date = date,
            time = time,
            items = emptyList()
        )
    }

    // ── Tambah transaksi beserta rincian list item (OCR / AI Chat) ───────────
    suspend fun addTransactionWithItems(
        accountId: String,
        type: String,
        amount: Long,
        categoryId: String,
        walletId: String,
        toWalletId: String? = null,
        note: String? = null,
        receiptImageUri: String? = null,
        date: LocalDate = LocalDate.now(),
        time: LocalTime = LocalTime.now(),
        items: List<ParsedReceiptItem> = emptyList()
    ) {
        val txId = UUID.randomUUID().toString()
        val transaction = TransactionEntity(
            id                = txId,
            accountId         = accountId,
            type              = type,
            amount            = amount,
            categoryId        = categoryId,
            walletId          = walletId,
            toWalletId        = toWalletId,
            note              = note,
            receiptImageUri   = receiptImageUri,
            date              = date.format(dateFormatter),
            time              = time.format(timeFormatter)
        )

        val itemEntities = items.filter { it.name.isNotBlank() }.map { item ->
            TransactionItemEntity(
                id = UUID.randomUUID().toString(),
                transactionId = txId,
                name = item.name,
                price = item.price,
                quantity = 1
            )
        }

        db.withTransaction {
            transactionDao.insertTransaction(transaction)
            if (itemEntities.isNotEmpty()) {
                transactionDao.insertTransactionItems(itemEntities)
            }
            applyBalanceEffect(type, walletId, toWalletId, amount)
        }
    }

    // ── Update transaksi dengan rollback + apply saldo ───────────────────────
    suspend fun updateTransaction(
        oldTransaction: TransactionEntity,
        newType: String,
        newAmount: Long,
        newCategoryId: String,
        newWalletId: String,
        newToWalletId: String? = null,
        newNote: String? = null,
        newDate: LocalDate,
        newTime: LocalTime
    ) {
        db.withTransaction {
            rollbackBalanceEffect(
                oldTransaction.type,
                oldTransaction.walletId,
                oldTransaction.toWalletId,
                oldTransaction.amount
            )

            val updated = oldTransaction.copy(
                type       = newType,
                amount     = newAmount,
                categoryId = newCategoryId,
                walletId   = newWalletId,
                toWalletId = newToWalletId,
                note       = newNote,
                date       = newDate.format(dateFormatter),
                time       = newTime.format(timeFormatter)
            )
            transactionDao.updateTransaction(updated)
            applyBalanceEffect(newType, newWalletId, newToWalletId, newAmount)
        }
    }

    // ── Hapus transaksi + rollback saldo dompet ──────────────────────────────
    suspend fun deleteTransaction(transaction: TransactionEntity) {
        db.withTransaction {
            transactionDao.deleteTransaction(transaction)
            rollbackBalanceEffect(
                transaction.type,
                transaction.walletId,
                transaction.toWalletId,
                transaction.amount
            )
        }
    }

    private suspend fun applyBalanceEffect(
        type: String,
        walletId: String,
        toWalletId: String?,
        amount: Long
    ) = applyDeltas(BalanceEffect.apply(type, walletId, toWalletId, amount))

    private suspend fun rollbackBalanceEffect(
        type: String,
        walletId: String,
        toWalletId: String?,
        amount: Long
    ) = applyDeltas(BalanceEffect.rollback(type, walletId, toWalletId, amount))

    private suspend fun applyDeltas(deltas: List<WalletDelta>) {
        deltas.forEach { delta ->
            if (delta.amount >= 0) walletDao.addToBalance(delta.walletId, delta.amount)
            else walletDao.subtractFromBalance(delta.walletId, -delta.amount)
        }
    }
}
