package com.onemind.app

import com.onemind.app.domain.model.Memory
import com.onemind.app.domain.model.ProcessingState
import com.onemind.app.domain.processing.*
import com.onemind.app.domain.repository.DerivedDataRepository
import com.onemind.app.domain.repository.MemoryRepository
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Tests the Processing Pipeline through its public seam: given a Memory in the
 * repository and a set of registered stages, what happens to the Memory's state
 * and which stages ran.
 */
class ProcessingPipelineTest {

    private lateinit var memoryRepository: MemoryRepository
    private lateinit var derivedDataRepository: DerivedDataRepository

    /** Records the order stages ran in, so ordering can be asserted. */
    private val executionOrder = mutableListOf<StageId>()

    @Before
    fun setup() {
        memoryRepository = mockk(relaxed = true)
        derivedDataRepository = mockk(relaxed = true)
        executionOrder.clear()
    }

    /** A stage that records that it ran and returns a fixed result. */
    private fun stage(
        stageId: StageId,
        result: StageResult = StageResult.Success
    ) = object : ProcessingStage {
        override val id = stageId
        override suspend fun process(memory: Memory): StageResult {
            executionOrder.add(stageId)
            return result
        }
    }

    /** A stage that throws rather than returning a result. */
    private fun throwingStage(stageId: StageId) = object : ProcessingStage {
        override val id = stageId
        override suspend fun process(memory: Memory): StageResult {
            executionOrder.add(stageId)
            throw RuntimeException("boom in $stageId")
        }
    }

    private fun pipelineWith(vararg stages: ProcessingStage) = ProcessingPipeline(
        memoryRepository = memoryRepository,
        derivedDataRepository = derivedDataRepository,
        stages = ProcessingStageRegistry(stages.toSet())
    )

    private fun memoryIn(state: ProcessingState, id: Long = 1L) = Memory(
        id = id,
        processingState = state
    )

    @Test
    fun `runs every registered stage`() = runTest {
        coEvery { memoryRepository.getMemoryById(1L) } returns memoryIn(ProcessingState.SAVED)

        val outcome = pipelineWith(
            stage(StageId.OCR),
            stage(StageId.METADATA),
            stage(StageId.SUMMARIZATION)
        ).run(1L)

        assertTrue(outcome is PipelineOutcome.Completed)
        assertEquals(
            listOf(StageId.OCR, StageId.METADATA, StageId.SUMMARIZATION),
            executionOrder
        )
    }

    @Test
    fun `runs stages in canonical order regardless of registration order`() = runTest {
        coEvery { memoryRepository.getMemoryById(1L) } returns memoryIn(ProcessingState.SAVED)

        // Registered deliberately backwards.
        pipelineWith(
            stage(StageId.SUMMARIZATION),
            stage(StageId.EMBEDDING),
            stage(StageId.OCR)
        ).run(1L)

        assertEquals(
            listOf(StageId.OCR, StageId.EMBEDDING, StageId.SUMMARIZATION),
            executionOrder
        )
    }

    @Test
    fun `transitions SAVED memory through PROCESSING to READY`() = runTest {
        coEvery { memoryRepository.getMemoryById(1L) } returns memoryIn(ProcessingState.SAVED)

        pipelineWith(stage(StageId.OCR)).run(1L)

        coVerifyOrder {
            memoryRepository.transitionState(1L, ProcessingState.PROCESSING)
            memoryRepository.transitionState(1L, ProcessingState.READY)
        }
    }

    @Test
    fun `a failing stage does not abort the stages behind it`() = runTest {
        coEvery { memoryRepository.getMemoryById(1L) } returns memoryIn(ProcessingState.SAVED)

        val outcome = pipelineWith(
            stage(StageId.OCR, StageResult.Failed("no recognizer")),
            stage(StageId.METADATA),
            stage(StageId.SUMMARIZATION)
        ).run(1L)

        assertEquals(
            listOf(StageId.OCR, StageId.METADATA, StageId.SUMMARIZATION),
            executionOrder
        )
        val completed = outcome as PipelineOutcome.Completed
        assertEquals(listOf(StageId.OCR), completed.failedStages)
        assertFalse(completed.allSucceeded)
    }

    @Test
    fun `a failing stage still leaves the memory READY`() = runTest {
        coEvery { memoryRepository.getMemoryById(1L) } returns memoryIn(ProcessingState.SAVED)

        pipelineWith(stage(StageId.OCR, StageResult.Failed("no recognizer"))).run(1L)

        coVerify { memoryRepository.transitionState(1L, ProcessingState.READY) }
        coVerify(exactly = 0) { memoryRepository.transitionState(1L, ProcessingState.FAILED) }
    }

    @Test
    fun `a throwing stage is contained and recorded as failed`() = runTest {
        coEvery { memoryRepository.getMemoryById(1L) } returns memoryIn(ProcessingState.SAVED)

        val outcome = pipelineWith(
            throwingStage(StageId.OCR),
            stage(StageId.METADATA)
        ).run(1L)

        val completed = outcome as PipelineOutcome.Completed
        assertEquals(listOf(StageId.OCR), completed.failedStages)
        assertEquals(listOf(StageId.OCR, StageId.METADATA), executionOrder)
    }

    @Test
    fun `an empty stage set still drives the memory to READY`() = runTest {
        coEvery { memoryRepository.getMemoryById(1L) } returns memoryIn(ProcessingState.SAVED)

        val outcome = pipelineWith().run(1L)

        assertTrue(outcome is PipelineOutcome.Completed)
        assertTrue((outcome as PipelineOutcome.Completed).allSucceeded)
        coVerify { memoryRepository.transitionState(1L, ProcessingState.READY) }
    }

    @Test
    fun `EDITED memory is eligible for reprocessing`() = runTest {
        coEvery { memoryRepository.getMemoryById(1L) } returns memoryIn(ProcessingState.EDITED)

        val outcome = pipelineWith(stage(StageId.OCR)).run(1L)

        assertTrue(outcome is PipelineOutcome.Completed)
        coVerify { memoryRepository.transitionState(1L, ProcessingState.PROCESSING) }
    }

    @Test
    fun `an EDITED memory has its stale derived data cleared before stages run`() = runTest {
        coEvery { memoryRepository.getMemoryById(1L) } returns memoryIn(ProcessingState.EDITED)

        pipelineWith(stage(StageId.OCR)).run(1L)

        coVerifyOrder {
            derivedDataRepository.clearDerivedData(1L)
            memoryRepository.transitionState(1L, ProcessingState.READY)
        }
    }

    @Test
    fun `a freshly SAVED memory has nothing to clear`() = runTest {
        coEvery { memoryRepository.getMemoryById(1L) } returns memoryIn(ProcessingState.SAVED)

        pipelineWith(stage(StageId.OCR)).run(1L)

        coVerify(exactly = 0) { derivedDataRepository.clearDerivedData(any()) }
    }

    @Test
    fun `a retried FAILED memory keeps whatever partial enrichment it has`() = runTest {
        // A retry resumes rather than restarting: stages that already succeeded
        // wrote real data, and re-running them will simply overwrite it.
        coEvery { memoryRepository.getMemoryById(1L) } returns memoryIn(ProcessingState.FAILED)

        pipelineWith(stage(StageId.OCR)).run(1L)

        coVerify(exactly = 0) { derivedDataRepository.clearDerivedData(any()) }
    }

    @Test
    fun `FAILED memory is eligible so the user can retry`() = runTest {
        coEvery { memoryRepository.getMemoryById(1L) } returns memoryIn(ProcessingState.FAILED)

        val outcome = pipelineWith(stage(StageId.OCR)).run(1L)

        assertTrue(outcome is PipelineOutcome.Completed)
        coVerify { memoryRepository.transitionState(1L, ProcessingState.PROCESSING) }
    }

    @Test
    fun `a memory already PROCESSING is not picked up twice`() = runTest {
        coEvery { memoryRepository.getMemoryById(1L) } returns memoryIn(ProcessingState.PROCESSING)

        val outcome = pipelineWith(stage(StageId.OCR)).run(1L)

        assertTrue(outcome is PipelineOutcome.NotEligible)
        assertTrue(executionOrder.isEmpty())
        coVerify(exactly = 0) { memoryRepository.transitionState(any(), any()) }
    }

    @Test
    fun `a READY memory is not reprocessed without an edit`() = runTest {
        coEvery { memoryRepository.getMemoryById(1L) } returns memoryIn(ProcessingState.READY)

        val outcome = pipelineWith(stage(StageId.OCR)).run(1L)

        assertTrue(outcome is PipelineOutcome.NotEligible)
        assertEquals(ProcessingState.READY, (outcome as PipelineOutcome.NotEligible).state)
    }

    @Test
    fun `a DRAFT memory is not processed`() = runTest {
        coEvery { memoryRepository.getMemoryById(1L) } returns memoryIn(ProcessingState.DRAFT)

        val outcome = pipelineWith(stage(StageId.OCR)).run(1L)

        assertTrue(outcome is PipelineOutcome.NotEligible)
        assertTrue(executionOrder.isEmpty())
    }

    @Test
    fun `a memory deleted before the run starts is handled`() = runTest {
        coEvery { memoryRepository.getMemoryById(1L) } returns null

        val outcome = pipelineWith(stage(StageId.OCR)).run(1L)

        assertEquals(PipelineOutcome.MemoryGone, outcome)
        coVerify(exactly = 0) { memoryRepository.transitionState(any(), any()) }
    }

    @Test
    fun `a memory deleted mid-run is handled`() = runTest {
        // Eligible on entry, gone by the time the first stage asks for it again.
        coEvery { memoryRepository.getMemoryById(1L) } returnsMany listOf(
            memoryIn(ProcessingState.SAVED),
            null
        )

        val outcome = pipelineWith(stage(StageId.OCR)).run(1L)

        assertEquals(PipelineOutcome.MemoryGone, outcome)
        coVerify(exactly = 0) { memoryRepository.transitionState(1L, ProcessingState.READY) }
    }

    @Test
    fun `markFailed moves a PROCESSING memory to FAILED`() = runTest {
        coEvery { memoryRepository.getMemoryById(1L) } returns memoryIn(ProcessingState.PROCESSING)

        pipelineWith().markFailed(1L)

        coVerify { memoryRepository.transitionState(1L, ProcessingState.FAILED) }
    }

    @Test
    fun `markFailed leaves a memory that is not PROCESSING alone`() = runTest {
        coEvery { memoryRepository.getMemoryById(1L) } returns memoryIn(ProcessingState.READY)

        pipelineWith().markFailed(1L)

        coVerify(exactly = 0) { memoryRepository.transitionState(any(), any()) }
    }
}
