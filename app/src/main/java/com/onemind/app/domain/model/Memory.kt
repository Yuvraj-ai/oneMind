package com.onemind.app.domain.model

import java.time.Instant

/**
 * A Memory is the core domain object: one atomic unit of saved information.
 * It may contain multiple content blocks (text, images, URLs) and is enriched
 * asynchronously by the processing pipeline.
 *
 * [contentBlocks] is what the user gave us and is authoritative. [derived] is
 * what a machine inferred from it, and can be cleared and regenerated freely.
 * Keeping them in separate fields is what stops one being mistaken for the other.
 */
data class Memory(
    val id: Long = 0,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now(),
    val sourceType: SourceType = SourceType.MANUAL,
    val processingState: ProcessingState = ProcessingState.DRAFT,
    val contentBlocks: List<ContentBlock> = emptyList(),
    /** Package name of the source app (for SHARE type), nullable */
    val sourcePackage: String? = null,
    /**
     * Pipeline output. [DerivedData.EMPTY] before processing, and also whenever a
     * Memory is loaded through a path that does not need enrichments.
     */
    val derived: DerivedData = DerivedData.EMPTY
) {
    /** Text the user themselves supplied, in block order. */
    fun userText(): String = contentBlocks
        .filter { it.type == ContentType.TEXT }
        .joinToString("\n") { it.content }

    /** Image blocks, in block order. */
    fun imageBlocks(): List<ContentBlock> = contentBlocks.filter { it.type == ContentType.IMAGE }

    /**
     * Everything a stage can read as text: what the user wrote, plus whatever
     * earlier stages recognised in the images.
     */
    fun allText(): List<Pair<DerivedSource, String>> = buildList {
        val typed = userText()
        if (typed.isNotBlank()) add(DerivedSource.USER_TEXT to typed)
        addAll(derived.derivedText())
    }
}
