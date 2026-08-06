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
import kotlinx.coroutines.CompletableDeferred
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
    val transactions: List<TransactionEntity>   = emptyList(),
    val pendingTransaction: PendingTransaction? = null,
    val replyingToMessage: UiMessage?           = null
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

// AiDraft & ChatSlotResolver ada di paket ai/ (ChatSlotResolver.kt) supaya
// keputusan slot bisa diuji tanpa Hilt/Room/Android.

// Hasil combine data akun untuk membangun konteks finansial chat.
private data class ChatAccountData(
    val wallets: List<WalletEntity>,
    val expenseCategories: List<CategoryEntity>,
    val incomeCategories: List<CategoryEntity>,
    val transactions: List<TransactionEntity>,
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

        /** Di bawah ini hampir pasti bukan nominal rupiah — lihat [captureSlotsFromUserMessage]. */
        private const val MIN_CAPTURED_AMOUNT = 1_000L
    }

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private val _isCheckingNetwork = MutableStateFlow(true)
    val isCheckingNetwork: StateFlow<Boolean> = _isCheckingNetwork.asStateFlow()

    private val chatHistory   = mutableListOf<Pair<String, String>>()
    private var systemPrompt  = ""
    private var lastFinanceContext = ""
    private var activePersona: PersonaPreset = PersonaPresets.MAI
    private var activeVoice: PersonaVoice = PersonaVoices.MAI
    private var activeCustomText: String = ""
    private var lastTotalIncome: Long = 0L
    private var lastTotalExpense: Long = 0L
    private var botStep: BotStep = BotStep.Idle
    private var aiDraft: AiDraft = AiDraft()
    private var generationJob: Job? = null
    private var retryJob: Job?      = null
    private var currentLoadingId: String? = null
    private var sessionId: String? = null
    /** Kartu konfirmasi hasil restore yang masih menunggu data akun. */
    private var restoredConfirm: ChatOption.TransactionConfirm? = null

    private var lastUserMessage: String = ""
    private var lastHistorySnapshot: List<Pair<String, String>> = emptyList()
    private var hasShownOfflineMessage = false
    private var hasShownNoApiKeyMessage = false
    private var aiRetryCount = 0

    /**
     * Sebelum refresh selesai, RoomKeywordSource.findCategory SELALU
     * mengembalikan null — pesan pertama jadi berperilaku beda dari pesan
     * berikutnya (kategori tidak pernah ketemu). Dulu refresh-nya
     * fire-and-forget di init; sekarang penanda ini ditunggu dulu oleh setiap
     * pemanggil parser, dan baru selesai setelah kamus terisi sesuai akun aktif.
     */
    private val keywordReady = CompletableDeferred<Unit>()

    init {
        observeActiveAccountAndSync()
        observeChatHistory()
        observeNetwork()
        observePersona()
    }

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
                            option  = ChatOption.decode(entity.optionJson),
                            isError = entity.isError
                        )
                    }
                    _uiState.update { it.copy(messages = persisted) }

                    // Kartu konfirmasi yang ikut pulih harus tetap bisa ditekan:
                    // pendingTransaction TIDAK persist, jadi tanpa ini tombol
                    // Simpan mati tanpa penjelasan. Disiapkan ulang setelah data
                    // dompet/kategori akun tersedia (lihat observeActiveAccountAndSync).
                    restoredConfirm = persisted.lastOrNull()?.option as? ChatOption.TransactionConfirm
                    tryPrepareRestoredConfirm()

                    chatHistory.clear()
                    persisted
                        .filter { !it.isError && it.text.isNotBlank() }
                        .takeLast(MAX_CHAT_HISTORY)
                        .forEach { msg -> chatHistory.add(msg.role to msg.text) }
                }
        }
    }

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

    private fun observeNetwork() {
        viewModelScope.launch {
            _isCheckingNetwork.value = true

            var connected = false
            repeat(3) { attempt ->
                connected = networkMonitor.isCurrentlyConnected()
                if (connected) return@repeat
                if (attempt < 2) delay(500)
            }

            if (!connected) switchToOfflineMode()
            _isCheckingNetwork.value = false

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
        // botStep SENGAJA dipertahankan. Wizard bot justru jalur yang bekerja
        // tanpa internet, jadi mereset step di sini membuang transaksi yang
        // setengah terisi persis pada saat alurnya masih bisa dilanjutkan.
        if (!hasShownOfflineMessage) {
            hasShownOfflineMessage = true
            addMessage(UiMessage(role = "model", text = activeVoice.switchOffline))
        }
    }

    private fun switchToNoApiKeyBotMode() {
        _uiState.update {
            it.copy(connectionStatus = ConnectionStatus.BOT_MODE, isBotMode = true)
        }
        // Sama seperti switchToOfflineMode: wizard yang sedang berjalan tetap
        // bisa diselesaikan tanpa API key, jadi step-nya tidak dibuang.
        if (!hasShownNoApiKeyMessage) {
            hasShownNoApiKeyMessage = true
            addMessage(UiMessage(role = "model", text = activeVoice.switchNoApiKey))
        }
    }

private data class Tuple4<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observeActiveAccountAndSync() {
        viewModelScope.launch {
            syncEventBus.syncCompleted
                .onStart { emit(Unit) }
                .flatMapLatest { accountRepo.getActiveAccount() }
                .flatMapLatest { account ->
                    _uiState.update { it.copy(activeAccount = account) }
                    if (account == null) {
                        // Tidak ada akun = tidak akan pernah ada kamus. Penanda
                        // tetap dilepas supaya pengiriman pesan tidak menggantung.
                        keywordReady.complete(Unit)
                        emptyFlow()
                    } else {
                        val now   = LocalDate.now()
                        val start = now.withDayOfMonth(1)
                        val baseFlow = combine(
                            walletRepo.getWalletsByAccount(account.id),
                            categoryRepo.getCategoriesByAccountAndType(account.id, "EXPENSE"),
                            categoryRepo.getCategoriesByAccountAndType(account.id, "INCOME"),
                            transactionRepo.getTransactionsByAccount(account.id)
                        ) { wallets, expCats, incCats, txs ->
                            Tuple4(wallets, expCats, incCats, txs)
                        }

                        combine(
                            baseFlow,
                            transactionRepo.getTotalIncome(account.id, start, now),
                            transactionRepo.getTotalExpense(account.id, start, now)
                        ) { base, income, expense ->
                            ChatAccountData(base.a, base.b, base.c, base.d, income ?: 0L, expense ?: 0L)
                        }
                    }
                }
                .collect { data ->
                    _uiState.update {
                        it.copy(
                            wallets           = data.wallets,
                            expenseCategories = data.expenseCategories,
                            incomeCategories  = data.incomeCategories,
                            transactions      = data.transactions
                        )
                    }
                    lastTotalIncome  = data.totalIncome
                    lastTotalExpense = data.totalExpense
                    rebuildSystemPrompt(data.totalIncome, data.totalExpense)
                    // Kamus kata kunci dibatasi ke akun aktif (+ kategori global)
                    // dan ikut diperbarui saat akun berganti atau kategori berubah.
                    keywordSource.refresh(_uiState.value.activeAccount?.id)
                    keywordReady.complete(Unit)

                    tryPrepareRestoredConfirm()
                }
        }
    }

    private fun rebuildSystemPrompt(totalIncome: Long = 0L, totalExpense: Long = 0L) {
        val state = _uiState.value
        lastFinanceContext = contextBuilder.buildContext(
            account           = state.activeAccount,
            wallets           = state.wallets,
            expenseCategories = state.expenseCategories,
            incomeCategories  = state.incomeCategories,
            totalIncome       = totalIncome,
            totalExpense      = totalExpense
        )
        systemPrompt = buildPrompt()
    }

    /**
     * Prompt dirakit ulang tiap permintaan karena slot yang sudah diketahui
     * berubah tiap giliran, dan penegasannya harus ikut masuk ke dalam blok
     * alur — bukan ditempel di ujung prompt.
     */
    private fun buildPrompt(): String = systemPromptBuilder.build(
        financeContext    = lastFinanceContext,
        userName          = _uiState.value.activeAccount?.name ?: "Kamu",
        persona           = activePersona,
        customPersonaText = activeCustomText,
        knownSlots        = slotReminder()
    )

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
        if (botStep !is BotStep.Idle) {
            // Kembali ke AI berarti mengulang pesan terakhir dari awal, jadi
            // wizard memang harus dihentikan — tapi user diberi tahu, tidak
            // dibuang diam-diam seperti sebelumnya.
            botStep = BotStep.Idle
            addMessage(UiMessage(
                role = "model",
                text = "Balik ke mode AI — pencatatan yang tadi belum selesai jadi kubatalkan dulu ya."
            ))
        }
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

    // ── Manajemen Aksi Pesan (Edit, Hapus, Balas) ─────────────────────────────
    fun setReplyingMessage(message: UiMessage?) {
        _uiState.update { it.copy(replyingToMessage = message) }
    }

    fun clearReplyingMessage() {
        _uiState.update { it.copy(replyingToMessage = null) }
    }

    fun deleteMessage(messageId: String) {
        _uiState.update { state ->
            state.copy(
                messages = state.messages.filter { it.id != messageId },
                replyingToMessage = if (state.replyingToMessage?.id == messageId) null else state.replyingToMessage
            )
        }
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            chatRepo.deleteMessage(messageId)
        }
    }

    fun editMessage(messageId: String, newText: String) {
        val trimmed = newText.trim()
        if (trimmed.isBlank() || _uiState.value.isTyping) return

        val currentMessages = _uiState.value.messages
        val index = currentMessages.indexOfFirst { it.id == messageId }
        if (index == -1) return

        // Hentikan pengetikan AI jika sedang berjalan
        stopGeneration()

        // Ambil pesan-pesan setelah pesan yang diedit untuk dihapus dari UI & DB
        val messagesToDelete = currentMessages.drop(index + 1)
        val updatedUserMsg = currentMessages[index].copy(text = trimmed)
        val truncatedMessages = currentMessages.take(index) + updatedUserMsg

        _uiState.update { state ->
            state.copy(
                messages = truncatedMessages,
                pendingTransaction = null,
                replyingToMessage = null
            )
        }

        // Percakapan dipotong sampai titik edit — slot yang terkumpul dari
        // pesan-pesan setelahnya ikut tidak berlaku lagi.
        resetAiDraft()

        // Rebuild chatHistory sampai titik sebelum pesan yang diedit
        chatHistory.clear()
        truncatedMessages
            .dropLast(1)
            .filter { !it.isError && it.text.isNotBlank() }
            .takeLast(MAX_CHAT_HISTORY)
            .forEach { msg -> chatHistory.add(msg.role to msg.text) }

        // Update DB: update pesan user & hapus pesan-pesan setelahnya
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            chatRepo.updateMessage(messageId, trimmed)
            messagesToDelete.forEach { msg ->
                chatRepo.deleteMessage(msg.id)
            }
        }

        // Kirim ulang (resend) pesan yang sudah diedit ke AI / Bot Mode
        routeMessage(trimmed)
    }

    // ── Kirim pesan ───────────────────────────────────────────────────────────
    fun sendMessage() {
        val text = _uiState.value.inputText.trim()
        if (text.isBlank() || _uiState.value.isTyping) return

        val replyMsg = _uiState.value.replyingToMessage
        val formattedText = if (replyMsg != null) {
            val replyAuthor = if (replyMsg.role == "user") "Kamu" else "Mai"
            val quoted = replyMsg.text.take(60).replace("\n", " ")
            " > *[Balas $replyAuthor: \"$quoted\"]*\n$text"
        } else {
            text
        }

        // Clear pending transaction & options dari pesan sebelumnya — mencegah
        // kartu konfirmasi zombie saat user langsung kirim pesan baru.
        if (_uiState.value.pendingTransaction != null) {
            _uiState.update { it.copy(pendingTransaction = null) }
            clearAllOptions()
        }

        addMessage(UiMessage(role = "user", text = formattedText))
        _uiState.update { it.copy(inputText = "", replyingToMessage = null) }
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

        // Offline dan "Pakai Bot" ditangani dengan cara yang SAMA. Sebelumnya
        // cabang offline langsung lompat ke handleBotMode, sehingga
        // TransactionParser tidak pernah dipakai justru saat sedang tidak ada
        // internet — padahal itu satu-satunya situasi parser ini dirancang
        // untuk menggantikan AI. Akibatnya kalimat wajar seperti "jajan 15rb"
        // dijawab "Perintah tidak dikenal", dan user offline hanya bisa
        // mencatat lewat perintah setor/tarik.
        val offline = !networkMonitor.isCurrentlyConnected()
        if (offline) switchToOfflineMode()

        if (offline || _uiState.value.isBotMode) {
            if (isBotCommand(text)) {
                handleBotMode(text)
                return
            }
            viewModelScope.launch {
                keywordReady.await()
                when (val parsed = TransactionParser.parse(text, keywordSource)) {
                    is ParseResult.Complete -> handleParsedTransaction(parsed.draft)
                    is ParseResult.Partial  -> handleParsedTransaction(parsed.draft)
                    ParseResult.NotATransaction -> handleBotMode(text)
                }
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
        viewModelScope.launch {
            keywordReady.await()
            val wasReady = aiDraft.isReadyToConfirm
            captureSlotsFromUserMessage(text)
            // Pesan inilah yang melengkapi slot terakhir → giliran ini memang
            // sudah waktunya konfirmasi. Dipakai sebagai jaring pengaman kalau
            // model malah bertanya lagi (lihat executeAiRequest).
            val justCompleted = !wasReady && aiDraft.isReadyToConfirm
            executeAiRequest(text, historySnapshot, justCompleted)
        }
    }

    // ── Slot transaksi jalur AI (lihat dok AiDraft) ───────────────────────────

    private fun mergeAiDraft(
        type: String? = null,
        amount: Long? = null,
        categoryName: String? = null,
        walletName: String? = null,
        title: String? = null
    ) {
        aiDraft = aiDraft.merge(type, amount, categoryName, walletName, title)
    }

    /**
     * Parser lokal tetap dijalankan di jalur AI — bukan untuk mengambil alih
     * percakapan, tapi supaya nominal & kategori punya sumber kebenaran di sisi
     * aplikasi yang tidak bergantung pada ingatan model.
     *
     * Ambang Rp 1.000 menyaring angka yang jelas bukan nominal ("2 bulan
     * terakhir", "3 hari lalu") supaya tidak mencemari draft saat user sebenarnya
     * cuma bertanya.
     */
    private fun captureSlotsFromUserMessage(text: String) {
        val draft = when (val parsed = TransactionParser.parse(text, keywordSource)) {
            is ParseResult.Complete      -> parsed.draft
            is ParseResult.Partial       -> parsed.draft
            ParseResult.NotATransaction  -> null
        }
        if (draft != null) {
            mergeAiDraft(
                type         = draft.type,
                amount       = draft.amount?.takeIf { it >= MIN_CAPTURED_AMOUNT },
                categoryName = draft.categoryName,
                walletName   = draft.walletHint?.let { hint -> matchWalletName(hint) },
                title        = draft.title
            )
        }

        // Pesan yang isinya persis nama kategori/dompet — bentuk paling umum
        // adalah user menekan chip pilihan.
        val trimmed = text.trim()
        state().allCategories.find { it.name.equals(trimmed, ignoreCase = true) }
            ?.let { mergeAiDraft(categoryName = it.name) }
        state().wallets.find { it.name.equals(trimmed, ignoreCase = true) }
            ?.let { mergeAiDraft(walletName = it.name) }
    }

    /** Ringkasan slot terisi yang disisipkan ke system prompt tiap giliran. */
    private fun slotReminder(): String {
        if (aiDraft.isEmpty) return ""
        val fmt   = java.text.NumberFormat.getNumberInstance(java.util.Locale("id", "ID"))
        val lines = buildList {
            aiDraft.type?.let { add("- Tipe: ${if (it == "INCOME") "PEMASUKAN" else "PENGELUARAN"}") }
            aiDraft.amount?.let { add("- Nominal: ${fmt.format(it)}") }
            aiDraft.categoryName?.let { add("- Kategori: $it") }
            aiDraft.walletName?.let { add("- Dompet: $it") }
            aiDraft.title?.let { add("- Judul: $it") }
        }
        val skipped = buildList {
            if (aiDraft.categoryName != null) add("1")
            if (aiDraft.walletName != null) add("2")
            if ((aiDraft.amount ?: 0L) > 0L) add("3")
        }
        val skipLine = if (skipped.isEmpty()) "" else
            "\n⛔ LANGKAH ${skipped.joinToString(", ")} SUDAH TERJAWAB — DILARANG menanyakannya lagi " +
            "dengan kalimat apa pun (termasuk \"Berapa?\")."
        return """

            ⚠️ SLOT YANG SUDAH DIKETAHUI DI PERCAKAPAN INI (menimpa alur bernomor di atas):
            ${lines.joinToString("\n            ")}$skipLine
            Pakai nilai di atas apa adanya, lanjut ke langkah pertama yang BELUM terjawab.
            Kalau semuanya sudah terisi, langsung Langkah 4 (konfirmasi).
        """.trimIndent()
    }

    private fun resetAiDraft() { aiDraft = AiDraft() }

    /**
     * Pemulihan riwayat chat dan pemuatan data akun berjalan di dua coroutine
     * terpisah, urutannya tidak dijamin — makanya dipanggil dari keduanya dan
     * baru bekerja saat dua-duanya siap.
     */
    private fun tryPrepareRestoredConfirm() {
        val confirm = restoredConfirm ?: return
        if (state().wallets.isEmpty() || state().allCategories.isEmpty()) return
        restoredConfirm = null
        if (state().pendingTransaction == null) preparePendingTransaction(confirm)
    }

    /** Ringkasan konfirmasi yang dirakit aplikasi sendiri, tanpa melibatkan model. */
    private fun confirmSummaryText(c: ChatOption.TransactionConfirm): String {
        val fmt       = java.text.NumberFormat.getNumberInstance(java.util.Locale("id", "ID"))
        val typeLabel = if (c.type == "INCOME") "Pemasukan" else "Pengeluaran"
        val descLine  = if (c.title.isNotBlank()) "\n📋 Judul    : ${c.title}" else ""
        return "📋 *Konfirmasi $typeLabel*\n\n" +
            "💰 Nominal  : Rp ${fmt.format(c.amount)}\n" +
            "🏷️ Kategori : ${c.category}\n" +
            "👛 Dompet   : ${c.wallet}$descLine\n\n" +
            "Sudah benar?"
    }

    /**
     * Chip cukup divalidasi "ada di database atau tidak", memakai kedua daftar
     * kategori. Menyaringnya per tipe di sini justru berbahaya: tipe di aiDraft
     * masih bisa tebakan parser (default EXPENSE kalau tidak ada kata kerja),
     * jadi daftar kategori INCOME yang sebenarnya benar bisa ikut terbuang.
     * Kecocokan tipe ditegakkan di [preparePendingTransaction], saat menyimpan.
     */
    private fun sanitizeOption(option: ChatOption?): ChatOption? = ChatSlotResolver.sanitize(
        option         = option,
        realWallets    = state().wallets.map { it.name },
        realCategories = state().allCategories.map { it.name }
    )

    private fun matchWalletName(input: String): String? {
        if (input.isBlank()) return null
        val wallets = state().wallets
        return (wallets.find { it.name.equals(input, ignoreCase = true) }
            ?: wallets.find { it.name.contains(input, ignoreCase = true) })?.name
    }

    /** INCOME/EXPENSE memakai daftar kategorinya sendiri; tipe tak dikenal pakai keduanya. */
    private fun categoryPoolFor(type: String?): List<CategoryEntity> = when (type) {
        "INCOME"  -> state().incomeCategories
        "EXPENSE" -> state().expenseCategories
        else      -> state().allCategories
    }

    private fun state() = _uiState.value

    private val ChatUiState.allCategories: List<CategoryEntity>
        get() = expenseCategories + incomeCategories

    // ── Execute AI request ────────────────────────────────────────────────────
    private fun executeAiRequest(
        text: String,
        historySnapshot: List<Pair<String, String>>,
        justCompletedDraft: Boolean = false
    ) {
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
                // Dirakit ulang tiap giliran supaya slot yang sudah terkumpul
                // ikut masuk: blok [CHATFIN_OPTIONS] dibuang dari chatHistory,
                // jadi ini satu-satunya cara model tetap tahu nominal yang
                // sudah disebut user di awal percakapan.
                systemPrompt = buildPrompt()
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
                    var option = when (val sanitized = sanitizeOption(parsed.option)) {
                        is ChatOption.TransactionConfirm -> patchWithDraft(sanitized)
                        else -> sanitized
                    }
                    // Jaring pengaman: pesan barusan melengkapi slot terakhir,
                    // tapi model tetap tidak mengeluarkan kartu konfirmasi —
                    // biasanya karena ia mengikuti skrip langkah bernomor dan
                    // menanyakan sesuatu yang sudah dijawab. Aplikasi yang
                    // memegang datanya, jadi kartunya dibuat di sini. User tetap
                    // harus menekan Simpan, tidak ada yang tersimpan diam-diam.
                    var bodyText = parsed.text
                    if (justCompletedDraft && option !is ChatOption.TransactionConfirm) {
                        option   = aiDraft.toConfirm()
                        bodyText = confirmSummaryText(option)
                    }
                    addMessage(UiMessage(role = "model", text = bodyText, option = option))
                    if (option is ChatOption.TransactionConfirm) {
                        preparePendingTransaction(option)
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
            val fallback = req.toConfirm()
            addMessage(UiMessage(role = "model", text = confirmSummaryText(fallback), option = fallback))
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
                        preparePendingTransaction(req.toConfirm())
                    }
                },
                onFailure = {
                    val fallback = req.toConfirm()
                    addMessage(UiMessage(role = "model", text = confirmSummaryText(fallback), option = fallback))
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
        val answeredIds = _uiState.value.messages.filter { it.option == option }.map { it.id }
        _uiState.update { state ->
            state.copy(
                messages = state.messages.map { msg ->
                    if (msg.option == option) msg.copy(option = null) else msg
                }
            )
        }
        // Ikut dikosongkan di Room — kalau tidak, pertanyaan yang sudah dijawab
        // muncul lagi sebagai tombol aktif begitu layar chat dibuka ulang.
        viewModelScope.launch { answeredIds.forEach { chatRepo.clearMessageOption(it) } }
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
                // Transaksi selesai — slot dikosongkan supaya tidak bocor ke
                // transaksi berikutnya di percakapan yang sama.
                resetAiDraft()
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
        resetAiDraft()
    }

    private fun clearAllOptions() {
        _uiState.update { state ->
            state.copy(
                messages = state.messages.map { msg ->
                    if (msg.option != null) msg.copy(option = null) else msg
                }
            )
        }
        sessionId?.let { sid -> viewModelScope.launch { chatRepo.clearAllOptions(sid) } }
    }

    // ── Clear chat ────────────────────────────────────────────────────────────
    fun clearChat() {
        generationJob?.cancel()
        retryJob?.cancel()
        chatHistory.clear()
        sessionId?.let { sid -> viewModelScope.launch { chatRepo.clearSession(sid) } }
        botStep                = BotStep.Idle
        resetAiDraft()
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

    private fun patchWithDraft(confirm: ChatOption.TransactionConfirm) =
        ChatSlotResolver.patch(confirm, aiDraft)

    private fun preparePendingTransaction(confirm: ChatOption.TransactionConfirm) {
        val missingSlot = ChatSlotResolver.missingSlot(confirm)
        if (missingSlot != null) {
            // Dulu ini `return` senyap: kartu konfirmasi tetap tampil tapi
            // tombol Simpan mati tanpa penjelasan apa pun.
            addMessage(UiMessage(
                role    = "model",
                text    = "*mengetuk meja* Belum bisa disimpan — $missingSlot. Coba sebutkan lagi ya.",
                isError = true
            ))
            return
        }

        val state = _uiState.value
        // Dibatasi ke daftar kategori sesuai tipe transaksi. Tanpa filter ini,
        // transaksi INCOME bisa tersimpan memakai kategori EXPENSE (kedua daftar
        // sama-sama punya "Lainnya") dan merusak semua laporan per kategori.
        val allCats = categoryPoolFor(confirm.type)
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
            mergeAiDraft(
                type         = confirm.type,
                amount       = confirm.amount,
                categoryName = category.name,
                walletName   = wallet.name,
                title        = confirm.title
            )
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
        // Bubble loading (typing indicator) tidak pernah ditulis ke Room.
        // Pesan tanpa teks TAPI berisi opsi tetap disimpan — isinya justru
        // tombol pilihan, satu-satunya cara user melanjutkan percakapan.
        if (msg.isLoading || (msg.text.isBlank() && msg.option == null)) return
        sessionId?.let { sid ->
            viewModelScope.launch {
                chatRepo.saveMessage(
                    id         = msg.id,
                    sessionId  = sid,
                    role       = msg.role,
                    content    = msg.text,
                    isError    = msg.isError,
                    optionJson = ChatOption.encode(msg.option)
                )
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