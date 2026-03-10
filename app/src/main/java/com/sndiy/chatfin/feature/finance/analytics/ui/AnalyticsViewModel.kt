package com.sndiy.chatfin.feature.finance.analytics.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sndiy.chatfin.feature.finance.account.data.repository.AccountRepository
import com.sndiy.chatfin.feature.finance.transaction.data.repository.CategoryRepository
import com.sndiy.chatfin.feature.finance.transaction.data.repository.TransactionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject

data class DailyExpensePoint(val date: String, val dayLabel: String, val amount: Long)
data class CategorySlice(val categoryId: String, val categoryName: String, val amount: Long, val percentage: Float)
data class MonthlyBarEntry(val monthLabel: String, val income: Long, val expense: Long)

enum class AnalyticsPeriod(val label: String) {
    THIS_MONTH("Bulan Ini"),
    LAST_MONTH("Bulan Lalu"),
    LAST_3_MONTHS("3 Bulan"),
    LAST_6_MONTHS("6 Bulan")
}

data class AnalyticsUiState(
    val isLoading: Boolean                          = true,
    val selectedPeriod: AnalyticsPeriod             = AnalyticsPeriod.THIS_MONTH,
    val totalIncome: Long                           = 0L,
    val totalExpense: Long                          = 0L,
    val netBalance: Long                            = 0L,
    val dailyExpensePoints: List<DailyExpensePoint> = emptyList(),
    val categorySlices: List<CategorySlice>         = emptyList(),
    val monthlyBarEntries: List<MonthlyBarEntry>    = emptyList(),
    val error: String?                              = null
)

private data class AnalyticsRawData(
    val accountId: String,
    val income: Long,
    val expense: Long,
    val daily: List<com.sndiy.chatfin.core.data.local.dao.DailyTotal>,
    val catSums: List<com.sndiy.chatfin.core.data.local.dao.CategorySum>
)

@HiltViewModel
class AnalyticsViewModel @Inject constructor(
    private val accountRepo: AccountRepository,
    private val transactionRepo: TransactionRepository,
    private val categoryRepo: CategoryRepository
) : ViewModel() {

    private val _selectedPeriod = MutableStateFlow(AnalyticsPeriod.THIS_MONTH)
    private val _uiState        = MutableStateFlow(AnalyticsUiState())
    val uiState: StateFlow<AnalyticsUiState> = _uiState.asStateFlow()

    private val monthShort = DateTimeFormatter.ofPattern("MMM")

    init { observeAnalytics() }

    fun selectPeriod(period: AnalyticsPeriod) {
        _selectedPeriod.value = period
        _uiState.update { it.copy(selectedPeriod = period, isLoading = true) }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observeAnalytics() {
        viewModelScope.launch {
            accountRepo.getActiveAccount()
                .filterNotNull()
                .combine(_selectedPeriod) { account, period -> Pair(account, period) }
                .flatMapLatest { pair ->
                    val account = pair.first
                    val period  = pair.second
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
                    val top5     = raw.catSums.take(5)
                    val others   = raw.catSums.drop(5).sumOf { it.total }
                    val slices   = buildList {
                        top5.forEach { cs ->
                            add(CategorySlice(
                                categoryId   = cs.categoryId,
                                categoryName = catMap[cs.categoryId] ?: cs.categoryId,
                                amount       = cs.total,
                                percentage   = cs.total.toFloat() / totalExp * 100f
                            ))
                        }
                        if (others > 0) add(
                            CategorySlice("others", "Lainnya", others, others.toFloat() / totalExp * 100f)
                        )
                    }

                    val monthlyEntries = buildMonthlyEntries(raw.accountId)

                    _uiState.update {
                        it.copy(
                            isLoading          = false,
                            totalIncome        = raw.income,
                            totalExpense       = raw.expense,
                            netBalance         = raw.income - raw.expense,
                            dailyExpensePoints = dailyPoints,
                            categorySlices     = slices,
                            monthlyBarEntries  = monthlyEntries,
                            error              = null
                        )
                    }
                }
        }

        viewModelScope.launch {
            accountRepo.getActiveAccount()
                .filter { it == null }
                .collect { _uiState.update { it.copy(isLoading = false) } }
        }
    }

    private suspend fun buildMonthlyEntries(accountId: String): List<MonthlyBarEntry> {
        val today = LocalDate.now()
        // Paralel — semua 6 bulan di-query sekaligus
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
}