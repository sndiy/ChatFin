package com.sndiy.chatfin.core.ocr

import org.junit.Assert.*
import org.junit.Test

class ReceiptParserTest {

    @Test
    fun parse_esbTakeawayReceipt_extractsCorrectTotalAndItemsIgnoringCashAndMetadata() {
        val esbReceipt = """
            ESB
            G. Penambangan, Balongbendo
            -----------------------------
            No          : GPPB01202608030127
            Tanggal     : 03-08-2026 16:19
            Jam Masuk   : 03-08-2026 16:19
            No Meja     : Quick Service
            Mode        : TAKEAWAY
            Kasir       : Fita Mifthaqul
                          Khusnah
            -----------------------------
            GEPREK JUMBO
            2x    @12.000             24.000
            -----------------------------
            2 item
                   Subtotal :         24.000
             Grand Total :            24.000
                   CASH :             50.000
              Kembalian :             26.000
            -----------------------------
               - Thank You -
        """.trimIndent()

        val result = ReceiptParser.parse(esbReceipt)

        assertEquals("Esb", result.merchant)
        assertEquals("2026-08-03", result.date)
        assertEquals("16:19", result.time)
        assertEquals(24000L, result.totalAmount)
        assertFalse(result.isTotalLowConfidence)

        assertEquals(1, result.items.size)
        assertEquals("Geprek Jumbo", result.items[0].name)
        assertEquals(24000L, result.items[0].price)
    }

    @Test
    fun parse_englishStarbucksReceipt_extractsMerchantTotalAndItemsCorrectly() {
        val englishReceipt = """
            STARBUCKS COFFEE
            Store #12903 - Grand Indonesia
            Date: 03/08/2026 15:45
            Check #: 98124
            Server: Alex
            
            1 Caramel Macchiato Venti 65.000
            1 Butter Croissant 32.000
            
            Subtotal: 97.000
            Tax (10%): 9.700
            Grand Total: 106.700
            Cash Tendered: 150.000
            Change Due: 43.300
            
            Thank you for visiting us!
        """.trimIndent()

        val result = ReceiptParser.parse(englishReceipt)

        assertEquals("Starbucks Coffee", result.merchant)
        assertEquals("2026-08-03", result.date)
        assertEquals("15:45", result.time)
        assertEquals(106700L, result.totalAmount)
        assertFalse(result.isTotalLowConfidence)
        assertEquals(2, result.items.size)
        assertEquals("Caramel Macchiato Venti", result.items[0].name)
        assertEquals(65000L, result.items[0].price)
    }

    @Test
    fun parse_goPayEReceipt_extractsMerchantAndTotal() {
        val goPayText = """
            GoPay
            Transaksi Berhasil
            Status: Selesai
            Waktu Transaksi: 03-08-2026 18:30
            
            Penerima: Kopi Kenangan Mantan
            Nominal: Rp 35.000
            Total Transaksi: Rp 35.000
            Sisa Saldo: Rp 120.000
        """.trimIndent()

        val result = ReceiptParser.parse(goPayText)

        assertEquals("Kopi Kenangan Mantan", result.merchant)
        assertEquals("2026-08-03", result.date)
        assertEquals("18:30", result.time)
        assertEquals(35000L, result.totalAmount)
        assertFalse(result.isTotalLowConfidence)
    }

    @Test
    fun parse_validIndomaretReceipt_extractsAllFieldsCorrectly() {
        val rawReceipt = """
            INDOMARET HIDAYATULLAH
            JL. HIDAYATULLAH NO. 45
            TGL: 03/08/2026 14:30
            
            1 INDOMIE GORENG 3.500
            2 TEH BOTOL SOSRO 8.000
            
            SUBTOTAL: 11.500
            TOTAL BAYAR: 11.500
            CASH: 20.000
            KEMBALI: 8.500
            TERIMA KASIH
        """.trimIndent()

        val result = ReceiptParser.parse(rawReceipt)

        assertEquals("Indomaret Hidayatullah", result.merchant)
        assertEquals("2026-08-03", result.date)
        assertEquals("14:30", result.time)
        assertEquals(11500L, result.totalAmount)
        assertFalse(result.isMerchantLowConfidence)
        assertFalse(result.isDateLowConfidence)
        assertFalse(result.isTotalLowConfidence)
        assertTrue(result.items.isNotEmpty())
    }

    @Test
    fun parse_blurryReceipt_flagsLowConfidenceFields() {
        val blurryReceipt = """
            ??? UNKNOWN SHOP ???
            TEXT BURAM TULISAN TANGAN
            KERTAS SOBEK
        """.trimIndent()

        val result = ReceiptParser.parse(blurryReceipt)

        assertTrue(result.isTotalLowConfidence)
        assertTrue(result.isDateLowConfidence)
        assertTrue(result.hasLowConfidenceField)
    }

    @Test
    fun parse_eReceiptScreenshot_extractsMerchantAndTotal() {
        val eReceiptText = """
            TOKOPEDIA OFFICIAL STORE
            Nota Pembelian: INV/20260803/MPL/12345
            Tanggal: 03-08-2026 09:15
            
            1x Headset Bluetooth Wireless Rp 150.000
            1x Ongkos Kirim Rp 15.000
            
            TOTAL BELANJA: Rp 165.000
        """.trimIndent()

        val result = ReceiptParser.parse(eReceiptText)

        assertEquals("Tokopedia Official Store", result.merchant)
        assertEquals("2026-08-03", result.date)
        assertEquals("09:15", result.time)
        assertEquals(165000L, result.totalAmount)
        assertFalse(result.isTotalLowConfidence)
    }
}
