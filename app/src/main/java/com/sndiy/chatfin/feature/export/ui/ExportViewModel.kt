// app/src/main/java/com/sndiy/chatfin/feature/export/ui/ExportViewModel.kt

package com.sndiy.chatfin.feature.export.ui

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sndiy.chatfin.feature.export.data.ExportRepository
import com.sndiy.chatfin.feature.finance.account.data.repository.AccountRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

enum class ExportPeriod(val label: String) {
    THIS_MONTH("Bulan Ini"),
    LAST_MONTH("Bulan Lalu"),
    LAST_3_MONTHS("3 Bulan"),
    LAST_6_MONTHS("6 Bulan"),
    THIS_YEAR("Tahun Ini"),
    ALL("Semua")
}

data class ExportUiState(
    val selectedPeriod: ExportPeriod = ExportPeriod.THIS_MONTH,
    val isExporting: Boolean        = false,
    val successMessage: String?     = null,
    val errorMessage: String?       = null
)

@HiltViewModel
class ExportViewModel @Inject constructor(
    private val exportRepo: ExportRepository,
    private val accountRepo: AccountRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ExportUiState())
    val uiState: StateFlow<ExportUiState> = _uiState.asStateFlow()

    fun selectPeriod(period: ExportPeriod) {
        _uiState.update { it.copy(selectedPeriod = period) }
    }

    fun exportCsv(uri: Uri) = export("CSV", uri)
    fun exportPdf(uri: Uri) = export("PDF", uri)

    private fun export(type: String, uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(isExporting = true, successMessage = null, errorMessage = null) }

            val account = accountRepo.getActiveAccount().first()
            if (account == null) {
                _uiState.update { it.copy(isExporting = false, errorMessage = "Tidak ada akun aktif") }
                return@launch
            }

            val (startDate, endDate) = periodToRange(_uiState.value.selectedPeriod)

            val result = when (type) {
                "PDF" -> exportRepo.exportPdf(uri, account.id, startDate, endDate)
                else  -> exportRepo.exportCsv(uri, account.id, startDate, endDate)
            }

            result.fold(
                onSuccess = { msg -> _uiState.update { it.copy(isExporting = false, successMessage = msg) } },
                onFailure = { err -> _uiState.update { it.copy(isExporting = false, errorMessage = err.message) } }
            )
        }
    }

    fun generateFileName(type: String): String = exportRepo.generateFileName(type)

    fun clearMessages() {
        _uiState.update { it.copy(successMessage = null, errorMessage = null) }
    }

    private fun periodToRange(period: ExportPeriod): Pair<LocalDate, LocalDate> {
        val today = LocalDate.now()
        return when (period) {
            ExportPeriod.THIS_MONTH    -> today.withDayOfMonth(1) to today
            ExportPeriod.LAST_MONTH    -> {
                val start = today.minusMonths(1).withDayOfMonth(1)
                start to start.plusMonths(1).minusDays(1)
            }
            ExportPeriod.LAST_3_MONTHS -> today.minusMonths(3).withDayOfMonth(1) to today
            ExportPeriod.LAST_6_MONTHS -> today.minusMonths(6).withDayOfMonth(1) to today
            ExportPeriod.THIS_YEAR     -> today.withDayOfYear(1) to today
            ExportPeriod.ALL           -> LocalDate.of(2020, 1, 1) to today
        }
    }
}
