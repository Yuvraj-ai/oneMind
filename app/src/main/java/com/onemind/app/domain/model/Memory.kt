package com.onemind.app.domain.model

import java.time.Instant

/**
 * A Memory is the core domain object: one atomic unit of saved information.
 * It may contain multiple content blocks (text, images, URLs) and is enriched
 * asynchronously by the processing pipeline.
 */
data class Memory(
    val id: Long = 0,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now(),
    val sourceType: SourceType = SourceType.MANUAL,
    val processingState: ProcessingState = ProcessingState.DRAFT,
    val contentBlocks: List<ContentBlock> = emptyList(),
    /** Package name of the source app (for SHARE type), nullable */
    val sourcePackage: String? = null
)
