package com.onemind.app.domain.model

import com.onemind.app.domain.processing.StageStatus
import java.time.Instant

/**
 * Everything the Processing Pipeline inferred about a Memory.
 *
 * Held separately from the Memory's content blocks, because the two are not
 * equivalent: content is what the user gave us and is authoritative; this is a
 * machine's reading of it, and can be thrown away and regenerated at any time.
 * A Memory that has not been processed yet has [DerivedData.EMPTY].
 *
 * The embedding vector is deliberately *not* here. It is bulky and only vector
 * search reads it, so it is fetched on its own rather than riding along every
 * time the feed loads a Memory.
 */
data class DerivedData(
    /** One entry per image content block that OCR ran against. */
    val ocrResults: List<OcrResult> = emptyList(),

    /** One entry per image content block that vision ran against. */
    val visionResults: List<VisionResult> = emptyList(),

    val urls: List<ExtractedUrl> = emptyList(),
    val dates: List<ExtractedDate> = emptyList(),
    val entities: List<ExtractedEntity> = emptyList(),
    val summary: MemorySummary? = null
) {
    /** All text a later stage can read: OCR output plus image descriptions. */
    fun derivedText(): List<Pair<DerivedSource, String>> = buildList {
        ocrResults
            .filter { it.status == StageStatus.SUCCESS && it.extractedText.isNotBlank() }
            .forEach { add(DerivedSource.OCR to it.extractedText) }
        visionResults
            .filter { it.status == StageStatus.SUCCESS && it.description.isNotBlank() }
            .forEach { add(DerivedSource.VISION to it.description) }
    }

    companion object {
        val EMPTY = DerivedData()
    }
}

/** Text recognised in one image by on-device OCR. */
data class OcrResult(
    val id: Long = 0,
    val memoryId: Long,
    /** The image content block this came from. */
    val contentBlockId: Long,
    val status: StageStatus,
    val extractedText: String = "",
    val processedAt: Instant = Instant.now()
)

/** A model's description of one image. */
data class VisionResult(
    val id: Long = 0,
    val memoryId: Long,
    val contentBlockId: Long,
    val status: StageStatus,
    val description: String = "",
    /** Which provider and model produced this, when one did. */
    val providerModel: String? = null,
    val processedAt: Instant = Instant.now()
)

/** A link found in a Memory's text. */
data class ExtractedUrl(
    val id: Long = 0,
    val memoryId: Long,
    val rawUrl: String,
    /** Lowercased scheme and host, no trailing slash, no fragment. */
    val normalizedUrl: String,
    val domain: String
)

/**
 * A date or time mentioned in a Memory's content.
 *
 * Distinct from the Memory's own `createdAt`: a screenshot captured today can
 * describe an event next month, and conflating the two makes temporal retrieval
 * wrong. [isEventTime] marks that distinction.
 */
data class ExtractedDate(
    val id: Long = 0,
    val memoryId: Long,
    /** The text as it appeared, e.g. "September 15 at 10 AM". */
    val rawText: String,
    /** Resolved instant, when the text was unambiguous enough to resolve. */
    val parsedInstant: Instant? = null,
    /** True when this is a time the content is *about*, not a capture time. */
    val isEventTime: Boolean = true,
    val source: DerivedSource = DerivedSource.USER_TEXT
)

/** A named thing found in a Memory's content. */
data class ExtractedEntity(
    val id: Long = 0,
    val memoryId: Long,
    val name: String,
    val entityType: EntityType,
    /** Only present when the producing model supplied one. Never invented. */
    val confidence: Float? = null,
    val source: DerivedSource = DerivedSource.USER_TEXT
)

/** A short description of what a Memory is generally about. */
data class MemorySummary(
    val memoryId: Long,
    val summaryText: String,
    val status: StageStatus = StageStatus.SUCCESS,
    val generatedAt: Instant = Instant.now(),
    val providerModel: String? = null
)

/**
 * A Memory's vector representation, for semantic search.
 *
 * Fetched separately from [DerivedData] because it is ~1.5KB of floats that only
 * retrieval reads.
 */
data class MemoryEmbedding(
    val memoryId: Long,
    val vector: FloatArray,
    val dimensions: Int,
    /** Which embedding model produced this, so a model change can invalidate it. */
    val modelId: String,
    val generatedAt: Instant = Instant.now()
) {
    // FloatArray uses identity equals, so data-class equality would be wrong.
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is MemoryEmbedding) return false
        return memoryId == other.memoryId &&
            dimensions == other.dimensions &&
            modelId == other.modelId &&
            vector.contentEquals(other.vector)
    }

    override fun hashCode(): Int {
        var result = memoryId.hashCode()
        result = 31 * result + vector.contentHashCode()
        result = 31 * result + dimensions
        result = 31 * result + modelId.hashCode()
        return result
    }
}
