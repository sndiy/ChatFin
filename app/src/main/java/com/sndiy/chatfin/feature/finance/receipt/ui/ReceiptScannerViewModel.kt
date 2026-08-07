package com.sndiy.chatfin.feature.finance.receipt.ui

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.mlkit.vision.common.InputImage
import com.sndiy.chatfin.ai.GeminiClient
import com.sndiy.chatfin.ai.ReceiptAiEnhancer
import com.sndiy.chatfin.ai.ReceiptAiResult
import com.sndiy.chatfin.core.data.local.entity.CategoryEntity
import com.sndiy.chatfin.core.data.local.entity.TransactionEntity
import com.sndiy.chatfin.core.data.local.entity.WalletEntity
import com.sndiy.chatfin.core.ocr.ImageUtils
import com.sndiy.chatfin.core.ocr.OcrAnalysisResult
import com.sndiy.chatfin.core.ocr.ParsedReceipt
import com.sndiy.chatfin.core.ocr.ReceiptOcrEngine
import com.sndiy.chatfin.core.ocr.TextBoundingBox
import com.sndiy.chatfin.core.utils.NetworkMonitor
import com.sndiy.chatfin.feature.finance.account.data.repository.AccountRepository
import com.sndiy.chatfin.feature.finance.transaction.data.repository.CategoryRepository
import com.sndiy.chatfin.feature.finance.transaction.data.repository.TransactionRepository
import com.sndiy.chatfin.feature.finance.transaction.data.repository.WalletRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject

data class ReceiptScannerUiState(
    val isAnalyzingFrame: Boolean = false,
    val isProcessingFullOcr: Boolean = false,
    val flashEnabled: Boolean = false,
    val boundingBoxes: List<TextBoundingBox> = emptyList(),
    val frameWidth: Int = 0,
    val frameHeight: Int = 0,
    val parsedReceipt: ParsedReceipt? = null,
    val receiptImageUri: String? = null,
    val wallets: List<WalletEntity> = emptyList(),
    val categories: List<CategoryEntity> = emptyList(),
    val isSaved: Boolean = false,
    val errorMessage: String? = null,
    val isOnline: Boolean = true,
    val isAiKeyAvailable: Boolean = false,
    val isAiEnhancing: Boolean = false,
    @StringRes val aiErrorRes: Int? = null
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ReceiptScannerViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val ocrEngine: ReceiptOcrEngine,
    private val transactionRepo: TransactionRepository,
    private val walletRepo: WalletRepository,
    private val categoryRepo: CategoryRepository,
    private val accountRepo: AccountRepository,
    private val networkMonitor: NetworkMonitor,
    private val geminiClient: GeminiClient,
    private val receiptAiEnhancer: ReceiptAiEnhancer
) : ViewModel() {

    private val _uiState = MutableStateFlow(ReceiptScannerUiState())
    val uiState: StateFlow<ReceiptScannerUiState> = _uiState.asStateFlow()

    private var activeAccountId: String? = null
    private var lastAnalysisTime = 0L

    init {
        viewModelScope.launch {
            accountRepo.getActiveAccount()
                .filterNotNull()
                .flatMapLatest { account ->
                    activeAccountId = account.id
                    combine(
                        walletRepo.getWalletsByAccount(account.id),
                        categoryRepo.getCategoriesByAccountAndType(account.id, "EXPENSE")
                    ) { wallets: List<WalletEntity>, categories: List<CategoryEntity> ->
                        Pair(wallets, categories)
                    }
                }
                .collect { (wallets, categories) ->
                    _uiState.update {
                        it.copy(wallets = wallets, categories = categories)
                    }
                }
        }

        viewModelScope.launch {
            networkMonitor.isConnected.collect { online ->
                _uiState.update { it.copy(isOnline = online) }
            }
        }
    }

    /** Dibaca ulang tiap popup edit dibuka supaya key yang baru diisi user di Setelan langsung terdeteksi. */
    fun refreshAiAvailability() {
        viewModelScope.launch {
            val hasKey = geminiClient.resolveApiKey().isNotBlank()
            _uiState.update { it.copy(isAiKeyAvailable = hasKey) }
        }
    }

    fun toggleFlash() {
        _uiState.update { it.copy(flashEnabled = !it.flashEnabled) }
    }

    /**
     * Memproses frame kamera live stream untuk overlay AR real-time di background thread.
     */
    fun processCameraFrame(inputImage: InputImage) {
        val now = System.currentTimeMillis()
        if (now - lastAnalysisTime < 100L || _uiState.value.isProcessingFullOcr) return
        lastAnalysisTime = now

        viewModelScope.launch(Dispatchers.Default) {
            try {
                val result = ocrEngine.processInputImage(inputImage)
                _uiState.update {
                    it.copy(
                        boundingBoxes = result.boundingBoxes,
                        frameWidth = inputImage.width,
                        frameHeight = inputImage.height
                    )
                }
            } catch (_: Exception) {
                // Abaikan kesalahan analisis frame tunggal
            }
        }
    }

    /**
     * Memproses foto/screenshot struk dari galeri.
     * Loading UI diaktifkan seketika di UI thread, sementara proses berat disk I/O & ML Kit dipindah ke Dispatchers.IO.
     */
    fun processGalleryImage(sourceUri: Uri) {
        _uiState.update { it.copy(isProcessingFullOcr = true, errorMessage = null) }

        viewModelScope.launch {
            try {
                val (savedUri, ocrResult) = withContext(Dispatchers.IO) {
                    val saved = ImageUtils.saveImageToInternalStorage(context, sourceUri)
                    val result = ocrEngine.processImage(context, sourceUri)
                    Pair(saved, result)
                }

                _uiState.update {
                    it.copy(
                        isProcessingFullOcr = false,
                        parsedReceipt = ocrResult.parsedReceipt,
                        receiptImageUri = savedUri ?: sourceUri.toString()
                    )
                }
                refreshAiAvailability()
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isProcessingFullOcr = false,
                        errorMessage = "Gagal membaca struk dari galeri: ${e.localizedMessage}"
                    )
                }
            }
        }
    }

    /**
     * Memproses foto yang ditangkap langsung dari tombol jepret kamera.
     * Loading UI diaktifkan seketika di UI thread, sementara proses kompresi bitmap & OCR dipindah ke Dispatchers.Default.
     */
    fun processCapturedBitmap(bitmap: Bitmap, rotationDegrees: Int) {
        _uiState.update { it.copy(isProcessingFullOcr = true, errorMessage = null) }

        viewModelScope.launch {
            try {
                val (savedUri, ocrResult) = withContext(Dispatchers.Default) {
                    val saved = ImageUtils.saveBitmapToInternalStorage(context, bitmap)
                    val result = ocrEngine.processBitmap(bitmap, rotationDegrees)
                    Pair(saved, result)
                }

                _uiState.update {
                    it.copy(
                        isProcessingFullOcr = false,
                        parsedReceipt = ocrResult.parsedReceipt,
                        receiptImageUri = savedUri
                    )
                }
                refreshAiAvailability()
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isProcessingFullOcr = false,
                        errorMessage = "Gagal memproses foto struk: ${e.localizedMessage}"
                    )
                }
            }
        }
    }

    fun dismissEditDialog() {
        _uiState.update {
            it.copy(
                parsedReceipt = null,
                errorMessage = null,
                isAiEnhancing = false,
                aiErrorRes = null
            )
        }
    }

    fun dismissAiError() {
        _uiState.update { it.copy(aiErrorRes = null) }
    }

    /**
     * Lapisan kedua opsional: kirim gambar struk yang sudah tersimpan ke Gemini supaya
     * subtotal/diskon/pajak/total dibedakan dengan konteks, bukan cuma OCR karakter.
     * Hanya berjalan saat user menekan tombol secara eksplisit — bukan otomatis di tiap scan.
     */
    fun enhanceWithAi() {
        val current = _uiState.value
        // Anti double-tap: satu request berjalan pada satu waktu, mencegah retry
        // tak sengaja yang bisa memboroskan kuota harian user.
        if (current.isAiEnhancing) return
        val baseline = current.parsedReceipt ?: return
        val imageUri = current.receiptImageUri ?: return

        _uiState.update { it.copy(isAiEnhancing = true, aiErrorRes = null) }

        viewModelScope.launch {
            when (val result = receiptAiEnhancer.enhance(context, imageUri, baseline)) {
                is ReceiptAiResult.Success -> {
                    _uiState.update {
                        it.copy(isAiEnhancing = false, parsedReceipt = result.receipt)
                    }
                }
                is ReceiptAiResult.Failure -> {
                    // Gagal (kuota/timeout/parse) → tetap pakai hasil ML Kit yang sudah ada.
                    _uiState.update {
                        it.copy(isAiEnhancing = false, aiErrorRes = result.messageRes)
                    }
                }
            }
        }
    }

    /**
     * Menyimpan transaksi hasil konfirmasi/edit struk beserta rincian item ke database SQLite Room.
     */
    fun saveReceiptTransaction(
        merchant: String,
        date: String,
        time: String,
        totalAmount: Long,
        walletId: String,
        categoryId: String,
        itemsSummary: String,
        editedItems: List<com.sndiy.chatfin.core.ocr.ParsedReceiptItem> = emptyList()
    ) {
        val accountId = activeAccountId ?: return
        val receiptImageUri = _uiState.value.receiptImageUri

        viewModelScope.launch {
            try {
                val note = merchant.trim().ifBlank { "Struk Belanja" }

                val parsedDate = runCatching { java.time.LocalDate.parse(date) }.getOrDefault(java.time.LocalDate.now())
                val parsedTime = runCatching { java.time.LocalTime.parse(time) }.getOrDefault(java.time.LocalTime.now())

                withContext(Dispatchers.IO) {
                    transactionRepo.addTransactionWithItems(
                        accountId = accountId,
                        type = "EXPENSE",
                        amount = totalAmount,
                        categoryId = categoryId,
                        walletId = walletId,
                        note = note,
                        receiptImageUri = receiptImageUri,
                        date = parsedDate,
                        time = parsedTime,
                        items = editedItems
                    )
                }

                _uiState.update { it.copy(isSaved = true, parsedReceipt = null) }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(errorMessage = "Gagal menyimpan transaksi: ${e.localizedMessage}")
                }
            }
        }
    }
}
