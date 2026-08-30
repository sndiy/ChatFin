package com.sndiy.chatfin.core.data.sync

import com.sndiy.chatfin.core.data.local.ChatFinDatabase
import com.sndiy.chatfin.core.data.local.dao.AccountDao
import com.sndiy.chatfin.core.data.local.dao.BudgetDao
import com.sndiy.chatfin.core.data.local.dao.CategoryDao
import com.sndiy.chatfin.core.data.local.dao.TransactionDao
import com.sndiy.chatfin.core.data.local.dao.WalletDao
import com.sndiy.chatfin.core.data.local.entity.BudgetEntity
import com.sndiy.chatfin.core.data.local.entity.CategoryEntity
import com.sndiy.chatfin.core.data.local.entity.FinanceAccountEntity
import com.sndiy.chatfin.core.data.local.entity.TransactionEntity
import com.sndiy.chatfin.core.data.local.entity.TransactionItemEntity
import com.sndiy.chatfin.core.data.local.entity.WalletEntity
import com.sndiy.chatfin.core.data.local.withTransaction
import dev.gitlive.firebase.firestore.ChangeType
import dev.gitlive.firebase.firestore.DocumentChange
import dev.gitlive.firebase.firestore.DocumentSnapshot
import dev.gitlive.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onCompletion

/**
 * Repository sinkronisasi real-time Firestore untuk Desktop (JVM).
 *
 * Menggunakan GitLive Firebase SDK snapshots(includeMetadataChanges = true) yang setara
 * dengan MetadataChanges.INCLUDE pada Android SDK.
 *
 * Fitur:
 * 1. Snapshot listeners per koleksi (accounts, wallets, categories, budgets, transactions).
 * 2. Scope query transaksi per wallet: listenTransactionsByWallet(uid, walletId).
 * 3. Update SyncStatusRepository secara otomatis (SYNCING / OFFLINE / IN_SYNC).
 * 4. Conflict resolution Last-Write-Wins (updatedAt).
 * 5. Operasi Room atomik dalam db.withTransaction {}.
 * 6. Parsing strict Long rupiah (tolak desimal non-nol).
 * 7. Unregister listener deterministik saat Coroutine Job dibatalkan.
 */
class DesktopRealtimeSyncRepository(
    private val firestore: FirebaseFirestore,
    private val db: ChatFinDatabase,
    private val accountDao: AccountDao,
    private val walletDao: WalletDao,
    private val categoryDao: CategoryDao,
    private val transactionDao: TransactionDao,
    private val budgetDao: BudgetDao,
    private val syncStatusRepo: SyncStatusRepository
) {
    private fun col(uid: String, name: String) =
        firestore.collection("users").document(uid).collection(name)

    // ── Public Listener APIs ──────────────────────────────────────────────────

    fun listenAccounts(uid: String): Flow<Unit> = flow {
        val query = col(uid, "accounts")
        query.snapshots(includeMetadataChanges = true).collect { snapshot ->
            try {
                processAccountChanges(snapshot.documentChanges)
            } catch (e: Exception) {
                println("[DesktopRealtimeSync] Account processing error: ${e.message}")
            }
            emit(Unit)
        }
    }.onCompletion {
        println("[DesktopRealtimeSync] Account listener stopped")
    }

    fun listenWallets(uid: String): Flow<Unit> = flow {
        val query = col(uid, "wallets")
        query.snapshots(includeMetadataChanges = true).collect { snapshot ->
            updateSyncStatus(snapshot.metadata.hasPendingWrites, snapshot.metadata.isFromCache)
            try {
                processWalletChanges(snapshot.documentChanges)
            } catch (e: Exception) {
                println("[DesktopRealtimeSync] Wallet processing error: ${e.message}")
            }
            emit(Unit)
        }
    }.onCompletion {
        println("[DesktopRealtimeSync] Wallet listener stopped")
    }

    fun listenCategories(uid: String): Flow<Unit> = flow {
        val query = col(uid, "categories")
        query.snapshots(includeMetadataChanges = true).collect { snapshot ->
            try {
                processCategoryChanges(snapshot.documentChanges)
            } catch (e: Exception) {
                println("[DesktopRealtimeSync] Category processing error: ${e.message}")
            }
            emit(Unit)
        }
    }.onCompletion {
        println("[DesktopRealtimeSync] Category listener stopped")
    }

    fun listenBudgets(uid: String): Flow<Unit> = flow {
        val query = col(uid, "budgets")
        query.snapshots(includeMetadataChanges = true).collect { snapshot ->
            try {
                processBudgetChanges(snapshot.documentChanges)
            } catch (e: Exception) {
                println("[DesktopRealtimeSync] Budget processing error: ${e.message}")
            }
            emit(Unit)
        }
    }.onCompletion {
        println("[DesktopRealtimeSync] Budget listener stopped")
    }

    fun listenTransactions(uid: String): Flow<Unit> = flow {
        val query = col(uid, "transactions")
        query.snapshots(includeMetadataChanges = true).collect { snapshot ->
            updateSyncStatus(snapshot.metadata.hasPendingWrites, snapshot.metadata.isFromCache)
            try {
                processTransactionChanges(snapshot.documentChanges)
            } catch (e: Exception) {
                println("[DesktopRealtimeSync] Transaction processing error: ${e.message}")
            }
            emit(Unit)
        }
    }.onCompletion {
        println("[DesktopRealtimeSync] Transaction listener stopped")
    }

    fun listenTransactionsByWallet(uid: String, walletId: String): Flow<Unit> = flow {
        val query = col(uid, "transactions").where { "walletId" equalTo walletId }
        query.snapshots(includeMetadataChanges = true).collect { snapshot ->
            updateSyncStatus(snapshot.metadata.hasPendingWrites, snapshot.metadata.isFromCache)
            try {
                processTransactionChanges(snapshot.documentChanges)
            } catch (e: Exception) {
                println("[DesktopRealtimeSync] Scoped Wallet Transaction processing error: ${e.message}")
            }
            emit(Unit)
        }
    }.onCompletion {
        println("[DesktopRealtimeSync] Scoped Wallet Transaction listener stopped ($walletId)")
    }

    // ── Status Updater ────────────────────────────────────────────────────────

    private fun updateSyncStatus(hasPendingWrites: Boolean, isFromCache: Boolean) {
        val status = when {
            hasPendingWrites -> SyncStatus.SYNCING
            isFromCache      -> SyncStatus.OFFLINE
            else             -> SyncStatus.IN_SYNC
        }
        syncStatusRepo.update(status)
    }

    // ── Process Changes Atomically in Room ────────────────────────────────────

    private suspend fun processAccountChanges(changes: List<DocumentChange>) {
        if (changes.isEmpty()) return

        db.withTransaction {
            for (change in changes) {
                val doc = change.document
                when (change.type) {
                    ChangeType.ADDED, ChangeType.MODIFIED -> {
                        val cloud = doc.toFinanceAccount() ?: continue
                        val local = accountDao.getAccountById(cloud.id)
                        val shouldKeepActive = local?.isActive ?: false
                        if (local == null || cloud.updatedAt >= local.updatedAt) {
                            accountDao.insertAccount(cloud.copy(isActive = shouldKeepActive))
                        }
                    }
                    ChangeType.REMOVED -> {
                        val local = accountDao.getAccountById(doc.id)
                        if (local != null) accountDao.deleteAccount(local)
                    }
                }
            }

            val allLocalAfter = accountDao.getAllAccounts().first()
            val currentStillActive = allLocalAfter.find { it.isActive }
            if (currentStillActive == null && allLocalAfter.isNotEmpty()) {
                val bestAccount = allLocalAfter.find { acc ->
                    transactionDao.getTransactionsByAccount(acc.id).first().isNotEmpty()
                } ?: allLocalAfter.first()
                accountDao.switchActiveAccount(bestAccount.id)
            }
        }
    }

    private suspend fun processTransactionChanges(changes: List<DocumentChange>) {
        if (changes.isEmpty()) return
        var hasBalanceChange = false

        db.withTransaction {
            for (change in changes) {
                val doc = change.document
                when (change.type) {
                    ChangeType.ADDED, ChangeType.MODIFIED -> {
                        val cloud = doc.toTransaction() ?: continue
                        val local = transactionDao.getTransactionById(cloud.id)
                        if (local == null || cloud.updatedAt >= local.updatedAt) {
                            transactionDao.insertTransaction(cloud)
                            transactionDao.deleteTransactionItemsByTransactionId(cloud.id)
                            val items = doc.toTransactionItems(cloud.id)
                            if (items.isNotEmpty()) transactionDao.insertTransactionItems(items)
                            hasBalanceChange = true
                        }
                    }
                    ChangeType.REMOVED -> {
                        val local = transactionDao.getTransactionById(doc.id)
                        if (local != null) {
                            transactionDao.deleteTransactionItemsByTransactionId(doc.id)
                            transactionDao.deleteTransaction(local)
                            hasBalanceChange = true
                        }
                    }
                }
            }
        }

        if (hasBalanceChange) recomputeWalletBalances()
    }

    private suspend fun processWalletChanges(changes: List<DocumentChange>) {
        if (changes.isEmpty()) return
        var hasStructuralChange = false

        db.withTransaction {
            for (change in changes) {
                val doc = change.document
                when (change.type) {
                    ChangeType.ADDED, ChangeType.MODIFIED -> {
                        val cloud = doc.toWallet() ?: continue
                        val local = walletDao.getWalletById(cloud.id)
                        when {
                            local == null -> {
                                walletDao.insertWallet(cloud)
                                hasStructuralChange = true
                            }
                            cloud.updatedAt > local.updatedAt -> {
                                walletDao.insertWallet(cloud.copy(balance = local.balance))
                                hasStructuralChange = true
                            }
                        }
                    }
                    ChangeType.REMOVED -> {
                        val local = walletDao.getWalletById(doc.id)
                        if (local != null) {
                            walletDao.deleteWallet(local)
                            hasStructuralChange = true
                        }
                    }
                }
            }
        }

        if (hasStructuralChange) recomputeWalletBalances()
    }

    private suspend fun processCategoryChanges(changes: List<DocumentChange>) {
        if (changes.isEmpty()) return

        db.withTransaction {
            for (change in changes) {
                val doc = change.document
                when (change.type) {
                    ChangeType.ADDED, ChangeType.MODIFIED -> {
                        val cloud = doc.toCategory() ?: continue
                        val local = categoryDao.getCategoryById(cloud.id)
                        if (local == null || cloud.updatedAt >= local.updatedAt) {
                            categoryDao.insertCategory(cloud)
                        }
                    }
                    ChangeType.REMOVED -> {
                        val local = categoryDao.getCategoryById(doc.id)
                        if (local != null) categoryDao.deleteCategory(local)
                    }
                }
            }
        }
    }

    private suspend fun processBudgetChanges(changes: List<DocumentChange>) {
        if (changes.isEmpty()) return

        db.withTransaction {
            for (change in changes) {
                val doc = change.document
                when (change.type) {
                    ChangeType.ADDED, ChangeType.MODIFIED -> {
                        val cloud = doc.toBudget() ?: continue
                        val local = budgetDao.getBudgetById(cloud.id)
                        if (local == null || cloud.createdAt >= local.createdAt) {
                            budgetDao.insertBudget(cloud)
                        }
                    }
                    ChangeType.REMOVED -> {
                        val local = budgetDao.getBudgetById(doc.id)
                        if (local != null) budgetDao.deleteBudget(local)
                    }
                }
            }
        }
    }

    private suspend fun recomputeWalletBalances() {
        try {
            val allAccounts = db.accountDao().getAllAccounts().first()
            val allWallets = allAccounts.flatMap { walletDao.getWalletsByAccount(it.id).first() }
            val allTransactions = allAccounts.flatMap { transactionDao.getTransactionsByAccount(it.id).first() }

            val balanceMap = mutableMapOf<String, Long>()
            val walletHasTx = mutableSetOf<String>()
            allWallets.forEach { balanceMap[it.id] = 0L }

            allTransactions.forEach { tx ->
                walletHasTx.add(tx.walletId)
                when (tx.type) {
                    "INCOME"   -> balanceMap[tx.walletId] = (balanceMap[tx.walletId] ?: 0L) + tx.amount
                    "EXPENSE"  -> balanceMap[tx.walletId] = (balanceMap[tx.walletId] ?: 0L) - tx.amount
                    "TRANSFER" -> {
                        balanceMap[tx.walletId] = (balanceMap[tx.walletId] ?: 0L) - tx.amount
                        tx.toWalletId?.let {
                            walletHasTx.add(it)
                            balanceMap[it] = (balanceMap[it] ?: 0L) + tx.amount
                        }
                    }
                }
            }

            db.withTransaction {
                allWallets.forEach { wallet ->
                    val computed = if (wallet.id in walletHasTx) balanceMap[wallet.id] ?: 0L
                    else wallet.balance
                    if (wallet.balance != computed) {
                        walletDao.updateWallet(wallet.copy(balance = computed, updatedAt = System.currentTimeMillis()))
                    }
                }
            }
        } catch (e: Exception) {
            println("[DesktopRealtimeSync] recomputeWalletBalances error: ${e.message}")
        }
    }

    // ── Entity Parsers with Strict Long Integrity ─────────────────────────────

    private fun DocumentSnapshot.toFinanceAccount(): FinanceAccountEntity? {
        return try {
            val name = strictString("name") ?: return null
            val createdAtVal = firestoreStrictLong("createdAt") ?: System.currentTimeMillis()
            FinanceAccountEntity(
                id = strictString("id") ?: id,
                name = name,
                iconName = strictString("iconName") ?: "account_balance_wallet",
                colorHex = strictString("colorHex") ?: "#0061A4",
                currency = strictString("currency") ?: "IDR",
                description = strictString("description")?.ifBlank { null },
                isPinProtected = strictBoolean("isPinProtected"),
                pinHash = strictString("pinHash")?.ifBlank { null },
                isBiometricEnabled = strictBoolean("isBiometricEnabled"),
                isActive = strictBoolean("isActive"),
                sortOrder = strictInt("sortOrder"),
                createdAt = createdAtVal,
                updatedAt = firestoreStrictLong("updatedAt") ?: createdAtVal
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun DocumentSnapshot.toWallet(): WalletEntity? {
        return try {
            val accountId = strictString("accountId") ?: return null
            val name = strictString("name") ?: return null
            val balance = firestoreStrictLong("balance") ?: return null
            val createdAtVal = firestoreStrictLong("createdAt") ?: System.currentTimeMillis()
            WalletEntity(
                id = strictString("id") ?: id,
                accountId = accountId,
                name = name,
                type = strictString("type") ?: "CASH",
                balance = balance,
                currency = strictString("currency") ?: "IDR",
                bankName = strictString("bankName")?.ifBlank { null },
                accountNumber = strictString("accountNumber")?.ifBlank { null },
                iconName = strictString("iconName") ?: "payments",
                colorHex = strictString("colorHex") ?: "#1B8A4C",
                isDefault = strictBoolean("isDefault"),
                sortOrder = strictInt("sortOrder"),
                createdAt = createdAtVal,
                updatedAt = firestoreStrictLong("updatedAt") ?: createdAtVal
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun DocumentSnapshot.toCategory(): CategoryEntity? {
        return try {
            val type = strictString("type") ?: return null
            val name = strictString("name")
            CategoryEntity(
                id = strictString("id") ?: id,
                accountId = strictString("accountId")?.ifBlank { null },
                name = if (name.isNullOrBlank()) "Lainnya" else name,
                type = type,
                iconName = strictString("iconName") ?: "category",
                colorHex = strictString("colorHex") ?: "#757575",
                isCustom = strictBoolean("isCustom", true),
                sortOrder = strictInt("sortOrder"),
                updatedAt = firestoreStrictLong("updatedAt") ?: 0L
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun DocumentSnapshot.toBudget(): BudgetEntity? {
        return try {
            val accountId = strictString("accountId") ?: return null
            val categoryId = strictString("categoryId") ?: return null
            val limitAmount = firestoreStrictLong("limitAmount") ?: return null
            val year = strictInt("year", 0)
            if (year <= 0) return null
            val month = strictInt("month", 0)
            BudgetEntity(
                id = strictString("id") ?: id,
                accountId = accountId,
                categoryId = categoryId,
                limitAmount = limitAmount,
                period = strictString("period") ?: "MONTHLY",
                month = if (month == 0) null else month,
                year = year,
                createdAt = firestoreStrictLong("createdAt") ?: System.currentTimeMillis()
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun DocumentSnapshot.toTransaction(): TransactionEntity? {
        return try {
            val accountId = strictString("accountId") ?: return null
            val type = strictString("type") ?: return null
            val amount = firestoreStrictLong("amount") ?: return null
            val categoryId = strictString("categoryId") ?: return null
            val walletId = strictString("walletId") ?: return null
            val date = strictString("date") ?: return null
            val time = strictString("time") ?: return null
            val createdAtVal = firestoreStrictLong("createdAt") ?: System.currentTimeMillis()
            TransactionEntity(
                id = strictString("id") ?: id,
                accountId = accountId,
                type = type,
                amount = amount,
                categoryId = categoryId,
                walletId = walletId,
                toWalletId = strictString("toWalletId")?.ifBlank { null },
                note = strictString("note")?.ifBlank { null },
                receiptImageUri = strictString("receiptImageUri")?.ifBlank { null },
                date = date,
                time = time,
                transferPairId = strictString("transferPairId")?.ifBlank { null },
                isInitialBalance = strictBoolean("isInitialBalance"),
                createdAt = createdAtVal,
                updatedAt = firestoreStrictLong("updatedAt") ?: createdAtVal
            )
        } catch (_: Exception) {
            null
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun DocumentSnapshot.toTransactionItems(transactionId: String): List<TransactionItemEntity> {
        return try {
            val itemsList: List<Map<String, Any?>>? = get("items")
            if (itemsList == null) return emptyList()
            itemsList.mapNotNull { map ->
                try {
                    val rawPrice: Number? = map["price"] as? Number
                    val priceVal = rawPrice?.toDouble()
                    val validPrice = if (priceVal != null && priceVal % 1.0 == 0.0) priceVal.toLong() else null ?: return@mapNotNull null
                    TransactionItemEntity(
                        id = map["id"] as? String ?: return@mapNotNull null,
                        transactionId = transactionId,
                        name = map["name"] as? String ?: return@mapNotNull null,
                        price = validPrice,
                        quantity = (map["quantity"] as? Number)?.toInt() ?: 1
                    )
                } catch (_: Exception) {
                    null
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }
}
