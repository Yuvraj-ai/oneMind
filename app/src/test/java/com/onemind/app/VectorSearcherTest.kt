package com.onemind.app

import com.onemind.app.domain.model.MemoryEmbedding
import com.onemind.app.domain.processing.EmbeddingGenerator
import com.onemind.app.domain.repository.DerivedDataRepository
import com.onemind.app.domain.search.VectorSearcher
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class VectorSearcherTest {

    private lateinit var generator: EmbeddingGenerator
    private lateinit var repository: DerivedDataRepository
    private lateinit var searcher: VectorSearcher

    private val model = "universal_sentence_encoder"

    @Before
    fun setup() {
        // A real interface implementation rather than a mock, so the genuine
        // cosine similarity runs. Mocking similarity() would test the mock.
        generator = spyGenerator()
        repository = mockk()
        searcher = VectorSearcher(generator, repository)
    }

    /**
     * Stand-in generator whose embedding of a string is deterministic and whose
     * similarity is the interface's real implementation.
     */
    private fun spyGenerator(
        ready: Boolean = true,
        modelId: String = model,
        embedder: (String) -> Result<FloatArray> = { text -> Result.success(vectorFor(text)) }
    ): EmbeddingGenerator = object : EmbeddingGenerator {
        override val isReady = ready
        override val dimensions = 4
        override val modelId = modelId
        override suspend fun load() = Result.success(Unit)
        override suspend fun unload() {}
        override suspend fun embed(text: String) = embedder(text)
    }

    /** Fixed vectors so similarity relationships are known in advance. */
    private fun vectorFor(text: String): FloatArray = when (text) {
        "ai on phones" -> floatArrayOf(1f, 0f, 0f, 0f)
        "cooking" -> floatArrayOf(0f, 1f, 0f, 0f)
        else -> floatArrayOf(0.5f, 0.5f, 0f, 0f)
    }

    private fun embedding(
        id: Long,
        vector: FloatArray,
        modelId: String = model
    ) = MemoryEmbedding(
        memoryId = id, vector = vector, dimensions = vector.size, modelId = modelId
    )

    private fun corpus(vararg embeddings: MemoryEmbedding) {
        coEvery { repository.getAllEmbeddings() } returns embeddings.toList()
    }

    // --- similarity ranking -------------------------------------------------

    @Test
    fun `an identical vector scores near one`() = runTest {
        corpus(embedding(1L, floatArrayOf(1f, 0f, 0f, 0f)))

        val results = searcher.search("ai on phones")

        assertEquals(1, results.size)
        assertEquals(1.0, results.first().score, 0.001)
    }

    @Test
    fun `an orthogonal vector is excluded as irrelevant`() = runTest {
        // Cosine 0 — no shared meaning at all.
        corpus(embedding(1L, floatArrayOf(0f, 1f, 0f, 0f)))

        assertTrue(searcher.search("ai on phones").isEmpty())
    }

    @Test
    fun `results are ordered most similar first`() = runTest {
        corpus(
            embedding(1L, floatArrayOf(0.7f, 0.7f, 0f, 0f)),  // ~0.707
            embedding(2L, floatArrayOf(1f, 0f, 0f, 0f)),      // 1.0
            embedding(3L, floatArrayOf(0.9f, 0.4f, 0f, 0f))   // ~0.914
        )

        val results = searcher.search("ai on phones")

        assertEquals(listOf(2L, 3L, 1L), results.map { it.memoryId })
    }

    @Test
    fun `a negative cosine is clamped to zero rather than ranked below nothing`() = runTest {
        // Cosine runs [-1, 1]. "Opposed" is just "irrelevant" for retrieval, and a
        // negative score would break blending with the keyword score in #28.
        corpus(embedding(1L, floatArrayOf(-1f, 0f, 0f, 0f)))

        val results = searcher.search("ai on phones", minSimilarity = 0.0)

        results.forEach { assertTrue("score ${it.score} below zero", it.score >= 0.0) }
    }

    @Test
    fun `scores stay within zero and one`() = runTest {
        corpus(
            embedding(1L, floatArrayOf(1f, 0f, 0f, 0f)),
            embedding(2L, floatArrayOf(0.5f, 0.5f, 0f, 0f))
        )

        searcher.search("ai on phones", minSimilarity = 0.0).forEach {
            assertTrue("score ${it.score} out of range", it.score in 0.0..1.0)
        }
    }

    // --- the threshold ------------------------------------------------------

    @Test
    fun `matches below the threshold are dropped`() = runTest {
        corpus(embedding(1L, floatArrayOf(0.5f, 0.5f, 0f, 0f)))  // ~0.707

        assertTrue(searcher.search("ai on phones", minSimilarity = 0.9).isEmpty())
    }

    @Test
    fun `nothing above the threshold returns empty rather than the least bad match`() = runTest {
        corpus(
            embedding(1L, floatArrayOf(0f, 1f, 0f, 0f)),
            embedding(2L, floatArrayOf(0f, 0f, 1f, 0f))
        )

        assertTrue(searcher.search("ai on phones").isEmpty())
    }

    // --- model versioning ---------------------------------------------------

    @Test
    fun `vectors from a different model are skipped, not compared`() = runTest {
        // A vector from another model describes a different space. Comparing across
        // models produces a number that looks like a similarity and means nothing.
        corpus(
            embedding(1L, floatArrayOf(1f, 0f, 0f, 0f), modelId = "some_older_model"),
            embedding(2L, floatArrayOf(1f, 0f, 0f, 0f), modelId = model)
        )

        val results = searcher.search("ai on phones")

        assertEquals(listOf(2L), results.map { it.memoryId })
    }

    @Test
    fun `a corpus entirely from an old model returns empty`() = runTest {
        corpus(embedding(1L, floatArrayOf(1f, 0f, 0f, 0f), modelId = "old"))

        assertTrue(searcher.search("ai on phones").isEmpty())
    }

    @Test
    fun `a dimension mismatch is skipped rather than throwing`() = runTest {
        // Should be impossible given the model filter, but a stored vector is data
        // on disk: a mismatch means something is already wrong, and skipping beats
        // crashing a search.
        corpus(
            embedding(1L, floatArrayOf(1f, 0f)),                 // wrong size
            embedding(2L, floatArrayOf(1f, 0f, 0f, 0f))          // right size
        )

        val results = searcher.search("ai on phones")

        assertEquals(listOf(2L), results.map { it.memoryId })
    }

    // --- degrading rather than failing --------------------------------------

    @Test
    fun `an unavailable model returns empty rather than throwing`() = runTest {
        searcher = VectorSearcher(
            spyGenerator(ready = false, embedder = { Result.failure(IllegalStateException("no model")) }),
            repository
        )
        corpus(embedding(1L, floatArrayOf(1f, 0f, 0f, 0f)))

        assertTrue(searcher.search("ai on phones").isEmpty())
    }

    @Test
    fun `a failed embedding returns empty`() = runTest {
        searcher = VectorSearcher(
            spyGenerator(embedder = { Result.failure(RuntimeException("embed failed")) }),
            repository
        )
        corpus(embedding(1L, floatArrayOf(1f, 0f, 0f, 0f)))

        assertTrue(searcher.search("ai on phones").isEmpty())
    }

    @Test
    fun `an empty corpus returns empty`() = runTest {
        corpus()

        assertTrue(searcher.search("ai on phones").isEmpty())
    }

    @Test
    fun `a blank query does not touch the corpus`() = runTest {
        assertTrue(searcher.search("   ").isEmpty())
    }

    // --- limit --------------------------------------------------------------

    @Test
    fun `results are capped at the limit`() = runTest {
        corpus(*(1L..100L).map { embedding(it, floatArrayOf(1f, 0f, 0f, 0f)) }.toTypedArray())

        assertEquals(10, searcher.search("ai on phones", limit = 10).size)
    }

    // --- the property that makes this worth having --------------------------

    @Test
    fun `text with no words in common can still match on meaning`() = runTest {
        // The whole point of semantic search. The query and the Memory share no
        // vocabulary, but their vectors are close.
        corpus(embedding(42L, floatArrayOf(1f, 0f, 0f, 0f)))

        val results = searcher.search("ai on phones")

        assertEquals(42L, results.single().memoryId)
    }
}
