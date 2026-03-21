package com.sndiy.chatfin.ai

import com.sndiy.chatfin.core.data.local.entity.CategoryEntity
import com.sndiy.chatfin.core.data.local.entity.WalletEntity
import java.text.NumberFormat
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

sealed class BotStep {
    object Idle : BotStep()
    data class WaitAmount(val type: String) : BotStep()
    data class WaitCategory(val type: String, val amount: Long) : BotStep()
    data class WaitWallet(val type: String, val amount: Long, val category: String) : BotStep()
    data class WaitDesc(val type: String, val amount: Long, val category: String, val wallet: String) : BotStep()
    data class WaitConfirm(
        val type: String, val amount: Long,
        val category: String, val wallet: String, val desc: String
    ) : BotStep()
}

data class BotResult(
    val text: String,
    val option: ChatOption?           = null,
    val saveTransaction: SaveRequest? = null,
    val nextStep: BotStep             = BotStep.Idle,
    // Signal ke ViewModel: minta AI buat kalimat konfirmasi
    val requestAiConfirm: AiConfirmRequest? = null
)

data class AiConfirmRequest(
    val type: String,
    val amount: Long,
    val category: String,
    val wallet: String,
    val desc: String
)

data class SaveRequest(
    val type: String,
    val amount: Long,
    val categoryName: String,
    val walletName: String,
    val desc: String
)

@Singleton
class BotModeHandler @Inject constructor() {

    private val fmt = NumberFormat.getNumberInstance(Locale("id", "ID"))
    private fun rp(amount: Long) = "Rp ${fmt.format(amount)}"

    fun handle(
        input: String,
        currentStep: BotStep,
        wallets: List<WalletEntity>,
        expenseCategories: List<CategoryEntity>,
        incomeCategories: List<CategoryEntity>,
        totalBalance: Long
    ): BotResult {
        val raw = input.trim()
        val cmd = raw.lowercase().trimStart('/')

        if (currentStep !is BotStep.Idle) {
            return handleStep(currentStep, raw, wallets, expenseCategories, incomeCategories)
        }

        return when {
            cmd == "help" || cmd == "bantuan" -> helpMessage()

            cmd == "setor" || cmd.startsWith("setor ") -> {
                val inline = raw.substringAfter(" ", "").trim()
                val amount = parseAmount(inline)
                when {
                    inline.isNotBlank() && amount != null -> askCategory("INCOME", amount, incomeCategories)
                    inline.isNotBlank() -> BotResult("Nominal tidak valid. Contoh: setor 50rb", nextStep = BotStep.Idle)
                    else -> BotResult("💰 Berapa jumlah yang mau disetor?", nextStep = BotStep.WaitAmount("INCOME"))
                }
            }

            cmd == "tarik" || cmd.startsWith("tarik ") -> {
                val inline = raw.substringAfter(" ", "").trim()
                val amount = parseAmount(inline)
                when {
                    inline.isNotBlank() && amount != null -> askCategory("EXPENSE", amount, expenseCategories)
                    inline.isNotBlank() -> BotResult("Nominal tidak valid. Contoh: tarik 30rb", nextStep = BotStep.Idle)
                    else -> BotResult("💸 Berapa jumlah yang mau ditarik?", nextStep = BotStep.WaitAmount("EXPENSE"))
                }
            }

            cmd == "saldo" || cmd == "balance" -> {
                if (wallets.isEmpty()) {
                    BotResult("Belum ada dompet. Tambahkan dulu di Setelan.")
                } else {
                    val lines = wallets.joinToString("\n") { w -> "• ${w.name}: ${rp(w.balance)}" }
                    BotResult("💼 *Saldo Dompet*\n\n$lines\n\n*Total: ${rp(totalBalance)}*")
                }
            }

            cmd == "rangkuman" || cmd == "summary" -> BotResult("__RANGKUMAN__")

            else -> BotResult(
                "❓ Perintah tidak dikenal.\n\nKetik *help* untuk melihat daftar perintah yang tersedia."
            )
        }
    }

    private fun handleStep(
        step: BotStep,
        input: String,
        wallets: List<WalletEntity>,
        expenseCategories: List<CategoryEntity>,
        incomeCategories: List<CategoryEntity>
    ): BotResult {
        return when (step) {
            is BotStep.WaitAmount -> {
                val amount = parseAmount(input)
                if (amount == null) {
                    BotResult("Nominal tidak valid 🤔\nContoh: 50000 · 50rb · 50k · 1.5jt", nextStep = step)
                } else {
                    val cats = if (step.type == "INCOME") incomeCategories else expenseCategories
                    askCategory(step.type, amount, cats)
                }
            }

            is BotStep.WaitCategory -> {
                val cats = if (step.type == "INCOME") incomeCategories else expenseCategories
                val cat  = cats.find { it.name.equals(input, ignoreCase = true) }
                    ?: cats.find { it.name.contains(input, ignoreCase = true) }
                    ?: cats.find { input.contains(it.name, ignoreCase = true) }
                if (cat == null) askCategory(step.type, step.amount, cats, invalid = true)
                else askWallet(step.type, step.amount, cat.name, wallets)
            }

            is BotStep.WaitWallet -> {
                val wallet = wallets.find { it.name.equals(input, ignoreCase = true) }
                    ?: wallets.find { it.name.contains(input, ignoreCase = true) }
                    ?: wallets.find { input.contains(it.name, ignoreCase = true) }
                if (wallet == null) askWallet(step.type, step.amount, step.category, wallets, invalid = true)
                else BotResult(
                    text     = "Judul transaksi? (atau ketik *skip*)",
                    nextStep = BotStep.WaitDesc(step.type, step.amount, step.category, wallet.name)
                )
            }

            is BotStep.WaitDesc -> {
                val desc = if (input.lowercase() in listOf("skip", "-", "lewati", "")) "" else input
                // Semua data lengkap → minta AI buat konfirmasi
                BotResult(
                    text             = "",
                    nextStep         = BotStep.WaitConfirm(step.type, step.amount, step.category, step.wallet, desc),
                    requestAiConfirm = AiConfirmRequest(step.type, step.amount, step.category, step.wallet, desc)
                )
            }

            is BotStep.WaitConfirm -> {
                when (input.lowercase().trim()) {
                    "ya", "y", "iya", "yes", "ok", "oke", "simpan" -> BotResult(
                        text            = "✅ Tersimpan!",
                        saveTransaction = SaveRequest(
                            type         = step.type,
                            amount       = step.amount,
                            categoryName = step.category,
                            walletName   = step.wallet,
                            desc         = step.desc
                        ),
                        nextStep = BotStep.Idle
                    )
                    "tidak", "batal", "cancel", "no", "n" -> BotResult(
                        text     = "❌ Dibatalkan.",
                        nextStep = BotStep.Idle
                    )
                    else -> BotResult(
                        text     = "Ketik *ya* untuk simpan atau *tidak* untuk batal.",
                        nextStep = step
                    )
                }
            }

            BotStep.Idle -> BotResult("", nextStep = BotStep.Idle)
        }
    }

    private fun askCategory(
        type: String, amount: Long,
        cats: List<CategoryEntity>, invalid: Boolean = false
    ): BotResult {
        val prefix = if (invalid) "Kategori tidak ditemukan.\n" else ""
        return if (cats.isEmpty()) {
            BotResult("${prefix}Belum ada kategori. Tambahkan dulu di Setelan.", nextStep = BotStep.Idle)
        } else {
            BotResult(
                text     = "${prefix}${rp(amount)} — pilih kategori:",
                option   = ChatOption.CategoryOptions(cats.map { it.name }),
                nextStep = BotStep.WaitCategory(type, amount)
            )
        }
    }

    private fun askWallet(
        type: String, amount: Long, category: String,
        wallets: List<WalletEntity>, invalid: Boolean = false
    ): BotResult {
        val prefix = if (invalid) "Dompet tidak ditemukan.\n" else ""
        return if (wallets.isEmpty()) {
            BotResult("${prefix}Belum ada dompet.", nextStep = BotStep.Idle)
        } else {
            BotResult(
                text     = "${prefix}Oke, *$category*. Pilih dompet:",
                option   = ChatOption.WalletOptions(wallets.map { it.name }),
                nextStep = BotStep.WaitWallet(type, amount, category)
            )
        }
    }

    private fun helpMessage() = BotResult(
        """
        🤖 *Mode Bot* (AI sedang tidak tersedia)
        
        Mode ini aktif saat semua model AI sudah mencapai batas kuota.
        Gunakan perintah berikut untuk tetap mencatat transaksi:
        
        💰 *setor* [nominal]  → Catat pemasukan
           Contoh: setor 500rb
        
        💸 *tarik* [nominal]  → Catat pengeluaran
           Contoh: tarik 30k
        
        👛 *saldo*            → Lihat saldo dompet
        
        📊 *rangkuman*        → Ringkasan bulan ini
        
        ❓ *help*             → Tampilkan perintah ini
        
        💡 Format nominal: 50000 · 50rb · 50k · 50_000 · 1.5jt
        💡 Bisa pakai slash: /setor, /tarik, dll.
        
        Saat AI kembali tersedia, tekan *Coba lagi* di banner atas.
        """.trimIndent()
    )

    fun parseAmount(input: String): Long? {
        if (input.isBlank()) return null
        var clean = input.trim().lowercase()
            .replace("_", "")
            .replace(" ", "")
            .replace("rp", "")

        val jutaRegex = Regex("""^([\d.,]+)\s*j(?:t|uta)?$""")
        val ribuRegex = Regex("""^([\d.,]+)\s*(?:rb|ribu|k)$""")

        jutaRegex.find(clean)?.let { match ->
            val num = parseDecimal(match.groupValues[1]) ?: return null
            return (num * 1_000_000).toLong().takeIf { it > 0 }
        }

        ribuRegex.find(clean)?.let { match ->
            val num = parseDecimal(match.groupValues[1]) ?: return null
            return (num * 1_000).toLong().takeIf { it > 0 }
        }

        clean = clean.replace(".", "").replace(",", ".")
        return clean.toDoubleOrNull()?.toLong()?.takeIf { it > 0 }
    }

    private fun parseDecimal(input: String): Double? =
        input.replace(",", ".").toDoubleOrNull()
}