package com.sndiy.chatfin.feature.chat.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sndiy.chatfin.core.ui.theme.MaiPurple
import kotlinx.coroutines.delay

@Composable
fun ChatScreen(viewModel: ChatViewModel = hiltViewModel()) {
    val uiState           by viewModel.uiState.collectAsStateWithLifecycle()
    val isCheckingNetwork by viewModel.isCheckingNetwork.collectAsStateWithLifecycle()

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
        onQuickAction        = viewModel::quickAction
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
    onQuickAction: (String) -> Unit
) {
    val listState = rememberLazyListState()
    var showClearDialog by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.messages.size) {
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
                    text      = uiState.inputText,
                    isTyping  = uiState.isTyping,
                    isBotMode = uiState.isBotMode,
                    enabled   = !isCheckingNetwork &&
                            (uiState.connectionStatus != ConnectionStatus.NO_INTERNET || uiState.isBotMode),
                    onChange  = onInputChange,
                    onSend    = onSendMessage,
                    onStop    = onStopGeneration
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
                                    message.role == "user" -> UserMessageBubble(text = message.text)
                                    else -> AiMessageBubble(
                                        text                 = message.text,
                                        option               = message.option,
                                        isError              = message.isError,
                                        pendingTransaction   = uiState.pendingTransaction,
                                        onOptionSelected     = { value -> onOptionSelected(message.option!!, value) },
                                        onConfirmTransaction = onConfirmTransaction,
                                        onCancelTransaction  = onCancelTransaction
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
