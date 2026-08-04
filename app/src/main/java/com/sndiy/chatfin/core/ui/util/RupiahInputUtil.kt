// app/src/main/java/com/sndiy/chatfin/core/ui/util/RupiahInputUtil.kt
//
// Satu-satunya sumber kebenaran untuk format rupiah di layer UI.
// ATURAN PEMAKAIAN:
//   - Nilai yang DISIMPAN ke DB/state selalu Long rupiah utuh (tanpa separator).
//   - VisualTransformation di bawah hanya mengubah TAMPILAN di TextField, bukan value aktual.
//   - Format display: pemisah ribuan titik (1.500.000), tanpa "Rp" prefix di sini
//     (prefix "Rp " cukup diset via `prefix = { Text("Rp ") }` di OutlinedTextField).

package com.sndiy.chatfin.core.ui.util

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import java.text.NumberFormat
import java.util.Locale

// ── Static formatter (thread-local-safe via ThreadLocal untuk menghindari re-create) ─
private val RUPIAH_FMT: ThreadLocal<NumberFormat> = ThreadLocal.withInitial {
    NumberFormat.getNumberInstance(Locale("id", "ID"))
}

/**
 * Format Long rupiah ke string dengan pemisah ribuan titik.
 * Contoh: 1_500_000L → "1.500.000"
 */
fun Long.formatRupiah(): String =
    RUPIAH_FMT.get()!!.format(this)

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
 *
 * - value (disimpan di state) = digit murni, misal "15000"
 * - tampilan di TextField      = "15.000"
 * - kursor selalu dipetakan ke posisi yang benar setelah separator ditambahkan
 *
 * Cara pakai:
 *   OutlinedTextField(
 *       value = rawDigits,                  // digit murni di state
 *       onValueChange = { rawDigits = it.filter { c -> c.isDigit() } },
 *       visualTransformation = RupiahVisualTransformation,
 *       keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
 *       ...
 *   )
 */
object RupiahVisualTransformation : VisualTransformation {

    override fun filter(text: AnnotatedString): TransformedText {
        val digits  = text.text.filter { it.isDigit() }
        val display = if (digits.isEmpty()) "" else {
            digits.toLongOrNull()?.formatRupiah() ?: digits
        }
        val annotated = AnnotatedString(display)

        // Peta setiap posisi di string digit → posisi di string terformat
        // Contoh: "15000" → "15.000"
        //   digit[0]='1' → display[0]='1'
        //   digit[1]='5' → display[1]='5'
        //   digit[2]='0' → display[3]='0'  (skip separator '.' di display[2])
        //   digit[3]='0' → display[4]='0'
        //   digit[4]='0' → display[5]='0'
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

    /**
     * Bangun peta offset: indeks digit → indeks di display.
     * Iterasi char-by-char di display, skip separator (titik).
     */
    private fun buildDigitToDisplayMap(digits: String, display: String): IntArray {
        val map = IntArray(digits.length + 1)
        var di = 0   // indeks di display
        var gi = 0   // indeks di digits
        while (gi < digits.length && di < display.length) {
            if (display[di] == '.') {
                di++
                continue
            }
            map[gi] = di
            gi++
            di++
        }
        // Posisi setelah digit terakhir → akhir display
        map[gi] = display.length
        return map
    }

    /**
     * Bangun peta offset: indeks di display → indeks digit.
     * Separator (titik) dipetakan ke indeks digit berikutnya.
     */
    private fun buildDisplayToDigitMap(digits: String, display: String): IntArray {
        val map = IntArray(display.length + 1)
        var di = 0
        var gi = 0
        while (di < display.length) {
            if (display[di] == '.') {
                // separator → petakan ke indeks digit saat ini
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
