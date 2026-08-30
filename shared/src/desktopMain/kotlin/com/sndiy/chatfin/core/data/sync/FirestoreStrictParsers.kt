package com.sndiy.chatfin.core.data.sync

import dev.gitlive.firebase.firestore.DocumentSnapshot

/**
 * Helper untuk parsing nominal uang secara presisi sesuai AGENTS.md Bagian 2.2 & 3.
 *
 * Aturan:
 * 1. Long -> diterima langsung.
 * 2. Double bulat (misal 50000.0) -> dikonversi aman ke Long.
 * 3. Double pecahan non-nol (misal 50000.75) / bukan angka -> DITOLAK (return null), dokumen di-skip.
 */
fun DocumentSnapshot.firestoreStrictLong(field: String): Long? {
    try {
        val longVal: Long? = get(field)
        if (longVal != null) return longVal
    } catch (_: Exception) {
        // Coba evaluasi sebagai Number/Double
    }

    return try {
        val rawNum: Number = get<Number?>(field) ?: return null
        val doubleVal = rawNum.toDouble()
        if (doubleVal % 1.0 == 0.0 && doubleVal >= Long.MIN_VALUE.toDouble() && doubleVal <= Long.MAX_VALUE.toDouble()) {
            doubleVal.toLong()
        } else {
            // Pecahan desimal non-nol terdeteksi pada nominal rupiah -> tolak data rusak
            null
        }
    } catch (_: Exception) {
        null
    }
}

fun DocumentSnapshot.strictString(field: String): String? = try {
    get<String?>(field)
} catch (_: Exception) {
    try { get<Any?>(field)?.toString() } catch (_: Exception) { null }
}

fun DocumentSnapshot.strictBoolean(field: String, default: Boolean = false): Boolean = try {
    get<Boolean?>(field) ?: default
} catch (_: Exception) {
    default
}

fun DocumentSnapshot.strictInt(field: String, default: Int = 0): Int = try {
    val raw: Number? = get(field)
    raw?.toInt() ?: default
} catch (_: Exception) {
    default
}
