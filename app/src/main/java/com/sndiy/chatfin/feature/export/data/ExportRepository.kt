// app/src/main/java/com/sndiy/chatfin/feature/export/data/ExportRepository.kt

package com.sndiy.chatfin.feature.export.data

import android.content.Context
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import com.sndiy.chatfin.core.data.local.dao.AccountDao
import com.sndiy.chatfin.core.data.local.dao.CategoryDao
import com.sndiy.chatfin.core.data.local.dao.TransactionDao
import com.sndiy.chatfin.core.data.local.dao.WalletDao
import com.sndiy.chatfin.core.data.local.entity.TransactionEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import java.text.NumberFormat
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExportRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val accountDao: AccountDao,
    private val walletDao: WalletDao,
    private val categoryDao: CategoryDao,
    private val transactionDao: TransactionDao
) {
    private val fmt      = NumberFormat.getNumberInstance(Locale("id", "ID"))
    private val dateFmt  = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    private val labelFmt = DateTimeFormatter.ofPattern("dd MMMM yyyy", Locale("id", "ID"))
    private val shortFmt = DateTimeFormatter.ofPattern("dd MMM", Locale("id", "ID"))

    // =====================================================================
    //  CSV EXPORT — clean, Excel-friendly
    // =====================================================================
    suspend fun exportCsv(
        uri: Uri,
        accountId: String,
        startDate: LocalDate,
        endDate: LocalDate
    ): Result<String> {
        return try {
            val account = accountDao.getAccountById(accountId)
                ?: return Result.failure(Exception("Akun tidak ditemukan"))
            val transactions = transactionDao.getTransactionsByPeriod(
                accountId, startDate.format(dateFmt), endDate.format(dateFmt)
            ).first()
            val catMap    = buildCategoryMap(accountId)
            val walletMap = buildWalletMap(accountId)

            val totalIncome  = transactions.filter { it.type == "INCOME" }.sumOf { it.amount }
            val totalExpense = transactions.filter { it.type == "EXPENSE" }.sumOf { it.amount }

            val csv = buildString {
                // Metadata header (diawali # agar bisa diskip di Excel)
                appendLine("# Laporan Keuangan — ${account.name}")
                appendLine("# Periode: ${startDate.format(labelFmt)} s/d ${endDate.format(labelFmt)}")
                appendLine("# Diekspor: ${LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm", Locale("id", "ID")))}")
                appendLine("#")
                appendLine("# Total Pemasukan: Rp ${fmt.format(totalIncome)}")
                appendLine("# Total Pengeluaran: Rp ${fmt.format(totalExpense)}")
                appendLine("# Selisih: Rp ${fmt.format(totalIncome - totalExpense)}")
                appendLine("#")

                // Column headers
                appendLine("No,Tanggal,Waktu,Tipe,Kategori,Dompet,Nominal (Rp),Catatan")

                // Data rows
                transactions.forEachIndexed { index, tx ->
                    val no         = index + 1
                    val tipe       = when (tx.type) { "INCOME" -> "Pemasukan"; "EXPENSE" -> "Pengeluaran"; else -> "Transfer" }
                    val kategori   = (catMap[tx.categoryId] ?: "-").esc()
                    val dompet     = (walletMap[tx.walletId] ?: "-").esc()
                    val catatan    = (tx.note ?: "").esc()
                    appendLine("$no,${tx.date},${tx.time},$tipe,$kategori,$dompet,${tx.amount},$catatan")
                }

                // Summary footer
                appendLine()
                appendLine(",,,,,Total Pemasukan,${totalIncome},")
                appendLine(",,,,,Total Pengeluaran,${totalExpense},")
                appendLine(",,,,,Selisih,${totalIncome - totalExpense},")
            }

            context.contentResolver.openOutputStream(uri)?.use { stream ->
                stream.write(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())) // UTF-8 BOM
                stream.write(csv.toByteArray(Charsets.UTF_8))
            } ?: return Result.failure(Exception("Tidak bisa membuka file"))

            Result.success("${transactions.size} transaksi diekspor ke CSV")
        } catch (e: Exception) {
            Result.failure(Exception("Export CSV gagal: ${e.message}"))
        }
    }

    // =====================================================================
    //  PDF EXPORT — minimalis clean, garis tipis, font rapi
    // =====================================================================
    suspend fun exportPdf(
        uri: Uri,
        accountId: String,
        startDate: LocalDate,
        endDate: LocalDate
    ): Result<String> {
        return try {
            val account = accountDao.getAccountById(accountId)
                ?: return Result.failure(Exception("Akun tidak ditemukan"))
            val transactions = transactionDao.getTransactionsByPeriod(
                accountId, startDate.format(dateFmt), endDate.format(dateFmt)
            ).first()
            val wallets   = walletDao.getWalletsByAccount(accountId).first()
            val catMap    = buildCategoryMap(accountId)
            val walletMap = buildWalletMap(accountId)

            val totalIncome  = transactions.filter { it.type == "INCOME" }.sumOf { it.amount }
            val totalExpense = transactions.filter { it.type == "EXPENSE" }.sumOf { it.amount }
            val net          = totalIncome - totalExpense
            val period       = "${startDate.format(labelFmt)} — ${endDate.format(labelFmt)}"

            // ── Color palette (minimalis) ────────────────────────────────
            val cBlack      = 0xFF1A1A1A.toInt()
            val cDarkGray   = 0xFF4A4A4A.toInt()
            val cMedGray    = 0xFF8A8A8A.toInt()
            val cLightGray  = 0xFFE8E8E8.toInt()
            val cZebraGray  = 0xFFF7F7F7.toInt()
            val cWhite      = 0xFFFFFFFF.toInt()
            val cGreen      = 0xFF1B8A4C.toInt()
            val cRed         = 0xFFBA1A1A.toInt()
            val cAccentBg   = 0xFFF0F7F4.toInt()  // very light green
            val cAccentBgR  = 0xFFFFF5F5.toInt()  // very light red

            // ── Page setup ───────────────────────────────────────────────
            val pw = 595; val ph = 842  // A4
            val ml = 48f; val mr = 48f; val mt = 48f; val mb = 48f
            val cw = pw - ml - mr

            // ── Paints ───────────────────────────────────────────────────
            val pTitle = paint(18f, cBlack, bold = true)
            val pSubtitle = paint(10f, cMedGray)
            val pSection = paint(11f, cBlack, bold = true)
            val pBody = paint(9f, cDarkGray)
            val pBodyBold = paint(9f, cBlack, bold = true)
            val pSmall = paint(8f, cMedGray)
            val pGreen = paint(9f, cGreen, bold = true)
            val pRed = paint(9f, cRed, bold = true)
            val pGreenSmall = paint(11f, cGreen, bold = true)
            val pRedSmall = paint(11f, cRed, bold = true)
            val pTableHeader = paint(8f, cDarkGray, bold = true)
            val pTableBody = paint(8f, cDarkGray)
            val pLine = paint(0.5f, cLightGray).apply { style = Paint.Style.STROKE }
            val pLineMed = paint(0.5f, 0xFFD0D0D0.toInt()).apply { style = Paint.Style.STROKE }
            val pFillZebra = paint(0f, cZebraGray).apply { style = Paint.Style.FILL }
            val pFillGreen = paint(0f, cAccentBg).apply { style = Paint.Style.FILL }
            val pFillRed = paint(0f, cAccentBgR).apply { style = Paint.Style.FILL }
            val pFillWhite = paint(0f, cWhite).apply { style = Paint.Style.FILL }
            val pFooter = paint(7f, 0xFFB0B0B0.toInt())

            val doc = PdfDocument()
            var pageNum = 1
            var pageInfo = PdfDocument.PageInfo.Builder(pw, ph, pageNum).create()
            var page = doc.startPage(pageInfo)
            var c = page.canvas
            var y = mt

            fun newPage() {
                // Footer on current page
                c.drawText("ChatFin — Halaman $pageNum", pw / 2f, ph - 20f, pFooter.apply { textAlign = Paint.Align.CENTER })
                doc.finishPage(page)
                pageNum++
                pageInfo = PdfDocument.PageInfo.Builder(pw, ph, pageNum).create()
                page = doc.startPage(pageInfo)
                c = page.canvas
                y = mt
            }

            fun need(h: Float) { if (y + h > ph - mb) newPage() }

            // ══════════════════════════════════════════════════════════════
            //  HEADER
            // ══════════════════════════════════════════════════════════════
            c.drawText("Laporan Keuangan", ml, y + 18f, pTitle)
            y += 26f
            c.drawText(account.name, ml, y + 10f, paint(12f, cDarkGray))
            y += 18f
            c.drawText(period, ml, y + 10f, pSubtitle)
            y += 14f
            c.drawText(
                "Diekspor ${LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm", Locale("id", "ID")))}",
                ml, y + 10f, pSmall
            )
            y += 22f

            // Thin separator
            c.drawLine(ml, y, ml + cw, y, pLineMed)
            y += 16f

            // ══════════════════════════════════════════════════════════════
            //  SUMMARY CARDS (3 boxes side by side)
            // ══════════════════════════════════════════════════════════════
            val cardW = (cw - 16f) / 3f
            val cardH = 52f

            // Card 1: Pemasukan
            val x1 = ml
            c.drawRect(x1, y, x1 + cardW, y + cardH, pFillGreen)
            c.drawText("Pemasukan", x1 + 10f, y + 16f, pSmall)
            c.drawText("Rp ${fmt.format(totalIncome)}", x1 + 10f, y + 36f, pGreenSmall)

            // Card 2: Pengeluaran
            val x2 = ml + cardW + 8f
            c.drawRect(x2, y, x2 + cardW, y + cardH, pFillRed)
            c.drawText("Pengeluaran", x2 + 10f, y + 16f, pSmall)
            c.drawText("Rp ${fmt.format(totalExpense)}", x2 + 10f, y + 36f, pRedSmall)

            // Card 3: Selisih
            val x3 = ml + (cardW + 8f) * 2
            val netBg = if (net >= 0) pFillGreen else pFillRed
            val netPaint = if (net >= 0) pGreenSmall else pRedSmall
            c.drawRect(x3, y, x3 + cardW, y + cardH, netBg)
            c.drawText("Selisih Bersih", x3 + 10f, y + 16f, pSmall)
            c.drawText("${if (net >= 0) "+" else ""}Rp ${fmt.format(net)}", x3 + 10f, y + 36f, netPaint)

            y += cardH + 16f

            // ══════════════════════════════════════════════════════════════
            //  SALDO DOMPET
            // ══════════════════════════════════════════════════════════════
            if (wallets.isNotEmpty()) {
                c.drawText("Saldo Dompet", ml, y + 11f, pSection)
                y += 18f
                wallets.forEach { w ->
                    need(14f)
                    c.drawText(w.name, ml + 8f, y + 9f, pBody)
                    val balText = "Rp ${fmt.format(w.balance)}"
                    c.drawText(balText, ml + cw, y + 9f, pBodyBold.apply { textAlign = Paint.Align.RIGHT })
                    pBodyBold.textAlign = Paint.Align.LEFT
                    y += 14f
                }
                y += 8f
                c.drawLine(ml, y, ml + cw, y, pLineMed)
                y += 16f
            }

            // ══════════════════════════════════════════════════════════════
            //  TABEL TRANSAKSI
            // ══════════════════════════════════════════════════════════════
            c.drawText("Detail Transaksi (${transactions.size})", ml, y + 11f, pSection)
            y += 20f

            // Column layout: No | Tanggal | Kategori | Dompet | Nominal | Catatan
            val cols = floatArrayOf(
                ml,             // No        (30px)
                ml + 30f,       // Tanggal   (62px)
                ml + 92f,       // Kategori  (110px)
                ml + 202f,      // Dompet    (80px)
                ml + 282f,      // Nominal   (100px) RIGHT-aligned
                ml + 392f       // Catatan   (rest)
            )
            val colEnd = ml + cw
            val rowH = 18f

            // Table header
            need(rowH + 4f)
            c.drawRect(ml, y, colEnd, y + rowH, paint(0f, 0xFFF0F0F0.toInt()).apply { style = Paint.Style.FILL })
            c.drawText("No", cols[0] + 4f, y + 12f, pTableHeader)
            c.drawText("Tanggal", cols[1] + 4f, y + 12f, pTableHeader)
            c.drawText("Kategori", cols[2] + 4f, y + 12f, pTableHeader)
            c.drawText("Dompet", cols[3] + 4f, y + 12f, pTableHeader)
            c.drawText("Nominal", cols[5] - 8f, y + 12f, pTableHeader.apply { textAlign = Paint.Align.RIGHT })
            pTableHeader.textAlign = Paint.Align.LEFT
            c.drawText("Catatan", cols[5] + 4f, y + 12f, pTableHeader)
            y += rowH
            c.drawLine(ml, y, colEnd, y, pLineMed)
            y += 1f

            // Table rows
            transactions.forEachIndexed { index, tx ->
                need(rowH + 1f)

                // Zebra striping
                if (index % 2 == 1) {
                    c.drawRect(ml, y, colEnd, y + rowH, pFillZebra)
                }

                val rowY = y + 12f
                val isIncome = tx.type == "INCOME"
                val amountPaint = if (isIncome) pGreen else if (tx.type == "EXPENSE") pRed else pTableBody
                val prefix = if (isIncome) "+" else if (tx.type == "EXPENSE") "-" else ""

                c.drawText("${index + 1}", cols[0] + 4f, rowY, pSmall)
                c.drawText(formatDateShort(tx.date), cols[1] + 4f, rowY, pTableBody)
                c.drawText(trunc(catMap[tx.categoryId] ?: "-", 16), cols[2] + 4f, rowY, pTableBody)
                c.drawText(trunc(walletMap[tx.walletId] ?: "-", 11), cols[3] + 4f, rowY, pTableBody)
                c.drawText(
                    "${prefix}Rp ${fmt.format(tx.amount)}",
                    cols[5] - 8f, rowY,
                    amountPaint.apply { textAlign = Paint.Align.RIGHT }
                )
                amountPaint.textAlign = Paint.Align.LEFT
                c.drawText(trunc(tx.note ?: "", 18), cols[5] + 4f, rowY, pSmall)

                y += rowH
                // Light row separator
                c.drawLine(ml, y, colEnd, y, pLine)
                y += 1f
            }

            // ══════════════════════════════════════════════════════════════
            //  FOOTER
            // ══════════════════════════════════════════════════════════════
            y += 16f
            need(20f)
            c.drawLine(ml, y, ml + cw, y, pLineMed)
            y += 12f
            c.drawText(
                "${transactions.size} transaksi · ${period}",
                ml, y + 7f, pSmall
            )
            c.drawText(
                "Dibuat dengan ChatFin",
                ml + cw, y + 7f, pSmall.apply { textAlign = Paint.Align.RIGHT }
            )
            pSmall.textAlign = Paint.Align.LEFT

            // Page footer
            c.drawText("ChatFin — Halaman $pageNum", pw / 2f, ph - 20f, pFooter.apply { textAlign = Paint.Align.CENTER })
            doc.finishPage(page)

            // Write
            context.contentResolver.openOutputStream(uri)?.use { doc.writeTo(it) }
                ?: return Result.failure(Exception("Tidak bisa membuka file"))
            doc.close()

            Result.success("${transactions.size} transaksi diekspor ke PDF")
        } catch (e: Exception) {
            Result.failure(Exception("Export PDF gagal: ${e.message}"))
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun paint(size: Float, color: Int, bold: Boolean = false) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = size
        this.color = color
        typeface = if (bold) Typeface.create(Typeface.DEFAULT, Typeface.BOLD) else Typeface.DEFAULT
        textAlign = Paint.Align.LEFT
    }

    private fun formatDateShort(date: String): String {
        return try {
            LocalDate.parse(date).format(shortFmt)
        } catch (_: Exception) { date }
    }

    private suspend fun buildCategoryMap(accountId: String): Map<String, String> {
        val expense = categoryDao.getCategoriesByAccountAndType(accountId, "EXPENSE").first()
        val income  = categoryDao.getCategoriesByAccountAndType(accountId, "INCOME").first()
        return (expense + income).associate { it.id to it.name }
    }

    private suspend fun buildWalletMap(accountId: String): Map<String, String> =
        walletDao.getWalletsByAccount(accountId).first().associate { it.id to it.name }

    private fun String.esc(): String =
        if (contains(",") || contains("\"") || contains("\n")) "\"${replace("\"", "\"\"")}\"" else this

    private fun trunc(text: String, max: Int): String =
        if (text.length > max) text.take(max - 1) + "…" else text

    fun generateFileName(type: String): String {
        val ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
        return "chatfin_laporan_$ts.${if (type == "PDF") "pdf" else "csv"}"
    }
}
