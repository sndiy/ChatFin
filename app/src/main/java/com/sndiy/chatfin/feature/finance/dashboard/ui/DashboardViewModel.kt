package com.sndiy.chatfin.feature.finance.dashboard.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sndiy.chatfin.core.data.local.entity.WalletEntity
import com.sndiy.chatfin.feature.finance.account.data.repository.AccountRepository
import com.sndiy.chatfin.feature.finance.analytics.ui.AnalyticsPeriod
import com.sndiy.chatfin.feature.finance.analytics.ui.CategorySlice
import com.sndiy.chatfin.feature.finance.analytics.ui.DailyExpensePoint
import com.sndiy.chatfin.feature.finance.analytics.ui.MonthlyBarEntry
import com.sndiy.chatfin.feature.finance.transaction.data.repository.CategoryRepository
import com.sndiy.chatfin.feature.finance.transaction.data.repository.TransactionRepository
import com.sndiy.chatfin.feature.finance.transaction.data.repository.WalletRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
    val recentTransactions: List<TransactionDisplay> = emptyList(),
    // Analytics
    val selectedPeriod: AnalyticsPeriod              = AnalyticsPeriod.THIS_MONTH,
    val analyticsLoading: Boolean                    = true,
    val analyticsIncome: Long                        = 0L,
    val analyticsExpense: Long                       = 0L,
    val analyticsNet: Long                           = 0L,
    val dailyExpensePoints: List<DailyExpensePoint>  = emptyList(),
    val categorySlices: List<CategorySlice>          = emptyList(),
    val monthlyBarEntries: List<MonthlyBarEntry>     = emptyList()
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val accountRepo: AccountRepository,
    private val walletRepo: WalletRepository,
    private val transactionRepo: TransactionRepository,
    private val categoryRepo: CategoryRepository
) : ViewModel() {

    private val _uiState       = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    private val _selectedPeriod = MutableStateFlow(AnalyticsPeriod.THIS_MONTH)
    private val monthShort      = DateTimeFormatter.ofPattern("MMM")

    init {
        observeDashboard()
        observeAnalytics()
    }

    fun selectPeriod(period: AnalyticsPeriod) {
        _selectedPeriod.value = period
        _uiState.update { it.copy(selectedPeriod = period, analyticsLoading = true) }
    }

    // ── Dashboard ─────────────────────────────────────────────────────────────
    private fun observeDashboard() {
        viewModelScope.launch {
            accountRepo.getActiveAccount().collect { account ->
                if (account == null) {
                    _uiState.update { it.copy(isLoading = false, isOnboarded = false) }
                    return@collect
                }
                _uiState.update { it.copy(isOnboarded = true, accountName = account.name) }

                val now = LocalDate.now()
                combine(
                    walletRepo.getWalletsByAccount(account.id),
                    transactionRepo.getTotalIncome(account.id, now.withDayOfMonth(1), now),
                    transactionRepo.getTotalExpense(account.id, now.withDayOfMonth(1), now),
                    transactionRepo.getTransactionsByAccount(account.id)
                ) { wallets, income, expense, transactions ->
                    DashboardRawData(wallets, income ?: 0L, expense ?: 0L, transactions)
                }.collect { raw ->
                    val expCats   = categoryRepo.getCategoriesByAccountAndType(account.id, "EXPENSE").first()
                    val incCats   = categoryRepo.getCategoriesByAccountAndType(account.id, "INCOME").first()
                    val catMap    = (expCats + incCats).associate { it.id to it.name }
                    val walletMap = raw.wallets.associate { it.id to it.name }

                    val recent = raw.transactions.take(3).map { tx ->
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

                    _uiState.update {
                        it.copy(
                            isLoading          = false,
                            totalBalance       = raw.wallets.sumOf { w -> w.balance },
                            monthlyIncome      = raw.income,
                            monthlyExpense     = raw.expense,
                            wallets            = raw.wallets,
                            recentTransactions = recent
                        )
                    }
                }
            }
        }
    }

    // ── Analytics ─────────────────────────────────────────────────────────────
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observeAnalytics() {
        viewModelScope.launch {
            accountRepo.getActiveAccount()
                .filterNotNull()
                .combine(_selectedPeriod) { account, period -> Pair(account, period) }
                .flatMapLatest { pair ->
                    val account      = pair.first
                    val period       = pair.second
                    val (start, end) = periodRange(period)
                    combine(
                        transactionRepo.getTotalIncome(account.id, start, end),
                        transactionRepo.getTotalExpense(account.id, start, end),
                        transactionRepo.getDailyExpense(account.id, start, end),
                        transactionRepo.getExpenseSumByCategory(account.id, start, end)
                    ) { income, expense, daily, catSums ->
                        AnalyticsRawData(
                            accountId = account.id,
                            income    = income ?: 0L,
                            expense   = expense ?: 0L,
                            daily     = daily,
                            catSums   = catSums
                        )
                    }
                }
                .collect { raw ->
                    val expCats = categoryRepo
                        .getCategoriesByAccountAndType(raw.accountId, "EXPENSE").first()
                    val catMap = expCats.associate { it.id to it.name }

                    val dailyPoints = raw.daily.map { d ->
                        val dayNum = d.date.substring(8, 10).trimStart('0').ifEmpty { "0" }
                        DailyExpensePoint(date = d.date, dayLabel = dayNum, amount = d.total)
                    }

                    val totalExp = raw.expense.takeIf { it > 0 } ?: 1L
                    val slices   = buildList {
                        raw.catSums.take(5).forEach { cs ->
                            add(CategorySlice(
                                categoryId   = cs.categoryId,
                                categoryName = catMap[cs.categoryId] ?: cs.categoryId,
                                amount       = cs.total,
                                percentage   = cs.total.toFloat() / totalExp * 100f
                            ))
                        }
                        val others = raw.catSums.drop(5).sumOf { it.total }
                        if (others > 0) add(
                            CategorySlice("others", "Lainnya", others, others.toFloat() / totalExp * 100f)
                        )
                    }

                    val monthlyEntries = buildMonthlyEntries(raw.accountId)

                    _uiState.update {
                        it.copy(
                            analyticsLoading   = false,
                            analyticsIncome    = raw.income,
                            analyticsExpense   = raw.expense,
                            analyticsNet       = raw.income - raw.expense,
                            dailyExpensePoints = dailyPoints,
                            categorySlices     = slices,
                            monthlyBarEntries  = monthlyEntries
                        )
                    }
                }
        }
    }

    private suspend fun buildMonthlyEntries(accountId: String): List<MonthlyBarEntry> {
        val today = LocalDate.now()
        return (5 downTo 0).map { i ->
            val monthStart = today.minusMonths(i.toLong()).withDayOfMonth(1)
            val monthEnd   = monthStart.plusMonths(1).minusDays(1)
            MonthlyBarEntry(
                monthLabel = monthStart.format(monthShort),
                income     = transactionRepo.getTotalIncome(accountId, monthStart, monthEnd).first() ?: 0L,
                expense    = transactionRepo.getTotalExpense(accountId, monthStart, monthEnd).first() ?: 0L
            )
        }
    }

    private fun periodRange(period: AnalyticsPeriod): Pair<LocalDate, LocalDate> {
        val today = LocalDate.now()
        return when (period) {
            AnalyticsPeriod.THIS_MONTH    -> today.withDayOfMonth(1) to today
            AnalyticsPeriod.LAST_MONTH    -> {
                val start = today.minusMonths(1).withDayOfMonth(1)
                start to start.plusMonths(1).minusDays(1)
            }
            AnalyticsPeriod.LAST_3_MONTHS -> today.minusMonths(3).withDayOfMonth(1) to today
            AnalyticsPeriod.LAST_6_MONTHS -> today.minusMonths(6).withDayOfMonth(1) to today
        }
    }

    fun setupInitialData(accountName: String) {
        viewModelScope.launch {
            val id = accountRepo.createAccount(name = accountName)
            accountRepo.switchActiveAccount(id)
        }
    }
}

// ── Helper data classes ───────────────────────────────────────────────────────
data class DashboardRawData(
    val wallets: List<com.sndiy.chatfin.core.data.local.entity.WalletEntity>,
    val income: Long,
    val expense: Long,
    val transactions: List<com.sndiy.chatfin.core.data.local.entity.TransactionEntity>
)

private data class AnalyticsRawData(
    val accountId: String,
    val income: Long,
    val expense: Long,
    val daily: List<com.sndiy.chatfin.core.data.local.dao.DailyTotal>,
    val catSums: List<com.sndiy.chatfin.core.data.local.dao.CategorySum>
)

private fun <A, B, C, D> combine(
    flow1: Flow<A>, flow2: Flow<B>, flow3: Flow<C>, flow4: Flow<D>
): Flow<DashboardRawData> = throw NotImplementedError("use the typed version")

fun <A, B, C, D> combineFour(
    flow1: Flow<A>, flow2: Flow<B>, flow3: Flow<C>, flow4: Flow<D>,
    transform: suspend (A, B, C, D) -> DashboardRawData
): Flow<DashboardRawData> = combine(flow1, flow2, flow3) { a, b, c ->
    Triple(a, b, c)
}.combine(flow4) { (a, b, c), d ->
    @Suppress("UNCHECKED_CAST")
    transform(a as A, b as B, c as C, d as D)
}