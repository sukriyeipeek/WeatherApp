package com.sukriye.havam

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.sukriye.havam.data.WeatherSyncWorker
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class HavamApp : Application(), Configuration.Provider {

    // Worker'lara Hilt ile bağımlılık verebilmek için WorkManager'ı kendi fabrikamızla başlatıyoruz
    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        WeatherSyncWorker.schedule(this)
    }
}
