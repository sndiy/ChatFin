package com.sndiy.chatfin.feature.finance.category.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sndiy.chatfin.core.data.local.entity.CategoryEntity
import com.sndiy.chatfin.feature.finance.account.data.repository.AccountRepository
import com.sndiy.chatfin.feature.finance.transaction.data.repository.CategoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
    val isLoading: Boolean                 = true,
    val errorMessage: String?              = null
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class CategoryViewModel @Inject constructor(
    private val categoryRepo: CategoryRepository,
    private val accountRepo: AccountRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(CategoryUiState())
    val uiState: StateFlow<CategoryUiState> = _uiState.asStateFlow()

    // Sumber kebenaran untuk tab aktif — bagian dari upstream Flow, bukan
    // dibaca dari _uiState.value di dalam transform. Ini yang membuat
    // flatMapLatest di bawah bisa membatalkan collector lama secara otomatis
    // setiap kali tab ATAU akun berganti, alih-alih menumpuk collector baru
    // setiap kali onTabChange() dipanggil (bug lama: kategori berkedip acak).
    private val _activeType = MutableStateFlow("EXPENSE")

    private var activeAccountId: String? = null

    init {
        viewModelScope.launch {
            combine(
                accountRepo.getActiveAccount().filterNotNull(),
                _activeType
            ) { account, type -> account.id to type }
                .distinctUntilChanged()
                .flatMapLatest { (accountId, type) ->
                    activeAccountId = accountId
                    _uiState.update { it.copy(isLoading = true) }
                    categoryRepo.getCategoriesByAccountAndType(accountId, type)
                }
                .collect { cats ->
                    _uiState.update { it.copy(categories = cats, isLoading = false) }
                }
        }
    }

    fun onTabChange(type: String) {
        _uiState.update { it.copy(activeType = type) }
        _activeType.value = type
    }

    fun showAddDialog() {
        _uiState.update {
            it.copy(
                showDialog      = true,
                editingCategory = null,
                formState       = CategoryFormState()
            )
        }
    }

    fun showEditDialog(category: CategoryEntity) {
        _uiState.update {
            it.copy(
                showDialog      = true,
                editingCategory = category,
                formState       = CategoryFormState(name = category.name, colorHex = category.colorHex)
            )
        }
    }

    fun hideDialog() {
        _uiState.update { it.copy(showDialog = false) }
    }

    fun onNameChange(value: String) {
        _uiState.update {
            it.copy(formState = it.formState.copy(name = value, nameError = null))
        }
    }

    fun onColorChange(hex: String) {
        _uiState.update {
            it.copy(formState = it.formState.copy(colorHex = hex))
        }
    }

    fun saveCategory() {
        val form      = _uiState.value.formState
        val accountId = activeAccountId ?: return

        if (form.name.isBlank()) {
            _uiState.update {
                it.copy(formState = form.copy(nameError = "Nama kategori tidak boleh kosong"))
            }
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
                _uiState.update { it.copy(showDialog = false) }
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = "Gagal menyimpan: ${e.message}") }
            }
        }
    }

    fun deleteCategory(category: CategoryEntity) {
        if (!category.isCustom) {
            _uiState.update { it.copy(errorMessage = "Kategori default tidak bisa dihapus") }
            return
        }
        viewModelScope.launch {
            try {
                categoryRepo.deleteCategory(category)
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = "Gagal menghapus: ${e.message}") }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}