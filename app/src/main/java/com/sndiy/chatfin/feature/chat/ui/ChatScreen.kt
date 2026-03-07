package com.sndiy.chatfin.feature.chat.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sndiy.chatfin.ai.ChatOption
import java.text.NumberFormat
import java.util.Locale

private const val MAI_NAME  = "Sakurajima Mai"
private const val MAI_COLOR = "#7E57C2"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel = hiltViewModel()
) {
    val uiState  by viewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    LaunchedEffect(uiState.messages.size) {
        if (uiState.messages.isNotEmpty()) {
            listState.animateScrollToItem(uiState.messages.lastIndex)
        }
    }

    Scaffold(
        topBar = {
            ChatTopBar(
                accountName = uiState.activeAccount?.name ?: "Pilih Akun",
                onClearChat = viewModel::clearChat
            )
        },
        bottomBar = {
            ChatInputBar(
                text     = uiState.inputText,
                isTyping = uiState.isTyping,
                onChange = viewModel::onInputChange,
                onSend   = viewModel::sendMessage
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (uiState.messages.isEmpty()) {
                ChatWelcomeState(
                    accountName   = uiState.activeAccount?.name,
                    onQuickAction = { viewModel.onInputChange(it); viewModel.sendMessage() }
                )
            } else {
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
                                message.isLoading        -> TypingIndicatorBubble()
                                message.role == "user"   -> UserMessageBubble(text = message.text)
                                else                     -> AiMessageBubble(
                                    text                 = message.text,
                                    option               = message.option,
                                    isError              = message.isError,
                                    onOptionSelected     = { value -> viewModel.onOptionSelected(message.option!!, value) },
                                    onConfirmTransaction = viewModel::confirmTransaction,
                                    onCancelTransaction  = viewModel::cancelTransaction
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatTopBar(accountName: String, onClearChat: () -> Unit) {
    val color = Color(android.graphics.Color.parseColor(MAI_COLOR))
    TopAppBar(
        title = {
            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier         = Modifier.size(38.dp).clip(CircleShape).background(color),
                    contentAlignment = Alignment.Center
                ) {
                    Text("M", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
                Column {
                    Text(MAI_NAME, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(accountName, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        actions = {
            IconButton(onClick = onClearChat) {
                Icon(Icons.Outlined.DeleteSweep, contentDescription = "Hapus chat")
            }
        }
    )
}

@Composable
private fun ChatInputBar(
    text: String,
    isTyping: Boolean,
    onChange: (String) -> Unit,
    onSend: () -> Unit
) {
    Surface(tonalElevation = 3.dp, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier              = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp).navigationBarsPadding(),
            verticalAlignment     = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value           = text,
                onValueChange   = onChange,
                placeholder     = {
                    Text(
                        if (isTyping) "Mai sedang mengetik..." else "Ketik pesan atau catat transaksi...",
                        style = MaterialTheme.typography.bodyMedium
                    )
                },
                maxLines        = 4,
                enabled         = !isTyping,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { onSend() }),
                shape           = RoundedCornerShape(24.dp),
                colors          = OutlinedTextFieldDefaults.colors(
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                ),
                modifier        = Modifier.weight(1f)
            )
            FilledIconButton(
                onClick  = onSend,
                enabled  = text.isNotBlank() && !isTyping,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(Icons.Default.Send, contentDescription = "Kirim")
            }
        }
    }
}

@Composable
private fun UserMessageBubble(text: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Surface(
            shape = RoundedCornerShape(topStart = 18.dp, topEnd = 4.dp, bottomStart = 18.dp, bottomEnd = 18.dp),
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.widthIn(max = 280.dp)
        ) {
            Text(
                text     = text,
                color    = MaterialTheme.colorScheme.onPrimary,
                style    = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
            )
        }
    }
}

@Composable
private fun AiMessageBubble(
    text: String,
    option: ChatOption?,
    isError: Boolean,
    onOptionSelected: (String) -> Unit,
    onConfirmTransaction: () -> Unit,
    onCancelTransaction: () -> Unit
) {
    Column(
        modifier            = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (text.isNotBlank()) {
            Surface(
                shape    = RoundedCornerShape(topStart = 4.dp, topEnd = 18.dp, bottomStart = 18.dp, bottomEnd = 18.dp),
                color    = if (isError) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.widthIn(max = 300.dp)
            ) {
                Text(
                    text     = text,
                    style    = MaterialTheme.typography.bodyMedium,
                    color    = if (isError) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                )
            }
        }

        when (option) {
            is ChatOption.CategoryOptions -> OptionChips(options = option.options, icon = Icons.Default.Category, onSelect = onOptionSelected)
            is ChatOption.WalletOptions   -> OptionChips(options = option.options, icon = Icons.Default.AccountBalanceWallet, onSelect = onOptionSelected)
            is ChatOption.TransactionConfirm -> TransactionConfirmCard(confirm = option, onConfirm = onConfirmTransaction, onCancel = onCancelTransaction)
            is ChatOption.YesNo -> {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { onOptionSelected("Tidak") }) { Text("Tidak") }
                    Button(onClick = { onOptionSelected("Ya") }) { Text("Ya") }
                }
            }
            null -> {}
        }
    }
}

@Composable
private fun OptionChips(
    options: List<String>,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onSelect: (String) -> Unit
) {
    Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { opt ->
            InputChip(
                selected    = false,
                onClick     = { onSelect(opt) },
                label       = { Text(opt) },
                leadingIcon = { Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp)) }
            )
        }
    }
}

@Composable
private fun TransactionConfirmCard(
    confirm: ChatOption.TransactionConfirm,
    onConfirm: () -> Unit,
    onCancel: () -> Unit
) {
    val fmt       = NumberFormat.getNumberInstance(Locale("id", "ID"))
    val typeLabel = when (confirm.type) { "INCOME" -> "Pemasukan"; "TRANSFER" -> "Transfer"; else -> "Pengeluaran" }
    val typeColor = when (confirm.type) { "INCOME" -> Color(0xFF1B8A4C); "TRANSFER" -> MaterialTheme.colorScheme.primary; else -> MaterialTheme.colorScheme.error }

    Card(modifier = Modifier.widthIn(max = 300.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Receipt, contentDescription = null, tint = typeColor, modifier = Modifier.size(20.dp))
                Text("Konfirmasi $typeLabel", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            }
            HorizontalDivider()
            TransactionDetailRow("Nominal",  "Rp ${fmt.format(confirm.amount)}")
            TransactionDetailRow("Kategori", confirm.category)
            TransactionDetailRow("Dompet",   confirm.wallet)
            HorizontalDivider()
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onCancel,  modifier = Modifier.weight(1f)) { Text("Batal") }
                Button(onClick = onConfirm, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = typeColor)) { Text("Simpan") }
            }
        }
    }
}

@Composable
private fun TransactionDetailRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun TypingIndicatorBubble() {
    val infiniteTransition = rememberInfiniteTransition(label = "typing")
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
        Surface(shape = RoundedCornerShape(4.dp, 18.dp, 18.dp, 18.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
            Row(
                modifier              = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment     = Alignment.CenterVertically
            ) {
                repeat(3) { index ->
                    val offsetY by infiniteTransition.animateFloat(
                        initialValue  = 0f,
                        targetValue   = -6f,
                        animationSpec = infiniteRepeatable(
                            animation          = tween(400, easing = EaseInOut),
                            repeatMode         = RepeatMode.Reverse,
                            initialStartOffset = StartOffset(index * 120)
                        ),
                        label = "dot$index"
                    )
                    Box(
                        modifier = Modifier.size(8.dp).offset(y = offsetY.dp).clip(CircleShape)
                            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                    )
                }
            }
        }
    }
}

@Composable
private fun ChatWelcomeState(accountName: String?, onQuickAction: (String) -> Unit) {
    val quickActions = listOf("💰 Lihat saldo", "📊 Ringkasan bulan ini", "➕ Catat pemasukan", "➖ Catat pengeluaran")
    val maiColor     = Color(android.graphics.Color.parseColor(MAI_COLOR))

    Column(
        modifier            = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier         = Modifier.size(80.dp).clip(CircleShape).background(maiColor),
            contentAlignment = Alignment.Center
        ) {
            Text("M", style = MaterialTheme.typography.displaySmall, color = Color.White, fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text("Halo! Aku $MAI_NAME", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)

        if (accountName != null) {
            Text("Mengelola akun: $accountName", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            "Ceritain aja mau catat transaksi apa,\natau tanya seputar keuanganmu!",
            style     = MaterialTheme.typography.bodyMedium,
            color     = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(32.dp))

        Text("Mulai dengan:", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

        Spacer(modifier = Modifier.height(12.dp))

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            quickActions.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { action ->
                        SuggestionChip(
                            onClick  = { onQuickAction(action.drop(2).trim()) },
                            label    = { Text(action) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    if (row.size == 1) Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}