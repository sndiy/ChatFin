// app/src/main/java/com/sndiy/chatfin/feature/onboarding/ui/OnboardingScreen.kt

package com.sndiy.chatfin.feature.onboarding.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

private data class OnboardingPage(
    val icon: ImageVector,
    val iconColor: Color,
    val title: String,
    val subtitle: String,
    val description: String
)

private val pages = listOf(
    OnboardingPage(
        icon        = Icons.Default.AccountBalanceWallet,
        iconColor   = Color(0xFF1B8A4C),
        title       = "Catat Keuanganmu",
        subtitle    = "Semua di satu tempat",
        description = "Kelola pemasukan, pengeluaran, dan dompet digitalmu dengan mudah. Semua tercatat rapi dan otomatis."
    ),
    OnboardingPage(
        icon        = Icons.Default.AutoAwesome,
        iconColor   = Color(0xFF7E57C2),
        title       = "Mai, Asisten AI-mu",
        subtitle    = "Sakurajima Mai siap membantu",
        description = "Tanya Mai soal kondisi keuanganmu, minta analisis pengeluaran, atau sekadar ngobrol santai. Mai tahu datamu."
    ),
    OnboardingPage(
        icon        = Icons.Default.PieChart,
        iconColor   = Color(0xFF1E88E5),
        title       = "Analitik & Budget",
        subtitle    = "Kontrol penuh keuanganmu",
        description = "Lihat grafik pengeluaran, atur budget per kategori, dan terima insight otomatis. Keuanganmu jadi transparan."
    )
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(
    onComplete: (accountName: String, initialBalance: Long) -> Unit
) {
    val pagerState = rememberPagerState(pageCount = { pages.size + 1 }) // +1 untuk setup page
    val scope      = rememberCoroutineScope()

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Pager
            HorizontalPager(
                state    = pagerState,
                modifier = Modifier.weight(1f)
            ) { page ->
                if (page < pages.size) {
                    OnboardingPageContent(pages[page])
                } else {
                    SetupPageContent(onComplete = onComplete)
                }
            }

            // Bottom: dots + button
            Column(
                modifier            = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Page dots
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    repeat(pages.size + 1) { index ->
                        Box(
                            modifier = Modifier
                                .size(if (index == pagerState.currentPage) 24.dp else 8.dp, 8.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(
                                    if (index == pagerState.currentPage) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.outlineVariant
                                )
                        )
                    }
                }

                // Button
                if (pagerState.currentPage < pages.size) {
                    Row(
                        modifier              = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        if (pagerState.currentPage > 0) {
                            TextButton(onClick = {
                                scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
                            }) { Text("Kembali") }
                        } else {
                            Spacer(Modifier.width(1.dp))
                        }

                        Button(onClick = {
                            scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                        }) {
                            Text(if (pagerState.currentPage == pages.size - 1) "Mulai Setup" else "Lanjut")
                            Spacer(Modifier.width(4.dp))
                            Icon(Icons.Default.ArrowForward, null, Modifier.size(18.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OnboardingPageContent(page: OnboardingPage) {
    Column(
        modifier            = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier         = Modifier
                .size(100.dp)
                .clip(CircleShape)
                .background(page.iconColor.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                page.icon, null,
                modifier = Modifier.size(48.dp),
                tint     = page.iconColor
            )
        }

        Spacer(Modifier.height(32.dp))

        Text(
            page.title,
            style      = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign  = TextAlign.Center
        )

        Spacer(Modifier.height(8.dp))

        Text(
            page.subtitle,
            style     = MaterialTheme.typography.titleMedium,
            color     = page.iconColor,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(16.dp))

        Text(
            page.description,
            style     = MaterialTheme.typography.bodyMedium,
            color     = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun SetupPageContent(
    onComplete: (accountName: String, initialBalance: Long) -> Unit
) {
    var accountName    by remember { mutableStateOf("Keuangan Pribadi") }
    var initialBalance by remember { mutableStateOf("") }
    var walletName     by remember { mutableStateOf("Kas") }

    Column(
        modifier            = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.RocketLaunch, null,
            modifier = Modifier.size(48.dp),
            tint     = MaterialTheme.colorScheme.primary
        )

        Spacer(Modifier.height(16.dp))

        Text(
            "Satu langkah lagi!",
            style      = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign  = TextAlign.Center
        )

        Spacer(Modifier.height(4.dp))

        Text(
            "Setup profil keuanganmu",
            style     = MaterialTheme.typography.bodyMedium,
            color     = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(32.dp))

        OutlinedTextField(
            value         = accountName,
            onValueChange = { accountName = it },
            label         = { Text("Nama Profil") },
            placeholder   = { Text("Contoh: Keuangan Pribadi") },
            leadingIcon   = { Icon(Icons.Default.Person, null) },
            singleLine    = true,
            modifier      = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value           = initialBalance,
            onValueChange   = { initialBalance = it.filter { c -> c.isDigit() } },
            label           = { Text("Saldo Awal (opsional)") },
            placeholder     = { Text("0") },
            prefix          = { Text("Rp ") },
            leadingIcon     = { Icon(Icons.Default.AccountBalanceWallet, null) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine      = true,
            modifier        = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(8.dp))

        Text(
            "Saldo awal dompet \"Kas\" kamu. Bisa diubah nanti.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(24.dp))

        Button(
            onClick  = {
                if (accountName.isNotBlank()) {
                    onComplete(
                        accountName.trim(),
                        initialBalance.toLongOrNull() ?: 0L
                    )
                }
            },
            enabled  = accountName.isNotBlank(),
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) {
            Icon(Icons.Default.Check, null)
            Spacer(Modifier.width(8.dp))
            Text("Mulai Pakai ChatFin", fontWeight = FontWeight.SemiBold)
        }
    }
}
