package com.sndiy.chatfin.core.ui.util

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import java.text.NumberFormat
import java.util.Locale

// ── Static formatter (thread-local-safe via ThreadLocal untuk menghindari re-create) ─
private val RUPIAH_FMT: ThreadLocal<NumberFormat> = ThreadLocal.withInitial {
    NumberFormat.getNumberInstance(Locale.Builder().setLanguage("id").setRegion("ID").build())
}

/**
 * Format Long rupiah ke string dengan pemisah ribuan titik.
 * Contoh: 1_500_000L → "1.500.000"
 */
fun Long.formatRupiah(): String =
    RUPIAH_FMT.get()!!.format(this)

/**
 * Format Long rupiah ke string lengkap dengan prefix "Rp ".
 * Contoh: 1_500_000L → "Rp 1.500.000"
 */
fun Long.toRpString(): String = "Rp ${formatRupiah()}"

/**
 * Format string digit-murni ke tampilan rupiah dengan separator.
 * Input boleh kosong atau null. Output kosong jika input tidak valid/kosong.
 * Contoh: "15000" → "15.000", "" → "", "0" → "0"
 */
fun String?.toRupiahDisplay(): String {
    if (isNullOrBlank()) return ""
    val digits = this.filter { it.isDigit() }
    if (digits.isEmpty()) return ""
    val num = digits.toLongOrNull() ?: return digits
    return RUPIAH_FMT.get()!!.format(num)
}

/**
 * VisualTransformation kursor-aware untuk OutlinedTextField yang menerima digit saja.
 */
object RupiahVisualTransformation : VisualTransformation {

    override fun filter(text: AnnotatedString): TransformedText {
        val digits  = text.text.filter { it.isDigit() }
        val display = if (digits.isEmpty()) "" else {
            digits.toLongOrNull()?.formatRupiah() ?: digits
        }
        val annotated = AnnotatedString(display)

        val digitToDisplay = buildDigitToDisplayMap(digits, display)
        val displayToDigit = buildDisplayToDigitMap(digits, display)

        val offsetMapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int =
                digitToDisplay.getOrElse(offset) { display.length }

            override fun transformedToOriginal(offset: Int): Int =
                displayToDigit.getOrElse(offset) { digits.length }
        }

        return TransformedText(annotated, offsetMapping)
    }

    private fun buildDigitToDisplayMap(digits: String, display: String): IntArray {
        val map = IntArray(digits.length + 1)
        var di = 0
        var gi = 0
        while (gi < digits.length && di < display.length) {
            if (display[di] == '.') {
                di++
                continue
            }
            map[gi] = di
            gi++
            di++
        }
        map[gi] = display.length
        return map
    }

    private fun buildDisplayToDigitMap(digits: String, display: String): IntArray {
        val map = IntArray(display.length + 1)
        var di = 0
        var gi = 0
        while (di < display.length) {
            if (display[di] == '.') {
                map[di] = gi
            } else {
                map[di] = gi
                gi++
            }
            di++
        }
        map[display.length] = gi
        return map
    }
}
