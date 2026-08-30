package com.sndiy.chatfin.core.di

import android.content.Context
import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.sndiy.chatfin.core.data.local.ChatFinDatabase
import com.sndiy.chatfin.core.data.security.AndroidSecureStorage
import com.sndiy.chatfin.core.data.security.SecureStorage
import com.sndiy.chatfin.core.data.sync.NoOpOutboundSync
import com.sndiy.chatfin.core.data.sync.OutboundSync
import kotlinx.coroutines.Dispatchers
import org.koin.core.module.Module
import org.koin.dsl.module

actual fun platformModule(): Module = module {
    single<SecureStorage> { AndroidSecureStorage(get<Context>()) }
    single<OutboundSync> { NoOpOutboundSync() }

    single<ChatFinDatabase> {
        val context = get<Context>()
        val builder = Room.databaseBuilder(
            context,
            ChatFinDatabase::class.java,
            "chatfin_database"
        )
        builder
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO)
            .build()
    }
}
