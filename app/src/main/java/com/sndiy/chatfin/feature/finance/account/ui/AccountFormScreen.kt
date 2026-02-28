// app/src/main/java/com/sndiy/chatfin/feature/finance/account/ui/AccountFormScreen.kt

package com.sndiy.chatfin.feature.finance.account.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import com.sndiy.chatfin.core.ui.theme.AccountColors
import com.sndiy.chatfin.core.ui.theme.ChatFinSpacing

// Pilihan warna akun
private val accountColorOptions = listOf(
    "#0061A4", "#006874", "#1B8A4C", "#7B3294",
    "#BF5B00", "#BA1A1A", "#005FAD", "#9A4521"
)

// Pilihan mata uang
private val currencyOptions = listOf("IDR", "USD", "EUR", "SGD", "MYR", "JPY")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountFormScreen(
    accountId: String = "new",
    onNavigateBack: () -> Unit,
    viewModel: AccountViewModel = hiltViewModel()
) {
    val formState by viewModel.formState.collectAsStateWithLifecycle()
    val isEditMode = accountId != "new"

    // Load data jika mode edit
    LaunchedEffect(accountId) {
        if (isEditMode) {
            viewModel.loadAccountForEdit(accountId)
        } else {
            viewModel.resetForm()
        }
    }

    // Navigasi kembali setelah berhasil simpan
    LaunchedEffect(formState.isSaved) {
        if (formState.isSaved) onNavigateBack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(if (isEditMode) "Edit Akun" else "Buat Akun Baru")
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector        = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Kembali"
                        )
                    }
                },
                actions = {
                    // Tombol simpan di kanan atas
                    TextButton(
                        onClick  = viewModel::saveAccount,
                        enabled  = !formState.isLoading
                    ) {
                        if (formState.isLoading) {
                            CircularProgressIndicator(
                                modifier  = Modifier.size(16.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text(
                                text       = "Simpan",
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            )
        }
    ) { paddingValues ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(ChatFinSpacing.md),
            verticalArrangement = Arrangement.spacedBy(ChatFinSpacing.md)
        ) {

            // ── Preview Avatar Akun ───────────────────────────────────────────
            AccountPreviewCard(
                name     = formState.name.ifBlank { "?" },
                colorHex = formState.colorHex
            )

            // ── Nama Akun ─────────────────────────────────────────────────────
            OutlinedTextField(
                value         = formState.name,
                onValueChange = viewModel::onNameChange,
                label         = { Text("Nama Akun") },
                placeholder   = { Text("Contoh: Pribadi, Bisnis, Keluarga") },
                isError       = formState.nameError != null,
                supportingText = formState.nameError?.let {
                    { Text(text = it, color = MaterialTheme.colorScheme.error) }
                },
                singleLine    = true,
                modifier      = Modifier.fillMaxWidth()
            )

            // ── Deskripsi ─────────────────────────────────────────────────────
            OutlinedTextField(
                value         = formState.description,
                onValueChange = viewModel::onDescriptionChange,
                label         = { Text("Deskripsi (opsional)") },
                placeholder   = { Text("Contoh: Untuk kebutuhan sehari-hari") },
                maxLines      = 3,
                modifier      = Modifier.fillMaxWidth()
            )

            // ── Pilih Warna ───────────────────────────────────────────────────
            SectionTitle(title = "Warna Akun")
            ColorPicker(
                selectedColorHex = formState.colorHex,
                onColorSelected  = viewModel::onColorChange
            )

            // ── Pilih Mata Uang ───────────────────────────────────────────────
            SectionTitle(title = "Mata Uang")
            CurrencyPicker(
                selectedCurrency = formState.currency,
                onCurrencySelected = viewModel::onCurrencyChange
            )

            Spacer(modifier = Modifier.height(ChatFinSpacing.xl))
        }
    }
}

// ── Preview card: tampilkan avatar sebelum disimpan ───────────────────────────
@Composable
private fun AccountPreviewCard(name: String, colorHex: String) {
    val color = runCatching {
        Color(android.graphics.Color.parseColor(colorHex))
    }.getOrElse { MaterialTheme.colorScheme.primary }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors   = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier         = Modifier.padding(ChatFinSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(ChatFinSpacing.md)
        ) {
            Box(
                modifier         = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(color),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text  = name.take(1).uppercase(),
                    style = MaterialTheme.typography.headlineMedium,
                    color = Color.White
                )
            }
            Column {
                Text(
                    text  = name.ifBlank { "Nama Akun" },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text  = "Preview tampilan akun",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// ── Pilih warna dari grid ─────────────────────────────────────────────────────
@Composable
private fun ColorPicker(
    selectedColorHex: String,
    onColorSelected: (String) -> Unit
) {
    LazyVerticalGrid(
        columns             = GridCells.Fixed(8),
        modifier            = Modifier.height(56.dp),
        horizontalArrangement = Arrangement.spacedBy(ChatFinSpacing.sm),
        userScrollEnabled   = false
    ) {
        items(accountColorOptions) { hex ->
            val color = runCatching {
                Color(android.graphics.Color.parseColor(hex))
            }.getOrElse { Color.Gray }

            val isSelected = hex == selectedColorHex

            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(color)
                    .then(
                        if (isSelected) Modifier.border(
                            width = 3.dp,
                            color = MaterialTheme.colorScheme.onSurface,
                            shape = CircleShape
                        ) else Modifier
                    )
                    .clickable { onColorSelected(hex) },
                contentAlignment = Alignment.Center
            ) {
                if (isSelected) {
                    Icon(
                        imageVector        = Icons.Default.Check,
                        contentDescription = "Dipilih",
                        tint               = Color.White,
                        modifier           = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

// ── Pilih mata uang ───────────────────────────────────────────────────────────
@Composable
private fun CurrencyPicker(
    selectedCurrency: String,
    onCurrencySelected: (String) -> Unit
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(ChatFinSpacing.sm),
        modifier = Modifier.fillMaxWidth()
    ) {
        currencyOptions.forEach { currency ->
            FilterChip(
                selected  = currency == selectedCurrency,
                onClick   = { onCurrencySelected(currency) },
                label     = { Text(currency) }
            )
        }
    }
}

// ── Label section ─────────────────────────────────────────────────────────────
@Composable
private fun SectionTitle(title: String) {
    Text(
        text  = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}