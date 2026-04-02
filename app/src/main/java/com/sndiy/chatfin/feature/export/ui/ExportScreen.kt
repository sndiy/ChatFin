// app/src/main/java/com/sndiy/chatfin/feature/export/ui/ExportScreen.kt

package com.sndiy.chatfin.feature.export.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportScreen(
    onNavigateBack: () -> Unit,
    viewModel: ExportViewModel = hiltViewModel()
) {
    val uiState           by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.successMessage, uiState.errorMessage) {
        uiState.successMessage?.let { snackbarHostState.showSnackbar(it); viewModel.clearMessages() }
        uiState.errorMessage?.let   { snackbarHostState.showSnackbar(it); viewModel.clearMessages() }
    }

    val csvLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri -> uri?.let { viewModel.exportCsv(it) } }

    val pdfLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri -> uri?.let { viewModel.exportPdf(it) } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Export Laporan", fontWeight = FontWeight.Bold) },
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // ── Header illustration ──────────────────────────────────────
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors   = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(24.dp).fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Default.Description, null,
                        modifier = Modifier.size(40.dp),
                        tint     = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        "Download Laporan Keuangan",
                        style      = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        textAlign  = TextAlign.Center
                    )
                    Text(
                        "Pilih rentang waktu, lalu export ke format yang kamu butuhkan",
                        style     = MaterialTheme.typography.bodySmall,
                        color     = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }

            // ── Periode ──────────────────────────────────────────────────
            Text(
                "Rentang Waktu",
                style      = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )

            val periods = ExportPeriod.entries
            val rows = periods.chunked(3)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                rows.forEach { row ->
                    Row(
                        modifier              = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        row.forEach { period ->
                            val isSelected = uiState.selectedPeriod == period
                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { viewModel.selectPeriod(period) }
                                    .then(
                                        if (isSelected) Modifier.border(
                                            1.5.dp,
                                            MaterialTheme.colorScheme.primary,
                                            RoundedCornerShape(12.dp)
                                        ) else Modifier
                                    ),
                                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(
                                    period.label,
                                    modifier   = Modifier.padding(vertical = 12.dp, horizontal = 8.dp).fillMaxWidth(),
                                    textAlign  = TextAlign.Center,
                                    style      = MaterialTheme.typography.labelMedium,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color      = if (isSelected) MaterialTheme.colorScheme.primary
                                                 else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        // Pad remaining
                        repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            // ── Format cards ─────────────────────────────────────────────
            Text(
                "Pilih Format",
                style      = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )

            // CSV card
            ExportFormatCard(
                icon        = Icons.Default.TableChart,
                iconTint    = Color(0xFF1B8A4C),
                title       = "CSV",
                subtitle    = "Spreadsheet",
                description = "Bisa dibuka di Excel, Google Sheets, atau Numbers. Cocok untuk analisis data lanjutan.",
                isExporting = uiState.isExporting,
                onClick     = { csvLauncher.launch(viewModel.generateFileName("CSV")) }
            )

            // PDF card
            ExportFormatCard(
                icon        = Icons.Default.PictureAsPdf,
                iconTint    = Color(0xFFBA1A1A),
                title       = "PDF",
                subtitle    = "Dokumen",
                description = "Laporan rapi siap cetak atau kirim. Berisi ringkasan keuangan dan tabel transaksi lengkap.",
                isExporting = uiState.isExporting,
                onClick     = { pdfLauncher.launch(viewModel.generateFileName("PDF")) }
            )

            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun ExportFormatCard(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String,
    description: String,
    isExporting: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().animateContentSize(),
        colors   = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border   = CardDefaults.outlinedCardBorder()
    ) {
        Row(
            modifier              = Modifier.padding(20.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment     = Alignment.Top
        ) {
            // Icon badge
            Box(
                modifier         = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(iconTint.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = iconTint, modifier = Modifier.size(24.dp))
            }

            Column(
                modifier            = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            subtitle,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            style    = MaterialTheme.typography.labelSmall,
                            color    = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick  = onClick,
                    enabled  = !isExporting,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isExporting) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                        Spacer(Modifier.width(8.dp))
                        Text("Mengekspor...")
                    } else {
                        Icon(Icons.Default.FileDownload, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Export $title")
                    }
                }
            }
        }
    }
}
