package com.onemind.app

import com.onemind.app.domain.model.Category
import com.onemind.app.domain.model.DerivedData
import com.onemind.app.domain.model.Memory
import com.onemind.app.domain.search.KeywordMatch
import com.onemind.app.domain.search.ResultRanking
import com.onemind.app.domain.search.VectorMatch
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

/**
 * Blending two scoring sources into one ordering.
 *
 * The arithmetic is where a search quietly becomes bad: nothing throws, results
 * still appear, they are just in the wrong order. So the properties are asserted
 * directly rather than inferred from end-to-end behaviour.
 */
class ResultRankingTest {

    private fun memory(
        id: Long,
        createdAt: Instant = Instant.parse("2026-08-19T10:00:00Z"),
        categories: List<Category> = emptyList()
    ) = Memory(
        id = id,
        createdAt = createdAt,
        derived = DerivedData(categories = categories)
    )

    private fun memories(vararg m: Memory) = m.associateBy { it.id }

    private fun keyword(id: Long, score: Double, text: String = "matched text") =
        KeywordMatch(memoryId = id, score = score, matchedText = text)

    private fun vector(id: Long, score: Double) = VectorMatch(memoryId = id, score = score)

    // --- union, not intersection --------------------------------------------

    @Test
    fun `a memory found only by keyword is still a candidate`() {
        // An exact product name has no semantic neighbours. Requiring both signals
        // would discard the case keyword search exists to catch.
        val results = ResultRanking.rank(
            memories(memory(1L)),
            keywordMatches = listOf(keyword(1L, 1.0)),
            vectorMatches = emptyList()
        )

        assertEquals(1, results.size)
    }

    @Test
    fun `a memory found only by meaning is still a candidate`() {
        // A paraphrase shares no words. Same argument, other direction.
        val results = ResultRanking.rank(
            memories(memory(1L)),
            keywordMatches = emptyList(),
            vectorMatches = listOf(vector(1L, 0.95))
        )

        assertEquals(1, results.size)
        assertTrue(results.first().isSemanticOnly)
    }

    @Test
    fun `a memory found by both appears once`() {
        // Returning it twice would be the most visible possible bug.
        val results = ResultRanking.rank(
            memories(memory(1L)),
            keywordMatches = listOf(keyword(1L, 0.8)),
            vectorMatches = listOf(vector(1L, 0.9))
        )

        assertEquals(1, results.size)
    }

    @Test
    fun `a memory found by both counts both contributions`() {
        // Agreement between two independent signals is the strongest evidence
        // available; scoring by whichever was checked first would waste it.
        val both = ResultRanking.rank(
            memories(memory(1L)),
            listOf(keyword(1L, 0.8)),
            listOf(vector(1L, 0.9))
        ).first().score

        val keywordOnly = ResultRanking.rank(
            memories(memory(1L)),
            listOf(keyword(1L, 0.8)),
            emptyList()
        ).first().score

        assertTrue("$both should exceed $keywordOnly", both > keywordOnly)
    }

    // --- semantic rescaling -------------------------------------------------

    @Test
    fun `a cosine at the noise floor contributes nothing`() {
        // #13 measured unrelated text at ~0.5 on-device. Treating that as "half a
        // match" would let noise outrank a genuine partial keyword hit.
        val results = ResultRanking.rank(
            memories(memory(1L)),
            emptyList(),
            listOf(vector(1L, 0.5))
        )

        assertTrue("a floor-level cosine should not clear the threshold", results.isEmpty())
    }

    @Test
    fun `a high cosine reads as strong after rescaling`() {
        val results = ResultRanking.rank(
            memories(memory(1L)),
            emptyList(),
            listOf(vector(1L, 0.95))
        )

        assertTrue(results.isNotEmpty())
        assertTrue("score ${results.first().score} should be high", results.first().score > 0.5)
    }

    @Test
    fun `a partial keyword hit beats a near-noise semantic match`() {
        // The precise thing rescaling exists to fix.
        val results = ResultRanking.rank(
            memories(memory(1L), memory(2L)),
            keywordMatches = listOf(keyword(1L, 0.5)),
            vectorMatches = listOf(vector(2L, 0.6))
        )

        assertEquals(1L, results.first().memory.id)
    }

    // --- weighting ----------------------------------------------------------

    @Test
    fun `semantic evidence is weighted above keyword evidence`() {
        // This app is for people who have forgotten the words they saved.
        val semantic = ResultRanking.rank(
            memories(memory(1L)), emptyList(), listOf(vector(1L, 1.0))
        ).first().score

        val keywordOnly = ResultRanking.rank(
            memories(memory(2L)), listOf(keyword(2L, 1.0)), emptyList()
        ).first().score

        assertTrue("$semantic should exceed $keywordOnly", semantic > keywordOnly)
        assertEquals(ResultRanking.SEMANTIC_WEIGHT, semantic, 0.001)
        assertEquals(ResultRanking.KEYWORD_WEIGHT, keywordOnly, 0.001)
    }

    @Test
    fun `scores stay within zero and one`() {
        val results = ResultRanking.rank(
            memories(memory(1L, categories = listOf(Category(id = 9L, name = "Technology")))),
            listOf(keyword(1L, 1.0)),
            listOf(vector(1L, 1.0)),
            impliedCategories = listOf(Category(id = 9L, name = "Technology"))
        )

        // Perfect on both signals plus the category boost would exceed 1.0 unclamped.
        assertTrue(results.first().score <= 1.0)
    }

    // --- the category boost -------------------------------------------------

    @Test
    fun `a matching category raises the score`() {
        val category = Category(id = 9L, name = "Food & Cooking")

        val boosted = ResultRanking.rank(
            memories(memory(1L, categories = listOf(category))),
            listOf(keyword(1L, 0.5)),
            emptyList(),
            impliedCategories = listOf(category)
        ).first().score

        val unboosted = ResultRanking.rank(
            memories(memory(1L)),
            listOf(keyword(1L, 0.5)),
            emptyList(),
            impliedCategories = listOf(category)
        ).first().score

        assertEquals(ResultRanking.CATEGORY_BOOST, boosted - unboosted, 0.001)
    }

    @Test
    fun `the category boost cannot lift an irrelevant memory over a relevant one`() {
        // It is a hint, not evidence. Additive and small for exactly this reason.
        val category = Category(id = 9L, name = "Food & Cooking")

        val results = ResultRanking.rank(
            memories(
                memory(1L, categories = listOf(category)),
                memory(2L)
            ),
            keywordMatches = listOf(keyword(1L, 0.3), keyword(2L, 0.9)),
            vectorMatches = emptyList(),
            impliedCategories = listOf(category)
        )

        assertEquals(2L, results.first().memory.id)
    }

    @Test
    fun `no implied categories means no boost`() {
        val category = Category(id = 9L, name = "Food & Cooking")

        val results = ResultRanking.rank(
            memories(memory(1L, categories = listOf(category))),
            listOf(keyword(1L, 0.5)),
            emptyList()
        )

        assertEquals(ResultRanking.KEYWORD_WEIGHT * 0.5, results.first().score, 0.001)
    }

    // --- the relevance threshold --------------------------------------------

    @Test
    fun `everything below the threshold returns empty`() {
        // The locked decisions: "No memories found" beats misleading results, and
        // weak matches must not be shown to populate the screen.
        val results = ResultRanking.rank(
            memories(memory(1L), memory(2L)),
            keywordMatches = listOf(keyword(1L, 0.05)),
            vectorMatches = listOf(vector(2L, 0.52))
        )

        assertTrue(results.isEmpty())
    }

    @Test
    fun `a result exactly at the threshold is kept`() {
        val score = ResultRanking.MINIMUM_RELEVANCE / ResultRanking.KEYWORD_WEIGHT

        val results = ResultRanking.rank(
            memories(memory(1L)),
            listOf(keyword(1L, score)),
            emptyList()
        )

        assertEquals(1, results.size)
    }

    // --- ordering -----------------------------------------------------------

    @Test
    fun `results are ordered by descending score`() {
        val results = ResultRanking.rank(
            memories(memory(1L), memory(2L), memory(3L)),
            keywordMatches = listOf(keyword(1L, 0.4), keyword(2L, 0.9), keyword(3L, 0.6)),
            vectorMatches = emptyList()
        )

        assertEquals(listOf(2L, 3L, 1L), results.map { it.memory.id })
    }

    @Test
    fun `equal scores are broken by recency, so ordering is stable`() {
        // An arbitrary tiebreak would make identical searches return different
        // orders, which looks like a bug to the user.
        val older = memory(1L, createdAt = Instant.parse("2026-01-01T10:00:00Z"))
        val newer = memory(2L, createdAt = Instant.parse("2026-08-01T10:00:00Z"))

        val results = ResultRanking.rank(
            memories(older, newer),
            keywordMatches = listOf(keyword(1L, 0.8), keyword(2L, 0.8)),
            vectorMatches = emptyList()
        )

        assertEquals(listOf(2L, 1L), results.map { it.memory.id })
    }

    // --- component scores are preserved -------------------------------------

    @Test
    fun `component scores are carried through for diagnosis and snippets`() {
        val results = ResultRanking.rank(
            memories(memory(1L)),
            listOf(keyword(1L, 0.8, text = "the indexed document")),
            listOf(vector(1L, 0.9))
        )

        val result = results.first()
        assertEquals(0.8, result.keywordScore, 0.001)
        assertEquals(0.9, result.semanticScore, 0.001)
        assertEquals("the indexed document", result.matchedText)
    }

    @Test
    fun `a semantic-only result has no matched text to highlight`() {
        val results = ResultRanking.rank(
            memories(memory(1L)),
            emptyList(),
            listOf(vector(1L, 0.95))
        )

        assertNull(results.first().matchedText)
        assertTrue(results.first().isSemanticOnly)
    }

    // --- missing memories ---------------------------------------------------

    @Test
    fun `a candidate with no memory behind it is skipped`() {
        // The index outliving its row. There is nothing to show, so skip rather
        // than fabricate a result.
        val results = ResultRanking.rank(
            memories(memory(1L)),
            keywordMatches = listOf(keyword(1L, 0.9), keyword(999L, 1.0)),
            vectorMatches = emptyList()
        )

        assertEquals(listOf(1L), results.map { it.memory.id })
    }

    @Test
    fun `no candidates returns empty`() {
        assertTrue(ResultRanking.rank(emptyMap(), emptyList(), emptyList()).isEmpty())
    }
}
