package com.sndiy.chatfin.feature.chat.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sndiy.chatfin.ai.*
import com.sndiy.chatfin.core.data.local.entity.*
import com.sndiy.chatfin.core.data.sync.SyncEventBus
import com.sndiy.chatfin.core.utils.NetworkMonitor
import com.sndiy.chatfin.feature.finance.account.data.repository.AccountRepository
import com.sndiy.chatfin.feature.finance.transaction.data.repository.CategoryRepository
import com.sndiy.chatfin.feature.finance.transaction.data.repository.TransactionRepository
import com.sndiy.chatfin.feature.finance.transaction.data.repository.WalletRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID
import javax.inject.Inject

data class UiMessage(
    val id: String          = UUID.randomUUID().toString(),
    val role: String,
    val text: String,
    val option: ChatOption? = null,
    val isLoading: Boolean  = false,
    val isError: Boolean    = false
)

enum class ConnectionStatus {
    CONNECTED, NO_INTERNET, QUOTA_LIMIT, BOT_MODE
}

data class ChatUiState(
    val messages: List<UiMessage>               = emptyList(),
    val inputText: String                       = "",
    val isTyping: Boolean                       = false,
    val isBotMode: Boolean                      = false,
    val connectionStatus: ConnectionStatus      = ConnectionStatus.CONNECTED,
    val retryCountdown: Int                     = 0,
    val activeModelName: String                 = "gemini-2.5-flash",
    val activeAccount: FinanceAccountEntity?    = null,
    val wallets: List<WalletEntity>             = emptyList(),
    val expenseCategories: List<CategoryEntity> = emptyList(),
    val incomeCategories: List<CategoryEntity>  = emptyList(),
    val pendingTransaction: PendingTransaction? = null
)

data class PendingTransaction(
    val type: String,
    val amount: Long,
    val categoryName: String,
    val walletName: String,
    val categoryId: String,
    val walletId: String,
    val desc: String = ""
)

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val geminiRepo: GeminiRepository,
    private val accountRepo: AccountRepository,
    private val walletRepo: WalletRepository,
    private val categoryRepo: CategoryRepository,
    private val transactionRepo: TransactionRepository,
    private val systemPromptBuilder: SystemPromptBuilder,
    private val contextBuilder: FinanceContextBuilder,
    private val botHandler: BotModeHandler,
    private val geminiClient: GeminiClient,
    private val networkMonitor: NetworkMonitor,
    private val syncEventBus: SyncEventBus
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private val _isCheckingNetwork = MutableStateFlow(true)
    val isCheckingNetwork: StateFlow<Boolean> = _isCheckingNetwork.asStateFlow()

    private val chatHistory   = mutableListOf<Pair<String, String>>()
    private var systemPrompt  = ""
    private var botStep: BotStep = BotStep.Idle
    private var generationJob: Job? = null
    private var retryJob: Job?      = null
    private var currentLoadingId: String? = null

    private var lastUserMessage: String = ""
    private var lastHistorySnapshot: List<Pair<String, String>> = emptyList()
    private var hasShownOfflineMessage = false

    // FIX Bug 2: batasi retry maksimal 1x per pesan
    private var aiRetryCount = 0

    init {
        observeActiveAccount()
        observeNetwork()
        observeSyncEvent()
    }

    private fun observeSyncEvent() {
        viewModelScope.launch {
            syncEventBus.syncCompleted.collect {
                android.util.Log.d("ChatVM", "Sync selesai, reload data")
                val account = _uiState.value.activeAccount
                if (account != null) {
                    loadAccountData(account.id)
                } else {
                    // Coba ambil akun aktif dari database
                    accountRepo.getActiveAccount().first()?.let { acc ->
                        _uiState.update { it.copy(activeAccount = acc) }
                        loadAccountData(acc.id)
                    }
                }
            }
        }
    }

    // ── Monitor jaringan ──────────────────────────────────────────────────────
    private fun observeNetwork() {
        viewModelScope.launch {
            _isCheckingNetwork.value = true
            val connected = networkMonitor.isCurrentlyConnected()
            if (!connected) switchToOfflineMode()
            _isCheckingNetwork.value = false

            networkMonitor.isConnected.collect { isConnected ->
                val currentStatus = _uiState.value.connectionStatus
                if (!isConnected && currentStatus != ConnectionStatus.NO_INTERNET) {
                    generationJob?.cancel()
                    currentLoadingId?.let { removeMessage(it) }
                    currentLoadingId = null
                    _uiState.value = _uiState.value.copy(isTyping = false)
                    // FIX Bug 1: rollback history yang belum dapat response
                    if (chatHistory.lastOrNull()?.first == "user") {
                        chatHistory.removeLastOrNull()
                    }
                    switchToOfflineMode()
                } else if (isConnected && currentStatus == ConnectionStatus.NO_INTERNET) {
                    hasShownOfflineMessage = false
                }
            }
        }
    }

    private fun switchToOfflineMode() {
        _uiState.value = _uiState.value.copy(
            connectionStatus = ConnectionStatus.NO_INTERNET,
            isBotMode        = true
        )
        // FIX Bug 3: reset botStep saat masuk offline biar tidak stuck di tengah alur
        if (botStep !is BotStep.Idle) {
            botStep = BotStep.Idle
        }
        if (!hasShownOfflineMessage) {
            hasShownOfflineMessage = true
            addMessage(UiMessage(
                role = "model",
                text = "Tidak ada koneksi internet. Mode Bot aktif — ketik *help* untuk perintah yang tersedia."
            ))
        }
    }

    // ── Observe akun aktif ────────────────────────────────────────────────────
    private fun observeActiveAccount() {
        viewModelScope.launch {
            accountRepo.getActiveAccount().collect { account ->
                _uiState.value = _uiState.value.copy(activeAccount = account)
                if (account != null) loadAccountData(account.id)
            }
        }
    }

    private fun loadAccountData(accountId: String) {
        viewModelScope.launch {
            val now   = LocalDate.now()
            val start = now.withDayOfMonth(1)
            combine(
                walletRepo.getWalletsByAccount(accountId),
                categoryRepo.getCategoriesByAccountAndType(accountId, "EXPENSE"),
                categoryRepo.getCategoriesByAccountAndType(accountId, "INCOME"),
                transactionRepo.getTotalIncome(accountId, start, now),
                transactionRepo.getTotalExpense(accountId, start, now)
            ) { wallets, expCats, incCats, income, expense ->
                object {
                    val w  = wallets
                    val ec = expCats
                    val ic = incCats
                    val ti = income ?: 0L
                    val te = expense ?: 0L
                }
            }.collect { data ->
                _uiState.value = _uiState.value.copy(
                    wallets           = data.w,
                    expenseCategories = data.ec,
                    incomeCategories  = data.ic
                )
                rebuildSystemPrompt(data.ti, data.te)
            }
        }
    }

    private fun rebuildSystemPrompt(totalIncome: Long = 0L, totalExpense: Long = 0L) {
        val state = _uiState.value
        val ctx   = contextBuilder.buildContext(
            account           = state.activeAccount,
            wallets           = state.wallets,
            expenseCategories = state.expenseCategories,
            incomeCategories  = state.incomeCategories,
            totalIncome       = totalIncome,
            totalExpense      = totalExpense
        )
        systemPrompt = systemPromptBuilder.build(
            financeContext = ctx,
            userName       = state.activeAccount?.name ?: "Kamu"
        )
    }

    fun onInputChange(text: String) {
        _uiState.value = _uiState.value.copy(inputText = text)
    }

    // ── Stop generation ───────────────────────────────────────────────────────
    fun stopGeneration() {
        generationJob?.cancel()
        generationJob    = null
        currentLoadingId?.let { removeMessage(it) }
        currentLoadingId = null
        // FIX Bug 1: rollback history kalau stop di tengah jalan
        if (chatHistory.lastOrNull()?.first == "user") {
            chatHistory.removeLastOrNull()
        }
        _uiState.value = _uiState.value.copy(isTyping = false)
    }

    // ── Retry AI ──────────────────────────────────────────────────────────────
    fun retryAi() {
        if (_uiState.value.retryCountdown > 0) return

        if (!networkMonitor.isCurrentlyConnected()) {
            startRetryCountdown(10)
            return
        }

        startRetryCountdown(10)
        hasShownOfflineMessage = false
        aiRetryCount = 0

        // FIX Bug 3: pastikan botStep bersih saat keluar bot mode
        botStep = BotStep.Idle
        _uiState.value = _uiState.value.copy(
            connectionStatus = ConnectionStatus.CONNECTED,
            isBotMode        = false
        )
        if (lastUserMessage.isNotBlank()) {
            executeAiRequest(lastUserMessage, lastHistorySnapshot)
        }
    }

    private fun startRetryCountdown(seconds: Int) {
        retryJob?.cancel()
        retryJob = viewModelScope.launch {
            for (i in seconds downTo 1) {
                _uiState.value = _uiState.value.copy(retryCountdown = i)
                delay(1000)
            }
            _uiState.value = _uiState.value.copy(retryCountdown = 0)
        }
    }

    // ── Kirim pesan ───────────────────────────────────────────────────────────
    fun sendMessage() {
        val text = _uiState.value.inputText.trim()
        if (text.isBlank() || _uiState.value.isTyping) return

        addMessage(UiMessage(role = "user", text = text))
        _uiState.value = _uiState.value.copy(inputText = "")

        // Kalau sedang di tengah alur bot → lanjutkan ke bot tanpa cek intent
        if (botStep !is BotStep.Idle) {
            handleBotMode(text)
            return
        }

        // Cek internet
        if (!networkMonitor.isCurrentlyConnected()) {
            switchToOfflineMode()
            handleBotMode(text)
            return
        }

        // Routing berdasarkan intent
        if (isTransactionIntent(text)) {
            handleBotMode(text)
        } else {
            sendToAi(text)
        }
    }

    // ── Routing ke AI ─────────────────────────────────────────────────────────
    private fun sendToAi(text: String) {
        val historySnapshot = chatHistory.toList()
        // FIX Bug 1: tambah ke history SETELAH dapat response sukses, bukan sebelum
        lastUserMessage     = text
        lastHistorySnapshot = historySnapshot
        aiRetryCount        = 0
        executeAiRequest(text, historySnapshot)
    }

    // ── Execute AI request ────────────────────────────────────────────────────
    private fun executeAiRequest(text: String, historySnapshot: List<Pair<String, String>>) {
        _uiState.value = _uiState.value.copy(
            isTyping        = true,
            activeModelName = geminiClient.currentModelName
        )

        val loadingId = UUID.randomUUID().toString()
        currentLoadingId = loadingId
        addMessage(UiMessage(id = loadingId, role = "model", text = "", isLoading = true))

        generationJob = viewModelScope.launch {
            val result = geminiRepo.sendMessage(
                userMessage  = text,
                chatHistory  = historySnapshot,
                systemPrompt = systemPrompt
            )
            currentLoadingId = null
            removeMessage(loadingId)
            _uiState.value = _uiState.value.copy(isTyping = false)

            result.fold(
                onSuccess = { parsed ->
                    hasShownOfflineMessage = false
                    aiRetryCount = 0
                    // FIX Bug 1: baru masukkan ke history setelah sukses
                    chatHistory.add("user" to text)
                    chatHistory.add("model" to parsed.text)
                    _uiState.value = _uiState.value.copy(
                        connectionStatus = ConnectionStatus.CONNECTED,
                        activeModelName  = geminiClient.currentModelName
                    )
                    addMessage(UiMessage(role = "model", text = parsed.text, option = parsed.option))
                    if (parsed.option is ChatOption.TransactionConfirm) {
                        preparePendingTransaction(parsed.option)
                    }
                },
                onFailure = { error ->
                    val isNoInternet = error.message?.contains("internet", ignoreCase = true) == true ||
                            error.message?.contains("Unable to resolve", ignoreCase = true) == true ||
                            error.message?.contains("network", ignoreCase = true) == true
                    val isAllLimit = error is QuotaExhaustedException &&
                            error.message?.contains("semua") == true
                    // FIX Bug 2: isOneLimit hanya retry 1x, bukan infinite
                    val isOneLimit = error is QuotaExhaustedException && !isAllLimit

                    when {
                        isNoInternet -> {
                            switchToOfflineMode()
                        }
                        isOneLimit && aiRetryCount < 1 -> {
                            aiRetryCount++
                            android.util.Log.d("ChatVM", "Model limit, retry ke-$aiRetryCount dengan ${geminiClient.currentModelName}")
                            _uiState.value = _uiState.value.copy(
                                activeModelName = geminiClient.currentModelName,
                                isTyping        = false
                            )
                            executeAiRequest(text, historySnapshot)
                        }
                        isOneLimit || isAllLimit -> {
                            // Sudah retry atau semua model limit
                            _uiState.value = _uiState.value.copy(
                                connectionStatus = ConnectionStatus.QUOTA_LIMIT
                            )
                            addMessage(UiMessage(
                                role    = "model",
                                text    = "Semua model AI sedang limit.",
                                isError = true
                            ))
                        }
                        else -> {
                            addMessage(UiMessage(
                                role    = "model",
                                text    = error.message ?: "Terjadi kesalahan",
                                isError = true
                            ))
                        }
                    }
                }
            )
        }
    }

    fun switchToBotMode() {
        botStep = BotStep.Idle // FIX Bug 3: pastikan state bersih
        _uiState.value = _uiState.value.copy(
            isBotMode        = true,
            connectionStatus = ConnectionStatus.BOT_MODE
        )
        addMessage(UiMessage(
            role = "model",
            text = "Mode Bot aktif. Ketik *help* untuk melihat perintah yang tersedia."
        ))
    }

    // ── Bot mode ──────────────────────────────────────────────────────────────
    // FIX Bug 4: hapus kondisi kontradiktif, logika diperjelas
    private fun handleBotMode(input: String) {
        val state        = _uiState.value
        val totalBalance = state.wallets.sumOf { it.balance }
        val result       = botHandler.handle(
            input             = input,
            currentStep       = botStep,
            wallets           = state.wallets,
            expenseCategories = state.expenseCategories,
            incomeCategories  = state.incomeCategories,
            totalBalance      = totalBalance
        )
        botStep = result.nextStep

        if (result.text == "__RANGKUMAN__") { handleRangkuman(); return }

        // FIX Bug 7: hanya satu jalur simpan — via saveBotTransaction, bukan confirmTransaction
        if (result.saveTransaction != null) {
            saveBotTransaction(result.saveTransaction)
            addMessage(UiMessage(role = "model", text = result.text))
            return
        }

        if (result.requestAiConfirm != null) {
            botStep = BotStep.Idle
            generateAiConfirm(result.requestAiConfirm)
            return
        }

        if (result.text.isNotBlank() || result.option != null) {
            addMessage(UiMessage(role = "model", text = result.text, option = result.option))
        }
    }

    // ── Generate AI confirm ───────────────────────────────────────────────────
    private fun generateAiConfirm(req: AiConfirmRequest) {
        // FIX Bug 5: cek internet dulu sebelum panggil AI
        if (!networkMonitor.isCurrentlyConnected()) {
            val fmt       = java.text.NumberFormat.getNumberInstance(java.util.Locale("id", "ID"))
            val typeLabel = if (req.type == "INCOME") "Pemasukan" else "Pengeluaran"
            val descLine  = if (req.desc.isNotBlank()) "\n📋 Judul    : ${req.desc}" else ""
            val fallback  = ChatOption.TransactionConfirm(
                type     = req.type,
                amount   = req.amount,
                category = req.category,
                wallet   = req.wallet,
                title    = req.desc.ifBlank { "${req.category} ${req.wallet}" }
            )
            addMessage(UiMessage(
                role   = "model",
                text   = "📋 *Konfirmasi $typeLabel*\n\n" +
                        "💰 Nominal  : Rp ${fmt.format(req.amount)}\n" +
                        "🏷️ Kategori : ${req.category}\n" +
                        "👛 Dompet   : ${req.wallet}$descLine\n\n" +
                        "Sudah benar?",
                option = fallback
            ))
            preparePendingTransaction(fallback)
            return
        }

        val confirmPrompt = systemPromptBuilder.buildConfirmPrompt(
            userName = _uiState.value.activeAccount?.name ?: "Kamu",
            type     = req.type,
            amount   = req.amount,
            category = req.category,
            wallet   = req.wallet,
            desc     = req.desc
        )

        _uiState.value = _uiState.value.copy(isTyping = true)
        val loadingId = UUID.randomUUID().toString()
        currentLoadingId = loadingId
        addMessage(UiMessage(id = loadingId, role = "model", text = "", isLoading = true))

        viewModelScope.launch {
            val result = geminiRepo.sendMessage(
                userMessage  = "Generate konfirmasi transaksi.",
                chatHistory  = emptyList(),
                systemPrompt = confirmPrompt
            )
            currentLoadingId = null
            removeMessage(loadingId)
            _uiState.value = _uiState.value.copy(isTyping = false)

            result.fold(
                onSuccess = { parsed ->
                    // Sanitasi judul kalau AI tidak ganti placeholder
                    val sanitizedOption = if (parsed.option is ChatOption.TransactionConfirm) {
                        val opt = parsed.option
                        if (opt.title.contains("GANTI_DENGAN_JUDUL", ignoreCase = true)) {
                            opt.copy(title = req.desc.ifBlank { "${req.category} ${req.wallet}" })
                        } else opt
                    } else parsed.option

                    addMessage(UiMessage(role = "model", text = parsed.text, option = sanitizedOption))
                    if (sanitizedOption is ChatOption.TransactionConfirm) {
                        preparePendingTransaction(sanitizedOption)
                    } else {
                        // AI respond tapi tidak kasih confirm option → fallback
                        val fallback = ChatOption.TransactionConfirm(
                            type     = req.type,
                            amount   = req.amount,
                            category = req.category,
                            wallet   = req.wallet,
                            title    = req.desc.ifBlank { "${req.category} ${req.wallet}" }
                        )
                        preparePendingTransaction(fallback)
                    }
                },
                onFailure = {
                    val fmt       = java.text.NumberFormat.getNumberInstance(java.util.Locale("id", "ID"))
                    val typeLabel = if (req.type == "INCOME") "Pemasukan" else "Pengeluaran"
                    val descLine  = if (req.desc.isNotBlank()) "\n📋 Judul    : ${req.desc}" else ""
                    val fallback  = ChatOption.TransactionConfirm(
                        type     = req.type,
                        amount   = req.amount,
                        category = req.category,
                        wallet   = req.wallet,
                        title    = req.desc.ifBlank { "${req.category} ${req.wallet}" }
                    )
                    addMessage(UiMessage(
                        role   = "model",
                        text   = "📋 *Konfirmasi $typeLabel*\n\n" +
                                "💰 Nominal  : Rp ${fmt.format(req.amount)}\n" +
                                "🏷️ Kategori : ${req.category}\n" +
                                "👛 Dompet   : ${req.wallet}$descLine\n\n" +
                                "Sudah benar?",
                        option = fallback
                    ))
                    preparePendingTransaction(fallback)
                }
            )
        }
    }

    // ── Rangkuman ─────────────────────────────────────────────────────────────
    private fun handleRangkuman() {
        val state   = _uiState.value
        val account = state.activeAccount ?: return
        val now     = LocalDate.now()
        val start   = now.withDayOfMonth(1)
        viewModelScope.launch {
            try {
                combine(
                    transactionRepo.getTransactionsByPeriod(account.id, start, now),
                    transactionRepo.getTotalIncome(account.id, start, now),
                    transactionRepo.getTotalExpense(account.id, start, now)
                ) { _, income, expense -> Pair(income ?: 0L, expense ?: 0L) }
                    .first()
                    .let { (income, expense) ->
                        val fmt = java.text.NumberFormat.getNumberInstance(java.util.Locale("id", "ID"))
                        fun rp(v: Long) = "Rp ${fmt.format(v)}"
                        val text = buildString {
                            appendLine("📊 *Rangkuman ${now.month.getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale("id", "ID"))} ${now.year}*")
                            appendLine()
                            appendLine("💚 Pemasukan  : ${rp(income)}")
                            appendLine("❤️ Pengeluaran: ${rp(expense)}")
                            appendLine("📈 Selisih    : ${rp(income - expense)}")
                            appendLine()
                            appendLine("💼 *Saldo per Dompet*")
                            state.wallets.forEach { w -> appendLine("• ${w.name}: ${rp(w.balance)}") }
                            appendLine()
                            append("Total: ${rp(state.wallets.sumOf { it.balance })}")
                        }
                        addMessage(UiMessage(role = "model", text = text.trim()))
                    }
            } catch (e: Exception) {
                addMessage(UiMessage(role = "model", text = "Gagal memuat rangkuman: ${e.message}", isError = true))
            }
        }
    }

    // ── Simpan transaksi dari bot ─────────────────────────────────────────────
    private fun saveBotTransaction(req: SaveRequest) {
        val state     = _uiState.value
        val accountId = state.activeAccount?.id ?: return
        val category  = (state.expenseCategories + state.incomeCategories)
            .find { it.name.equals(req.categoryName, ignoreCase = true) } ?: return
        val wallet    = state.wallets
            .find { it.name.equals(req.walletName, ignoreCase = true) } ?: return
        viewModelScope.launch {
            try {
                transactionRepo.addTransaction(
                    accountId  = accountId,
                    type       = req.type,
                    amount     = req.amount,
                    categoryId = category.id,
                    walletId   = wallet.id,
                    note       = req.desc.ifBlank { null },
                    date       = LocalDate.now(),
                    time       = LocalTime.now()
                )
            } catch (e: Exception) {
                addMessage(UiMessage(role = "model", text = "Gagal simpan: ${e.message}", isError = true))
            }
        }
    }

    // ── Option selected ───────────────────────────────────────────────────────
    fun onOptionSelected(option: ChatOption, selectedValue: String) {
        _uiState.value = _uiState.value.copy(
            messages = _uiState.value.messages.map { msg ->
                if (msg.option == option) msg.copy(option = null) else msg
            }
        )
        addMessage(UiMessage(role = "user", text = selectedValue))

        // Pilihan kategori/dompet/konfirmasi → selalu ke bot
        val isTransactionOption = option is ChatOption.CategoryOptions ||
                option is ChatOption.WalletOptions   ||
                option is ChatOption.TransactionConfirm

        if (isTransactionOption || botStep !is BotStep.Idle) {
            handleBotMode(selectedValue)
            return
        }

        // YesNo dari AI → kirim ke AI
        sendToAi(selectedValue)
    }

    // ── Konfirmasi transaksi dari AI (bukan bot) ──────────────────────────────
    // FIX Bug 7: confirmTransaction hanya untuk transaksi yang datang dari AI
    fun confirmTransaction() {
        val pending   = _uiState.value.pendingTransaction ?: return
        val accountId = _uiState.value.activeAccount?.id ?: return
        viewModelScope.launch {
            try {
                transactionRepo.addTransaction(
                    accountId  = accountId,
                    type       = pending.type,
                    amount     = pending.amount,
                    categoryId = pending.categoryId,
                    walletId   = pending.walletId,
                    note       = pending.desc.ifBlank { null },
                    date       = LocalDate.now(),
                    time       = LocalTime.now()
                )
                _uiState.value = _uiState.value.copy(pendingTransaction = null)
                clearAllOptions()
                val fmt       = java.text.NumberFormat.getNumberInstance(java.util.Locale("id", "ID"))
                val now       = LocalTime.now()
                val timeStr   = "%02d:%02d".format(now.hour, now.minute)
                val sign      = if (pending.type == "INCOME") "+" else "-"
                val titlePart = if (pending.desc.isNotBlank()) " · ${pending.desc}" else ""
                addMessage(UiMessage(
                    role = "model",
                    text = "Tersimpan.$titlePart ${sign}Rp ${fmt.format(pending.amount)} · ${pending.categoryName} · ${pending.walletName} · $timeStr"
                ))
                chatHistory.add("model" to "Transaksi tersimpan.")
            } catch (e: Exception) {
                addMessage(UiMessage(role = "model", text = "Gagal menyimpan: ${e.message}", isError = true))
            }
        }
    }

    fun cancelTransaction() {
        _uiState.value = _uiState.value.copy(pendingTransaction = null)
        clearAllOptions()
        addMessage(UiMessage(role = "user", text = "Batal"))
        chatHistory.add("user" to "Batal")
        // FIX Bug 3: reset botStep saat cancel supaya tidak stuck
        botStep = BotStep.Idle
    }

    private fun clearAllOptions() {
        _uiState.value = _uiState.value.copy(
            messages = _uiState.value.messages.map { msg ->
                if (msg.option != null) msg.copy(option = null) else msg
            }
        )
    }

    // ── Deteksi intent transaksi ──────────────────────────────────────────────
    // FIX Bug 6: logika diperbaiki, tidak ada false positive dari angka sembarangan
    private fun isTransactionIntent(input: String): Boolean {
        if (botStep !is BotStep.Idle) return true

        val text = input.trim().lowercase()

        val transactionKeywords = listOf(
            "pemasukan", "pengeluaran", "setor", "tarik", "beli", "bayar",
            "makan", "minum", "jajan", "belanja", "ongkos", "biaya", "catat",
            "keluarin", "habis", "transfer", "dapat", "gaji", "bonus",
            "terima", "masuk", "keluar", "income", "expense", "transaksi"
        )

        val aiOnlyKeywords = listOf(
            "saldo", "berapa", "total", "rangkuman", "ringkasan", "analisis",
            "laporan", "grafik", "statistik", "summary", "halo", "hai",
            "apa", "siapa", "bagaimana", "gimana", "tolong jelaskan",
            "kenapa", "mengapa", "kapan", "tips", "saran"
        )

        val hasTransactionKw = transactionKeywords.any { text.contains(it) }
        val hasAiOnlyKw      = aiOnlyKeywords.any { text.contains(it) }

        // Ada keyword transaksi tanpa keyword AI-only → bot
        if (hasTransactionKw && !hasAiOnlyKw) return true

        // Punya nominal + tidak ada keyword AI-only → kemungkinan transaksi
        // FIX Bug 6: cek nominal dari teks langsung, bukan manipulasi regex salah
        val hasAmount = botHandler.parseAmount(text) != null

        if (hasAmount && !hasAiOnlyKw) return true

        return false
    }

    // ── Clear chat ────────────────────────────────────────────────────────────
    fun clearChat() {
        generationJob?.cancel()
        retryJob?.cancel()
        chatHistory.clear()
        botStep                = BotStep.Idle
        currentLoadingId       = null
        lastUserMessage        = ""
        lastHistorySnapshot    = emptyList()
        hasShownOfflineMessage = false
        aiRetryCount           = 0
        _uiState.value = _uiState.value.copy(
            messages           = emptyList(),
            isBotMode          = false,
            isTyping           = false,
            connectionStatus   = ConnectionStatus.CONNECTED,
            retryCountdown     = 0,
            pendingTransaction = null
        )
    }

    // ── Prepare pending transaction ───────────────────────────────────────────
    private fun preparePendingTransaction(confirm: ChatOption.TransactionConfirm) {
        if (confirm.amount <= 0 || confirm.wallet.isBlank()) return
        val state    = _uiState.value
        val allCats  = state.expenseCategories + state.incomeCategories
        val category = allCats.find { it.name.equals(confirm.category, ignoreCase = true) }
            ?: allCats.find { it.name.contains(confirm.category, ignoreCase = true) }
            ?: allCats.find { confirm.category.contains(it.name, ignoreCase = true) }
        val wallet   = state.wallets.find { it.name.equals(confirm.wallet, ignoreCase = true) }
            ?: state.wallets.find { it.name.contains(confirm.wallet, ignoreCase = true) }
            ?: state.wallets.find { confirm.wallet.contains(it.name, ignoreCase = true) }
        if (category != null && wallet != null) {
            _uiState.value = _uiState.value.copy(
                pendingTransaction = PendingTransaction(
                    type         = confirm.type,
                    amount       = confirm.amount,
                    categoryName = category.name,
                    walletName   = wallet.name,
                    categoryId   = category.id,
                    walletId     = wallet.id,
                    desc         = confirm.title
                )
            )
        } else {
            val missing = buildString {
                if (category == null) append("kategori '${confirm.category}' tidak dikenali")
                if (category == null && wallet == null) append(", ")
                if (wallet == null) append("dompet '${confirm.wallet}' tidak dikenali")
            }
            addMessage(UiMessage(role = "model", text = "*mengernyit* $missing.", isError = true))
        }
    }

    private fun addMessage(msg: UiMessage) {
        _uiState.value = _uiState.value.copy(messages = _uiState.value.messages + msg)
    }

    private fun removeMessage(id: String) {
        _uiState.value = _uiState.value.copy(messages = _uiState.value.messages.filter { it.id != id })
    }

    override fun onCleared() {
        super.onCleared()
        generationJob?.cancel()
        retryJob?.cancel()
    }
}