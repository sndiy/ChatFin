// app/src/main/java/com/sndiy/chatfin/feature/finance/transaction/ui/TransactionViewModel.kt

package com.sndiy.chatfin.feature.finance.transaction.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sndiy.chatfin.core.data.local.entity.CategoryEntity
import com.sndiy.chatfin.core.data.local.entity.TransactionEntity
import com.sndiy.chatfin.core.data.local.entity.WalletEntity
import com.sndiy.chatfin.feature.finance.account.data.repository.AccountRepository
import com.sndiy.chatfin.feature.finance.transaction.data.repository.CategoryRepository
import com.sndiy.chatfin.feature.finance.transaction.data.repository.TransactionRepository
import com.sndiy.chatfin.feature.finance.transaction.data.repository.WalletRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject

// ── Tipe Transaksi ────────────────────────────────────────────────────────────
enum class TransactionType(val label: String) {
    EXPENSE("Pengeluaran"),
    INCOME("Pemasukan"),
    TRANSFER("Transfer")
}

// ── UI State form transaksi ───────────────────────────────────────────────────
data class TransactionFormState(
    val type: TransactionType        = TransactionType.EXPENSE,
    val amount: String               = "",
    val selectedCategory: CategoryEntity? = null,
    val selectedWallet: WalletEntity? = null,
    val selectedToWallet: WalletEntity? = null,  // khusus transfer
    val note: String                 = "",
    val date: LocalDate              = LocalDate.now(),
    val time: LocalTime              = LocalTime.now(),
    val isRecurring: Boolean         = false,
    val recurringInterval: String?   = null,
    val amountError: String?         = null,
    val categoryError: String?       = null,
    val walletError: String?         = null,
    val isLoading: Boolean           = false,
    val isSaved: Boolean             = false
)

// ── UI State list transaksi ───────────────────────────────────────────────────
data class TransactionListUiState(
    val transactions: List<TransactionEntity> = emptyList(),
    val wallets: List<WalletEntity>           = emptyList(),
    val expenseCategories: List<CategoryEntity> = emptyList(),
    val incomeCategories: List<CategoryEntity>  = emptyList(),
    val isLoading: Boolean  = false,
    val errorMessage: String? = null,
    val successMessage: String? = null
)

@HiltViewModel
class TransactionViewModel @Inject constructor(
    private val transactionRepo: TransactionRepository,
    private val walletRepo: WalletRepository,
    private val categoryRepo: CategoryRepository,
    private val accountRepo: AccountRepository
) : ViewModel() {

    private val _listState = MutableStateFlow(TransactionListUiState())
    val listState: StateFlow<TransactionListUiState> = _listState.asStateFlow()

    private val _formState = MutableStateFlow(TransactionFormState())
    val formState: StateFlow<TransactionFormState> = _formState.asStateFlow()

    // Simpan accountId aktif
    private var activeAccountId: String? = null

    init {
        observeActiveAccount()
    }

    private fun observeActiveAccount() {
        viewModelScope.launch {
            accountRepo.getActiveAccount().collect { account ->
                account?.let {
                    activeAccountId = it.id
                    loadData(it.id)
                }
            }
        }
    }

    private fun loadData(accountId: String) {
        viewModelScope.launch {
            combine(
                transactionRepo.getTransactionsByAccount(accountId),
                walletRepo.getWalletsByAccount(accountId),
                categoryRepo.getCategoriesByAccountAndType(accountId, "EXPENSE"),
                categoryRepo.getCategoriesByAccountAndType(accountId, "INCOME")
            ) { transactions, wallets, expCats, incCats ->
                TransactionListUiState(
                    transactions      = transactions,
                    wallets           = wallets,
                    expenseCategories = expCats,
                    incomeCategories  = incCats
                )
            }.collect { _listState.value = it }
        }
    }

    // ── Form actions ──────────────────────────────────────────────────────────

    fun onTypeChange(type: TransactionType) {
        _formState.value = _formState.value.copy(
            type             = type,
            selectedCategory = null,  // reset kategori saat ganti tipe
            categoryError    = null
        )
    }

    fun onAmountChange(value: String) {
        // Hanya izinkan angka
        val filtered = value.filter { it.isDigit() }
        _formState.value = _formState.value.copy(
            amount      = filtered,
            amountError = null
        )
    }

    fun onCategorySelect(category: CategoryEntity) {
        _formState.value = _formState.value.copy(
            selectedCategory = category,
            categoryError    = null
        )
    }

    fun onWalletSelect(wallet: WalletEntity) {
        _formState.value = _formState.value.copy(
            selectedWallet = wallet,
            walletError    = null
        )
    }

    fun onToWalletSelect(wallet: WalletEntity) {
        _formState.value = _formState.value.copy(selectedToWallet = wallet)
    }

    fun onNoteChange(value: String) {
        _formState.value = _formState.value.copy(note = value)
    }

    fun onDateChange(date: LocalDate) {
        _formState.value = _formState.value.copy(date = date)
    }

    fun onTimeChange(time: LocalTime) {
        _formState.value = _formState.value.copy(time = time)
    }

    fun onRecurringChange(isRecurring: Boolean) {
        _formState.value = _formState.value.copy(
            isRecurring       = isRecurring,
            recurringInterval = if (isRecurring) "MONTHLY" else null
        )
    }

    fun onRecurringIntervalChange(interval: String) {
        _formState.value = _formState.value.copy(recurringInterval = interval)
    }

    fun resetForm() {
        _formState.value = TransactionFormState()
    }

    // ── Simpan transaksi ──────────────────────────────────────────────────────
    fun saveTransaction() {
        val form      = _formState.value
        val accountId = activeAccountId ?: return
        var hasError  = false

        // Validasi amount
        if (form.amount.isBlank() || form.amount.toLongOrNull() == null || form.amount.toLong() <= 0) {
            _formState.value = _formState.value.copy(amountError = "Nominal tidak valid")
            hasError = true
        }

        // Validasi kategori (tidak perlu untuk transfer)
        if (form.type != TransactionType.TRANSFER && form.selectedCategory == null) {
            _formState.value = _formState.value.copy(categoryError = "Pilih kategori")
            hasError = true
        }

        // Validasi dompet
        if (form.selectedWallet == null) {
            _formState.value = _formState.value.copy(walletError = "Pilih dompet")
            hasError = true
        }

        if (hasError) return

        _formState.value = _formState.value.copy(isLoading = true)

        viewModelScope.launch {
            try {
                transactionRepo.addTransaction(
                    accountId         = accountId,
                    type              = form.type.name,
                    amount            = form.amount.toLong(),
                    categoryId        = form.selectedCategory?.id ?: "transfer",
                    walletId          = form.selectedWallet!!.id,
                    toWalletId        = form.selectedToWallet?.id,
                    note              = form.note.trim().ifBlank { null },
                    date              = form.date,
                    time              = form.time,
                    isRecurring       = form.isRecurring,
                    recurringInterval = form.recurringInterval
                )
                _formState.value = _formState.value.copy(isLoading = false, isSaved = true)
            } catch (e: Exception) {
                _formState.value = _formState.value.copy(isLoading = false)
                _listState.value = _listState.value.copy(
                    errorMessage = "Gagal menyimpan: ${e.message}"
                )
            }
        }
    }

    // ── Hapus transaksi ───────────────────────────────────────────────────────
    fun deleteTransaction(transaction: TransactionEntity) {
        viewModelScope.launch {
            try {
                transactionRepo.deleteTransaction(transaction)
                _listState.value = _listState.value.copy(successMessage = "Transaksi dihapus")
            } catch (e: Exception) {
                _listState.value = _listState.value.copy(
                    errorMessage = "Gagal menghapus: ${e.message}"
                )
            }
        }
    }

    fun clearMessages() {
        _listState.value = _listState.value.copy(errorMessage = null, successMessage = null)
    }
}