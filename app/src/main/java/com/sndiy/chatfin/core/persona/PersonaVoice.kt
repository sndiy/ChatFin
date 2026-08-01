package com.sndiy.chatfin.core.persona

/**
 * Frasa Mode Bot (rule-based, TANPA AI) per persona — beda dari
 * [PersonaPreset.promptFragment] yang dikonsumsi Gemini. Mode Bot jalan saat
 * AI tidak tersedia (offline/tanpa API key/limit), jadi frasanya harus tetap
 * berupa teks tetap, bukan digenerate.
 *
 * Cakupan sengaja dibatasi ke frasa yang paling sering dilihat user
 * (keputusan produk M11, "Opsi B"): pindah-mode, wizard nominal/kategori/
 * dompet/judul, dan konfirmasi/batal. String teknis lain (mis. pesan nominal
 * tidak valid, "Perintah tidak dikenal") tetap satu gaya netral untuk semua
 * persona — lihat BotModeHandler.kt.
 */
data class PersonaVoice(
    val switchOffline: String,
    val switchBotMode: String,
    val switchNoApiKey: String,
    val askAmountIncome: String,
    val askAmountExpense: String,
    private val askCategoryPrompt: String,
    private val askCategoryInvalid: String,
    private val askWalletPrompt: String,
    private val askWalletInvalid: String,
    val askTitle: String
) {
    fun categoryPrompt(amountFormatted: String, invalid: Boolean): String =
        (if (invalid) askCategoryInvalid else askCategoryPrompt).replace("{amount}", amountFormatted)

    fun walletPrompt(category: String, invalid: Boolean): String =
        (if (invalid) askWalletInvalid else askWalletPrompt).replace("{category}", category)
}

object PersonaVoices {

    val MAI = PersonaVoice(
        switchOffline = "*menghela napas* Tidak ada koneksi. Aku pakai mode manual dulu ya. Ketik *help* kalau bingung.",
        switchBotMode = "*melipat tangan* Oke, aku pakai mode manual. Ketik *help* kalau kau lupa caranya.",
        switchNoApiKey = "*melirik* Belum ada API key AI. Aku pakai Mode Bot dulu — ketik *help* kalau bingung, atau atur API key di Setelan kalau mau aku lebih pintar.",
        askAmountIncome = "💰 Berapa jumlah yang mau disetor?",
        askAmountExpense = "💸 Berapa jumlah yang mau ditarik?",
        askCategoryPrompt = "{amount} — pilih kategori:",
        askCategoryInvalid = "Kategori tidak ditemukan.\n{amount} — pilih kategori:",
        askWalletPrompt = "Oke, *{category}*. Pilih dompet:",
        askWalletInvalid = "Dompet tidak ditemukan.\nOke, *{category}*. Pilih dompet:",
        askTitle = "Judul transaksi? (atau ketik *skip*)"
    )

    val ASISTEN = PersonaVoice(
        switchOffline = "Koneksi internet tidak tersedia. Sistem beralih ke Mode Bot. Ketik \"help\" untuk bantuan.",
        switchBotMode = "Beralih ke Mode Bot. Ketik \"help\" untuk melihat daftar perintah.",
        switchNoApiKey = "API key AI belum diatur. Sistem menggunakan Mode Bot. Atur API key di Setelan untuk mengaktifkan AI.",
        askAmountIncome = "Masukkan jumlah pemasukan.",
        askAmountExpense = "Masukkan jumlah pengeluaran.",
        askCategoryPrompt = "{amount}. Pilih kategori:",
        askCategoryInvalid = "Kategori tidak ditemukan. {amount}. Pilih kategori:",
        askWalletPrompt = "Kategori {category} dipilih. Pilih dompet:",
        askWalletInvalid = "Dompet tidak ditemukan. Kategori {category} dipilih. Pilih dompet:",
        askTitle = "Masukkan judul transaksi, atau ketik \"skip\" untuk melewati."
    )

    val SAHABAT = PersonaVoice(
        switchOffline = "Waduh, lagi nggak ada koneksi nih. Kita pakai Mode Bot dulu ya, ketik help kalau bingung.",
        switchBotMode = "Oke, kita pakai Mode Bot dulu. Ketik help kalau butuh bantuan ya!",
        switchNoApiKey = "Eh, API key AI-nya belum diisi nih. Kita pakai Mode Bot dulu deh — atau atur API key di Setelan kalau mau lebih canggih.",
        askAmountIncome = "Berapa nih jumlah pemasukannya?",
        askAmountExpense = "Berapa nih jumlah pengeluarannya?",
        askCategoryPrompt = "{amount} nih. Pilih kategorinya dong:",
        askCategoryInvalid = "Kategorinya nggak ketemu nih. {amount}. Coba pilih lagi:",
        askWalletPrompt = "Oke, kategori {category} ya. Dompetnya yang mana nih?",
        askWalletInvalid = "Dompetnya nggak ketemu nih. Kategori {category} ya. Coba pilih lagi:",
        askTitle = "Mau kasih judul apa nih? (atau ketik skip kalau nggak perlu)"
    )

    val PELATIH = PersonaVoice(
        switchOffline = "Koneksi terputus. Lanjut pakai Mode Bot. Ketik help kalau butuh panduan.",
        switchBotMode = "Mode Bot aktif. Ketik help untuk lihat perintahnya.",
        switchNoApiKey = "API key belum diatur. Pakai Mode Bot dulu. Atur di Setelan kalau mau AI aktif.",
        askAmountIncome = "Sebutkan jumlah pemasukan.",
        askAmountExpense = "Sebutkan jumlah pengeluaran.",
        askCategoryPrompt = "{amount}. Pilih kategori sekarang:",
        askCategoryInvalid = "Kategori tidak ditemukan. {amount}. Pilih lagi:",
        askWalletPrompt = "Kategori {category} dicatat. Pilih dompet:",
        askWalletInvalid = "Dompet tidak ditemukan. Kategori {category}. Pilih lagi:",
        askTitle = "Beri judul transaksi ini. Atau ketik skip."
    )

    fun byId(id: PersonaId): PersonaVoice = when (id) {
        PersonaId.MAI -> MAI
        PersonaId.ASISTEN -> ASISTEN
        PersonaId.SAHABAT -> SAHABAT
        PersonaId.PELATIH -> PELATIH
        // Mode Bot rule-based tidak bisa "mengarang" gaya dari teks bebas
        // custom user — pakai suara netral (Asisten) sebagai fallback yang
        // masuk akal, bukan diam-diam tetap Mai.
        PersonaId.CUSTOM -> ASISTEN
    }
}
