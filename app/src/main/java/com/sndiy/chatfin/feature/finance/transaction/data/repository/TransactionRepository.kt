// app/src/main/java/com/sndiy/chatfin/feature/finance/transaction/data/repository/TransactionRepository.kt

package com.sndiy.chatfin.feature.finance.transaction.data.repository

import com.sndiy.chatfin.core.data.local.dao.CategorySum
import com.sndiy.chatfin.core.data.local.dao.DailyTotal
import com.sndiy.chatfin.core.data.local.dao.TransactionDao
import com.sndiy.chatfin.core.data.local.dao.WalletDao
import com.sndiy.chatfin.core.data.local.entity.TransactionEntity
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TransactionRepository @Inject constructor(
    private val transactionDao: TransactionDao,
    private val walletDao: WalletDao
) {
    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    fun getTransactionsByAccount(accountId: String): Flow<List<TransactionEntity>> =
        transactionDao.getTransactionsByAccount(accountId)

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

    // Tambah transaksi + update saldo dompet otomatis
    suspend fun addTransaction(
        accountId: String,
        type: String,           // INCOME | EXPENSE | TRANSFER
        amount: Long,
        categoryId: String,
        walletId: String,
        toWalletId: String? = null,
        note: String? = null,
        receiptImageUri: String? = null,
        date: LocalDate = LocalDate.now(),
        time: LocalTime = LocalTime.now(),
        isRecurring: Boolean = false,
        recurringInterval: String? = null
    ) {
        val transaction = TransactionEntity(
            id                = UUID.randomUUID().toString(),
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
            isRecurring       = isRecurring,
            recurringInterval = recurringInterval
        )
        transactionDao.insertTransaction(transaction)

        // Update saldo dompet sesuai tipe transaksi
        when (type) {
            "INCOME"   -> walletDao.addToBalance(walletId, amount)
            "EXPENSE"  -> walletDao.subtractFromBalance(walletId, amount)
            "TRANSFER" -> {
                walletDao.subtractFromBalance(walletId, amount)
                toWalletId?.let { walletDao.addToBalance(it, amount) }
            }
        }
    }

    // Hapus transaksi + rollback saldo dompet
    suspend fun deleteTransaction(transaction: TransactionEntity) {
        transactionDao.deleteTransaction(transaction)

        // Rollback saldo
        when (transaction.type) {
            "INCOME"   -> walletDao.subtractFromBalance(transaction.walletId, transaction.amount)
            "EXPENSE"  -> walletDao.addToBalance(transaction.walletId, transaction.amount)
            "TRANSFER" -> {
                walletDao.addToBalance(transaction.walletId, transaction.amount)
                transaction.toWalletId?.let {
                    walletDao.subtractFromBalance(it, transaction.amount)
                }
            }
        }
    }
}