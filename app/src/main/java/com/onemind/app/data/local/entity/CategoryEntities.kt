package com.onemind.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.onemind.app.domain.processing.StageStatus

/*
 * The category vocabulary and the Memories filed under it.
 *
 * Kept apart from DerivedEntities.kt because the lifecycles differ, and that
 * difference matters:
 *
 * - `categories` is **reference data the application owns**. It is seeded from
 *   CategoryDictionary and is not tied to any Memory. Clearing a Memory's derived
 *   data must never touch it.
 * - `memory_categories` is **derived data**. The pipeline writes it and an edit
 *   clears it, exactly like OCR results.
 *
 * They live in one file because the join is meaningless without the vocabulary,
 * and reading them side by side is what makes that asymmetry visible.
 */

/** One entry in the controlled vocabulary. */
@Entity(
    tableName = "categories",
    // Unique on name so seeding is idempotent: INSERT OR IGNORE makes re-running
    // it a no-op rather than a source of duplicates.
    indices = [Index(value = ["name"], unique = true)]
)
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** Unpopulated in v1. See [com.onemind.app.domain.model.Category.parentId]. */
    val parentId: Long? = null
)

/**
 * A Memory filed under a category.
 *
 * The composite primary key makes a duplicate assignment impossible at the
 * storage layer, so a model returning "Technology" twice cannot produce two rows.
 *
 * `categoryId` uses RESTRICT rather than CASCADE: a category in use should not be
 * silently deletable out from under the Memories that reference it.
 */
@Entity(
    tableName = "memory_categories",
    primaryKeys = ["memoryId", "categoryId"],
    foreignKeys = [
        ForeignKey(
            entity = MemoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["memoryId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [Index(value = ["memoryId"]), Index(value = ["categoryId"])]
)
data class MemoryCategoryEntity(
    val memoryId: Long,
    val categoryId: Long
)

/**
 * Outcome of running categorization over a Memory.
 *
 * Separate from the assignments because an empty assignment list is ambiguous on
 * its own: it could mean no provider is configured, or that a model ran and
 * judged that nothing in the vocabulary fits, or that the Memory has not been
 * processed at all. Those need different things said to the user, so the
 * distinction is stored rather than guessed. Mirrors `memory_summaries`.
 */
@Entity(
    tableName = "memory_categorization",
    foreignKeys = [
        ForeignKey(
            entity = MemoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["memoryId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class MemoryCategorizationEntity(
    @PrimaryKey val memoryId: Long,
    val status: StageStatus,
    val processedAt: Long,
    val providerModel: String?
)
