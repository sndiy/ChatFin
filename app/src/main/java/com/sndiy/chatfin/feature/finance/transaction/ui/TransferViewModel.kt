// app/src/main/java/com/sndiy/chatfin/feature/finance/transaction/ui/TransferViewModel.kt

package com.sndiy.chatfin.feature.finance.transaction.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sndiy.chatfin.core.data.local.entity.WalletEntity
import com.sndiy.chatfin.feature.finance.account.data.repository.AccountRepository
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

data class TransferFormState(
    val wallets: List<WalletEntity>    = emptyList(),
    val sourceWallet: WalletEntity?    = null,
    val destWallet: WalletEntity?      = null,
    val amount: String                 = "",
    val note: String                   = "",
    val sourceError: String?           = null,
    val destError: String?             = null,
    val amountError: String?           = null,
    val isLoading: Boolean             = false,
    val isSaved: Boolean               = false,
    val showConfirm: Boolean           = false,
    val errorMessage: String?          = null
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TransferViewModel @Inject constructor(
    private val transactionRepo: TransactionRepository,
    private val walletRepo: WalletRepository,
    private val accountRepo: AccountRepository
) : ViewModel() {

    private val _formState = MutableStateFlow(TransferFormState())
    val formState: StateFlow<TransferFormState> = _formState.asStateFlow()

    private var activeAccountId: String? = null

    init {
        viewModelScope.launch {
            accountRepo.getActiveAccount()
                .filterNotNull()
                .flatMapLatest { account ->
                    activeAccountId = account.id
                    walletRepo.getWalletsByAccount(account.id)
                }
                .collect { wallets ->
                    _formState.update { it.copy(wallets = wallets) }
                }
        }
    }

    fun onSourceSelect(wallet: WalletEntity) {
        _formState.update { it.copy(sourceWallet = wallet, sourceError = null, destError = null) }
    }

    fun onDestSelect(wallet: WalletEntity) {
        _formState.update { it.copy(destWallet = wallet, destError = null) }
    }

    fun onAmountChange(value: String) {
        _formState.update {
            it.copy(amount = value.filter { c -> c.isDigit() }, amountError = null)
        }
    }

    fun onNoteChange(value: String) {
        _formState.update { it.copy(note = value) }
    }

    fun requestConfirm() {
        val form   = _formState.value
        val amount = form.amount.toLongOrNull() ?: 0L
        var hasError = false

        if (amount <= 0) {
            _formState.update { it.copy(amountError = "Nominal tidak valid") }
            hasError = true
        }
        if (form.sourceWallet == null) {
            _formState.update { it.copy(sourceError = "Pilih dompet sumber") }
            hasError = true
        }
        if (form.destWallet == null) {
            _formState.update { it.copy(destError = "Pilih dompet tujuan") }
            hasError = true
        }
        if (form.sourceWallet != null && form.destWallet != null && form.sourceWallet.id == form.destWallet.id) {
            _formState.update { it.copy(destError = "Dompet tujuan harus berbeda dari sumber") }
            hasError = true
        }
        if (!hasError && form.sourceWallet != null && form.sourceWallet.type != "CREDIT_CARD" && amount > form.sourceWallet.balance) {
            _formState.update { it.copy(amountError = "Saldo ${form.sourceWallet.name} tidak cukup") }
            hasError = true
        }
        if (hasError) return

        _formState.update { it.copy(showConfirm = true) }
    }

    fun dismissConfirm() {
        _formState.update { it.copy(showConfirm = false) }
    }

    fun confirmTransfer() {
        val form      = _formState.value
        val accountId = activeAccountId ?: return
        val source    = form.sourceWallet ?: return
        val dest      = form.destWallet ?: return
        val amount    = form.amount.toLongOrNull() ?: return

        _formState.update { it.copy(isLoading = true) }
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    transactionRepo.addTransaction(
                        accountId  = accountId,
                        type       = "TRANSFER",
                        amount     = amount,
                        categoryId = "transfer",
                        walletId   = source.id,
                        toWalletId = dest.id,
                        note       = form.note.trim().ifBlank { null },
                        date       = LocalDate.now(),
                        time       = LocalTime.now()
                    )
                }
                _formState.update { it.copy(isLoading = false, isSaved = true, showConfirm = false) }
            } catch (e: Exception) {
                _formState.update {
                    it.copy(isLoading = false, showConfirm = false, errorMessage = "Gagal transfer: ${e.message}")
                }
            }
        }
    }

    fun clearError() {
        _formState.update { it.copy(errorMessage = null) }
    }
}
