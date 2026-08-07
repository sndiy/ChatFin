// app/src/main/java/com/sndiy/chatfin/ai/FinanceContextBuilder.kt

package com.sndiy.chatfin.ai

import com.sndiy.chatfin.core.data.local.entity.CategoryEntity
import com.sndiy.chatfin.core.data.local.entity.FinanceAccountEntity
import com.sndiy.chatfin.core.data.local.entity.TransactionEntity
import com.sndiy.chatfin.core.data.local.entity.WalletEntity
import java.text.NumberFormat
import java.time.LocalDate
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Membangun konteks keuangan yang disuntikkan ke prompt AI
 * agar AI tahu kondisi keuangan user saat menjawab
 */
@Singleton
class FinanceContextBuilder @Inject constructor() {

    /**
     * [transactions] TIDAK dikirim baris per baris ke AI — cuma diringkas jadi
     * peringkat kategori di bawah. Alasannya: pertanyaan wajar seperti
     * "pengeluaran paling besar apa?" dulu tidak bisa dijawab sama sekali
     * (konteks hanya berisi total), sementara mengirim seluruh baris transaksi
     * akan membengkakkan token tiap giliran dan tetap tidak dipercaya untuk
     * ditampilkan (lihat larangan detail transaksi satuan di SystemPromptBuilder).
     */
    fun buildContext(
        account: FinanceAccountEntity?,
        wallets: List<WalletEntity>,
        expenseCategories: List<CategoryEntity>,
        incomeCategories: List<CategoryEntity>,
        totalIncome: Long,
        totalExpense: Long,
        transactions: List<TransactionEntity> = emptyList(),
        today: LocalDate = LocalDate.now()
    ): String {
        val fmt = NumberFormat.getNumberInstance(Locale("id", "ID"))
        val totalBalance = wallets.sumOf { it.balance }

        return buildString {
            appendLine("=== DATA KEUANGAN USER (gunakan untuk membantu) ===")
            appendLine("Akun aktif: ${account?.name ?: "Tidak ada"}")
            appendLine("Total saldo: Rp ${fmt.format(totalBalance)}")
            appendLine()

            if (wallets.isNotEmpty()) {
                appendLine("Dompet yang dimiliki:")
                wallets.forEach { w ->
                    appendLine("  - ${w.name}: Rp ${fmt.format(w.balance)}")  // hapus (${w.type})
                }
            }

            appendLine()
            appendLine("Pemasukan bulan ini: Rp ${fmt.format(totalIncome)}")
            appendLine("Pengeluaran bulan ini: Rp ${fmt.format(totalExpense)}")
            appendLine("Net: Rp ${fmt.format(totalIncome - totalExpense)}")
            appendLine()

            val monthPrefix = today.toString().take(7)   // "yyyy-MM"
            val allCategories = expenseCategories + incomeCategories
            val topExpense = transactions
                .filter { it.type == "EXPENSE" && it.date.startsWith(monthPrefix) }
                .groupBy { it.categoryId }
                .map { (categoryId, rows) ->
                    Triple(
                        allCategories.find { it.id == categoryId }?.name ?: "Tanpa kategori",
                        rows.sumOf { it.amount },
                        rows.size
                    )
                }
                .sortedByDescending { it.second }
                .take(5)

            if (topExpense.isNotEmpty()) {
                appendLine("Pengeluaran terbesar bulan ini (urut dari yang paling besar):")
                topExpense.forEachIndexed { index, (name, amount, count) ->
                    val share = if (totalExpense > 0) (amount * 100 / totalExpense) else 0
                    appendLine("  ${index + 1}. $name: Rp ${fmt.format(amount)} ($count transaksi, $share% dari total)")
                }
                appendLine()
            }

            appendLine("Kategori pengeluaran: ${expenseCategories.joinToString(", ") { it.name }}")
            appendLine("Kategori pemasukan: ${incomeCategories.joinToString(", ") { it.name }}")
            appendLine("=== AKHIR DATA KEUANGAN ===")
        }
    }
}