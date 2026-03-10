package com.sndiy.chatfin.feature.settings.backup

import android.content.Context
import android.net.Uri
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.sndiy.chatfin.core.data.local.dao.AccountDao
import com.sndiy.chatfin.core.data.local.dao.CategoryDao
import com.sndiy.chatfin.core.data.local.dao.TransactionDao
import com.sndiy.chatfin.core.data.local.dao.WalletDao
import com.sndiy.chatfin.core.data.local.entity.CategoryEntity
import com.sndiy.chatfin.core.data.local.entity.FinanceAccountEntity
import com.sndiy.chatfin.core.data.local.entity.TransactionEntity
import com.sndiy.chatfin.core.data.local.entity.WalletEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BackupRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val accountDao: AccountDao,
    private val walletDao: WalletDao,
    private val categoryDao: CategoryDao,
    private val transactionDao: TransactionDao
) {
    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()

    // ── Export ────────────────────────────────────────────────────────────────
    suspend fun exportToUri(uri: Uri): Result<String> {
        return try {
            val accounts     = accountDao.getAllAccounts().first()
            val wallets      = accounts.flatMap { walletDao.getWalletsByAccount(it.id).first() }
            val categories   = accounts.flatMap {
                categoryDao.getCategoriesByAccountAndType(it.id, "EXPENSE").first() +
                        categoryDao.getCategoriesByAccountAndType(it.id, "INCOME").first()
            }.distinctBy { it.id }
            val transactions = accounts.flatMap { transactionDao.getTransactionsByAccount(it.id).first() }

            val backup = BackupData(
                version      = 1,
                exportedAt   = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")),
                accounts     = accounts,
                wallets      = wallets,
                categories   = categories,
                transactions = transactions
            )

            val json = gson.toJson(backup)
            context.contentResolver.openOutputStream(uri)?.use { stream ->
                stream.write(json.toByteArray())
            } ?: return Result.failure(Exception("Tidak bisa membuka file"))

            Result.success("Berhasil export ${transactions.size} transaksi")
        } catch (e: Exception) {
            Result.failure(Exception("Export gagal: ${e.message}"))
        }
    }

    // ── Import ────────────────────────────────────────────────────────────────
    suspend fun importFromUri(uri: Uri): Result<String> {
        return try {
            val json = context.contentResolver.openInputStream(uri)?.use { stream ->
                stream.bufferedReader().readText()
            } ?: return Result.failure(Exception("Tidak bisa membaca file"))

            val backup = gson.fromJson(json, BackupData::class.java)
                ?: return Result.failure(Exception("Format file tidak valid"))

            if (backup.version != 1) {
                return Result.failure(Exception("Versi backup tidak kompatibel"))
            }

            // Insert semua data — Room akan skip duplicate (INSERT OR IGNORE)
            backup.accounts.forEach     { accountDao.insertAccount(it) }
            backup.wallets.forEach      { walletDao.insertWallet(it) }
            backup.categories.forEach   { categoryDao.insertCategory(it) }
            backup.transactions.forEach { transactionDao.insertTransaction(it) }

            Result.success(
                "Berhasil import:\n" +
                        "• ${backup.accounts.size} akun\n" +
                        "• ${backup.wallets.size} dompet\n" +
                        "• ${backup.categories.size} kategori\n" +
                        "• ${backup.transactions.size} transaksi"
            )
        } catch (e: Exception) {
            Result.failure(Exception("Import gagal: ${e.message}"))
        }
    }

    fun generateFileName(): String {
        val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
        return "chatfin_backup_$timestamp.json"
    }
}