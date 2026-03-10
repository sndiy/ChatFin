package com.sndiy.chatfin.feature.splash.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay

private const val MAI_COLOR = "#7E57C2"

private val FINANCE_FACTS = listOf(
    "Aturan 50/30/20: 50% kebutuhan, 30% keinginan, 20% investasi masa depan.",
    "Investasi Rp 500rb/bulan selama 20 tahun bisa tumbuh hingga Rp 400 juta lebih.",
    "Dana darurat ideal adalah 3–6 bulan pengeluaran rutin di instrumen likuid.",
    "Efek Latte: Kebiasaan kopi Rp 30rb/hari setara Rp 10,9 juta per tahun.",
    "Kartu kredit bukan musuh; masalah utama ada pada kontrol diri penggunanya.",
    "Orang kaya rata-rata memiliki minimal 7 sumber penghasilan yang berbeda.",
    "Bunga bank konvensional seringkali kalah telak oleh laju inflasi tahunan.",
    "Tujuan keuangan spesifik 10x lebih mungkin tercapai daripada target samar.",
    "Uang diam akan menyusut nilainya 3–5% tiap tahun akibat gerusan inflasi.",
    "Catat pengeluaran 30 hari untuk temukan kebocoran uang yang tak disadari.",
    "Bayar dirimu sendiri dulu; amankan tabungan sebelum belanja yang lain.",
    "Cicilan rumah idealnya tidak memakan lebih dari 30% pendapatan bulanan.",
    "Aplikasi keuangan membantu pengguna hemat hingga 20% pengeluaran bulanan.",
    "Reksa dana pasar uang cocok untuk dana darurat karena stabil dan likuid.",
    "Hutang konsumtif menggerus aset; hutang produktif justru membangun kekayaan.",
    "Literasi keuangan yang kuat adalah kunci kesejahteraan jangka panjang.",
    "Bunga majemuk: kekuatan kecil yang melipatgandakan aset secara eksponensial.",
    "Mulai di usia 25 vs 35 bisa membuat selisih hasil akhir hingga 2 kali lipat.",
    "Daftar belanja sebelum ke toko terbukti kurangi belanja impulsif hingga 23%.",
    "80% masalah keuangan berasal dari gaya hidup yang melebihi pendapatan.",
    "Asuransi adalah alat pelindung agar rencana besar tidak hancur seketika.",
    "Gunakan prinsip SMART: Spesifik, Terukur, Realistis, dan ada Batas Waktu.",
    "Disiplin keuangan itu seperti otot; makin dilatih makin kuat hasilnya.",
    "Diversifikasi aset adalah cara cerdas mengurangi risiko kerugian total.",
    "Net worth atau nilai bersih adalah indikator kesehatan keuangan yang nyata.",
    "Review rutin 15 menit per bulan cukup untuk menjaga arus kas tetap sehat.",
    "FOMO adalah musuh; berinvestasilah karena riset, bukan karena ikut-ikutan.",
    "Bisnis sampingan Rp 1 juta/bulan setara dengan menabung Rp 12 juta/tahun.",
    "Psikologi mengelola uang lebih penting daripada sekadar teori investasi.",
    "Waktu terbaik mulai adalah kemarin; waktu terbaik kedua adalah detik ini.",
    "Hindari transaksi yang tidak transparan demi menjaga keberkahan harta.",
    "Jangan puas dengan hasil rata-rata; bidik target pertumbuhan 10x lipat.",
    "Kekayaan sejati dimulai dari efisiensi pengeluaran dan keberanian bermimpi.",
)

@Composable
fun SplashScreen(
    onNavigateToDashboard: () -> Unit,
    onNavigateToOnboarding: () -> Unit,
    viewModel: SplashViewModel = hiltViewModel()
) {
    val destination by viewModel.destination.collectAsStateWithLifecycle()

    LaunchedEffect(destination) {
        when (destination) {
            is SplashDestination.Dashboard  -> onNavigateToDashboard()
            is SplashDestination.Onboarding -> onNavigateToOnboarding()
            else -> {}
        }
    }

    SplashContent()
}

@Composable
private fun SplashContent() {
    val maiColor = Color(android.graphics.Color.parseColor(MAI_COLOR))

    // Pilih fact random sekali saat composable pertama kali masuk
    val randomFact = remember { FINANCE_FACTS.random() }

    // Animasi pulse avatar
    val infiniteTransition = rememberInfiniteTransition(label = "splash_pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue  = 1f,
        targetValue   = 1.08f,
        animationSpec = infiniteRepeatable(
            animation  = tween(900, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "avatar_scale"
    )

    // Animasi fade untuk fact text — berganti setiap 3 detik
    var currentFact by remember { mutableStateOf(randomFact) }
    var factAlpha   by remember { mutableStateOf(1f) }

    val factAlphaAnim by animateFloatAsState(
        targetValue   = factAlpha,
        animationSpec = tween(500),
        label         = "fact_alpha"
    )

    LaunchedEffect(Unit) {
        val shuffled = FINANCE_FACTS.shuffled().toMutableList()
        var index    = shuffled.indexOf(randomFact)
        while (true) {
            delay(3000)
            // Fade out
            factAlpha = 0f
            delay(500)
            // Ganti teks
            index      = (index + 1) % shuffled.size
            currentFact = shuffled[index]
            // Fade in
            factAlpha = 1f
        }
    }

    Box(
        modifier         = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(0.dp),
            modifier            = Modifier.padding(horizontal = 32.dp)
        ) {
            // Avatar Mai
            Box(
                modifier = Modifier
                    .scale(scale)
                    .size(96.dp)
                    .clip(CircleShape)
                    .background(maiColor),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "M",
                    style      = MaterialTheme.typography.displaySmall,
                    color      = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(Modifier.height(20.dp))

            Text(
                "ChatFin",
                style      = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color      = MaterialTheme.colorScheme.onBackground
            )

            Spacer(Modifier.height(4.dp))

            Text(
                "Bersama Sakurajima Mai",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(40.dp))

            // ── Fun fact card ──────────────────────────────────────────────────
            androidx.compose.material3.Card(
                modifier = Modifier.fillMaxWidth(),
                colors   = androidx.compose.material3.CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                )
            ) {
                Column(
                    modifier            = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "💬 Tahukah kamu?",
                        style      = MaterialTheme.typography.labelMedium,
                        color      = Color(android.graphics.Color.parseColor(MAI_COLOR)),
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text      = currentFact,
                        style     = MaterialTheme.typography.bodySmall,
                        color     = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = factAlphaAnim),
                        textAlign = TextAlign.Start,
                        minLines  = 2
                    )
                }
            }

            Spacer(Modifier.height(32.dp))

            // Dot loading indicator
            LoadingDots(color = maiColor)

            Spacer(Modifier.height(8.dp))

            Text(
                "Memuat data...",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
        }
    }
}

@Composable
private fun LoadingDots(color: Color) {
    val infiniteTransition = rememberInfiniteTransition(label = "dots")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        repeat(3) { index ->
            val offsetY by infiniteTransition.animateFloat(
                initialValue  = 0f,
                targetValue   = -10f,
                animationSpec = infiniteRepeatable(
                    animation          = tween(400, easing = EaseInOut),
                    repeatMode         = RepeatMode.Reverse,
                    initialStartOffset = StartOffset(index * 130)
                ),
                label = "dot_$index"
            )
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .offset(y = offsetY.dp)
                    .clip(CircleShape)
                    .background(color)
            )
        }
    }
}