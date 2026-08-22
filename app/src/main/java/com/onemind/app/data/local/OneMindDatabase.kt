package com.onemind.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.onemind.app.data.local.dao.DerivedDataDao
import com.onemind.app.data.local.dao.MemoryDao
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
        MemoryEmbeddingEntity::class
    ],
    version = 2,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class OneMindDatabase : RoomDatabase() {
    abstract fun memoryDao(): MemoryDao
    abstract fun derivedDataDao(): DerivedDataDao
}
