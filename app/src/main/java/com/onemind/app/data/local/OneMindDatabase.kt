package com.onemind.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.onemind.app.data.local.dao.MemoryDao
import com.onemind.app.data.local.entity.ContentBlockEntity
import com.onemind.app.data.local.entity.MemoryEntity

@Database(
    entities = [MemoryEntity::class, ContentBlockEntity::class],
    version = 1,
    exportSchema = true
)
abstract class OneMindDatabase : RoomDatabase() {
    abstract fun memoryDao(): MemoryDao
}
