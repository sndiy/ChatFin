// app/src/main/java/com/sndiy/chatfin/ai/FinanceContextBuilder.kt

package com.sndiy.chatfin.ai

import com.sndiy.chatfin.core.data.local.entity.CategoryEntity
import com.sndiy.chatfin.core.data.local.entity.FinanceAccountEntity
import com.sndiy.chatfin.core.data.local.entity.WalletEntity
import java.text.NumberFormat
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Membangun konteks keuangan yang disuntikkan ke prompt AI
 * agar AI tahu kondisi keuangan user saat menjawab
 */
@Singleton
class FinanceContextBuilder @Inject constructor() {

    fun buildContext(
        account: FinanceAccountEntity?,
        wallets: List<WalletEntity>,
        expenseCategories: List<CategoryEntity>,
        incomeCategories: List<CategoryEntity>,
        totalIncome: Long,
        totalExpense: Long
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
                    appendLine("  - ${w.name} (${w.type}): Rp ${fmt.format(w.balance)}")
                }
            }

            appendLine()
            appendLine("Pemasukan bulan ini: Rp ${fmt.format(totalIncome)}")
            appendLine("Pengeluaran bulan ini: Rp ${fmt.format(totalExpense)}")
            appendLine("Net: Rp ${fmt.format(totalIncome - totalExpense)}")
            appendLine()

            appendLine("Kategori pengeluaran: ${expenseCategories.joinToString(", ") { it.name }}")
            appendLine("Kategori pemasukan: ${incomeCategories.joinToString(", ") { it.name }}")
            appendLine("=== AKHIR DATA KEUANGAN ===")
        }
    }
}