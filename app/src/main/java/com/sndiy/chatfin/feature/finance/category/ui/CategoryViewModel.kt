package com.sndiy.chatfin.feature.finance.category.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sndiy.chatfin.core.data.local.entity.CategoryEntity
import com.sndiy.chatfin.feature.finance.account.data.repository.AccountRepository
import com.sndiy.chatfin.feature.finance.transaction.data.repository.CategoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CategoryFormState(
    val name: String      = "",
    val colorHex: String  = "#0061A4",
    val nameError: String? = null
)

data class CategoryUiState(
    val categories: List<CategoryEntity>   = emptyList(),
    val activeType: String                 = "EXPENSE",
    val showDialog: Boolean                = false,
    val editingCategory: CategoryEntity?   = null,
    val formState: CategoryFormState       = CategoryFormState(),
    val errorMessage: String?              = null
)

@HiltViewModel
class CategoryViewModel @Inject constructor(
    private val categoryRepo: CategoryRepository,
    private val accountRepo: AccountRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(CategoryUiState())
    val uiState: StateFlow<CategoryUiState> = _uiState.asStateFlow()

    private var activeAccountId: String? = null

    init {
        viewModelScope.launch {
            accountRepo.getActiveAccount().collect { account ->
                activeAccountId = account?.id
                if (account != null) loadCategories(account.id, "EXPENSE")
            }
        }
    }

    private fun loadCategories(accountId: String, type: String) {
        viewModelScope.launch {
            categoryRepo.getCategoriesByAccountAndType(accountId, type).collect { cats ->
                _uiState.value = _uiState.value.copy(categories = cats)
            }
        }
    }

    fun onTabChange(type: String) {
        _uiState.value = _uiState.value.copy(activeType = type)
        val accountId  = activeAccountId ?: return
        loadCategories(accountId, type)
    }

    fun showAddDialog() {
        _uiState.value = _uiState.value.copy(
            showDialog      = true,
            editingCategory = null,
            formState       = CategoryFormState()
        )
    }

    fun showEditDialog(category: CategoryEntity) {
        _uiState.value = _uiState.value.copy(
            showDialog      = true,
            editingCategory = category,
            formState       = CategoryFormState(name = category.name, colorHex = category.colorHex)
        )
    }

    fun hideDialog() {
        _uiState.value = _uiState.value.copy(showDialog = false)
    }

    fun onNameChange(value: String) {
        _uiState.value = _uiState.value.copy(
            formState = _uiState.value.formState.copy(name = value, nameError = null)
        )
    }

    fun onColorChange(hex: String) {
        _uiState.value = _uiState.value.copy(
            formState = _uiState.value.formState.copy(colorHex = hex)
        )
    }

    fun saveCategory() {
        val form      = _uiState.value.formState
        val accountId = activeAccountId ?: return

        if (form.name.isBlank()) {
            _uiState.value = _uiState.value.copy(
                formState = form.copy(nameError = "Nama kategori tidak boleh kosong")
            )
            return
        }

        viewModelScope.launch {
            try {
                val editing = _uiState.value.editingCategory
                if (editing != null) {
                    categoryRepo.updateCategory(
                        editing.copy(name = form.name.trim(), colorHex = form.colorHex)
                    )
                } else {
                    categoryRepo.createCategory(
                        accountId = accountId,
                        name      = form.name.trim(),
                        type      = _uiState.value.activeType,
                        iconName  = "category",
                        colorHex  = form.colorHex
                    )
                }
                _uiState.value = _uiState.value.copy(showDialog = false)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(errorMessage = "Gagal menyimpan: ${e.message}")
            }
        }
    }

    fun deleteCategory(category: CategoryEntity) {
        if (!category.isCustom) {
            _uiState.value = _uiState.value.copy(errorMessage = "Kategori default tidak bisa dihapus")
            return
        }
        viewModelScope.launch {
            try {
                categoryRepo.deleteCategory(category)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(errorMessage = "Gagal menghapus: ${e.message}")
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }
}