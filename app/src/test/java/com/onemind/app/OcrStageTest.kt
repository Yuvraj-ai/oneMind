package com.onemind.app

import com.onemind.app.domain.model.*
import com.onemind.app.domain.processing.StageResult
import com.onemind.app.domain.processing.StageStatus
import com.onemind.app.domain.processing.TextRecognizer
import com.onemind.app.domain.processing.stages.OcrStage
import com.onemind.app.domain.repository.DerivedDataRepository
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Tests the OCR stage through its seam: given a Memory and a recognizer that
 * behaves a certain way, what gets persisted and what does the pipeline see.
 *
 * The recognizer is an interface precisely so this can run on the JVM. The
 * behaviour worth pinning down is the aggregation: how many per-image outcomes
 * collapse into one stage result.
 */
class OcrStageTest {

    private lateinit var textRecognizer: TextRecognizer
    private lateinit var derivedDataRepository: DerivedDataRepository
    private lateinit var stage: OcrStage

    /** Captures what the stage persisted, so the records can be asserted on. */
    private val savedSlot = slot<List<OcrResult>>()

    @Before
    fun setup() {
        textRecognizer = mockk()
        derivedDataRepository = mockk(relaxed = true)
        stage = OcrStage(textRecognizer, derivedDataRepository)
        coEvery { derivedDataRepository.saveOcrResults(capture(savedSlot)) } just Runs
    }

    private fun memoryWith(vararg blocks: ContentBlock) = Memory(
        id = 1L,
        processingState = ProcessingState.PROCESSING,
        contentBlocks = blocks.toList()
    )

    private fun image(id: Long, path: String = "/img/$id.webp") = ContentBlock(
        id = id,
        memoryId = 1L,
        position = id.toInt(),
        type = ContentType.IMAGE,
        content = path
    )

    private fun text(content: String) = ContentBlock(
        id = 99L,
        memoryId = 1L,
        position = 0,
        type = ContentType.TEXT,
        content = content
    )

    // --- the happy path ---------------------------------------------------

    @Test
    fun `an image with text is recorded as SUCCESS with that text`() = runTest {
        coEvery { textRecognizer.recognize(any()) } returns Result.success("AI Summit 2026")

        val result = stage.process(memoryWith(image(1L)))

        assertEquals(StageResult.Success, result)
        val saved = savedSlot.captured.single()
        assertEquals(StageStatus.SUCCESS, saved.status)
        assertEquals("AI Summit 2026", saved.extractedText)
        assertEquals(1L, saved.contentBlockId)
    }

    @Test
    fun `recognized text is trimmed`() = runTest {
        coEvery { textRecognizer.recognize(any()) } returns Result.success("  padded  \n")

        stage.process(memoryWith(image(1L)))

        assertEquals("padded", savedSlot.captured.single().extractedText)
    }

    // --- empty is an answer, not an error ---------------------------------

    @Test
    fun `an image with no text is EMPTY rather than FAILED`() = runTest {
        // A photo of a mountain. The recognizer worked fine; there was no text.
        coEvery { textRecognizer.recognize(any()) } returns Result.success("")

        val result = stage.process(memoryWith(image(1L)))

        assertEquals(StageResult.Empty, result)
        assertEquals(StageStatus.EMPTY, savedSlot.captured.single().status)
    }

    @Test
    fun `whitespace-only recognition counts as EMPTY`() = runTest {
        coEvery { textRecognizer.recognize(any()) } returns Result.success("   \n\t ")

        stage.process(memoryWith(image(1L)))

        assertEquals(StageStatus.EMPTY, savedSlot.captured.single().status)
    }

    // --- failure ----------------------------------------------------------

    @Test
    fun `an undecodable image is recorded as FAILED`() = runTest {
        coEvery { textRecognizer.recognize(any()) } returns
            Result.failure(IllegalArgumentException("could not decode"))

        val result = stage.process(memoryWith(image(1L)))

        assertTrue(result is StageResult.Failed)
        assertEquals(StageStatus.FAILED, savedSlot.captured.single().status)
    }

    @Test
    fun `a failed image records no text rather than a partial value`() = runTest {
        coEvery { textRecognizer.recognize(any()) } returns Result.failure(RuntimeException())

        stage.process(memoryWith(image(1L)))

        assertEquals("", savedSlot.captured.single().extractedText)
    }

    // --- aggregation across many images -----------------------------------

    @Test
    fun `a memory with three images gets three records`() = runTest {
        coEvery { textRecognizer.recognize("/img/1.webp") } returns Result.success("one")
        coEvery { textRecognizer.recognize("/img/2.webp") } returns Result.success("two")
        coEvery { textRecognizer.recognize("/img/3.webp") } returns Result.success("three")

        stage.process(memoryWith(image(1L), image(2L), image(3L)))

        val saved = savedSlot.captured
        assertEquals(3, saved.size)
        assertEquals(setOf(1L, 2L, 3L), saved.map { it.contentBlockId }.toSet())
        assertEquals(setOf("one", "two", "three"), saved.map { it.extractedText }.toSet())
    }

    @Test
    fun `one unreadable image does not hide the text in the others`() = runTest {
        coEvery { textRecognizer.recognize("/img/1.webp") } returns Result.failure(RuntimeException())
        coEvery { textRecognizer.recognize("/img/2.webp") } returns Result.success("readable")

        val result = stage.process(memoryWith(image(1L), image(2L)))

        // Partial success is success: the Memory genuinely gained something.
        assertEquals(StageResult.Success, result)
        val saved = savedSlot.captured
        assertEquals(StageStatus.FAILED, saved.first { it.contentBlockId == 1L }.status)
        assertEquals(StageStatus.SUCCESS, saved.first { it.contentBlockId == 2L }.status)
    }

    @Test
    fun `only a clean sweep of failures fails the stage`() = runTest {
        coEvery { textRecognizer.recognize(any()) } returns Result.failure(RuntimeException())

        val result = stage.process(memoryWith(image(1L), image(2L)))

        assertTrue(result is StageResult.Failed)
        assertTrue((result as StageResult.Failed).reason.contains("2 image"))
    }

    @Test
    fun `all images running but none holding text is Empty, not Failed`() = runTest {
        coEvery { textRecognizer.recognize(any()) } returns Result.success("")

        val result = stage.process(memoryWith(image(1L), image(2L)))

        assertEquals(StageResult.Empty, result)
    }

    @Test
    fun `a mix of empty and failed is Empty, since some images did run clean`() = runTest {
        coEvery { textRecognizer.recognize("/img/1.webp") } returns Result.failure(RuntimeException())
        coEvery { textRecognizer.recognize("/img/2.webp") } returns Result.success("")

        val result = stage.process(memoryWith(image(1L), image(2L)))

        assertEquals(StageResult.Empty, result)
    }

    // --- nothing to do ----------------------------------------------------

    @Test
    fun `a text-only memory is skipped without touching the recognizer`() = runTest {
        val result = stage.process(memoryWith(text("just some notes")))

        assertEquals(StageResult.Skipped, result)
        coVerify(exactly = 0) { textRecognizer.recognize(any()) }
        coVerify(exactly = 0) { derivedDataRepository.saveOcrResults(any()) }
    }

    @Test
    fun `an empty memory is skipped`() = runTest {
        val result = stage.process(memoryWith())

        assertEquals(StageResult.Skipped, result)
        coVerify(exactly = 0) { derivedDataRepository.saveOcrResults(any()) }
    }

    @Test
    fun `only image blocks are read, text blocks alongside them are ignored`() = runTest {
        coEvery { textRecognizer.recognize(any()) } returns Result.success("from the image")

        stage.process(memoryWith(text("typed by the user"), image(1L)))

        // One record, for the image. The typed text is content, not OCR output.
        assertEquals(1, savedSlot.captured.size)
        coVerify(exactly = 1) { textRecognizer.recognize("/img/1.webp") }
    }

    @Test
    fun `the stage declares itself as OCR so the registry orders it first`() {
        assertEquals(com.onemind.app.domain.processing.StageId.OCR, stage.id)
    }
}
