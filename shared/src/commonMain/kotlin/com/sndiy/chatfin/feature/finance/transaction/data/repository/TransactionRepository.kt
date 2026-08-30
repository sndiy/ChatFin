package com.sndiy.chatfin.feature.finance.transaction.data.repository

import com.sndiy.chatfin.core.data.local.ChatFinDatabase
import com.sndiy.chatfin.core.data.local.withTransaction
import com.sndiy.chatfin.core.data.local.dao.CategorySum
import com.sndiy.chatfin.core.data.local.dao.DailyTotal
import com.sndiy.chatfin.core.data.local.dao.TransactionDao
import com.sndiy.chatfin.core.data.local.dao.WalletDao
import com.sndiy.chatfin.core.data.local.entity.TransactionEntity
import com.sndiy.chatfin.core.data.local.entity.TransactionItemEntity
import com.sndiy.chatfin.core.data.local.entity.TransactionWithItems
import com.sndiy.chatfin.core.data.sync.OutboundSync
import com.sndiy.chatfin.core.domain.BalanceEffect
import com.sndiy.chatfin.core.domain.WalletDelta
import com.sndiy.chatfin.core.ocr.ParsedReceiptItem
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.UUID

class TransactionRepository(
    private val db: ChatFinDatabase,
    private val transactionDao: TransactionDao,
    private val walletDao: WalletDao,
    private val outboundSync: OutboundSync
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

    fun getTotalIncome(accountId: String, startMonth: LocalDate): Flow<Long?> {
        val startDate = startMonth.withDayOfMonth(1)
        val endDate   = startMonth.withDayOfMonth(startMonth.lengthOfMonth())
        return getTotalIncome(accountId, startDate, endDate)
    }

    fun getTotalExpense(accountId: String, startMonth: LocalDate): Flow<Long?> {
        val startDate = startMonth.withDayOfMonth(1)
        val endDate   = startMonth.withDayOfMonth(startMonth.lengthOfMonth())
        return getTotalExpense(accountId, startDate, endDate)
    }

    fun getExpenseSumByCategory(
        accountId: String,
        startDate: LocalDate,
        endDate: LocalDate
    ): Flow<List<CategorySum>> = transactionDao.getExpenseSumByCategory(
        accountId = accountId,
        startDate = startDate.format(dateFormatter),
        endDate   = endDate.format(dateFormatter)
    )

    fun getDailyExpense(
        accountId: String,
        startDate: LocalDate,
        endDate: LocalDate
    ): Flow<List<DailyTotal>> = transactionDao.getDailyExpenseInPeriod(
        accountId = accountId,
        startDate = startDate.format(dateFormatter),
        endDate   = endDate.format(dateFormatter)
    )

    suspend fun getTransactionById(id: String): TransactionEntity? =
        transactionDao.getTransactionById(id)

    suspend fun getTransactionWithItemsById(id: String): TransactionWithItems? =
        transactionDao.getTransactionWithItemsById(id)

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
    ) = addTransactionWithItems(
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
        val now = System.currentTimeMillis()

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
            time              = time.format(timeFormatter),
            createdAt         = now,
            updatedAt         = now
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

        outboundSync.pushTransaction(transaction, itemEntities)
    }

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
        updateTransactionWithItems(
            oldTransaction = oldTransaction,
            newType = newType,
            newAmount = newAmount,
            newCategoryId = newCategoryId,
            newWalletId = newWalletId,
            newToWalletId = newToWalletId,
            newNote = newNote,
            newDate = newDate,
            newTime = newTime,
            newItems = emptyList()
        )
    }

    suspend fun updateTransactionWithItems(
        oldTransaction: TransactionEntity,
        newType: String,
        newAmount: Long,
        newCategoryId: String,
        newWalletId: String,
        newToWalletId: String? = null,
        newNote: String? = null,
        newDate: LocalDate,
        newTime: LocalTime,
        newItems: List<TransactionItemEntity> = emptyList()
    ) {
        val updated = oldTransaction.copy(
            type       = newType,
            amount     = newAmount,
            categoryId = newCategoryId,
            walletId   = newWalletId,
            toWalletId = newToWalletId,
            note       = newNote,
            date       = newDate.format(dateFormatter),
            time       = newTime.format(timeFormatter),
            updatedAt  = System.currentTimeMillis()
        )

        val preparedItems = if (newItems.isNotEmpty()) {
            newItems.filter { it.name.isNotBlank() }.map {
                if (it.transactionId.isBlank()) it.copy(transactionId = oldTransaction.id) else it
            }
        } else {
            emptyList()
        }

        db.withTransaction {
            rollbackBalanceEffect(
                oldTransaction.type,
                oldTransaction.walletId,
                oldTransaction.toWalletId,
                oldTransaction.amount
            )

            transactionDao.updateTransaction(updated)

            // Re-insert items
            transactionDao.deleteTransactionItemsByTransactionId(oldTransaction.id)
            if (preparedItems.isNotEmpty()) {
                transactionDao.insertTransactionItems(preparedItems)
            }

            applyBalanceEffect(newType, newWalletId, newToWalletId, newAmount)
        }

        outboundSync.pushTransaction(updated, preparedItems)
        if (oldTransaction.walletId != newWalletId) {
            outboundSync.pushWalletById(oldTransaction.walletId)
        }
        if (oldTransaction.toWalletId != null && oldTransaction.toWalletId != newToWalletId) {
            outboundSync.pushWalletById(oldTransaction.toWalletId)
        }
    }

    suspend fun deleteTransaction(transaction: TransactionEntity) {
        db.withTransaction {
            transactionDao.deleteTransactionItemsByTransactionId(transaction.id)
            transactionDao.deleteTransaction(transaction)
            rollbackBalanceEffect(
                transaction.type,
                transaction.walletId,
                transaction.toWalletId,
                transaction.amount
            )
        }

        outboundSync.deleteTransaction(transaction.id, transaction.walletId, transaction.toWalletId)
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
