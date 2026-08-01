package com.sndiy.chatfin.feature.settings.apikey

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sndiy.chatfin.R

private const val AI_STUDIO_URL = "https://aistudio.google.com/apikey"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApiKeyScreen(
    onNavigateBack: () -> Unit,
    viewModel: ApiKeyViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val uriHandler = LocalUriHandler.current
    val snackbarState = remember { SnackbarHostState() }
    var showClearConfirm by remember { mutableStateOf(false) }
    var showKeyVisible by remember { mutableStateOf(false) }

    val errorText = state.errorMessageRes?.let { stringResource(it) }
    val successText = state.successMessageRes?.let { stringResource(it) }
    LaunchedEffect(errorText, successText) {
        (errorText ?: successText)?.let {
            snackbarState.showSnackbar(it)
            viewModel.clearMessages()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.apikey_title), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarState) }
    ) { padding ->
        if (state.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ── Penjelasan singkat — prinsip utama: AI opsional ─────────────────
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        stringResource(R.string.apikey_intro_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        stringResource(R.string.apikey_intro_body),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                    )
                }
            }

            // ── Status kunci aktif saat ini ──────────────────────────────────────
            if (state.hasStoredKey) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.padding(16.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.VpnKey, null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                stringResource(R.string.apikey_current_label),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(state.maskedKey, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
                OutlinedButton(
                    onClick = { showClearConfirm = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(Icons.Default.Delete, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.apikey_clear_button))
                }
                HorizontalDivider()
            }

            // ── Panduan mendapatkan API key ───────────────────────────────────
            Text(
                stringResource(R.string.apikey_guide_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.apikey_guide_step1), style = MaterialTheme.typography.bodyMedium)
                Text(stringResource(R.string.apikey_guide_step2), style = MaterialTheme.typography.bodyMedium)
                Text(stringResource(R.string.apikey_guide_step3), style = MaterialTheme.typography.bodyMedium)
                Text(stringResource(R.string.apikey_guide_step4), style = MaterialTheme.typography.bodyMedium)
            }
            OutlinedButton(
                onClick = { uriHandler.openUri(AI_STUDIO_URL) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.AutoMirrored.Filled.OpenInNew, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.apikey_open_studio))
            }

            // ── Input & validasi ─────────────────────────────────────────────
            OutlinedTextField(
                value = state.inputText,
                onValueChange = viewModel::onInputChange,
                label = { Text(stringResource(R.string.apikey_input_label)) },
                singleLine = true,
                enabled = !state.isValidating,
                visualTransformation = if (showKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                trailingIcon = {
                    IconButton(onClick = { showKeyVisible = !showKeyVisible }) {
                        Icon(
                            if (showKeyVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = null
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )

            Button(
                onClick = viewModel::validateAndSave,
                enabled = !state.isValidating,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (state.isValidating) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.apikey_validating))
                } else {
                    Text(stringResource(R.string.apikey_validate_button))
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text(stringResource(R.string.apikey_clear_confirm_title)) },
            text = { Text(stringResource(R.string.apikey_clear_confirm_body)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearKey()
                    showClearConfirm = false
                }) {
                    Text(stringResource(R.string.apikey_clear_confirm_action), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) {
                    Text(stringResource(R.string.apikey_cancel))
                }
            }
        )
    }
}
