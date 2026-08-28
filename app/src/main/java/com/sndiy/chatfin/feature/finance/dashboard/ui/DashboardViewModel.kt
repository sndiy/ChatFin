// app/src/main/java/com/sndiy/chatfin/feature/finance/dashboard/ui/DashboardViewModel.kt
//
// PERUBAHAN:
// 1. Inject BudgetRepository
// 2. Tambah budgetOverview (List<BudgetWithSpent>) ke DashboardUiState
// 3. Observe budgets di observeDashboard()

package com.sndiy.chatfin.feature.finance.dashboard.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sndiy.chatfin.core.data.local.entity.WalletEntity
import com.sndiy.chatfin.core.data.sync.SyncEventBus
import com.sndiy.chatfin.core.data.sync.SyncStatus
import com.sndiy.chatfin.core.data.sync.SyncStatusRepository
import com.sndiy.chatfin.core.domain.LocalInsightEngine
import com.sndiy.chatfin.core.domain.PeriodRange
import com.sndiy.chatfin.feature.finance.account.data.repository.AccountRepository
import com.sndiy.chatfin.feature.finance.analytics.ui.AnalyticsPeriod
import com.sndiy.chatfin.feature.finance.analytics.ui.CategorySlice
import com.sndiy.chatfin.feature.finance.analytics.ui.DailyExpensePoint
import com.sndiy.chatfin.feature.finance.analytics.ui.MonthlyBarEntry
import com.sndiy.chatfin.feature.finance.budget.data.repository.BudgetRepository
import com.sndiy.chatfin.feature.finance.budget.data.repository.BudgetWithSpent
import com.sndiy.chatfin.feature.finance.transaction.data.repository.CategoryRepository
import com.sndiy.chatfin.feature.finance.transaction.data.repository.TransactionRepository
import com.sndiy.chatfin.feature.finance.transaction.data.repository.WalletRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.random.Random
import javax.inject.Inject

data class TransactionDisplay(
    val id: String,
    val type: String,
    val amount: Long,
    val categoryName: String,
    val walletName: String,
    val date: String,
    val time: String,
    val note: String?,
    val receiptImageUri: String? = null
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
    val selectedPeriod: AnalyticsPeriod              = AnalyticsPeriod.THIS_MONTH,
    val analyticsLoading: Boolean                    = true,
    val analyticsIncome: Long                        = 0L,
    val analyticsExpense: Long                       = 0L,
    val analyticsNet: Long                           = 0L,
    val dailyExpensePoints: List<DailyExpensePoint>  = emptyList(),
    val categorySlices: List<CategorySlice>          = emptyList(),
    val monthlyBarEntries: List<MonthlyBarEntry>     = emptyList(),
    // Budget overview
    val budgetOverview: List<BudgetWithSpent>        = emptyList(),
    val hasBudgets: Boolean                          = false,
    // Mai's daily insight
    val maiInsight: String                           = "",
    // Status sinkronisasi Firestore (ditampilkan di TopAppBar)
    val syncStatus: SyncStatus                       = SyncStatus.IDLE
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val accountRepo: AccountRepository,
    private val walletRepo: WalletRepository,
    private val transactionRepo: TransactionRepository,
    private val categoryRepo: CategoryRepository,
    private val budgetRepo: BudgetRepository,
    private val syncEventBus: SyncEventBus,
    private val syncStatusRepo: SyncStatusRepository
) : ViewModel() {

    private val _uiState        = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    private val _selectedPeriod = MutableStateFlow(AnalyticsPeriod.THIS_MONTH)
    private val monthShort      = DateTimeFormatter.ofPattern("MMM")

    private var dashboardJob: Job? = null
    private var analyticsJob: Job? = null

    init {
        startObservers()
        observeSyncEvent()
    }

    private fun startObservers() {
        dashboardJob?.cancel()
        analyticsJob?.cancel()
        dashboardJob = observeDashboard()
        analyticsJob = observeAnalytics()
        observeSyncStatus()
    }

    private fun observeSyncStatus() {
        viewModelScope.launch {
            syncStatusRepo.status.collect { status ->
                _uiState.update { it.copy(syncStatus = status) }
            }
        }
    }

    private fun observeSyncEvent() {
        viewModelScope.launch {
            syncEventBus.syncCompleted.collect {
                // Flow Room otomatis memancarkan data baru; cukup pastikan ada akun aktif
                val active = accountRepo.getActiveAccount().first()
                if (active == null) {
                    val all = accountRepo.getAllAccounts().first()
                    if (all.isNotEmpty()) {
                        val withData = all.find { acc ->
                            transactionRepo.getTransactionsByAccount(acc.id).first().isNotEmpty()
                        } ?: all.first()
                        accountRepo.switchActiveAccount(withData.id)
                    }
                }
            }
        }
    }

    fun selectPeriod(period: AnalyticsPeriod) {
        _selectedPeriod.value = period
        _uiState.update { it.copy(selectedPeriod = period, analyticsLoading = true) }
    }

    // ── Dashboard ─────────────────────────────────────────────────────────────
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observeDashboard(): Job = viewModelScope.launch {
        accountRepo.getActiveAccount()
            .flatMapLatest { account ->
                if (account == null) {
                    val all = accountRepo.getAllAccounts().first()
                    if (all.isNotEmpty()) {
                        val withData = all.find { acc ->
                            transactionRepo.getTransactionsByAccount(acc.id).first().isNotEmpty()
                        } ?: all.first()
                        accountRepo.switchActiveAccount(withData.id)
                    } else {
                        // Tidak ada akun sama sekali -> matikan loading agar skeleton tidak macet
                        _uiState.update { it.copy(isLoading = false, isOnboarded = false) }
                    }
                    return@flatMapLatest emptyFlow()
                }
                _uiState.update { it.copy(isOnboarded = true, accountName = account.name) }

                val now = LocalDate.now()
                combine(
                    walletRepo.getWalletsByAccount(account.id),
                    transactionRepo.getTotalIncome(account.id, now.withDayOfMonth(1), now),
                    transactionRepo.getTotalExpense(account.id, now.withDayOfMonth(1), now),
                    transactionRepo.getTransactionsByAccount(account.id),
                    budgetRepo.getBudgetsByAccountAndPeriod(account.id, now.year, now.monthValue)
                ) { wallets, income, expense, transactions, budgets ->
                    DashboardRawData(
                        accountId    = account.id,
                        wallets      = wallets,
                        income       = income ?: 0L,
                        expense      = expense ?: 0L,
                        transactions = transactions,
                        budgets      = budgets
                    )
                }
            }
            .collect { raw ->
                val expCats   = categoryRepo.getCategoriesByAccountAndType(raw.accountId, "EXPENSE").first()
                val incCats   = categoryRepo.getCategoriesByAccountAndType(raw.accountId, "INCOME").first()
                val catMap    = (expCats + incCats).associate { it.id to it.name }
                val walletMap = raw.wallets.associate { it.id to it.name }
                val catColorMap = expCats.associate { it.id to it.colorHex }

                val recent = raw.transactions.take(3).map { tx ->
                    TransactionDisplay(
                        id           = tx.id,
                        type         = tx.type,
                        amount       = tx.amount,
                        categoryName = if (tx.type == "TRANSFER") "Transfer" else catMap[tx.categoryId] ?: tx.categoryId,
                        walletName   = walletMap[tx.walletId] ?: tx.walletId,
                        date         = tx.date,
                        time         = tx.time,
                        note         = tx.note,
                        receiptImageUri = tx.receiptImageUri
                    )
                }

                // Build budget overview (top 3 yang paling mendekati/melebihi limit)
                val now = LocalDate.now()
                val startDate = now.withDayOfMonth(1)
                val endDate = now
                val budgetOverview = raw.budgets.map { budget ->
                    val spent = budgetRepo.getSpentForCategory(raw.accountId, budget.categoryId, startDate, endDate)
                    BudgetWithSpent(
                        budget        = budget,
                        spent         = spent,
                        categoryName  = catMap[budget.categoryId] ?: budget.categoryId,
                        categoryColor = catColorMap[budget.categoryId] ?: "#888888"
                    )
                }.sortedByDescending { it.percentage }.take(3)

                val totalBal = raw.wallets.sumOf { w -> w.balance }
                val insight  = LocalInsightEngine.spendingInsight(totalBal, raw.income, raw.expense)

                _uiState.update {
                    it.copy(
                        isLoading          = false,
                        totalBalance       = totalBal,
                        monthlyIncome      = raw.income,
                        monthlyExpense     = raw.expense,
                        maiInsight         = insight,
                        wallets            = raw.wallets,
                        recentTransactions = recent,
                        budgetOverview     = budgetOverview,
                        hasBudgets         = raw.budgets.isNotEmpty()
                    )
                }
            }
    }

    // ── Analytics ─────────────────────────────────────────────────────────────
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observeAnalytics(): Job = viewModelScope.launch {
        accountRepo.getActiveAccount()
            .filterNotNull()
            .combine(_selectedPeriod) { account, period -> account to period }
            .flatMapLatest { (account, period) ->
                val (start, end) = periodRange(period)
                combine(
                    transactionRepo.getTotalIncome(account.id, start, end),
                    transactionRepo.getTotalExpense(account.id, start, end),
                    transactionRepo.getDailyExpense(account.id, start, end),
                    transactionRepo.getExpenseSumByCategory(account.id, start, end)
                ) { income, expense, daily, catSums ->
                    AnalyticsRawData(
                        accountId = account.id,
                        period    = period,
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
                val catMap  = expCats.associate { it.id to it.name }

                val dailyPoints = raw.daily.map { d ->
                    // d.date bisa berasal dari sync Firestore — jangan andalkan
                    // posisi karakter mentah (substring) untuk data yang formatnya
                    // bisa saja rusak. Parse via LocalDate, fallback ke "?" kalau gagal.
                    val dayNum = runCatching { LocalDate.parse(d.date).dayOfMonth.toString() }
                        .getOrDefault("?")
                    DailyExpensePoint(date = d.date, dayLabel = dayNum, amount = d.total)
                }

                val totalExp = raw.expense.takeIf { it > 0 } ?: 1L
                val slices = buildList {
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

                val monthlyEntries = buildMonthlyEntries(raw.accountId, raw.period)

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

    private suspend fun buildMonthlyEntries(
        accountId: String,
        period: AnalyticsPeriod
    ): List<MonthlyBarEntry> {
        val anchor = when (period) {
            AnalyticsPeriod.THIS_MONTH    -> LocalDate.now()
            AnalyticsPeriod.LAST_MONTH    -> LocalDate.now().minusMonths(1)
            AnalyticsPeriod.LAST_3_MONTHS -> LocalDate.now()
            AnalyticsPeriod.LAST_6_MONTHS -> LocalDate.now()
        }
        return (5 downTo 0).map { i ->
            val monthStart = anchor.minusMonths(i.toLong()).withDayOfMonth(1)
            val monthEnd   = monthStart.plusMonths(1).minusDays(1)
            MonthlyBarEntry(
                monthLabel = monthStart.format(monthShort),
                income     = transactionRepo.getTotalIncome(accountId, monthStart, monthEnd).first() ?: 0L,
                expense    = transactionRepo.getTotalExpense(accountId, monthStart, monthEnd).first() ?: 0L
            )
        }
    }

    private fun periodRange(period: AnalyticsPeriod): Pair<LocalDate, LocalDate> = when (period) {
        AnalyticsPeriod.THIS_MONTH    -> PeriodRange.thisMonth()
        AnalyticsPeriod.LAST_MONTH    -> PeriodRange.lastMonth()
        AnalyticsPeriod.LAST_3_MONTHS -> PeriodRange.lastNMonths(3)
        AnalyticsPeriod.LAST_6_MONTHS -> PeriodRange.lastNMonths(6)
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
    val accountId: String,
    val wallets: List<com.sndiy.chatfin.core.data.local.entity.WalletEntity>,
    val income: Long,
    val expense: Long,
    val transactions: List<com.sndiy.chatfin.core.data.local.entity.TransactionEntity>,
    val budgets: List<com.sndiy.chatfin.core.data.local.entity.BudgetEntity> = emptyList()
)

private data class AnalyticsRawData(
    val accountId: String,
    val period: AnalyticsPeriod,
    val income: Long,
    val expense: Long,
    val daily: List<com.sndiy.chatfin.core.data.local.dao.DailyTotal>,
    val catSums: List<com.sndiy.chatfin.core.data.local.dao.CategorySum>
)
