package com.sndiy.chatfin.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * ChatOption disimpan ke Room sebagai JSON supaya tombol pilihan tidak hilang
 * saat layar chat dibuka ulang. Round-trip-nya harus utuh untuk SEMUA varian,
 * dan baris lama/rusak tidak boleh menjatuhkan pemuatan riwayat.
 */
class ChatOptionSerializationTest {

    private fun roundTrip(option: ChatOption): ChatOption? =
        ChatOption.decode(ChatOption.encode(option))

    @Test fun `chip kategori utuh setelah round trip`() {
        val option = ChatOption.CategoryOptions(listOf("Gaji", "Bonus", "Hadiah"))
        assertEquals(option, roundTrip(option))
    }

    @Test fun `chip dompet utuh setelah round trip`() {
        val option = ChatOption.WalletOptions(listOf("Kas", "BCA"))
        assertEquals(option, roundTrip(option))
    }

    @Test fun `kartu konfirmasi utuh setelah round trip`() {
        val option = ChatOption.TransactionConfirm(
            type = "INCOME", amount = 15000L,
            category = "Bonus", wallet = "Kas", title = "Uang jajan"
        )
        assertEquals(option, roundTrip(option))
    }

    @Test fun `yesno utuh setelah round trip`() {
        val option = ChatOption.YesNo("Sudah benar?")
        assertEquals(option, roundTrip(option))
    }

    @Test fun `permintaan grafik utuh setelah round trip`() {
        val option = ChatOption.VisualizationRequest("Grafik Bulanan", "LINE", "LAST_MONTH")
        assertEquals(option, roundTrip(option))
    }

    @Test fun `permintaan tabel utuh setelah round trip`() {
        val option = ChatOption.TableRequest("Tabel Kategori", "CATEGORY_SUMMARY")
        assertEquals(option, roundTrip(option))
    }

    @Test fun `varian tetap dibedakan setelah round trip`() {
        // Category dan Wallet punya bentuk data identik — tanpa penanda tipe,
        // chip dompet bisa pulih sebagai chip kategori.
        val wallet = ChatOption.WalletOptions(listOf("Kas"))
        assertEquals(ChatOption.WalletOptions::class, roundTrip(wallet)!!::class)
    }

    // ── Baris yang tidak bisa dibaca tidak boleh menjatuhkan riwayat ─────────

    @Test fun `null dan blank menghasilkan null`() {
        assertNull(ChatOption.encode(null))
        assertNull(ChatOption.decode(null))
        assertNull(ChatOption.decode(""))
        assertNull(ChatOption.decode("   "))
    }

    @Test fun `json rusak menghasilkan null bukan exception`() {
        assertNull(ChatOption.decode("{bukan json"))
        assertNull(ChatOption.decode("""{"type":"TidakDikenal"}"""))
    }
}
