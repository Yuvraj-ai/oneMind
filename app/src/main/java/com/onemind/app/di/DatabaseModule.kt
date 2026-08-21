package com.onemind.app.di

import android.content.Context
import androidx.room.Room
import com.onemind.app.data.local.OneMindDatabase
import com.onemind.app.data.local.dao.MemoryDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): OneMindDatabase {
        return Room.databaseBuilder(
            context,
            OneMindDatabase::class.java,
            "onemind.db"
        ).build()
    }

    @Provides
    fun provideMemoryDao(database: OneMindDatabase): MemoryDao {
        return database.memoryDao()
    }
}
