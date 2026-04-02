// app/src/main/java/com/sndiy/chatfin/feature/chat/ui/ChatScreen.kt
//
// REFACTORED: Semua composable dipindah ke ChatComponents.kt
// File ini sekarang hanya berisi ChatScreen + ChatScreenContent (orchestrator)

package com.sndiy.chatfin.feature.chat.ui

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.core.graphics.toColorInt
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

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
        onCancelTransaction  = viewModel::cancelTransaction
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
) {
    val listState = rememberLazyListState()

    LaunchedEffect(uiState.messages.size) {
        if (uiState.messages.isNotEmpty())
            listState.animateScrollToItem(uiState.messages.lastIndex)
    }

    Scaffold(
        topBar = {
            ChatTopBar(
                accountName = uiState.activeAccount?.name ?: "Pilih Akun",
                onClearChat = onClearChat
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
                            color    = Color(MAI_COLOR.toColorInt())
                        )
                        Spacer(Modifier.height(16.dp))
                        Text("Memeriksa koneksi...", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                uiState.messages.isEmpty() -> {
                    ChatWelcomeState(
                        accountName   = uiState.activeAccount?.name,
                        onQuickAction = { onInputChange(it); onSendMessage() }
                    )
                }

                else -> {
                    LazyColumn(
                        state               = listState,
                        modifier            = Modifier.fillMaxSize(),
                        contentPadding      = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
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
