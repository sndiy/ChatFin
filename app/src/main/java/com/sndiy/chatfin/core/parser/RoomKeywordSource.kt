package com.sndiy.chatfin.core.parser

import com.sndiy.chatfin.core.data.local.dao.CategoryKeywordDao
import com.sndiy.chatfin.core.data.local.dao.CategoryKeywordWithCategoryName
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementasi KeywordSource yang dibaca dari Room (tabel category_keywords),
 * supaya user bisa menambah kata kunci sendiri tanpa update aplikasi (belum
 * ada UI untuk itu — disiapkan untuk milestone berikutnya).
 *
 * Beda dengan file lain di core/parser/, kelas ini SENGAJA berdependency ke
 * Room/Hilt — dia adalah adapter persistence untuk KeywordSource, bukan
 * bagian dari mesin parsing murni.
 *
 * [KeywordSource.findCategory] harus sinkron (dipanggil dari TransactionParser
 * yang murni, bukan suspend), sementara Room DAO hanya suspend/Flow. Solusinya:
 * cache in-memory yang diisi lewat [refresh], dibaca sinkron oleh
 * [findCategory]. Pemanggil (ChatViewModel di M7) yang bertanggung jawab
 * memanggil [refresh] di scope-nya sendiri sebelum kamus ini dipakai, dan
 * setiap kali kata kunci baru ditambah/dihapus.
 *
 * Sebelum [refresh] pernah dipanggil, [findCategory] selalu null — bukan
 * crash, bukan exception, cuma belum ada data untuk dicocokkan.
 */
@Singleton
class RoomKeywordSource @Inject constructor(
    private val dao: CategoryKeywordDao
) : KeywordSource {

    @Volatile
    private var cache: List<CategoryKeywordWithCategoryName> = emptyList()

    /**
     * Isi cache dengan kata kunci milik [accountId] plus kategori global.
     * Wajib dipanggil ulang setiap akun aktif berganti — kalau tidak, kamus
     * akun sebelumnya masih terpakai untuk akun yang sekarang.
     */
    suspend fun refresh(accountId: String?) {
        cache = dao.getAllWithCategoryName(accountId)
    }

    override fun findCategory(text: String, type: String): CategoryMatch? {
        val lower = text.lowercase()
        return cache
            .asSequence()
            .filter { it.type == type }
            .filter { lower.contains(it.keyword) }
            .maxByOrNull { it.keyword.length }
            ?.let { CategoryMatch(it.categoryId, it.categoryName, it.keyword) }
    }
}
