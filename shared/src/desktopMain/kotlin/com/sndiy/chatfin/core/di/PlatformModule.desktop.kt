package com.sndiy.chatfin.core.di

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.sndiy.chatfin.core.data.auth.DesktopAuthRepository
import com.sndiy.chatfin.core.data.auth.DesktopAuthRepositoryImpl
import com.sndiy.chatfin.core.data.local.ChatFinDatabase
import com.sndiy.chatfin.core.data.security.DesktopSecureStorage
import com.sndiy.chatfin.core.data.security.SecureStorage
import com.sndiy.chatfin.core.data.sync.DesktopRealtimeSyncRepository
import com.sndiy.chatfin.core.data.sync.DesktopSyncOrchestrator
import com.sndiy.chatfin.core.data.sync.DesktopSyncRepository
import com.sndiy.chatfin.core.data.sync.NoOpOutboundSync
import com.sndiy.chatfin.core.data.sync.OutboundSync
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.auth.FirebaseAuth
import dev.gitlive.firebase.auth.auth
import dev.gitlive.firebase.firestore.FirebaseFirestore
import dev.gitlive.firebase.firestore.firestore
import kotlinx.coroutines.Dispatchers
import org.koin.core.module.Module
import org.koin.dsl.module
import java.io.File

actual fun platformModule(): Module = module {
    val desktopSecureStorage = DesktopSecureStorage()
    single<DesktopSecureStorage> { desktopSecureStorage }
    single<SecureStorage> { desktopSecureStorage }

    single<FirebaseAuth> { Firebase.auth }
    single<FirebaseFirestore> { Firebase.firestore }

    single<DesktopAuthRepository> { DesktopAuthRepositoryImpl(get(), get(), get()) }
    single<OutboundSync> { NoOpOutboundSync() }

    single { DesktopSyncRepository(get(), get(), get(), get(), get(), get(), get()) }
    single { DesktopRealtimeSyncRepository(get(), get(), get(), get(), get(), get(), get(), get()) }
    single { DesktopSyncOrchestrator(get(), get(), get(), get(), get()) }

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

