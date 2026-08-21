package com.onemind.app.data.local.entity

import androidx.room.Embedded
import androidx.room.Relation

/**
 * Room relationship class that loads a Memory with all its ContentBlocks.
 */
data class MemoryWithBlocks(
    @Embedded val memory: MemoryEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "memoryId"
    )
    val contentBlocks: List<ContentBlockEntity>
)
