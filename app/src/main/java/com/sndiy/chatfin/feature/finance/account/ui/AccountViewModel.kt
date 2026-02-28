// app/src/main/java/com/sndiy/chatfin/feature/finance/account/ui/AccountViewModel.kt
// ⚠️ Ini file BARU — taruh di folder feature/finance/account/ui/

package com.sndiy.chatfin.feature.finance.account.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sndiy.chatfin.core.data.local.entity.FinanceAccountEntity
import com.sndiy.chatfin.feature.finance.account.data.repository.AccountRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AccountUiState(
    val accounts: List<FinanceAccountEntity> = emptyList(),
    val activeAccount: FinanceAccountEntity? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val successMessage: String? = null
)

data class AccountFormState(
    val id: String? = null,
    val name: String = "",
    val iconName: String = "account_balance_wallet",
    val colorHex: String = "#0061A4",
    val currency: String = "IDR",
    val description: String = "",
    val nameError: String? = null,
    val isLoading: Boolean = false,
    val isSaved: Boolean = false
)

@HiltViewModel
class AccountViewModel @Inject constructor(
    private val repository: AccountRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AccountUiState())
    val uiState: StateFlow<AccountUiState> = _uiState.asStateFlow()

    private val _formState = MutableStateFlow(AccountFormState())
    val formState: StateFlow<AccountFormState> = _formState.asStateFlow()

    init {
        loadAccounts()
    }

    private fun loadAccounts() {
        viewModelScope.launch {
            combine(
                repository.getAllAccounts(),
                repository.getActiveAccount()
            ) { accounts, activeAccount ->
                AccountUiState(accounts = accounts, activeAccount = activeAccount)
            }.collect { _uiState.value = it }
        }
    }

    fun onNameChange(value: String) {
        _formState.value = _formState.value.copy(name = value, nameError = null)
    }

    fun onIconChange(iconName: String) {
        _formState.value = _formState.value.copy(iconName = iconName)
    }

    fun onColorChange(colorHex: String) {
        _formState.value = _formState.value.copy(colorHex = colorHex)
    }

    fun onCurrencyChange(currency: String) {
        _formState.value = _formState.value.copy(currency = currency)
    }

    fun onDescriptionChange(value: String) {
        _formState.value = _formState.value.copy(description = value)
    }

    fun loadAccountForEdit(accountId: String) {
        viewModelScope.launch {
            val account = repository.getAccountById(accountId) ?: return@launch
            _formState.value = AccountFormState(
                id          = account.id,
                name        = account.name,
                iconName    = account.iconName,
                colorHex    = account.colorHex,
                currency    = account.currency,
                description = account.description ?: ""
            )
        }
    }

    fun resetForm() {
        _formState.value = AccountFormState()
    }

    fun saveAccount() {
        val form = _formState.value
        if (form.name.isBlank()) {
            _formState.value = form.copy(nameError = "Nama akun tidak boleh kosong")
            return
        }
        _formState.value = form.copy(isLoading = true)
        viewModelScope.launch {
            try {
                if (form.id == null) {
                    val newId = repository.createAccount(
                        name        = form.name.trim(),
                        iconName    = form.iconName,
                        colorHex    = form.colorHex,
                        currency    = form.currency,
                        description = form.description.trim().ifBlank { null }
                    )
                    if (_uiState.value.accounts.isEmpty()) {
                        repository.switchActiveAccount(newId)
                    }
                } else {
                    val existing = repository.getAccountById(form.id) ?: return@launch
                    repository.updateAccount(
                        existing.copy(
                            name        = form.name.trim(),
                            iconName    = form.iconName,
                            colorHex    = form.colorHex,
                            currency    = form.currency,
                            description = form.description.trim().ifBlank { null }
                        )
                    )
                }
                _formState.value = _formState.value.copy(isLoading = false, isSaved = true)
            } catch (e: Exception) {
                _formState.value = _formState.value.copy(isLoading = false)
                _uiState.value   = _uiState.value.copy(errorMessage = "Gagal menyimpan: ${e.message}")
            }
        }
    }

    fun deleteAccount(account: FinanceAccountEntity) {
        viewModelScope.launch {
            try {
                repository.deleteAccount(account)
                if (account.isActive) {
                    val remaining = _uiState.value.accounts.filter { it.id != account.id }
                    if (remaining.isNotEmpty()) repository.switchActiveAccount(remaining.first().id)
                }
                _uiState.value = _uiState.value.copy(successMessage = "'${account.name}' berhasil dihapus")
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(errorMessage = "Gagal menghapus: ${e.message}")
            }
        }
    }

    fun switchAccount(accountId: String) {
        viewModelScope.launch { repository.switchActiveAccount(accountId) }
    }

    fun clearMessages() {
        _uiState.value = _uiState.value.copy(errorMessage = null, successMessage = null)
    }
}