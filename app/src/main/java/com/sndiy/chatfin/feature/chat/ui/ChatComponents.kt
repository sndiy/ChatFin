package com.sndiy.chatfin.feature.chat.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.*
import com.sndiy.chatfin.ai.ChatOption
import com.sndiy.chatfin.core.ui.theme.MaiAccent
import com.sndiy.chatfin.core.ui.theme.MaiPurple
import com.sndiy.chatfin.core.ui.theme.MaiPurpleDk
import java.text.NumberFormat
import java.time.LocalTime
import java.util.Locale

internal const val MAI_NAME  = "Mai"

// ── Top Bar ───────────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ChatTopBar(
    accountName: String,
    onClearChat: () -> Unit,
    onShowClearConfirm: () -> Unit = onClearChat
) {
    TopAppBar(
        title = {
            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Mai's avatar with gradient
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(listOf(MaiPurple, MaiPurpleDk))
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "舞",
                        color      = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize   = 18.sp
                    )
                }
                Column {
                    Text(
                        "Sakurajima $MAI_NAME",
                        style      = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        accountName,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        actions = {
            IconButton(onClick = onShowClearConfirm) {
                Icon(Icons.Outlined.DeleteSweep, contentDescription = "Hapus chat")
            }
        }
    )
}

// ── Input Bar ─────────────────────────────────────────────────────────────────
@Composable
internal fun ChatInputBar(
    text: String,
    isTyping: Boolean,
    isBotMode: Boolean,
    enabled: Boolean,
    onChange: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit
) {
    Surface(tonalElevation = 3.dp, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier              = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment     = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value         = text,
                onValueChange = onChange,
                placeholder   = {
                    Text(
                        when {
                            !enabled && !isBotMode -> "Memeriksa koneksi..."
                            isTyping               -> "Mai sedang mengetik..."
                            isBotMode              -> "Ketik perintah bot (help untuk bantuan)..."
                            else                   -> "Tulis sesuatu ke Mai..."
                        },
                        style = MaterialTheme.typography.bodyMedium
                    )
                },
                maxLines        = 4,
                enabled         = enabled && !isTyping,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { if (!isTyping) onSend() }),
                shape  = RoundedCornerShape(24.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor   = MaiPurple,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                    disabledBorderColor  = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                    cursorColor          = MaiPurple
                ),
                modifier = Modifier.weight(1f)
            )

            AnimatedContent(
                targetState   = isTyping,
                transitionSpec = {
                    scaleIn(animationSpec = tween(200)) + fadeIn() togetherWith
                            scaleOut(animationSpec = tween(200)) + fadeOut()
                },
                label = "send_stop_button"
            ) { typing ->
                if (typing) {
                    FilledIconButton(
                        onClick  = onStop,
                        modifier = Modifier.size(48.dp),
                        colors   = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor   = MaterialTheme.colorScheme.onErrorContainer
                        )
                    ) { Icon(Icons.Default.Stop, contentDescription = "Berhenti") }
                } else {
                    FilledIconButton(
                        onClick  = onSend,
                        enabled  = text.isNotBlank() && enabled,
                        modifier = Modifier.size(48.dp),
                        colors   = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaiPurple,
                            contentColor   = Color.White,
                            disabledContainerColor = MaiPurple.copy(alpha = 0.3f),
                            disabledContentColor   = Color.White.copy(alpha = 0.5f)
                        )
                    ) { Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Kirim") }
                }
            }
        }
    }
}

// ── Connection Status Banner ──────────────────────────────────────────────────
@Composable
internal fun ConnectionStatusBanner(
    status: ConnectionStatus,
    countdown: Int,
    onRetry: () -> Unit,
    onBotMode: () -> Unit
) {
    when (status) {
        ConnectionStatus.NO_INTERNET -> {
            Surface(color = Color(0xFF1C1C1E), modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier              = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(Icons.Default.WifiOff, null, tint = Color(0xFFFF9500), modifier = Modifier.size(20.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Tidak ada koneksi", style = MaterialTheme.typography.labelMedium, color = Color.White, fontWeight = FontWeight.SemiBold)
                        Text("Mode Bot aktif · Tekan Coba lagi saat online", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.6f))
                    }
                    if (countdown > 0) {
                        Text("${countdown}s", style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.5f))
                    } else {
                        TextButton(onClick = onRetry, colors = ButtonDefaults.textButtonColors(contentColor = MaiAccent)) {
                            Text("Coba lagi", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        }
        ConnectionStatus.QUOTA_LIMIT -> {
            Surface(color = Color(0xFF2C1810), modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier              = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(Icons.Default.CloudOff, null, tint = Color(0xFFFF9500), modifier = Modifier.size(20.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Mai sedang istirahat", style = MaterialTheme.typography.labelMedium, color = Color.White, fontWeight = FontWeight.SemiBold)
                        Text("Batas kuota tercapai", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.6f))
                    }
                    if (countdown > 0) {
                        Text("${countdown}s", style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.5f))
                    } else {
                        TextButton(onClick = onRetry, colors = ButtonDefaults.textButtonColors(contentColor = MaiAccent)) {
                            Text("Coba lagi", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                    TextButton(onClick = onBotMode, colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFF30D158))) {
                        Text("Pakai Bot", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
        ConnectionStatus.BOT_MODE -> {
            Surface(color = Color(0xFF1A2A1A), modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier              = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(Icons.Default.SmartToy, null, tint = Color(0xFF30D158), modifier = Modifier.size(18.dp))
                    Text("Mode Bot Aktif · Ketik help untuk perintah", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.7f), modifier = Modifier.weight(1f))
                }
            }
        }
        ConnectionStatus.CONNECTED -> {}
    }
}

// ── User Bubble ───────────────────────────────────────────────────────────────
@Composable
internal fun UserMessageBubble(text: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Surface(
            shape    = RoundedCornerShape(topStart = 18.dp, topEnd = 4.dp, bottomStart = 18.dp, bottomEnd = 18.dp),
            color    = MaterialTheme.colorScheme.primary,
            modifier = Modifier.widthIn(max = 300.dp)
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

// ── AI Bubble ─────────────────────────────────────────────────────────────────
@Composable
internal fun AiMessageBubble(
    text: String,
    option: ChatOption?,
    isError: Boolean,
    pendingTransaction: PendingTransaction?,
    onOptionSelected: (String) -> Unit,
    onConfirmTransaction: () -> Unit,
    onCancelTransaction: () -> Unit
) {
    Row(
        modifier              = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment     = Alignment.Top
    ) {
        // Mini Mai avatar
        Box(
            modifier = Modifier
                .padding(top = 2.dp)
                .size(28.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(MaiPurple, MaiPurpleDk))),
            contentAlignment = Alignment.Center
        ) {
            Text("舞", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }

        Spacer(Modifier.width(8.dp))

        Column(
            modifier              = Modifier.weight(1f),
            horizontalAlignment   = Alignment.Start,
            verticalArrangement   = Arrangement.spacedBy(6.dp)
        ) {
            if (text.isNotBlank()) {
                Surface(
                    shape    = RoundedCornerShape(topStart = 4.dp, topEnd = 18.dp, bottomStart = 18.dp, bottomEnd = 18.dp),
                    color    = if (isError) MaterialTheme.colorScheme.errorContainer
                              else MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.widthIn(max = 300.dp)
                ) {
                    Text(
                        text     = parseMarkdown(text),
                        style    = MaterialTheme.typography.bodyMedium,
                        color    = if (isError) MaterialTheme.colorScheme.onErrorContainer
                                  else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp).fillMaxWidth()
                    )
                }
            }
            when (option) {
                is ChatOption.CategoryOptions    -> OptionChips(option.options, Icons.Default.Category, onOptionSelected)
                is ChatOption.WalletOptions      -> OptionChips(option.options, Icons.Default.AccountBalanceWallet, onOptionSelected)
                is ChatOption.TransactionConfirm -> TransactionConfirmCard(option, pendingTransaction, onConfirmTransaction, onCancelTransaction)
                is ChatOption.YesNo -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { onOptionSelected("Tidak") }) { Text("Tidak") }
                    Button(
                        onClick = { onOptionSelected("Ya") },
                        colors  = ButtonDefaults.buttonColors(containerColor = MaiPurple)
                    ) { Text("Ya") }
                }
                null -> {}
            }
        }
    }
}

@Composable
private fun OptionChips(
    options: List<String>,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onSelect: (String) -> Unit
) {
    Row(
        modifier              = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        options.forEach { opt ->
            InputChip(
                selected  = false,
                onClick   = { onSelect(opt) },
                label     = { Text(opt) },
                leadingIcon = { Icon(icon, null, Modifier.size(16.dp)) },
                colors    = InputChipDefaults.inputChipColors(
                    labelColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    }
}

@Composable
private fun TransactionConfirmCard(
    confirm: ChatOption.TransactionConfirm,
    pendingTransaction: PendingTransaction?,
    onConfirm: () -> Unit,
    onCancel: () -> Unit
) {
    val fmt       = NumberFormat.getNumberInstance(Locale("id", "ID"))
    val typeLabel = when (confirm.type) { "INCOME" -> "Pemasukan"; "TRANSFER" -> "Transfer"; else -> "Pengeluaran" }
    val typeColor = when (confirm.type) { "INCOME" -> Color(0xFF1B8A4C); "TRANSFER" -> MaterialTheme.colorScheme.primary; else -> MaterialTheme.colorScheme.error }

    val displayAmount   = pendingTransaction?.amount       ?: confirm.amount
    val displayCategory = pendingTransaction?.categoryName ?: confirm.category
    val displayWallet   = pendingTransaction?.walletName   ?: confirm.wallet
    val displayTitle    = pendingTransaction?.desc?.takeIf { it.isNotBlank() } ?: confirm.title.takeIf { it.isNotBlank() }
    val canSave         = pendingTransaction != null && displayAmount > 0

    Card(
        modifier = Modifier.widthIn(max = 300.dp),
        border   = BorderStroke(1.dp, MaiPurple.copy(alpha = 0.3f)),
        colors   = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Receipt, null, tint = typeColor, modifier = Modifier.size(20.dp))
                Text("Konfirmasi $typeLabel", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            }
            HorizontalDivider(color = MaiPurple.copy(alpha = 0.15f))
            displayTitle?.let { DetailRow("Judul", it) }
            DetailRow("Nominal", "Rp ${fmt.format(displayAmount)}")
            DetailRow("Kategori", displayCategory)
            DetailRow("Dompet", displayWallet.ifBlank { "—" })
            HorizontalDivider(color = MaiPurple.copy(alpha = 0.15f))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick  = onCancel,
                    modifier = Modifier.weight(1f),
                    border   = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                ) { Text("Batal") }
                Button(
                    onClick  = onConfirm,
                    enabled  = canSave,
                    modifier = Modifier.weight(1f),
                    colors   = ButtonDefaults.buttonColors(containerColor = typeColor)
                ) { Text("Simpan") }
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
    }
}

// ── Typing Indicator ──────────────────────────────────────────────────────────
@Composable
internal fun TypingIndicatorBubble() {
    val infiniteTransition = rememberInfiniteTransition(label = "typing")
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
        // Mini Mai avatar
        Box(
            modifier = Modifier
                .padding(top = 2.dp)
                .size(28.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(MaiPurple, MaiPurpleDk))),
            contentAlignment = Alignment.Center
        ) {
            Text("舞", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(8.dp))
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
                        Modifier
                            .size(8.dp)
                            .offset(y = offsetY.dp)
                            .clip(CircleShape)
                            .background(MaiPurple.copy(alpha = 0.6f))
                    )
                }
            }
        }
    }
}

// ── Welcome State ─────────────────────────────────────────────────────────────
@Composable
internal fun ChatWelcomeState(accountName: String?, onQuickAction: (String) -> Unit) {
    val hour = LocalTime.now().hour
    val greeting = when {
        hour < 10  -> "Ohayou"
        hour < 14  -> "Konichiwa"
        hour < 18  -> "Konichiwa"
        hour < 21  -> "Konbanwa"
        else       -> "Mada okiteru no?"
    }
    val subGreeting = when {
        hour < 10  -> "Pagi-pagi sudah buka app keuangan... rajin juga."
        hour < 14  -> "Mau cek keuangan? ...bukan berarti aku peduli, ya."
        hour < 18  -> "*membalik rambut* Ada yang mau kau tanyakan?"
        hour < 21  -> "Malam ini mau review pengeluaran?"
        else       -> "*menghela napas* Sudah malam. Cepat selesaikan urusanmu."
    }

    val quickActions = listOf(
        "💰 Cek saldo"               to "saldo",
        "📊 Rangkuman bulan ini"      to "rangkuman",
        "🤔 Aku boros nggak?"         to "Mai, aku boros nggak bulan ini?",
        "💡 Tips hemat"               to "Mai, kasih tips hemat dong"
    )

    Column(
        modifier            = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Mai avatar
        Box(
            modifier = Modifier
                .size(88.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(MaiPurple, MaiPurpleDk))),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "舞",
                style      = MaterialTheme.typography.displaySmall,
                color      = Color.White,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(Modifier.height(20.dp))

        Text(
            "$greeting.",
            style      = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign  = TextAlign.Center,
            color      = MaterialTheme.colorScheme.onBackground
        )

        Spacer(Modifier.height(4.dp))

        Text(
            subGreeting,
            style     = MaterialTheme.typography.bodyMedium,
            color     = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        if (accountName != null) {
            Spacer(Modifier.height(4.dp))
            Text(
                "Akun: $accountName",
                style = MaterialTheme.typography.labelSmall,
                color = MaiPurple.copy(alpha = 0.7f),
                textAlign = TextAlign.Center
            )
        }

        Spacer(Modifier.height(36.dp))

        Text(
            "Mau apa?",
            style      = MaterialTheme.typography.labelLarge,
            color      = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Medium
        )

        Spacer(Modifier.height(12.dp))

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            quickActions.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { (label, command) ->
                        SuggestionChip(
                            onClick  = { onQuickAction(command) },
                            label    = { Text(label, style = MaterialTheme.typography.labelMedium) },
                            modifier = Modifier.weight(1f),
                            border   = SuggestionChipDefaults.suggestionChipBorder(
                                enabled      = true,
                                borderColor  = MaiPurple.copy(alpha = 0.3f),
                                borderWidth  = 1.dp
                            )
                        )
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

// ── Clear Chat Confirmation Dialog ────────────────────────────────────────────
@Composable
internal fun ClearChatDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.DeleteSweep, null, tint = MaiPurple) },
        title = { Text("Hapus Semua Chat?") },
        text = {
            Text(
                "*menatap tajam* Yakin mau hapus semua percakapan kita? " +
                "Aku tidak akan mengulang apa yang sudah kukatakan.",
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors  = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) { Text("Hapus") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Batal") }
        }
    )
}

// ── Markdown Parser ───────────────────────────────────────────────────────────
internal fun parseMarkdown(text: String) = buildAnnotatedString {
    val lines = text.split("\n")
    lines.forEachIndexed { lineIndex, line ->
        when {
            line.startsWith("## ") -> withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = 16.sp)) { append(line.removePrefix("## ")) }
            line.startsWith("# ")  -> withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = 18.sp)) { append(line.removePrefix("# ")) }
            line.startsWith("* ") || line.startsWith("- ") -> append("• ${line.drop(2)}")
            line.startsWith("  * ") || line.startsWith("  - ") -> append("    ◦ ${line.drop(4)}")
            else -> {
                var remaining = line
                while (remaining.isNotEmpty()) {
                    when {
                        remaining.startsWith("**") -> {
                            val end = remaining.indexOf("**", 2)
                            if (end != -1) { withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(remaining.substring(2, end)) }; remaining = remaining.substring(end + 2) }
                            else { append(remaining); remaining = "" }
                        }
                        remaining.startsWith("*") -> {
                            val end = remaining.indexOf("*", 1)
                            if (end != -1) { withStyle(SpanStyle(fontStyle = FontStyle.Italic, color = MaiPurple.copy(alpha = 0.7f))) { append(remaining.substring(1, end)) }; remaining = remaining.substring(end + 1) }
                            else { append(remaining); remaining = "" }
                        }
                        else -> {
                            val nextSpecial = minOf(
                                remaining.indexOf("**").takeIf { it != -1 } ?: remaining.length,
                                remaining.indexOf("*").takeIf { it != -1 } ?: remaining.length
                            )
                            append(remaining.substring(0, nextSpecial)); remaining = remaining.substring(nextSpecial)
                        }
                    }
                }
            }
        }
        if (lineIndex < lines.lastIndex) append("\n")
    }
}
