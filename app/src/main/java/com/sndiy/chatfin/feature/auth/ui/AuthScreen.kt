package com.sndiy.chatfin.feature.auth.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun AuthScreen(
    onAuthSuccess: () -> Unit,
    onSkip: () -> Unit,
    viewModel: AuthViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showPassword        by remember { mutableStateOf(false) }
    var showConfirmPassword by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.authState) {
        if (uiState.authState is AuthState.Success) onAuthSuccess()
    }

    Column(
        modifier            = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Spacer(Modifier.height(32.dp))

        // ── Header ────────────────────────────────────────────────────────────
        Text("💬", style = MaterialTheme.typography.displayMedium)
        Text(
            "ChatFin",
            style      = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color      = MaterialTheme.colorScheme.primary
        )
        Text(
            if (uiState.isRegisterMode) "Buat akun baru" else "Masuk ke akunmu",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(8.dp))

        // ── Form ──────────────────────────────────────────────────────────────
        OutlinedTextField(
            value         = uiState.email,
            onValueChange = viewModel::onEmailChange,
            label         = { Text("Email") },
            leadingIcon   = { Icon(Icons.Default.Email, null) },
            isError       = uiState.emailError != null,
            supportingText = uiState.emailError?.let { { Text(it) } },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email,
                imeAction    = ImeAction.Next
            ),
            singleLine = true,
            modifier   = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value         = uiState.password,
            onValueChange = viewModel::onPasswordChange,
            label         = { Text("Password") },
            leadingIcon   = { Icon(Icons.Default.Lock, null) },
            trailingIcon  = {
                IconButton(onClick = { showPassword = !showPassword }) {
                    Icon(
                        if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        null
                    )
                }
            },
            visualTransformation = if (showPassword) VisualTransformation.None
            else PasswordVisualTransformation(),
            isError        = uiState.passwordError != null,
            supportingText = uiState.passwordError?.let { { Text(it) } },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction    = ImeAction.Done
            ),
            singleLine = true,
            modifier   = Modifier.fillMaxWidth()
        )

        if (uiState.isRegisterMode) {
            OutlinedTextField(
                value         = uiState.confirmPassword,
                onValueChange = viewModel::onConfirmPasswordChange,
                label         = { Text("Konfirmasi Password") },
                leadingIcon   = { Icon(Icons.Default.Lock, null) },
                trailingIcon  = {
                    IconButton(onClick = { showConfirmPassword = !showConfirmPassword }) {
                        Icon(
                            if (showConfirmPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            null
                        )
                    }
                },
                visualTransformation = if (showConfirmPassword) VisualTransformation.None
                else PasswordVisualTransformation(),
                singleLine = true,
                modifier   = Modifier.fillMaxWidth()
            )
        }

        // ── Error message ─────────────────────────────────────────────────────
        if (uiState.authState is AuthState.Error) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Text(
                    (uiState.authState as AuthState.Error).message,
                    style    = MaterialTheme.typography.bodySmall,
                    color    = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }

        // ── Tombol utama ──────────────────────────────────────────────────────
        Button(
            onClick  = {
                if (uiState.isRegisterMode) viewModel.registerWithEmail()
                else viewModel.loginWithEmail()
            },
            enabled  = uiState.authState !is AuthState.Loading,
            modifier = Modifier.fillMaxWidth().height(50.dp)
        ) {
            if (uiState.authState is AuthState.Loading) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
            } else {
                Text(if (uiState.isRegisterMode) "Daftar" else "Masuk")
            }
        }

        // ── Lupa password ─────────────────────────────────────────────────────
        if (!uiState.isRegisterMode) {
            TextButton(onClick = viewModel::sendPasswordReset) {
                Text("Lupa password?")
            }
        }

        // ── Toggle mode ───────────────────────────────────────────────────────
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (uiState.isRegisterMode) "Sudah punya akun?" else "Belum punya akun?",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            TextButton(onClick = viewModel::toggleMode) {
                Text(if (uiState.isRegisterMode) "Masuk" else "Daftar")
            }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        // ── Skip ──────────────────────────────────────────────────────────────
        OutlinedButton(
            onClick  = onSkip,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.PersonOutline, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Lewati, pakai tanpa akun")
        }

        Text(
            "Data tidak akan tersinkronisasi ke cloud jika tidak login",
            style     = MaterialTheme.typography.labelSmall,
            color     = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(16.dp))
    }
}