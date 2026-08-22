package com.onemind.app

import com.onemind.app.domain.categories.CategoryDictionary
import com.onemind.app.domain.model.*
import com.onemind.app.domain.processing.StageId
import com.onemind.app.domain.processing.StageResult
import com.onemind.app.domain.processing.StageStatus
import com.onemind.app.domain.processing.TextGenerator
import com.onemind.app.domain.processing.stages.CategorizationStage
import com.onemind.app.domain.repository.DerivedDataRepository
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import kotlin.random.Random

class CategorizationStageTest {

    private lateinit var textGenerator: TextGenerator
    private lateinit var repository: DerivedDataRepository
    private lateinit var stage: CategorizationStage

    private val idsSlot = slot<List<Long>>()
    private val resultSlot = slot<CategorizationResult>()
    private val promptSlot = slot<String>()

    /** A small vocabulary, so tests state exactly what was on offer. */
    private val vocabulary = listOf(
        Category(id = 1L, name = "Technology"),
        Category(id = 2L, name = "AI & Machine Learning"),
        Category(id = 3L, name = "Travel"),
        Category(id = 4L, name = "Food & Cooking"),
        Category(id = 5L, name = "Film & TV")
    )

    @Before
    fun setup() {
        textGenerator = mockk()
        repository = mockk(relaxed = true)
        stage = CategorizationStage(textGenerator, repository)

        every { textGenerator.modelIdentifier() } returns "gpt-4o-mini"
        coEvery { repository.getAllCategories() } returns vocabulary
        coEvery { repository.saveCategories(any(), capture(idsSlot)) } just Runs
        coEvery { repository.saveCategorizationResult(capture(resultSlot)) } just Runs
    }

    private fun modelReturns(response: String) {
        every { textGenerator.isAvailable() } returns true
        coEvery { textGenerator.generate(capture(promptSlot), any()) } returns
            Result.success(response)
    }

    private fun memory(text: String = "A guide to running language models locally") =
        Memory(
            id = 1L,
            contentBlocks = listOf(
                ContentBlock(
                    id = 1L, memoryId = 1L, position = 0,
                    type = ContentType.TEXT, content = text
                )
            )
        )

    private fun assignedNames(): List<String> =
        idsSlot.captured.map { id -> vocabulary.first { it.id == id }.name }

    // --- the happy path ---------------------------------------------------

    @Test
    fun `categories the model chose are assigned`() = runTest {
        modelReturns("Technology\nAI & Machine Learning")

        val result = stage.process(memory())

        assertEquals(StageResult.Success, result)
        assertEquals(listOf("Technology", "AI & Machine Learning"), assignedNames())
    }

    @Test
    fun `a Memory may receive several categories`() = runTest {
        modelReturns("Technology\nTravel\nFood & Cooking")

        stage.process(memory())

        assertEquals(3, idsSlot.captured.size)
    }

    @Test
    fun `assignments are stored as ids, not names`() = runTest {
        // Names can be renamed; ids cannot orphan the Memories filed under them.
        modelReturns("Travel")

        stage.process(memory())

        assertEquals(listOf(3L), idsSlot.captured)
    }

    // --- the invariant: the model can never invent -------------------------

    @Test
    fun `an invented category is discarded, not created`() = runTest {
        modelReturns("Technology\nQuantum Basketweaving\nCryptocurrency")

        stage.process(memory())

        assertEquals(listOf("Technology"), assignedNames())
    }

    @Test
    fun `a response of nothing but invented categories assigns none`() = runTest {
        modelReturns("Sportsball\nUnderwater Yodelling")

        val result = stage.process(memory())

        assertEquals(StageResult.Empty, result)
        assertTrue(idsSlot.captured.isEmpty())
    }

    @Test
    fun `a near-miss is rejected rather than talked into a real category`() = runTest {
        // Fuzzy matching would be how a model smuggles in a category under
        // another name, defeating the point of a controlled vocabulary.
        modelReturns("AI stuff\nTech\nTravelling")

        stage.process(memory())

        assertTrue(
            "expected no assignments, got ${assignedNames()}",
            idsSlot.captured.isEmpty()
        )
    }

    @Test
    fun `the stage only ever offers what the database will accept`() = runTest {
        // Validation is against the loaded vocabulary, not the dictionary
        // constant, so the stage cannot accept an id that has no row.
        coEvery { repository.getAllCategories() } returns
            listOf(Category(id = 99L, name = "Only This One"))
        modelReturns("Technology\nOnly This One")

        stage.process(memory())

        assertEquals(listOf(99L), idsSlot.captured)
    }

    /**
     * The property the ticket turns on: whatever a model replies, the assigned
     * categories are a subset of the vocabulary.
     *
     * Hand-rolled generative test rather than a new dependency. The seed is fixed,
     * so a failure is reproducible.
     */
    @Test
    fun `property - assigned categories are always a subset of the vocabulary`() = runTest {
        val full = CategoryDictionary.ALL.mapIndexed { i, name ->
            Category(id = i + 1L, name = name)
        }
        coEvery { repository.getAllCategories() } returns full
        val allowedIds = full.map { it.id }.toSet()

        val random = Random(seed = 20260820)
        val noise = listOf(
            "", "   ", "null", "None of the above", "[]", "{}", "```json\n[]\n```",
            "I'm not sure", "Category: Unknown", "42", "\u0000", "<script>",
            "Technology, but also maybe not", "ALL OF THEM", "-", "* ", "1.",
            "Miscellaneous", "Other", "Uncategorised", "🎉", "a".repeat(500)
        )

        repeat(400) { iteration ->
            // A response mixing real names, mangled names, and pure noise.
            val parts = buildList {
                repeat(random.nextInt(0, 4)) {
                    add(CategoryDictionary.ALL.random(random))
                }
                repeat(random.nextInt(0, 3)) {
                    add(CategoryDictionary.ALL.random(random) + " Extra")
                }
                repeat(random.nextInt(0, 3)) { add(noise.random(random)) }
            }.shuffled(random)

            val separator = listOf("\n", ", ", "\n- ", "\", \"").random(random)
            val response = parts.joinToString(separator)

            idsSlot.clear()
            modelReturns(response)
            stage.process(memory())

            val assigned = if (idsSlot.isCaptured) idsSlot.captured else emptyList()

            assertTrue(
                "iteration $iteration produced ids outside the vocabulary. " +
                    "response=<$response> assigned=$assigned",
                assigned.all { it in allowedIds }
            )
            assertEquals(
                "iteration $iteration produced duplicate assignments",
                assigned.size, assigned.toSet().size
            )
            assertTrue(
                "iteration $iteration exceeded the cap",
                assigned.size <= CategorizationStage.MAX_CATEGORIES
            )
        }
    }

    // --- tolerating the shapes models actually reply in --------------------

    @Test
    fun `a JSON array is understood`() = runTest {
        modelReturns("""["Technology", "Travel"]""")

        stage.process(memory())

        assertEquals(listOf("Technology", "Travel"), assignedNames())
    }

    @Test
    fun `a fenced JSON block is understood`() = runTest {
        modelReturns("```json\n[\"Technology\"]\n```")

        stage.process(memory())

        assertEquals(listOf("Technology"), assignedNames())
    }

    @Test
    fun `a bulleted list is understood`() = runTest {
        modelReturns("- Technology\n- Travel")

        stage.process(memory())

        assertEquals(listOf("Technology", "Travel"), assignedNames())
    }

    @Test
    fun `a numbered list is understood`() = runTest {
        modelReturns("1. Technology\n2) Travel")

        stage.process(memory())

        assertEquals(listOf("Technology", "Travel"), assignedNames())
    }

    @Test
    fun `a comma-separated line is understood`() = runTest {
        modelReturns("Technology, Travel")

        stage.process(memory())

        assertEquals(listOf("Technology", "Travel"), assignedNames())
    }

    @Test
    fun `matching folds case`() = runTest {
        modelReturns("technology\nTRAVEL")

        stage.process(memory())

        assertEquals(listOf("Technology", "Travel"), assignedNames())
    }

    @Test
    fun `a name containing an ampersand survives parsing`() = runTest {
        // "AI & Machine Learning" and "Film & TV" must not be split on the
        // ampersand, or the richest category names would be unmatchable.
        modelReturns("AI & Machine Learning\nFilm & TV")

        stage.process(memory())

        assertEquals(listOf("AI & Machine Learning", "Film & TV"), assignedNames())
    }

    @Test
    fun `a repeated category is assigned once`() = runTest {
        // The composite primary key would reject the duplicate anyway; not
        // sending it keeps the storage layer from having to.
        modelReturns("Technology\nTechnology\ntechnology")

        stage.process(memory())

        assertEquals(listOf(1L), idsSlot.captured)
    }

    @Test
    fun `more categories than the cap are trimmed`() = runTest {
        coEvery { repository.getAllCategories() } returns
            CategoryDictionary.ALL.mapIndexed { i, n -> Category(id = i + 1L, name = n) }
        modelReturns(CategoryDictionary.ALL.joinToString("\n"))

        stage.process(memory())

        assertEquals(CategorizationStage.MAX_CATEGORIES, idsSlot.captured.size)
    }

    // --- zero categories is a legitimate answer ---------------------------

    @Test
    fun `a Memory that fits nothing receives no categories`() = runTest {
        modelReturns("")

        val result = stage.process(memory())

        assertEquals(StageResult.Empty, result)
        assertEquals(StageStatus.EMPTY, resultSlot.captured.status)
    }

    @Test
    fun `an empty answer still clears a previous run's categories`() = runTest {
        // Otherwise reprocessing a Memory whose content changed would leave it
        // filed under categories that no longer apply.
        modelReturns("")

        stage.process(memory())

        coVerify { repository.saveCategories(1L, emptyList()) }
    }

    // --- no provider ------------------------------------------------------

    @Test
    fun `with no provider the stage records NOT_SUPPORTED`() = runTest {
        every { textGenerator.isAvailable() } returns false

        val result = stage.process(memory())

        assertEquals(StageResult.NotSupported, result)
        assertEquals(StageStatus.NOT_SUPPORTED, resultSlot.captured.status)
    }

    @Test
    fun `with no provider the model is never called`() = runTest {
        every { textGenerator.isAvailable() } returns false

        stage.process(memory())

        coVerify(exactly = 0) { textGenerator.generate(any(), any()) }
    }

    @Test
    fun `with no provider existing categories are left alone`() = runTest {
        // NOT_SUPPORTED means "could not judge", which is not grounds for
        // discarding an answer a configured provider gave earlier.
        every { textGenerator.isAvailable() } returns false

        stage.process(memory())

        coVerify(exactly = 0) { repository.saveCategories(any(), any()) }
    }

    // --- failure ----------------------------------------------------------

    @Test
    fun `a model error is recorded as FAILED`() = runTest {
        every { textGenerator.isAvailable() } returns true
        coEvery { textGenerator.generate(any(), any()) } returns
            Result.failure(RuntimeException("rate limited"))

        val result = stage.process(memory())

        assertTrue(result is StageResult.Failed)
        assertEquals(StageStatus.FAILED, resultSlot.captured.status)
    }

    @Test
    fun `a failure leaves existing categories alone`() = runTest {
        every { textGenerator.isAvailable() } returns true
        coEvery { textGenerator.generate(any(), any()) } returns
            Result.failure(RuntimeException("boom"))

        stage.process(memory())

        coVerify(exactly = 0) { repository.saveCategories(any(), any()) }
    }

    // --- the prompt -------------------------------------------------------

    @Test
    fun `the prompt lists every category on offer`() = runTest {
        modelReturns("Technology")

        stage.process(memory())

        vocabulary.forEach { category ->
            assertTrue(
                "prompt omitted ${category.name}",
                promptSlot.captured.contains(category.name)
            )
        }
    }

    @Test
    fun `the prompt forbids inventing a category`() = runTest {
        modelReturns("Technology")

        stage.process(memory())

        assertTrue(promptSlot.captured.lowercase().contains("do not invent a category"))
    }

    @Test
    fun `the prompt permits choosing nothing`() = runTest {
        // Without this a model will reach for the least-bad option rather than
        // admit nothing fits, and every Memory ends up mislabelled.
        modelReturns("Technology")

        stage.process(memory())

        assertTrue(promptSlot.captured.lowercase().contains("nothing in the list fits"))
    }

    @Test
    fun `the prompt states the cap`() = runTest {
        modelReturns("Technology")

        stage.process(memory())

        assertTrue(
            promptSlot.captured.contains("at most ${CategorizationStage.MAX_CATEGORIES}")
        )
    }

    // --- nothing to categorise --------------------------------------------

    @Test
    fun `a memory with nothing in it is skipped`() = runTest {
        val result = stage.process(Memory(id = 1L))

        assertEquals(StageResult.Skipped, result)
        coVerify(exactly = 0) { repository.saveCategories(any(), any()) }
    }

    @Test
    fun `an unseeded vocabulary is skipped rather than offering an empty list`() = runTest {
        // Asking a model to choose from nothing would waste a call and could only
        // ever produce invented answers.
        coEvery { repository.getAllCategories() } returns emptyList()
        every { textGenerator.isAvailable() } returns true

        val result = stage.process(memory())

        assertEquals(StageResult.Skipped, result)
        coVerify(exactly = 0) { textGenerator.generate(any(), any()) }
    }

    @Test
    fun `the stage declares itself as CATEGORIZATION`() {
        assertEquals(StageId.CATEGORIZATION, stage.id)
    }
}
