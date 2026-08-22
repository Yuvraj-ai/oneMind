package com.onemind.app

import com.onemind.app.domain.model.*
import com.onemind.app.domain.processing.BoundedAnalysisInput
import com.onemind.app.domain.processing.StageId
import com.onemind.app.domain.processing.StageResult
import com.onemind.app.domain.processing.StageStatus
import com.onemind.app.domain.processing.TextGenerator
import com.onemind.app.domain.processing.stages.MetadataExtractionStage
import com.onemind.app.domain.repository.DerivedDataRepository
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.time.Instant

/**
 * The stage that ties deterministic and model-driven extraction together.
 *
 * The behaviour worth pinning down is how those two halves fail independently: a
 * user with no provider should still get their links, and a model that errors
 * should not discard links already found.
 */
class MetadataExtractionStageTest {

    private lateinit var textGenerator: TextGenerator
    private lateinit var derivedDataRepository: DerivedDataRepository
    private lateinit var stage: MetadataExtractionStage

    private val urlSlot = slot<List<ExtractedUrl>>()
    private val entitySlot = slot<List<ExtractedEntity>>()
    private val dateSlot = slot<List<ExtractedDate>>()
    private val promptSlot = slot<String>()

    @Before
    fun setup() {
        textGenerator = mockk()
        derivedDataRepository = mockk(relaxed = true)
        stage = MetadataExtractionStage(textGenerator, derivedDataRepository)

        every { textGenerator.modelIdentifier() } returns "gpt-4o-mini"
        coEvery { derivedDataRepository.saveUrls(capture(urlSlot)) } just Runs
        coEvery { derivedDataRepository.saveEntities(capture(entitySlot)) } just Runs
        coEvery { derivedDataRepository.saveDates(capture(dateSlot)) } just Runs
    }

    private fun modelAvailable(response: String) {
        every { textGenerator.isAvailable() } returns true
        coEvery { textGenerator.generate(capture(promptSlot), any()) } returns Result.success(response)
    }

    private fun modelUnavailable() {
        every { textGenerator.isAvailable() } returns false
    }

    private fun memory(
        text: String? = null,
        ocr: String? = null,
        vision: String? = null
    ): Memory {
        val blocks = buildList {
            if (text != null) add(
                ContentBlock(
                    id = 1L, memoryId = 1L, position = 0,
                    type = ContentType.TEXT, content = text
                )
            )
            if (ocr != null || vision != null) add(
                ContentBlock(
                    id = 2L, memoryId = 1L, position = 1,
                    type = ContentType.IMAGE, content = "/img/2.webp"
                )
            )
        }

        return Memory(
            id = 1L,
            processingState = ProcessingState.PROCESSING,
            contentBlocks = blocks,
            derived = DerivedData(
                ocrResults = ocr?.let {
                    listOf(
                        OcrResult(
                            memoryId = 1L, contentBlockId = 2L,
                            status = StageStatus.SUCCESS, extractedText = it,
                            processedAt = Instant.EPOCH
                        )
                    )
                } ?: emptyList(),
                visionResults = vision?.let {
                    listOf(
                        VisionResult(
                            memoryId = 1L, contentBlockId = 2L,
                            status = StageStatus.SUCCESS, description = it,
                            providerModel = "m", processedAt = Instant.EPOCH
                        )
                    )
                } ?: emptyList()
            )
        )
    }

    private val emptyResponse = """{"entities":[],"dates":[]}"""

    // --- links work without a model ---------------------------------------

    @Test
    fun `links are extracted with no provider configured`() = runTest {
        // The local-first win: a Memory full of links is still usefully enriched
        // for a user who trusts nobody.
        modelUnavailable()

        val result = stage.process(memory(text = "see https://github.com/example/project"))

        assertEquals(StageResult.Success, result)
        assertEquals("github.com", urlSlot.captured.single().domain)
    }

    @Test
    fun `with no provider and no links the stage reports NOT_SUPPORTED`() = runTest {
        modelUnavailable()

        val result = stage.process(memory(text = "just some notes"))

        assertEquals(StageResult.NotSupported, result)
        coVerify(exactly = 0) { derivedDataRepository.saveUrls(any()) }
    }

    @Test
    fun `with no provider the model is never called`() = runTest {
        modelUnavailable()

        stage.process(memory(text = "https://a.com"))

        coVerify(exactly = 0) { textGenerator.generate(any(), any()) }
    }

    @Test
    fun `links are found in OCR text too`() = runTest {
        modelUnavailable()

        stage.process(memory(ocr = "visit https://example.com/page for more"))

        assertEquals("example.com", urlSlot.captured.single().domain)
    }

    // --- the model half ---------------------------------------------------

    @Test
    fun `entities from the model are saved with their type`() = runTest {
        modelAvailable(
            """{"entities":[{"name":"Google","type":"ORGANIZATION"}],"dates":[]}"""
        )

        val result = stage.process(memory(text = "Google announced something"))

        assertEquals(StageResult.Success, result)
        val entity = entitySlot.captured.single()
        assertEquals("Google", entity.name)
        assertEquals(EntityType.ORGANIZATION, entity.entityType)
    }

    @Test
    fun `dates from the model are saved as event times, not capture times`() = runTest {
        // Conflating the two is what makes temporal retrieval wrong: a screenshot
        // taken today can describe an event next month.
        modelAvailable(
            """{"entities":[],"dates":[{"text":"September 15","iso8601":"2026-09-15"}]}"""
        )

        stage.process(memory(text = "launch is September 15"))

        val date = dateSlot.captured.single()
        assertTrue(date.isEventTime)
        assertEquals("September 15", date.rawText)
        assertEquals(Instant.parse("2026-09-15T00:00:00Z"), date.parsedInstant)
    }

    @Test
    fun `confidence is stored only when the model supplied it`() = runTest {
        modelAvailable(
            """{"entities":[
                 {"name":"Google","type":"ORGANIZATION","confidence":0.9},
                 {"name":"OpenAI","type":"ORGANIZATION"}
               ],"dates":[]}"""
        )

        stage.process(memory(text = "Google and OpenAI"))

        val byName = entitySlot.captured.associateBy { it.name }
        assertEquals(0.9f, byName.getValue("Google").confidence!!, 0.001f)
        assertNull(byName.getValue("OpenAI").confidence)
    }

    @Test
    fun `nothing is saved when the model finds nothing`() = runTest {
        modelAvailable(emptyResponse)

        val result = stage.process(memory(text = "just some notes"))

        assertEquals(StageResult.Empty, result)
        coVerify(exactly = 0) { derivedDataRepository.saveEntities(any()) }
        coVerify(exactly = 0) { derivedDataRepository.saveDates(any()) }
    }

    // --- provenance -------------------------------------------------------

    @Test
    fun `an entity found only in OCR text is attributed to OCR`() = runTest {
        modelAvailable("""{"entities":[{"name":"Qwen3","type":"PRODUCT"}],"dates":[]}""")

        stage.process(memory(text = "look at this", ocr = "Qwen3-30B-A3B"))

        assertEquals(DerivedSource.OCR, entitySlot.captured.single().source)
    }

    @Test
    fun `an entity found only in a description is attributed to vision`() = runTest {
        modelAvailable("""{"entities":[{"name":"mountain","type":"PLACE"}],"dates":[]}""")

        stage.process(memory(text = "nice", vision = "A snow-covered mountain range."))

        assertEquals(DerivedSource.VISION, entitySlot.captured.single().source)
    }

    @Test
    fun `an entity in the user's own text is attributed to user text`() = runTest {
        modelAvailable("""{"entities":[{"name":"Bangalore","type":"PLACE"}],"dates":[]}""")

        stage.process(memory(text = "meeting in Bangalore", ocr = "unrelated"))

        assertEquals(DerivedSource.USER_TEXT, entitySlot.captured.single().source)
    }

    @Test
    fun `an entity the model invented falls back to user text rather than being dropped`() = runTest {
        // Attribution is best-effort; a name that appears in no source still gets
        // stored, because discarding it would lose a possibly-real finding.
        modelAvailable("""{"entities":[{"name":"Nowhere","type":"OTHER"}],"dates":[]}""")

        stage.process(memory(text = "something else entirely"))

        assertEquals(DerivedSource.USER_TEXT, entitySlot.captured.single().source)
    }

    // --- the two halves fail independently --------------------------------

    @Test
    fun `a model error does not discard links already found`() = runTest {
        every { textGenerator.isAvailable() } returns true
        coEvery { textGenerator.generate(any(), any()) } returns
            Result.failure(RuntimeException("rate limited"))

        val result = stage.process(memory(text = "https://a.com and notes"))

        assertEquals(StageResult.Success, result)
        assertEquals(1, urlSlot.captured.size)
    }

    @Test
    fun `a model error with no links to fall back on fails the stage`() = runTest {
        every { textGenerator.isAvailable() } returns true
        coEvery { textGenerator.generate(any(), any()) } returns
            Result.failure(RuntimeException("rate limited"))

        val result = stage.process(memory(text = "just notes"))

        assertTrue(result is StageResult.Failed)
    }

    @Test
    fun `unparseable model output does not crash the stage`() = runTest {
        modelAvailable("I'm sorry, I can't help with that.")

        val result = stage.process(memory(text = "just notes"))

        assertTrue(result is StageResult.Failed)
        coVerify(exactly = 0) { derivedDataRepository.saveEntities(any()) }
    }

    @Test
    fun `unparseable model output still leaves links intact`() = runTest {
        modelAvailable("total nonsense, no json here")

        val result = stage.process(memory(text = "https://a.com"))

        assertEquals(StageResult.Success, result)
        assertEquals(1, urlSlot.captured.size)
    }

    // --- the prompt -------------------------------------------------------

    @Test
    fun `the prompt forbids inventing metadata`() = runTest {
        modelAvailable(emptyResponse)

        stage.process(memory(text = "something"))

        val prompt = promptSlot.captured.lowercase()
        assertTrue(prompt.contains("do not infer or invent"))
        assertTrue("should name the allowed types", prompt.contains("organization"))
    }

    @Test
    fun `the prompt tells the model to omit confidence rather than guess`() = runTest {
        modelAvailable(emptyResponse)

        stage.process(memory(text = "something"))

        assertTrue(promptSlot.captured.lowercase().contains("omit \"confidence\""))
    }

    @Test
    fun `very long content is capped before reaching the model`() = runTest {
        modelAvailable(emptyResponse)
        val long = "word ".repeat(5_000)

        stage.process(memory(text = long))

        // Shares the cap with every other model call rather than keeping a private
        // one that could drift out of step.
        assertTrue(
            "prompt was ${promptSlot.captured.length} chars",
            promptSlot.captured.length < BoundedAnalysisInput.MAX_TEXT_CHARS + 1_000
        )
    }

    @Test
    fun `all three sources are given to the model together`() = runTest {
        modelAvailable(emptyResponse)

        stage.process(
            memory(text = "typed words", ocr = "scanned words", vision = "described scene")
        )

        val prompt = promptSlot.captured
        assertTrue(prompt.contains("typed words"))
        assertTrue(prompt.contains("scanned words"))
        assertTrue(prompt.contains("described scene"))
    }

    // --- nothing to do ----------------------------------------------------

    @Test
    fun `a memory with no text is skipped without calling the model`() = runTest {
        val result = stage.process(
            Memory(id = 1L, processingState = ProcessingState.PROCESSING)
        )

        assertEquals(StageResult.Skipped, result)
        coVerify(exactly = 0) { textGenerator.generate(any(), any()) }
        verify(exactly = 0) { textGenerator.isAvailable() }
    }

    @Test
    fun `the stage declares itself as METADATA so it runs after vision`() {
        assertEquals(StageId.METADATA, stage.id)
    }
}
