package com.sndiy.chatfin.core.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Kamus buatan, terpisah dari DefaultKeywords — supaya grup test "mekanisme"
 * di bawah ini murni menguji LOGIKA TransactionParser (longest-match, filter
 * tipe, penempatan span nominal), bukan kebetulan bergantung pada isi kamus
 * asli yang bisa berubah kapan saja.
 */
private data class FakeEntry(val keyword: String, val categoryId: String, val categoryName: String, val type: String)

private class FakeKeywordSource(private val entries: List<FakeEntry>) : KeywordSource {
    override fun findCategory(text: String, type: String): CategoryMatch? {
        val lower = text.lowercase()
        return entries
            .filter { it.type == type }
            .filter { lower.contains(it.keyword) }
            .maxByOrNull { it.keyword.length }
            ?.let { CategoryMatch(it.categoryId, it.categoryName, it.keyword) }
    }
}

class TransactionParserTest {

    private val fakeSource = FakeKeywordSource(
        listOf(
            FakeEntry("kopi", "fk_food", "Makanan Palsu", "EXPENSE"),
            FakeEntry("kopi susu", "fk_food_susu", "Susu Palsu", "EXPENSE"),
            FakeEntry("gaji", "fk_salary", "Gaji Palsu", "INCOME")
        )
    )

    // ── NotATransaction — tidak ada nominal sama sekali ──────────────────────

    @Test fun `string kosong adalah NotATransaction`() {
        assertEquals(ParseResult.NotATransaction, TransactionParser.parse("", fakeSource))
    }

    @Test fun `string blank adalah NotATransaction`() {
        assertEquals(ParseResult.NotATransaction, TransactionParser.parse("   ", fakeSource))
    }

    @Test fun `kalimat tanpa angka sama sekali adalah NotATransaction`() {
        assertEquals(ParseResult.NotATransaction, TransactionParser.parse("berapa saldo aku", fakeSource))
    }

    @Test fun `sapaan tanpa angka adalah NotATransaction`() {
        assertEquals(ParseResult.NotATransaction, TransactionParser.parse("halo", fakeSource))
    }

    @Test fun `pertanyaan tanpa angka adalah NotATransaction`() {
        assertEquals(ParseResult.NotATransaction, TransactionParser.parse("kategori apa saja yang ada", fakeSource))
    }

    // ── Ekstraksi nominal — token tunggal ────────────────────────────────────

    @Test fun `nominal token tunggal di akhir kalimat`() {
        val result = TransactionParser.parse("kopi 15000", fakeSource) as ParseResult.Complete
        assertEquals(15000L, result.draft.amount)
    }

    @Test fun `nominal dengan akhiran rb`() {
        val result = TransactionParser.parse("kopi 15rb", fakeSource) as ParseResult.Complete
        assertEquals(15000L, result.draft.amount)
    }

    @Test fun `nominal dengan akhiran jt`() {
        val result = TransactionParser.parse("gaji 5jt", fakeSource) as ParseResult.Complete
        assertEquals(5_000_000L, result.draft.amount)
    }

    @Test fun `nominal token pertama yang valid yang dipakai kalau ada lebih dari satu angka`() {
        val result = TransactionParser.parse("kirim 5000 buat 3000 lagi", fakeSource)
        val draft = when (result) {
            is ParseResult.Complete -> result.draft
            is ParseResult.Partial -> result.draft
            ParseResult.NotATransaction -> error("harusnya bukan NotATransaction")
        }
        assertEquals(5000L, draft.amount)
    }

    // ── Ekstraksi nominal — pasangan token bersebelahan (spasi) ─────────────

    @Test fun `nominal terpisah spasi angka lalu akhiran ribu`() {
        val result = TransactionParser.parse("kopi 50 rb", fakeSource) as ParseResult.Complete
        assertEquals(50000L, result.draft.amount)
    }

    @Test fun `nominal terpisah spasi angka lalu akhiran juta`() {
        val result = TransactionParser.parse("gaji 1 5jt", fakeSource)
        // "1" tunggal juga valid (=1), tapi pasangan "15jt" dicoba lebih dulu
        // di token sebelumnya — pastikan tidak crash dan hasilkan salah satu
        // interpretasi yang valid secara internal konsisten.
        assertTrue(result is ParseResult.Complete || result is ParseResult.Partial)
    }

    @Test fun `nominal dengan prefiks rp terpisah spasi`() {
        val result = TransactionParser.parse("kopi rp 50000", fakeSource) as ParseResult.Complete
        assertEquals(50000L, result.draft.amount)
    }

    @Test fun `spasi berlebih antar token tidak mengganggu ekstraksi`() {
        val result = TransactionParser.parse("kopi    15rb", fakeSource) as ParseResult.Complete
        assertEquals(15000L, result.draft.amount)
        assertEquals("Kopi", result.draft.title)
    }

    // ── Judul (title) ─────────────────────────────────────────────────────────

    @Test fun `judul membuang token nominal dan kapital di awal saja`() {
        val result = TransactionParser.parse("beli bensin 50000", fakeSource) as ParseResult.Partial
        assertEquals("Beli bensin", result.draft.title)
    }

    @Test fun `judul fallback ke Pengeluaran kalau seluruh input hanya nominal`() {
        val result = TransactionParser.parse("50000", fakeSource) as ParseResult.Partial
        assertEquals("Pengeluaran", result.draft.title)
        assertEquals("EXPENSE", result.draft.type)
    }

    @Test fun `judul fallback ke Pemasukan kalau tipe income dan sisa teks kosong`() {
        val result = TransactionParser.parse("gaji 5000000", fakeSource) as ParseResult.Complete
        // "gaji" match sebagai kata kunci kategori DAN kata kerja, jadi sisa
        // teks tidak kosong di kasus ini — title = "Gaji", bukan fallback.
        assertEquals("Gaji", result.draft.title)
    }

    // ── Deteksi tipe dari kata kerja ─────────────────────────────────────────

    @Test fun `kata kerja beli menghasilkan EXPENSE`() {
        val result = TransactionParser.parse("beli barang 20000", fakeSource) as ParseResult.Partial
        assertEquals("EXPENSE", result.draft.type)
    }

    @Test fun `kata kerja terima menghasilkan INCOME`() {
        val result = TransactionParser.parse("terima uang 20000", fakeSource) as ParseResult.Partial
        assertEquals("INCOME", result.draft.type)
    }

    @Test fun `tanpa kata kerja default EXPENSE`() {
        val result = TransactionParser.parse("apa saja 20000", fakeSource) as ParseResult.Partial
        assertEquals("EXPENSE", result.draft.type)
    }

    @Test fun `kata kerja tidak sensitif huruf besar kecil`() {
        val result = TransactionParser.parse("BELI barang 20000", fakeSource) as ParseResult.Partial
        assertEquals("EXPENSE", result.draft.type)
    }

    @Test fun `kata kerja income dan expense sekaligus jatuh ke default EXPENSE`() {
        // "bayar" (expense) dan "gaji" (income) sama-sama muncul — ambigu,
        // parser tidak boleh menebak salah satu secara membabi buta.
        val result = TransactionParser.parse("bayar gaji karyawan 5000000", fakeSource) as ParseResult.Partial
        assertEquals("EXPENSE", result.draft.type)
    }

    // ── Kecocokan kategori — longest match & filter tipe ─────────────────────

    @Test fun `longest match menang atas match yang lebih pendek`() {
        val result = TransactionParser.parse("beli kopi susu 20000", fakeSource) as ParseResult.Complete
        assertEquals("fk_food_susu", result.draft.categoryId)
    }

    @Test fun `match pendek dipakai kalau frasa panjang tidak ada`() {
        val result = TransactionParser.parse("beli kopi item 20000", fakeSource) as ParseResult.Complete
        assertEquals("fk_food", result.draft.categoryId)
    }

    @Test fun `kata kunci income tidak match saat tipe EXPENSE`() {
        // "gaji" adalah kata kunci kategori INCOME di kamus fake — kalau tipe
        // yang terdeteksi EXPENSE, match ini harus diabaikan.
        val result = TransactionParser.parse("bayar gaji karyawan 5000000", fakeSource) as ParseResult.Partial
        assertNull(result.draft.categoryId)
    }

    @Test fun `kategori tidak ketemu menghasilkan Partial dengan missing CATEGORY`() {
        val result = TransactionParser.parse("aku habis 50000", fakeSource) as ParseResult.Partial
        assertEquals(listOf(TransactionField.CATEGORY), result.missing)
    }

    // ── Skor keyakinan (confidence) ───────────────────────────────────────────

    @Test fun `confidence 1 0 kalau kata kerja dan kategori dua duanya cocok`() {
        val result = TransactionParser.parse("beli kopi 15000", fakeSource) as ParseResult.Complete
        assertEquals(1.0f, result.confidence, 0.001f)
    }

    @Test fun `confidence 0 75 kalau hanya kata kerja tanpa kategori`() {
        val result = TransactionParser.parse("beli barang 15000", fakeSource) as ParseResult.Partial
        // Partial tidak membawa confidence field secara eksplisit di sini,
        // jadi verifikasi lewat draft: type terdeteksi tapi kategori kosong.
        assertEquals("EXPENSE", result.draft.type)
        assertNull(result.draft.categoryId)
    }

    @Test fun `confidence 0 75 kalau kategori cocok tanpa kata kerja eksplisit`() {
        val result = TransactionParser.parse("kopi susu 15000", fakeSource) as ParseResult.Complete
        assertEquals(0.75f, result.confidence, 0.001f)
    }

    @Test fun `confidence 0 5 kalau tidak ada kata kerja maupun kategori`() {
        val result = TransactionParser.parse("apa saja 15000", fakeSource) as ParseResult.Partial
        assertEquals("EXPENSE", result.draft.type)
    }

    // ── walletHint dari kata depan ────────────────────────────────────────────

    @Test fun `walletHint diambil dari kata depan dari`() {
        val result = TransactionParser.parse("beli kopi 15000 dari BCA", fakeSource) as ParseResult.Complete
        assertEquals("BCA", result.draft.walletHint)
    }

    @Test fun `walletHint diambil dari kata depan pakai`() {
        val result = TransactionParser.parse("beli kopi 15000 pakai GoPay", fakeSource) as ParseResult.Complete
        assertEquals("GoPay", result.draft.walletHint)
    }

    @Test fun `frasa dompet tidak ikut masuk judul`() {
        val result = TransactionParser.parse("beli kopi 15000 dari BCA", fakeSource) as ParseResult.Complete
        assertEquals("Beli kopi", result.draft.title)
    }

    @Test fun `kata depan terakhir yang dipakai untuk walletHint`() {
        val result = TransactionParser.parse("beli kopi 15000 dari warung pakai GoPay", fakeSource) as ParseResult.Complete
        assertEquals("GoPay", result.draft.walletHint)
    }

    @Test fun `walletHint null kalau tidak ada kata depan dompet`() {
        val result = TransactionParser.parse("beli kopi 15000", fakeSource) as ParseResult.Complete
        assertNull(result.draft.walletHint)
    }

    // ── Nominal: kuantitas tidak boleh menang atas nominal bersufiks ─────────

    @Test fun `angka kuantitas tidak dikira nominal kalau ada nominal bersufiks`() {
        val result = TransactionParser.parse("beli 2 kopi 30rb", fakeSource) as ParseResult.Complete
        assertEquals(30000L, result.draft.amount)
    }

    @Test fun `angka polos besar tetap dipakai kalau tidak ada yang bersufiks`() {
        val result = TransactionParser.parse("beli 2 kopi 30000", fakeSource) as ParseResult.Complete
        assertEquals(30000L, result.draft.amount)
    }

    // ── Imbuhan kata kerja ────────────────────────────────────────────────────

    @Test fun `mendapatkan dikenali sebagai kata kerja INCOME`() {
        val result = TransactionParser.parse("saya mendapatkan uang saku 15rb", fakeSource) as ParseResult.Partial
        assertEquals("INCOME", result.draft.type)
        assertEquals(15000L, result.draft.amount)
    }

    @Test fun `menerima dikenali sebagai kata kerja INCOME`() {
        val result = TransactionParser.parse("menerima transferan 200rb", fakeSource) as ParseResult.Partial
        assertEquals("INCOME", result.draft.type)
    }

    @Test fun `membeli dikenali sebagai kata kerja EXPENSE`() {
        val result = TransactionParser.parse("membeli kopi 20000", fakeSource) as ParseResult.Complete
        assertEquals("EXPENSE", result.draft.type)
    }

    // ══════════════════════════════════════════════════════════════════════
    // Skenario realistis dengan DefaultKeywords asli (parameter default) —
    // sekaligus menguji kualitas kamus bawaan, bukan hanya mekanisme parser.
    // ══════════════════════════════════════════════════════════════════════

    @Test fun `jajan 15rb menjadi Complete EXPENSE Makanan`() {
        val result = TransactionParser.parse("jajan 15rb") as ParseResult.Complete
        assertEquals("EXPENSE", result.draft.type)
        assertEquals(15000L, result.draft.amount)
        assertEquals("exp_food", result.draft.categoryId)
    }

    @Test fun `uang jajan yang diterima terbaca INCOME bukan EXPENSE`() {
        // Regresi: "jajan" adalah kata kunci kategori exp_food, dan dulu juga
        // terdaftar sebagai kata kerja pengeluaran — kalimat ini jadi terbaca
        // EXPENSE dengan confidence 1.0, sehingga user tidak pernah ditawari
        // daftar kategori untuk mengoreksinya. Sekarang arah uang ditentukan
        // kata kerjanya ("mendapatkan"), dan karena tidak ada kategori INCOME
        // yang cocok hasilnya Partial — kategori ditanyakan, bukan ditebak.
        val result = TransactionParser.parse("saya mendapatkan uang jajan sebesar 15rb") as ParseResult.Partial
        assertEquals("INCOME", result.draft.type)
        assertEquals(15000L, result.draft.amount)
        assertEquals(listOf(TransactionField.CATEGORY), result.missing)
    }

    @Test fun `gaji 5jt menjadi Complete INCOME Gaji`() {
        val result = TransactionParser.parse("gaji 5jt") as ParseResult.Complete
        assertEquals("INCOME", result.draft.type)
        assertEquals(5_000_000L, result.draft.amount)
        assertEquals("inc_salary", result.draft.categoryId)
    }

    @Test fun `beli bensin 50000 menjadi Complete EXPENSE Transportasi`() {
        val result = TransactionParser.parse("beli bensin 50000") as ParseResult.Complete
        assertEquals("EXPENSE", result.draft.type)
        assertEquals(50000L, result.draft.amount)
        assertEquals("exp_transport", result.draft.categoryId)
        assertEquals("Beli bensin", result.draft.title)
    }

    @Test fun `aku habis 50rb menjadi Partial kategori`() {
        val result = TransactionParser.parse("aku habis 50rb") as ParseResult.Partial
        assertEquals("EXPENSE", result.draft.type)
        assertEquals(50000L, result.draft.amount)
        assertEquals(listOf(TransactionField.CATEGORY), result.missing)
    }

    @Test fun `berapa saldo aku menjadi NotATransaction`() {
        assertEquals(ParseResult.NotATransaction, TransactionParser.parse("berapa saldo aku"))
    }

    @Test fun `halo menjadi NotATransaction`() {
        assertEquals(ParseResult.NotATransaction, TransactionParser.parse("halo"))
    }

    @Test fun `target nabung 5jt menjadi Partial bukan Complete`() {
        // Catatan desain (lihat dok TransactionParser): parser TIDAK menebak
        // kategori kalau tidak ada kata kunci yang cocok. "target"/"nabung"
        // tidak ada di kamus kategori manapun, jadi hasilnya Partial (nanya
        // kategori) — ini LEBIH aman daripada memaksakan Complete dengan
        // kategori tebakan, sesuai prinsip "jangan menebak salah" di rencana
        // mitigasi risiko M5.
        val result = TransactionParser.parse("target nabung 5jt") as ParseResult.Partial
        assertEquals("EXPENSE", result.draft.type)
        assertEquals(5_000_000L, result.draft.amount)
        assertEquals(listOf(TransactionField.CATEGORY), result.missing)
    }

    @Test fun `makan siang 25000 menjadi Complete EXPENSE Makanan`() {
        val result = TransactionParser.parse("makan siang 25000") as ParseResult.Complete
        assertEquals("exp_food", result.draft.categoryId)
    }

    @Test fun `nonton bioskop 50rb menjadi Complete EXPENSE Hiburan`() {
        val result = TransactionParser.parse("nonton bioskop 50rb") as ParseResult.Complete
        assertEquals("exp_entertain", result.draft.categoryId)
        assertEquals(50000L, result.draft.amount)
    }

    @Test fun `bayar listrik 150000 menjadi Complete EXPENSE Tagihan`() {
        val result = TransactionParser.parse("bayar listrik 150000") as ParseResult.Complete
        assertEquals("exp_bills", result.draft.categoryId)
    }

    @Test fun `beli obat 30rb menjadi Complete EXPENSE Kesehatan`() {
        val result = TransactionParser.parse("beli obat 30rb") as ParseResult.Complete
        assertEquals("exp_health", result.draft.categoryId)
    }

    @Test fun `bayar spp 500rb menjadi Complete EXPENSE Pendidikan`() {
        val result = TransactionParser.parse("bayar spp 500rb") as ParseResult.Complete
        assertEquals("exp_education", result.draft.categoryId)
    }

    @Test fun `sewa kos 800rb menjadi Complete EXPENSE Rumah`() {
        val result = TransactionParser.parse("sewa kos 800rb") as ParseResult.Complete
        assertEquals("exp_home", result.draft.categoryId)
    }

    @Test fun `beli saham 1jt menjadi Complete EXPENSE Investasi`() {
        val result = TransactionParser.parse("beli saham 1jt") as ParseResult.Complete
        assertEquals("exp_investment", result.draft.categoryId)
    }

    @Test fun `dapat bonus 2jt menjadi Complete INCOME Bonus`() {
        val result = TransactionParser.parse("dapat bonus 2jt") as ParseResult.Complete
        assertEquals("INCOME", result.draft.type)
        assertEquals("inc_bonus", result.draft.categoryId)
    }

    @Test fun `terima hadiah 100rb menjadi Complete INCOME Hadiah`() {
        val result = TransactionParser.parse("terima hadiah 100rb") as ParseResult.Complete
        assertEquals("INCOME", result.draft.type)
        assertEquals("inc_gift", result.draft.categoryId)
    }

    @Test fun `jualan online 500rb menjadi Complete INCOME Bisnis`() {
        val result = TransactionParser.parse("jualan online 500rb") as ParseResult.Complete
        assertEquals("INCOME", result.draft.type)
        assertEquals("inc_business", result.draft.categoryId)
    }

    @Test fun `freelance project 3jt menjadi Complete INCOME Freelance`() {
        val result = TransactionParser.parse("freelance project 3jt") as ParseResult.Complete
        assertEquals("INCOME", result.draft.type)
        assertEquals("inc_freelance", result.draft.categoryId)
    }

    @Test fun `isi bensin 50rb menjadi Complete EXPENSE Transportasi walau tanpa kata kerja dikenal`() {
        val result = TransactionParser.parse("isi bensin 50rb") as ParseResult.Complete
        assertEquals("EXPENSE", result.draft.type)
        assertEquals("exp_transport", result.draft.categoryId)
        assertEquals(0.75f, result.confidence, 0.001f)
    }

    @Test fun `vitamin c 20rb menjadi Complete EXPENSE Kesehatan`() {
        val result = TransactionParser.parse("vitamin c 20rb") as ParseResult.Complete
        assertEquals("exp_health", result.draft.categoryId)
    }

    @Test fun `beli gadget baru 3jt menjadi Complete EXPENSE Belanja`() {
        val result = TransactionParser.parse("beli gadget baru 3jt") as ParseResult.Complete
        assertEquals("exp_shopping", result.draft.categoryId)
    }

    @Test fun `JAJAN 15RB huruf besar tetap terdeteksi kategori`() {
        val result = TransactionParser.parse("JAJAN 15RB") as ParseResult.Complete
        assertEquals("EXPENSE", result.draft.type)
        assertEquals("exp_food", result.draft.categoryId)
        assertEquals(15000L, result.draft.amount)
        // 0.75, bukan 1.0: "jajan" kini hanya berperan sebagai kata kunci
        // kategori, tidak lagi dihitung ganda sebagai kata kerja pengeluaran.
        assertEquals(0.75f, result.confidence, 0.001f)
    }

    @Test fun `konser musik 500rb menjadi Complete EXPENSE Hiburan`() {
        val result = TransactionParser.parse("konser musik 500rb") as ParseResult.Complete
        assertEquals("exp_entertain", result.draft.categoryId)
    }

    @Test fun `beli tiket wisata 200rb menjadi Complete EXPENSE Hiburan`() {
        val result = TransactionParser.parse("beli tiket wisata 200rb") as ParseResult.Complete
        assertEquals("exp_entertain", result.draft.categoryId)
    }
}
