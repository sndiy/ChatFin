package com.sndiy.chatfin.core.parser

/**
 * Kamus kata kunci bawaan (Bahasa Indonesia) → kategori default ChatFin
 * (lihat DefaultCategories.kt untuk daftar id/nama aslinya). Dipakai sebagai
 * KeywordSource default di TransactionParser.
 *
 * Sengaja konservatif — hanya kata yang jelas maksudnya masuk sini. Kata
 * kerja transaksi ("beli", "bayar", "gaji") TIDAK masuk sebagai kata kunci
 * kategori (itu urusan deteksi tipe di TransactionParser), supaya tidak
 * terjadi kecocokan kategori yang salah lewat kata kerja.
 *
 * Ini juga calon data seed untuk tabel category_keywords di Room (M6),
 * supaya user bisa menambah kata kunci sendiri tanpa update aplikasi.
 */
object DefaultKeywords : KeywordSource {

    data class Entry(val keyword: String, val categoryId: String, val categoryName: String, val type: String)

    val entries: List<Entry> = buildList {
        fun cat(categoryId: String, categoryName: String, type: String, vararg words: String) {
            words.forEach { add(Entry(it, categoryId, categoryName, type)) }
        }

        cat(
            "exp_food", "Makanan & Minuman", "EXPENSE",
            "makan", "makanan", "minum", "minuman", "jajan", "jajanan", "kopi", "teh", "nasi",
            "sarapan", "snack", "cemilan", "resto", "restoran", "warteg", "kafe", "cafe",
            "mie", "bakso", "ayam", "sate", "gorengan", "boba", "es krim", "roti", "kue"
        )

        cat(
            "exp_transport", "Transportasi", "EXPENSE",
            "bensin", "bbm", "ojek", "ojol", "gojek", "grab", "taxi", "taksi", "angkot",
            "busway", "transjakarta", "tol", "parkir", "kereta", "krl", "mrt", "pertamax",
            "pertalite", "servis motor", "service motor", "ganti oli", "bengkel"
        )

        cat(
            "exp_shopping", "Belanja", "EXPENSE",
            "belanja", "baju", "sepatu", "tas", "skincare", "kosmetik", "elektronik",
            "gadget", "aksesoris", "mall"
        )

        cat(
            "exp_entertain", "Hiburan", "EXPENSE",
            "nonton", "bioskop", "film", "netflix", "spotify", "game", "konser",
            "tiket", "karaoke", "wisata", "liburan"
        )

        cat(
            "exp_health", "Kesehatan", "EXPENSE",
            "obat", "dokter", "rumah sakit", "klinik", "apotek", "vitamin", "bpjs",
            "checkup", "periksa"
        )

        cat(
            "exp_education", "Pendidikan", "EXPENSE",
            "buku", "kursus", "sekolah", "kuliah", "spp", "les", "seminar", "pelatihan"
        )

        cat(
            "exp_bills", "Tagihan & Utilitas", "EXPENSE",
            "listrik", "token listrik", "air", "pulsa", "paket data", "wifi", "internet",
            "pln", "pdam", "telepon", "cicilan"
        )

        cat(
            "exp_home", "Rumah & Properti", "EXPENSE",
            "sewa", "kontrakan", "kos", "kost", "renovasi", "perabot", "furniture", "galon"
        )

        cat(
            "exp_investment", "Investasi", "EXPENSE",
            "saham", "reksadana", "crypto", "emas", "obligasi"
        )

        cat("inc_salary", "Gaji", "INCOME", "gaji", "gajian", "salary")
        cat("inc_freelance", "Freelance", "INCOME", "freelance", "proyek", "project", "klien")
        cat("inc_bonus", "Bonus", "INCOME", "bonus", "thr", "insentif", "komisi")
        cat("inc_gift", "Hadiah", "INCOME", "hadiah", "kado", "angpao", "angpau")
        cat("inc_invest", "Return Investasi", "INCOME", "dividen")
        cat("inc_business", "Bisnis", "INCOME", "jualan", "dagang", "laba", "omzet")
    }

    override fun findCategory(text: String, type: String): CategoryMatch? {
        val lower = text.lowercase()
        return entries
            .filter { it.type == type }
            .filter { lower.contains(it.keyword) }
            .maxByOrNull { it.keyword.length }
            ?.let { CategoryMatch(it.categoryId, it.categoryName, it.keyword) }
    }
}
