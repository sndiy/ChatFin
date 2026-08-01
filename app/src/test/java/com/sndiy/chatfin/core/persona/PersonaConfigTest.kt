package com.sndiy.chatfin.core.persona

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PersonaConfigTest {

    @Test fun `semua PersonaId punya preset terdaftar`() {
        val registeredIds = PersonaPresets.all.map { it.id }.toSet()
        assertEquals(PersonaId.entries.toSet(), registeredIds)
    }

    @Test fun `tidak ada duplikat PersonaId di daftar preset`() {
        val ids = PersonaPresets.all.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test fun `byId mengembalikan preset yang sesuai untuk setiap id`() {
        PersonaId.entries.forEach { id ->
            assertEquals(id, PersonaPresets.byId(id).id)
        }
    }

    @Test fun `setiap preset punya displayName dan tagline tidak kosong`() {
        PersonaPresets.all.forEach { preset ->
            assertTrue("displayName kosong untuk ${preset.id}", preset.displayName.isNotBlank())
            assertTrue("tagline kosong untuk ${preset.id}", preset.tagline.isNotBlank())
        }
    }

    @Test fun `promptFragment mengganti placeholder userName dengan nama sungguhan`() {
        PersonaPresets.all.forEach { preset ->
            val fragment = preset.promptFragment("Budi")
            assertTrue("placeholder tidak terganti untuk ${preset.id}", fragment.contains("Budi"))
            assertFalse("placeholder mentah masih tersisa untuk ${preset.id}", fragment.contains("{{userName}}"))
        }
    }

    @Test fun `promptFragment berbeda nama tetap konsisten strukturnya`() {
        val a = PersonaPresets.MAI.promptFragment("Andi")
        val b = PersonaPresets.MAI.promptFragment("Sari")
        assertNotEquals(a, b)
        assertTrue(a.contains("Andi"))
        assertTrue(b.contains("Sari"))
    }

    @Test fun `keempat preset punya fragment yang benar benar berbeda satu sama lain`() {
        val fragments = PersonaPresets.all.map { it.promptFragment("Test").trim() }
        assertEquals(fragments.size, fragments.toSet().size)
    }

    @Test fun `preset MAI menyebutkan Sakurajima Mai`() {
        assertTrue(PersonaPresets.MAI.promptFragment("Test").contains("Sakurajima Mai"))
    }

    @Test fun `preset ASISTEN tidak memakai aksi naratif ala Mai`() {
        val fragment = PersonaPresets.ASISTEN.promptFragment("Test")
        assertFalse(fragment.contains("*menghela napas*"))
    }
}
