package com.onemind.app.domain.search

import com.onemind.app.domain.model.SourceType
import com.onemind.app.domain.processing.TextGenerator
import com.onemind.app.domain.repository.DerivedDataRepository
import javax.inject.Inject

/**
 * Turns a natural-language query into a [SearchIntent].
 *
 * This is what lets the search box replace a filter UI. The locked decisions
 * prohibit manual filter controls in search, on the grounds that a user should be
 * able to write "the AI stuff I saved from Chrome last week" and have the system
 * work out that this is a topic, a source, and a time range. That promise is only
 * kept if something does the working-out.
 *
 * ## Two paths, and why
 *
 * Simple queries never reach a model. See [QueryComplexity]: a one-word search must
 * feel instant, and a model call cannot fit in that budget.
 *
 * Complex queries are decomposed by the configured provider, with every asserted
 * constraint validated against a closed set before it is believed.
 *
 * ## Search never depends on a provider
 *
 * No provider, a failed call, a timeout, and unreadable output all fall back to the
 * literal query. That is not politeness — a hard filter derived from a bad
 * decomposition silently removes the Memory the user wants and tells them "no
 * results", which is worse than not decomposing at all. Keyword and vector search
 * both work offline and unaided, so the fallback is a complete search rather than a
 * degraded one.
 */
class QueryUnderstanding @Inject constructor(
    private val textGenerator: TextGenerator,
    private val temporalParser: TemporalExpressionParser,
    private val derivedDataRepository: DerivedDataRepository
) {

    suspend fun understand(rawQuery: String): SearchIntent {
        val query = rawQuery.trim()
        if (query.isEmpty()) return SearchIntent.literal("")

        // Resolved by rule, before and independently of any model call. Time
        // expressions are a closed set, so a model cannot do this better and can
        // hallucinate a date. Runs for simple queries too — "yesterday" is one word.
        val temporal = temporalParser.parse(query)

        // The temporal words are removed from the semantic query but left in the
        // keyword query. Embedding "from last week" drags the vector toward the
        // language of asking about time; keyword matching is indifferent to it, and
        // those same words might legitimately appear in content.
        val semanticBase = temporal
            ?.let { query.replace(it.matchedText, " ").replace(Regex("""\s+"""), " ").trim() }
            ?.ifBlank { query }
            ?: query

        if (QueryComplexity.isSimple(query) || !textGenerator.isAvailable()) {
            return SearchIntent(
                keywordQuery = query,
                semanticQuery = semanticBase,
                temporal = temporal,
                wasDecomposed = false
            )
        }

        return decompose(query, semanticBase, temporal)
    }

    private suspend fun decompose(
        query: String,
        semanticBase: String,
        temporal: TemporalExpression?
    ): SearchIntent {
        val fallback = SearchIntent(
            keywordQuery = query,
            semanticQuery = semanticBase,
            temporal = temporal,
            wasDecomposed = false
        )

        // Loaded before prompting so the model is offered exactly the names the
        // database holds, and the reply is matched against that same set — the same
        // structural guarantee as #14 rather than a request to behave.
        val vocabulary = runCatching { derivedDataRepository.getAllCategories() }
            .getOrElse { return fallback }

        val response = textGenerator
            .generate(buildPrompt(query, vocabulary.map { it.name }), MAX_RESPONSE_TOKENS)
            .getOrElse { return fallback }

        val decomposition = QueryDecompositionParser.parse(response) ?: return fallback

        return SearchIntent(
            keywordQuery = query,
            // A model's rewrite is only accepted if it left something to search. An
            // empty rewrite would otherwise blank the semantic query entirely.
            semanticQuery = decomposition.semanticQuery?.ifBlank { null } ?: semanticBase,
            temporal = temporal,
            sourceType = decomposition.sourceType,
            sourcePackage = decomposition.sourcePackage,
            categories = QueryDecompositionParser.resolveCategories(
                decomposition.categoryNames,
                vocabulary
            ),
            wasDecomposed = true
        )
    }

    private fun buildPrompt(query: String, categoryNames: List<String>): String = """
        A user is searching their personal collection of saved memories. Read their
        query and separate what they are looking for from how they are narrowing it
        down.

        Reply with only this JSON object:
        {
          "semantic": "the subject they are looking for, with words about source and time removed",
          "sourceType": "one of MANUAL, SCREENSHOT, SHARE, CLIPBOARD, or omit",
          "sourcePackage": "android package name if they named an app, or omit",
          "categories": ["names copied exactly from the list below, or omit"]
        }

        Rules:
        - "semantic" should read as a topic, not a request. For "show me the AI
          stuff I saved from Chrome last week" it is "AI".
        - Set "sourceType" only when they are describing where a memory came from.
          A query about a Chrome extension is about the subject, not the source.
        - Use only category names from the list, copied exactly. Never invent one.
        - Omit any field you are unsure about. A missing field is better than a
          wrong one, because these narrow the search.
        - Do not include time expressions. Those are handled separately.

        Categories:
        ${categoryNames.joinToString("\n") { "- $it" }}

        Query:
        $query
    """.trimIndent()

    companion object {
        /** A small JSON object needs little room. */
        const val MAX_RESPONSE_TOKENS = 256
    }
}
