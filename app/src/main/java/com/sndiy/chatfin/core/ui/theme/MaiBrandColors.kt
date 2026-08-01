package com.sndiy.chatfin.core.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Warna ciri khas persona Mai — sengaja TERPISAH dari AppAccents (warna tema
 * yang bisa diganti user di Setelan). Identitas visual Mai selalu ungu apa
 * pun aksen tema yang sedang aktif, jadi tidak ikut skema warna user.
 *
 * Sebelumnya dideklarasikan dua kali secara terpisah (ChatComponents.kt dan
 * DashboardScreen.kt) dengan nilai hex yang sama — disatukan di sini supaya
 * ganti satu warna Mai tidak perlu diingat harus diubah di dua tempat.
 */
val MaiPurple   = Color(0xFF9C27B0)
val MaiPurpleDk = Color(0xFF7B1FA2)
val MaiPink     = Color(0xFFE1BEE7)
val MaiAccent   = Color(0xFFCE93D8)
