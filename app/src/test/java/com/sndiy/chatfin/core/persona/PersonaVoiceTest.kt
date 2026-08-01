package com.sndiy.chatfin.core.persona

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PersonaVoiceTest {

    @Test fun `semua PersonaId punya voice terdaftar tanpa exception`() {
        PersonaId.entries.forEach { id ->
            val voice = PersonaVoices.byId(id)
            assertTrue("switchOffline kosong untuk $id", voice.switchOffline.isNotBlank())
        }
    }

    @Test fun `CUSTOM memakai voice ASISTEN sebagai fallback netral`() {
        assertEquals(PersonaVoices.ASISTEN, PersonaVoices.byId(PersonaId.CUSTOM))
    }

    @Test fun `keempat voice bawaan berbeda satu sama lain`() {
        val voices = listOf(PersonaVoices.MAI, PersonaVoices.ASISTEN, PersonaVoices.SAHABAT, PersonaVoices.PELATIH)
        assertEquals(voices.size, voices.toSet().size)
    }

    @Test fun `categoryPrompt mengganti placeholder amount`() {
        PersonaVoices.let { voices ->
            listOf(voices.MAI, voices.ASISTEN, voices.SAHABAT, voices.PELATIH).forEach { voice ->
                val prompt = voice.categoryPrompt("Rp 15.000", invalid = false)
                assertTrue("placeholder tidak terganti: $prompt", prompt.contains("Rp 15.000"))
                assertFalse("placeholder mentah tersisa: $prompt", prompt.contains("{amount}"))
            }
        }
    }

    @Test fun `categoryPrompt invalid berbeda dari yang normal`() {
        listOf(PersonaVoices.MAI, PersonaVoices.ASISTEN, PersonaVoices.SAHABAT, PersonaVoices.PELATIH).forEach { voice ->
            val normal = voice.categoryPrompt("Rp 15.000", invalid = false)
            val invalid = voice.categoryPrompt("Rp 15.000", invalid = true)
            assertNotEquals(normal, invalid)
        }
    }

    @Test fun `walletPrompt mengganti placeholder category`() {
        listOf(PersonaVoices.MAI, PersonaVoices.ASISTEN, PersonaVoices.SAHABAT, PersonaVoices.PELATIH).forEach { voice ->
            val prompt = voice.walletPrompt("Makanan & Minuman", invalid = false)
            assertTrue("placeholder tidak terganti: $prompt", prompt.contains("Makanan & Minuman"))
            assertFalse("placeholder mentah tersisa: $prompt", prompt.contains("{category}"))
        }
    }

    @Test fun `walletPrompt invalid berbeda dari yang normal`() {
        listOf(PersonaVoices.MAI, PersonaVoices.ASISTEN, PersonaVoices.SAHABAT, PersonaVoices.PELATIH).forEach { voice ->
            val normal = voice.walletPrompt("Transportasi", invalid = false)
            val invalid = voice.walletPrompt("Transportasi", invalid = true)
            assertNotEquals(normal, invalid)
        }
    }

    @Test fun `voice MAI mempertahankan gaya aksi naratif`() {
        assertTrue(PersonaVoices.MAI.switchOffline.contains("*menghela napas*"))
    }

    @Test fun `voice ASISTEN tidak memakai aksi naratif`() {
        val voice = PersonaVoices.ASISTEN
        listOf(voice.switchOffline, voice.switchBotMode, voice.switchNoApiKey, voice.askTitle)
            .forEach { assertFalse("mengandung aksi naratif: $it", it.contains("*")) }
    }
}
