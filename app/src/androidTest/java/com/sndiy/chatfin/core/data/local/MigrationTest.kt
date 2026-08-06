// app/src/androidTest/java/com/sndiy/chatfin/core/data/local/MigrationTest.kt
//
// Verifikasi M0: MIGRATION_1_2 wajib ada dan wajib membawa database v1 nyata
// (bukan hipotetis) sampai ke versi terbaru tanpa crash dan tanpa kehilangan
// data. Diperluas di M12 (MIGRATION_5_6) — transactions dibongkar-pasang
// lewat pola create-copy-drop-rename untuk menghapus kolom
// isRecurring/recurringInterval/recurringParentId (fitur "Transaksi
// Berulang" yang dihapus karena tidak pernah benar-benar berfungsi), jadi
// test ini SEKALIGUS jadi bukti data transaksi lama tidak hilang saat
// tabelnya dibangun ulang, bukan cuma bukti kolom baru bertambah seperti
// migrasi-migrasi sebelumnya.
//
// Skenario yang diuji persis meniru user yang install ChatFin saat masih
// versi 1, mencatat beberapa data, lalu update ke build ini:
//   1. Bangun database fisik sesuai app/schemas/1.json (skema historis asli,
//      hanya 4 tabel: finance_accounts, wallets, categories, transactions —
//      TANPA budgets/chat_sessions/chat_messages yang baru ada di v3/v4).
//   2. Isi satu baris data nyata di tiap tabel.
//   3. Jalankan seluruh rantai migrasi lewat MigrationTestHelper, dengan
//      validateDroppedTables=true — ini membandingkan skema HASIL migrasi
//      terhadap app/schemas/8.json kolom demi kolom, indeks demi indeks.
//      Kalau ada satu kolom saja yang meleset (termasuk kolom recurring yang
//      seharusnya SUDAH tidak ada lagi), test ini gagal.
//   4. Buka hasil migrasi lewat Room (bukan raw SQL) dan pastikan baris yang
//      ditanam di langkah 2 masih terbaca utuh.

package com.sndiy.chatfin.core.data.local

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sndiy.chatfin.core.di.MIGRATION_1_2
import com.sndiy.chatfin.core.di.MIGRATION_2_3
import com.sndiy.chatfin.core.di.MIGRATION_3_4
import com.sndiy.chatfin.core.di.MIGRATION_4_5
import com.sndiy.chatfin.core.di.MIGRATION_5_6
import com.sndiy.chatfin.core.di.MIGRATION_6_7
import com.sndiy.chatfin.core.di.MIGRATION_7_8
import com.sndiy.chatfin.core.parser.DefaultKeywords
import kotlinx.coroutines.flow.first
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MigrationTest {

    private val testDbName = "migration-test-chatfin"

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        ChatFinDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory()
    )

    @Test
    fun migrate1To8_realWorldSchema_preservesDataAndMatchesLatestSchema() {
        // ── 1. Bangun database v1 PERSIS sesuai app/schemas/1.json ──────────
        // createDatabase() menjalankan CREATE TABLE dari @Database class saat
        // ini kalau kita tidak berhati-hati — untuk memastikan kita menguji
        // skema HISTORIS yang benar (bukan skema Kotlin saat ini yang sudah
        // berisi 7 entity), tabel dibuat manual di sini persis sama dengan
        // createSql di schemas/1.json.
        var db = helper.createDatabase(testDbName, 1).apply {
            execSQL(
                """
                CREATE TABLE IF NOT EXISTS `finance_accounts` (
                    `id` TEXT NOT NULL, `name` TEXT NOT NULL, `iconName` TEXT NOT NULL,
                    `colorHex` TEXT NOT NULL, `currency` TEXT NOT NULL, `description` TEXT,
                    `isPinProtected` INTEGER NOT NULL, `pinHash` TEXT,
                    `isBiometricEnabled` INTEGER NOT NULL, `isActive` INTEGER NOT NULL,
                    `sortOrder` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL,
                    PRIMARY KEY(`id`)
                )
                """.trimIndent()
            )
            execSQL(
                """
                CREATE TABLE IF NOT EXISTS `wallets` (
                    `id` TEXT NOT NULL, `accountId` TEXT NOT NULL, `name` TEXT NOT NULL,
                    `type` TEXT NOT NULL, `balance` INTEGER NOT NULL, `currency` TEXT NOT NULL,
                    `bankName` TEXT, `accountNumber` TEXT, `iconName` TEXT NOT NULL,
                    `colorHex` TEXT NOT NULL, `isDefault` INTEGER NOT NULL,
                    `sortOrder` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL,
                    PRIMARY KEY(`id`)
                )
                """.trimIndent()
            )
            execSQL(
                """
                CREATE TABLE IF NOT EXISTS `categories` (
                    `id` TEXT NOT NULL, `accountId` TEXT, `name` TEXT NOT NULL,
                    `type` TEXT NOT NULL, `iconName` TEXT NOT NULL, `colorHex` TEXT NOT NULL,
                    `isCustom` INTEGER NOT NULL, `sortOrder` INTEGER NOT NULL,
                    PRIMARY KEY(`id`)
                )
                """.trimIndent()
            )
            execSQL(
                """
                CREATE TABLE IF NOT EXISTS `transactions` (
                    `id` TEXT NOT NULL, `accountId` TEXT NOT NULL, `type` TEXT NOT NULL,
                    `amount` INTEGER NOT NULL, `categoryId` TEXT NOT NULL,
                    `walletId` TEXT NOT NULL, `toWalletId` TEXT, `note` TEXT,
                    `receiptImageUri` TEXT, `date` TEXT NOT NULL, `time` TEXT NOT NULL,
                    `isRecurring` INTEGER NOT NULL, `recurringInterval` TEXT,
                    `recurringParentId` TEXT, `transferPairId` TEXT,
                    `createdAt` INTEGER NOT NULL,
                    PRIMARY KEY(`id`)
                )
                """.trimIndent()
            )

            // ── 2. Tanam data nyata — persis alur onboarding user sungguhan ──
            execSQL(
                """
                INSERT INTO finance_accounts
                (id, name, iconName, colorHex, currency, description,
                 isPinProtected, pinHash, isBiometricEnabled, isActive, sortOrder, createdAt)
                VALUES
                ('acc-1', 'Keuangan Pribadi', 'account_balance_wallet', '#0061A4', 'IDR', NULL,
                 0, NULL, 0, 1, 0, 1700000000000)
                """.trimIndent()
            )
            execSQL(
                """
                INSERT INTO wallets
                (id, accountId, name, type, balance, currency, bankName, accountNumber,
                 iconName, colorHex, isDefault, sortOrder, createdAt)
                VALUES
                ('wal-1', 'acc-1', 'Kas', 'CASH', 250000, 'IDR', NULL, NULL,
                 'payments', '#1B8A4C', 1, 0, 1700000000000)
                """.trimIndent()
            )
            execSQL(
                """
                INSERT INTO categories
                (id, accountId, name, type, iconName, colorHex, isCustom, sortOrder)
                VALUES
                ('exp_food', NULL, 'Makanan & Minuman', 'EXPENSE', 'restaurant', '#E53935', 0, 0)
                """.trimIndent()
            )
            execSQL(
                """
                INSERT INTO transactions
                (id, accountId, type, amount, categoryId, walletId, toWalletId, note,
                 receiptImageUri, date, time, isRecurring, recurringInterval,
                 recurringParentId, transferPairId, createdAt)
                VALUES
                ('tx-1', 'acc-1', 'EXPENSE', 15000, 'exp_food', 'wal-1', NULL, 'Jajan',
                 NULL, '2024-01-15', '12:30', 0, NULL, NULL, NULL, 1700000000000)
                """.trimIndent()
            )
            close()
        }

        // ── 3. Jalankan rantai migrasi asli, validasi terhadap schema v6 ────
        db = helper.runMigrationsAndValidate(
            testDbName,
            8,
            true,
            MIGRATION_1_2,
            MIGRATION_2_3,
            MIGRATION_3_4,
            MIGRATION_4_5,
            MIGRATION_5_6,
            MIGRATION_6_7,
            MIGRATION_7_8
        )
        db.close()

        // ── 4. Buka lewat Room sungguhan, pastikan data v1 masih utuh ───────
        val roomDb = Room.databaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            ChatFinDatabase::class.java,
            testDbName
        )
            .addMigrations(
                MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5,
                MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8
            )
            .build()

        runBlockingTestRead {
            val account = roomDb.accountDao().getAllAccounts().first()
            val wallet = roomDb.walletDao().getWalletById("wal-1")
            val transaction = roomDb.transactionDao().getTransactionById("tx-1")

            assertEquals("Keuangan Pribadi", account.first().name)
            assertEquals(250000L, wallet?.balance)
            assertEquals(15000L, transaction?.amount)
            assertEquals("exp_food", transaction?.categoryId)

            // MIGRATION_4_5 wajib menanam SELURUH seed DefaultKeywords, bukan
            // hanya membuat tabel kosong — kalau ini gagal, parser (M5) akan
            // jalan tanpa kamus sama sekali untuk user yang upgrade dari v4.
            val keywordCount = roomDb.categoryKeywordDao().count()
            assertEquals(DefaultKeywords.entries.size, keywordCount)

            val jajanMatch = roomDb.categoryKeywordDao().getAllWithCategoryName(null)
                .first { it.keyword == "jajan" }
            assertEquals("exp_food", jajanMatch.categoryId)
            assertEquals("Makanan & Minuman", jajanMatch.categoryName)
        }

        roomDb.close()
    }
}

// Helper kecil supaya test tetap sinkron tanpa menambah dependency
// kotlinx-coroutines-test yang baru dijadwalkan masuk di M4.
private fun runBlockingTestRead(block: suspend () -> Unit) {
    kotlinx.coroutines.runBlocking { block() }
}
