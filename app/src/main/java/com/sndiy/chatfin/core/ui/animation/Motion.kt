package com.sndiy.chatfin.core.ui.animation

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

// Helper animasi dibuat SENGAJA jadi (hampir) tanpa efek setelah user
// melaporkan aplikasi terasa berat di hp menengah ke bawah — target utama
// app ini (CLAUDE.md §1). Signature tetap sama supaya semua call site (belasan
// layar) tidak perlu diubah satu-satu; tinggal panggil fungsi ini lagi kalau
// suatu saat mau menghidupkan animasi lagi tanpa harus edit ulang tiap layar.

/**
 * Sebelumnya: animasi scale per-tekan via spring + graphicsLayer di tiap
 * kartu/swatch yang bisa ditap. Dihapus — feedback tekan cukup dari ripple
 * bawaan `clickable(indication = LocalIndication.current)` yang sudah dipasang
 * di semua call site; ripple itu sendiri sudah cukup dan jauh lebih murah
 * daripada animasi scale custom per elemen.
 */
fun Modifier.pressScale(
    interactionSource: MutableInteractionSource,
    pressedScale: Float = 0.97f
): Modifier = this

/**
 * Sebelumnya: fade+slide-up bertahap per item list via LaunchedEffect+delay+
 * AnimatedVisibility — di list panjang (mis. Riwayat Transaksi) ini berarti
 * satu coroutine+state per item yang baru masuk composition. Dihapus —
 * sekarang murni render langsung, tanpa animasi masuk.
 */
@Composable
fun StaggeredEntrance(
    index: Int,
    modifier: Modifier = Modifier,
    maxStaggerIndex: Int = 8,
    stepMillis: Int = 40,
    content: @Composable () -> Unit
) {
    content()
}

/**
 * Sebelumnya: sapuan gradient berjalan terus lewat rememberInfiniteTransition
 * selama skeleton loading tampil. Diganti blok warna statis — tetap
 * mengomunikasikan "sedang memuat" tanpa animasi berkelanjutan.
 */
@Composable
fun Modifier.shimmerEffect(): Modifier {
    val base = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)
    return this.background(base)
}
