package com.sndiy.chatfin.feature.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sndiy.chatfin.core.data.local.DefaultCategories
import com.sndiy.chatfin.core.data.local.entity.FinanceAccountEntity
import com.sndiy.chatfin.core.data.local.entity.TransactionEntity
import com.sndiy.chatfin.core.data.local.entity.WalletEntity
import com.sndiy.chatfin.core.domain.FinanceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.UUID
import javax.inject.Inject

data class DashboardUiState(
    val account: FinanceAccountEntity? = null,
    val wallets: List<WalletEntity> = emptyList(),
    val totalBalance: Long = 0L,
    val monthlyIncome: Long = 0L,
    val monthlyExpense: Long = 0L,
    val recentTransactions: List<TransactionEntity> = emptyList(),
    val isLoading: Boolean = true,
    val isOnboarded: Boolean = false
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val financeRepo: FinanceRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    init {
        observeData()
    }

    private fun observeData() {
        viewModelScope.launch {
            financeRepo.getActiveAccount().collect { account ->
                _uiState.update { it.copy(account = account, isOnboarded = account != null) }
                if (account != null) {
                    loadAccountData(account.id)
                } else {
                    _uiState.update { it.copy(isLoading = false) }
                }
            }
        }
    }

    private fun loadAccountData(accountId: String) {
        val now = LocalDate.now()
        val start = now.withDayOfMonth(1).format(DateTimeFormatter.ISO_LOCAL_DATE)
        val end = now.format(DateTimeFormatter.ISO_LOCAL_DATE)

        viewModelScope.launch {
            financeRepo.getWalletsByAccount(accountId).collect { wallets ->
                _uiState.update {
                    it.copy(
                        wallets = wallets,
                        totalBalance = wallets.sumOf { w -> w.balance },
                        isLoading = false
                    )
                }
            }
        }
        viewModelScope.launch {
            financeRepo.getTotalByTypeAndPeriod(accountId, "INCOME", start, end).collect { v ->
                _uiState.update { it.copy(monthlyIncome = v ?: 0L) }
            }
        }
        viewModelScope.launch {
            financeRepo.getTotalByTypeAndPeriod(accountId, "EXPENSE", start, end).collect { v ->
                _uiState.update { it.copy(monthlyExpense = v ?: 0L) }
            }
        }
        viewModelScope.launch {
            financeRepo.getTransactionsByPeriod(accountId, start, end).collect { txs ->
                _uiState.update { it.copy(recentTransactions = txs.take(10)) }
            }
        }
    }

    fun setupInitialData(accountName: String) {
        viewModelScope.launch {
            val accountId = UUID.randomUUID().toString()
            val account = FinanceAccountEntity(
                id = accountId,
                name = accountName,
                isActive = true
            )
            financeRepo.insertAccount(account)

            // Seed kategori default
            financeRepo.insertCategories(DefaultCategories.all)

            // Buat dompet default: Kas
            val wallet = WalletEntity(
                id = UUID.randomUUID().toString(),
                accountId = accountId,
                name = "Kas",
                type = "CASH",
                balance = 0L,
                isDefault = true
            )
            financeRepo.insertWallet(wallet)
        }
    }
}
