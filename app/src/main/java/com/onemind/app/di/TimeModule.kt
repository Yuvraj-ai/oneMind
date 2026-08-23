package com.onemind.app.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import javax.inject.Singleton

/**
 * Supplies the clock, so nothing that reasons about "now" has to read it ambiently.
 *
 * `Instant.now()` inline is untestable by construction: a test cannot say what now
 * is, so any rule about future versus past can only be asserted against whenever
 * the suite happens to run. Injecting the clock lets those tests pin an instant.
 */
@Module
@InstallIn(SingletonComponent::class)
object TimeModule {

    @Provides
    @Singleton
    fun provideClock(): Clock = Clock.systemUTC()
}
