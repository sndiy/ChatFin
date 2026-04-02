// app/src/main/java/com/sndiy/chatfin/core/di/DatabaseModule.kt

package com.sndiy.chatfin.core.di

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.sndiy.chatfin.core.data.local.ChatFinDatabase
import com.sndiy.chatfin.core.data.local.DefaultCategories
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
                }
            }
        }

        return Room.databaseBuilder(
            context,
            ChatFinDatabase::class.java,
            "chatfin_database"
        )
            .addCallback(callback)
            .addMigrations(MIGRATION_2_3)
            .build()
            .also { db = it }
    }

    @Provides fun provideAccountDao(db: ChatFinDatabase)     = db.accountDao()
    @Provides fun provideWalletDao(db: ChatFinDatabase)      = db.walletDao()
    @Provides fun provideCategoryDao(db: ChatFinDatabase)    = db.categoryDao()
    @Provides fun provideTransactionDao(db: ChatFinDatabase) = db.transactionDao()
    @Provides fun provideBudgetDao(db: ChatFinDatabase)      = db.budgetDao()
}
