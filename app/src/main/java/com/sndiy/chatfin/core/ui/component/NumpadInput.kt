package com.sndiy.chatfin.core.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Numpad visual tap-only untuk input nominal — tanpa keyboard sistem.
 *
 * @param rawDigits   String digit murni (tanpa separator), contoh: "25000"
 * @param onDigit     Dipanggil saat user tap angka
 * @param onBackspace Dipanggil saat user tap backspace
 * @param onClear     Dipanggil saat user long-press backspace (hapus semua)
 * @param buttonSize  Ukuran tombol keypad
 */
@Composable
fun NumpadKeyboard(
    rawDigits: String,
    onDigit: (String) -> Unit,
    onBackspace: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
    buttonSize: Dp = 72.dp
) {
    val keys = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        listOf("000", "0", "⌫")
    )

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        keys.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                row.forEach { key ->
                    when (key) {
                        "⌫" -> NumpadBackspaceKey(
                            size = buttonSize,
                            onTap = onBackspace,
                            onLongPress = onClear
                        )
                        else -> NumpadDigitKey(
                            label = key,
                            size = buttonSize,
                            onClick = {
                                // Jangan izinkan '000' jika string masih kosong
                                if (key == "000" && rawDigits.isBlank()) return@NumpadDigitKey
                                // Jangan izinkan leading zeros
                                if (rawDigits == "0" && key == "0") return@NumpadDigitKey
                                onDigit(key)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NumpadDigitKey(
    label: String,
    size: Dp,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun NumpadBackspaceKey(
    size: Dp,
    onTap: () -> Unit,
    onLongPress: () -> Unit
) {
    // Menggunakan kombinasi clickable + ExperimentalFoundationApi untuk long press
    // Fallback: gunakan tombol biasa dan tombol "AC" terpisah
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f))
            .clickable(onClick = onTap),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.Backspace,
            contentDescription = "Hapus",
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(26.dp)
        )
    }
}

/**
 * Preview nominal besar yang diformat Rupiah, ditampilkan di atas numpad.
 */
@Composable
fun NumpadAmountDisplay(
    formattedAmount: String,
    currencyPrefix: String = "Rp",
    error: String? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(16.dp)
            )
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = currencyPrefix,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = formattedAmount.ifBlank { "0" },
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
            color = if (error != null) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            maxLines = 1,
            fontSize = when {
                formattedAmount.length > 12 -> 28.sp
                formattedAmount.length > 9  -> 34.sp
                else                        -> 40.sp
            }
        )
        if (error != null) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = error,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

/**
 * Preset nominal chip — tap untuk mengisi langsung.
 */
@Composable
fun NumpadPresetChips(
    presets: List<Long>,
    currentRaw: String,
    onSelect: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        presets.forEach { preset ->
            val isSelected = currentRaw == preset.toString()
            val label = when {
                preset >= 1_000_000 -> "${preset / 1_000_000}jt"
                preset >= 100_000   -> "${preset / 1_000}rb"
                preset >= 10_000    -> "${preset / 1_000}rb"
                else                -> "${preset / 1_000}k"
            }
            FilterChip(
                selected = isSelected,
                onClick = { onSelect(preset) },
                label = { Text(label, style = MaterialTheme.typography.labelMedium) }
            )
        }
    }
}
