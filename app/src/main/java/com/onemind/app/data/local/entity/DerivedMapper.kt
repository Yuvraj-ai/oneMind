package com.onemind.app.data.local.entity

import com.onemind.app.data.local.VectorCodec
import com.onemind.app.domain.model.*
import java.time.Instant

/**
 * Maps derived-data entities to and from their domain types.
 *
 * Kept apart from [EntityMapper] so the boundary between source content and
 * derived intelligence is visible in the file layout too, not just in the types.
 */
object DerivedMapper {

    // --- to domain --------------------------------------------------------

    fun OcrResultEntity.toDomain() = OcrResult(
        id = id,
        memoryId = memoryId,
        contentBlockId = contentBlockId,
        status = status,
        extractedText = extractedText,
        processedAt = Instant.ofEpochMilli(processedAt)
    )

    fun VisionResultEntity.toDomain() = VisionResult(
        id = id,
        memoryId = memoryId,
        contentBlockId = contentBlockId,
        status = status,
        description = description,
        providerModel = providerModel,
        processedAt = Instant.ofEpochMilli(processedAt)
    )

    fun ExtractedUrlEntity.toDomain() = ExtractedUrl(
        id = id,
        memoryId = memoryId,
        rawUrl = rawUrl,
        normalizedUrl = normalizedUrl,
        domain = domain
    )

    fun ExtractedDateEntity.toDomain() = ExtractedDate(
        id = id,
        memoryId = memoryId,
        rawText = rawText,
        parsedInstant = parsedInstant?.let(Instant::ofEpochMilli),
        isEventTime = isEventTime,
        source = source
    )

    fun ExtractedEntityEntity.toDomain() = ExtractedEntity(
        id = id,
        memoryId = memoryId,
        name = name,
        entityType = entityType,
        confidence = confidence,
        source = source
    )

    fun MemorySummaryEntity.toDomain() = MemorySummary(
        memoryId = memoryId,
        summaryText = summaryText,
        title = title,
        status = status,
        generatedAt = Instant.ofEpochMilli(generatedAt),
        providerModel = providerModel
    )

    fun MemoryEmbeddingEntity.toDomain() = MemoryEmbedding(
        memoryId = memoryId,
        vector = VectorCodec.decode(vector),
        dimensions = dimensions,
        modelId = modelId,
        generatedAt = Instant.ofEpochMilli(generatedAt)
    )

    // --- to entity --------------------------------------------------------

    fun OcrResult.toEntity() = OcrResultEntity(
        id = id,
        memoryId = memoryId,
        contentBlockId = contentBlockId,
        status = status,
        extractedText = extractedText,
        processedAt = processedAt.toEpochMilli()
    )

    fun VisionResult.toEntity() = VisionResultEntity(
        id = id,
        memoryId = memoryId,
        contentBlockId = contentBlockId,
        status = status,
        description = description,
        providerModel = providerModel,
        processedAt = processedAt.toEpochMilli()
    )

    fun ExtractedUrl.toEntity() = ExtractedUrlEntity(
        id = id,
        memoryId = memoryId,
        rawUrl = rawUrl,
        normalizedUrl = normalizedUrl,
        domain = domain
    )

    fun ExtractedDate.toEntity() = ExtractedDateEntity(
        id = id,
        memoryId = memoryId,
        rawText = rawText,
        parsedInstant = parsedInstant?.toEpochMilli(),
        isEventTime = isEventTime,
        source = source
    )

    fun ExtractedEntity.toEntity() = ExtractedEntityEntity(
        id = id,
        memoryId = memoryId,
        name = name,
        entityType = entityType,
        confidence = confidence,
        source = source
    )

    fun MemorySummary.toEntity() = MemorySummaryEntity(
        memoryId = memoryId,
        summaryText = summaryText,
        title = title,
        status = status,
        generatedAt = generatedAt.toEpochMilli(),
        providerModel = providerModel
    )

    fun MemoryEmbedding.toEntity() = MemoryEmbeddingEntity(
        memoryId = memoryId,
        vector = VectorCodec.encode(vector),
        dimensions = dimensions,
        modelId = modelId,
        generatedAt = generatedAt.toEpochMilli()
    )
}
