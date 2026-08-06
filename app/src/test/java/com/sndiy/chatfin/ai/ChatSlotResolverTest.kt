package com.sndiy.chatfin.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Test regresi untuk tiga bug penyimpanan di jalur AI:
 *  1. kategori kosong diam-diam jadi kategori PERTAMA ("Makanan & Minuman"),
 *  2. nominal yang sudah disebut hilang antar-giliran,
 *  3. chip dompet/kategori karangan model tampil sebagai tombol yang bisa ditekan.
 */
class ChatSlotResolverTest {

    // Urutan ini meniru DB: CategoryDao mengurutkan isCustom lalu sortOrder,
    // dan "Makanan & Minuman" (exp_food) punya sortOrder 0 — selalu pertama.
    private val expenseCategories = listOf("Makanan & Minuman", "Transportasi", "Belanja", "Lainnya")
    private val incomeCategories  = listOf("Gaji", "Bonus", "Lainnya")
    private val wallets           = listOf("Kas", "BCA", "GoPay")

    private fun confirm(
        type: String = "EXPENSE",
        amount: Long = 15000L,
        category: String = "Transportasi",
        wallet: String = "Kas",
        title: String = "Judul"
    ) = ChatOption.TransactionConfirm(type, amount, category, wallet, title)

    // ── missingSlot: kategori kosong tidak boleh lolos ────────────────────────

    @Test fun `confirm lengkap tidak punya slot yang kurang`() {
        assertNull(ChatSlotResolver.missingSlot(confirm()))
    }

    @Test fun `kategori kosong ditolak, bukan dibiarkan jatuh ke kategori pertama`() {
        // Regresi: `"Makanan & Minuman".contains("")` bernilai true, jadi
        // kategori kosong dulu diam-diam ter-resolve ke kategori pertama.
        assertEquals("kategorinya belum dipilih", ChatSlotResolver.missingSlot(confirm(category = "")))
    }

    @Test fun `kategori berisi spasi saja juga ditolak`() {
        assertEquals("kategorinya belum dipilih", ChatSlotResolver.missingSlot(confirm(category = "   ")))
    }

    @Test fun `nominal nol ditolak`() {
        assertEquals("nominalnya belum jelas", ChatSlotResolver.missingSlot(confirm(amount = 0L)))
    }

    @Test fun `dompet kosong ditolak`() {
        assertEquals("dompetnya belum dipilih", ChatSlotResolver.missingSlot(confirm(wallet = "")))
    }

    // ── patch: slot yang sudah diketahui aplikasi menambal confirm ────────────

    @Test fun `nominal yang hilang dari model diambil dari draft`() {
        // Skenario asli: user menyebut 15rb di kalimat pertama, lalu memilih
        // kategori & dompet; model kehilangan nominalnya di giliran konfirmasi.
        val draft  = AiDraft(amount = 15000L)
        val patched = ChatSlotResolver.patch(confirm(amount = 0L), draft)
        assertEquals(15000L, patched.amount)
        assertNull(ChatSlotResolver.missingSlot(patched))
    }

    @Test fun `nominal dari model menang atas draft kalau ada`() {
        val patched = ChatSlotResolver.patch(confirm(amount = 20000L), AiDraft(amount = 15000L))
        assertEquals(20000L, patched.amount)
    }

    @Test fun `kategori dan dompet kosong ditambal dari draft`() {
        val draft = AiDraft(categoryName = "Transportasi", walletName = "BCA")
        val patched = ChatSlotResolver.patch(confirm(category = "", wallet = ""), draft)
        assertEquals("Transportasi", patched.category)
        assertEquals("BCA", patched.wallet)
    }

    @Test fun `patch tanpa draft tidak mengarang nilai`() {
        val patched = ChatSlotResolver.patch(confirm(amount = 0L, category = ""), AiDraft())
        assertEquals(0L, patched.amount)
        assertEquals("", patched.category)
        // Tetap tidak lengkap → user diberi tahu, bukan disimpan diam-diam.
        assertEquals("nominalnya belum jelas", ChatSlotResolver.missingSlot(patched))
    }

    // ── merge: informasi lama tidak boleh terhapus giliran berikutnya ─────────

    @Test fun `merge tidak menghapus slot yang sudah terisi`() {
        val draft = AiDraft(amount = 15000L, categoryName = "Transportasi")
            .merge(walletName = "GoPay")
        assertEquals(15000L, draft.amount)
        assertEquals("Transportasi", draft.categoryName)
        assertEquals("GoPay", draft.walletName)
    }

    @Test fun `merge mengabaikan nilai kosong dan nol`() {
        val draft = AiDraft(amount = 15000L, walletName = "BCA")
            .merge(amount = 0L, walletName = "", categoryName = "  ")
        assertEquals(15000L, draft.amount)
        assertEquals("BCA", draft.walletName)
        assertNull(draft.categoryName)
    }

    // ── sanitize: chip harus mencerminkan data yang benar-benar ada ───────────

    @Test fun `dompet yang tidak ada di database dibuang dari chip`() {
        // Regresi gejala utama: nama dompet lama/karangan model tetap tampil
        // sebagai tombol karena daftar chip tidak pernah dicocokkan ke DB.
        val option = ChatOption.WalletOptions(listOf("Kas", "Dompet Hantu", "BCA"))
        val result = ChatSlotResolver.sanitize(option, wallets, expenseCategories)
        assertEquals(listOf("Kas", "BCA"), (result as ChatOption.WalletOptions).options)
    }

    @Test fun `chip dompet jatuh ke daftar database kalau tidak ada yang cocok`() {
        val option = ChatOption.WalletOptions(listOf("Jenius", "Seabank"))
        val result = ChatSlotResolver.sanitize(option, wallets, expenseCategories)
        assertEquals(wallets, (result as ChatOption.WalletOptions).options)
    }

    @Test fun `nama kanonik database yang dipakai walau model salah kapitalisasi`() {
        val option = ChatOption.WalletOptions(listOf("  gopay  "))
        val result = ChatSlotResolver.sanitize(option, wallets, expenseCategories)
        assertEquals(listOf("GoPay"), (result as ChatOption.WalletOptions).options)
    }

    @Test fun `chip kategori dibatasi ke daftar yang diberikan`() {
        val option = ChatOption.CategoryOptions(listOf("Transportasi", "Tarik Tunai", "Belanja"))
        val result = ChatSlotResolver.sanitize(option, wallets, expenseCategories)
        assertEquals(listOf("Transportasi", "Belanja"), (result as ChatOption.CategoryOptions).options)
    }

    @Test fun `chip kategori INCOME tidak boleh berisi kategori EXPENSE`() {
        // Pemanggil menyerahkan pool sesuai tipe; "Makanan & Minuman" tidak ada
        // di daftar INCOME sehingga harus tersaring.
        val option = ChatOption.CategoryOptions(listOf("Gaji", "Makanan & Minuman"))
        val result = ChatSlotResolver.sanitize(option, wallets, incomeCategories)
        assertEquals(listOf("Gaji"), (result as ChatOption.CategoryOptions).options)
    }

    @Test fun `duplikat dari model dibuang`() {
        val option = ChatOption.WalletOptions(listOf("Kas", "kas", "KAS"))
        val result = ChatSlotResolver.sanitize(option, wallets, expenseCategories)
        assertEquals(listOf("Kas"), (result as ChatOption.WalletOptions).options)
    }

    @Test fun `option selain daftar chip dibiarkan apa adanya`() {
        val option = confirm()
        assertEquals(option, ChatSlotResolver.sanitize(option, wallets, expenseCategories))
        assertNull(ChatSlotResolver.sanitize(null, wallets, expenseCategories))
    }
}
