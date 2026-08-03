package com.sndiy.chatfin.core.ocr

import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.regex.Pattern

/**
 * Parser OCR Multibahasa — Adaptif untuk struk anomali, bahasa asing, dan format tak standar.
 *
 * Mendukung: Bahasa Indonesia, Inggris, Jepang (romanji), Korea (romanji), Mandarin (romanji),
 * Thailand, Vietnam, Melayu, Filipina, dan format regional Eropa.
 *
 * Fitur:
 * - Deteksi tanggal multi-format (dd/MM/yyyy, MM/dd/yyyy, dd.MM.yyyy, yyyy年MM月dd日, dll.)
 * - Disambiguasi cerdas dd/MM vs MM/dd berdasarkan validitas hari & bulan
 * - Deteksi AM/PM dan konversi ke 24-jam
 * - Deteksi mata uang Internasional (Rp, $, €, £, ¥, ₩, RM, ₫, ฿, ₱)
 * - Item extraction dengan dukungan format qty × price, qty @ price, dll.
 * - Merchant extraction adaptif (header, label eksplisit, atau fallback)
 */
object ReceiptParser {

    // ── Ignore Keywords: baris yang bukan item belanja ────────────────────────
    private val headerIgnoreKeywords = listOf(
        // Indonesian
        "struk", "nota", "selamat datang", "kasir", "tgl", "tanggal", "jl.", "jln", "telp", "npwp",
        "no meja", "jam masuk", "jam keluar", "jam", "mode", "pembayaran", "terima kasih",
        "transaksi berhasil", "pembayaran berhasil", "transfer berhasil", "detail transaksi",
        "waktu transaksi", "no. pesanan", "rincian pesanan", "penerima", "diskon", "pajak", "biaya layanan",
        "alamat", "kota", "provinsi", "kode pos", "member", "kartu", "pelanggan",
        // English
        "receipt", "tax invoice", "official receipt", "invoice", "cashier", "cashier:", "server",
        "table", "guest", "guests", "order #", "order no", "order num", "check #", "check no",
        "date", "date:", "time", "time:", "drivethru", "takeout", "take away", "dine in", "eat in",
        "station", "store #", "store no", "tel", "phone", "welcome", "thank you", "thanks",
        "visit us again", "status:", "status", "payment details", "item count", "qty total",
        "tax", "vat", "service", "service charge", "discount", "tip", "rounding",
        "address", "city", "zip", "state", "country", "customer", "loyalty", "rewards", "points",
        "authorization", "auth code", "card number", "card type", "approval", "trans id", "ref no",
        "terminal", "batch", "sequence", "receipt no", "order id",
        // Malay
        "resit", "jurutera", "pelayan", "selamat datang", "terima kasih",
        // Thai (romanized)
        "baiberk", "kasian", "kob kun", "khob khun",
        // Japanese (romanized)
        "ryoushuusho", "irasshaimase", "arigatou",
        // Korean (romanized)
        "yeongsujeung", "gamsahamnida",
        // General
        "www.", "http", ".com", ".co.", "wifi", "password"
    )

    private val paymentExcludeKeywords = listOf(
        // Indonesian
        "cash", "tunai", "kembali", "kembalian", "kembalian :", "kembali :", "sisa saldo", "saldo", "sumber dana",
        // English
        "cash tender", "cash tendered", "tendered", "change", "change due", "change given",
        "balance due", "card", "credit card", "debit card", "visa", "mastercard", "amex", "account balance",
        "paid", "payment", "payment method", "payment type", "method of payment",
        // Regional
        "baki", "wang tunai", "bayar", "pembayaran"
    )

    private val totalPriorityKeywords = listOf(
        // Indonesian
        "grand total", "total bayar", "total belanja", "total pembayaran", "total transaksi",
        "jumlah bayar", "jumlah total", "total akhir", "total harga", "netto", "net total",
        "tagihan", "nominal",
        // English
        "total amount", "total amount due", "amount due", "total paid",
        "net amount", "balance due", "you pay", "total due",
        // Malay
        "jumlah keseluruhan",
        // Thai (romanized)
        "yod rวm", "rab thang mod",
        // Japanese (romanized)
        "goukei", "gokei",
        // Korean (romanized)
        "chonggeum", "hapgye"
    )

    // ── Bulan Multi-Bahasa untuk parsing tanggal teks ────────────────────────
    private val monthMap = mapOf(
        // English
        "jan" to 1, "feb" to 2, "mar" to 3, "apr" to 4, "may" to 5, "jun" to 6,
        "jul" to 7, "aug" to 8, "sep" to 9, "oct" to 10, "nov" to 11, "dec" to 12,
        "january" to 1, "february" to 2, "march" to 3, "april" to 4, "june" to 6,
        "july" to 7, "august" to 8, "september" to 9, "october" to 10, "november" to 11, "december" to 12,
        // Indonesian
        "jan" to 1, "feb" to 2, "mar" to 3, "apr" to 4, "mei" to 5, "jun" to 6,
        "jul" to 7, "agu" to 8, "sep" to 9, "okt" to 10, "nov" to 11, "des" to 12,
        "januari" to 1, "februari" to 2, "maret" to 3, "mei" to 5, "juni" to 6,
        "juli" to 7, "agustus" to 8, "september" to 9, "oktober" to 10, "november" to 11, "desember" to 12,
        // Malay
        "mac" to 3, "ogos" to 8, "dis" to 12,
        // French
        "janv" to 1, "févr" to 2, "fevr" to 2, "mars" to 3, "avr" to 4, "mai" to 5, "juin" to 6,
        "juil" to 7, "août" to 8, "aout" to 8, "déc" to 12,
        // Spanish
        "ene" to 1, "abr" to 4, "ago" to 8, "dic" to 12,
        // German
        "mär" to 3, "maer" to 3, "dez" to 12,
        // Portuguese
        "fev" to 2, "set" to 9, "out" to 10, "dez" to 12
    )

    // ── Date Regexes — diperluas dengan format internasional ─────────────────
    private val dateRegexes = listOf(
        // yyyy-MM-dd or yyyy/MM/dd or yyyy.MM.dd
        Regex("""(?<!\d)(20\d{2})[/\-.](\d{1,2})[/\-.](\d{1,2})(?!\d)"""),
        // dd/MM/yyyy or dd-MM-yyyy or dd.MM.yyyy (European/Asian/Indonesian)
        Regex("""(?<!\d)(\d{1,2})[/\-.](\d{1,2})[/\-.](\d{4})(?!\d)"""),
        // dd/MM/yy or dd-MM-yy or dd.MM.yy
        Regex("""(?<!\d)(\d{1,2})[/\-.](\d{1,2})[/\-.](\d{2})(?!\d)"""),
        // dd MMM yyyy or dd MMM, yyyy (multilingual month names)
        Regex("""(?<!\d)(\d{1,2})\s+([a-zA-ZÀ-ÿ]{3,})\s*,?\s+(20\d{2})(?!\d)"""),
        // MMM dd, yyyy or MMM dd yyyy (English US style)
        Regex("""([a-zA-ZÀ-ÿ]{3,})\s+(\d{1,2})(?:st|nd|rd|th)?\s*,?\s+(20\d{2})(?!\d)"""),
        // yyyy年MM月dd日 (Japanese/Chinese — if OCR reads the kanji)
        Regex("""(20\d{2})\s*年\s*(\d{1,2})\s*月\s*(\d{1,2})\s*日""")
    )

    // ── Time Regex — dengan dukungan AM/PM ───────────────────────────────────
    private val timeRegex = Regex("""(?<!\d)(\d{1,2})[:.](\d{2})(?::(\d{2}))?\s*(am|pm|AM|PM)?(?!\d)""")

    // ── Semua simbol mata uang yang diketahui ────────────────────────────────
    private val currencyPattern = Regex("""(?i)(?:rp|idr|\$|usd|sgd|myr|rm|€|eur|£|gbp|¥|jpy|cny|₩|krw|₫|vnd|฿|thb|₱|php|aud|nzd|hkd|twd|nt\$)\.?\s*""")

    fun parse(rawText: String): ParsedReceipt {
        if (rawText.isBlank()) {
            return ParsedReceipt(
                isMerchantLowConfidence = true,
                isDateLowConfidence = true,
                isTotalLowConfidence = true
            )
        }

        val lines = rawText.lines().map { it.trim() }.filter { it.isNotEmpty() }

        val (merchant, isMerchantLow) = extractMerchant(lines)
        val (date, time, isDateLow) = extractDateAndTime(rawText, lines)
        val items = extractItems(lines)
        val (total, isTotalLow) = extractTotalAmount(lines, items)

        return ParsedReceipt(
            merchant = merchant,
            date = date,
            time = time,
            items = items,
            totalAmount = total,
            rawText = rawText,
            isMerchantLowConfidence = isMerchantLow,
            isDateLowConfidence = isDateLow,
            isTotalLowConfidence = isTotalLow
        )
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // MERCHANT EXTRACTION
    // ═══════════════════════════════════════════════════════════════════════════

    private fun extractMerchant(lines: List<String>): Pair<String?, Boolean> {
        if (lines.isEmpty()) return Pair(null, true)

        // 1. Cari merchant dari label eksplisit multi-bahasa
        val merchantLabelRegex = Regex(
            """(?i)^\s*(?:penerima|merchant|tujuan|penjual|nama toko|seller|to|merchant name|shop|store name|nama kedai|ร้าน)\s*[:.]\s*(.{3,})""",
        )
        for (line in lines) {
            val match = merchantLabelRegex.find(line)
            if (match != null) {
                val candidate = match.groupValues[1].trim()
                if (candidate.length >= 3 && !isIgnoreLine(candidate)) {
                    return Pair(cleanMerchantName(candidate), false)
                }
            }
        }

        // 2. Heuristic: baris pertama yang cukup panjang dan huruf besar dominan (nama toko)
        for (i in 0 until minOf(5, lines.size)) {
            val line = lines[i]
            if (line.length < 3 || line.all { !it.isLetter() }) continue
            if (isIgnoreLine(line)) continue
            // Skip jika baris isinya hanya angka / tanggal / waktu
            if (Regex("""^[\d/\-.:,\s]+$""").matches(line)) continue

            return Pair(cleanMerchantName(line), false)
        }

        return Pair(lines.firstOrNull()?.let { cleanMerchantName(it) }, true)
    }

    private fun cleanMerchantName(raw: String): String {
        return raw.replace(Regex("""[^\w\s\.\-&']"""), "").trim()
            .split(" ")
            .filter { it.isNotBlank() }
            .joinToString(" ") { word ->
                word.lowercase(Locale.ROOT).replaceFirstChar { char ->
                    if (char.isLowerCase()) char.titlecase(Locale.ROOT) else char.toString()
                }
            }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // DATE & TIME EXTRACTION — Adaptif multi-format & multi-bahasa
    // ═══════════════════════════════════════════════════════════════════════════

    private fun extractDateAndTime(rawText: String, lines: List<String>): Triple<String?, String?, Boolean> {
        var foundDate: String? = null
        var foundTime: String? = null

        // --- Date Extraction ---
        for (regex in dateRegexes) {
            val match = regex.find(rawText)
            if (match != null) {
                foundDate = parseNormalizedDate(match)
                if (foundDate != null) break
            }
        }

        // Jika belum ditemukan, coba cari dari baris berlabel "Date:", "Tanggal:", "Tgl:", dll.
        if (foundDate == null) {
            val dateLabelRegex = Regex("""(?i)(?:date|tanggal|tgl|fecha|datum|tarikh|日付|날짜)\s*[:.]\s*(.+)""")
            for (line in lines) {
                val match = dateLabelRegex.find(line)
                if (match != null) {
                    val dateCandidate = match.groupValues[1].trim()
                    for (regex in dateRegexes) {
                        val innerMatch = regex.find(dateCandidate)
                        if (innerMatch != null) {
                            foundDate = parseNormalizedDate(innerMatch)
                            if (foundDate != null) break
                        }
                    }
                    if (foundDate != null) break
                }
            }
        }

        // --- Time Extraction ---
        val timeMatch = timeRegex.find(rawText)
        if (timeMatch != null) {
            foundTime = parseNormalizedTime(timeMatch)
        }

        // Jika belum ditemukan, cari dari baris berlabel "Time:", "Jam:", "Waktu:", dll.
        if (foundTime == null) {
            val timeLabelRegex = Regex("""(?i)(?:time|jam|waktu|hora|zeit|masa|時間|시간)\s*[:.]\s*(.+)""")
            for (line in lines) {
                val match = timeLabelRegex.find(line)
                if (match != null) {
                    val timeCandidate = match.groupValues[1].trim()
                    val innerMatch = timeRegex.find(timeCandidate)
                    if (innerMatch != null) {
                        foundTime = parseNormalizedTime(innerMatch)
                        break
                    }
                }
            }
        }

        val isDateLow = foundDate == null
        val finalDate = foundDate ?: LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
        val finalTime = foundTime ?: LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))

        return Triple(finalDate, finalTime, isDateLow)
    }

    /**
     * Normalisasi tanggal dari MatchResult ke format ISO yyyy-MM-dd.
     * Mendukung disambiguasi cerdas antara dd/MM/yyyy dan MM/dd/yyyy.
     */
    private fun parseNormalizedDate(match: MatchResult): String? {
        val raw = match.value.trim()
        val today = LocalDate.now()

        return try {
            // Cek apakah mengandung nama bulan teks (dd MMM yyyy atau MMM dd yyyy)
            val monthNameResult = tryParseTextMonth(raw)
            if (monthNameResult != null) return monthNameResult

            // Cek format kanji Jepang/Mandarin (yyyy年MM月dd日)
            val kanjiMatch = Regex("""(20\d{2})\s*年\s*(\d{1,2})\s*月\s*(\d{1,2})\s*日""").find(raw)
            if (kanjiMatch != null) {
                val y = kanjiMatch.groupValues[1].toInt()
                val m = kanjiMatch.groupValues[2].toInt()
                val d = kanjiMatch.groupValues[3].toInt()
                return safeDate(y, m, d)
            }

            // Numerik: split by /-.
            val cleaned = raw.replace('-', '/').replace('.', '/')
            val parts = cleaned.split('/').map { it.trim() }
            if (parts.size != 3) return null

            val nums = parts.map { it.toIntOrNull() ?: return null }

            if (nums[0] in 2020..2099) {
                // yyyy/MM/dd
                return safeDate(nums[0], nums[1], nums[2])
            }

            var year = nums[2]
            if (year < 100) year += 2000

            val a = nums[0] // bisa hari atau bulan
            val b = nums[1] // bisa hari atau bulan

            // Disambiguasi cerdas dd/MM vs MM/dd
            val aIsValidMonth = a in 1..12
            val bIsValidMonth = b in 1..12
            val aIsValidDay = a in 1..31
            val bIsValidDay = b in 1..31

            return when {
                // Jika hanya satu interpretasi yang valid
                aIsValidDay && bIsValidMonth && (!aIsValidMonth || !bIsValidDay) -> safeDate(year, b, a)  // dd/MM/yyyy
                aIsValidMonth && bIsValidDay && (!aIsValidDay || !bIsValidMonth) -> safeDate(year, a, b)  // MM/dd/yyyy
                // Jika keduanya ambigu, default ke dd/MM/yyyy (format paling umum di Indonesia & Asia)
                aIsValidDay && bIsValidMonth -> safeDate(year, b, a)
                aIsValidMonth && bIsValidDay -> safeDate(year, a, b)
                else -> null
            }
        } catch (_: Exception) {
            null
        }
    }

    /** Coba parsing tanggal dari nama bulan teks (dd MMM yyyy / MMM dd yyyy) multi-bahasa. */
    private fun tryParseTextMonth(raw: String): String? {
        val cleaned = raw.replace(",", "").replace(".", "").trim()
        val words = cleaned.split(Regex("""\s+"""))
        if (words.size < 3) return null

        // Cari kata yang cocok dengan nama bulan
        for (i in words.indices) {
            val monthNum = monthMap[words[i].lowercase(Locale.ROOT)]
            if (monthNum != null) {
                // Cek apakah angka di sekitarnya bisa jadi hari & tahun
                val before = if (i > 0) words[i - 1].replace(Regex("""[^\d]"""), "").toIntOrNull() else null
                val after = if (i < words.lastIndex) words[i + 1].replace(Regex("""[^\d]"""), "").toIntOrNull() else null
                val afterAfter = if (i + 2 <= words.lastIndex) words[i + 2].replace(Regex("""[^\d]"""), "").toIntOrNull() else null

                // Format: dd MMM yyyy
                if (before != null && before in 1..31 && after != null && after in 2000..2099) {
                    return safeDate(after, monthNum, before)
                }
                // Format: MMM dd yyyy  
                if (after != null && after in 1..31 && afterAfter != null && afterAfter in 2000..2099) {
                    return safeDate(afterAfter, monthNum, after)
                }
                // Format: MMM dd, yyyy (koma sudah di-strip)
                if (after != null && after in 1..31) {
                    val yearCandidate = afterAfter ?: (if (i + 2 <= words.lastIndex) words[i + 2].replace(Regex("""[^\d]"""), "").toIntOrNull() else null)
                    if (yearCandidate != null && yearCandidate in 2000..2099) {
                        return safeDate(yearCandidate, monthNum, after)
                    }
                }
            }
        }
        return null
    }

    /** Buat LocalDate dengan perlindungan terhadap tanggal invalid. */
    private fun safeDate(year: Int, month: Int, day: Int): String? {
        return try {
            if (year < 2000 || year > 2099 || month < 1 || month > 12 || day < 1 || day > 31) return null
            LocalDate.of(year, month, day).format(DateTimeFormatter.ISO_LOCAL_DATE)
        } catch (_: Exception) {
            null
        }
    }

    /** Normalisasi waktu dari MatchResult, termasuk konversi AM/PM ke 24-jam. */
    private fun parseNormalizedTime(match: MatchResult): String {
        var hour = match.groupValues[1].toIntOrNull() ?: 0
        val minute = match.groupValues[2].toIntOrNull() ?: 0
        val ampm = match.groupValues.getOrNull(4)?.uppercase(Locale.ROOT) ?: ""

        // Konversi AM/PM ke 24-jam
        if (ampm == "PM" && hour < 12) hour += 12
        if (ampm == "AM" && hour == 12) hour = 0

        return String.format(Locale.ROOT, "%02d:%02d", hour.coerceIn(0, 23), minute.coerceIn(0, 59))
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // TOTAL AMOUNT EXTRACTION
    // ═══════════════════════════════════════════════════════════════════════════

    private fun extractTotalAmount(lines: List<String>, items: List<ParsedReceiptItem>): Pair<Long?, Boolean> {
        // 1. Cari baris dengan kata kunci prioritas grand total
        for (line in lines) {
            val lower = line.lowercase(Locale.ROOT)
            if (isPaymentOrChangeLine(lower) || lower.contains("subtotal") || lower.contains("sub total")) continue

            if (totalPriorityKeywords.any { lower.contains(it) }) {
                val amount = extractMoneyValue(line)
                if (amount != null && amount > 0) {
                    return Pair(amount, false)
                }
            }
        }

        // 2. Cari baris yang mengandung "TOTAL" (bukan CASH/CHANGE/SUBTOTAL)
        for (line in lines.reversed()) {
            val lower = line.lowercase(Locale.ROOT)
            if (isPaymentOrChangeLine(lower) || lower.contains("subtotal") || lower.contains("sub total")) continue

            if (lower.contains("total") || lower.contains("合計") || lower.contains("총")) {
                val amount = extractMoneyValue(line)
                if (amount != null && amount > 0) {
                    return Pair(amount, false)
                }
            }
        }

        // 3. Penjumlahan harga item
        if (items.isNotEmpty()) {
            val itemsSum = items.sumOf { it.price }
            if (itemsSum > 0L) return Pair(itemsSum, false)
        }

        // 4. Cari SUBTOTAL sebagai fallback
        for (line in lines) {
            val lower = line.lowercase(Locale.ROOT)
            if (lower.contains("subtotal") || lower.contains("sub total") || lower.contains("小計") || lower.contains("소계")) {
                val amount = extractMoneyValue(line)
                if (amount != null && amount > 0) return Pair(amount, false)
            }
        }

        // 5. Fallback: nominal terbesar (mengabaikan CASH & CHANGE)
        val amountsFound = mutableListOf<Long>()
        for (line in lines) {
            val lower = line.lowercase(Locale.ROOT)
            if (isPaymentOrChangeLine(lower)) continue
            val amount = extractMoneyValue(line)
            if (amount != null && amount in 100..500_000_000) {
                amountsFound.add(amount)
            }
        }

        val maxAmount = amountsFound.maxOrNull()
        return if (maxAmount != null) Pair(maxAmount, true) else Pair(null, true)
    }

    private fun isPaymentOrChangeLine(lower: String): Boolean =
        paymentExcludeKeywords.any { lower.contains(it) }

    // ═══════════════════════════════════════════════════════════════════════════
    // ITEM EXTRACTION — Adaptif multi-format & multi-bahasa
    // ═══════════════════════════════════════════════════════════════════════════

    private fun extractItems(lines: List<String>): List<ParsedReceiptItem> {
        val items = mutableListOf<ParsedReceiptItem>()
        var previousLineCandidate: String? = null

        for (line in lines) {
            val lower = line.lowercase(Locale.ROOT)

            // Abaikan baris metadata, header, pembatas, total, cash, dll.
            if (isIgnoreLine(lower) ||
                isPaymentOrChangeLine(lower) ||
                lower.contains("subtotal") || lower.contains("sub total") || lower.contains("total") ||
                lower.contains("合計") || lower.contains("총") ||
                Regex("""(?i)^\s*no\b""").containsMatchIn(lower) ||
                Regex("""^\d+\s*item""").containsMatchIn(lower) ||
                line.all { !it.isLetterOrDigit() } ||
                // Skip baris yang hanya berisi tanggal atau waktu
                Regex("""^[\d/\-.:,\s]+$""").matches(line)
            ) {
                previousLineCandidate = null
                continue
            }

            val price = extractMoneyValue(line)
            if (price != null && price > 0) {
                // Hapus harga, kuantitas, dan simbol mata uang dari baris untuk mendapat nama item
                val textWithoutPrice = line
                    .replace(currencyPattern, "")
                    .replace(Regex("""(?i)\b\d{1,3}(?:[.,]\d{3})+(?:[.,]\d{2})?\b"""), "")
                    .replace(Regex("""\b\d{4,}\b"""), "")
                    .replace(Regex("""(?i)^\s*\d+\s*[xX×]\s*@?\s*"""), "")  // qty x @price
                    .replace(Regex("""(?i)@\s*\d+(?:[.,]\d+)*"""), "")       // @price
                    .replace(Regex("""(?i)\bqty\s*:?\s*\d+"""), "")          // qty: N
                    .replace(Regex("""[^\w\s\.\-&'()]"""), "")
                    .trim()

                var itemName = textWithoutPrice

                // Jika nama terlalu pendek, gunakan baris sebelumnya (format struk split-line)
                if (itemName.length < 2 && previousLineCandidate != null && previousLineCandidate.length >= 2) {
                    itemName = previousLineCandidate
                }

                itemName = itemName.replace(Regex("""^[0-9\s*.\-]+"""), "").trim()

                if (itemName.length >= 2 && !isIgnoreLine(itemName)) {
                    val formattedName = itemName.split(" ")
                        .filter { it.isNotBlank() }
                        .joinToString(" ") { word ->
                            word.lowercase(Locale.ROOT).replaceFirstChar {
                                if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString()
                            }
                        }
                    items.add(ParsedReceiptItem(name = formattedName, price = price))
                }
                previousLineCandidate = null
            } else {
                // Baris tanpa harga yang mungkin merupakan nama item di baris terpisah
                val cleanedCandidate = line
                    .replace(Regex("""[^\w\s\.\-&'()]"""), "")
                    .replace(Regex("""^[0-9\s*.\-]+"""), "")
                    .trim()
                previousLineCandidate = if (cleanedCandidate.length >= 2 && !isIgnoreLine(cleanedCandidate)) {
                    cleanedCandidate
                } else {
                    null
                }
            }
        }

        return items.take(30) // Maksimal 30 item untuk struk belanja besar
    }

    private fun isIgnoreLine(text: String): Boolean {
        val lower = text.lowercase(Locale.ROOT)
        return headerIgnoreKeywords.any { lower.contains(it) }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // MONEY VALUE EXTRACTION — Mendukung format nominal internasional
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Memparsing string angka menjadi Long nominal utuh.
     *
     * Mendukung format:
     * - Indonesia: Rp 25.000,00 → 25000
     * - US/UK:     $25,000.00 → 25000 (atau $25.00 → 2500? Disambiguasi via context)
     * - Eropa:     €25.000,00 → 25000
     * - Jepang:    ¥2,500 → 2500
     * - Tanpa simbol: 25000, 25.000, 25,000
     */
    fun extractMoneyValue(text: String): Long? {
        var cleaned = text
        // Hapus tanggal dan waktu agar angka tanggal/waktu tidak dianggap nominal uang
        for (regex in dateRegexes) {
            cleaned = cleaned.replace(regex, " ")
        }
        cleaned = cleaned.replace(timeRegex, " ")

        // Hapus kode referensi / nomor transaksi / ID alfanumerik
        cleaned = cleaned.replace(Regex("""(?i)\b(?:no|ref|inv|order|trans|id|check|store|batch|seq|auth|terminal)\s*[:.]?\s*[a-z0-9_\-]+\b"""), " ")

        // Hapus simbol mata uang
        cleaned = cleaned.replace(currencyPattern, " ").trim()

        // Pattern untuk mendeteksi angka dengan separator ribuan
        // Format 1: 25.000 atau 25.000,00 (Indonesia/Eropa — titik pemisah ribuan)
        // Format 2: 25,000 atau 25,000.00 (US/UK — koma pemisah ribuan)
        // Format 3: plain integer 25000
        val moneyPatterns = listOf(
            // Titik sebagai pemisah ribuan: 1.234 atau 12.345 atau 1.234.567 (bukan 12.34 yang desimal)
            Regex("""\b\d{1,3}(?:\.\d{3})+(?:,\d{1,2})?\b"""),
            // Koma sebagai pemisah ribuan: 1,234 atau 12,345 atau 1,234,567
            Regex("""\b\d{1,3}(?:,\d{3})+(?:\.\d{1,2})?\b"""),
            // Plain integer 4-9 digit (tanpa separator)
            Regex("""\b\d{3,9}\b""")
        )

        var lastValidAmount: Long? = null

        for (pattern in moneyPatterns) {
            val matcher = pattern.toPattern().matcher(cleaned)
            while (matcher.find()) {
                val raw = matcher.group()
                val value = normalizeMoneyString(raw)
                if (value != null && value in 100..500_000_000) {
                    lastValidAmount = value
                }
            }
            // Jika sudah ketemu dari pattern pertama (titik pemisah ribuan), prioritaskan itu
            if (lastValidAmount != null) break
        }

        return lastValidAmount
    }

    /**
     * Normalisasi string angka dengan berbagai format separator ke Long.
     * Mendukung disambiguasi titik vs koma sebagai pemisah ribuan atau desimal.
     */
    private fun normalizeMoneyString(raw: String): Long? {
        return try {
            val hasDot = raw.contains('.')
            val hasComma = raw.contains(',')

            val sanitized = when {
                // Titik sebagai pemisah ribuan, koma desimal (Indonesia/Eropa): 25.000,00 → 25000
                hasDot && hasComma && raw.lastIndexOf('.') < raw.lastIndexOf(',') -> {
                    raw.replace(".", "").replace(",", "")  // hapus semua, desimal diabaikan
                }
                // Koma sebagai pemisah ribuan, titik desimal (US/UK): 25,000.00 → 25000
                hasDot && hasComma && raw.lastIndexOf(',') < raw.lastIndexOf('.') -> {
                    raw.replace(",", "").replace(Regex("""\.\d{1,2}$"""), "")
                }
                // Hanya titik: bisa 25.000 (ribuan) atau 25.00 (desimal)
                hasDot && !hasComma -> {
                    // Jika pola xxx.xxx.xxx atau xxx.xxx → titik = pemisah ribuan
                    if (Regex("""\d{1,3}(\.\d{3})+""").matches(raw)) {
                        raw.replace(".", "")
                    } else {
                        // Titik desimal: 25.00 → 25, 125.50 → 125
                        raw.replace(Regex("""\.\d{1,2}$"""), "").replace(".", "")
                    }
                }
                // Hanya koma: bisa 25,000 (ribuan) atau 25,00 (desimal)
                hasComma && !hasDot -> {
                    if (Regex("""\d{1,3}(,\d{3})+""").matches(raw)) {
                        raw.replace(",", "")
                    } else {
                        raw.replace(Regex(""",\d{1,2}$"""), "").replace(",", "")
                    }
                }
                // Tanpa separator
                else -> raw
            }

            sanitized.toLongOrNull()
        } catch (_: Exception) {
            null
        }
    }
}
