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

enum class TransactionType(val label: String) {
    EXPENSE("Pengeluaran"), INCOME("Pemasukan"), TRANSFER("Transfer")
}

data class TransactionFormState(
    val editingId: String?                = null,
    val type: TransactionType             = TransactionType.EXPENSE,
    val amount: String                    = "",
    val selectedCategory: CategoryEntity? = null,
    val selectedWallet: WalletEntity?     = null,
    val selectedToWallet: WalletEntity?   = null,
    val note: String                      = "",
    val date: LocalDate                   = LocalDate.now(),
    val time: LocalTime                   = LocalTime.now(),
    val isRecurring: Boolean              = false,
    val recurringInterval: String?        = null,
    val amountError: String?              = null,
    val categoryError: String?            = null,
    val walletError: String?              = null,
    val isLoading: Boolean                = false,
    val isSaved: Boolean                  = false
)

data class DateFilter(
    val startDate: LocalDate? = null,
    val endDate: LocalDate?   = null
) {
    val isActive: Boolean get() = startDate != null || endDate != null
    val label: String get() = when {
        startDate != null && endDate != null -> {
            val fmt = java.time.format.DateTimeFormatter.ofPattern("dd/MM")
            "${startDate.format(fmt)} - ${endDate.format(fmt)}"
        }
        startDate != null -> ">= ${startDate.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM"))}"
        endDate   != null -> "<= ${endDate.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM"))}"
        else -> "Semua tanggal"
    }
}

data class TransactionListUiState(
    val transactions: List<TransactionEntity>   = emptyList(),
    val wallets: List<WalletEntity>             = emptyList(),
    val expenseCategories: List<CategoryEntity> = emptyList(),
    val incomeCategories: List<CategoryEntity>  = emptyList(),
    val dateFilter: DateFilter                  = DateFilter(),
    val isLoading: Boolean                      = false,
    val errorMessage: String?                   = null,
    val successMessage: String?                 = null
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

    private var activeAccountId: String? = null

    init { observeActiveAccount() }

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
                _listState.value.copy(
                    transactions      = transactions,
                    wallets           = wallets,
                    expenseCategories = expCats,
                    incomeCategories  = incCats
                )
            }.collect { _listState.value = it }
        }
    }

    // ── Filter tanggal ────────────────────────────────────────────────────────
    fun setDateFilter(startDate: LocalDate?, endDate: LocalDate?) {
        _listState.value = _listState.value.copy(
            dateFilter = DateFilter(startDate = startDate, endDate = endDate)
        )
    }

    fun clearDateFilter() {
        _listState.value = _listState.value.copy(dateFilter = DateFilter())
    }

    // ── Load transaksi untuk diedit ───────────────────────────────────────────
    fun loadForEdit(transaction: TransactionEntity) {
        val state    = _listState.value
        val allCats  = state.expenseCategories + state.incomeCategories
        val category = allCats.find { it.id == transaction.categoryId }
        val wallet   = state.wallets.find { it.id == transaction.walletId }
        val type     = when (transaction.type) {
            "INCOME"   -> TransactionType.INCOME
            "TRANSFER" -> TransactionType.TRANSFER
            else       -> TransactionType.EXPENSE
        }
        _formState.value = TransactionFormState(
            editingId        = transaction.id,
            type             = type,
            amount           = transaction.amount.toString(),
            selectedCategory = category,
            selectedWallet   = wallet,
            note             = transaction.note ?: "",
            date             = LocalDate.parse(transaction.date),
            time             = LocalTime.parse(transaction.time)
        )
    }

    fun onTypeChange(type: TransactionType) {
        _formState.value = _formState.value.copy(
            type             = type,
            selectedCategory = null,
            categoryError    = null
        )
    }

    fun onAmountChange(value: String) {
        _formState.value = _formState.value.copy(
            amount      = value.filter { it.isDigit() },
            amountError = null
        )
    }

    fun onCategorySelect(category: CategoryEntity) {
        _formState.value = _formState.value.copy(selectedCategory = category, categoryError = null)
    }

    fun onWalletSelect(wallet: WalletEntity) {
        _formState.value = _formState.value.copy(selectedWallet = wallet, walletError = null)
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

    fun saveTransaction() {
        val form      = _formState.value
        val accountId = activeAccountId ?: return
        var hasError  = false

        if (form.amount.isBlank() || form.amount.toLongOrNull() == null || form.amount.toLong() <= 0) {
            _formState.value = _formState.value.copy(amountError = "Nominal tidak valid")
            hasError = true
        }
        if (form.type != TransactionType.TRANSFER && form.selectedCategory == null) {
            _formState.value = _formState.value.copy(categoryError = "Pilih kategori")
            hasError = true
        }
        if (form.selectedWallet == null) {
            _formState.value = _formState.value.copy(walletError = "Pilih dompet")
            hasError = true
        }
        if (hasError) return

        _formState.value = _formState.value.copy(isLoading = true)
        viewModelScope.launch {
            try {
                if (form.editingId != null) {
                    val old = transactionRepo.getTransactionById(form.editingId)
                    if (old != null) transactionRepo.deleteTransaction(old)
                }
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
                _listState.value = _listState.value.copy(
                    successMessage = if (form.editingId != null) "Transaksi diperbarui" else "Transaksi disimpan"
                )
            } catch (e: Exception) {
                _formState.value = _formState.value.copy(isLoading = false)
                _listState.value = _listState.value.copy(errorMessage = "Gagal menyimpan: ${e.message}")
            }
        }
    }

    fun deleteTransaction(transaction: TransactionEntity) {
        viewModelScope.launch {
            try {
                transactionRepo.deleteTransaction(transaction)
                _listState.value = _listState.value.copy(successMessage = "Transaksi dihapus")
            } catch (e: Exception) {
                _listState.value = _listState.value.copy(errorMessage = "Gagal menghapus: ${e.message}")
            }
        }
    }

    fun deleteWallet(wallet: WalletEntity) {
        viewModelScope.launch {
            try {
                walletRepo.deleteWallet(wallet)
                _listState.value = _listState.value.copy(successMessage = "Dompet dihapus")
            } catch (e: Exception) {
                _listState.value = _listState.value.copy(errorMessage = "Gagal menghapus dompet: ${e.message}")
            }
        }
    }

    fun clearMessages() {
        _listState.value = _listState.value.copy(errorMessage = null, successMessage = null)
    }
}