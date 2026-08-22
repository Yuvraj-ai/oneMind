package com.onemind.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.onemind.app.capture.NotificationChannels
import com.onemind.app.data.ai.ProviderRestorer
import com.onemind.app.data.processing.StaleProcessingSweeper
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class OneMindApplication : Application(), Configuration.Provider {

    /** Lets WorkManager construct @HiltWorker workers with their dependencies. */
    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var notificationChannels: NotificationChannels

    @Inject
    lateinit var providerRestorer: ProviderRestorer

    @Inject
    lateinit var staleProcessingSweeper: StaleProcessingSweeper

    /**
     * Application-lifetime scope for start-up work.
     *
     * Deliberately not tied to any screen or worker: both tasks below must complete
     * even if the user immediately navigates, and neither has a UI to attach to.
     */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()

        notificationChannels.create()

        appScope.launch {
            // Restore the provider before anything can want it. Enrichment runs in a
            // worker that Android usually starts in a fresh process, where nothing has
            // configured one — so without this, every AI feature silently did nothing
            // in the background while Settings showed it as configured.
            providerRestorer.restore()

            // Release Memories claimed by a run that died with its process. Without
            // this they stay in PROCESSING forever: the card spins, and the retry
            // affordance never appears because retry is offered only for FAILED.
            staleProcessingSweeper.sweep()
        }
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}
