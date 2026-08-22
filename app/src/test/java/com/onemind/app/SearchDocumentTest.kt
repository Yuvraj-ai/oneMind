package com.onemind.app

import com.onemind.app.domain.model.*
import com.onemind.app.domain.processing.StageStatus
import com.onemind.app.domain.search.SearchDocument
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

/**
 * What goes into the search document, and what deliberately does not.
 *
 * These matter because the index is the only thing keyword search can see. A
 * source left out here is content the user can never find, and they would have no
 * way to tell why.
 */
class SearchDocumentTest {

    private fun textBlock(content: String, id: Long = 1L) = ContentBlock(
        id = id, memoryId = 1L, position = 0,
        type = ContentType.TEXT, content = content
    )

    private fun memory(
        blocks: List<ContentBlock> = emptyList(),
        derived: DerivedData = DerivedData.EMPTY
    ) = Memory(id = 1L, contentBlocks = blocks, derived = derived)

    private fun ocr(text: String, status: StageStatus = StageStatus.SUCCESS) = OcrResult(
        memoryId = 1L, contentBlockId = 2L, status = status,
        extractedText = text, processedAt = Instant.EPOCH
    )

    private fun vision(text: String, status: StageStatus = StageStatus.SUCCESS) = VisionResult(
        memoryId = 1L, contentBlockId = 2L, status = status,
        description = text, providerModel = "m", processedAt = Instant.EPOCH
    )

    private fun summary(text: String, status: StageStatus = StageStatus.SUCCESS) = MemorySummary(
        memoryId = 1L, summaryText = text, status = status
    )

    // --- every source is indexed -------------------------------------------

    @Test
    fun `user text is indexed`() {
        val doc = SearchDocument.build(memory(listOf(textBlock("Research Qwen models"))))

        assertTrue(doc.contains("Research Qwen models"))
    }

    @Test
    fun `the summary is indexed`() {
        val doc = SearchDocument.build(
            memory(derived = DerivedData(summary = summary("A collection about local AI.")))
        )

        assertTrue(doc.contains("A collection about local AI."))
    }

    @Test
    fun `OCR text is indexed`() {
        val doc = SearchDocument.build(
            memory(derived = DerivedData(ocrResults = listOf(ocr("AI Summit 2026"))))
        )

        assertTrue(doc.contains("AI Summit 2026"))
    }

    @Test
    fun `image descriptions are indexed`() {
        val doc = SearchDocument.build(
            memory(derived = DerivedData(visionResults = listOf(vision("A mountain range"))))
        )

        assertTrue(doc.contains("A mountain range"))
    }

    @Test
    fun `entity names are indexed`() {
        val doc = SearchDocument.build(
            memory(
                derived = DerivedData(
                    entities = listOf(
                        ExtractedEntity(memoryId = 1L, name = "Anthropic", entityType = EntityType.ORGANIZATION)
                    )
                )
            )
        )

        assertTrue(doc.contains("Anthropic"))
    }

    @Test
    fun `category names are indexed`() {
        // So a query naming a category finds its Memories even when no body text
        // uses the word.
        val doc = SearchDocument.build(
            memory(derived = DerivedData(categories = listOf(Category(id = 1L, name = "Food & Cooking"))))
        )

        assertTrue(doc.contains("Food & Cooking"))
    }

    @Test
    fun `all sources appear together`() {
        val doc = SearchDocument.build(
            memory(
                blocks = listOf(textBlock("typed words")),
                derived = DerivedData(
                    summary = summary("the summary"),
                    ocrResults = listOf(ocr("scanned words")),
                    visionResults = listOf(vision("seen things")),
                    entities = listOf(
                        ExtractedEntity(memoryId = 1L, name = "Qwen", entityType = EntityType.TECHNOLOGY)
                    ),
                    urls = listOf(
                        ExtractedUrl(
                            memoryId = 1L, rawUrl = "https://github.com/a/b",
                            normalizedUrl = "https://github.com/a/b", domain = "github.com"
                        )
                    ),
                    categories = listOf(Category(id = 1L, name = "Technology"))
                )
            )
        )

        listOf(
            "typed words", "the summary", "scanned words",
            "seen things", "Qwen", "github.com", "Technology"
        ).forEach { expected ->
            assertTrue("document omitted '$expected'", doc.contains(expected))
        }
    }
    // --- URLs are indexed by domain, not in full ---------------------------

    @Test
    fun `URLs contribute their host and path, but never the query string`() {
        // The locked product decisions list URLs among searchable things, so the
        // path is indexed. The query string is not: it carries tracking parameters
        // and session ids that match queries by accident and never on purpose.
        val doc = SearchDocument.build(
            memory(
                derived = DerivedData(
                    urls = listOf(
                        ExtractedUrl(
                            memoryId = 1L,
                            rawUrl = "https://seriouseats.com/ramen?utm_source=newsletter&ref=xyz123",
                            normalizedUrl = "https://seriouseats.com/ramen?utm_source=newsletter&ref=xyz123",
                            domain = "seriouseats.com"
                        )
                    )
                )
            )
        )

        assertTrue("host should be searchable", doc.contains("seriouseats.com"))
        assertTrue("path should be searchable", doc.contains("ramen"))
        assertFalse("tracking parameters must not be indexed", doc.contains("utm_source"))
        assertFalse(doc.contains("xyz123"))
        assertFalse(doc.contains("newsletter"))
    }

    @Test
    fun `a www host is findable by its bare domain`() {
        // `domain` is indexed alongside the normalised URL precisely because it has
        // "www." stripped, and a query for "example.com" should find this.
        val doc = SearchDocument.build(
            memory(
                derived = DerivedData(
                    urls = listOf(
                        ExtractedUrl(
                            memoryId = 1L,
                            rawUrl = "https://www.example.com/page",
                            normalizedUrl = "https://www.example.com/page",
                            domain = "example.com"
                        )
                    )
                )
            )
        )

        assertTrue(doc.split("\n").any { it == "example.com" })
    }

    @Test
    fun `a repeated domain is indexed once`() {
        val doc = SearchDocument.build(
            memory(
                derived = DerivedData(
                    urls = (1..3).map {
                        ExtractedUrl(
                            id = it.toLong(), memoryId = 1L,
                            rawUrl = "https://github.com/repo$it",
                            normalizedUrl = "https://github.com/repo$it",
                            domain = "github.com"
                        )
                    }
                )
            )
        )

        assertEquals(1, doc.split("\n").count { it == "github.com" })
    }
    // --- only successful derived data is indexed ---------------------------

    @Test
    fun `a failed OCR result is not indexed`() {
        val doc = SearchDocument.build(
            memory(derived = DerivedData(ocrResults = listOf(ocr("garbage", StageStatus.FAILED))))
        )

        assertFalse(doc.contains("garbage"))
    }

    @Test
    fun `a NOT_SUPPORTED summary is not indexed`() {
        // Its text is empty; indexing it would add a blank line and make the
        // index's contents depend on stage outcomes in a confusing way.
        val doc = SearchDocument.build(
            memory(derived = DerivedData(summary = summary("", StageStatus.NOT_SUPPORTED)))
        )

        assertTrue(doc.isBlank())
    }

    @Test
    fun `a failed vision result is not indexed`() {
        val doc = SearchDocument.build(
            memory(derived = DerivedData(visionResults = listOf(vision("nonsense", StageStatus.FAILED))))
        )

        assertFalse(doc.contains("nonsense"))
    }

    // --- nothing to index --------------------------------------------------

    @Test
    fun `a memory with nothing in it produces an empty document`() {
        assertTrue(SearchDocument.build(memory()).isBlank())
    }

    @Test
    fun `blank text does not produce a document`() {
        assertTrue(SearchDocument.build(memory(listOf(textBlock("   \n  ")))).isBlank())
    }

    @Test
    fun `an unprocessed image-only memory produces an empty document`() {
        // No OCR or vision output yet, so there is genuinely nothing to find it by.
        val imageOnly = memory(
            listOf(
                ContentBlock(
                    id = 1L, memoryId = 1L, position = 0,
                    type = ContentType.IMAGE, content = "/img/1.webp"
                )
            )
        )

        assertTrue(SearchDocument.build(imageOnly).isBlank())
    }

    // --- stability ---------------------------------------------------------

    @Test
    fun `the same memory always produces the same document`() {
        // Otherwise reindexing an unchanged Memory would churn the FTS table.
        val m = memory(
            blocks = listOf(textBlock("stable")),
            derived = DerivedData(
                summary = summary("also stable"),
                ocrResults = listOf(ocr("and this"))
            )
        )

        assertEquals(SearchDocument.build(m), SearchDocument.build(m))
    }
}
