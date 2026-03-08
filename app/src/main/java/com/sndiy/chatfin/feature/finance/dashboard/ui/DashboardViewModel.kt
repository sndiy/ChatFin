package com.sndiy.chatfin.feature.finance.dashboard.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sndiy.chatfin.core.data.local.entity.WalletEntity
import com.sndiy.chatfin.feature.finance.account.data.repository.AccountRepository
import com.sndiy.chatfin.feature.finance.transaction.data.repository.CategoryRepository
import com.sndiy.chatfin.feature.finance.transaction.data.repository.TransactionRepository
import com.sndiy.chatfin.feature.finance.transaction.data.repository.WalletRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject

data class TransactionDisplay(
    val id: String,
    val type: String,
    val amount: Long,
    val categoryName: String,
    val walletName: String,
    val date: String,
    val time: String,
    val note: String?
)

data class DashboardUiState(
    val isLoading: Boolean                           = true,
    val isOnboarded: Boolean                         = false,
    val accountName: String                          = "",
    val totalBalance: Long                           = 0L,
    val monthlyIncome: Long                          = 0L,
    val monthlyExpense: Long                         = 0L,
    val wallets: List<WalletEntity>                  = emptyList(),
    val recentTransactions: List<TransactionDisplay> = emptyList()
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val accountRepo: AccountRepository,
    private val walletRepo: WalletRepository,
    private val transactionRepo: TransactionRepository,
    private val categoryRepo: CategoryRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    private val dateFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    init { observeDashboard() }

    private fun observeDashboard() {
        viewModelScope.launch {
            accountRepo.getActiveAccount().collect { account ->
                if (account == null) {
                    _uiState.value = DashboardUiState(isLoading = false, isOnboarded = false)
                    return@collect
                }

                _uiState.value = _uiState.value.copy(
                    isOnboarded = true,
                    accountName = account.name
                )

                val now = LocalDate.now()

                combine(
                    walletRepo.getWalletsByAccount(account.id),
                    transactionRepo.getTotalIncome(account.id, now.withDayOfMonth(1), now),
                    transactionRepo.getTotalExpense(account.id, now.withDayOfMonth(1), now),
                    transactionRepo.getTransactionsByAccount(account.id)
                ) { wallets, income, expense, transactions ->
                    Quad(wallets, income ?: 0L, expense ?: 0L, transactions)
                }.collect { (wallets, income, expense, transactions) ->

                    // Kategori: sudah include global (accountId IS NULL) dari query DAO
                    val expCats = categoryRepo.getCategoriesByAccountAndType(account.id, "EXPENSE").first()
                    val incCats = categoryRepo.getCategoriesByAccountAndType(account.id, "INCOME").first()
                    val catMap  = (expCats + incCats).associate { it.id to it.name }
                    val walletMap = wallets.associate { it.id to it.name }

                    val recent = transactions.take(10).map { tx ->
                        TransactionDisplay(
                            id           = tx.id,
                            type         = tx.type,
                            amount       = tx.amount,
                            categoryName = catMap[tx.categoryId] ?: tx.categoryId,
                            walletName   = walletMap[tx.walletId] ?: tx.walletId,
                            date         = tx.date,
                            time         = tx.time,
                            note         = tx.note
                        )
                    }

                    _uiState.value = _uiState.value.copy(
                        isLoading          = false,
                        totalBalance       = wallets.sumOf { it.balance },
                        monthlyIncome      = income,
                        monthlyExpense     = expense,
                        wallets            = wallets,
                        recentTransactions = recent
                    )
                }
            }
        }
    }

    fun setupInitialData(accountName: String) {
        viewModelScope.launch {
            val id = accountRepo.createAccount(name = accountName)
            accountRepo.switchActiveAccount(id)
        }
    }
}

private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

private fun <A, B, C, D> combine(
    flow1: Flow<A>, flow2: Flow<B>, flow3: Flow<C>, flow4: Flow<D>
): Flow<Quad<A, B, C, D>> = combine(flow1, flow2, flow3) { a, b, c ->
    Triple(a, b, c)
}.combine(flow4) { (a, b, c), d -> Quad(a, b, c, d) }