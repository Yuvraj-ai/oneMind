package com.onemind.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.onemind.app.data.local.dao.CategoryDao
import com.onemind.app.data.local.dao.DerivedDataDao
import com.onemind.app.data.local.dao.EventDao
import com.onemind.app.data.local.dao.MemoryDao
import com.onemind.app.data.local.dao.SearchIndexDao
import com.onemind.app.data.local.entity.*

@Database(
    entities = [
        MemoryEntity::class,
        ContentBlockEntity::class,
        // Derived data (v2)
        OcrResultEntity::class,
        VisionResultEntity::class,
        ExtractedUrlEntity::class,
        ExtractedDateEntity::class,
        ExtractedEntityEntity::class,
        MemorySummaryEntity::class,
        MemoryEmbeddingEntity::class,
        // Category vocabulary and assignments (v3)
        CategoryEntity::class,
        MemoryCategoryEntity::class,
        MemoryCategorizationEntity::class,
        // Full-text search index (v4)
        MemorySearchIndexEntity::class,
        // Detected events (v5)
        DetectedEventEntity::class
    ],
    version = 5,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class OneMindDatabase : RoomDatabase() {
    abstract fun memoryDao(): MemoryDao
    abstract fun derivedDataDao(): DerivedDataDao
    abstract fun categoryDao(): CategoryDao
    abstract fun searchIndexDao(): SearchIndexDao
    abstract fun eventDao(): EventDao
}
