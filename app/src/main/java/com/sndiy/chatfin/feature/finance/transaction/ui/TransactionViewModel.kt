// app/src/main/java/com/sndiy/chatfin/feature/finance/transaction/ui/TransactionViewModel.kt

package com.sndiy.chatfin.feature.finance.transaction.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sndiy.chatfin.core.data.local.entity.CategoryEntity
import com.sndiy.chatfin.core.data.local.entity.TransactionEntity
import com.sndiy.chatfin.core.data.local.entity.TransactionItemEntity
import com.sndiy.chatfin.core.data.local.entity.WalletEntity
import com.sndiy.chatfin.feature.finance.account.data.repository.AccountRepository
import com.sndiy.chatfin.feature.finance.transaction.data.repository.CategoryRepository
import com.sndiy.chatfin.feature.finance.transaction.data.repository.TransactionRepository
import com.sndiy.chatfin.feature.finance.transaction.data.repository.WalletRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject

enum class TransactionType(val label: String) {
    EXPENSE("Pengeluaran"), INCOME("Pemasukan"), TRANSFER("Transfer")
}

data class TransactionFormState(
    val editingId: String?                     = null,
    val type: TransactionType                  = TransactionType.EXPENSE,
    val amount: String                         = "",
    val selectedCategory: CategoryEntity?      = null,
    val selectedWallet: WalletEntity?          = null,
    val selectedToWallet: WalletEntity?        = null,
    val note: String                           = "",
    val date: LocalDate                        = LocalDate.now(),
    val time: LocalTime                        = LocalTime.now(),
    val items: List<TransactionItemEntity>     = emptyList(),
    val amountError: String?                   = null,
    val categoryError: String?                 = null,
    val walletError: String?                   = null,
    val isLoading: Boolean                     = false,
    val isSaved: Boolean                       = false
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
    val isLoading: Boolean                      = true,
    val errorMessage: String?                   = null,
    val successMessage: String?                 = null
)

@OptIn(ExperimentalCoroutinesApi::class)
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

    init {
        viewModelScope.launch {
            accountRepo.getActiveAccount()
                .filterNotNull()
                .flatMapLatest { account ->
                    activeAccountId = account.id
                    _listState.update { it.copy(isLoading = true) }
                    combine(
                        transactionRepo.getTransactionsByAccount(account.id),
                        walletRepo.getWalletsByAccount(account.id),
                        categoryRepo.getCategoriesByAccountAndType(account.id, "EXPENSE"),
                        categoryRepo.getCategoriesByAccountAndType(account.id, "INCOME")
                    ) { transactions, wallets, expCats, incCats ->
                        transactions to Triple(wallets, expCats, incCats)
                    }
                }
                .collect { (transactions, rest) ->
                    val (wallets, expCats, incCats) = rest
                    _listState.update {
                        it.copy(
                            transactions      = transactions,
                            wallets           = wallets,
                            expenseCategories = expCats,
                            incomeCategories  = incCats,
                            isLoading         = false
                        )
                    }
                }
        }
    }

    fun setDateFilter(startDate: LocalDate?, endDate: LocalDate?) {
        _listState.update { it.copy(dateFilter = DateFilter(startDate = startDate, endDate = endDate)) }
    }

    fun clearDateFilter() {
        _listState.update { it.copy(dateFilter = DateFilter()) }
    }

    fun loadForEdit(transaction: TransactionEntity) {
        val state    = _listState.value
        val allCats  = state.expenseCategories + state.incomeCategories
        val category = allCats.find { it.id == transaction.categoryId }
        val wallet   = state.wallets.find { it.id == transaction.walletId }
        val toWallet = transaction.toWalletId?.let { tid -> state.wallets.find { it.id == tid } }
        val type     = when (transaction.type) {
            "INCOME"   -> TransactionType.INCOME
            "TRANSFER" -> TransactionType.TRANSFER
            else       -> TransactionType.EXPENSE
        }
        val safeDate = runCatching { LocalDate.parse(transaction.date) }.getOrDefault(LocalDate.now())
        val safeTime = runCatching { LocalTime.parse(transaction.time) }.getOrDefault(LocalTime.now())

        viewModelScope.launch {
            val txWithItems = withContext(Dispatchers.IO) {
                transactionRepo.getTransactionWithItemsById(transaction.id)
            }
            val fetchedItems = txWithItems?.items ?: emptyList()

            _formState.update {
                TransactionFormState(
                    editingId        = transaction.id,
                    type             = type,
                    amount           = transaction.amount.toString(),
                    selectedCategory = category,
                    selectedWallet   = wallet,
                    selectedToWallet = toWallet,
                    note             = transaction.note ?: "",
                    date             = safeDate,
                    time             = safeTime,
                    items            = fetchedItems
                )
            }
        }
    }

    fun onTypeChange(type: TransactionType) {
        _formState.update {
            it.copy(type = type, selectedCategory = null, categoryError = null)
        }
    }

    fun onAmountChange(value: String) {
        _formState.update {
            it.copy(amount = value.filter { c -> c.isDigit() }, amountError = null)
        }
    }

    fun onCategorySelect(category: CategoryEntity) {
        _formState.update { it.copy(selectedCategory = category, categoryError = null) }
    }

    fun onWalletSelect(wallet: WalletEntity) {
        _formState.update { it.copy(selectedWallet = wallet, walletError = null) }
    }

    fun onToWalletSelect(wallet: WalletEntity) {
        _formState.update { it.copy(selectedToWallet = wallet) }
    }

    fun onNoteChange(value: String) {
        _formState.update { it.copy(note = value) }
    }

    fun onDateChange(date: LocalDate) {
        _formState.update { it.copy(date = date) }
    }

    fun onTimeChange(time: LocalTime) {
        _formState.update { it.copy(time = time) }
    }

    fun onItemsChange(items: List<TransactionItemEntity>) {
        _formState.update {
            it.copy(
                items = items,
                amount = if (items.isNotEmpty()) items.sumOf { item -> item.price }.toString() else it.amount
            )
        }
    }

    fun resetForm() {
        _formState.update { TransactionFormState() }
    }

    fun saveTransaction() {
        val form      = _formState.value
        val accountId = activeAccountId ?: return
        var hasError  = false

        val calculatedAmount = if (form.items.isNotEmpty()) form.items.sumOf { it.price } else form.amount.toLongOrNull() ?: 0L

        if (calculatedAmount <= 0) {
            _formState.update { it.copy(amountError = "Nominal tidak valid") }
            hasError = true
        }
        if (form.type != TransactionType.TRANSFER && form.selectedCategory == null) {
            _formState.update { it.copy(categoryError = "Pilih kategori") }
            hasError = true
        }
        if (form.selectedWallet == null) {
            _formState.update { it.copy(walletError = "Pilih dompet") }
            hasError = true
        }
        if (hasError) return

        _formState.update { it.copy(isLoading = true) }
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    if (form.editingId != null) {
                        val old = transactionRepo.getTransactionById(form.editingId)
                        if (old != null) {
                            transactionRepo.updateTransactionWithItems(
                                oldTransaction = old,
                                newType        = form.type.name,
                                newAmount      = calculatedAmount,
                                newCategoryId  = form.selectedCategory?.id ?: "transfer",
                                newWalletId    = form.selectedWallet!!.id,
                                newToWalletId  = form.selectedToWallet?.id,
                                newNote        = form.note.trim().ifBlank { null },
                                newDate        = form.date,
                                newTime        = form.time,
                                newItems       = form.items
                            )
                        }
                    } else {
                        transactionRepo.addTransaction(
                            accountId         = accountId,
                            type              = form.type.name,
                            amount            = calculatedAmount,
                            categoryId        = form.selectedCategory?.id ?: "transfer",
                            walletId          = form.selectedWallet!!.id,
                            toWalletId        = form.selectedToWallet?.id,
                            note              = form.note.trim().ifBlank { null },
                            date              = form.date,
                            time              = form.time
                        )
                    }
                }
                _formState.update { it.copy(isLoading = false, isSaved = true) }
                _listState.update {
                    it.copy(successMessage = if (form.editingId != null) "Transaksi diperbarui" else "Transaksi disimpan")
                }
            } catch (e: Exception) {
                _formState.update { it.copy(isLoading = false) }
                _listState.update { it.copy(errorMessage = "Gagal menyimpan: ${e.message}") }
            }
        }
    }

    fun deleteTransaction(transaction: TransactionEntity) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    transactionRepo.deleteTransaction(transaction)
                }
                _listState.update { it.copy(successMessage = "Transaksi dihapus") }
            } catch (e: Exception) {
                _listState.update { it.copy(errorMessage = "Gagal menghapus: ${e.message}") }
            }
        }
    }

    fun deleteWallet(wallet: WalletEntity) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    walletRepo.deleteWallet(wallet)
                }
                _listState.update { it.copy(successMessage = "Dompet dihapus") }
            } catch (e: Exception) {
                _listState.update { it.copy(errorMessage = "Gagal menghapus dompet: ${e.message}") }
            }
        }
    }

    fun quickAdd(type: String, amount: Long, categoryId: String, walletId: String, note: String) {
        val accountId = activeAccountId ?: return
        // Jaring pengaman terakhir sebelum menulis ke DB. Jalur quick add
        // sebelumnya menerima apa pun yang dikirim sheet tanpa satu pun validasi,
        // padahal jalur form manual menolak kategori kosong (lihat saveTransaction).
        if (amount <= 0L || categoryId.isBlank() || walletId.isBlank()) {
            _listState.update { it.copy(errorMessage = "Transaksi belum lengkap — cek nominal, kategori, dan dompet") }
            return
        }
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    transactionRepo.addTransaction(
                        accountId = accountId,
                        type = type,
                        amount = amount,
                        categoryId = categoryId,
                        walletId = walletId,
                        note = note.ifBlank { null }
                    )
                }
                _listState.update { it.copy(successMessage = "Transaksi disimpan") }
            } catch (e: Exception) {
                _listState.update { it.copy(errorMessage = "Gagal menyimpan: ${e.message}") }
            }
        }
    }

    fun clearMessages() {
        _listState.update { it.copy(errorMessage = null, successMessage = null) }
    }
}
