package com.onemind.app.data.repository

import com.onemind.app.data.local.dao.MemoryDao
import com.onemind.app.data.local.entity.EntityMapper.toDomain
import com.onemind.app.data.local.entity.EntityMapper.toEntity
import com.onemind.app.domain.model.Memory
import com.onemind.app.domain.model.ProcessingState
import com.onemind.app.domain.repository.InvalidStateTransitionException
import com.onemind.app.domain.repository.MemoryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MemoryRepositoryImpl @Inject constructor(
    private val memoryDao: MemoryDao
) : MemoryRepository {

    override fun observeAllMemories(): Flow<List<Memory>> {
        return memoryDao.observeAllMemories().map { list ->
            list.map { it.toDomain() }
        }
    }

    override suspend fun getMemoryById(id: Long): Memory? {
        return memoryDao.getMemoryById(id)?.toDomain()
    }

    override suspend fun createMemory(memory: Memory): Long {
        val entity = memory.toEntity()
        val blockEntities = memory.contentBlocks.mapIndexed { index, block ->
            block.toEntity(memoryId = 0).copy(position = index)
        }
        return memoryDao.insertMemoryWithBlocks(entity, blockEntities)
    }

    override suspend fun updateMemory(memory: Memory) {
        val entity = memory.toEntity().copy(updatedAt = Instant.now().toEpochMilli())
        val blockEntities = memory.contentBlocks.mapIndexed { index, block ->
            block.toEntity(memoryId = memory.id).copy(position = index)
        }
        memoryDao.updateMemoryWithBlocks(entity, blockEntities)
    }

    override suspend fun deleteMemory(id: Long) {
        memoryDao.deleteMemory(id)
    }

    override suspend fun transitionState(memoryId: Long, newState: ProcessingState) {
        val memoryWithBlocks = memoryDao.getMemoryById(memoryId)
            ?: throw IllegalArgumentException("Memory not found: $memoryId")

        val currentState = memoryWithBlocks.memory.processingState

        if (!ProcessingState.isValidTransition(currentState, newState)) {
            throw InvalidStateTransitionException(memoryId, currentState, newState)
        }

        memoryDao.updateProcessingState(
            memoryId = memoryId,
            state = newState,
            updatedAt = Instant.now().toEpochMilli()
        )
    }
}
