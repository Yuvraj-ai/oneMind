package com.onemind.app.di

import com.onemind.app.domain.processing.ProcessingStage
import dagger.Module
import dagger.multibindings.Multibinds
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Declares the multibound set of [ProcessingStage]s.
 *
 * `@Multibinds` lets the set be legitimately empty, which is exactly the state
 * this ticket lands in: the pipeline machinery works, and each later ticket
 * binds its stage into this set with `@Binds @IntoSet`.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class ProcessingModule {

    @Multibinds
    abstract fun processingStages(): Set<ProcessingStage>
}
