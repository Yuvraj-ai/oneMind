package com.onemind.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.onemind.app.data.local.entity.CategoryEntity
import com.onemind.app.data.local.entity.MemoryCategorizationEntity
import com.onemind.app.data.local.entity.MemoryCategoryEntity

/**
 * The category vocabulary and Memory assignments.
 *
 * Deliberately offers no way to insert a category. The vocabulary is seeded from
 * [com.onemind.app.domain.categories.CategoryDictionary] and nothing at runtime
 * may add to it — leaving the method out is a stronger guarantee than a comment
 * asking callers not to.
 */
@Dao
interface CategoryDao {

    /** The whole vocabulary, in seeded order. */
    @Query("SELECT * FROM categories ORDER BY id")
    suspend fun getAllCategories(): List<CategoryEntity>

    @Query(
        """
        SELECT c.* FROM categories c
        INNER JOIN memory_categories mc ON mc.categoryId = c.id
        WHERE mc.memoryId = :memoryId
        ORDER BY c.id
        """
    )
    suspend fun getCategoriesForMemory(memoryId: Long): List<CategoryEntity>

    /**
     * Categories for several Memories at once, for the feed.
     *
     * One query rather than one per row: the feed renders chips on every card, and
     * a query per card would cost on every scroll.
     */
    @Query(
        """
        SELECT mc.memoryId AS memoryId, c.id AS id, c.name AS name, c.parentId AS parentId
        FROM memory_categories mc
        INNER JOIN categories c ON c.id = mc.categoryId
        WHERE mc.memoryId IN (:memoryIds)
        ORDER BY mc.memoryId, c.id
        """
    )
    suspend fun getCategoriesForMemories(memoryIds: List<Long>): List<MemoryCategoryRow>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAssignments(assignments: List<MemoryCategoryEntity>)

    @Query("DELETE FROM memory_categories WHERE memoryId = :memoryId")
    suspend fun deleteAssignments(memoryId: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCategorization(record: MemoryCategorizationEntity)

    @Query("SELECT * FROM memory_categorization WHERE memoryId = :memoryId")
    suspend fun getCategorization(memoryId: Long): MemoryCategorizationEntity?

    @Query("DELETE FROM memory_categorization WHERE memoryId = :memoryId")
    suspend fun deleteCategorization(memoryId: Long)

    /**
     * Replace a Memory's categories wholesale.
     *
     * Reprocessing should not union with a previous run's answer: if the pipeline
     * now judges a Memory to be about Travel and not Shopping, Shopping must go.
     */
    @Transaction
    suspend fun replaceAssignments(memoryId: Long, categoryIds: List<Long>) {
        deleteAssignments(memoryId)
        if (categoryIds.isNotEmpty()) {
            insertAssignments(categoryIds.map { MemoryCategoryEntity(memoryId, it) })
        }
    }
}

/** Flat join row, so the feed can group categories by Memory in one pass. */
data class MemoryCategoryRow(
    val memoryId: Long,
    val id: Long,
    val name: String,
    val parentId: Long?
)
