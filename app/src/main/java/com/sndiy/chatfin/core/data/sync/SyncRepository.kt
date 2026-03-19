package com.sndiy.chatfin.core.data.sync

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.sndiy.chatfin.core.data.local.dao.AccountDao
import com.sndiy.chatfin.core.data.local.dao.CategoryDao
import com.sndiy.chatfin.core.data.local.dao.TransactionDao
import com.sndiy.chatfin.core.data.local.dao.WalletDao
import com.sndiy.chatfin.core.data.local.entity.CategoryEntity
import com.sndiy.chatfin.core.data.local.entity.FinanceAccountEntity
import com.sndiy.chatfin.core.data.local.entity.TransactionEntity
import com.sndiy.chatfin.core.data.local.entity.WalletEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val accountDao: AccountDao,
    private val walletDao: WalletDao,
    private val categoryDao: CategoryDao,
    private val transactionDao: TransactionDao
) {
    // ── Path helper ───────────────────────────────────────────────────────────
    private fun userDoc(uid: String)                    = firestore.collection("users").document(uid)
    private fun accountsCol(uid: String)                = userDoc(uid).collection("accounts")
    private fun walletsCol(uid: String)                 = userDoc(uid).collection("wallets")
    private fun categoriesCol(uid: String)              = userDoc(uid).collection("categories")
    private fun transactionsCol(uid: String)            = userDoc(uid).collection("transactions")

    // ── Upload lokal → Firestore ──────────────────────────────────────────────
    suspend fun uploadAll(uid: String): Result<SyncStats> {
        return try {
            val accounts     = accountDao.getAllAccounts().first()
            val wallets      = accounts.flatMap { walletDao.getWalletsByAccount(it.id).first() }
            val categories   = accountDao.getAllAccounts().first().flatMap {
                categoryDao.getCategoriesByAccountAndType(it.id, "EXPENSE").first() +
                        categoryDao.getCategoriesByAccountAndType(it.id, "INCOME").first()
            }.distinctBy { it.id }
            val transactions = accounts.flatMap { transactionDao.getTransactionsByAccount(it.id).first() }

            val batch = firestore.batch()

            accounts.forEach { entity ->
                batch.set(accountsCol(uid).document(entity.id), entity.toMap(), SetOptions.merge())
            }
            wallets.forEach { entity ->
                batch.set(walletsCol(uid).document(entity.id), entity.toMap(), SetOptions.merge())
            }
            categories.forEach { entity ->
                batch.set(categoriesCol(uid).document(entity.id), entity.toMap(), SetOptions.merge())
            }

            // Transaksi banyak — commit per 500 (batas Firestore batch)
            batch.commit().await()

            val txBatches = transactions.chunked(400)
            txBatches.forEach { chunk ->
                val txBatch = firestore.batch()
                chunk.forEach { entity ->
                    txBatch.set(transactionsCol(uid).document(entity.id), entity.toMap(), SetOptions.merge())
                }
                txBatch.commit().await()
            }

            Result.success(SyncStats(
                accounts     = accounts.size,
                wallets      = wallets.size,
                categories   = categories.size,
                transactions = transactions.size
            ))
        } catch (e: Exception) {
            Result.failure(Exception("Upload gagal: ${e.message}"))
        }
    }

    // ── Download Firestore → lokal ────────────────────────────────────────────
    suspend fun downloadAll(uid: String): Result<SyncStats> {
        return try {
            val accountDocs     = accountsCol(uid).get().await()
            val walletDocs      = walletsCol(uid).get().await()
            val categoryDocs    = categoriesCol(uid).get().await()
            val transactionDocs = transactionsCol(uid).get().await()

            val accounts     = accountDocs.documents.mapNotNull     { it.toFinanceAccount() }
            val wallets      = walletDocs.documents.mapNotNull       { it.toWallet() }
            val categories   = categoryDocs.documents.mapNotNull     { it.toCategory() }
            val transactions = transactionDocs.documents.mapNotNull  { it.toTransaction() }

            accounts.forEach     { accountDao.insertAccount(it) }
            wallets.forEach      { walletDao.insertWallet(it) }
            categories.forEach   { categoryDao.insertCategory(it) }
            transactions.forEach { transactionDao.insertTransaction(it) }

            Result.success(SyncStats(
                accounts     = accounts.size,
                wallets      = wallets.size,
                categories   = categories.size,
                transactions = transactions.size
            ))
        } catch (e: Exception) {
            Result.failure(Exception("Download gagal: ${e.message}"))
        }
    }

    // ── Entity → Map (untuk Firestore) ────────────────────────────────────────
    private fun FinanceAccountEntity.toMap() = mapOf(
        "id"          to id,
        "name"        to name,
        "iconName"    to iconName,
        "colorHex"    to colorHex,
        "currency"    to currency,
        "description" to (description ?: ""),
        "isActive"    to isActive,
        "sortOrder"   to sortOrder
    )

    private fun WalletEntity.toMap() = mapOf(
        "id"        to id,
        "accountId" to accountId,
        "name"      to name,
        "type"      to type,
        "balance"   to balance,
        "currency"  to currency,
        "colorHex"  to colorHex,
        "iconName"  to iconName,
        "sortOrder" to sortOrder
    )

    private fun CategoryEntity.toMap() = mapOf(
        "id"        to id,
        "accountId" to (accountId ?: ""),
        "name"      to name,
        "type"      to type,
        "iconName"  to iconName,
        "colorHex"  to colorHex,
        "isCustom"  to isCustom,
        "sortOrder" to sortOrder
    )

    private fun TransactionEntity.toMap() = mapOf(
        "id"                to id,
        "accountId"         to accountId,
        "type"              to type,
        "amount"            to amount,
        "categoryId"        to categoryId,
        "walletId"          to walletId,
        "toWalletId"        to (toWalletId ?: ""),
        "note"              to (note ?: ""),
        "date"              to date,
        "time"              to time,
        "isRecurring"       to isRecurring,
        "recurringInterval" to (recurringInterval ?: "")
    )

    // ── Map → Entity (dari Firestore) ─────────────────────────────────────────
    private fun com.google.firebase.firestore.DocumentSnapshot.toFinanceAccount(): FinanceAccountEntity? {
        return try {
            FinanceAccountEntity(
                id          = getString("id") ?: id,
                name        = getString("name") ?: return null,
                iconName    = getString("iconName") ?: "account_balance_wallet",
                colorHex    = getString("colorHex") ?: "#0061A4",
                currency    = getString("currency") ?: "IDR",
                description = getString("description")?.ifBlank { null },
                isActive    = getBoolean("isActive") ?: false,
                sortOrder   = getLong("sortOrder")?.toInt() ?: 0
            )
        } catch (e: Exception) { null }
    }

    private fun com.google.firebase.firestore.DocumentSnapshot.toWallet(): WalletEntity? {
        return try {
            WalletEntity(
                id        = getString("id") ?: id,
                accountId = getString("accountId") ?: return null,
                name      = getString("name") ?: return null,
                type      = getString("type") ?: "CASH",
                balance   = getLong("balance") ?: 0L,
                currency  = getString("currency") ?: "IDR",
                colorHex  = getString("colorHex") ?: "#1B8A4C",
                iconName  = getString("iconName") ?: "payments",
                sortOrder = getLong("sortOrder")?.toInt() ?: 0
            )
        } catch (e: Exception) { null }
    }

    private fun com.google.firebase.firestore.DocumentSnapshot.toCategory(): CategoryEntity? {
        return try {
            CategoryEntity(
                id        = getString("id") ?: id,
                accountId = getString("accountId")?.ifBlank { null },
                name      = getString("name") ?: return null,
                type      = getString("type") ?: return null,
                iconName  = getString("iconName") ?: "category",
                colorHex  = getString("colorHex") ?: "#757575",
                isCustom  = getBoolean("isCustom") ?: true,
                sortOrder = getLong("sortOrder")?.toInt() ?: 0
            )
        } catch (e: Exception) { null }
    }

    private fun com.google.firebase.firestore.DocumentSnapshot.toTransaction(): TransactionEntity? {
        return try {
            TransactionEntity(
                id                = getString("id") ?: id,
                accountId         = getString("accountId") ?: return null,
                type              = getString("type") ?: return null,
                amount            = getLong("amount") ?: return null,
                categoryId        = getString("categoryId") ?: return null,
                walletId          = getString("walletId") ?: return null,
                toWalletId        = getString("toWalletId")?.ifBlank { null },
                note              = getString("note")?.ifBlank { null },
                date              = getString("date") ?: return null,
                time              = getString("time") ?: return null,
                isRecurring       = getBoolean("isRecurring") ?: false,
                recurringInterval = getString("recurringInterval")?.ifBlank { null }
            )
        } catch (e: Exception) { null }
    }
}

data class SyncStats(
    val accounts: Int,
    val wallets: Int,
    val categories: Int,
    val transactions: Int
)