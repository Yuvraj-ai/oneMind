package com.onemind.app.di

import com.onemind.app.data.repository.DerivedDataRepositoryImpl
import com.onemind.app.data.repository.MemoryRepositoryImpl
import com.onemind.app.domain.repository.DerivedDataRepository
import com.onemind.app.domain.repository.MemoryRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindMemoryRepository(
        impl: MemoryRepositoryImpl
    ): MemoryRepository

    @Binds
    @Singleton
    abstract fun bindDerivedDataRepository(
        impl: DerivedDataRepositoryImpl
    ): DerivedDataRepository
}
