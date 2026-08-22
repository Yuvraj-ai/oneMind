package com.onemind.app.domain.processing.stages

import com.onemind.app.domain.model.CategorizationResult
import com.onemind.app.domain.model.Category
import com.onemind.app.domain.model.Memory
import com.onemind.app.domain.processing.*
import com.onemind.app.domain.repository.DerivedDataRepository
import javax.inject.Inject

/**
 * Files a Memory under categories from the controlled vocabulary.
 *
 * The division of labour is the point of this stage: **the application owns the
 * taxonomy, the model only performs assignment**. A model is good at judging that
 * a screenshot of a recipe belongs under "Food & Cooking" and bad at being
 * consistent about what to call that category across a thousand Memories. So it
 * is given a closed list and asked to choose from it.
 *
 * The guarantee is structural rather than a matter of prompt obedience: the set of
 * names offered to the model is the same set the response is matched against, so
 * an invented category has nothing to match and is discarded. A model that ignores
 * every instruction still cannot introduce a category.
 */
class CategorizationStage @Inject constructor(
    private val textGenerator: TextGenerator,
    private val derivedDataRepository: DerivedDataRepository
) : ProcessingStage {

    override val id = StageId.CATEGORIZATION

    override suspend fun process(memory: Memory): StageResult {
        val input = BoundedAnalysisInput.from(memory)
        if (input.isEmpty) return StageResult.Skipped

        // Loaded rather than read from the dictionary constant, so what is offered
        // is exactly what the database will accept as a foreign key.
        val vocabulary = derivedDataRepository.getAllCategories()
        if (vocabulary.isEmpty()) return StageResult.Skipped

        if (!textGenerator.isAvailable()) {
            record(memory.id, StageStatus.NOT_SUPPORTED, null)
            return StageResult.NotSupported
        }

        val generated = textGenerator.generate(
            prompt = buildPrompt(input, vocabulary),
            maxTokens = MAX_RESPONSE_TOKENS
        )

        val response = generated.getOrElse { error ->
            record(memory.id, StageStatus.FAILED, null)
            return StageResult.Failed(error.message ?: "Categorization failed", error)
        }

        val model = textGenerator.modelIdentifier()
        val chosen = match(response, vocabulary)

        // Written even when empty, so a reprocess that now finds nothing clears a
        // previous run's answer rather than leaving it behind.
        derivedDataRepository.saveCategories(memory.id, chosen.map { it.id })

        if (chosen.isEmpty()) {
            record(memory.id, StageStatus.EMPTY, model)
            return StageResult.Empty
        }

        record(memory.id, StageStatus.SUCCESS, model)
        return StageResult.Success
    }

    private suspend fun record(memoryId: Long, status: StageStatus, model: String?) {
        derivedDataRepository.saveCategorizationResult(
            CategorizationResult(memoryId = memoryId, status = status, providerModel = model)
        )
    }

    /**
     * Resolve a model's reply to categories that actually exist.
     *
     * Parsing is deliberately permissive and validation is strict. Small models
     * reply in whatever shape they like — a JSON array, a bullet list, a bare
     * comma-separated line, sometimes wrapped in a code fence — and being fussy
     * about the shape would throw away correct answers. Being permissive is safe
     * *because* the matching step is a closed-set lookup: over-reading the
     * response can only ever produce tokens that fail to match.
     *
     * Matching is exact after trimming and case-folding. Nothing fuzzy: a
     * near-miss like "AI stuff" must fail rather than be talked into "AI &
     * Machine Learning", because approximate matching is how a model smuggles in
     * a category by another name.
     */
    private fun match(response: String, vocabulary: List<Category>): List<Category> {
        val byName = vocabulary.associateBy { it.name.trim().lowercase() }

        return response
            .replace("```", "\n")
            .replace(Regex("""[\[\]{}"']"""), "\n")
            .split('\n', ',')
            .map { it.trim().removePrefix("-").removePrefix("*").trim() }
            // Strip an ordered-list marker such as "1." or "2)".
            .map { it.replace(Regex("""^\d+[.)]\s*"""), "").trim() }
            .mapNotNull { byName[it.lowercase()] }
            .distinct()
            .take(MAX_CATEGORIES)
    }

    private fun buildPrompt(
        input: BoundedAnalysisInput,
        vocabulary: List<Category>
    ): String {
        val list = vocabulary.joinToString("\n") { "- ${it.name}" }

        return """
            Below is a saved memory from someone's personal collection, and a fixed
            list of categories.

            Choose every category from the list that the memory belongs to. Choose
            at most $MAX_CATEGORIES, and prefer the few that fit best.

            Rules:
            - Use only names from the list, copied exactly as written.
            - Do not invent a category, and do not adapt a name to fit better.
            - If nothing in the list fits, reply with nothing at all.
            - Reply with one category name per line. No preamble, no explanation.

            Categories:
            $list

            Memory:
            ${input.text}
        """.trimIndent()
    }

    companion object {
        /**
         * Ceiling on categories per Memory.
         *
         * A Memory filed under twenty categories is as useless for narrowing a
         * feed as one filed under none, and a model that returns the entire list
         * has effectively failed. Capping contains that rather than storing it.
         */
        const val MAX_CATEGORIES = 5

        /** A short list of names needs very little room. */
        const val MAX_RESPONSE_TOKENS = 128
    }
}
