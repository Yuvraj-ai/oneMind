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
}
