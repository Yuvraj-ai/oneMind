package com.onemind.app

import com.onemind.app.domain.model.*
import com.onemind.app.domain.processing.BoundedAnalysisInput
import com.onemind.app.domain.processing.StageStatus
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

/**
 * The size-limited view of a Memory handed to a language model.
 *
 * The caps are the point: a Memory of twenty screenshots and thirty links must not
 * be sent whole, both because it would overrun a small model's context and because
 * it would not produce a better answer.
 */
class BoundedAnalysisInputTest {

    private fun textBlock(content: String, id: Long = 1L) = ContentBlock(
        id = id, memoryId = 1L, position = id.toInt(),
        type = ContentType.TEXT, content = content
    )

    private fun imageBlock(id: Long) = ContentBlock(
        id = id, memoryId = 1L, position = id.toInt(),
        type = ContentType.IMAGE, content = "/img/$id.webp"
    )

    private fun ocr(blockId: Long, text: String) = OcrResult(
        memoryId = 1L, contentBlockId = blockId, status = StageStatus.SUCCESS,
        extractedText = text, processedAt = Instant.EPOCH
    )

    private fun vision(blockId: Long, text: String) = VisionResult(
        memoryId = 1L, contentBlockId = blockId, status = StageStatus.SUCCESS,
        description = text, providerModel = "m", processedAt = Instant.EPOCH
    )

    private fun url(raw: String, id: Long) = ExtractedUrl(
        id = id, memoryId = 1L, rawUrl = raw, normalizedUrl = raw,
        domain = raw.removePrefix("https://").substringBefore('/')
    )

    private fun memory(
        blocks: List<ContentBlock> = emptyList(),
        derived: DerivedData = DerivedData.EMPTY
    ) = Memory(id = 1L, contentBlocks = blocks, derived = derived)

    // --- what gets included -----------------------------------------------

    @Test
    fun `includes what the user wrote`() {
        val input = BoundedAnalysisInput.from(memory(listOf(textBlock("Research Qwen"))))

        assertTrue(input.text.contains("Research Qwen"))
    }

    @Test
    fun `includes text read off the images`() {
        val input = BoundedAnalysisInput.from(
            memory(
                blocks = listOf(imageBlock(2L)),
                derived = DerivedData(ocrResults = listOf(ocr(2L, "AI Summit 2026")))
            )
        )

        assertTrue(input.text.contains("AI Summit 2026"))
    }

    @Test
    fun `includes image descriptions`() {
        val input = BoundedAnalysisInput.from(
            memory(
                blocks = listOf(imageBlock(2L)),
                derived = DerivedData(visionResults = listOf(vision(2L, "A mountain range.")))
            )
        )

        assertTrue(input.text.contains("A mountain range."))
    }

    @Test
    fun `includes links`() {
        val input = BoundedAnalysisInput.from(
            memory(derived = DerivedData(urls = listOf(url("https://github.com/a", 1L))))
        )

        assertTrue(input.text.contains("https://github.com/a"))
    }

    @Test
    fun `labels each section so the model knows what it is looking at`() {
        val input = BoundedAnalysisInput.from(
            memory(
                blocks = listOf(textBlock("typed"), imageBlock(2L)),
                derived = DerivedData(
                    ocrResults = listOf(ocr(2L, "scanned")),
                    visionResults = listOf(vision(2L, "described")),
                    urls = listOf(url("https://a.com", 1L))
                )
            )
        )

        assertTrue(input.text.contains("The user wrote:"))
        assertTrue(input.text.contains("Text read from the images:"))
        assertTrue(input.text.contains("The images show:"))
        assertTrue(input.text.contains("Links:"))
    }

    @Test
    fun `omits sections that have nothing in them`() {
        val input = BoundedAnalysisInput.from(memory(listOf(textBlock("only text"))))

        assertFalse(input.text.contains("Links:"))
        assertFalse(input.text.contains("The images show:"))
    }

    // --- reuse rather than reprocess ---------------------------------------

    @Test
    fun `images are represented by derived text, never by their file paths`() {
        // Sending a path to a language model would be useless. The point of running
        // OCR and vision first is that their output is what carries meaning.
        val input = BoundedAnalysisInput.from(
            memory(
                blocks = listOf(imageBlock(2L)),
                derived = DerivedData(ocrResults = listOf(ocr(2L, "readable text")))
            )
        )

        assertFalse(input.text.contains("/img/2.webp"))
        assertTrue(input.text.contains("readable text"))
    }

    // --- the caps ---------------------------------------------------------

    @Test
    fun `only the first few images are represented`() {
        val blocks = (1L..20L).map { imageBlock(it) }
        val ocrResults = (1L..20L).map { ocr(it, "image $it text") }

        val input = BoundedAnalysisInput.from(
            memory(blocks = blocks, derived = DerivedData(ocrResults = ocrResults))
        )

        assertEquals(BoundedAnalysisInput.MAX_IMAGES, input.imagesConsidered)
        assertEquals(20, input.imagesTotal)
        // Text from an image beyond the cap must not leak in.
        assertFalse(input.text.contains("image 20 text"))
        assertTrue(input.text.contains("image 1 text"))
    }

    @Test
    fun `only the first few links are represented`() {
        val urls = (1L..30L).map { url("https://site$it.com", it) }

        val input = BoundedAnalysisInput.from(memory(derived = DerivedData(urls = urls)))

        assertEquals(BoundedAnalysisInput.MAX_URLS, input.urlsConsidered)
        assertEquals(30, input.urlsTotal)
        assertFalse(input.text.contains("site30.com"))
    }

    @Test
    fun `text is capped in length`() {
        val input = BoundedAnalysisInput.from(
            memory(listOf(textBlock("word ".repeat(5_000))))
        )

        assertTrue(input.text.length <= BoundedAnalysisInput.MAX_TEXT_CHARS)
    }

    @Test
    fun `truncation is reported so a prompt can acknowledge it`() {
        val blocks = (1L..20L).map { imageBlock(it) }

        val input = BoundedAnalysisInput.from(memory(blocks = blocks))

        assertTrue(input.wasTruncated)
    }

    @Test
    fun `a small memory is not marked truncated`() {
        val input = BoundedAnalysisInput.from(
            memory(
                blocks = listOf(textBlock("short"), imageBlock(2L)),
                derived = DerivedData(
                    ocrResults = listOf(ocr(2L, "a little text")),
                    urls = listOf(url("https://a.com", 1L))
                )
            )
        )

        assertFalse(input.wasTruncated)
    }

    // --- stability --------------------------------------------------------

    @Test
    fun `the same memory always produces the same input`() {
        // Order is fixed, so re-processing an unchanged Memory does not produce a
        // different summary for no reason.
        val m = memory(
            blocks = listOf(textBlock("typed"), imageBlock(2L)),
            derived = DerivedData(
                ocrResults = listOf(ocr(2L, "scanned")),
                visionResults = listOf(vision(2L, "described")),
                urls = listOf(url("https://a.com", 1L))
            )
        )

        assertEquals(
            BoundedAnalysisInput.from(m).text,
            BoundedAnalysisInput.from(m).text
        )
    }

    // --- nothing to work with ---------------------------------------------

    @Test
    fun `an empty memory produces empty input`() {
        assertTrue(BoundedAnalysisInput.from(memory()).isEmpty)
    }

    @Test
    fun `an image with no derived text yet produces empty input`() {
        // Nothing has been read out of it, so there is nothing to describe.
        assertTrue(BoundedAnalysisInput.from(memory(listOf(imageBlock(2L)))).isEmpty)
    }

    @Test
    fun `blank user text does not count as content`() {
        assertTrue(BoundedAnalysisInput.from(memory(listOf(textBlock("   \n ")))).isEmpty)
    }

    // --- the shared text bound --------------------------------------------

    @Test
    fun `boundText applies the same cap, so callers cannot drift`() {
        val bounded = BoundedAnalysisInput.boundText("x".repeat(10_000))

        assertEquals(BoundedAnalysisInput.MAX_TEXT_CHARS, bounded.length)
    }

    @Test
    fun `boundText leaves short text untouched`() {
        assertEquals("short", BoundedAnalysisInput.boundText("short"))
    }
}
