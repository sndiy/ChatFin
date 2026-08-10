package com.sndiy.chatfin.ai

import com.sndiy.chatfin.core.data.local.entity.CategoryEntity
import com.sndiy.chatfin.core.data.local.entity.WalletEntity
import com.sndiy.chatfin.core.parser.AmountParser
import com.sndiy.chatfin.core.parser.ParsedDraft
import com.sndiy.chatfin.core.parser.TransactionQueryParser
import com.sndiy.chatfin.core.persona.PersonaVoice
import java.text.NumberFormat
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

sealed class BotStep {
    object Idle : BotStep()
    data class WaitAmount(val type: String) : BotStep()
    data class WaitCategory(
        val type: String, val amount: Long,
        val suggestedTitle: String? = null
    ) : BotStep()
    data class WaitWallet(
        val type: String, val amount: Long, val category: String,
        val suggestedTitle: String? = null
    ) : BotStep()
    data class WaitDesc(
        val type: String, val amount: Long, val category: String, val wallet: String,
        val suggestedTitle: String? = null
    ) : BotStep()
}

data class BotResult(
    val text: String,
    val option: ChatOption? = null,
    val nextStep: BotStep   = BotStep.Idle,
    // Signal ke ViewModel: minta AI buat kalimat konfirmasi
    val requestAiConfirm: AiConfirmRequest? = null
)

data class AiConfirmRequest(
    val type: String,
    val amount: Long,
    val category: String,
    val wallet: String,
    val desc: String
) {
    /** Konfirmasi versi aplikasi, dipakai saat AI tidak tersedia atau gagal. */
    fun toConfirm() = ChatOption.TransactionConfirm(
        type     = type,
        amount   = amount,
        category = category,
        wallet   = wallet,
        title    = desc.ifBlank { "$category $wallet" }
    )
}

@Singleton
class BotModeHandler @Inject constructor() {

    private val fmt = NumberFormat.getNumberInstance(Locale("id", "ID"))
    private fun rp(amount: Long) = "Rp ${fmt.format(amount)}"

    private val cancelWords = setOf("batal", "batalkan", "cancel", "stop", "berhenti")
    private val hintCancel  = "\n\n_Ketik *batal* kalau mau berhenti._"

    fun handle(
        input: String,
        currentStep: BotStep,
        wallets: List<WalletEntity>,
        expenseCategories: List<CategoryEntity>,
        incomeCategories: List<CategoryEntity>,
        totalBalance: Long,
        voice: PersonaVoice
    ): BotResult {
        val raw = input.trim()
        val cmd = raw.lowercase().trimStart('/')

        if (currentStep !is BotStep.Idle) {
            return handleStep(currentStep, raw, wallets, expenseCategories, incomeCategories, voice)
        }

        return when {
            cmd == "help" || cmd == "bantuan" -> helpMessage()

            cmd == "setor" || cmd.startsWith("setor ") -> {
                val inline = raw.substringAfter(" ", "").trim()
                val amount = parseAmount(inline)
                when {
                    inline.isNotBlank() && amount != null -> askCategory("INCOME", amount, incomeCategories, voice)
                    inline.isNotBlank() -> BotResult("Nominal tidak valid. Contoh: setor 50rb", nextStep = BotStep.Idle)
                    else -> BotResult(voice.askAmountIncome, nextStep = BotStep.WaitAmount("INCOME"))
                }
            }

            cmd == "tarik" || cmd.startsWith("tarik ") -> {
                val inline = raw.substringAfter(" ", "").trim()
                val amount = parseAmount(inline)
                when {
                    inline.isNotBlank() && amount != null -> askCategory("EXPENSE", amount, expenseCategories, voice)
                    inline.isNotBlank() -> BotResult("Nominal tidak valid. Contoh: tarik 30rb", nextStep = BotStep.Idle)
                    else -> BotResult(voice.askAmountExpense, nextStep = BotStep.WaitAmount("EXPENSE"))
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

            cmd == "chart" || cmd == "grafik" || cmd == "diagram" || cmd.contains("chart") || cmd.contains("grafik") || cmd.contains("visualisasi") -> {
                // Filter kategori/dompet/tipe diekstrak dari perintah yang sama
                // (mis. "grafik untuk kategori belanja dan makanan") — dulu
                // selalu diabaikan, chart offline selalu tampil tanpa filter
                // apa pun walau usernya sudah menyebutkannya.
                val allCats = expenseCategories + incomeCategories
                BotResult(
                    text = "Berikut visualisasi grafik keuanganmu. Kamu bisa memilih tipe chart, rentang tanggal, dan warna tema langsung lewat tombol di bawah:",
                    option = ChatOption.VisualizationRequest(
                        title = "Grafik Keuangan Pengeluaran",
                        categoryNames = TransactionQueryParser.matchAllNames(cmd, allCats.map { it.name }),
                        walletNames = TransactionQueryParser.matchAllNames(cmd, wallets.map { it.name }),
                        txType = TransactionQueryParser.detectType(cmd)
                    )
                )
            }

            cmd == "tabel" || cmd == "table" || cmd.contains("tabel") || cmd.contains("table") -> {
                val allCats = expenseCategories + incomeCategories
                BotResult(
                    text = "Berikut visualisasi tabel keuanganmu. Pilih template siap pakai atau biarkan AI mendesain formatnya lewat tombol di bawah:",
                    option = ChatOption.TableRequest(
                        title = "Tabel Ringkasan Keuangan",
                        categoryNames = TransactionQueryParser.matchAllNames(cmd, allCats.map { it.name }),
                        walletNames = TransactionQueryParser.matchAllNames(cmd, wallets.map { it.name }),
                        txType = TransactionQueryParser.detectType(cmd)
                    )
                )
            }

            else -> BotResult(
                "❓ Perintah tidak dikenal.\n\nKetik *help* untuk melihat daftar perintah yang tersedia."
            )
        }
    }

    // ── Masuk wizard dari hasil TransactionParser (M7) ────────────────────────
    // Beda dengan handle(input, ...) yang selalu mulai dari command mentah,
    // fungsi ini menerima draft yang SUDAH tahu amount (selalu, baik Complete
    // maupun Partial dari TransactionParser) dan kadang juga sudah tahu
    // kategori (Complete) — jadi step yang sudah terjawab oleh parser dilompati,
    // bukan ditanya ulang. Judul (title) TETAP ditanya di WaitDesc seperti
    // biasa — parser sudah menebak judul dari sisa teks, tapi user tetap
    // diberi kesempatan mengoreksi/menambah detail sebelum disimpan (judul
    // tebakan itu dibawa sebagai suggestedTitle dan dipakai kalau user melewati
    // langkah judul, supaya tidak jatuh ke judul sintetis "Kategori Dompet").
    fun handleParsed(
        draft: ParsedDraft,
        wallets: List<WalletEntity>,
        expenseCategories: List<CategoryEntity>,
        incomeCategories: List<CategoryEntity>,
        voice: PersonaVoice
    ): BotResult {
        val type   = draft.type ?: "EXPENSE"
        val amount = draft.amount ?: return BotResult("Nominal tidak dikenali 🤔", nextStep = BotStep.Idle)
        val cats   = if (type == "INCOME") incomeCategories else expenseCategories
        val title  = draft.title.takeIf { it.isNotBlank() }

        val category = draft.categoryName
            ?: return askCategory(type, amount, cats, voice, suggestedTitle = title)

        // Dompet yang sudah disebut di kalimat awal ("... dari BCA") tidak perlu
        // ditanya ulang — tapi hanya kalau benar-benar cocok dengan dompet yang
        // ada. Kalau tidak cocok, alurnya kembali normal (ditanyakan).
        val hinted = draft.walletHint?.let { matchWallet(it, wallets) }
        return if (hinted != null) {
            BotResult(
                text     = voice.askTitle,
                nextStep = BotStep.WaitDesc(type, amount, category, hinted.name, title)
            )
        } else {
            askWallet(type, amount, category, wallets, voice, suggestedTitle = title)
        }
    }

    /** Cocokkan teks bebas ke dompet yang benar-benar ada: persis dulu, baru longgar. */
    private fun matchWallet(input: String, wallets: List<WalletEntity>): WalletEntity? {
        if (input.isBlank()) return null
        return wallets.find { it.name.equals(input, ignoreCase = true) }
            ?: wallets.find { it.name.contains(input, ignoreCase = true) }
            ?: wallets.find { input.contains(it.name, ignoreCase = true) }
    }

    private fun handleStep(
        step: BotStep,
        input: String,
        wallets: List<WalletEntity>,
        expenseCategories: List<CategoryEntity>,
        incomeCategories: List<CategoryEntity>,
        voice: PersonaVoice
    ): BotResult {
        // Jalan keluar dari wizard di langkah mana pun. Tanpa ini, input yang
        // tidak pernah cocok membuat user terkunci di step yang sama selamanya
        // (satu-satunya cara keluar dulu: tutup chat atau putus internet).
        if (input.trim().lowercase().trimStart('/') in cancelWords) {
            return BotResult("Oke, transaksinya dibatalkan. 👌", nextStep = BotStep.Idle)
        }

        return when (step) {
            is BotStep.WaitAmount -> {
                val amount = parseAmount(input)
                if (amount == null) {
                    BotResult(
                        "Nominal tidak valid 🤔\nContoh: 50000 · 50rb · 50k · 1.5jt\n_Ketik *batal* untuk berhenti._",
                        nextStep = step
                    )
                } else {
                    val cats = if (step.type == "INCOME") incomeCategories else expenseCategories
                    askCategory(step.type, amount, cats, voice)
                }
            }

            is BotStep.WaitCategory -> {
                val cats = if (step.type == "INCOME") incomeCategories else expenseCategories
                val cat  = cats.find { it.name.equals(input, ignoreCase = true) }
                    ?: cats.find { it.name.contains(input, ignoreCase = true) }
                    ?: cats.find { input.contains(it.name, ignoreCase = true) }
                if (cat == null) askCategory(step.type, step.amount, cats, voice, invalid = true, suggestedTitle = step.suggestedTitle)
                else askWallet(step.type, step.amount, cat.name, wallets, voice, suggestedTitle = step.suggestedTitle)
            }

            is BotStep.WaitWallet -> {
                val wallet = matchWallet(input, wallets)
                if (wallet == null) askWallet(step.type, step.amount, step.category, wallets, voice, invalid = true, suggestedTitle = step.suggestedTitle)
                else BotResult(
                    text     = voice.askTitle,
                    nextStep = BotStep.WaitDesc(step.type, step.amount, step.category, wallet.name, step.suggestedTitle)
                )
            }

            is BotStep.WaitDesc -> {
                // Judul yang sudah ditebak parser dari kalimat awal dipakai kalau
                // user melewati langkah ini — informasi yang sudah ada tidak dibuang.
                val desc = if (input.lowercase() in listOf("skip", "-", "lewati", "")) {
                    step.suggestedTitle.orEmpty()
                } else input
                // Semua data lengkap → minta AI buat konfirmasi. ChatViewModel
                // langsung mengambil alih lewat requestAiConfirm dan menaruh
                // hasilnya di pendingTransaction (dikonfirmasi via tombol UI,
                // bukan mengetik ya/tidak) — makanya nextStep balik ke Idle,
                // bukan menunggu step lanjutan di sini.
                BotResult(
                    text             = "",
                    nextStep         = BotStep.Idle,
                    requestAiConfirm = AiConfirmRequest(step.type, step.amount, step.category, step.wallet, desc)
                )
            }

            BotStep.Idle -> BotResult("", nextStep = BotStep.Idle)
        }
    }

    private fun askCategory(
        type: String, amount: Long,
        cats: List<CategoryEntity>, voice: PersonaVoice, invalid: Boolean = false,
        suggestedTitle: String? = null
    ): BotResult {
        return if (cats.isEmpty()) {
            BotResult("Belum ada kategori. Tambahkan dulu di Setelan.", nextStep = BotStep.Idle)
        } else {
            BotResult(
                text     = voice.categoryPrompt(rp(amount), invalid) + if (invalid) hintCancel else "",
                option   = ChatOption.CategoryOptions(cats.map { it.name }),
                nextStep = BotStep.WaitCategory(type, amount, suggestedTitle)
            )
        }
    }

    private fun askWallet(
        type: String, amount: Long, category: String,
        wallets: List<WalletEntity>, voice: PersonaVoice, invalid: Boolean = false,
        suggestedTitle: String? = null
    ): BotResult {
        return if (wallets.isEmpty()) {
            BotResult("Belum ada dompet.", nextStep = BotStep.Idle)
        } else {
            BotResult(
                text     = voice.walletPrompt(category, invalid) + if (invalid) hintCancel else "",
                option   = ChatOption.WalletOptions(wallets.map { it.name }),
                nextStep = BotStep.WaitWallet(type, amount, category, suggestedTitle)
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

    // Implementasi sesungguhnya ada di AmountParser (murni, teruji unit test,
    // tanpa dependency Android) — dipakai bersama Mode Bot dan parser
    // transaksi rule-based lain. Method ini dipertahankan sebagai fasad
    // supaya pemanggil yang sudah ada (mis. ChatViewModel.isTransactionIntent)
    // tidak perlu diubah.
    fun parseAmount(input: String): Long? = AmountParser.parse(input)
}