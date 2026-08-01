package com.sndiy.chatfin.feature.finance.category.ui

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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
import com.sndiy.chatfin.core.ui.animation.StaggeredEntrance
import com.sndiy.chatfin.core.ui.animation.pressScale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryScreen(
    onNavigateBack: () -> Unit,
    viewModel: CategoryViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Pengeluaran", "Pemasukan")
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Kategori") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Kembali")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.showAddDialog() }) {
                        Icon(Icons.Default.Add, contentDescription = "Tambah")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            TabRow(selectedTabIndex = selectedTab) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick  = {
                            selectedTab = index
                            viewModel.onTabChange(if (index == 0) "EXPENSE" else "INCOME")
                        },
                        text = { Text(title) }
                    )
                }
            }

            if (uiState.isLoading && uiState.categories.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (uiState.categories.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Category, null, modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Belum ada kategori", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        TextButton(onClick = { viewModel.showAddDialog() }) { Text("Tambah Kategori") }
                    }
                }
            } else {
                LazyColumn(
                    contentPadding      = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(uiState.categories, key = { _, it -> it.id }) { idx, category ->
                        StaggeredEntrance(index = idx) {
                            CategoryItem(
                                name      = category.name,
                                colorHex  = category.colorHex,
                                isCustom  = category.isCustom,
                                onEdit    = { viewModel.showEditDialog(category) },
                                onDelete  = { viewModel.deleteCategory(category) }
                            )
                        }
                    }
                }
            }
        }
    }

    // Dialog tambah/edit
    if (uiState.showDialog) {
        CategoryDialog(
            formState   = uiState.formState,
            isEdit      = uiState.editingCategory != null,
            onNameChange  = viewModel::onNameChange,
            onColorChange = viewModel::onColorChange,
            onDismiss   = viewModel::hideDialog,
            onSave      = viewModel::saveCategory
        )
    }

    // Snackbar error
    uiState.errorMessage?.let { msg ->
        LaunchedEffect(msg) {
            viewModel.clearError()
        }
    }
}

@Composable
private fun CategoryItem(
    name: String,
    colorHex: String,
    isCustom: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val color = runCatching { Color(android.graphics.Color.parseColor(colorHex)) }
        .getOrElse { MaterialTheme.colorScheme.primary }

    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier              = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier         = Modifier.size(40.dp).clip(CircleShape).background(color),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    name.take(1).uppercase(),
                    color      = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }
            Column(Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.bodyLarge)
                Text(
                    if (isCustom) "Kategori kustom" else "Kategori default",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (isCustom) {
                Row {
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete, contentDescription = "Hapus",
                            tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryDialog(
    formState: CategoryFormState,
    isEdit: Boolean,
    onNameChange: (String) -> Unit,
    onColorChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit
) {
    val colorOptions = listOf(
        "#E53935", "#F4511E", "#F9A825", "#1B8A4C", "#2E7D32",
        "#0061A4", "#1E88E5", "#3949AB", "#8E24AA", "#C2185B",
        "#00695C", "#00ACC1", "#558B2F", "#757575", "#546E7A"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isEdit) "Edit Kategori" else "Tambah Kategori") },
        text  = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(
                    value         = formState.name,
                    onValueChange = onNameChange,
                    label         = { Text("Nama Kategori") },
                    isError       = formState.nameError != null,
                    supportingText = formState.nameError?.let { { Text(it) } },
                    singleLine    = true,
                    modifier      = Modifier.fillMaxWidth()
                )

                Text("Warna", style = MaterialTheme.typography.labelMedium)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(colorOptions) { hex ->
                        val color    = Color(android.graphics.Color.parseColor(hex))
                        val selected = hex == formState.colorHex
                        val interactionSource = remember { MutableInteractionSource() }
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .pressScale(interactionSource)
                                .clip(CircleShape)
                                .background(color)
                                .then(
                                    if (selected) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                                    else Modifier
                                )
                                .clickable(
                                    interactionSource = interactionSource,
                                    indication         = LocalIndication.current
                                ) { onColorChange(hex) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onSave, enabled = formState.name.isNotBlank()) {
                Text(if (isEdit) "Simpan" else "Tambah")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Batal") }
        }
    )
}