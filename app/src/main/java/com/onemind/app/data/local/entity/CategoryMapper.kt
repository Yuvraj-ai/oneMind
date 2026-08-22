package com.onemind.app.data.local.entity

import com.onemind.app.domain.model.Category
import com.onemind.app.domain.model.CategorizationResult
import java.time.Instant

/** Storage <-> domain for the category vocabulary. */
object CategoryMapper {

    fun CategoryEntity.toDomain(): Category =
        Category(id = id, name = name, parentId = parentId)

    fun MemoryCategorizationEntity.toDomain(): CategorizationResult =
        CategorizationResult(
            memoryId = memoryId,
            status = status,
            providerModel = providerModel,
            processedAt = Instant.ofEpochMilli(processedAt)
        )

    fun CategorizationResult.toEntity(): MemoryCategorizationEntity =
        MemoryCategorizationEntity(
            memoryId = memoryId,
            status = status,
            processedAt = processedAt.toEpochMilli(),
            providerModel = providerModel
        )
}
