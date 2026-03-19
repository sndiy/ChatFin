package com.sndiy.chatfin.core.di

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
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
                // Seed kategori default SEKALI saat pertama install
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
            .build()
            .also { db = it }
    }

    @Provides fun provideAccountDao(db: ChatFinDatabase)     = db.accountDao()
    @Provides fun provideWalletDao(db: ChatFinDatabase)      = db.walletDao()
    @Provides fun provideCategoryDao(db: ChatFinDatabase)    = db.categoryDao()
    @Provides fun provideTransactionDao(db: ChatFinDatabase) = db.transactionDao()
    // provideBudgetDao & provideSavingsGoalDao dihapus
}