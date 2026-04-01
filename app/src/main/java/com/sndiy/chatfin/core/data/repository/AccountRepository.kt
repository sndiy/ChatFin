// app/src/main/java/com/sndiy/chatfin/feature/finance/account/data/repository/AccountRepository.kt

package com.sndiy.chatfin.feature.finance.account.data.repository

import com.sndiy.chatfin.core.data.local.dao.AccountDao
import com.sndiy.chatfin.core.data.local.dao.CategoryDao
import com.sndiy.chatfin.core.data.local.dao.WalletDao
import com.sndiy.chatfin.core.data.local.entity.FinanceAccountEntity
import com.sndiy.chatfin.core.data.local.entity.WalletEntity
import com.sndiy.chatfin.core.data.security.SecureStorage
import com.sndiy.chatfin.core.data.local.DefaultCategories
import kotlinx.coroutines.flow.Flow
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AccountRepository @Inject constructor(
    private val accountDao: AccountDao,
    private val walletDao: WalletDao,
    private val categoryDao: CategoryDao,
    private val secureStorage: SecureStorage
) {

    // Ambil semua akun (reactive, update otomatis saat data berubah)
    fun getAllAccounts(): Flow<List<FinanceAccountEntity>> =
        accountDao.getAllAccounts()

    // Ambil akun yang sedang aktif
    fun getActiveAccount(): Flow<FinanceAccountEntity?> =
        accountDao.getActiveAccount()

    // Ambil satu akun berdasarkan ID
    suspend fun getAccountById(id: String): FinanceAccountEntity? =
        accountDao.getAccountById(id)

    // Buat akun baru + otomatis seed dompet "Kas" dan kategori default
    suspend fun createAccount(
        name: String,
        iconName: String,
        colorHex: String,
        currency: String = "IDR",
        description: String? = null
    ): String {
        val accountId = UUID.randomUUID().toString()

        // Simpan akun
        accountDao.insertAccount(
            FinanceAccountEntity(
                id          = accountId,
                name        = name,
                iconName    = iconName,
                colorHex    = colorHex,
                currency    = currency,
                description = description,
                isActive    = false
            )
        )

        // Otomatis buat dompet "Kas" sebagai default
        walletDao.insertWallet(
            WalletEntity(
                id        = UUID.randomUUID().toString(),
                accountId = accountId,
                name      = "Kas",
                type      = "CASH",
                balance   = 0L,
                currency  = currency,
                colorHex  = "#1B8A4C",
                iconName  = "payments",
                sortOrder = 0
            )
        )

        // Seed kategori default global (skip jika sudah ada)
        categoryDao.insertCategories(DefaultCategories.all)

        return accountId
    }

    // Update akun yang sudah ada
    suspend fun updateAccount(account: FinanceAccountEntity) =
        accountDao.updateAccount(account)

    // Hapus akun
    suspend fun deleteAccount(account: FinanceAccountEntity) =
        accountDao.deleteAccount(account)

    // Ganti akun aktif + simpan ke SecureStorage
    suspend fun switchActiveAccount(accountId: String) {
        accountDao.switchActiveAccount(accountId)
        secureStorage.activeAccountId = accountId
    }

    // Ambil ID akun aktif dari SecureStorage (untuk restore saat app restart)
    fun getSavedActiveAccountId(): String? =
        secureStorage.activeAccountId
}