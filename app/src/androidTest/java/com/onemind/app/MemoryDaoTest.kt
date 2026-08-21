package com.onemind.app

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.onemind.app.data.local.OneMindDatabase
import com.onemind.app.data.local.dao.MemoryDao
import com.onemind.app.data.local.entity.ContentBlockEntity
import com.onemind.app.data.local.entity.MemoryEntity
import com.onemind.app.domain.model.ContentType
import com.onemind.app.domain.model.ProcessingState
import com.onemind.app.domain.model.SourceType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MemoryDaoTest {

    private lateinit var database: OneMindDatabase
    private lateinit var dao: MemoryDao

    @Before
    fun setup() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            OneMindDatabase::class.java
        ).allowMainThreadQueries().build()
        dao = database.memoryDao()
    }

    @After
    fun teardown() {
        database.close()
    }

    private fun createMemoryEntity(
        id: Long = 0,
        state: ProcessingState = ProcessingState.DRAFT
    ) = MemoryEntity(
        id = id,
        createdAt = System.currentTimeMillis(),
        updatedAt = System.currentTimeMillis(),
        sourceType = SourceType.MANUAL,
        processingState = state
    )

    @Test
    fun insertAndRetrieveMemoryWithBlocks() = runTest {
        val memory = createMemoryEntity()
        val blocks = listOf(
            ContentBlockEntity(
                memoryId = 0, position = 0,
                type = ContentType.TEXT, content = "Hello oneMind"
            ),
            ContentBlockEntity(
                memoryId = 0, position = 1,
                type = ContentType.URL, content = "https://example.com"
            )
        )

        val id = dao.insertMemoryWithBlocks(memory, blocks)
        assertTrue(id > 0)

        val result = dao.getMemoryById(id)
        assertNotNull(result)
        assertEquals(id, result!!.memory.id)
        assertEquals(2, result.contentBlocks.size)
        assertEquals("Hello oneMind", result.contentBlocks.first { it.position == 0 }.content)
        assertEquals("https://example.com", result.contentBlocks.first { it.position == 1 }.content)
    }

    @Test
    fun observeAllMemoriesReturnsFlow() = runTest {
        dao.insertMemoryWithBlocks(createMemoryEntity(), emptyList())
        dao.insertMemoryWithBlocks(createMemoryEntity(), emptyList())

        val memories = dao.observeAllMemories().first()
        assertEquals(2, memories.size)
    }

    @Test
    fun observeAllMemoriesOrderedByCreatedAtDesc() = runTest {
        val older = createMemoryEntity().copy(createdAt = 1000L)
        val newer = createMemoryEntity().copy(createdAt = 2000L)

        dao.insertMemoryWithBlocks(older, emptyList())
        dao.insertMemoryWithBlocks(newer, emptyList())

        val memories = dao.observeAllMemories().first()
        assertTrue(memories[0].memory.createdAt >= memories[1].memory.createdAt)
    }

    @Test
    fun updateProcessingState() = runTest {
        val id = dao.insertMemoryWithBlocks(createMemoryEntity(), emptyList())

        dao.updateProcessingState(id, ProcessingState.SAVED, System.currentTimeMillis())

        val result = dao.getMemoryById(id)
        assertEquals(ProcessingState.SAVED, result!!.memory.processingState)
    }

    @Test
    fun deleteMemoryCascadesContentBlocks() = runTest {
        val blocks = listOf(
            ContentBlockEntity(
                memoryId = 0, position = 0,
                type = ContentType.TEXT, content = "Will be deleted"
            )
        )
        val id = dao.insertMemoryWithBlocks(createMemoryEntity(), blocks)

        dao.deleteMemory(id)

        val result = dao.getMemoryById(id)
        assertNull(result)
    }

    @Test
    fun updateMemoryWithBlocksReplacesBlocks() = runTest {
        val originalBlocks = listOf(
            ContentBlockEntity(
                memoryId = 0, position = 0,
                type = ContentType.TEXT, content = "Original"
            )
        )
        val id = dao.insertMemoryWithBlocks(createMemoryEntity(), originalBlocks)

        val updatedMemory = createMemoryEntity(id = id, state = ProcessingState.SAVED)
        val newBlocks = listOf(
            ContentBlockEntity(
                memoryId = id, position = 0,
                type = ContentType.TEXT, content = "Updated"
            ),
            ContentBlockEntity(
                memoryId = id, position = 1,
                type = ContentType.IMAGE, content = "/path/to/image.webp"
            )
        )

        dao.updateMemoryWithBlocks(updatedMemory, newBlocks)

        val result = dao.getMemoryById(id)
        assertEquals(2, result!!.contentBlocks.size)
        assertEquals("Updated", result.contentBlocks.first { it.position == 0 }.content)
    }

    @Test
    fun getMemoryByIdReturnsNullForNonExistent() = runTest {
        val result = dao.getMemoryById(9999L)
        assertNull(result)
    }
}
