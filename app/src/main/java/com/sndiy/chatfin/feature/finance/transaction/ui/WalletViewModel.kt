// app/src/main/java/com/sndiy/chatfin/feature/finance/transaction/ui/WalletViewModel.kt

package com.sndiy.chatfin.feature.finance.transaction.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sndiy.chatfin.feature.finance.account.data.repository.AccountRepository
import com.sndiy.chatfin.feature.finance.transaction.data.repository.WalletRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class WalletViewModel @Inject constructor(
    private val walletRepo: WalletRepository,
    private val accountRepo: AccountRepository
) : ViewModel() {

    private val _formState = MutableStateFlow(WalletFormState())
    val formState: StateFlow<WalletFormState> = _formState.asStateFlow()

    private var activeAccountId: String? = null

    init {
        viewModelScope.launch {
            accountRepo.getActiveAccount().collect { account ->
                activeAccountId = account?.id
            }
        }
    }

    fun onNameChange(value: String) {
        _formState.value = _formState.value.copy(name = value, nameError = null)
    }

    fun onBalanceChange(value: String) {
        val filtered = value.filter { it.isDigit() }
        _formState.value = _formState.value.copy(balance = filtered)
    }

    fun onTypeChange(type: String) {
        _formState.value = _formState.value.copy(type = type)
    }

    fun onColorChange(colorHex: String) {
        _formState.value = _formState.value.copy(colorHex = colorHex)
    }

    fun saveWallet() {
        val form      = _formState.value
        val accountId = activeAccountId ?: return

        if (form.name.isBlank()) {
            _formState.value = form.copy(nameError = "Nama dompet tidak boleh kosong")
            return
        }

        _formState.value = form.copy(isLoading = true)

        viewModelScope.launch {
            try {
                walletRepo.createWallet(
                    accountId = accountId,
                    name      = form.name.trim(),
                    type      = form.type,
                    balance   = form.balance.toLongOrNull() ?: 0L,
                    currency  = "IDR",
                    colorHex  = form.colorHex,
                    iconName  = "account_balance_wallet"
                )
                _formState.value = _formState.value.copy(isLoading = false, isSaved = true)
            } catch (e: Exception) {
                _formState.value = _formState.value.copy(isLoading = false)
            }
        }
    }
}