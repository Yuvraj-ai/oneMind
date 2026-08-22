package com.onemind.app.data.local.dao

import androidx.room.*
import com.onemind.app.data.local.entity.ContentBlockEntity
import com.onemind.app.data.local.entity.MemoryEntity
import com.onemind.app.data.local.entity.MemoryWithBlocks
import com.onemind.app.domain.model.ProcessingState
import kotlinx.coroutines.flow.Flow

@Dao
interface MemoryDao {

    @Transaction
    @Query("SELECT * FROM memories ORDER BY createdAt DESC")
    fun observeAllMemories(): Flow<List<MemoryWithBlocks>>

    @Transaction
    @Query("SELECT * FROM memories WHERE id = :memoryId")
    suspend fun getMemoryById(memoryId: Long): MemoryWithBlocks?

    @Transaction
    @Query("SELECT * FROM memories ORDER BY createdAt DESC")
    suspend fun getAllMemories(): List<MemoryWithBlocks>

    /**
     * Several Memories in one query, for hydrating search results.
     *
     * Returns them in no particular order — SQL `IN` makes no promise — so callers
     * that care about ranking must reorder.
     */
    @Transaction
    @Query("SELECT * FROM memories WHERE id IN (:ids)")
    suspend fun getMemoriesByIds(ids: List<Long>): List<MemoryWithBlocks>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMemory(memory: MemoryEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertContentBlocks(blocks: List<ContentBlockEntity>)

    @Update
    suspend fun updateMemory(memory: MemoryEntity)

    @Query("UPDATE memories SET processingState = :state, updatedAt = :updatedAt WHERE id = :memoryId")
    suspend fun updateProcessingState(memoryId: Long, state: ProcessingState, updatedAt: Long)

    @Query("DELETE FROM memories WHERE id = :memoryId")
    suspend fun deleteMemory(memoryId: Long)

    @Query("DELETE FROM content_blocks WHERE memoryId = :memoryId")
    suspend fun deleteContentBlocks(memoryId: Long)

    @Transaction
    suspend fun insertMemoryWithBlocks(memory: MemoryEntity, blocks: List<ContentBlockEntity>): Long {
        val memoryId = insertMemory(memory)
        val blocksWithId = blocks.map { it.copy(memoryId = memoryId) }
        insertContentBlocks(blocksWithId)
        return memoryId
    }

    @Transaction
    suspend fun updateMemoryWithBlocks(memory: MemoryEntity, blocks: List<ContentBlockEntity>) {
        updateMemory(memory)
        deleteContentBlocks(memory.id)
        insertContentBlocks(blocks)
    }

    // --- source filtering (#21) -------------------------------------------

    /**
     * Count of Memories per source, for building filter chips with totals.
     *
     * Groups by sourceType and sourcePackage together because "Chrome (12)" and
     * "WhatsApp (8)" are more useful than "SHARE (20)" when the user has shared
     * from multiple apps.
     */
    @Query(
        """
        SELECT sourceType, sourcePackage, COUNT(*) as count
        FROM memories
        GROUP BY sourceType, sourcePackage
        ORDER BY count DESC
        """
    )
    suspend fun getSourceCounts(): List<SourceCount>

    /**
     * Memories from a specific sourceType.
     */
    @Transaction
    @Query("SELECT * FROM memories WHERE sourceType = :sourceType ORDER BY createdAt DESC")
    fun observeMemoriesBySourceType(sourceType: String): Flow<List<MemoryWithBlocks>>

    /**
     * Memories from a specific sourceType AND sourcePackage.
     */
    @Transaction
    @Query(
        """
        SELECT * FROM memories
        WHERE sourceType = :sourceType AND sourcePackage = :sourcePackage
        ORDER BY createdAt DESC
        """
    )
    fun observeMemoriesBySource(sourceType: String, sourcePackage: String): Flow<List<MemoryWithBlocks>>
}

/**
 * A source group with its count, for the filter chips.
 */
data class SourceCount(
    val sourceType: String,
    val sourcePackage: String?,
    val count: Int
)
