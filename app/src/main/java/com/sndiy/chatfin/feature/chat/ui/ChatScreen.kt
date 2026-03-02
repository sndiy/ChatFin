// app/src/main/java/com/sndiy/chatfin/feature/chat/ui/ChatScreen.kt

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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sndiy.chatfin.ai.ChatOption
import java.text.NumberFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel = hiltViewModel()
) {
    val uiState   by viewModel.uiState.collectAsStateWithLifecycle()
    val listState  = rememberLazyListState()

    // Auto-scroll ke bawah saat pesan baru masuk
    LaunchedEffect(uiState.messages.size) {
        if (uiState.messages.isNotEmpty()) {
            listState.animateScrollToItem(uiState.messages.lastIndex)
        }
    }

    Scaffold(
        topBar = {
            ChatTopBar(
                characterName  = uiState.activeCharacter?.name ?: "AI Assistant",
                characterColor = "#0061A4",
                accountName    = uiState.activeAccount?.name ?: "Pilih Akun",
                onClearChat    = viewModel::clearChat
            )
        },
        bottomBar = {
            ChatInputBar(
                text      = uiState.inputText,
                isTyping  = uiState.isTyping,
                onChange  = viewModel::onInputChange,
                onSend    = viewModel::sendMessage
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {

            if (uiState.messages.isEmpty()) {
                // Tampilan welcome saat belum ada chat
                ChatWelcomeState(
                    characterName = uiState.activeCharacter?.name ?: "AI Assistant",
                    accountName   = uiState.activeAccount?.name,
                    onQuickAction = { viewModel.onInputChange(it); viewModel.sendMessage() }
                )
            } else {
                LazyColumn(
                    state          = listState,
                    modifier       = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start  = 12.dp,
                        end    = 12.dp,
                        top    = 8.dp,
                        bottom = 8.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(
                        items = uiState.messages,
                        key   = { it.id }
                    ) { message ->
                        AnimatedVisibility(
                            visible = true,
                            enter   = fadeIn() + slideInVertically(initialOffsetY = { it / 2 })
                        ) {
                            if (message.isLoading) {
                                TypingIndicatorBubble()
                            } else if (message.role == "user") {
                                UserMessageBubble(text = message.text)
                            } else {
                                AiMessageBubble(
                                    text    = message.text,
                                    option  = message.option,
                                    isError = message.isError,
                                    onOptionSelected = { value ->
                                        viewModel.onOptionSelected(message.option!!, value)
                                    },
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

// ── Top Bar ───────────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatTopBar(
    characterName: String,
    characterColor: String,
    accountName: String,
    onClearChat: () -> Unit
) {
    val color = runCatching {
        Color(android.graphics.Color.parseColor(characterColor))
    }.getOrElse { MaterialTheme.colorScheme.primary }

    TopAppBar(
        title = {
            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Avatar karakter
                Box(
                    modifier         = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(color),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text       = characterName.take(1).uppercase(),
                        color      = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize   = 16.sp
                    )
                }
                Column {
                    Text(
                        text       = characterName,
                        style      = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text  = accountName,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        actions = {
            IconButton(onClick = onClearChat) {
                Icon(
                    imageVector        = Icons.Outlined.DeleteSweep,
                    contentDescription = "Hapus chat"
                )
            }
        }
    )
}

// ── Input Bar ─────────────────────────────────────────────────────────────────
@Composable
private fun ChatInputBar(
    text: String,
    isTyping: Boolean,
    onChange: (String) -> Unit,
    onSend: () -> Unit
) {
    Surface(
        tonalElevation = 3.dp,
        modifier       = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier          = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .navigationBarsPadding(),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value         = text,
                onValueChange = onChange,
                placeholder   = {
                    Text(
                        if (isTyping) "AI sedang mengetik..."
                        else          "Ketik pesan atau catat transaksi...",
                        style = MaterialTheme.typography.bodyMedium
                    )
                },
                maxLines      = 4,
                enabled       = !isTyping,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { onSend() }),
                shape         = RoundedCornerShape(24.dp),
                colors        = OutlinedTextFieldDefaults.colors(
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                ),
                modifier      = Modifier.weight(1f)
            )

            // Tombol send
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

// ── Bubble pesan User ─────────────────────────────────────────────────────────
@Composable
private fun UserMessageBubble(text: String) {
    Row(
        modifier              = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End
    ) {
        Surface(
            shape = RoundedCornerShape(
                topStart    = 18.dp,
                topEnd      = 4.dp,
                bottomStart = 18.dp,
                bottomEnd   = 18.dp
            ),
            color    = MaterialTheme.colorScheme.primary,
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

// ── Bubble pesan AI ───────────────────────────────────────────────────────────
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
        modifier              = Modifier.fillMaxWidth(),
        horizontalAlignment   = Alignment.Start,
        verticalArrangement   = Arrangement.spacedBy(6.dp)
    ) {
        // Teks pesan AI
        if (text.isNotBlank()) {
            Surface(
                shape = RoundedCornerShape(
                    topStart    = 4.dp,
                    topEnd      = 18.dp,
                    bottomStart = 18.dp,
                    bottomEnd   = 18.dp
                ),
                color    = if (isError)
                    MaterialTheme.colorScheme.errorContainer
                else
                    MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.widthIn(max = 300.dp)
            ) {
                Text(
                    text     = text,
                    style    = MaterialTheme.typography.bodyMedium,
                    color    = if (isError)
                        MaterialTheme.colorScheme.onErrorContainer
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                )
            }
        }

        // Tampilkan pilihan sesuai tipe
        when (option) {
            is ChatOption.CategoryOptions -> {
                OptionChips(
                    options  = option.options,
                    icon     = Icons.Default.Category,
                    onSelect = onOptionSelected
                )
            }
            is ChatOption.WalletOptions -> {
                OptionChips(
                    options  = option.options,
                    icon     = Icons.Default.AccountBalanceWallet,
                    onSelect = onOptionSelected
                )
            }
            is ChatOption.TransactionConfirm -> {
                TransactionConfirmCard(
                    confirm   = option,
                    onConfirm = onConfirmTransaction,
                    onCancel  = onCancelTransaction
                )
            }
            is ChatOption.YesNo -> {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { onOptionSelected("Tidak") }) {
                        Text("Tidak")
                    }
                    Button(onClick = { onOptionSelected("Ya") }) {
                        Text("Ya")
                    }
                }
            }
            null -> {}
        }
    }
}

// ── Chips pilihan (kategori / dompet) ─────────────────────────────────────────
@Composable
private fun OptionChips(
    options: List<String>,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onSelect: (String) -> Unit
) {
    // Bungkus dengan Row yang bisa scroll horizontal
    Row(
        modifier              = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        options.forEach { opt ->
            InputChip(
                selected  = false,
                onClick   = { onSelect(opt) },
                label     = { Text(opt) },
                leadingIcon = {
                    Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
                }
            )
        }
    }
}

// ── Card konfirmasi transaksi ─────────────────────────────────────────────────
@Composable
private fun TransactionConfirmCard(
    confirm: ChatOption.TransactionConfirm,
    onConfirm: () -> Unit,
    onCancel: () -> Unit
) {
    val fmt        = NumberFormat.getNumberInstance(Locale("id", "ID"))
    val typeLabel  = when (confirm.type) {
        "INCOME"   -> "Pemasukan"
        "TRANSFER" -> "Transfer"
        else       -> "Pengeluaran"
    }
    val typeColor  = when (confirm.type) {
        "INCOME"   -> Color(0xFF1B8A4C)
        "TRANSFER" -> MaterialTheme.colorScheme.primary
        else       -> MaterialTheme.colorScheme.error
    }

    Card(
        modifier = Modifier.widthIn(max = 300.dp),
        border   = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Header
            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector        = Icons.Default.Receipt,
                    contentDescription = null,
                    tint               = typeColor,
                    modifier           = Modifier.size(20.dp)
                )
                Text(
                    text       = "Konfirmasi $typeLabel",
                    style      = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
            }

            HorizontalDivider()

            // Detail transaksi
            TransactionDetailRow("Nominal", "Rp ${fmt.format(confirm.amount)}")
            TransactionDetailRow("Kategori", confirm.category)
            TransactionDetailRow("Dompet", confirm.wallet)

            HorizontalDivider()

            // Tombol aksi
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick  = onCancel,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Batal")
                }
                Button(
                    onClick   = onConfirm,
                    modifier  = Modifier.weight(1f),
                    colors    = ButtonDefaults.buttonColors(
                        containerColor = typeColor
                    )
                ) {
                    Text("Simpan")
                }
            }
        }
    }
}

@Composable
private fun TransactionDetailRow(label: String, value: String) {
    Row(
        modifier              = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text  = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text       = value,
            style      = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold
        )
    }
}

// ── Indikator AI sedang mengetik (3 titik animasi) ────────────────────────────
@Composable
private fun TypingIndicatorBubble() {
    val infiniteTransition = rememberInfiniteTransition(label = "typing")

    Row(
        modifier              = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start
    ) {
        Surface(
            shape = RoundedCornerShape(4.dp, 18.dp, 18.dp, 18.dp),
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Row(
                modifier              = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment     = Alignment.CenterVertically
            ) {
                repeat(3) { index ->
                    val offsetY by infiniteTransition.animateFloat(
                        initialValue   = 0f,
                        targetValue    = -6f,
                        animationSpec  = infiniteRepeatable(
                            animation  = tween(400, easing = EaseInOut),
                            repeatMode = RepeatMode.Reverse,
                            initialStartOffset = StartOffset(index * 120)
                        ),
                        label          = "dot$index"
                    )
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .offset(y = offsetY.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                    )
                }
            }
        }
    }
}

// ── Welcome state saat chat kosong ────────────────────────────────────────────
@Composable
private fun ChatWelcomeState(
    characterName: String,
    accountName: String?,
    onQuickAction: (String) -> Unit
) {
    val quickActions = listOf(
        "💰 Lihat saldo",
        "📊 Ringkasan bulan ini",
        "➕ Catat pemasukan",
        "➖ Catat pengeluaran"
    )

    Column(
        modifier              = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment   = Alignment.CenterHorizontally,
        verticalArrangement   = Arrangement.Center
    ) {
        // Avatar besar
        Box(
            modifier         = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text       = characterName.take(1).uppercase(),
                style      = MaterialTheme.typography.displaySmall,
                color      = MaterialTheme.colorScheme.onPrimaryContainer,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text       = "Halo! Aku $characterName",
            style      = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign  = TextAlign.Center
        )

        if (accountName != null) {
            Text(
                text      = "Mengelola akun: $accountName",
                style     = MaterialTheme.typography.bodyMedium,
                color     = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text      = "Ceritain aja mau catat transaksi apa,\natau tanya seputar keuanganmu!",
            style     = MaterialTheme.typography.bodyMedium,
            color     = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Quick action chips
        Text(
            text  = "Mulai dengan:",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Grid 2 kolom
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