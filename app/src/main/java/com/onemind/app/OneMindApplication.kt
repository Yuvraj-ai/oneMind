package com.onemind.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class OneMindApplication : Application(), Configuration.Provider {

    /** Lets WorkManager construct @HiltWorker workers with their dependencies. */
    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var notificationChannels: com.onemind.app.capture.NotificationChannels

    override fun onCreate() {
        super.onCreate()
        notificationChannels.create()
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}
