package com.sndiy.chatfin.feature.chat.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sndiy.chatfin.ai.*
import com.sndiy.chatfin.core.data.local.entity.*
import com.sndiy.chatfin.feature.finance.account.data.repository.AccountRepository
import com.sndiy.chatfin.feature.finance.transaction.data.repository.CategoryRepository
import com.sndiy.chatfin.feature.finance.transaction.data.repository.TransactionRepository
import com.sndiy.chatfin.feature.finance.transaction.data.repository.WalletRepository
import dagger.hilt.android.lifecycle.HiltViewModel
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

data class ChatUiState(
    val messages: List<UiMessage>               = emptyList(),
    val inputText: String                       = "",
    val isTyping: Boolean                       = false,
    val isBotMode: Boolean                      = false,
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
    private val botHandler: BotModeHandler
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private val chatHistory  = mutableListOf<Pair<String, String>>()
    private var systemPrompt = ""
    private var botStep: BotStep = BotStep.Idle

    init { observeActiveAccount() }

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

    // ── Kirim pesan ───────────────────────────────────────────────────────────
    fun sendMessage() {
        val text = _uiState.value.inputText.trim()
        if (text.isBlank() || _uiState.value.isTyping) return

        addMessage(UiMessage(role = "user", text = text))
        _uiState.value = _uiState.value.copy(inputText = "")

        // Bot mode aktif atau input adalah perintah bot
        if (_uiState.value.isBotMode || botStep !is BotStep.Idle || botHandler.isBotCommand(text)) {
            handleBotMode(text)
            return
        }

        // Mode AI normal
        val historySnapshot = chatHistory.toList()
        chatHistory.add("user" to text)
        _uiState.value = _uiState.value.copy(isTyping = true)

        val loadingId = UUID.randomUUID().toString()
        addMessage(UiMessage(id = loadingId, role = "model", text = "", isLoading = true))

        viewModelScope.launch {
            val result = geminiRepo.sendMessage(
                userMessage  = text,
                chatHistory  = historySnapshot,
                systemPrompt = systemPrompt
            )
            removeMessage(loadingId)
            _uiState.value = _uiState.value.copy(isTyping = false)

            result.fold(
                onSuccess = { parsed ->
                    addMessage(UiMessage(role = "model", text = parsed.text, option = parsed.option))
                    chatHistory.add("model" to parsed.text)
                    if (parsed.option is ChatOption.TransactionConfirm) {
                        preparePendingTransaction(parsed.option)
                    }
                },
                onFailure = { error ->
                    val isQuota = error is QuotaExhaustedException
                    if (isQuota) {
                        // Masuk bot mode, sambut user
                        _uiState.value = _uiState.value.copy(isBotMode = true)
                        addMessage(UiMessage(
                            role = "model",
                            text = "⚠️ AI sedang tidak tersedia karena batas kuota.\n\n" +
                                    "Aku beralih ke *Mode Bot*. Ketik *help* untuk melihat perintah yang tersedia."
                        ))
                    } else {
                        addMessage(UiMessage(
                            role    = "model",
                            text    = error.message ?: "Terjadi kesalahan",
                            isError = true
                        ))
                    }
                }
            )
        }
    }

    // ── Bot mode handler ──────────────────────────────────────────────────────
    private fun handleBotMode(input: String) {
        val state        = _uiState.value
        val totalBalance = state.wallets.sumOf { it.balance }

        val result = botHandler.handle(
            input              = input,
            currentStep        = botStep,
            wallets            = state.wallets,
            expenseCategories  = state.expenseCategories,
            incomeCategories   = state.incomeCategories,
            totalBalance       = totalBalance
        )

        botStep = result.nextStep

        // Perintah rangkuman — generate di sini karena butuh data transaksi
        if (result.text == "__RANGKUMAN__") {
            handleRangkuman()
            return
        }

        if (result.saveTransaction != null) {
            saveBotTransaction(result.saveTransaction)
        }

        addMessage(UiMessage(
            role   = "model",
            text   = result.text,
            option = result.option
        ))
    }

    private fun handleRangkuman() {
        val state   = _uiState.value
        val account = state.activeAccount ?: return
        val now        = LocalDate.now()
        val startMonth = now.withDayOfMonth(1)

        viewModelScope.launch {
            try {
                combine(
                    transactionRepo.getTransactionsByPeriod(account.id, startMonth, now),
                    transactionRepo.getTotalIncome(account.id, startMonth, now),
                    transactionRepo.getTotalExpense(account.id, startMonth, now)
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
                    date       = LocalDate.now(),
                    time       = LocalTime.now()
                )
            } catch (e: Exception) {
                addMessage(UiMessage(role = "model", text = "Gagal simpan: ${e.message}", isError = true))
            }
        }
    }

    // ── Option selected (chip) ────────────────────────────────────────────────
    fun onOptionSelected(option: ChatOption, selectedValue: String) {
        addMessage(UiMessage(role = "user", text = selectedValue))

        if (_uiState.value.isBotMode || botStep !is BotStep.Idle) {
            handleBotMode(selectedValue)
            return
        }

        val historySnapshot = chatHistory.toList()
        chatHistory.add("user" to selectedValue)
        _uiState.value = _uiState.value.copy(isTyping = true)

        val loadingId = UUID.randomUUID().toString()
        addMessage(UiMessage(id = loadingId, role = "model", text = "", isLoading = true))

        viewModelScope.launch {
            val result = geminiRepo.sendMessage(
                userMessage  = selectedValue,
                chatHistory  = historySnapshot,
                systemPrompt = systemPrompt
            )
            removeMessage(loadingId)
            _uiState.value = _uiState.value.copy(isTyping = false)
            result.fold(
                onSuccess = { parsed ->
                    addMessage(UiMessage(role = "model", text = parsed.text, option = parsed.option))
                    chatHistory.add("model" to parsed.text)
                    if (parsed.option is ChatOption.TransactionConfirm) preparePendingTransaction(parsed.option)
                },
                onFailure = { error ->
                    addMessage(UiMessage(role = "model", text = error.message ?: "Error", isError = true))
                }
            )
        }
    }

    // ── Konfirmasi transaksi AI ───────────────────────────────────────────────
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
                    date       = LocalDate.now(),
                    time       = LocalTime.now()
                )
                _uiState.value = _uiState.value.copy(pendingTransaction = null)
                addMessage(UiMessage(role = "user", text = "Simpan"))
                chatHistory.add("user" to "Simpan")
            } catch (e: Exception) {
                addMessage(UiMessage(role = "model", text = "Gagal menyimpan: ${e.message}", isError = true))
            }
        }
    }

    fun cancelTransaction() {
        _uiState.value = _uiState.value.copy(pendingTransaction = null)
        addMessage(UiMessage(role = "user", text = "Batal"))
        chatHistory.add("user" to "Batal")
    }

    fun clearChat() {
        chatHistory.clear()
        botStep        = BotStep.Idle
        _uiState.value = _uiState.value.copy(messages = emptyList(), isBotMode = false)
    }

    private fun preparePendingTransaction(confirm: ChatOption.TransactionConfirm) {
        val state    = _uiState.value
        val category = (state.expenseCategories + state.incomeCategories)
            .find { it.name.equals(confirm.category, ignoreCase = true) }
        val wallet   = state.wallets
            .find { it.name.equals(confirm.wallet, ignoreCase = true) }

        if (category != null && wallet != null) {
            _uiState.value = _uiState.value.copy(
                pendingTransaction = PendingTransaction(
                    type         = confirm.type,
                    amount       = confirm.amount,
                    categoryName = confirm.category,
                    walletName   = confirm.wallet,
                    categoryId   = category.id,
                    walletId     = wallet.id
                )
            )
        }
    }

    private fun addMessage(msg: UiMessage) {
        _uiState.value = _uiState.value.copy(messages = _uiState.value.messages + msg)
    }

    private fun removeMessage(id: String) {
        _uiState.value = _uiState.value.copy(
            messages = _uiState.value.messages.filter { it.id != id }
        )
    }
}