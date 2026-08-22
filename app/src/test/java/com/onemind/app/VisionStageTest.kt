package com.onemind.app

import com.onemind.app.domain.model.*
import com.onemind.app.domain.processing.ImageDescriber
import com.onemind.app.domain.processing.StageId
import com.onemind.app.domain.processing.StageResult
import com.onemind.app.domain.processing.StageStatus
import com.onemind.app.domain.processing.stages.VisionStage
import com.onemind.app.domain.repository.DerivedDataRepository
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Tests the vision stage through its seam.
 *
 * The behaviour that matters most here is what happens when vision *is not*
 * available, since that is the common case for a user running a small local
 * model, and getting it wrong would either report a false failure or leak an
 * image to a provider they did not choose.
 */
class VisionStageTest {

    private lateinit var imageDescriber: ImageDescriber
    private lateinit var derivedDataRepository: DerivedDataRepository
    private lateinit var stage: VisionStage

    private val savedSlot = slot<List<VisionResult>>()

    @Before
    fun setup() {
        imageDescriber = mockk()
        derivedDataRepository = mockk(relaxed = true)
        stage = VisionStage(imageDescriber, derivedDataRepository)
        coEvery { derivedDataRepository.saveVisionResults(capture(savedSlot)) } just Runs
    }

    private fun memoryWith(vararg blocks: ContentBlock) = Memory(
        id = 1L,
        processingState = ProcessingState.PROCESSING,
        contentBlocks = blocks.toList()
    )

    private fun image(id: Long) = ContentBlock(
        id = id,
        memoryId = 1L,
        position = id.toInt(),
        type = ContentType.IMAGE,
        content = "/img/$id.webp"
    )

    private fun text(content: String) = ContentBlock(
        id = 99L, memoryId = 1L, position = 0,
        type = ContentType.TEXT, content = content
    )

    private fun visionAvailable(model: String = "Gemma 3 4B") {
        every { imageDescriber.isAvailable() } returns true
        every { imageDescriber.modelIdentifier() } returns model
    }

    private fun visionUnavailable() {
        every { imageDescriber.isAvailable() } returns false
        every { imageDescriber.modelIdentifier() } returns null
    }

    // --- capability awareness --------------------------------------------

    @Test
    fun `a text-only model yields NOT_SUPPORTED rather than a failure`() = runTest {
        visionUnavailable()

        val result = stage.process(memoryWith(image(1L)))

        assertEquals(StageResult.NotSupported, result)
        assertEquals(StageStatus.NOT_SUPPORTED, savedSlot.captured.single().status)
    }

    @Test
    fun `no image is described when vision is unavailable`() = runTest {
        visionUnavailable()

        stage.process(memoryWith(image(1L), image(2L)))

        // The guarantee that matters: nothing is sent anywhere the user did not
        // configure. Availability is checked before any image is touched.
        coVerify(exactly = 0) { imageDescriber.describe(any(), any()) }
    }

    @Test
    fun `unavailability is recorded for every image, so the UI can explain itself`() = runTest {
        visionUnavailable()

        stage.process(memoryWith(image(1L), image(2L), image(3L)))

        val saved = savedSlot.captured
        assertEquals(3, saved.size)
        assertTrue(saved.all { it.status == StageStatus.NOT_SUPPORTED })
        assertTrue(saved.all { it.description.isEmpty() })
    }

    @Test
    fun `no model is attributed when vision was unavailable`() = runTest {
        visionUnavailable()

        stage.process(memoryWith(image(1L)))

        assertNull(savedSlot.captured.single().providerModel)
    }

    // --- the happy path ---------------------------------------------------

    @Test
    fun `a vision-capable model produces a description`() = runTest {
        visionAvailable()
        coEvery { imageDescriber.describe(any(), any()) } returns
            Result.success("A snow-covered mountain range under a cloudy sky.")

        val result = stage.process(memoryWith(image(1L)))

        assertEquals(StageResult.Success, result)
        val saved = savedSlot.captured.single()
        assertEquals(StageStatus.SUCCESS, saved.status)
        assertEquals("A snow-covered mountain range under a cloudy sky.", saved.description)
    }

    @Test
    fun `the producing model is recorded for provenance`() = runTest {
        visionAvailable(model = "Gemma 3 4B")
        coEvery { imageDescriber.describe(any(), any()) } returns Result.success("A chart.")

        stage.process(memoryWith(image(1L)))

        assertEquals("Gemma 3 4B", savedSlot.captured.single().providerModel)
    }

    @Test
    fun `the prompt asks for facts and forbids speculation`() = runTest {
        visionAvailable()
        val promptSlot = slot<String>()
        coEvery { imageDescriber.describe(any(), capture(promptSlot)) } returns Result.success("x")

        stage.process(memoryWith(image(1L)))

        val prompt = promptSlot.captured.lowercase()
        assertTrue("should ask for factual content", prompt.contains("factual"))
        assertTrue("should rule out speculation", prompt.contains("not speculate"))
    }

    @Test
    fun `descriptions are trimmed`() = runTest {
        visionAvailable()
        coEvery { imageDescriber.describe(any(), any()) } returns Result.success("  a photo \n")

        stage.process(memoryWith(image(1L)))

        assertEquals("a photo", savedSlot.captured.single().description)
    }

    // --- failure and emptiness -------------------------------------------

    @Test
    fun `a provider error is FAILED, distinct from unsupported`() = runTest {
        visionAvailable()
        coEvery { imageDescriber.describe(any(), any()) } returns
            Result.failure(RuntimeException("rate limited"))

        val result = stage.process(memoryWith(image(1L)))

        assertTrue(result is StageResult.Failed)
        assertEquals(StageStatus.FAILED, savedSlot.captured.single().status)
    }

    @Test
    fun `a blank description is EMPTY rather than SUCCESS`() = runTest {
        visionAvailable()
        coEvery { imageDescriber.describe(any(), any()) } returns Result.success("   ")

        val result = stage.process(memoryWith(image(1L)))

        assertEquals(StageResult.Empty, result)
        assertEquals(StageStatus.EMPTY, savedSlot.captured.single().status)
    }

    @Test
    fun `a failed image attributes no model`() = runTest {
        visionAvailable()
        coEvery { imageDescriber.describe(any(), any()) } returns Result.failure(RuntimeException())

        stage.process(memoryWith(image(1L)))

        assertNull(savedSlot.captured.single().providerModel)
    }

    // --- aggregation ------------------------------------------------------

    @Test
    fun `a memory with three images gets three records`() = runTest {
        visionAvailable()
        coEvery { imageDescriber.describe("/img/1.webp", any()) } returns Result.success("one")
        coEvery { imageDescriber.describe("/img/2.webp", any()) } returns Result.success("two")
        coEvery { imageDescriber.describe("/img/3.webp", any()) } returns Result.success("three")

        stage.process(memoryWith(image(1L), image(2L), image(3L)))

        assertEquals(3, savedSlot.captured.size)
        assertEquals(setOf(1L, 2L, 3L), savedSlot.captured.map { it.contentBlockId }.toSet())
    }

    @Test
    fun `one failed image does not discard the descriptions of the others`() = runTest {
        visionAvailable()
        coEvery { imageDescriber.describe("/img/1.webp", any()) } returns Result.failure(RuntimeException())
        coEvery { imageDescriber.describe("/img/2.webp", any()) } returns Result.success("a diagram")

        val result = stage.process(memoryWith(image(1L), image(2L)))

        assertEquals(StageResult.Success, result)
        val saved = savedSlot.captured
        assertEquals(StageStatus.FAILED, saved.first { it.contentBlockId == 1L }.status)
        assertEquals(StageStatus.SUCCESS, saved.first { it.contentBlockId == 2L }.status)
    }

    @Test
    fun `only a clean sweep of failures fails the stage`() = runTest {
        visionAvailable()
        coEvery { imageDescriber.describe(any(), any()) } returns Result.failure(RuntimeException())

        val result = stage.process(memoryWith(image(1L), image(2L)))

        assertTrue(result is StageResult.Failed)
        assertTrue((result as StageResult.Failed).reason.contains("2 image"))
    }

    // --- nothing to do ----------------------------------------------------

    @Test
    fun `a text-only memory is skipped before capability is even checked`() = runTest {
        val result = stage.process(memoryWith(text("just notes")))

        assertEquals(StageResult.Skipped, result)
        verify(exactly = 0) { imageDescriber.isAvailable() }
        coVerify(exactly = 0) { derivedDataRepository.saveVisionResults(any()) }
    }

    @Test
    fun `an empty memory is skipped`() = runTest {
        assertEquals(StageResult.Skipped, stage.process(memoryWith()))
    }

    @Test
    fun `text blocks alongside images are not described`() = runTest {
        visionAvailable()
        coEvery { imageDescriber.describe(any(), any()) } returns Result.success("a photo")

        stage.process(memoryWith(text("typed by the user"), image(1L)))

        assertEquals(1, savedSlot.captured.size)
        coVerify(exactly = 1) { imageDescriber.describe("/img/1.webp", any()) }
    }

    @Test
    fun `the stage declares itself as VISION so it runs after OCR`() {
        assertEquals(StageId.VISION, stage.id)
    }
}
