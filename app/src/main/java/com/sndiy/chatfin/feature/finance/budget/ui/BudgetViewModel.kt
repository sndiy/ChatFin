// app/src/main/java/com/sndiy/chatfin/feature/finance/budget/ui/BudgetViewModel.kt

package com.sndiy.chatfin.feature.finance.budget.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sndiy.chatfin.core.data.local.entity.BudgetEntity
import com.sndiy.chatfin.core.data.local.entity.CategoryEntity
import com.sndiy.chatfin.feature.finance.account.data.repository.AccountRepository
import com.sndiy.chatfin.feature.finance.budget.data.repository.BudgetRepository
import com.sndiy.chatfin.feature.finance.budget.data.repository.BudgetWithSpent
import com.sndiy.chatfin.feature.finance.transaction.data.repository.CategoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class BudgetUiState(
    val isLoading: Boolean                             = true,
    val budgets: List<BudgetWithSpent>                 = emptyList(),
    val availableCategories: List<CategoryEntity>      = emptyList(),
    val totalBudget: Long                              = 0L,
    val totalSpent: Long                               = 0L,
    val month: Int                                     = LocalDate.now().monthValue,
    val year: Int                                      = LocalDate.now().year,
    val showAddDialog: Boolean                         = false,
    val editingBudget: BudgetEntity?                   = null,
    val errorMessage: String?                          = null,
    val successMessage: String?                        = null
)

@HiltViewModel
class BudgetViewModel @Inject constructor(
    private val budgetRepo: BudgetRepository,
    private val accountRepo: AccountRepository,
    private val categoryRepo: CategoryRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(BudgetUiState())
    val uiState: StateFlow<BudgetUiState> = _uiState.asStateFlow()

    private var activeAccountId: String? = null

    init { observeData() }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observeData() {
        viewModelScope.launch {
            accountRepo.getActiveAccount()
                .filterNotNull()
                .flatMapLatest { account ->
                    activeAccountId = account.id
                    val state = _uiState.value
                    combine(
                        budgetRepo.getBudgetsByAccountAndPeriod(account.id, state.year, state.month),
                        categoryRepo.getCategoriesByAccountAndType(account.id, "EXPENSE")
                    ) { budgets, categories ->
                        Triple(account.id, budgets, categories)
                    }
                }
                .collect { (accountId, budgets, categories) ->
                    val state = _uiState.value
                    val startDate = LocalDate.of(state.year, state.month, 1)
                    val endDate = startDate.plusMonths(1).minusDays(1)

                    val budgetsWithSpent = budgets.map { budget ->
                        val cat = categories.find { it.id == budget.categoryId }
                        val spent = budgetRepo.getSpentForCategory(
                            accountId, budget.categoryId, startDate, endDate
                        )
                        BudgetWithSpent(
                            budget        = budget,
                            spent         = spent,
                            categoryName  = cat?.name ?: "Tidak dikenal",
                            categoryColor = cat?.colorHex ?: "#888888"
                        )
                    }.sortedByDescending { it.percentage }

                    // Kategori yang belum punya budget
                    val budgetedCategoryIds = budgets.map { it.categoryId }.toSet()
                    val availableCategories = categories.filter { it.id !in budgetedCategoryIds }

                    _uiState.update {
                        it.copy(
                            isLoading           = false,
                            budgets             = budgetsWithSpent,
                            availableCategories = availableCategories,
                            totalBudget         = budgets.sumOf { b -> b.limitAmount },
                            totalSpent          = budgetsWithSpent.sumOf { b -> b.spent }
                        )
                    }
                }
        }
    }

    fun showAddDialog() {
        _uiState.update { it.copy(showAddDialog = true, editingBudget = null) }
    }

    fun showEditDialog(budget: BudgetEntity) {
        _uiState.update { it.copy(showAddDialog = true, editingBudget = budget) }
    }

    fun dismissDialog() {
        _uiState.update { it.copy(showAddDialog = false, editingBudget = null) }
    }

    fun saveBudget(categoryId: String, limitAmount: Long) {
        val accountId = activeAccountId ?: return
        val state = _uiState.value
        viewModelScope.launch {
            try {
                if (state.editingBudget != null) {
                    budgetRepo.updateBudget(
                        state.editingBudget.copy(
                            categoryId  = categoryId,
                            limitAmount = limitAmount
                        )
                    )
                    _uiState.update { it.copy(successMessage = "Budget diperbarui", showAddDialog = false, editingBudget = null) }
                } else {
                    budgetRepo.createBudget(
                        accountId   = accountId,
                        categoryId  = categoryId,
                        limitAmount = limitAmount,
                        year        = state.year,
                        month       = state.month
                    )
                    _uiState.update { it.copy(successMessage = "Budget ditambahkan", showAddDialog = false) }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = "Gagal menyimpan: ${e.message}") }
            }
        }
    }

    fun deleteBudget(budget: BudgetEntity) {
        viewModelScope.launch {
            try {
                budgetRepo.deleteBudget(budget)
                _uiState.update { it.copy(successMessage = "Budget dihapus") }
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = "Gagal menghapus: ${e.message}") }
            }
        }
    }

    fun navigateMonth(delta: Int) {
        val state = _uiState.value
        var newMonth = state.month + delta
        var newYear = state.year
        if (newMonth < 1) { newMonth = 12; newYear-- }
        if (newMonth > 12) { newMonth = 1; newYear++ }
        _uiState.update { it.copy(month = newMonth, year = newYear, isLoading = true) }
        observeData()
    }

    fun clearMessages() {
        _uiState.update { it.copy(errorMessage = null, successMessage = null) }
    }
}
