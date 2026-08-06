package com.sndiy.chatfin.ai

/**
 * Slot transaksi yang terkumpul selama percakapan di JALUR AI.
 *
 * Sebelumnya jalur AI sama sekali tidak punya state di sisi aplikasi: entity
 * yang sudah diekstrak hanya hidup di dalam konteks model, dan satu-satunya
 * bentuk terstrukturnya (blok [CHATFIN_OPTIONS]) justru dibuang oleh
 * ChatOptionsParser sebelum teks masuk chatHistory. Akibatnya nominal yang
 * sudah disebut user di kalimat pertama bisa hilang dan ditanyakan lagi
 * beberapa giliran kemudian.
 *
 * AiDraft menutup celah itu: diisi dari parsing lokal tiap pesan user dan dari
 * pilihan chip, lalu (a) disuntikkan balik ke system prompt tiap giliran supaya
 * model tidak bertanya ulang, dan (b) dipakai menambal `confirm` yang datang
 * dengan slot kosong. Sengaja TIDAK dipakai untuk menyimpan otomatis — kartu
 * konfirmasi tetap wajib disetujui user.
 */
data class AiDraft(
    val type: String?         = null,
    val amount: Long?         = null,
    val categoryName: String? = null,
    val walletName: String?   = null,
    val title: String?        = null
) {
    val isEmpty: Boolean
        get() = type == null && amount == null && categoryName == null && walletName == null

    /** Semua slot wajib sudah terisi — transaksi bisa langsung dikonfirmasi. */
    val isReadyToConfirm: Boolean
        get() = type != null && (amount ?: 0L) > 0L &&
            !categoryName.isNullOrBlank() && !walletName.isNullOrBlank()

    fun toConfirm(): ChatOption.TransactionConfirm = ChatOption.TransactionConfirm(
        type     = type ?: "EXPENSE",
        amount   = amount ?: 0L,
        category = categoryName.orEmpty(),
        wallet   = walletName.orEmpty(),
        title    = title.orEmpty()
    )

    /**
     * Nilai baru hanya menimpa kalau benar-benar berisi — supaya informasi yang
     * sudah terkumpul tidak terhapus oleh giliran yang tidak menyebutkannya.
     */
    fun merge(
        type: String? = null,
        amount: Long? = null,
        categoryName: String? = null,
        walletName: String? = null,
        title: String? = null
    ): AiDraft = AiDraft(
        type         = type?.takeIf { it.isNotBlank() } ?: this.type,
        amount       = amount?.takeIf { it > 0L } ?: this.amount,
        categoryName = categoryName?.takeIf { it.isNotBlank() } ?: this.categoryName,
        walletName   = walletName?.takeIf { it.isNotBlank() } ?: this.walletName,
        title        = title?.takeIf { it.isNotBlank() } ?: this.title
    )
}

/**
 * Keputusan murni seputar slot transaksi di jalur AI — dipisah dari
 * ChatViewModel supaya bisa diuji tanpa Hilt/Room/Android, karena di sinilah
 * tiga bug penyimpanan yang paling berbahaya pernah bersarang.
 */
object ChatSlotResolver {

    /** Alasan sebuah `confirm` belum layak disimpan; null = lengkap. */
    fun missingSlot(confirm: ChatOption.TransactionConfirm): String? = when {
        confirm.amount <= 0L       -> "nominalnya belum jelas"
        confirm.wallet.isBlank()   -> "dompetnya belum dipilih"
        // Kategori WAJIB ikut dicek di sini. Tanpa ini, kategori kosong lolos ke
        // pencocokan nama — dan `"Makanan & Minuman".contains("")` bernilai true,
        // sehingga kategori PERTAMA terpilih diam-diam untuk transaksi apa pun
        // yang kategorinya tidak disebut (mis. tarik tunai).
        confirm.category.isBlank() -> "kategorinya belum dipilih"
        else                       -> null
    }

    /**
     * Tambal slot yang datang kosong dari model dengan yang sudah diketahui
     * aplikasi. Tanpa ini, `confirm` dengan amount 0 hanya menghasilkan kartu
     * konfirmasi mati padahal nominalnya sudah disebut user beberapa giliran
     * sebelumnya.
     */
    fun patch(confirm: ChatOption.TransactionConfirm, draft: AiDraft): ChatOption.TransactionConfirm =
        confirm.copy(
            type     = confirm.type.ifBlank { draft.type ?: "EXPENSE" },
            amount   = if (confirm.amount > 0L) confirm.amount else (draft.amount ?: 0L),
            category = confirm.category.ifBlank { draft.categoryName.orEmpty() },
            wallet   = confirm.wallet.ifBlank { draft.walletName.orEmpty() },
            title    = confirm.title.ifBlank { draft.title.orEmpty() }
        )

    /**
     * Pangkas daftar chip dari model ke data yang benar-benar ada, dan pakai
     * nama kanonik dari database.
     *
     * Isi `WalletOptions`/`CategoryOptions` sepenuhnya berasal dari JSON model
     * dan tidak pernah dicocokkan ke database — jadi dompet yang sudah dihapus
     * (masih tertulis di riwayat chat lama yang di-replay sebagai konteks) atau
     * nama karangan model tetap muncul sebagai tombol yang bisa ditekan. Kalau
     * tidak ada satu pun yang cocok, daftar asli dari database yang dipakai
     * supaya user tidak terjebak tanpa pilihan sama sekali.
     */
    fun sanitize(
        option: ChatOption?,
        realWallets: List<String>,
        realCategories: List<String>
    ): ChatOption? = when (option) {
        is ChatOption.WalletOptions ->
            ChatOption.WalletOptions(keepExisting(option.options, realWallets).ifEmpty { realWallets })
        is ChatOption.CategoryOptions ->
            ChatOption.CategoryOptions(keepExisting(option.options, realCategories).ifEmpty { realCategories })
        else -> option
    }

    fun keepExisting(offered: List<String>, real: List<String>): List<String> =
        offered.mapNotNull { offer -> real.find { it.equals(offer.trim(), ignoreCase = true) } }.distinct()
}
