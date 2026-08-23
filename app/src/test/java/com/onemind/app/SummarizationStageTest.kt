package com.onemind.app

import com.onemind.app.domain.model.*
import com.onemind.app.domain.processing.StageId
import com.onemind.app.domain.processing.StageResult
import com.onemind.app.domain.processing.StageStatus
import com.onemind.app.domain.processing.TextGenerator
import com.onemind.app.domain.processing.stages.SummarizationStage
import com.onemind.app.domain.repository.DerivedDataRepository
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.time.Instant

class SummarizationStageTest {

    private lateinit var textGenerator: TextGenerator
    private lateinit var derivedDataRepository: DerivedDataRepository
    private lateinit var stage: SummarizationStage

    private val savedSlot = slot<MemorySummary>()
    private val promptSlot = slot<String>()

    @Before
    fun setup() {
        textGenerator = mockk()
        derivedDataRepository = mockk(relaxed = true)
        stage = SummarizationStage(textGenerator, derivedDataRepository)

        every { textGenerator.modelIdentifier() } returns "gpt-4o-mini"
        coEvery { derivedDataRepository.saveSummary(capture(savedSlot)) } just Runs
    }

    private fun modelReturns(response: String) {
        every { textGenerator.isAvailable() } returns true
        coEvery { textGenerator.generate(capture(promptSlot), any()) } returns Result.success(response)
    }

    private fun textBlock(content: String) = ContentBlock(
        id = 1L, memoryId = 1L, position = 0, type = ContentType.TEXT, content = content
    )

    private fun imageBlock(id: Long) = ContentBlock(
        id = id, memoryId = 1L, position = id.toInt(),
        type = ContentType.IMAGE, content = "/img/$id.webp"
    )

    private fun memory(
        blocks: List<ContentBlock> = listOf(textBlock("Resources about local AI")),
        derived: DerivedData = DerivedData.EMPTY
    ) = Memory(id = 1L, processingState = ProcessingState.PROCESSING,
        contentBlocks = blocks, derived = derived)

    // --- the happy path ---------------------------------------------------

    @Test
    fun `a summary is generated and saved`() = runTest {
        modelReturns("A collection of resources about running AI models on Android.")

        val result = stage.process(memory())

        assertEquals(StageResult.Success, result)
        val saved = savedSlot.captured
        assertEquals(StageStatus.SUCCESS, saved.status)
        assertEquals("A collection of resources about running AI models on Android.", saved.summaryText)
    }

    @Test
    fun `the producing model is recorded`() = runTest {
        modelReturns("A summary.")

        stage.process(memory())

        assertEquals("gpt-4o-mini", savedSlot.captured.providerModel)
    }

    // --- cleaning up what models actually return ---------------------------

    @Test
    fun `a Summary colon preamble is stripped`() = runTest {
        modelReturns("Summary: A collection of Android AI resources.")

        stage.process(memory())

        assertEquals("A collection of Android AI resources.", savedSlot.captured.summaryText)
    }

    @Test
    fun `a conversational preamble is stripped`() = runTest {
        modelReturns("Sure! Here's a summary: A collection of Android AI resources.")

        stage.process(memory())

        assertEquals("A collection of Android AI resources.", savedSlot.captured.summaryText)
    }

    @Test
    fun `wrapping quotes are stripped`() = runTest {
        modelReturns("\"A collection of Android AI resources.\"")

        stage.process(memory())

        assertEquals("A collection of Android AI resources.", savedSlot.captured.summaryText)
    }

    @Test
    fun `surrounding whitespace is trimmed`() = runTest {
        modelReturns("   A tidy summary.  \n ")

        stage.process(memory())

        assertEquals("A tidy summary.", savedSlot.captured.summaryText)
    }

    @Test
    fun `the word summary inside a real sentence is not stripped`() = runTest {
        // Preamble-stripping must not eat content that happens to start similarly.
        modelReturns("Summary statistics from a research paper on model quantization.")

        stage.process(memory())

        assertTrue(
            savedSlot.captured.summaryText.startsWith("Summary statistics")
        )
    }

    // --- no provider ------------------------------------------------------

    @Test
    fun `with no provider the stage records NOT_SUPPORTED`() = runTest {
        every { textGenerator.isAvailable() } returns false

        val result = stage.process(memory())

        assertEquals(StageResult.NotSupported, result)
        assertEquals(StageStatus.NOT_SUPPORTED, savedSlot.captured.status)
    }

    @Test
    fun `NOT_SUPPORTED is recorded rather than left blank, so the feed can explain`() = runTest {
        // Distinguishes "no provider configured" from "not processed yet".
        every { textGenerator.isAvailable() } returns false

        stage.process(memory())

        coVerify { derivedDataRepository.saveSummary(any()) }
        assertNull(savedSlot.captured.providerModel)
    }

    @Test
    fun `with no provider the model is never called`() = runTest {
        every { textGenerator.isAvailable() } returns false

        stage.process(memory())

        coVerify(exactly = 0) { textGenerator.generate(any(), any()) }
    }

    // --- failure and emptiness -------------------------------------------

    @Test
    fun `a model error is recorded as FAILED`() = runTest {
        every { textGenerator.isAvailable() } returns true
        coEvery { textGenerator.generate(any(), any()) } returns
            Result.failure(RuntimeException("rate limited"))

        val result = stage.process(memory())

        assertTrue(result is StageResult.Failed)
        assertEquals(StageStatus.FAILED, savedSlot.captured.status)
    }

    @Test
    fun `a blank response is recorded as EMPTY, distinct from failure`() = runTest {
        modelReturns("   ")

        val result = stage.process(memory())

        assertEquals(StageResult.Empty, result)
        assertEquals(StageStatus.EMPTY, savedSlot.captured.status)
    }

    @Test
    fun `a response that is only a preamble is EMPTY rather than saving the preamble`() = runTest {
        modelReturns("Summary:")

        val result = stage.process(memory())

        assertEquals(StageResult.Empty, result)
    }

    // --- what the model is asked ------------------------------------------

    @Test
    fun `the prompt asks for the subject rather than a list`() = runTest {
        modelReturns("x")

        stage.process(memory())

        val prompt = promptSlot.captured.lowercase()
        assertTrue(prompt.contains("do not list the individual items"))
        assertTrue(prompt.contains("generally about"))
    }

    @Test
    fun `the prompt forbids inventing content`() = runTest {
        modelReturns("x")

        stage.process(memory())

        assertTrue(promptSlot.captured.lowercase().contains("do not infer or invent"))
    }

    @Test
    fun `the prompt asks for no preamble`() = runTest {
        modelReturns("x")

        stage.process(memory())

        assertTrue(promptSlot.captured.lowercase().contains("no preamble"))
    }

    @Test
    fun `a truncated memory tells the model it is seeing only part`() = runTest {
        // Otherwise the model would describe six images as though that were all of
        // them, and the summary would understate what the Memory holds.
        modelReturns("x")
        val manyImages = (1L..20L).map { imageBlock(it) }

        stage.process(
            memory(
                blocks = manyImages,
                derived = DerivedData(
                    ocrResults = (1L..20L).map {
                        OcrResult(
                            memoryId = 1L, contentBlockId = it,
                            status = StageStatus.SUCCESS, extractedText = "text $it",
                            processedAt = Instant.EPOCH
                        )
                    }
                )
            )
        )

        val prompt = promptSlot.captured
        assertTrue(prompt.contains("partial view"))
        assertTrue(prompt.contains("20 images"))
    }

    @Test
    fun `a complete memory does not mention truncation`() = runTest {
        modelReturns("x")

        stage.process(memory())

        assertFalse(promptSlot.captured.contains("partial view"))
    }

    // --- nothing to summarise ---------------------------------------------

    @Test
    fun `a memory with nothing in it is skipped`() = runTest {
        val result = stage.process(Memory(id = 1L))

        assertEquals(StageResult.Skipped, result)
        verify(exactly = 0) { textGenerator.isAvailable() }
        coVerify(exactly = 0) { derivedDataRepository.saveSummary(any()) }
    }

    @Test
    fun `an image with nothing read out of it yet is skipped`() = runTest {
        val result = stage.process(memory(blocks = listOf(imageBlock(2L))))

        assertEquals(StageResult.Skipped, result)
    }

    @Test
    fun `the stage declares itself as SUMMARIZATION so it runs last`() {
        assertEquals(StageId.SUMMARIZATION, stage.id)
    }

    // --- title extraction -------------------------------------------------

    @Test
    fun `a title and summary are both extracted from the requested format`() = runTest {
        modelReturns(
            """
            TITLE: Phone Quick Settings
            SUMMARY: A screenshot of a Realme phone's quick settings panel.
            """.trimIndent()
        )

        stage.process(memory())

        assertEquals("Phone Quick Settings", savedSlot.captured.title)
        assertEquals(
            "A screenshot of a Realme phone's quick settings panel.",
            savedSlot.captured.summaryText
        )
    }

    @Test
    fun `a title survives reasoning that ends in an explicit boundary`() = runTest {
        modelReturns(
            """
            We need to produce a title and summary. The text appears to be a phone
            screenshot. Let's craft:
            TITLE: Phone Quick Settings
            SUMMARY: A screenshot of a Realme phone's quick settings panel.
            """.trimIndent()
        )

        stage.process(memory())

        assertEquals("Phone Quick Settings", savedSlot.captured.title)
    }

    @Test
    fun `a title survives reasoning with no boundary marker`() = runTest {
        // takeLastSentences joins sentences with a space, which puts "TITLE:" in the
        // middle of a line. parseResponse anchors on ^TITLE: (MULTILINE), so the
        // anchor fails and the title is silently lost.
        modelReturns(
            """
            We need to produce a title. The text appears to be a screenshot of a phone.
            TITLE: Phone Quick Settings
            SUMMARY: A screenshot of a Realme phone's quick settings panel.
            """.trimIndent()
        )

        stage.process(memory())

        assertEquals("Phone Quick Settings", savedSlot.captured.title)
    }

    @Test
    fun `reasoning never reaches the summary, even when the title anchor fails`() = runTest {
        // The worst consequence of the anchor failing: parseResponse falls back to
        // `null to cleaned`, so the reasoning AND the literal TITLE:/SUMMARY: labels
        // are saved as the summary and rendered on the feed card.
        modelReturns(
            """
            We need to produce a title. The text appears to be a screenshot of a phone.
            TITLE: Phone Quick Settings
            SUMMARY: A screenshot of a Realme phone's quick settings panel.
            """.trimIndent()
        )

        stage.process(memory())

        val summary = savedSlot.captured.summaryText
        assertFalse("reasoning leaked: $summary", summary.contains("We need to"))
        assertFalse("reasoning leaked: $summary", summary.contains("text appears to be"))
        assertFalse("label leaked: $summary", summary.contains("TITLE:"))
        assertFalse("label leaked: $summary", summary.contains("SUMMARY:"))
    }

    @Test
    fun `a summary spanning several lines is kept whole`() = runTest {
        // `.` excludes newlines, so ^SUMMARY:\s*(.+) captures only the first line and
        // the rest of the summary is dropped.
        modelReturns(
            """
            TITLE: Ramen Recipes
            SUMMARY: A collection of tonkotsu ramen resources.
            Includes simmering times and stock ratios.
            """.trimIndent()
        )

        stage.process(memory())

        assertTrue(
            "lost the tail: ${savedSlot.captured.summaryText}",
            savedSlot.captured.summaryText.contains("simmering times")
        )
    }

    @Test
    fun `a model that ignores the format still yields a summary and no title`() = runTest {
        modelReturns("A collection of resources about running AI models on Android.")

        stage.process(memory())

        assertNull(savedSlot.captured.title)
        assertEquals(
            "A collection of resources about running AI models on Android.",
            savedSlot.captured.summaryText
        )
    }
}
