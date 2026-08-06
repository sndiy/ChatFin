package com.sndiy.chatfin.core.data.local

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sndiy.chatfin.core.data.local.entity.CategoryEntity
import com.sndiy.chatfin.core.data.local.entity.CategoryKeywordEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Kamus kata kunci parser dulu dibaca lewat JOIN tanpa filter akun sama sekali,
 * sehingga kata kunci milik akun lain ikut terpakai — transaksi bisa tersimpan
 * dengan categoryId yang tidak ada di akun aktif, dan di UI muncul sebagai UUID
 * mentah, bukan nama kategori.
 */
@RunWith(AndroidJUnit4::class)
class CategoryKeywordScopeTest {

    private lateinit var db: ChatFinDatabase

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            ChatFinDatabase::class.java
        ).build()

        runBlocking {
            db.categoryDao().insertCategories(
                listOf(
                    CategoryEntity(id = "glob_food", accountId = null, name = "Makanan Global", type = "EXPENSE"),
                    CategoryEntity(id = "a_kopi", accountId = "akun-A", name = "Kopi Akun A", type = "EXPENSE"),
                    CategoryEntity(id = "b_kopi", accountId = "akun-B", name = "Kopi Akun B", type = "EXPENSE")
                )
            )
            db.categoryKeywordDao().insertKeywords(
                listOf(
                    CategoryKeywordEntity(id = "kw-1", keyword = "nasi", categoryId = "glob_food", type = "EXPENSE"),
                    CategoryKeywordEntity(id = "kw-2", keyword = "espresso", categoryId = "a_kopi", type = "EXPENSE"),
                    CategoryKeywordEntity(id = "kw-3", keyword = "latte", categoryId = "b_kopi", type = "EXPENSE")
                )
            )
        }
    }

    @After fun tearDown() = db.close()

    @Test fun kamusAkunAktifPlusGlobalSaja() = runBlocking {
        val hasil = db.categoryKeywordDao().getAllWithCategoryName("akun-A")
        val ids = hasil.map { it.categoryId }.toSet()

        assertEquals(setOf("glob_food", "a_kopi"), ids)
        assertTrue("kata kunci akun lain tidak boleh ikut", hasil.none { it.keyword == "latte" })
    }

    @Test fun kategoriGlobalIkutUntukSemuaAkun() = runBlocking {
        val hasil = db.categoryKeywordDao().getAllWithCategoryName("akun-B")
        assertEquals(setOf("glob_food", "b_kopi"), hasil.map { it.categoryId }.toSet())
    }

    @Test fun accountIdNullBerartiTanpaPembatasan() = runBlocking {
        // Dipakai jalur diagnostik/backup — harus tetap mengembalikan semuanya.
        val hasil = db.categoryKeywordDao().getAllWithCategoryName(null)
        assertEquals(3, hasil.size)
    }

    @Test fun namaKategoriIkutTerbaruDariJoin() = runBlocking {
        db.categoryDao().updateCategory(
            CategoryEntity(id = "a_kopi", accountId = "akun-A", name = "Kopi Diubah", type = "EXPENSE")
        )
        val hasil = db.categoryKeywordDao().getAllWithCategoryName("akun-A")
        assertEquals("Kopi Diubah", hasil.first { it.keyword == "espresso" }.categoryName)
    }
}
