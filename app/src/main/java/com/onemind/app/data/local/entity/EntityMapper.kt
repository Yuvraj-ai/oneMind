package com.onemind.app.data.local.entity

import com.onemind.app.domain.model.ContentBlock
import com.onemind.app.domain.model.Memory
import java.time.Instant

/**
 * Maps between Room entities and domain models.
 */
object EntityMapper {

    fun MemoryWithBlocks.toDomain(): Memory {
        return Memory(
            id = memory.id,
            createdAt = Instant.ofEpochMilli(memory.createdAt),
            updatedAt = Instant.ofEpochMilli(memory.updatedAt),
            sourceType = memory.sourceType,
            processingState = memory.processingState,
            sourcePackage = memory.sourcePackage,
            contentBlocks = contentBlocks
                .sortedBy { it.position }
                .map { it.toDomain() }
        )
    }

    fun ContentBlockEntity.toDomain(): ContentBlock {
        return ContentBlock(
            id = id,
            memoryId = memoryId,
            position = position,
            type = type,
            content = content,
            thumbnailPath = thumbnailPath,
            metadata = metadata
        )
    }

    fun Memory.toEntity(): MemoryEntity {
        return MemoryEntity(
            id = id,
            createdAt = createdAt.toEpochMilli(),
            updatedAt = updatedAt.toEpochMilli(),
            sourceType = sourceType,
            processingState = processingState,
            sourcePackage = sourcePackage
        )
    }

    fun ContentBlock.toEntity(memoryId: Long): ContentBlockEntity {
        return ContentBlockEntity(
            id = id,
            memoryId = memoryId,
            position = position,
            type = type,
            content = content,
            thumbnailPath = thumbnailPath,
            metadata = metadata
        )
    }
}
