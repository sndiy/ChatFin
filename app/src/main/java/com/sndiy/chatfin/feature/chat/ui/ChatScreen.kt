package com.sndiy.chatfin.feature.chat.ui

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sndiy.chatfin.R
import com.sndiy.chatfin.core.ui.theme.MaiPurple
import kotlinx.coroutines.delay

@Composable
fun ChatScreen(
    onNavigateToEditTransaction: (String) -> Unit = {},
    viewModel: ChatViewModel = hiltViewModel()
) {
    val uiState           by viewModel.uiState.collectAsStateWithLifecycle()
    val isCheckingNetwork by viewModel.isCheckingNetwork.collectAsStateWithLifecycle()
    val clipboardManager  = LocalClipboardManager.current
    val context           = LocalContext.current
    val copiedToastMsg    = stringResource(R.string.chat_copied_toast)

    ChatScreenContent(
        uiState              = uiState,
        isCheckingNetwork    = isCheckingNetwork,
        onClearChat          = viewModel::clearChat,
        onRetry              = viewModel::retryAi,
        onBotMode            = viewModel::switchToBotMode,
        onInputChange        = viewModel::onInputChange,
        onSendMessage        = viewModel::sendMessage,
        onStopGeneration     = viewModel::stopGeneration,
        onOptionSelected     = viewModel::onOptionSelected,
        onConfirmTransaction = viewModel::confirmTransaction,
        onCancelTransaction  = viewModel::cancelTransaction,
        onQuickAction        = viewModel::quickAction,
        onCopyText           = { text ->
            clipboardManager.setText(AnnotatedString(text))
            Toast.makeText(context, copiedToastMsg, Toast.LENGTH_SHORT).show()
        },
        onEditMessage        = viewModel::editMessage,
        onDeleteMessage      = viewModel::deleteMessage,
        onReplyMessage       = { messageId, _ ->
            val msg = uiState.messages.find { it.id == messageId }
            if (msg != null) viewModel.setReplyingMessage(msg)
        },
        onCancelReply        = viewModel::clearReplyingMessage,
        onEditTransactionQuick     = viewModel::requestQuickEdit,
        onDeleteTransactionRequest = viewModel::requestDeleteTransaction,
        deleteConfirmTransaction   = uiState.deleteConfirmTransaction,
        onConfirmDeleteTransaction = viewModel::confirmDeleteTransaction,
        onDismissDeleteConfirm     = viewModel::dismissDeleteConfirm,
        quickEditTransaction       = uiState.quickEditTransaction,
        expenseCategories          = uiState.expenseCategories,
        incomeCategories           = uiState.incomeCategories,
        wallets                    = uiState.wallets,
        onSaveQuickEdit            = viewModel::saveQuickEdit,
        onDismissQuickEdit         = viewModel::dismissQuickEdit,
        onFullEditTransaction      = onNavigateToEditTransaction
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatScreenContent(
    uiState: ChatUiState,
    isCheckingNetwork: Boolean,
    onClearChat: () -> Unit,
    onRetry: () -> Unit,
    onBotMode: () -> Unit,
    onInputChange: (String) -> Unit,
    onSendMessage: () -> Unit,
    onStopGeneration: () -> Unit,
    onOptionSelected: (com.sndiy.chatfin.ai.ChatOption, String) -> Unit,
    onConfirmTransaction: () -> Unit,
    onCancelTransaction: () -> Unit,
    onQuickAction: (String) -> Unit,
    onCopyText: (String) -> Unit,
    onEditMessage: (String, String) -> Unit,
    onDeleteMessage: (String) -> Unit,
    onReplyMessage: (String, String) -> Unit,
    onCancelReply: () -> Unit,
    onEditTransactionQuick: (com.sndiy.chatfin.core.data.local.entity.TransactionEntity) -> Unit,
    onDeleteTransactionRequest: (com.sndiy.chatfin.core.data.local.entity.TransactionEntity) -> Unit,
    deleteConfirmTransaction: com.sndiy.chatfin.core.data.local.entity.TransactionEntity?,
    onConfirmDeleteTransaction: () -> Unit,
    onDismissDeleteConfirm: () -> Unit,
    quickEditTransaction: com.sndiy.chatfin.core.data.local.entity.TransactionEntity?,
    expenseCategories: List<com.sndiy.chatfin.core.data.local.entity.CategoryEntity>,
    incomeCategories: List<com.sndiy.chatfin.core.data.local.entity.CategoryEntity>,
    wallets: List<com.sndiy.chatfin.core.data.local.entity.WalletEntity>,
    onSaveQuickEdit: (String, Long, String, String, java.time.LocalDate) -> Unit,
    onDismissQuickEdit: () -> Unit,
    onFullEditTransaction: (String) -> Unit
) {
    val listState = rememberLazyListState()
    var showClearDialog by remember { mutableStateOf(false) }
    val lastUserMessageId = remember(uiState.messages) {
        uiState.messages.lastOrNull { it.role == "user" }?.id
    }

    LaunchedEffect(uiState.messages.size, uiState.messages.lastOrNull()?.id) {
        if (uiState.messages.isNotEmpty()) {
            // Small delay so the new item is laid out before scrolling
            delay(50)
            listState.animateScrollToItem(uiState.messages.lastIndex)
        }
    }

    if (showClearDialog) {
        ClearChatDialog(
            onConfirm = {
                showClearDialog = false
                onClearChat()
            },
            onDismiss = { showClearDialog = false }
        )
    }

    deleteConfirmTransaction?.let { tx ->
        val fmt     = java.text.NumberFormat.getNumberInstance(java.util.Locale("id", "ID"))
        val isLarge = tx.amount >= 500_000
        com.sndiy.chatfin.core.ui.component.ConfirmDestructiveDialog(
            title             = stringResource(if (isLarge) R.string.chat_tx_delete_title_large else R.string.chat_tx_delete_title),
            body              = if (isLarge)
                stringResource(R.string.chat_tx_delete_body_large, fmt.format(tx.amount))
            else
                stringResource(R.string.chat_tx_delete_body),
            confirmLabel      = stringResource(R.string.chat_tx_delete_confirm),
            useErrorContainer = isLarge,
            onConfirm         = onConfirmDeleteTransaction,
            onDismiss         = onDismissDeleteConfirm
        )
    }

    quickEditTransaction?.let { tx ->
        QuickEditTransactionSheet(
            transaction       = tx,
            expenseCategories = expenseCategories,
            incomeCategories  = incomeCategories,
            wallets           = wallets,
            onSave            = onSaveQuickEdit,
            onDismiss         = onDismissQuickEdit,
            onFullEdit        = onFullEditTransaction
        )
    }

    Scaffold(
        topBar = {
            ChatTopBar(
                accountName      = uiState.activeAccount?.name ?: "Pilih Akun",
                onClearChat      = { showClearDialog = true },
                onShowClearConfirm = { showClearDialog = true }
            )
        },
        bottomBar = {
            Column {
                ConnectionStatusBanner(
                    status    = uiState.connectionStatus,
                    countdown = uiState.retryCountdown,
                    onRetry   = onRetry,
                    onBotMode = onBotMode
                )
                ChatInputBar(
                    text              = uiState.inputText,
                    isTyping          = uiState.isTyping,
                    isBotMode         = uiState.isBotMode,
                    enabled           = !isCheckingNetwork &&
                            (uiState.connectionStatus != ConnectionStatus.NO_INTERNET || uiState.isBotMode),
                    replyingToMessage = uiState.replyingToMessage,
                    onCancelReply     = onCancelReply,
                    onChange          = onInputChange,
                    onSend            = onSendMessage,
                    onStop            = onStopGeneration
                )
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                isCheckingNetwork -> {
                    Column(
                        modifier            = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(40.dp),
                            color    = MaiPurple
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            "Mai sedang bersiap...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                uiState.messages.isEmpty() -> {
                    ChatWelcomeState(
                        accountName   = uiState.activeAccount?.name,
                        onQuickAction = onQuickAction
                    )
                }

                else -> {
                    LazyColumn(
                        state               = listState,
                        modifier            = Modifier.fillMaxSize(),
                        contentPadding      = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(items = uiState.messages, key = { it.id }) { message ->
                            AnimatedVisibility(
                                visible = true,
                                enter   = fadeIn() + slideInVertically(initialOffsetY = { it / 2 })
                            ) {
                                when {
                                    message.isLoading      -> TypingIndicatorBubble()
                                    message.role == "user" -> UserMessageBubble(
                                        messageId       = message.id,
                                        text            = message.text,
                                        isEditable      = message.id == lastUserMessageId,
                                        onCopyText      = onCopyText,
                                        onEditMessage   = onEditMessage,
                                        onDeleteMessage = onDeleteMessage,
                                        onReplyMessage  = onReplyMessage
                                    )
                                    else -> AiMessageBubble(
                                        messageId              = message.id,
                                        text                   = message.text,
                                        option                 = message.option,
                                        isError                = message.isError,
                                        transactions           = uiState.transactions,
                                        categories             = uiState.expenseCategories + uiState.incomeCategories,
                                        wallets                = uiState.wallets,
                                        pendingTransaction     = uiState.pendingTransaction,
                                        onOptionSelected       = { value -> onOptionSelected(message.option!!, value) },
                                        onConfirmTransaction   = onConfirmTransaction,
                                        onCancelTransaction    = onCancelTransaction,
                                        onEditTransactionQuick = onEditTransactionQuick,
                                        onDeleteTransactionRequest = onDeleteTransactionRequest,
                                        onCopyText             = onCopyText,
                                        onDeleteMessage        = onDeleteMessage,
                                        onReplyMessage         = onReplyMessage
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
