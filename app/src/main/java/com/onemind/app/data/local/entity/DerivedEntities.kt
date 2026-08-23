package com.onemind.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.onemind.app.domain.model.DerivedSource
import com.onemind.app.domain.model.EntityType
import com.onemind.app.domain.processing.StageStatus

/*
 * Storage for everything the Processing Pipeline infers.
 *
 * Every table cascades from `memories`, so deleting a Memory takes its derived
 * data with it and cannot leave orphans. They are grouped in one file because
 * they are one concern with one repository and one lifecycle: they are written
 * together by the pipeline and cleared together on edit.
 */

private const val MEMORY_ID = "memoryId"

/** Text recognised in one image content block. */
@Entity(
    tableName = "ocr_results",
    foreignKeys = [
        ForeignKey(
            entity = MemoryEntity::class,
            parentColumns = ["id"],
            childColumns = [MEMORY_ID],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = [MEMORY_ID]), Index(value = ["contentBlockId"])]
)
data class OcrResultEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val memoryId: Long,
    val contentBlockId: Long,
    val status: StageStatus,
    val extractedText: String,
    val processedAt: Long
)

/** A model's description of one image content block. */
@Entity(
    tableName = "vision_results",
    foreignKeys = [
        ForeignKey(
            entity = MemoryEntity::class,
            parentColumns = ["id"],
            childColumns = [MEMORY_ID],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = [MEMORY_ID]), Index(value = ["contentBlockId"])]
)
data class VisionResultEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val memoryId: Long,
    val contentBlockId: Long,
    val status: StageStatus,
    val description: String,
    val providerModel: String?,
    val processedAt: Long
)

/** A link found in a Memory's text. */
@Entity(
    tableName = "extracted_urls",
    foreignKeys = [
        ForeignKey(
            entity = MemoryEntity::class,
            parentColumns = ["id"],
            childColumns = [MEMORY_ID],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = [MEMORY_ID]), Index(value = ["domain"])]
)
data class ExtractedUrlEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val memoryId: Long,
    val rawUrl: String,
    val normalizedUrl: String,
    val domain: String
)

/** A date or time the content mentions. Indexed for temporal retrieval (M15). */
@Entity(
    tableName = "extracted_dates",
    foreignKeys = [
        ForeignKey(
            entity = MemoryEntity::class,
            parentColumns = ["id"],
            childColumns = [MEMORY_ID],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = [MEMORY_ID]), Index(value = ["parsedInstant"])]
)
data class ExtractedDateEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val memoryId: Long,
    val rawText: String,
    /** Null when the text was too vague to resolve to an instant. */
    val parsedInstant: Long?,
    val isEventTime: Boolean,
    val source: DerivedSource
)

/** A named thing found in a Memory's content. Indexed by name for retrieval. */
@Entity(
    tableName = "extracted_entities",
    foreignKeys = [
        ForeignKey(
            entity = MemoryEntity::class,
            parentColumns = ["id"],
            childColumns = [MEMORY_ID],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = [MEMORY_ID]), Index(value = ["name"])]
)
data class ExtractedEntityEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val memoryId: Long,
    val name: String,
    val entityType: EntityType,
    /** Null unless the producing model supplied one. Never fabricated. */
    val confidence: Float?,
    val source: DerivedSource
)

/** One summary per Memory, so memoryId is the primary key. */
@Entity(
    tableName = "memory_summaries",
    foreignKeys = [
        ForeignKey(
            entity = MemoryEntity::class,
            parentColumns = ["id"],
            childColumns = [MEMORY_ID],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class MemorySummaryEntity(
    @PrimaryKey val memoryId: Long,
    val summaryText: String,
    /** A 3-8 word heading for the Memory, generated alongside the summary. */
    val title: String? = null,
    val status: StageStatus,
    val generatedAt: Long,
    val providerModel: String?
)

/**
 * One embedding per Memory, so memoryId is the primary key. That is what makes
 * re-embedding a replace rather than an append: a Memory can never end up with
 * two active vectors.
 *
 * The vector is a BLOB. 384 floats is 1536 bytes packed, against roughly 4.6KB
 * as comma-separated text.
 */
@Entity(
    tableName = "memory_embeddings",
    foreignKeys = [
        ForeignKey(
            entity = MemoryEntity::class,
            parentColumns = ["id"],
            childColumns = [MEMORY_ID],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class MemoryEmbeddingEntity(
    @PrimaryKey val memoryId: Long,
    val vector: ByteArray,
    val dimensions: Int,
    val modelId: String,
    val generatedAt: Long
) {
    // ByteArray uses identity equals, so the generated data-class equality
    // would compare references and quietly misbehave in tests.
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is MemoryEmbeddingEntity) return false
        return memoryId == other.memoryId &&
            dimensions == other.dimensions &&
            modelId == other.modelId &&
            generatedAt == other.generatedAt &&
            vector.contentEquals(other.vector)
    }

    override fun hashCode(): Int {
        var result = memoryId.hashCode()
        result = 31 * result + vector.contentHashCode()
        result = 31 * result + dimensions
        result = 31 * result + modelId.hashCode()
        result = 31 * result + generatedAt.hashCode()
        return result
    }
}
