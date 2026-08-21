package com.onemind.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.onemind.app.domain.model.ContentType

/**
 * Room entity representing a content block within a Memory.
 * Foreign key ensures referential integrity with the parent Memory.
 */
@Entity(
    tableName = "content_blocks",
    foreignKeys = [
        ForeignKey(
            entity = MemoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["memoryId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["memoryId"])]
)
data class ContentBlockEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val memoryId: Long,
    val position: Int,
    val type: ContentType,
    val content: String,
    val thumbnailPath: String? = null,
    val metadata: String? = null
)
