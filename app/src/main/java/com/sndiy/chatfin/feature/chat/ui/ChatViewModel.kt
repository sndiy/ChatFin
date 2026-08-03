package com.sndiy.chatfin.feature.chat.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sndiy.chatfin.ai.*
import com.sndiy.chatfin.core.data.local.entity.*
import com.sndiy.chatfin.core.data.sync.SyncEventBus
import com.sndiy.chatfin.core.domain.LocalInsightEngine
import com.sndiy.chatfin.core.persona.PersonaPreferences
import com.sndiy.chatfin.core.persona.PersonaPreset
import com.sndiy.chatfin.core.persona.PersonaPresets
import com.sndiy.chatfin.core.persona.PersonaVoice
import com.sndiy.chatfin.core.persona.PersonaVoices
import com.sndiy.chatfin.core.parser.ParseResult
import com.sndiy.chatfin.core.parser.ParsedDraft
import com.sndiy.chatfin.core.parser.TransactionParser
import com.sndiy.chatfin.core.parser.RoomKeywordSource
import com.sndiy.chatfin.core.utils.NetworkMonitor
import com.sndiy.chatfin.feature.chat.data.repository.ChatRepository
import com.sndiy.chatfin.feature.finance.account.data.repository.AccountRepository
import com.sndiy.chatfin.feature.finance.transaction.data.repository.CategoryRepository
import com.sndiy.chatfin.feature.finance.transaction.data.repository.TransactionRepository
import com.sndiy.chatfin.feature.finance.transaction.data.repository.WalletRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
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

// Hasil combine data akun untuk membangun konteks finansial chat.
private data class ChatAccountData(
    val wallets: List<WalletEntity>,
    val expenseCategories: List<CategoryEntity>,
    val incomeCategories: List<CategoryEntity>,
    val totalIncome: Long,
    val totalExpense: Long
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
    private val syncEventBus: SyncEventBus,
    private val keywordSource: RoomKeywordSource,
    private val personaPrefs: PersonaPreferences,
    private val chatRepo: ChatRepository
) : ViewModel() {

    companion object {
        /** Maks pasangan (role, text) yang disimpan di chatHistory in-memory.
         *  Mencegah token API membengkak tanpa batas. */
        private const val MAX_CHAT_HISTORY = 30
    }

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private val _isCheckingNetwork = MutableStateFlow(true)
    val isCheckingNetwork: StateFlow<Boolean> = _isCheckingNetwork.asStateFlow()

    private val chatHistory   = mutableListOf<Pair<String, String>>()
    private var systemPrompt  = ""
    private var activePersona: PersonaPreset = PersonaPresets.MAI
    private var activeVoice: PersonaVoice = PersonaVoices.MAI
    private var activeCustomText: String = ""
    private var lastTotalIncome: Long = 0L
    private var lastTotalExpense: Long = 0L
    private var botStep: BotStep = BotStep.Idle
    private var generationJob: Job? = null
    private var retryJob: Job?      = null
    private var currentLoadingId: String? = null
    private var sessionId: String? = null

    private var lastUserMessage: String = ""
    private var lastHistorySnapshot: List<Pair<String, String>> = emptyList()
    private var hasShownOfflineMessage = false
    private var hasShownNoApiKeyMessage = false
    private var aiRetryCount = 0

    init {
        observeActiveAccountAndSync()
        observeChatHistory()
        observeNetwork()
        observePersona()
        // Kamus kategori (M6) dibaca sekali di sini — cache RoomKeywordSource
        // kosong sampai refresh() ini selesai; kalau user mengirim pesan super
        // cepat sebelum ini selesai, TransactionParser cuma degradasi ke
        // Partial (tetap nanya kategori), bukan salah atau crash.
        viewModelScope.launch { keywordSource.refresh() }
    }

    // Riwayat chat (M13): satu sesi berkelanjutan per akun, dimuat SEKALI
    // (bukan collector Flow hidup) saat akun aktif berubah — supaya tidak
    // rebutan dengan update optimistic in-memory dari addMessage() saat
    // percakapan sedang berjalan. distinctUntilChanged mencegah riwayat
    // ke-reset tiap kali syncEventBus memicu re-emit untuk akun yang sama.
    private fun observeChatHistory() {
        viewModelScope.launch {
            accountRepo.getActiveAccount()
                .filterNotNull()
                .map { it.id }
                .distinctUntilChanged()
                .collect { accountId ->
                    val session = chatRepo.getOrCreateSession(accountId)
                    sessionId = session.id
                    val persisted = chatRepo.getMessagesOnce(session.id).map { entity ->
                        UiMessage(
                            id      = entity.id,
                            role    = entity.role,
                            text    = entity.content,
                            isError = entity.isError
                        )
                    }
                    _uiState.update { it.copy(messages = persisted) }

                    // Rebuild chatHistory dari pesan yang dipersist supaya AI
                    // tidak kehilangan konteks percakapan setelah app restart.
                    chatHistory.clear()
                    persisted
                        .filter { !it.isError && it.text.isNotBlank() }
                        .takeLast(MAX_CHAT_HISTORY)
                        .forEach { msg -> chatHistory.add(msg.role to msg.text) }
                }
        }
    }

    // Flow DataStore langsung (M10) — ganti persona di Setelan langsung
    // berefek ke prompt berikutnya tanpa perlu keluar-masuk layar Chat.
    // Digabung dengan customPersonaText (M11) karena PersonaId.CUSTOM butuh
    // dua-duanya sekaligus untuk resolve promptFragment yang benar.
    private fun observePersona() {
        viewModelScope.launch {
            combine(
                personaPrefs.activePersonaId,
                personaPrefs.customPersonaText
            ) { id, customText -> id to customText }
                .collect { (id, customText) ->
                    activePersona = PersonaPresets.byId(id)
                    activeVoice = PersonaVoices.byId(id)
                    activeCustomText = customText
                    rebuildSystemPrompt(lastTotalIncome, lastTotalExpense)
                }
        }
    }

    // ── Monitor jaringan ──────────────────────────────────────────────────────
    private fun observeNetwork() {
        viewModelScope.launch {
            _isCheckingNetwork.value = true

            // Retry 3x karena network kadang belum siap saat init
            var connected = false
            repeat(3) { attempt ->
                connected = networkMonitor.isCurrentlyConnected()
                if (connected) return@repeat
                if (attempt < 2) delay(500)
            }

            if (!connected) switchToOfflineMode()
            _isCheckingNetwork.value = false

            // Realtime monitor
            networkMonitor.isConnected.collect { isConnected ->
                val currentStatus = _uiState.value.connectionStatus
                if (!isConnected && currentStatus != ConnectionStatus.NO_INTERNET) {
                    generationJob?.cancel()
                    currentLoadingId?.let { removeMessage(it) }
                    currentLoadingId = null
                    _uiState.update { it.copy(isTyping = false) }
                    if (chatHistory.lastOrNull()?.first == "user") {
                        chatHistory.removeLastOrNull()
                    }
                    switchToOfflineMode()
                } else if (isConnected && currentStatus == ConnectionStatus.NO_INTERNET) {
                    hasShownOfflineMessage = false
                    // Auto clear offline mode saat koneksi pulih
                    _uiState.update {
                        it.copy(
                            connectionStatus = ConnectionStatus.CONNECTED,
                            isBotMode        = false
                        )
                    }
                }
            }
        }
    }

    private fun switchToOfflineMode() {
        _uiState.update {
            it.copy(
                connectionStatus = ConnectionStatus.NO_INTERNET,
                isBotMode        = true
            )
        }
        if (botStep !is BotStep.Idle) botStep = BotStep.Idle
        if (!hasShownOfflineMessage) {
            hasShownOfflineMessage = true
            addMessage(UiMessage(role = "model", text = activeVoice.switchOffline))
        }
    }

    // AI belum diaktifkan (belum ada API key) — bukan kegagalan, hanya belum
    // di-setup. Degradasi ke Mode Bot alih-alih bubble error buntu, konsisten
    // dengan prinsip utama app: jalan penuh tanpa API key (CLAUDE.md §3.4).
    private fun switchToNoApiKeyBotMode() {
        _uiState.update {
            it.copy(connectionStatus = ConnectionStatus.BOT_MODE, isBotMode = true)
        }
        if (botStep !is BotStep.Idle) botStep = BotStep.Idle
        if (!hasShownNoApiKeyMessage) {
            hasShownNoApiKeyMessage = true
            addMessage(UiMessage(role = "model", text = activeVoice.switchNoApiKey))
        }
    }

    // ── Observe akun aktif + sync event ──────────────────────────────────────
    // Digabung jadi satu aliran reaktif (bukan dua collector terpisah yang
    // masing-masing memanggil loadAccountData()) supaya tidak ada collector
    // yang menumpuk setiap kali akun berganti ATAU setiap kali sync selesai.
    // syncEventBus.syncCompleted.onStart{emit(Unit)} memicu subscription
    // pertama ke accountRepo.getActiveAccount() saat ViewModel dibuat; setiap
    // sync berikutnya me-restart subscription itu (flatMapLatest otomatis
    // membatalkan yang lama) — jadi selalu 1 collector aktif, bukan N.
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observeActiveAccountAndSync() {
        viewModelScope.launch {
            syncEventBus.syncCompleted
                .onStart { emit(Unit) }
                .flatMapLatest { accountRepo.getActiveAccount() }
                .flatMapLatest { account ->
                    _uiState.update { it.copy(activeAccount = account) }
                    if (account == null) {
                        emptyFlow()
                    } else {
                        val now   = LocalDate.now()
                        val start = now.withDayOfMonth(1)
                        combine(
                            walletRepo.getWalletsByAccount(account.id),
                            categoryRepo.getCategoriesByAccountAndType(account.id, "EXPENSE"),
                            categoryRepo.getCategoriesByAccountAndType(account.id, "INCOME"),
                            transactionRepo.getTotalIncome(account.id, start, now),
                            transactionRepo.getTotalExpense(account.id, start, now)
                        ) { wallets, expCats, incCats, income, expense ->
                            ChatAccountData(wallets, expCats, incCats, income ?: 0L, expense ?: 0L)
                        }
                    }
                }
                .collect { data ->
                    _uiState.update {
                        it.copy(
                            wallets           = data.wallets,
                            expenseCategories = data.expenseCategories,
                            incomeCategories  = data.incomeCategories
                        )
                    }
                    lastTotalIncome  = data.totalIncome
                    lastTotalExpense = data.totalExpense
                    rebuildSystemPrompt(data.totalIncome, data.totalExpense)
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
            financeContext    = ctx,
            userName          = state.activeAccount?.name ?: "Kamu",
            persona           = activePersona,
            customPersonaText = activeCustomText
        )
    }

    fun onInputChange(text: String) {
        _uiState.update { it.copy(inputText = text) }
    }

    // ── Stop generation ───────────────────────────────────────────────────────
    fun stopGeneration() {
        generationJob?.cancel()
        generationJob    = null
        currentLoadingId?.let { removeMessage(it) }
        currentLoadingId = null
        if (chatHistory.lastOrNull()?.first == "user") chatHistory.removeLastOrNull()
        _uiState.update { it.copy(isTyping = false) }
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
        hasShownNoApiKeyMessage = false
        aiRetryCount = 0
        botStep = BotStep.Idle
        _uiState.update {
            it.copy(connectionStatus = ConnectionStatus.CONNECTED, isBotMode = false)
        }
        if (lastUserMessage.isNotBlank()) {
            executeAiRequest(lastUserMessage, lastHistorySnapshot)
        }
    }

    private fun startRetryCountdown(seconds: Int) {
        retryJob?.cancel()
        retryJob = viewModelScope.launch {
            for (i in seconds downTo 1) {
                _uiState.update { it.copy(retryCountdown = i) }
                delay(1000)
            }
            _uiState.update { it.copy(retryCountdown = 0) }
        }
    }

    // ── Kirim pesan ───────────────────────────────────────────────────────────
    fun sendMessage() {
        val text = _uiState.value.inputText.trim()
        if (text.isBlank() || _uiState.value.isTyping) return

        // Clear pending transaction & options dari pesan sebelumnya — mencegah
        // kartu konfirmasi zombie saat user langsung kirim pesan baru.
        if (_uiState.value.pendingTransaction != null) {
            _uiState.update { it.copy(pendingTransaction = null) }
            clearAllOptions()
        }

        addMessage(UiMessage(role = "user", text = text))
        _uiState.update { it.copy(inputText = "") }
        routeMessage(text)
    }

    // ── Quick action (fixes async state race condition) ─────────────────────
    fun quickAction(text: String) {
        if (text.isBlank() || _uiState.value.isTyping) return

        addMessage(UiMessage(role = "user", text = text))
        // Don't touch inputText — we have the text directly
        routeMessage(text)
    }

    // ── Routing pesan ────────────────────────────────────────────────────────
    // 1. Wizard bot aktif → lanjutkan. 2. Offline → bot mode.
    // 3. Bot mode manual → TransactionParser + commands.
    // 4. Online + AI → kirim SEMUA ke AI (AI mengenali transaksi sendiri).
    private fun routeMessage(text: String) {
        if (botStep !is BotStep.Idle) {
            handleBotMode(text)
            return
        }

        if (!networkMonitor.isCurrentlyConnected()) {
            switchToOfflineMode()
            handleBotMode(text)
            return
        }

        if (_uiState.value.isBotMode) {
            // User sedang di bot mode manual (tekan "Pakai Bot") — tetap
            // gunakan TransactionParser + bot commands.
            if (isBotCommand(text)) {
                handleBotMode(text)
                return
            }
            when (val parsed = TransactionParser.parse(text, keywordSource)) {
                is ParseResult.Complete -> handleParsedTransaction(parsed.draft)
                is ParseResult.Partial  -> handleParsedTransaction(parsed.draft)
                ParseResult.NotATransaction -> handleBotMode(text)
            }
            return
        }

        // Online + AI tersedia: kirim SEMUA ke AI.
        sendToAi(text)
    }

    private fun handleParsedTransaction(draft: ParsedDraft) {
        val state  = _uiState.value
        val result = botHandler.handleParsed(
            draft             = draft,
            wallets           = state.wallets,
            expenseCategories = state.expenseCategories,
            incomeCategories  = state.incomeCategories,
            voice             = activeVoice
        )
        botStep = result.nextStep
        if (result.text.isNotBlank() || result.option != null) {
            addMessage(UiMessage(role = "model", text = result.text, option = result.option))
        }
    }

    private fun isBotCommand(text: String): Boolean {
        val cmd = text.trim().lowercase().trimStart('/')
        return cmd == "help" || cmd == "bantuan" ||
            cmd == "setor" || cmd.startsWith("setor ") ||
            cmd == "tarik" || cmd.startsWith("tarik ") ||
            cmd == "saldo" || cmd == "balance" ||
            cmd == "rangkuman" || cmd == "summary"
    }

    // ── Routing ke AI ─────────────────────────────────────────────────────────
    private fun sendToAi(text: String) {
        val historySnapshot = chatHistory.toList()
        lastUserMessage     = text
        lastHistorySnapshot = historySnapshot
        aiRetryCount        = 0
        executeAiRequest(text, historySnapshot)
    }

    // ── Execute AI request ────────────────────────────────────────────────────
    private fun executeAiRequest(text: String, historySnapshot: List<Pair<String, String>>) {
        _uiState.update {
            it.copy(isTyping = true, activeModelName = geminiClient.currentModelName)
        }
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
            _uiState.update { it.copy(isTyping = false) }

            result.fold(
                onSuccess = { parsed ->
                    hasShownOfflineMessage = false
                    hasShownNoApiKeyMessage = false
                    aiRetryCount = 0
                    chatHistory.add("user" to text)
                    chatHistory.add("model" to parsed.text)
                    // Trim agar token API tidak membengkak tanpa batas
                    while (chatHistory.size > MAX_CHAT_HISTORY) chatHistory.removeFirst()
                    _uiState.update {
                        it.copy(
                            connectionStatus = ConnectionStatus.CONNECTED,
                            activeModelName  = geminiClient.currentModelName
                        )
                    }
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
                    val isOneLimit = error is QuotaExhaustedException && !isAllLimit

                    when {
                        error is ApiKeyMissingException -> switchToNoApiKeyBotMode()
                        isNoInternet -> switchToOfflineMode()
                        isOneLimit && aiRetryCount < 1 -> {
                            aiRetryCount++
                            _uiState.update {
                                it.copy(
                                    activeModelName = geminiClient.currentModelName,
                                    isTyping        = false
                                )
                            }
                            executeAiRequest(text, historySnapshot)
                        }
                        isOneLimit || isAllLimit -> {
                            _uiState.update { it.copy(connectionStatus = ConnectionStatus.QUOTA_LIMIT) }
                            addMessage(UiMessage(
                                role    = "model",
                                text    = "*menghela napas* Semua model sedang sibuk. Coba lagi nanti.",
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
        botStep = BotStep.Idle
        _uiState.update {
            it.copy(isBotMode = true, connectionStatus = ConnectionStatus.BOT_MODE)
        }
        addMessage(UiMessage(role = "model", text = activeVoice.switchBotMode))
    }

    // ── Bot mode ──────────────────────────────────────────────────────────────
    private fun handleBotMode(input: String) {
        val state        = _uiState.value
        val totalBalance = state.wallets.sumOf { it.balance }
        val result       = botHandler.handle(
            input             = input,
            currentStep       = botStep,
            wallets           = state.wallets,
            expenseCategories = state.expenseCategories,
            incomeCategories  = state.incomeCategories,
            totalBalance      = totalBalance,
            voice             = activeVoice
        )
        botStep = result.nextStep

        if (result.text == "__RANGKUMAN__") { handleRangkuman(); return }

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
            userName          = _uiState.value.activeAccount?.name ?: "Kamu",
            type              = req.type,
            amount            = req.amount,
            category          = req.category,
            wallet            = req.wallet,
            desc              = req.desc,
            persona           = activePersona,
            customPersonaText = activeCustomText
        )

        _uiState.update { it.copy(isTyping = true) }
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
            _uiState.update { it.copy(isTyping = false) }

            result.fold(
                onSuccess = { parsed ->
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
                        val monthLabel = "${now.month.getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale("id", "ID"))} ${now.year}"
                        val text = LocalInsightEngine.monthlySummary(
                            monthYearLabel = monthLabel,
                            income         = income,
                            expense        = expense,
                            walletBalances = state.wallets.map { it.name to it.balance }
                        )
                        addMessage(UiMessage(role = "model", text = text))
                    }
            } catch (e: Exception) {
                addMessage(UiMessage(role = "model", text = "Gagal memuat rangkuman: ${e.message}", isError = true))
            }
        }
    }

    // ── Option selected ───────────────────────────────────────────────────────
    fun onOptionSelected(option: ChatOption, selectedValue: String) {
        _uiState.update { state ->
            state.copy(
                messages = state.messages.map { msg ->
                    if (msg.option == option) msg.copy(option = null) else msg
                }
            )
        }
        addMessage(UiMessage(role = "user", text = selectedValue))

        // Bot wizard sedang berjalan (WaitCategory/WaitWallet/dll) → lanjutkan
        // step wizard, apa pun jenis option-nya.
        if (botStep !is BotStep.Idle) {
            handleBotMode(selectedValue)
            return
        }

        // Tidak ada wizard aktif → routing normal:
        // - Online + AI mode: kirim pilihan ke AI (AI lanjut ke step berikutnya)
        // - Bot mode / offline: TransactionParser + bot commands
        routeMessage(selectedValue)
    }

    // ── Konfirmasi transaksi ──────────────────────────────────────────────────
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
                _uiState.update { it.copy(pendingTransaction = null) }
                clearAllOptions()
                val fmt     = java.text.NumberFormat.getNumberInstance(java.util.Locale("id", "ID"))
                val now     = LocalTime.now()
                val timeStr = "%02d:%02d".format(now.hour, now.minute)
                val sign    = if (pending.type == "INCOME") "+" else "-"
                val title   = if (pending.desc.isNotBlank()) " · ${pending.desc}" else ""
                addMessage(UiMessage(
                    role = "model",
                    text = "Tersimpan.$title ${sign}Rp ${fmt.format(pending.amount)} · ${pending.categoryName} · ${pending.walletName} · $timeStr"
                ))
                chatHistory.add("model" to "Transaksi tersimpan.")
            } catch (e: Exception) {
                addMessage(UiMessage(role = "model", text = "Gagal menyimpan: ${e.message}", isError = true))
            }
        }
    }

    fun cancelTransaction() {
        _uiState.update { it.copy(pendingTransaction = null) }
        clearAllOptions()
        addMessage(UiMessage(role = "user", text = "Batal"))
        chatHistory.add("user" to "Batal")
        botStep = BotStep.Idle
    }

    private fun clearAllOptions() {
        _uiState.update { state ->
            state.copy(
                messages = state.messages.map { msg ->
                    if (msg.option != null) msg.copy(option = null) else msg
                }
            )
        }
    }

    // ── Clear chat ────────────────────────────────────────────────────────────
    fun clearChat() {
        generationJob?.cancel()
        retryJob?.cancel()
        chatHistory.clear()
        sessionId?.let { sid -> viewModelScope.launch { chatRepo.clearSession(sid) } }
        botStep                = BotStep.Idle
        currentLoadingId       = null
        lastUserMessage        = ""
        lastHistorySnapshot    = emptyList()
        hasShownOfflineMessage = false
        hasShownNoApiKeyMessage = false
        aiRetryCount           = 0
        _uiState.update {
            it.copy(
                messages           = emptyList(),
                isBotMode          = false,
                isTyping           = false,
                connectionStatus   = ConnectionStatus.CONNECTED,
                retryCountdown     = 0,
                pendingTransaction = null
            )
        }
    }

    // ── Prepare pending transaction ───────────────────────────────────────────
    private fun preparePendingTransaction(confirm: ChatOption.TransactionConfirm) {
        if (confirm.amount <= 0 || confirm.wallet.isBlank()) return
        val state   = _uiState.value
        val allCats = state.expenseCategories + state.incomeCategories
        val category = allCats.find { it.name.equals(confirm.category, ignoreCase = true) }
            ?: allCats.find { it.name.contains(confirm.category, ignoreCase = true) }
            ?: allCats.find { confirm.category.contains(it.name, ignoreCase = true) }
        val wallet = state.wallets.find { it.name.equals(confirm.wallet, ignoreCase = true) }
            ?: state.wallets.find { it.name.contains(confirm.wallet, ignoreCase = true) }
            ?: state.wallets.find { confirm.wallet.contains(it.name, ignoreCase = true) }
        if (category != null && wallet != null) {
            _uiState.update {
                it.copy(
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
            }
        } else {
            val missing = buildString {
                if (category == null) append("kategori '${confirm.category}' tidak dikenali")
                if (category == null && wallet == null) append(", ")
                if (wallet == null) append("dompet '${confirm.wallet}' tidak dikenali")
            }
            addMessage(UiMessage(role = "model", text = "*mengangkat alis* $missing. Coba cek lagi di Setelan.", isError = true))
        }
    }

    private fun addMessage(msg: UiMessage) {
        _uiState.update { it.copy(messages = it.messages + msg) }
        // Bubble loading (typing indicator) & bubble kosong (opsi tanpa teks,
        // isinya tidak bisa dipulihkan lagi karena ChatOption tidak tersimpan)
        // sengaja tidak ditulis ke Room.
        if (!msg.isLoading && msg.text.isNotBlank()) {
            sessionId?.let { sid ->
                viewModelScope.launch { chatRepo.saveMessage(sid, msg.role, msg.text, msg.isError) }
            }
        }
    }

    private fun removeMessage(id: String) {
        _uiState.update { it.copy(messages = it.messages.filter { m -> m.id != id }) }
    }

    override fun onCleared() {
        super.onCleared()
        generationJob?.cancel()
        retryJob?.cancel()
    }
}