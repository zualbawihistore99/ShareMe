package com.example

import android.app.Application
import com.example.data.local.DevicePreferencesRepository
import com.example.data.local.ShareMeDatabase
import com.example.data.local.TransferRepository
import com.example.transfer.FileTransferEngine
import com.example.transfer.NearbyDiscoveryManager

class AppContainer(application: Application) {
    val database: ShareMeDatabase = ShareMeDatabase.create(application)
    val transferRepository: TransferRepository = TransferRepository(database.transferDao())
    val devicePreferencesRepository: DevicePreferencesRepository =
        DevicePreferencesRepository(application)
    val discoveryManager: NearbyDiscoveryManager = NearbyDiscoveryManager(application)
    val transferEngine: FileTransferEngine = FileTransferEngine(application, transferRepository)
}

class ShareMeApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
