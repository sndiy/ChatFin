package com.sndiy.chatfin.core.di

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.sndiy.chatfin.core.data.local.ChatFinDatabase
import com.sndiy.chatfin.core.data.security.DesktopSecureStorage
import com.sndiy.chatfin.core.data.security.SecureStorage
import com.sndiy.chatfin.core.data.sync.NoOpOutboundSync
import com.sndiy.chatfin.core.data.sync.OutboundSync
import kotlinx.coroutines.Dispatchers
import org.koin.core.module.Module
import org.koin.dsl.module
import java.io.File

actual fun platformModule(): Module = module {
    single<SecureStorage> { DesktopSecureStorage() }
    single<OutboundSync> { NoOpOutboundSync() }

    single<ChatFinDatabase> {
        val dbFile = File(System.getProperty("user.home"), ".chatfin/chatfin.db")
        dbFile.parentFile.mkdirs()
        val builder = Room.databaseBuilder<ChatFinDatabase>(
            name = dbFile.absolutePath
        )
        builder
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO)
            .build()
    }
}
