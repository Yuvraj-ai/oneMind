package com.onemind.app

import com.onemind.app.domain.model.Memory
import com.onemind.app.domain.model.SourceType
import com.onemind.app.domain.repository.MemoryRepository
import com.onemind.app.domain.search.*
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * End-to-end retrieval: understanding, both search paths, hard filters, ranking.
 *
 * The hard-filter tests carry the most weight. A filter that is too permissive
 * answers a question the user did not ask; one that is too strict hides the Memory
 * they wanted and reports "no results". Neither failure announces itself.
 */
class SearchOrchestratorTest {

    private lateinit var queryUnderstanding: QueryUnderstanding
    private lateinit var keywordSearcher: KeywordSearcher
    private lateinit var vectorSearcher: VectorSearcher
    private lateinit var memoryRepository: MemoryRepository
    private lateinit var orchestrator: SearchOrchestrator

    private val zone = ZoneId.systemDefault()

    @Before
    fun setup() {
        queryUnderstanding = mockk()
        keywordSearcher = mockk()
        vectorSearcher = mockk()
        memoryRepository = mockk()
        orchestrator = SearchOrchestrator(
            queryUnderstanding, keywordSearcher, vectorSearcher, memoryRepository
        )

        // Sensible defaults; individual tests override.
        coEvery { keywordSearcher.search(any(), any()) } returns emptyList()
        coEvery { vectorSearcher.search(any(), any(), any()) } returns emptyList()
    }

    private fun intentIs(intent: SearchIntent) {
        coEvery { queryUnderstanding.understand(any()) } returns intent
    }

    private fun memoriesAre(vararg memories: Memory) {
        coEvery { memoryRepository.getMemoriesByIds(any()) } returns memories.toList()
    }

    private fun keywordFinds(vararg matches: Pair<Long, Double>) {
        coEvery { keywordSearcher.search(any(), any()) } returns
            matches.map { KeywordMatch(it.first, it.second, "text") }
    }

    private fun vectorFinds(vararg matches: Pair<Long, Double>) {
        coEvery { vectorSearcher.search(any(), any(), any()) } returns
            matches.map { VectorMatch(it.first, it.second) }
    }

    private fun memory(
        id: Long,
        createdAt: Instant = Instant.parse("2026-08-19T14:00:00Z"),
        sourceType: SourceType = SourceType.MANUAL,
        sourcePackage: String? = null
    ) = Memory(
        id = id, createdAt = createdAt,
        sourceType = sourceType, sourcePackage = sourcePackage
    )

    private fun dayRange(date: String): TemporalExpression {
        val start = ZonedDateTime.parse("${date}T00:00:00Z").withZoneSameInstant(zone)
        return TemporalExpression(
            start = start.toInstant(),
            endExclusive = start.plusDays(1).toInstant(),
            matchedText = "yesterday"
        )
    }

    // --- both paths run ------------------------------------------------------

    @Test
    fun `both keyword and vector search are consulted`() = runTest {
        intentIs(SearchIntent.literal("ramen"))
        keywordFinds(1L to 0.9)
        memoriesAre(memory(1L))

        orchestrator.search("ramen")

        coVerify(exactly = 1) { keywordSearcher.search(any(), any()) }
        coVerify(exactly = 1) { vectorSearcher.search(any(), any(), any()) }
    }

    @Test
    fun `each searcher gets the query text meant for it`() = runTest {
        // Keyword keeps the temporal words; semantic does not. #27 makes that
        // distinction and it must survive to here.
        intentIs(
            SearchIntent(
                keywordQuery = "AI stuff from last week",
                semanticQuery = "AI stuff",
                temporal = null
            )
        )
        keywordFinds(1L to 0.9)
        memoriesAre(memory(1L))

        orchestrator.search("AI stuff from last week")

        coVerify { keywordSearcher.search("AI stuff from last week", any()) }
        coVerify { vectorSearcher.search("AI stuff", any(), any()) }
    }

    @Test
    fun `results from both paths are merged and ranked`() = runTest {
        intentIs(SearchIntent.literal("ramen"))
        keywordFinds(1L to 0.4)
        vectorFinds(2L to 0.95)
        memoriesAre(memory(1L), memory(2L))

        val results = orchestrator.search("ramen")

        assertEquals(2, results.size)
        // The strong semantic match outranks the weak keyword one.
        assertEquals(2L, results.first().memory.id)
    }

    // --- temporal is a hard filter ------------------------------------------

    @Test
    fun `a memory outside the temporal window is excluded regardless of score`() = runTest {
        // "What did I save yesterday" is a statement about eligibility. Letting a
        // highly relevant Memory from last month through would answer a different
        // question.
        intentIs(
            SearchIntent(
                keywordQuery = "notes",
                semanticQuery = "notes",
                temporal = dayRange("2026-08-18")
            )
        )
        keywordFinds(1L to 1.0, 2L to 1.0)
        memoriesAre(
            memory(1L, createdAt = Instant.parse("2026-08-18T12:00:00Z")),
            memory(2L, createdAt = Instant.parse("2026-07-01T12:00:00Z"))
        )

        val results = orchestrator.search("notes yesterday")

        assertEquals(listOf(1L), results.map { it.memory.id })
    }

    @Test
    fun `no memory in the window returns empty even with strong matches`() = runTest {
        intentIs(
            SearchIntent(
                keywordQuery = "notes",
                semanticQuery = "notes",
                temporal = dayRange("2026-08-18")
            )
        )
        keywordFinds(1L to 1.0)
        memoriesAre(memory(1L, createdAt = Instant.parse("2026-01-01T12:00:00Z")))

        assertTrue(orchestrator.search("notes yesterday").isEmpty())
    }

    // --- source is a hard filter ---------------------------------------------

    @Test
    fun `a source type constraint excludes other sources`() = runTest {
        intentIs(
            SearchIntent(
                keywordQuery = "ai", semanticQuery = "ai",
                sourceType = SourceType.SHARE
            )
        )
        keywordFinds(1L to 0.9, 2L to 0.9)
        memoriesAre(
            memory(1L, sourceType = SourceType.SHARE),
            memory(2L, sourceType = SourceType.SCREENSHOT)
        )

        val results = orchestrator.search("ai I shared")

        assertEquals(listOf(1L), results.map { it.memory.id })
    }

    @Test
    fun `a source package constraint matches case-insensitively`() = runTest {
        intentIs(
            SearchIntent(
                keywordQuery = "ai", semanticQuery = "ai",
                sourcePackage = "COM.ANDROID.CHROME"
            )
        )
        keywordFinds(1L to 0.9)
        memoriesAre(memory(1L, sourcePackage = "com.android.chrome"))

        assertEquals(1, orchestrator.search("ai from chrome").size)
    }

    @Test
    fun `a memory with no known source is excluded by a source package constraint`() = runTest {
        // Unknown is not "not Chrome", but it is also not evidence of Chrome. #17 is
        // explicit that a missing source is never fabricated, so it cannot satisfy a
        // constraint that names one.
        intentIs(
            SearchIntent(
                keywordQuery = "ai", semanticQuery = "ai",
                sourcePackage = "com.android.chrome"
            )
        )
        keywordFinds(1L to 0.9)
        memoriesAre(memory(1L, sourcePackage = null))

        assertTrue(orchestrator.search("ai from chrome").isEmpty())
    }

    @Test
    fun `no source constraint admits memories with unknown source`() = runTest {
        intentIs(SearchIntent.literal("ai"))
        keywordFinds(1L to 0.9)
        memoriesAre(memory(1L, sourcePackage = null))

        assertEquals(1, orchestrator.search("ai").size)
    }

    // --- combined constraints, the locked docs' example ---------------------

    @Test
    fun `semantic source and temporal constraints apply together`() = runTest {
        // "AI stuff from Chrome last week" — the query the locked decisions use to
        // describe unified retrieval.
        intentIs(
            SearchIntent(
                keywordQuery = "AI stuff from Chrome last week",
                semanticQuery = "AI",
                temporal = dayRange("2026-08-18"),
                sourceType = SourceType.SHARE,
                sourcePackage = "com.android.chrome"
            )
        )
        vectorFinds(1L to 0.9, 2L to 0.9, 3L to 0.9, 4L to 0.9)
        memoriesAre(
            // Passes everything.
            memory(1L, Instant.parse("2026-08-18T10:00:00Z"), SourceType.SHARE, "com.android.chrome"),
            // Wrong day.
            memory(2L, Instant.parse("2026-07-01T10:00:00Z"), SourceType.SHARE, "com.android.chrome"),
            // Wrong app.
            memory(3L, Instant.parse("2026-08-18T10:00:00Z"), SourceType.SHARE, "com.whatsapp"),
            // Wrong source type.
            memory(4L, Instant.parse("2026-08-18T10:00:00Z"), SourceType.SCREENSHOT, "com.android.chrome")
        )

        val results = orchestrator.search("AI stuff from Chrome last week")

        assertEquals(listOf(1L), results.map { it.memory.id })
    }

    // --- empty and degenerate cases -----------------------------------------

    @Test
    fun `a blank query returns empty without any work`() = runTest {
        assertTrue(orchestrator.search("   ").isEmpty())

        coVerify(exactly = 0) { queryUnderstanding.understand(any()) }
        coVerify(exactly = 0) { keywordSearcher.search(any(), any()) }
    }

    @Test
    fun `neither path finding anything returns empty without hydrating`() = runTest {
        intentIs(SearchIntent.literal("nonexistent"))

        assertTrue(orchestrator.search("nonexistent").isEmpty())
        coVerify(exactly = 0) { memoryRepository.getMemoriesByIds(any()) }
    }

    @Test
    fun `weak matches below the relevance threshold return empty`() = runTest {
        intentIs(SearchIntent.literal("ramen"))
        keywordFinds(1L to 0.05)
        memoriesAre(memory(1L))

        assertTrue(orchestrator.search("ramen").isEmpty())
    }

    // --- hydration -----------------------------------------------------------

    @Test
    fun `memories are hydrated in one batched query`() = runTest {
        intentIs(SearchIntent.literal("ramen"))
        keywordFinds(1L to 0.9, 2L to 0.9, 3L to 0.9)
        memoriesAre(memory(1L), memory(2L), memory(3L))

        orchestrator.search("ramen")

        coVerify(exactly = 1) { memoryRepository.getMemoriesByIds(any()) }
    }

    @Test
    fun `a memory found by both paths is hydrated once`() = runTest {
        intentIs(SearchIntent.literal("ramen"))
        keywordFinds(1L to 0.9)
        vectorFinds(1L to 0.9)
        memoriesAre(memory(1L))

        val results = orchestrator.search("ramen")

        assertEquals(1, results.size)
        coVerify { memoryRepository.getMemoriesByIds(listOf(1L)) }
    }

    // --- limit ---------------------------------------------------------------

    @Test
    fun `results are capped at the limit`() = runTest {
        intentIs(SearchIntent.literal("ramen"))
        keywordFinds(*(1L..50L).map { it to 0.9 }.toTypedArray())
        memoriesAre(*(1L..50L).map { memory(it) }.toTypedArray())

        assertEquals(5, orchestrator.search("ramen", limit = 5).size)
    }

    @Test
    fun `candidates are over-fetched, since filters cut after retrieval`() = runTest {
        intentIs(SearchIntent.literal("ramen"))
        var requestedLimit = 0
        coEvery { keywordSearcher.search(any(), any()) } answers {
            requestedLimit = secondArg()
            emptyList()
        }

        orchestrator.search("ramen", limit = 10)

        assertTrue("asked for only $requestedLimit candidates", requestedLimit > 10)
    }
}
