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
                MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8
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
