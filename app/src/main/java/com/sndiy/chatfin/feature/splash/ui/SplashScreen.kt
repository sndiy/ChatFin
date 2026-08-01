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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sndiy.chatfin.core.domain.LocalInsightEngine
import com.sndiy.chatfin.core.ui.theme.MaiPurple
import com.sndiy.chatfin.core.ui.theme.MaiPurpleDk
import kotlinx.coroutines.delay

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
    val randomQuote = remember { LocalInsightEngine.randomQuote() }
    var currentQuote by remember { mutableStateOf(randomQuote) }
    var quoteAlpha   by remember { mutableStateOf(1f) }

    val quoteAlphaAnim by animateFloatAsState(
        targetValue   = quoteAlpha,
        animationSpec = tween(400),
        label         = "quote_alpha"
    )

    // Animate avatar pulse
    val infiniteTransition = rememberInfiniteTransition(label = "splash_pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue  = 1f,
        targetValue   = 1.06f,
        animationSpec = infiniteRepeatable(
            animation  = tween(1200, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "avatar_scale"
    )

    // Rotate quotes
    LaunchedEffect(Unit) {
        val shuffled = LocalInsightEngine.shuffledQuotes().toMutableList()
        var index = shuffled.indexOf(randomQuote)
        while (true) {
            delay(3500)
            quoteAlpha = 0f
            delay(400)
            index = (index + 1) % shuffled.size
            currentQuote = shuffled[index]
            quoteAlpha = 1f
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
            modifier            = Modifier.padding(horizontal = 32.dp)
        ) {
            // Mai avatar
            Box(
                modifier = Modifier
                    .scale(scale)
                    .size(96.dp)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(listOf(MaiPurple, MaiPurpleDk))),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "舞",
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
                "Bukan asisten keuanganmu... tapi ya sudahlah.",
                style     = MaterialTheme.typography.bodySmall,
                fontStyle = FontStyle.Italic,
                color     = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(40.dp))

            // Quote card
            androidx.compose.material3.Card(
                modifier = Modifier.fillMaxWidth(),
                colors   = androidx.compose.material3.CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Column(
                    modifier            = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "💬 Mai's tip",
                        style      = MaterialTheme.typography.labelMedium,
                        color      = MaiPurple,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text      = currentQuote,
                        style     = MaterialTheme.typography.bodySmall,
                        color     = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = quoteAlphaAnim),
                        textAlign = TextAlign.Start,
                        minLines  = 2
                    )
                }
            }

            Spacer(Modifier.height(32.dp))

            // Loading dots
            LoadingDots(color = MaiPurple)

            Spacer(Modifier.height(8.dp))

            Text(
                "Sedang bersiap...",
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