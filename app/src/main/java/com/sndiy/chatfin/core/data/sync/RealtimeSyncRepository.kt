package com.sndiy.chatfin.core.data.sync

// RealtimeSyncRepository: sinkronisasi real-time Firestore snapshot listener.
//
// Desain:
// 1. Satu listener per koleksi user (accounts, wallets, categories, transactions, budgets)
// 2. Query tanpa compound where+order agar TIDAK memerlukan composite index manual di Firebase
// 3. MetadataChanges.INCLUDE -> UI update cepat dari cache lokal, lalu sinkron dengan server
// 4. Conflict resolution last-write-wins via updatedAt
// 5. callbackFlow + awaitClose -> listener di-unregister secara deterministik
// 6. Operasi Room dalam withTransaction {} untuk atomisitas (AGENTS.md Bagian 2.9)
// 7. Fallback ke data lokal saat offline (Firestore offline persistence + Room)

import androidx.room.withTransaction
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.Query
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
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

private const val TX_LISTEN_LIMIT      = 150L
private const val WALLET_LISTEN_LIMIT  = 50L
private const val CAT_LISTEN_LIMIT     = 200L
private const val ACCOUNT_LISTEN_LIMIT = 50L
private const val BUDGET_LISTEN_LIMIT  = 100L

@Singleton
class RealtimeSyncRepository @Inject constructor(
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

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Listen perubahan akun finansial real-time untuk user.
     */
    fun listenAccounts(uid: String): Flow<Unit> = callbackFlow {
        val query = col(uid, "accounts")

        val registration = query.addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
            if (error != null) {
                android.util.Log.e("RealtimeSync", "Account listener error: ${error.code}: ${error.message}", error)
                return@addSnapshotListener
            }
            if (snapshot == null) return@addSnapshotListener

            launch {
                try {
                    processAccountChanges(snapshot.documentChanges)
                } catch (e: Exception) {
                    android.util.Log.e("RealtimeSync", "processAccountChanges: ${e.message}", e)
                }
            }
            trySend(Unit)
        }

        awaitClose {
            android.util.Log.d("RealtimeSync", "Account listener removed")
            registration.remove()
        }
    }

    /**
     * Listen perubahan transaksi real-time untuk user.
     */
    fun listenTransactions(uid: String): Flow<Unit> = callbackFlow {
        val query = col(uid, "transactions")

        val registration = query.addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
            if (error != null) {
                android.util.Log.e("RealtimeSync", "Transaction listener error: ${error.code}: ${error.message}", error)
                syncStatusRepo.update(SyncStatus.OFFLINE)
                return@addSnapshotListener
            }
            if (snapshot == null) return@addSnapshotListener

            syncStatusRepo.update(
                when {
                    snapshot.metadata.hasPendingWrites() -> SyncStatus.SYNCING
                    snapshot.metadata.isFromCache()      -> SyncStatus.OFFLINE
                    else                                 -> SyncStatus.IN_SYNC
                }
            )

            launch {
                try {
                    processTransactionChanges(snapshot.documentChanges)
                } catch (e: Exception) {
                    android.util.Log.e("RealtimeSync", "processTransactionChanges: ${e.message}", e)
                }
            }
            trySend(Unit)
        }

        awaitClose {
            android.util.Log.d("RealtimeSync", "Transaction listener removed")
            registration.remove()
        }
    }

    /**
     * Listen perubahan wallet real-time untuk user.
     */
    fun listenWallets(uid: String): Flow<Unit> = callbackFlow {
        val query = col(uid, "wallets")

        val registration = query.addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
            if (error != null) {
                android.util.Log.e("RealtimeSync", "Wallet listener error: ${error.code}: ${error.message}", error)
                syncStatusRepo.update(SyncStatus.OFFLINE)
                return@addSnapshotListener
            }
            if (snapshot == null) return@addSnapshotListener

            syncStatusRepo.update(
                when {
                    snapshot.metadata.hasPendingWrites() -> SyncStatus.SYNCING
                    snapshot.metadata.isFromCache()      -> SyncStatus.OFFLINE
                    else                                 -> SyncStatus.IN_SYNC
                }
            )

            launch {
                try {
                    processWalletChanges(snapshot.documentChanges)
                } catch (e: Exception) {
                    android.util.Log.e("RealtimeSync", "processWalletChanges: ${e.message}", e)
                }
            }
            trySend(Unit)
        }

        awaitClose {
            android.util.Log.d("RealtimeSync", "Wallet listener removed")
            registration.remove()
        }
    }

    /**
     * Listen perubahan kategori real-time untuk user.
     */
    fun listenCategories(uid: String): Flow<Unit> = callbackFlow {
        val query = col(uid, "categories")

        val registration = query.addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
            if (error != null) {
                android.util.Log.e("RealtimeSync", "Category listener error: ${error.code}: ${error.message}", error)
                return@addSnapshotListener
            }
            if (snapshot == null) return@addSnapshotListener

            launch {
                try {
                    processCategoryChanges(snapshot.documentChanges)
                } catch (e: Exception) {
                    android.util.Log.e("RealtimeSync", "processCategoryChanges: ${e.message}", e)
                }
            }
            trySend(Unit)
        }

        awaitClose {
            android.util.Log.d("RealtimeSync", "Category listener removed")
            registration.remove()
        }
    }

    /**
     * Listen perubahan budget real-time untuk user.
     */
    fun listenBudgets(uid: String): Flow<Unit> = callbackFlow {
        val query = col(uid, "budgets")

        val registration = query.addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
            if (error != null) {
                android.util.Log.e("RealtimeSync", "Budget listener error: ${error.code}: ${error.message}", error)
                return@addSnapshotListener
            }
            if (snapshot == null) return@addSnapshotListener

            launch {
                try {
                    processBudgetChanges(snapshot.documentChanges)
                } catch (e: Exception) {
                    android.util.Log.e("RealtimeSync", "processBudgetChanges: ${e.message}", e)
                }
            }
            trySend(Unit)
        }

        awaitClose {
            android.util.Log.d("RealtimeSync", "Budget listener removed")
            registration.remove()
        }
    }

    // ── Process changes ───────────────────────────────────────────────────────

    private suspend fun processAccountChanges(changes: List<DocumentChange>) {
        if (changes.isEmpty()) return

        db.withTransaction {
            val allLocalBefore = accountDao.getAllAccounts().first()
            val currentActive = allLocalBefore.find { it.isActive }

            for (change in changes) {
                val doc = change.document
                when (change.type) {
                    DocumentChange.Type.ADDED,
                    DocumentChange.Type.MODIFIED -> {
                        val cloud = doc.toFinanceAccount() ?: continue
                        val local = accountDao.getAccountById(cloud.id)
                        val shouldKeepActive = local?.isActive ?: false
                        if (local == null || cloud.updatedAt >= local.updatedAt) {
                            accountDao.insertAccount(cloud.copy(isActive = shouldKeepActive))
                        }
                    }
                    DocumentChange.Type.REMOVED -> {
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
                    DocumentChange.Type.ADDED,
                    DocumentChange.Type.MODIFIED -> {
                        val cloud = doc.toTransaction() ?: continue
                        val local = transactionDao.getTransactionById(cloud.id)
                        if (local == null || cloud.updatedAt >= local.updatedAt) {
                            transactionDao.insertTransaction(cloud)
                            // Sync embedded items
                            transactionDao.deleteTransactionItemsByTransactionId(cloud.id)
                            val items = doc.toTransactionItems(cloud.id)
                            if (items.isNotEmpty()) transactionDao.insertTransactionItems(items)
                            hasBalanceChange = true
                        }
                    }
                    DocumentChange.Type.REMOVED -> {
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
                    DocumentChange.Type.ADDED,
                    DocumentChange.Type.MODIFIED -> {
                        val cloud = doc.toWallet() ?: continue
                        val local = walletDao.getWalletById(cloud.id)
                        when {
                            local == null -> {
                                // Wallet baru dari cloud: insert dengan balance cloud sebagai titik awal
                                // (akan direkonsiliasi recomputeWalletBalances)
                                walletDao.insertWallet(cloud)
                                hasStructuralChange = true
                            }
                            cloud.updatedAt > local.updatedAt -> {
                                // Update metadata (nama, warna, tipe, dll) tapi JANGAN timpa balance lokal.
                                // Balance truth-of-record ada di transaksi, bukan di cloud.balance.
                                walletDao.insertWallet(cloud.copy(balance = local.balance))
                                hasStructuralChange = true
                            }
                            // cloud.updatedAt <= local.updatedAt → lokal lebih baru, skip
                        }
                    }
                    DocumentChange.Type.REMOVED -> {
                        val local = walletDao.getWalletById(doc.id)
                        if (local != null) {
                            walletDao.deleteWallet(local)
                            hasStructuralChange = true
                        }
                    }
                }
            }
        }

        // Rekonsiliasi saldo setelah ada perubahan struktural wallet
        if (hasStructuralChange) recomputeWalletBalances()
    }

    private suspend fun processCategoryChanges(changes: List<DocumentChange>) {
        if (changes.isEmpty()) return

        db.withTransaction {
            for (change in changes) {
                val doc = change.document
                when (change.type) {
                    DocumentChange.Type.ADDED,
                    DocumentChange.Type.MODIFIED -> {
                        val cloud = doc.toCategory() ?: continue
                        val local = categoryDao.getCategoryById(cloud.id)
                        if (local == null || cloud.updatedAt >= local.updatedAt) {
                            categoryDao.insertCategory(cloud)
                        }
                    }
                    DocumentChange.Type.REMOVED -> {
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
                    DocumentChange.Type.ADDED,
                    DocumentChange.Type.MODIFIED -> {
                        val cloud = doc.toBudget() ?: continue
                        val local = budgetDao.getBudgetById(cloud.id)
                        if (local == null || cloud.createdAt >= local.createdAt) {
                            budgetDao.insertBudget(cloud)
                        }
                    }
                    DocumentChange.Type.REMOVED -> {
                        val local = budgetDao.getBudgetById(doc.id)
                        if (local != null) budgetDao.deleteBudget(local)
                    }
                }
            }
        }
    }

    // ── Computed balance ──────────────────────────────────────────────────────

    private suspend fun recomputeWalletBalances() {
        try {
            val allAccounts     = db.accountDao().getAllAccounts().first()
            val allWallets      = allAccounts.flatMap { walletDao.getWalletsByAccount(it.id).first() }
            val allTransactions = allAccounts.flatMap { transactionDao.getTransactionsByAccount(it.id).first() }

            val balanceMap  = mutableMapOf<String, Long>()
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

            allWallets.forEach { wallet ->
                val computed = if (wallet.id in walletHasTx) balanceMap[wallet.id] ?: 0L
                               else wallet.balance
                if (wallet.balance != computed) {
                    walletDao.updateWallet(wallet.copy(balance = computed, updatedAt = System.currentTimeMillis()))
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("RealtimeSync", "recomputeWalletBalances: ${e.message}", e)
        }
    }

    // ── Map → Entity ──────────────────────────────────────────────────────────

    private fun DocumentSnapshot.toFinanceAccount(): FinanceAccountEntity? {
        return try {
            val createdAtVal = getLong("createdAt") ?: System.currentTimeMillis()
            FinanceAccountEntity(
                id                 = getString("id") ?: id,
                name               = getString("name") ?: return null,
                iconName           = getString("iconName") ?: "account_balance_wallet",
                colorHex           = getString("colorHex") ?: "#0061A4",
                currency           = getString("currency") ?: "IDR",
                description        = getString("description")?.ifBlank { null },
                isPinProtected     = getBoolean("isPinProtected") ?: false,
                pinHash            = getString("pinHash")?.ifBlank { null },
                isBiometricEnabled = getBoolean("isBiometricEnabled") ?: false,
                isActive           = getBoolean("isActive") ?: false,
                sortOrder          = getLong("sortOrder")?.toInt() ?: 0,
                createdAt          = createdAtVal,
                updatedAt          = getLong("updatedAt") ?: createdAtVal
            )
        } catch (e: Exception) {
            android.util.Log.w("RealtimeSync", "Skip account doc $id: ${e.message}")
            null
        }
    }

    private fun DocumentSnapshot.toTransaction(): TransactionEntity? {
        return try {
            val createdAtVal = getLong("createdAt") ?: System.currentTimeMillis()
            val accountId    = getString("accountId") ?: return null
            val type         = getString("type") ?: return null
            val amount       = firestoreLong("amount") ?: return null
            val categoryId   = getString("categoryId") ?: return null
            val walletId     = getString("walletId") ?: return null
            val date         = getString("date") ?: return null
            val time         = getString("time") ?: return null
            TransactionEntity(
                id               = getString("id") ?: id,
                accountId        = accountId,
                type             = type,
                amount           = amount,
                categoryId       = categoryId,
                walletId         = walletId,
                toWalletId       = getString("toWalletId")?.ifBlank { null },
                note             = getString("note")?.ifBlank { null },
                receiptImageUri  = getString("receiptImageUri")?.ifBlank { null },
                date             = date,
                time             = time,
                transferPairId   = getString("transferPairId")?.ifBlank { null },
                isInitialBalance = getBoolean("isInitialBalance") ?: false,
                createdAt        = createdAtVal,
                updatedAt        = getLong("updatedAt") ?: createdAtVal
            )
        } catch (e: Exception) {
            android.util.Log.w("RealtimeSync", "Skip tx doc $id: ${e.message}")
            null
        }
    }

    private fun DocumentSnapshot.toWallet(): WalletEntity? {
        return try {
            val createdAtVal = getLong("createdAt") ?: System.currentTimeMillis()
            val accountId    = getString("accountId") ?: return null
            val name         = getString("name") ?: return null
            WalletEntity(
                id            = getString("id") ?: id,
                accountId     = accountId,
                name          = name,
                type          = getString("type") ?: "CASH",
                balance       = getLong("balance") ?: 0L,
                currency      = getString("currency") ?: "IDR",
                bankName      = getString("bankName")?.ifBlank { null },
                accountNumber = getString("accountNumber")?.ifBlank { null },
                iconName      = getString("iconName") ?: "payments",
                colorHex      = getString("colorHex") ?: "#1B8A4C",
                isDefault     = getBoolean("isDefault") ?: false,
                sortOrder     = getLong("sortOrder")?.toInt() ?: 0,
                createdAt     = createdAtVal,
                updatedAt     = getLong("updatedAt") ?: createdAtVal
            )
        } catch (e: Exception) {
            android.util.Log.w("RealtimeSync", "Skip wallet doc $id: ${e.message}")
            null
        }
    }

    private fun DocumentSnapshot.toCategory(): CategoryEntity? {
        return try {
            val name = getString("name")
            val type = getString("type") ?: return null
            CategoryEntity(
                id        = getString("id") ?: id,
                accountId = getString("accountId")?.ifBlank { null },
                name      = if (name.isNullOrBlank()) "Lainnya" else name,
                type      = type,
                iconName  = getString("iconName") ?: "category",
                colorHex  = getString("colorHex") ?: "#757575",
                isCustom  = getBoolean("isCustom") ?: true,
                sortOrder = getLong("sortOrder")?.toInt() ?: 0,
                updatedAt = getLong("updatedAt") ?: 0L
            )
        } catch (e: Exception) {
            android.util.Log.w("RealtimeSync", "Skip category doc $id: ${e.message}")
            null
        }
    }

    private fun DocumentSnapshot.toBudget(): BudgetEntity? {
        return try {
            val accountId   = getString("accountId") ?: return null
            val categoryId  = getString("categoryId") ?: return null
            val limitAmount = firestoreLong("limitAmount") ?: return null
            val year        = getLong("year")?.toInt() ?: return null
            val month       = getLong("month")?.toInt()
            BudgetEntity(
                id          = getString("id") ?: id,
                accountId   = accountId,
                categoryId  = categoryId,
                limitAmount = limitAmount,
                period      = getString("period") ?: "MONTHLY",
                month       = if (month == 0) null else month,
                year        = year,
                createdAt   = getLong("createdAt") ?: System.currentTimeMillis()
            )
        } catch (e: Exception) {
            android.util.Log.w("RealtimeSync", "Skip budget doc $id: ${e.message}")
            null
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun DocumentSnapshot.toTransactionItems(transactionId: String): List<TransactionItemEntity> {
        return try {
            val itemsList = get("items") as? List<Map<String, Any?>> ?: return emptyList()
            itemsList.mapNotNull { map ->
                try {
                    TransactionItemEntity(
                        id            = map["id"] as? String ?: return@mapNotNull null,
                        transactionId = transactionId,
                        name          = map["name"] as? String ?: return@mapNotNull null,
                        price         = (map["price"] as? Long)
                            ?: (map["price"] as? Double)?.toLong()
                            ?: return@mapNotNull null,
                        quantity      = (map["quantity"] as? Long)?.toInt()
                            ?: (map["quantity"] as? Double)?.toInt()
                            ?: 1
                    )
                } catch (e: Exception) {
                    android.util.Log.w("RealtimeSync", "Skip item: ${e.message}")
                    null
                }
            }
        } catch (e: Exception) {
            android.util.Log.w("RealtimeSync", "Parse items error for $transactionId: ${e.message}")
            emptyList()
        }
    }

    /** Ambil Long dari Firestore — handle Long atau Double (Firestore kadang simpan sebagai Double) */
    private fun DocumentSnapshot.firestoreLong(field: String): Long? = try {
        getLong(field)
    } catch (e: Exception) {
        try { getDouble(field)?.toLong() } catch (_: Exception) { null }
    }
}
