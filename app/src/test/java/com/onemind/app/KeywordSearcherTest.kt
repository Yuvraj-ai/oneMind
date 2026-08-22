package com.onemind.app

import com.onemind.app.domain.repository.IndexedDocument
import com.onemind.app.domain.repository.SearchIndexRepository
import com.onemind.app.domain.search.KeywordScoring
import com.onemind.app.domain.search.KeywordSearcher
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class KeywordSearcherTest {

    private lateinit var repository: SearchIndexRepository
    private lateinit var searcher: KeywordSearcher

    private val expressionSlot = slot<String>()

    @Before
    fun setup() {
        repository = mockk()
        searcher = KeywordSearcher(repository)
    }

    private fun indexReturns(vararg docs: Pair<Long, String>) {
        coEvery { repository.match(capture(expressionSlot), any()) } returns
            docs.map { (id, text) -> IndexedDocument(id, text) }
    }

    // --- scoring and ordering ----------------------------------------------

    @Test
    fun `results are ordered best match first`() = runTest {
        indexReturns(
            1L to "only ramen here",
            2L to "ramen recipe from tokyo",
            3L to "recipe"
        )

        val results = searcher.search("ramen recipe tokyo")

        // Memory 2 covers all three terms; 1 and 3 cover one each.
        assertEquals(2L, results.first().memoryId)
    }

    @Test
    fun `coverage beats repetition`() = runTest {
        // Someone searching three words is describing one Memory, not asking for
        // whichever Memory says one word most often.
        indexReturns(
            1L to "ramen ramen ramen ramen ramen ramen ramen ramen",
            2L to "ramen recipe tokyo"
        )

        val results = searcher.search("ramen recipe tokyo")

        assertEquals(2L, results.first().memoryId)
    }

    @Test
    fun `scores are within zero and one`() = runTest {
        indexReturns(1L to "ramen recipe tokyo japan noodles")

        val results = searcher.search("ramen recipe tokyo")

        results.forEach {
            assertTrue("score ${it.score} out of range", it.score in 0.0..1.0)
        }
    }

    @Test
    fun `a document matching nothing is dropped`() = runTest {
        // FTS returns rows by OR, so a row can come back that our own scoring
        // rates at zero. It must not be presented as a result.
        indexReturns(1L to "entirely unrelated content")

        assertTrue(searcher.search("ramen").isEmpty())
    }

    @Test
    fun `the matched document is carried through for snippets`() = runTest {
        indexReturns(1L to "ramen recipe from tokyo")

        val results = searcher.search("ramen")

        assertEquals("ramen recipe from tokyo", results.first().matchedText)
    }

    // --- prefix matching ---------------------------------------------------

    @Test
    fun `a partial word finds a longer one`() = runTest {
        // Results should appear while the user is still typing.
        indexReturns(1L to "ramen noodles")

        val results = searcher.search("ram")

        assertEquals(1, results.size)
        assertTrue(results.first().score > 0.0)
    }

    @Test
    fun `the expression uses prefix syntax`() = runTest {
        indexReturns(1L to "ramen")

        searcher.search("ram")

        assertTrue(expressionSlot.captured.contains("ram*"))
    }

    // --- unusable queries --------------------------------------------------

    @Test
    fun `an empty query does not hit the index`() = runTest {
        val results = searcher.search("")

        assertTrue(results.isEmpty())
        coVerify(exactly = 0) { repository.match(any(), any()) }
    }

    @Test
    fun `a punctuation-only query does not hit the index`() = runTest {
        val results = searcher.search("!!! ...")

        assertTrue(results.isEmpty())
        coVerify(exactly = 0) { repository.match(any(), any()) }
    }

    // --- limits ------------------------------------------------------------

    @Test
    fun `results are capped at the requested limit`() = runTest {
        indexReturns(*(1L..100L).map { it to "ramen $it" }.toTypedArray())

        val results = searcher.search("ramen", limit = 10)

        assertEquals(10, results.size)
    }

    @Test
    fun `the index is over-fetched so weak matches cannot crowd out strong ones`() = runTest {
        // OR semantics mean rows matching a single term come back too. Cutting at
        // `limit` in SQL would let an arbitrary set of those displace a
        // full-coverage match that happened to sort later by rowid.
        indexReturns(1L to "ramen")
        val limitSlot = slot<Int>()
        coEvery { repository.match(any(), capture(limitSlot)) } returns
            listOf(IndexedDocument(1L, "ramen"))

        searcher.search("ramen", limit = 10)

        assertTrue(
            "expected over-fetch, asked for ${limitSlot.captured}",
            limitSlot.captured > 10
        )
    }

    @Test
    fun `no matches returns empty`() = runTest {
        indexReturns()

        assertTrue(searcher.search("ramen").isEmpty())
    }

    // --- scoring directly ---------------------------------------------------

    @Test
    fun `full coverage scores higher than partial`() {
        val full = KeywordScoring.score("ramen recipe tokyo", listOf("ramen", "recipe", "tokyo"))
        val partial = KeywordScoring.score("ramen only", listOf("ramen", "recipe", "tokyo"))

        assertTrue("$full should exceed $partial", full > partial)
    }

    @Test
    fun `an empty query scores zero`() {
        assertEquals(0.0, KeywordScoring.score("anything", emptyList()), 0.0)
    }

    @Test
    fun `an empty document scores zero`() {
        assertEquals(0.0, KeywordScoring.score("", listOf("ramen")), 0.0)
    }

    @Test
    fun `scoring agrees with prefix matching`() = runTest {
        // A row FTS returned because of a prefix match must not then score zero and
        // be discarded — found by the index, dropped by our own arithmetic.
        assertTrue(KeywordScoring.score("ramen noodles", listOf("ram")) > 0.0)
    }
}
