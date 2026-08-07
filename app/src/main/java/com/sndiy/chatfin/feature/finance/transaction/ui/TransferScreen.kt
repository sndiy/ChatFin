// app/src/main/java/com/sndiy/chatfin/feature/finance/transaction/ui/TransferScreen.kt

package com.sndiy.chatfin.feature.finance.transaction.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sndiy.chatfin.core.data.local.entity.WalletEntity
import com.sndiy.chatfin.core.ui.component.NumpadAmountDisplay
import com.sndiy.chatfin.core.ui.component.NumpadKeyboard
import com.sndiy.chatfin.core.ui.component.NumpadPresetChips
import com.sndiy.chatfin.core.ui.util.formatRupiah

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransferScreen(
    onNavigateBack: () -> Unit,
    viewModel: TransferViewModel = hiltViewModel()
) {
    val formState by viewModel.formState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(formState.isSaved) {
        if (formState.isSaved) onNavigateBack()
    }

    LaunchedEffect(formState.errorMessage) {
        formState.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Transfer Saldo") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Kembali")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {

            val formattedAmt = remember(formState.amount) {
                formState.amount.toLongOrNull()?.takeIf { it > 0 }?.formatRupiah() ?: ""
            }
            NumpadAmountDisplay(
                formattedAmount = formattedAmt,
                currencyPrefix  = "Rp",
                error           = formState.amountError,
                modifier        = Modifier.padding(horizontal = 16.dp)
            )

            Row(modifier = Modifier.padding(horizontal = 16.dp)) {
                NumpadPresetChips(
                    presets    = listOf(10_000L, 20_000L, 50_000L, 100_000L, 200_000L, 500_000L),
                    currentRaw = formState.amount,
                    onSelect   = { viewModel.onAmountChange(it.toString()) }
                )
            }

            NumpadKeyboard(
                rawDigits   = formState.amount,
                onDigit     = { key ->
                    val current = formState.amount
                    val next = (current + key).trimStart('0').take(12).ifBlank { key }
                    viewModel.onAmountChange(next)
                },
                onBackspace = {
                    if (formState.amount.isNotEmpty()) viewModel.onAmountChange(formState.amount.dropLast(1))
                },
                onClear     = { viewModel.onAmountChange("") },
                buttonSize  = 68.dp,
                modifier    = Modifier.padding(horizontal = 8.dp)
            )

            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {

                SectionLabel("Dari Dompet")
                TransferWalletSelector(
                    wallets        = formState.wallets,
                    selectedWallet = formState.sourceWallet,
                    error          = formState.sourceError,
                    onSelect       = viewModel::onSourceSelect
                )

                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.ArrowDownward,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                SectionLabel("Ke Dompet")
                TransferWalletSelector(
                    wallets        = formState.wallets.filter { it.id != formState.sourceWallet?.id },
                    selectedWallet = formState.destWallet,
                    error          = formState.destError,
                    onSelect       = viewModel::onDestSelect
                )

                SectionLabel("Catatan (opsional)")
                OutlinedTextField(
                    value         = formState.note,
                    onValueChange = viewModel::onNoteChange,
                    label         = { Text("Catatan") },
                    leadingIcon   = { Icon(Icons.Default.Edit, null) },
                    trailingIcon  = if (formState.note.isNotEmpty()) {
                        { IconButton(onClick = { viewModel.onNoteChange("") }) { Icon(Icons.Default.Clear, null) } }
                    } else null,
                    maxLines      = 2,
                    modifier      = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick  = viewModel::requestConfirm,
                    enabled  = !formState.isLoading,
                    modifier = Modifier.fillMaxWidth().height(52.dp)
                ) {
                    Text("Lanjutkan", fontWeight = FontWeight.SemiBold)
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    if (formState.showConfirm && formState.sourceWallet != null && formState.destWallet != null) {
        TransferConfirmDialog(
            source        = formState.sourceWallet!!,
            dest          = formState.destWallet!!,
            amount        = formState.amount.toLongOrNull() ?: 0L,
            note          = formState.note,
            isLoading     = formState.isLoading,
            onConfirm     = viewModel::confirmTransfer,
            onDismiss     = viewModel::dismissConfirm
        )
    }
}

@Composable
private fun TransferWalletSelector(
    wallets: List<WalletEntity>,
    selectedWallet: WalletEntity?,
    error: String?,
    onSelect: (WalletEntity) -> Unit
) {
    if (error != null) {
        Text(text = error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
    }

    Row(
        modifier              = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        wallets.forEach { wallet ->
            val isSelected = wallet.id == selectedWallet?.id
            val color = runCatching {
                Color(android.graphics.Color.parseColor(wallet.colorHex))
            }.getOrElse { MaterialTheme.colorScheme.primary }

            FilterChip(
                selected  = isSelected,
                onClick   = { onSelect(wallet) },
                label     = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(wallet.name, style = MaterialTheme.typography.labelMedium)
                        Text(
                            text  = "Rp ${wallet.balance.formatRupiah()}",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                },
                leadingIcon = {
                    Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(color))
                }
            )
        }
    }
}

@Composable
private fun TransferConfirmDialog(
    source: WalletEntity,
    dest: WalletEntity,
    amount: Long,
    note: String,
    isLoading: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Konfirmasi Transfer") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Dari: ${source.name}")
                Text("Ke: ${dest.name}")
                Text(
                    "Nominal: Rp ${amount.formatRupiah()}",
                    fontWeight = FontWeight.Bold
                )
                if (note.isNotBlank()) Text("Catatan: $note")
            }
        },
        confirmButton = {
            Button(onClick = onConfirm, enabled = !isLoading) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier    = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color       = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text("Konfirmasi Transfer")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isLoading) { Text("Batal") }
        }
    )
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text  = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}
