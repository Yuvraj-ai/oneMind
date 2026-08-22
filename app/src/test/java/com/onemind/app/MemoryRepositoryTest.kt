package com.onemind.app

import com.onemind.app.data.local.dao.CategoryDao
import com.onemind.app.data.local.dao.DerivedDataDao
import com.onemind.app.data.local.dao.MemoryDao
import com.onemind.app.data.local.entity.ContentBlockEntity
import com.onemind.app.data.local.entity.MemoryEntity
import com.onemind.app.data.local.entity.MemoryWithBlocks
import com.onemind.app.data.repository.MemoryRepositoryImpl
import com.onemind.app.domain.model.*
import com.onemind.app.domain.repository.InvalidStateTransitionException
import io.mockk.*
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.time.Instant

class MemoryRepositoryTest {

    private lateinit var memoryDao: MemoryDao
    private lateinit var derivedDataDao: DerivedDataDao
    private lateinit var categoryDao: CategoryDao
    private lateinit var repository: MemoryRepositoryImpl

    @Before
    fun setup() {
        memoryDao = mockk(relaxed = true)
        derivedDataDao = mockk(relaxed = true)
        categoryDao = mockk(relaxed = true)
        repository = MemoryRepositoryImpl(memoryDao, derivedDataDao, categoryDao)
    }

    @Test
    fun `createMemory persists and returns generated ID`() = runTest {
        val memory = Memory(
            sourceType = SourceType.MANUAL,
            processingState = ProcessingState.DRAFT,
            contentBlocks = listOf(
                ContentBlock(type = ContentType.TEXT, content = "Hello world")
            )
        )

        coEvery { memoryDao.insertMemoryWithBlocks(any(), any()) } returns 42L

        val id = repository.createMemory(memory)
        assertEquals(42L, id)

        coVerify { memoryDao.insertMemoryWithBlocks(any(), any()) }
    }

    @Test
    fun `getMemoryById returns null for non-existent memory`() = runTest {
        coEvery { memoryDao.getMemoryById(999L) } returns null

        val result = repository.getMemoryById(999L)
        assertNull(result)
    }

    @Test
    fun `getMemoryById returns mapped domain memory`() = runTest {
        val now = Instant.now().toEpochMilli()
        val entity = MemoryWithBlocks(
            memory = MemoryEntity(
                id = 1L,
                createdAt = now,
                updatedAt = now,
                sourceType = SourceType.MANUAL,
                processingState = ProcessingState.SAVED
            ),
            contentBlocks = listOf(
                ContentBlockEntity(
                    id = 1L,
                    memoryId = 1L,
                    position = 0,
                    type = ContentType.TEXT,
                    content = "Test content"
                )
            )
        )

        coEvery { memoryDao.getMemoryById(1L) } returns entity

        val result = repository.getMemoryById(1L)
        assertNotNull(result)
        assertEquals(1L, result!!.id)
        assertEquals(SourceType.MANUAL, result.sourceType)
        assertEquals(ProcessingState.SAVED, result.processingState)
        assertEquals(1, result.contentBlocks.size)
        assertEquals("Test content", result.contentBlocks[0].content)
    }

    @Test
    fun `observeAllMemories returns flow of domain memories`() = runTest {
        val now = Instant.now().toEpochMilli()
        val entities = listOf(
            MemoryWithBlocks(
                memory = MemoryEntity(
                    id = 1L, createdAt = now, updatedAt = now,
                    sourceType = SourceType.MANUAL, processingState = ProcessingState.SAVED
                ),
                contentBlocks = emptyList()
            )
        )

        every { memoryDao.observeAllMemories() } returns flowOf(entities)

        val result = repository.observeAllMemories().first()
        assertEquals(1, result.size)
        assertEquals(1L, result[0].id)
    }

    @Test
    fun `deleteMemory calls dao`() = runTest {
        coEvery { memoryDao.deleteMemory(1L) } just Runs

        repository.deleteMemory(1L)

        coVerify { memoryDao.deleteMemory(1L) }
    }

    @Test
    fun `transitionState with valid transition succeeds`() = runTest {
        val now = Instant.now().toEpochMilli()
        coEvery { memoryDao.getMemoryById(1L) } returns MemoryWithBlocks(
            memory = MemoryEntity(
                id = 1L, createdAt = now, updatedAt = now,
                sourceType = SourceType.MANUAL, processingState = ProcessingState.DRAFT
            ),
            contentBlocks = emptyList()
        )
        coEvery { memoryDao.updateProcessingState(any(), any(), any()) } just Runs

        repository.transitionState(1L, ProcessingState.SAVED)

        coVerify { memoryDao.updateProcessingState(1L, ProcessingState.SAVED, any()) }
    }

    @Test(expected = InvalidStateTransitionException::class)
    fun `transitionState with invalid transition throws`() = runTest {
        val now = Instant.now().toEpochMilli()
        coEvery { memoryDao.getMemoryById(1L) } returns MemoryWithBlocks(
            memory = MemoryEntity(
                id = 1L, createdAt = now, updatedAt = now,
                sourceType = SourceType.MANUAL, processingState = ProcessingState.DRAFT
            ),
            contentBlocks = emptyList()
        )

        repository.transitionState(1L, ProcessingState.READY)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `transitionState for non-existent memory throws`() = runTest {
        coEvery { memoryDao.getMemoryById(999L) } returns null

        repository.transitionState(999L, ProcessingState.SAVED)
    }
}
