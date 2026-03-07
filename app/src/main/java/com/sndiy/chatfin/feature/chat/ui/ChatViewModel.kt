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
    val walletId: String
)

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val geminiRepo: GeminiRepository,
    private val accountRepo: AccountRepository,
    private val walletRepo: WalletRepository,
    private val categoryRepo: CategoryRepository,
    private val transactionRepo: TransactionRepository,
    private val systemPromptBuilder: SystemPromptBuilder,
    private val contextBuilder: FinanceContextBuilder
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private val chatHistory = mutableListOf<Pair<String, String>>()
    private var systemPrompt: String = ""

    init {
        observeActiveAccount()
    }

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
            combine(
                walletRepo.getWalletsByAccount(accountId),
                categoryRepo.getCategoriesByAccountAndType(accountId, "EXPENSE"),
                categoryRepo.getCategoriesByAccountAndType(accountId, "INCOME")
            ) { wallets, expCats, incCats ->
                Triple(wallets, expCats, incCats)
            }.collect { (wallets, expCats, incCats) ->
                _uiState.value = _uiState.value.copy(
                    wallets           = wallets,
                    expenseCategories = expCats,
                    incomeCategories  = incCats
                )
                rebuildSystemPrompt()
            }
        }
    }

    private fun rebuildSystemPrompt() {
        val state = _uiState.value
        val financeCtx = contextBuilder.buildContext(
            account           = state.activeAccount,
            wallets           = state.wallets,
            expenseCategories = state.expenseCategories,
            incomeCategories  = state.incomeCategories,
            totalIncome       = 0L,
            totalExpense      = 0L
        )
        systemPrompt = systemPromptBuilder.build(financeContext = financeCtx)
    }

    fun onInputChange(text: String) {
        _uiState.value = _uiState.value.copy(inputText = text)
    }

    fun sendMessage() {
        val text = _uiState.value.inputText.trim()
        if (text.isBlank() || _uiState.value.isTyping) return

        addMessage(UiMessage(role = "user", text = text))
        val historySnapshot = chatHistory.toList()
        chatHistory.add("user" to text)
        _uiState.value = _uiState.value.copy(inputText = "", isTyping = true)

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
            handleResult(result)
        }
    }

    fun onOptionSelected(option: ChatOption, selectedValue: String) {
        val historySnapshot = chatHistory.toList()
        chatHistory.add("user" to selectedValue)
        addMessage(UiMessage(role = "user", text = selectedValue))
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
            handleResult(result)
        }
    }

    private fun handleResult(result: Result<ParsedMessage>) {
        result.fold(
            onSuccess = { parsed ->
                addMessage(UiMessage(role = "model", text = parsed.text, option = parsed.option))
                chatHistory.add("model" to parsed.text)
                if (parsed.option is ChatOption.TransactionConfirm) {
                    preparePendingTransaction(parsed.option)
                }
            },
            onFailure = { error ->
                addMessage(UiMessage(
                    role    = "model",
                    text    = error.message ?: "Terjadi kesalahan",
                    isError = true
                ))
            }
        )
    }

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
                addMessage(UiMessage(
                    role    = "model",
                    text    = "Gagal menyimpan: ${e.message}",
                    isError = true
                ))
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
        _uiState.value = _uiState.value.copy(messages = emptyList())
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