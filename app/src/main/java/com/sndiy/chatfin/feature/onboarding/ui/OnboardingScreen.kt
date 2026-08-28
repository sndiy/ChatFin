// app/src/main/java/com/sndiy/chatfin/feature/onboarding/ui/OnboardingScreen.kt

package com.sndiy.chatfin.feature.onboarding.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Login
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
        description = "Kelola pemasukan, pengeluaran, dan dompet dengan mudah. Offline-first, data aman di perangkatmu."
    ),
    OnboardingPage(
        icon        = Icons.Default.AutoAwesome,
        iconColor   = Color(0xFF7E57C2),
        title       = "Mai, Asisten Finansial",
        subtitle    = "Sakurajima Mai siap membantu",
        description = "Tanya Mai soal kondisi keuanganmu, minta analisis pengeluaran, atau sekadar saran hemat praktis."
    ),
    OnboardingPage(
        icon        = Icons.Default.PieChart,
        iconColor   = Color(0xFF1E88E5),
        title       = "Analitik & Budget",
        subtitle    = "Kontrol penuh keuanganmu",
        description = "Lihat grafik pengeluaran, atur budget per kategori, dan pantau kesehatan finansialmu setiap bulan."
    )
)

@Composable
fun OnboardingScreen(
    onLogin: () -> Unit,
    onRegister: () -> Unit,
    onStartOffline: () -> Unit
) {
    val totalPageCount = pages.size + 1 // 3 intro pages + 1 final action page
    val pagerState = rememberPagerState(pageCount = { totalPageCount })
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
                    FinalActionPageContent(
                        onLogin        = onLogin,
                        onRegister     = onRegister,
                        onStartOffline = onStartOffline
                    )
                }
            }

            // Bottom indicator & navigation
            Column(
                modifier            = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Page indicator dots
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    repeat(totalPageCount) { index ->
                        val isActive = index == pagerState.currentPage
                        val dotWidth by animateDpAsState(
                            targetValue = if (isActive) 24.dp else 8.dp,
                            label       = "dot_width"
                        )
                        Box(
                            modifier = Modifier
                                .size(dotWidth, 8.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(
                                    if (isActive) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.outlineVariant
                                )
                        )
                    }
                }

                // Nav buttons for intro pages
                if (pagerState.currentPage < pages.size) {
                    Row(
                        modifier              = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment     = Alignment.CenterVertically
                    ) {
                        if (pagerState.currentPage > 0) {
                            TextButton(onClick = {
                                scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
                            }) { Text("Kembali") }
                        } else {
                            TextButton(onClick = {
                                scope.launch { pagerState.animateScrollToPage(pages.size) }
                            }) { Text("Lewati") }
                        }

                        Button(onClick = {
                            scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                        }) {
                            Text(if (pagerState.currentPage == pages.size - 1) "Mulai" else "Lanjut")
                            Spacer(Modifier.width(4.dp))
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, null, Modifier.size(18.dp))
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
private fun FinalActionPageContent(
    onLogin: () -> Unit,
    onRegister: () -> Unit,
    onStartOffline: () -> Unit
) {
    Column(
        modifier            = Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier         = Modifier
                .size(88.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.CloudSync, null,
                modifier = Modifier.size(44.dp),
                tint     = MaterialTheme.colorScheme.primary
            )
        }

        Spacer(Modifier.height(20.dp))

        Text(
            "Selamat Datang di ChatFin",
            style      = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign  = TextAlign.Center
        )

        Spacer(Modifier.height(6.dp))

        Text(
            "Pilih cara memulai: masuk dengan email untuk sinkronisasi otomatis, buat akun baru, atau pakai langsung secara offline.",
            style     = MaterialTheme.typography.bodyMedium,
            color     = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(28.dp))

        // Opsi 1: Masuk dengan Email (Login)
        Button(
            onClick  = onLogin,
            modifier = Modifier.fillMaxWidth().height(48.dp)
        ) {
            Icon(Icons.AutoMirrored.Filled.Login, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Masuk dengan Email", fontWeight = FontWeight.SemiBold)
        }

        Spacer(Modifier.height(10.dp))

        // Opsi 2: Daftar Akun Baru (Registrasi)
        FilledTonalButton(
            onClick  = onRegister,
            modifier = Modifier.fillMaxWidth().height(48.dp)
        ) {
            Icon(Icons.Default.PersonAdd, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Daftar Akun Baru", fontWeight = FontWeight.SemiBold)
        }

        Spacer(Modifier.height(10.dp))

        // Opsi 3: Mulai Tanpa Akun (Offline)
        OutlinedButton(
            onClick  = onStartOffline,
            modifier = Modifier.fillMaxWidth().height(48.dp)
        ) {
            Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Mulai Tanpa Akun (Offline)", fontWeight = FontWeight.SemiBold)
        }

        Spacer(Modifier.height(16.dp))

        Text(
            "Semua data dan profil keuangan bisa diatur kapan saja di menu Pengaturan.",
            style     = MaterialTheme.typography.labelSmall,
            color     = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}
