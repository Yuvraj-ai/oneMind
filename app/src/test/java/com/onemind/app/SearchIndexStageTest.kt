package com.onemind.app

import com.onemind.app.domain.model.*
import com.onemind.app.domain.processing.StageId
import com.onemind.app.domain.processing.StageResult
import com.onemind.app.domain.processing.StageStatus
import com.onemind.app.domain.processing.stages.SearchIndexStage
import com.onemind.app.domain.repository.SearchIndexRepository
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class SearchIndexStageTest {

    private lateinit var repository: SearchIndexRepository
    private lateinit var stage: SearchIndexStage

    private val documentSlot = slot<String>()

    @Before
    fun setup() {
        repository = mockk(relaxed = true)
        stage = SearchIndexStage(repository)
        coEvery { repository.index(any(), capture(documentSlot)) } just Runs
    }

    private fun memory(
        text: String? = "Research Qwen models",
        derived: DerivedData = DerivedData.EMPTY
    ) = Memory(
        id = 1L,
        contentBlocks = text?.let {
            listOf(ContentBlock(id = 1L, memoryId = 1L, position = 0, type = ContentType.TEXT, content = it))
        } ?: emptyList(),
        derived = derived
    )

    @Test
    fun `a memory with text is indexed`() = runTest {
        val result = stage.process(memory())

        assertEquals(StageResult.Success, result)
        coVerify { repository.index(1L, any()) }
        assertTrue(documentSlot.captured.contains("Research Qwen models"))
    }

    @Test
    fun `the document includes derived text, not just what the user typed`() = runTest {
        stage.process(
            memory(
                text = "typed",
                derived = DerivedData(
                    summary = MemorySummary(memoryId = 1L, summaryText = "summarised", status = StageStatus.SUCCESS)
                )
            )
        )

        assertTrue(documentSlot.captured.contains("typed"))
        assertTrue(documentSlot.captured.contains("summarised"))
    }

    @Test
    fun `a memory with nothing to index writes no row`() = runTest {
        val result = stage.process(memory(text = null))

        assertEquals(StageResult.Empty, result)
        coVerify(exactly = 0) { repository.index(any(), any()) }
    }

    @Test
    fun `a memory with nothing to index has any stale row removed`() = runTest {
        // Otherwise a Memory edited down to nothing would stay findable by the
        // content it no longer has.
        stage.process(memory(text = null))

        coVerify { repository.remove(1L) }
    }

    @Test
    fun `indexing never reports NOT_SUPPORTED`() = runTest {
        // It needs no AI provider. Search must keep working on a device with no
        // model configured at all.
        val result = stage.process(memory())

        assertNotEquals(StageResult.NotSupported, result)
    }

    @Test
    fun `the stage declares itself as INDEXING`() {
        assertEquals(StageId.INDEXING, stage.id)
    }
}
