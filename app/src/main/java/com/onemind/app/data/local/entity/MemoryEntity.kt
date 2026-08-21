package com.onemind.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.onemind.app.domain.model.ProcessingState
import com.onemind.app.domain.model.SourceType

/**
 * Room entity representing a persisted Memory.
 */
@Entity(tableName = "memories")
data class MemoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val createdAt: Long, // epoch millis
    val updatedAt: Long, // epoch millis
    val sourceType: SourceType,
    val processingState: ProcessingState,
    val sourcePackage: String? = null
)
