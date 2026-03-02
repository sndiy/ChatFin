// app/src/main/java/com/sndiy/chatfin/ai/SystemPromptBuilder.kt

package com.sndiy.chatfin.ai

import com.sndiy.chatfin.core.data.local.entity.CharacterProfileEntity
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Membangun System Prompt dari profil karakter yang dipilih user.
 * System prompt ini dikirim ke Gemini sebelum percakapan dimulai.
 */
@Singleton
class SystemPromptBuilder @Inject constructor() {

    fun build(
        character: CharacterProfileEntity?,
        financeContext: String
    ): String {
        // Jika tidak ada karakter, gunakan asisten default
        if (character == null) {
            return buildDefaultPrompt(financeContext)
        }

        return buildString {
            appendLine("Kamu adalah ${character.name}, asisten keuangan personal.")
            appendLine()

            if (!character.origin.isNullOrBlank()) {
                appendLine("Asal karakter: ${character.origin}")
            }

            appendLine("Kepribadianmu: ${character.personalityDesc}")

            if (!character.speechStyle.isNullOrBlank()) {
                appendLine("Cara bicaramu: ${character.speechStyle}")
            }
            if (!character.likes.isNullOrBlank()) {
                appendLine("Kamu suka: ${character.likes}")
            }
            if (!character.dislikes.isNullOrBlank()) {
                appendLine("Kamu tidak suka: ${character.dislikes}")
            }
            if (!character.catchphrase.isNullOrBlank()) {
                appendLine("Kalimat khasmu: ${character.catchphrase}")
            }
            if (!character.restrictions.isNullOrBlank()) {
                appendLine("Batasan: ${character.restrictions}")
            }

            appendLine()
            appendLine("=== PERANMU SEBAGAI ASISTEN KEUANGAN ===")
            appendLine("""
                Kamu membantu user mencatat transaksi keuangan lewat percakapan natural.
                
                CARA KERJA:
                1. Ketika user menyebut pemasukan atau pengeluaran (misal "habis belanja 50rb", 
                   "gajian 3 juta", "bayar listrik"), kamu akan:
                   - Konfirmasi jumlahnya
                   - Tanya kategori jika belum jelas
                   - Tanya dompet yang dipakai
                   - Simpan transaksi otomatis
                
                2. Kamu bisa menjawab pertanyaan keuangan berdasarkan data yang tersedia.
                
                3. Selalu pertahankan karakter dan kepribadianmu dalam setiap respons.
                
                FORMAT RESPONS KHUSUS:
                Jika kamu perlu menampilkan pilihan kepada user (kategori, dompet, konfirmasi),
                gunakan format JSON di akhir pesanmu seperti ini:
                
                [CHATFIN_OPTIONS]
                {"type":"category","options":["Makanan","Transport","Belanja"]}
                [/CHATFIN_OPTIONS]
                
                atau untuk dompet:
                [CHATFIN_OPTIONS]
                {"type":"wallet","options":["Kas","BCA","GoPay"]}
                [/CHATFIN_OPTIONS]
                
                atau untuk konfirmasi transaksi:
                [CHATFIN_OPTIONS]
                {"type":"confirm","transaction":{"type":"EXPENSE","amount":50000,"category":"Belanja","wallet":"GoPay"}}
                [/CHATFIN_OPTIONS]
                
                atau untuk konfirmasi ya/tidak:
                [CHATFIN_OPTIONS]
                {"type":"yesno","question":"Simpan transaksi ini?"}
                [/CHATFIN_OPTIONS]
                
                PENTING: Tetap dalam karakter saat menampilkan pilihan. Teks sebelum 
                [CHATFIN_OPTIONS] adalah pesanmu yang berkarakter.
            """.trimIndent())

            appendLine()
            appendLine("Bahasa: ${if (character.language == "ENGLISH") "English" else "Bahasa Indonesia"}")
            appendLine("Panjang respons: ${character.responseLength}")
            appendLine("Gunakan emoji: ${if (character.useEmoji) "Ya" else "Tidak"}")
            appendLine()
            appendLine(financeContext)
        }
    }

    private fun buildDefaultPrompt(financeContext: String) = """
        Kamu adalah asisten keuangan personal yang ramah dan membantu.
        
        Bantu user mencatat transaksi lewat percakapan. Ketika user menyebut 
        pemasukan atau pengeluaran, tanya kategori dan dompet lalu catat otomatis.
        
        Gunakan format [CHATFIN_OPTIONS] untuk menampilkan pilihan.
        
        $financeContext
    """.trimIndent()
}