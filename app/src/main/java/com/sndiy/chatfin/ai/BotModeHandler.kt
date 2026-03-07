package com.sndiy.chatfin.ai

import com.sndiy.chatfin.core.data.local.entity.CategoryEntity
import com.sndiy.chatfin.core.data.local.entity.WalletEntity
import java.text.NumberFormat
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

sealed class BotStep {
    object Idle : BotStep()
    data class WaitAmount(val type: String)                                          : BotStep()
    data class WaitCategory(val type: String, val amount: Long)                      : BotStep()
    data class WaitWallet(val type: String, val amount: Long, val category: String)  : BotStep()
    data class WaitDesc(val type: String, val amount: Long, val category: String, val wallet: String) : BotStep()
    data class WaitConfirm(
        val type: String, val amount: Long,
        val category: String, val wallet: String, val desc: String
    ) : BotStep()
}

data class BotResult(
    val text: String,
    val option: ChatOption?     = null,
    val saveTransaction: SaveRequest? = null,
    val nextStep: BotStep       = BotStep.Idle
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
                if (inline.isNotBlank() && amount != null) {
                    askCategory("INCOME", amount, incomeCategories)
                } else if (inline.isNotBlank()) {
                    BotResult("Nominal tidak valid. Contoh: setor 50rb", nextStep = BotStep.Idle)
                } else {
                    BotResult("💰 Berapa jumlah yang mau disetor?", nextStep = BotStep.WaitAmount("INCOME"))
                }
            }

            cmd == "tarik" || cmd.startsWith("tarik ") -> {
                val inline = raw.substringAfter(" ", "").trim()
                val amount = parseAmount(inline)
                if (inline.isNotBlank() && amount != null) {
                    askCategory("EXPENSE", amount, expenseCategories)
                } else if (inline.isNotBlank()) {
                    BotResult("Nominal tidak valid. Contoh: tarik 30rb", nextStep = BotStep.Idle)
                } else {
                    BotResult("💸 Berapa jumlah yang mau ditarik?", nextStep = BotStep.WaitAmount("EXPENSE"))
                }
            }

            cmd == "saldo" || cmd == "balance" -> {
                if (wallets.isEmpty()) {
                    BotResult("Belum ada dompet. Tambahkan dompet dulu di Setelan.")
                } else {
                    val lines = wallets.joinToString("\n") { w -> "• ${w.name}: ${rp(w.balance)}" }
                    BotResult("💼 *Saldo Dompet*\n\n$lines\n\n*Total: ${rp(totalBalance)}*")
                }
            }

            cmd == "rangkuman" || cmd == "summary" -> BotResult("__RANGKUMAN__")

            else -> BotResult("❓ Perintah tidak dikenal. Ketik *help* untuk melihat daftar perintah.")
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
                    BotResult(
                        "Nominal tidak valid 🤔\n" +
                                "Contoh yang bisa dipakai:\n" +
                                "• 50000\n• 50rb\n• 50k\n• 50_000\n• 1.5jt",
                        nextStep = step
                    )
                } else {
                    val cats = if (step.type == "INCOME") incomeCategories else expenseCategories
                    askCategory(step.type, amount, cats)
                }
            }

            is BotStep.WaitCategory -> {
                val cats = if (step.type == "INCOME") incomeCategories else expenseCategories
                val cat  = cats.find { it.name.equals(input, ignoreCase = true) }
                if (cat == null) askCategory(step.type, step.amount, cats, invalid = true)
                else askWallet(step.type, step.amount, cat.name, wallets)
            }

            is BotStep.WaitWallet -> {
                val wallet = wallets.find { it.name.equals(input, ignoreCase = true) }
                if (wallet == null) askWallet(step.type, step.amount, step.category, wallets, invalid = true)
                else BotResult(
                    "📝 Tambahkan deskripsi? (ketik deskripsi atau *skip*)",
                    nextStep = BotStep.WaitDesc(step.type, step.amount, step.category, wallet.name)
                )
            }

            is BotStep.WaitDesc -> {
                val desc = if (input.lowercase() == "skip") "" else input
                showConfirm(step.type, step.amount, step.category, step.wallet, desc)
            }

            is BotStep.WaitConfirm -> {
                when (input.lowercase().trim()) {
                    "ya", "y", "iya", "yes", "ok", "oke", "simpan" -> BotResult(
                        "✅ Transaksi berhasil disimpan!",
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
                        "❌ Transaksi dibatalkan.",
                        nextStep = BotStep.Idle
                    )
                    else -> BotResult(
                        "Ketik *ya* / *oke* untuk simpan atau *tidak* / *batal* untuk cancel.",
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
                "${prefix}${rp(amount)} — pilih kategori:",
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
                "${prefix}Pilih dompet:",
                option   = ChatOption.WalletOptions(wallets.map { it.name }),
                nextStep = BotStep.WaitWallet(type, amount, category)
            )
        }
    }

    private fun showConfirm(
        type: String, amount: Long,
        category: String, wallet: String, desc: String
    ): BotResult {
        val typeLabel = if (type == "INCOME") "Pemasukan" else "Pengeluaran"
        val descLine  = if (desc.isNotBlank()) "\n📋 Deskripsi : $desc" else ""
        return BotResult(
            text = "📋 *Konfirmasi $typeLabel*\n\n" +
                    "💰 Nominal  : ${rp(amount)}\n" +
                    "🏷️ Kategori : $category\n" +
                    "👛 Dompet   : $wallet$descLine\n\n" +
                    "Ketik *ya* untuk simpan atau *tidak* untuk batal.",
            nextStep = BotStep.WaitConfirm(type, amount, category, wallet, desc)
        )
    }

    private fun helpMessage() = BotResult(
        """
        🤖 *Mode Bot Aktif* (AI sedang tidak tersedia)
        
        Perintah yang tersedia:
        
        💰 *setor* [nominal]  → Catat pemasukan
           Contoh: setor 500rb
        
        💸 *tarik* [nominal]  → Catat pengeluaran
           Contoh: tarik 30k
        
        👛 *saldo*            → Lihat saldo dompet
        
        📊 *rangkuman*        → Ringkasan bulan ini
        
        ❓ *help*             → Tampilkan perintah ini
        
        💡 Format nominal yang didukung:
           50000 · 50rb · 50k · 50_000 · 1.5jt · 1,5jt
        
        💡 Bisa pakai slash: /setor, /tarik, dll.
        """.trimIndent()
    )

    fun parseAmount(input: String): Long? {
        if (input.isBlank()) return null

        // Bersihkan spasi dan underscore
        var clean = input.trim().lowercase()
            .replace("_", "")
            .replace(" ", "")
            .replace("rp", "")

        // Handle suffix juta: 1.5jt, 1,5jt, 2jt, 1.5 juta
        val jutaRegex  = Regex("""^([\d.,]+)\s*j(?:t|uta)?$""")
        val ribuRegex  = Regex("""^([\d.,]+)\s*(?:rb|ribu|k)$""")

        jutaRegex.find(clean)?.let { match ->
            val num = parseDecimal(match.groupValues[1]) ?: return null
            return (num * 1_000_000).toLong().takeIf { it > 0 }
        }

        ribuRegex.find(clean)?.let { match ->
            val num = parseDecimal(match.groupValues[1]) ?: return null
            return (num * 1_000).toLong().takeIf { it > 0 }
        }

        // Angka biasa — hapus titik sebagai pemisah ribuan, ganti koma jadi titik desimal
        clean = clean.replace(".", "").replace(",", ".")
        return clean.toDoubleOrNull()?.toLong()?.takeIf { it > 0 }
    }

    private fun parseDecimal(input: String): Double? {
        // Normalkan: ganti koma jadi titik untuk desimal
        val normalized = input.replace(",", ".")
        return normalized.toDoubleOrNull()
    }

    fun isBotCommand(input: String): Boolean {
        val cmd = input.trim().lowercase().trimStart('/')
        return cmd in listOf("help", "bantuan", "setor", "tarik", "saldo", "balance", "rangkuman", "summary") ||
                cmd.startsWith("setor ") || cmd.startsWith("tarik ")
    }
}