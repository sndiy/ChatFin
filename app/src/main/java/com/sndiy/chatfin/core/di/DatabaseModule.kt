package com.sndiy.chatfin.core.di

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.sndiy.chatfin.core.data.local.ChatFinDatabase
import com.sndiy.chatfin.core.data.local.DefaultCategories
import com.sndiy.chatfin.core.data.local.entity.CategoryKeywordEntity
import com.sndiy.chatfin.core.parser.DefaultKeywords
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object FirebaseModule {

    @Provides
    @Singleton
    fun provideFirebaseAuth(): FirebaseAuth = FirebaseAuth.getInstance()

    @Provides
    @Singleton
    fun provideFirestore(): FirebaseFirestore = FirebaseFirestore.getInstance()
}

// Migration dari v1 ke v2
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {}
}

// Migration dari v2 ke v3: tambah tabel budgets
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS budgets (
                id TEXT NOT NULL PRIMARY KEY,
                accountId TEXT NOT NULL,
                categoryId TEXT NOT NULL,
                limitAmount INTEGER NOT NULL,
                period TEXT NOT NULL DEFAULT 'MONTHLY',
                month INTEGER,
                year INTEGER NOT NULL,
                createdAt INTEGER NOT NULL
            )
        """.trimIndent())
    }
}

// Migration dari v3 ke v4: tambah tabel chat_sessions + chat_messages
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS chat_sessions (
                id TEXT NOT NULL PRIMARY KEY,
                accountId TEXT NOT NULL,
                title TEXT,
                messageCount INTEGER NOT NULL DEFAULT 0,
                createdAt INTEGER NOT NULL,
                updatedAt INTEGER NOT NULL
            )
        """.trimIndent())
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS chat_messages (
                id TEXT NOT NULL PRIMARY KEY,
                sessionId TEXT NOT NULL,
                role TEXT NOT NULL,
                content TEXT NOT NULL,
                isError INTEGER NOT NULL DEFAULT 0,
                createdAt INTEGER NOT NULL
            )
        """.trimIndent())
    }
}

// Migration dari v4 ke v5: tambah tabel category_keywords
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS category_keywords (
                id TEXT NOT NULL PRIMARY KEY,
                keyword TEXT NOT NULL,
                categoryId TEXT NOT NULL,
                type TEXT NOT NULL,
                isCustom INTEGER NOT NULL DEFAULT 0
            )
        """.trimIndent())

        DefaultKeywords.entries.forEachIndexed { index, entry ->
            db.execSQL(
                "INSERT INTO category_keywords (id, keyword, categoryId, type, isCustom) VALUES (?, ?, ?, ?, 0)",
                arrayOf<Any>("seed_$index", entry.keyword, entry.categoryId, entry.type)
            )
        }
    }
}

// Migration dari v5 ke v6: hapus kolom transaksi berulang
val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS transactions_new (
                id TEXT NOT NULL,
                accountId TEXT NOT NULL,
                type TEXT NOT NULL,
                amount INTEGER NOT NULL,
                categoryId TEXT NOT NULL,
                walletId TEXT NOT NULL,
                toWalletId TEXT,
                note TEXT,
                receiptImageUri TEXT,
                date TEXT NOT NULL,
                time TEXT NOT NULL,
                transferPairId TEXT,
                createdAt INTEGER NOT NULL,
                PRIMARY KEY(id)
            )
        """.trimIndent())
        db.execSQL("""
            INSERT INTO transactions_new
                (id, accountId, type, amount, categoryId, walletId, toWalletId, note, receiptImageUri, date, time, transferPairId, createdAt)
            SELECT
                id, accountId, type, amount, categoryId, walletId, toWalletId, note, receiptImageUri, date, time, transferPairId, createdAt
            FROM transactions
        """.trimIndent())
        db.execSQL("DROP TABLE transactions")
        db.execSQL("ALTER TABLE transactions_new RENAME TO transactions")
    }
}

// Migration dari v6 ke v7: tambah tabel transaction_items (relasi 1-to-N rincian item)
val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS transaction_items (
                id TEXT NOT NULL PRIMARY KEY,
                transactionId TEXT NOT NULL,
                name TEXT NOT NULL,
                price INTEGER NOT NULL,
                quantity INTEGER NOT NULL DEFAULT 1,
                FOREIGN KEY(transactionId) REFERENCES transactions(id) ON DELETE CASCADE
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS index_transaction_items_transactionId ON transaction_items(transactionId)")
    }
}

// Migration dari v7 ke v8: chat_messages.optionJson — opsi (chip kategori/
// dompet, kartu konfirmasi) ikut tersimpan bersama pesannya. Sebelumnya opsi
// hanya hidup di memori, jadi tombolnya hilang tiap layar chat dibuka ulang.
val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE chat_messages ADD COLUMN optionJson TEXT DEFAULT NULL")
    }
}

// Migration dari v8 ke v9: seed kategori global "Transfer" (id="transfer")
// untuk transaksi TRANSFER — sebelumnya categoryId="transfer" dipakai sebagai
// fallback (lihat TransactionViewModel.saveTransaction) tapi bukan row nyata
// di tabel categories, jadi lookup nama kategori gagal dan fallback ke id mentah.
val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "INSERT OR IGNORE INTO categories (id, accountId, name, type, iconName, colorHex, isCustom, sortOrder) " +
                "VALUES ('transfer', NULL, 'Transfer', 'TRANSFER', 'swap_horiz', '#616161', 0, 0)"
        )
    }
}

// Bridge migrations v9→v12: versi 10, 11, 12 pernah ada di build lama
// (indeks transaksi, tabel daily_aggregates) tapi sudah revert dari source.
// Device yang pernah menjalankan build tersebut sudah di v12.
// No-op bridges memastikan rantai migrasi 9→10→11→12 lengkap.
val MIGRATION_9_10 = object : Migration(9, 10) {
    override fun migrate(db: SupportSQLiteDatabase) { /* no-op bridge */ }
}
val MIGRATION_10_11 = object : Migration(10, 11) {
    override fun migrate(db: SupportSQLiteDatabase) { /* no-op bridge */ }
}
val MIGRATION_11_12 = object : Migration(11, 12) {
    override fun migrate(db: SupportSQLiteDatabase) { /* no-op bridge */ }
}

// Migration dari v12 ke v13: tambah kolom updatedAt ke 4 tabel utama
// untuk conflict resolution saat sinkronisasi Firestore antar device.
// Data lama di-backfill: transactions/wallets/finance_accounts pakai createdAt,
// categories pakai 0 (tidak punya createdAt).
val MIGRATION_12_13 = object : Migration(12, 13) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // transactions: tambah updatedAt, backfill dari createdAt
        db.execSQL("ALTER TABLE transactions ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")
        db.execSQL("UPDATE transactions SET updatedAt = createdAt")

        // wallets: tambah updatedAt, backfill dari createdAt
        db.execSQL("ALTER TABLE wallets ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")
        db.execSQL("UPDATE wallets SET updatedAt = createdAt")

        // finance_accounts: tambah updatedAt, backfill dari createdAt
        db.execSQL("ALTER TABLE finance_accounts ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")
        db.execSQL("UPDATE finance_accounts SET updatedAt = createdAt")

        // categories: tambah updatedAt, default 0 (tidak punya createdAt)
        db.execSQL("ALTER TABLE categories ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")
    }
}

// Migration dari v13 ke v14: tambah kolom isInitialBalance + backfill transaksi saldo awal.
// Untuk setiap wallet: selisih = saldo_tersimpan - SUM(transaksi). Buat 1 transaksi
// isInitialBalance=true sebesar selisih. Verifikasi: SUM setelah insert == saldo tersimpan.
val MIGRATION_13_14 = object : Migration(13, 14) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Step 1: tambah kolom
        db.execSQL("ALTER TABLE transactions ADD COLUMN isInitialBalance INTEGER NOT NULL DEFAULT 0")

        // Step 2: baca semua wallet
        val wallets = mutableListOf<WalletMigrationData>()
        db.query("SELECT id, accountId, balance, createdAt FROM wallets").use { cursor ->
            while (cursor.moveToNext()) {
                wallets.add(
                    WalletMigrationData(
                        id        = cursor.getString(0),
                        accountId = cursor.getString(1),
                        balance   = cursor.getLong(2),
                        createdAt = cursor.getLong(3)
                    )
                )
            }
        }

        // Step 3: untuk setiap wallet, hitung selisih dan buat initial balance tx
        wallets.forEach { wallet ->
            // Idempotency: cek apakah sudah ada initial balance tx untuk wallet ini
            val existingCount = db.query(
                "SELECT COUNT(*) FROM transactions WHERE isInitialBalance = 1 AND walletId = ?",
                arrayOf(wallet.id)
            ).use { c -> c.moveToFirst(); c.getLong(0) }

            if (existingCount > 0L) return@forEach // sudah ada, skip

            // Hitung SUM transaksi yang sudah ada untuk wallet ini
            val computedSum = db.query(
                """
                SELECT
                    COALESCE(SUM(CASE WHEN type='INCOME'   AND walletId=? THEN amount ELSE 0 END), 0)
                  - COALESCE(SUM(CASE WHEN type='EXPENSE'  AND walletId=? THEN amount ELSE 0 END), 0)
                  - COALESCE(SUM(CASE WHEN type='TRANSFER' AND walletId=? THEN amount ELSE 0 END), 0)
                  + COALESCE(SUM(CASE WHEN type='TRANSFER' AND toWalletId=? THEN amount ELSE 0 END), 0)
                FROM transactions
                """.trimIndent(),
                arrayOf(wallet.id, wallet.id, wallet.id, wallet.id)
            ).use { c -> c.moveToFirst(); c.getLong(0) }

            val selisih = wallet.balance - computedSum
            val txType = if (selisih >= 0) "INCOME" else "EXPENSE"
            val txAmount = kotlin.math.abs(selisih)
            val txCategoryId = if (selisih >= 0) "inc_other" else "exp_other"

            // Konversi createdAt (millis) ke date string
            val dateStr = db.query(
                "SELECT strftime('%Y-%m-%d', ? / 1000, 'unixepoch', 'localtime')",
                arrayOf(wallet.createdAt.toString())
            ).use { c -> c.moveToFirst(); c.getString(0) ?: "2024-01-01" }

            // INSERT transaksi saldo awal
            db.execSQL(
                """
                INSERT INTO transactions
                    (id, accountId, type, amount, categoryId, walletId, toWalletId,
                     note, receiptImageUri, date, time, transferPairId,
                     isInitialBalance, createdAt, updatedAt)
                VALUES (?, ?, ?, ?, ?, ?, NULL,
                        'Saldo awal', NULL, ?, '00:00', NULL,
                        1, ?, ?)
                """.trimIndent(),
                arrayOf(
                    "init_bal_${wallet.id}",  // id deterministik
                    wallet.accountId,
                    txType,
                    txAmount,
                    txCategoryId,
                    wallet.id,
                    dateStr,
                    wallet.createdAt,
                    wallet.createdAt
                )
            )
        }

        // Step 4: VERIFIKASI — SUM(semua transaksi termasuk init bal) harus == saldo tersimpan
        wallets.forEach { wallet ->
            val finalSum = db.query(
                """
                SELECT
                    COALESCE(SUM(CASE WHEN type='INCOME'   AND walletId=? THEN amount ELSE 0 END), 0)
                  - COALESCE(SUM(CASE WHEN type='EXPENSE'  AND walletId=? THEN amount ELSE 0 END), 0)
                  - COALESCE(SUM(CASE WHEN type='TRANSFER' AND walletId=? THEN amount ELSE 0 END), 0)
                  + COALESCE(SUM(CASE WHEN type='TRANSFER' AND toWalletId=? THEN amount ELSE 0 END), 0)
                FROM transactions
                """.trimIndent(),
                arrayOf(wallet.id, wallet.id, wallet.id, wallet.id)
            ).use { c -> c.moveToFirst(); c.getLong(0) }

            if (finalSum != wallet.balance) {
                throw IllegalStateException(
                    "MIGRATION_13_14 GAGAL: wallet '${wallet.id}' " +
                    "saldo tersimpan=${wallet.balance} tapi SUM(tx)=$finalSum. " +
                    "Data TIDAK dicommit — rollback otomatis."
                )
            }
        }
    }
}

// Data class sementara untuk migration
private data class WalletMigrationData(
    val id: String,
    val accountId: String,
    val balance: Long,
    val createdAt: Long
)

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideChatFinDatabase(
        @ApplicationContext context: Context
    ): ChatFinDatabase {
        var db: ChatFinDatabase? = null

        val callback = object : RoomDatabase.Callback() {
            override fun onCreate(sqLiteDb: SupportSQLiteDatabase) {
                super.onCreate(sqLiteDb)
                CoroutineScope(Dispatchers.IO).launch {
                    db?.categoryDao()?.insertCategories(DefaultCategories.all)
                    db?.categoryKeywordDao()?.insertKeywords(
                        DefaultKeywords.entries.mapIndexed { index, entry ->
                            CategoryKeywordEntity(
                                id = "seed_$index",
                                keyword = entry.keyword,
                                categoryId = entry.categoryId,
                                type = entry.type,
                                isCustom = false
                            )
                        }
                    )
                }
            }
        }

        return Room.databaseBuilder(
            context,
            ChatFinDatabase::class.java,
            "chatfin_database"
        )
            .addCallback(callback)
            .addMigrations(
                MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5,
                MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9,
                MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13,
                MIGRATION_13_14
            )
            .build()
            .also { db = it }
    }

    @Provides fun provideAccountDao(db: ChatFinDatabase)         = db.accountDao()
    @Provides fun provideWalletDao(db: ChatFinDatabase)          = db.walletDao()
    @Provides fun provideCategoryDao(db: ChatFinDatabase)        = db.categoryDao()
    @Provides fun provideTransactionDao(db: ChatFinDatabase)     = db.transactionDao()
    @Provides fun provideBudgetDao(db: ChatFinDatabase)          = db.budgetDao()
    @Provides fun provideChatDao(db: ChatFinDatabase)            = db.chatDao()
    @Provides fun provideCategoryKeywordDao(db: ChatFinDatabase) = db.categoryKeywordDao()
}
