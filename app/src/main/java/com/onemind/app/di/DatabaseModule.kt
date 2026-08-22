package com.onemind.app.di

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.onemind.app.data.local.CategorySeeder
import com.onemind.app.data.local.Migrations
import com.onemind.app.data.local.OneMindDatabase
import com.onemind.app.data.local.dao.CategoryDao
import com.onemind.app.data.local.dao.DerivedDataDao
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
        )
            .addMigrations(*Migrations.ALL)
            // Seeds the category vocabulary on a fresh install. Upgrades get it
            // from MIGRATION_2_3 instead; both call the same seeder, so the two
            // paths cannot produce different tables.
            .addCallback(object : RoomDatabase.Callback() {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    CategorySeeder.seed(db)
                }
            })
            .build()
    }

    @Provides
    fun provideMemoryDao(database: OneMindDatabase): MemoryDao {
        return database.memoryDao()
    }

    @Provides
    fun provideDerivedDataDao(database: OneMindDatabase): DerivedDataDao {
        return database.derivedDataDao()
    }

    @Provides
    fun provideCategoryDao(database: OneMindDatabase): CategoryDao {
        return database.categoryDao()
    }
}
