package com.onemind.app

import com.onemind.app.domain.model.Category
import com.onemind.app.domain.model.SourceType
import com.onemind.app.domain.processing.TextGenerator
import com.onemind.app.domain.repository.DerivedDataRepository
import com.onemind.app.domain.search.QueryComplexity
import com.onemind.app.domain.search.QueryUnderstanding
import com.onemind.app.domain.search.TemporalExpressionParser
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class QueryUnderstandingTest {

    private lateinit var textGenerator: TextGenerator
    private lateinit var repository: DerivedDataRepository
    private lateinit var understanding: QueryUnderstanding

    private val promptSlot = slot<String>()

    private val vocabulary = listOf(
        Category(id = 1L, name = "Technology"),
        Category(id = 2L, name = "AI & Machine Learning"),
        Category(id = 3L, name = "Food & Cooking")
    )

    @Before
    fun setup() {
        textGenerator = mockk()
        repository = mockk()
        understanding = QueryUnderstanding(
            textGenerator,
            TemporalExpressionParser(),
            repository
        )

        every { textGenerator.isAvailable() } returns true
        coEvery { repository.getAllCategories() } returns vocabulary
    }

    private fun modelReturns(response: String) {
        coEvery { textGenerator.generate(capture(promptSlot), any()) } returns
            Result.success(response)
    }

    // --- the decision that matters most for perceived speed ------------------

    @Test
    fun `a one-word query never reaches the model`() = runTest {
        // Routing "Qwen3" through an LLM to be told it means "Qwen3" costs a second
        // for nothing, and a one-word search is the most common kind.
        val intent = understanding.understand("Qwen3")

        coVerify(exactly = 0) { textGenerator.generate(any(), any()) }
        assertFalse(intent.wasDecomposed)
        assertEquals("Qwen3", intent.keywordQuery)
    }

    @Test
    fun `a two-word topic never reaches the model`() = runTest {
        understanding.understand("ramen recipe")

        coVerify(exactly = 0) { textGenerator.generate(any(), any()) }
    }

    @Test
    fun `a longer query with no structure markers never reaches the model`() = runTest {
        // Reads as a topic, not a request. Nothing for a model to pull apart.
        understanding.understand("quantized llm inference android")

        coVerify(exactly = 0) { textGenerator.generate(any(), any()) }
    }

    @Test
    fun `a conversational query does reach the model`() = runTest {
        modelReturns("""{"semantic": "AI"}""")

        val intent = understanding.understand("show me the AI stuff I saved from Chrome")

        coVerify(exactly = 1) { textGenerator.generate(any(), any()) }
        assertTrue(intent.wasDecomposed)
    }

    @Test
    fun `an empty query is taken literally without a model call`() = runTest {
        val intent = understanding.understand("")

        coVerify(exactly = 0) { textGenerator.generate(any(), any()) }
        assertEquals("", intent.keywordQuery)
    }

    // --- temporal parsing happens by rule, for every query ------------------

    @Test
    fun `a simple temporal query gets its range without a model call`() = runTest {
        // "yesterday" is one word. Time is a closed set, so a rule beats a model
        // here and cannot hallucinate a date.
        val intent = understanding.understand("yesterday")

        coVerify(exactly = 0) { textGenerator.generate(any(), any()) }
        assertNotNull(intent.temporal)
    }

    @Test
    fun `temporal words are stripped from the semantic query but kept for keywords`() = runTest {
        modelReturns("""{"semantic": "AI"}""")

        val intent = understanding.understand("AI stuff I saved from last week")

        // Keyword search keeps them: those words might appear in content.
        assertTrue(intent.keywordQuery.contains("last week"))
        // Semantic search does not: embedding them drags the vector toward the
        // language of asking about time.
        assertFalse(intent.semanticQuery.contains("last week"))
    }

    @Test
    fun `stripping temporal words does not blank the semantic query`() = runTest {
        // A query that is *only* a time expression would otherwise embed nothing.
        val intent = understanding.understand("yesterday")

        assertTrue(intent.semanticQuery.isNotBlank())
    }

    // --- decomposition ------------------------------------------------------

    @Test
    fun `a source type is extracted`() = runTest {
        modelReturns("""{"semantic": "AI", "sourceType": "SHARE"}""")

        val intent = understanding.understand("show me the AI stuff I saved from Chrome")

        assertEquals(SourceType.SHARE, intent.sourceType)
    }

    @Test
    fun `a source package is extracted`() = runTest {
        modelReturns("""{"semantic": "AI", "sourcePackage": "com.android.chrome"}""")

        val intent = understanding.understand("show me the AI stuff I saved from Chrome")

        assertEquals("com.android.chrome", intent.sourcePackage)
    }

    @Test
    fun `the model's semantic rewrite is used`() = runTest {
        modelReturns("""{"semantic": "artificial intelligence"}""")

        val intent = understanding.understand("show me that AI stuff I saved")

        assertEquals("artificial intelligence", intent.semanticQuery)
    }

    @Test
    fun `all four dimensions are extracted together`() = runTest {
        // The query the locked decisions use as their example.
        modelReturns(
            """{"semantic": "AI", "sourceType": "SHARE",
                "sourcePackage": "com.android.chrome",
                "categories": ["AI & Machine Learning"]}"""
        )

        val intent = understanding.understand("show me the AI stuff I saved from Chrome last week")

        assertEquals("AI", intent.semanticQuery)
        assertEquals(SourceType.SHARE, intent.sourceType)
        assertEquals("com.android.chrome", intent.sourcePackage)
        assertEquals(listOf("AI & Machine Learning"), intent.categories.map { it.name })
        assertNotNull("temporal should be parsed by rule", intent.temporal)
    }

    // --- the closed vocabulary guarantee ------------------------------------

    @Test
    fun `an invented category is discarded`() = runTest {
        // Same guarantee as #14: the reply is matched against the set that was
        // offered, so an invented name has nothing to match.
        modelReturns("""{"semantic": "x", "categories": ["Quantum Basketweaving"]}""")

        val intent = understanding.understand("show me that thing I saved")

        assertTrue(intent.categories.isEmpty())
    }

    @Test
    fun `a real category alongside an invented one keeps only the real one`() = runTest {
        modelReturns("""{"semantic": "x", "categories": ["Technology", "Nonsense"]}""")

        val intent = understanding.understand("show me that thing I saved")

        assertEquals(listOf("Technology"), intent.categories.map { it.name })
    }

    @Test
    fun `category matching folds case`() = runTest {
        modelReturns("""{"semantic": "x", "categories": ["technology"]}""")

        val intent = understanding.understand("show me that thing I saved")

        assertEquals(listOf("Technology"), intent.categories.map { it.name })
    }

    @Test
    fun `an invented source type is discarded rather than becoming a filter`() = runTest {
        // A bad source filter excludes everything and tells the user "no results".
        modelReturns("""{"semantic": "x", "sourceType": "TELEPATHY"}""")

        val intent = understanding.understand("show me that thing I saved")

        assertNull(intent.sourceType)
    }

    @Test
    fun `the prompt offers the vocabulary from the database`() = runTest {
        modelReturns("""{"semantic": "x"}""")

        understanding.understand("show me that thing I saved")

        vocabulary.forEach {
            assertTrue("prompt omitted ${it.name}", promptSlot.captured.contains(it.name))
        }
    }

    @Test
    fun `the prompt forbids inventing a category`() = runTest {
        modelReturns("""{"semantic": "x"}""")

        understanding.understand("show me that thing I saved")

        assertTrue(promptSlot.captured.lowercase().contains("never invent"))
    }

    // --- search never depends on a provider ---------------------------------

    @Test
    fun `with no provider the query is taken literally`() = runTest {
        every { textGenerator.isAvailable() } returns false

        val intent = understanding.understand("show me the AI stuff I saved from Chrome")

        assertFalse(intent.wasDecomposed)
        assertEquals("show me the AI stuff I saved from Chrome", intent.keywordQuery)
        coVerify(exactly = 0) { textGenerator.generate(any(), any()) }
    }

    @Test
    fun `with no provider temporal parsing still works`() = runTest {
        // It is rule-based, so it owes nothing to the provider.
        every { textGenerator.isAvailable() } returns false

        val intent = understanding.understand("what did I save yesterday")

        assertNotNull(intent.temporal)
    }

    @Test
    fun `a provider error falls back to the literal query`() = runTest {
        coEvery { textGenerator.generate(any(), any()) } returns
            Result.failure(RuntimeException("rate limited"))

        val intent = understanding.understand("show me the AI stuff I saved from Chrome")

        assertFalse(intent.wasDecomposed)
        assertEquals("show me the AI stuff I saved from Chrome", intent.keywordQuery)
    }

    @Test
    fun `a malformed response falls back rather than being partly trusted`() = runTest {
        modelReturns("I'm not sure what you mean.")

        val intent = understanding.understand("show me the AI stuff I saved from Chrome")

        assertFalse(intent.wasDecomposed)
        assertNull(intent.sourceType)
    }

    @Test
    fun `truncated JSON falls back`() = runTest {
        modelReturns("""{"semantic": "AI", "sourceTy""")

        val intent = understanding.understand("show me the AI stuff I saved from Chrome")

        assertFalse(intent.wasDecomposed)
    }

    @Test
    fun `an empty JSON object falls back rather than claiming no constraints`() = runTest {
        // "{}" is not a decomposition — it is a model that said nothing. Treating it
        // as a successful empty result would hide the difference.
        modelReturns("{}")

        val intent = understanding.understand("show me the AI stuff I saved from Chrome")

        assertFalse(intent.wasDecomposed)
    }

    @Test
    fun `a failure loading the vocabulary falls back`() = runTest {
        coEvery { repository.getAllCategories() } throws RuntimeException("db gone")

        val intent = understanding.understand("show me the AI stuff I saved from Chrome")

        assertFalse(intent.wasDecomposed)
        coVerify(exactly = 0) { textGenerator.generate(any(), any()) }
    }

    @Test
    fun `JSON wrapped in prose is still read`() = runTest {
        modelReturns("""Sure! Here's the breakdown:
            ```json
            {"semantic": "AI", "sourceType": "SHARE"}
            ```
            Hope that helps.""")

        val intent = understanding.understand("show me the AI stuff I saved from Chrome")

        assertTrue(intent.wasDecomposed)
        assertEquals(SourceType.SHARE, intent.sourceType)
    }

    @Test
    fun `an empty semantic rewrite does not blank the semantic query`() = runTest {
        modelReturns("""{"semantic": "", "sourceType": "SHARE"}""")

        val intent = understanding.understand("show me the AI stuff I saved from Chrome")

        assertTrue(intent.semanticQuery.isNotBlank())
    }

    // --- complexity classification ------------------------------------------

    @Test
    fun `complexity classification treats topics as simple and requests as complex`() {
        listOf("Qwen3", "ramen recipe", "quantized llm android", "onnx runtime mobile")
            .forEach { assertTrue("'$it' should be simple", QueryComplexity.isSimple(it)) }

        listOf(
            "show me the AI stuff",
            "what did I save yesterday",
            "that laptop I saw last week",
            "things I saved from Chrome"
        ).forEach { assertFalse("'$it' should be complex", QueryComplexity.isSimple(it)) }
    }

    @Test
    fun `punctuation does not hide a structure marker`() = runTest {
        assertFalse(QueryComplexity.isSimple("what did I save, yesterday?"))
    }

    @Test
    fun `hasConstraints reflects hard filters only`() = runTest {
        modelReturns("""{"semantic": "x", "categories": ["Technology"]}""")

        val intent = understanding.understand("show me that thing I saved")

        // Categories are a soft boost, not a filter, so they do not count.
        assertFalse(intent.hasConstraints)
    }
}
