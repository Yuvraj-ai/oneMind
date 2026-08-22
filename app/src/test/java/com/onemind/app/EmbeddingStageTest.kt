package com.onemind.app

import com.onemind.app.domain.model.*
import com.onemind.app.domain.processing.EmbeddingGenerator
import com.onemind.app.domain.processing.StageId
import com.onemind.app.domain.processing.StageResult
import com.onemind.app.domain.processing.StageStatus
import com.onemind.app.domain.processing.stages.EmbeddingStage
import com.onemind.app.domain.repository.DerivedDataRepository
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.time.Instant

/**
 * Tests the embedding stage through its seam.
 *
 * The generator's own behaviour is covered on-device by
 * `EmbeddingGeneratorTest`; what matters here is what text the stage chooses to
 * embed, and what it records.
 */
class EmbeddingStageTest {

    private lateinit var generator: EmbeddingGenerator
    private lateinit var derivedDataRepository: DerivedDataRepository
    private lateinit var stage: EmbeddingStage

    private val savedSlot = slot<MemoryEmbedding>()
    private val embeddedTextSlot = slot<String>()

    @Before
    fun setup() {
        generator = mockk()
        derivedDataRepository = mockk(relaxed = true)
        stage = EmbeddingStage(generator, derivedDataRepository)

        every { generator.modelId } returns "universal-sentence-encoder"
        coEvery { derivedDataRepository.saveEmbedding(capture(savedSlot)) } just Runs
    }

    private fun generatorReturns(vector: FloatArray) {
        coEvery { generator.embed(capture(embeddedTextSlot)) } returns Result.success(vector)
    }

    private fun memory(
        blocks: List<ContentBlock> = emptyList(),
        derived: DerivedData = DerivedData.EMPTY
    ) = Memory(
        id = 1L,
        processingState = ProcessingState.PROCESSING,
        contentBlocks = blocks,
        derived = derived
    )

    private fun textBlock(content: String, id: Long = 1L) = ContentBlock(
        id = id, memoryId = 1L, position = id.toInt(),
        type = ContentType.TEXT, content = content
    )

    private fun imageBlock(id: Long = 2L) = ContentBlock(
        id = id, memoryId = 1L, position = id.toInt(),
        type = ContentType.IMAGE, content = "/img/$id.webp"
    )

    private fun ocr(text: String, status: StageStatus = StageStatus.SUCCESS) = OcrResult(
        memoryId = 1L, contentBlockId = 2L, status = status,
        extractedText = text, processedAt = Instant.EPOCH
    )

    private fun vision(text: String, status: StageStatus = StageStatus.SUCCESS) = VisionResult(
        memoryId = 1L, contentBlockId = 2L, status = status,
        description = text, providerModel = "m1", processedAt = Instant.EPOCH
    )

    // --- the happy path ---------------------------------------------------

    @Test
    fun `text is embedded and the vector saved`() = runTest {
        generatorReturns(FloatArray(100) { 0.1f })

        val result = stage.process(memory(listOf(textBlock("Research Qwen models"))))

        assertEquals(StageResult.Success, result)
        assertEquals(1L, savedSlot.captured.memoryId)
        assertEquals(100, savedSlot.captured.vector.size)
    }

    @Test
    fun `dimensionality is taken from the produced vector, not a constant`() = runTest {
        // A model swap that changes the width must show up in the data rather
        // than being silently mislabelled.
        generatorReturns(FloatArray(384) { 0.5f })

        stage.process(memory(listOf(textBlock("anything"))))

        assertEquals(384, savedSlot.captured.dimensions)
    }

    @Test
    fun `the generator's model id is recorded so vectors can be invalidated later`() = runTest {
        generatorReturns(FloatArray(100))

        stage.process(memory(listOf(textBlock("anything"))))

        assertEquals("universal-sentence-encoder", savedSlot.captured.modelId)
    }

    // --- what actually gets embedded --------------------------------------

    @Test
    fun `user text is embedded`() = runTest {
        generatorReturns(FloatArray(100))

        stage.process(memory(listOf(textBlock("Deploying LLMs on Android"))))

        assertEquals("Deploying LLMs on Android", embeddedTextSlot.captured)
    }

    @Test
    fun `OCR text is embedded even with no user text`() = runTest {
        generatorReturns(FloatArray(100))

        stage.process(
            memory(
                blocks = listOf(imageBlock()),
                derived = DerivedData(ocrResults = listOf(ocr("AI Summit 2026")))
            )
        )

        assertTrue(embeddedTextSlot.captured.contains("AI Summit 2026"))
    }

    @Test
    fun `a photo with no text is still embeddable through its description`() = runTest {
        // The case that makes a picture of a mountain findable at all.
        generatorReturns(FloatArray(100))

        stage.process(
            memory(
                blocks = listOf(imageBlock()),
                derived = DerivedData(
                    ocrResults = listOf(ocr("", StageStatus.EMPTY)),
                    visionResults = listOf(vision("A snow-covered mountain range."))
                )
            )
        )

        assertTrue(embeddedTextSlot.captured.contains("mountain"))
    }

    @Test
    fun `user text, OCR and description are all embedded together`() = runTest {
        generatorReturns(FloatArray(100))

        stage.process(
            memory(
                blocks = listOf(textBlock("look at this"), imageBlock()),
                derived = DerivedData(
                    ocrResults = listOf(ocr("Qwen3-30B")),
                    visionResults = listOf(vision("A screenshot of a model card."))
                )
            )
        )

        val embedded = embeddedTextSlot.captured
        assertTrue(embedded.contains("look at this"))
        assertTrue(embedded.contains("Qwen3-30B"))
        assertTrue(embedded.contains("model card"))
    }

    @Test
    fun `failed OCR contributes nothing rather than an empty line`() = runTest {
        generatorReturns(FloatArray(100))

        stage.process(
            memory(
                blocks = listOf(textBlock("only this")),
                derived = DerivedData(ocrResults = listOf(ocr("", StageStatus.FAILED)))
            )
        )

        assertEquals("only this", embeddedTextSlot.captured)
    }

    @Test
    fun `the same memory produces the same input text every time`() = runTest {
        // Order is fixed so re-processing an unchanged Memory yields the same
        // vector, keeping the index stable.
        generatorReturns(FloatArray(100))
        val m = memory(
            blocks = listOf(textBlock("first"), imageBlock()),
            derived = DerivedData(
                ocrResults = listOf(ocr("second")),
                visionResults = listOf(vision("third"))
            )
        )

        stage.process(m)
        val once = embeddedTextSlot.captured
        stage.process(m)
        val twice = embeddedTextSlot.captured

        assertEquals(once, twice)
    }

    // --- nothing to embed -------------------------------------------------

    @Test
    fun `a memory with no text at all is Empty, not Failed`() = runTest {
        val result = stage.process(memory(listOf(imageBlock())))

        assertEquals(StageResult.Empty, result)
        coVerify(exactly = 0) { generator.embed(any()) }
        coVerify(exactly = 0) { derivedDataRepository.saveEmbedding(any()) }
    }

    @Test
    fun `whitespace-only text is treated as nothing to embed`() = runTest {
        val result = stage.process(memory(listOf(textBlock("   \n\t "))))

        assertEquals(StageResult.Empty, result)
        coVerify(exactly = 0) { generator.embed(any()) }
    }

    @Test
    fun `an empty memory is Empty`() = runTest {
        assertEquals(StageResult.Empty, stage.process(memory()))
    }

    // --- failure ----------------------------------------------------------

    @Test
    fun `generator failure fails the stage without saving anything`() = runTest {
        coEvery { generator.embed(any()) } returns
            Result.failure(IllegalStateException("model not loaded"))

        val result = stage.process(memory(listOf(textBlock("something"))))

        assertTrue(result is StageResult.Failed)
        assertTrue((result as StageResult.Failed).reason.contains("model not loaded"))
        coVerify(exactly = 0) { derivedDataRepository.saveEmbedding(any()) }
    }

    @Test
    fun `the stage declares itself as EMBEDDING so it runs after vision`() {
        assertEquals(StageId.EMBEDDING, stage.id)
    }
}
