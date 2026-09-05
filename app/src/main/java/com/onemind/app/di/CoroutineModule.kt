package com.onemind.app.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Qualifier
import javax.inject.Singleton

/**
 * A `CoroutineScope` that lives as long as the process.
 *
 * For work that must finish even though the thing that started it is gone. A
 * `viewModelScope` is cancelled when its ViewModel is cleared, which for a screen the
 * user is navigating away from is *immediately* — so anything launched there as the
 * user leaves is racing its own cancellation.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

/**
 * Supplies the process-lifetime scope.
 *
 * `OneMindApplication` has its own private field of exactly this shape, documented as
 * "deliberately not tied to any screen or worker". Making the idea injectable is what lets a
 * ViewModel use it — and #50 is what happens when one cannot: the composer launched its
 * commit into `viewModelScope` and then navigated back in the same function, so the enqueue
 * four suspension points down never ran, and no hand-typed Memory was ever enriched, indexed
 * or searchable.
 *
 * The Application's private scope is deliberately left alone here rather than folded into
 * this one. Collapsing the two is a tidy-up with no behavioural effect, and bundling it into
 * a bug fix would make the fix harder to review and harder to revert.
 *
 * `SupervisorJob`, so one failed piece of start-up or commit work does not cancel the
 * rest. `Dispatchers.IO`, because everything launched here touches the database or the
 * filesystem.
 *
 * Nothing cancels this scope, which is the point and also the caution: only put work
 * here that is bounded and must complete. Anything tied to a screen belongs in
 * `viewModelScope`, where abandoning it is the correct behaviour.
 */
@Module
@InstallIn(SingletonComponent::class)
object CoroutineModule {

    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(): CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.IO)
}
