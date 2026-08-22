package com.onemind.app.domain.search

/**
 * Decides whether a query is worth sending to a language model.
 *
 * The single most important thing in query understanding for how fast search
 * *feels*. Routing "Qwen3" through an LLM to be told that it means "Qwen3" costs a
 * second or more and changes nothing — and a one-word search is the most common
 * kind. The spec is explicit that simple queries must stay under 500ms total, which
 * is not achievable with a model call in the path.
 *
 * The rule is deliberately conservative in one direction: it is far worse to add a
 * second of latency to a simple query than to miss a decomposition opportunity on a
 * complex one. A query wrongly classified simple still searches correctly by
 * keyword and vector — it just does not get its constraints extracted. A query
 * wrongly classified complex makes the user wait for nothing.
 */
object QueryComplexity {

    /**
     * Word count at or below which a query is taken at face value.
     *
     * Two words are almost always a topic ("ramen recipe", "qwen benchmarks"), not
     * a sentence with structure to extract.
     */
    private const val SIMPLE_WORD_LIMIT = 2

    /**
     * Words suggesting the query has structure worth decomposing.
     *
     * Presence of any of these is what distinguishes "the AI stuff I saved from
     * Chrome last week" from "AI Chrome extension". Both mention an app; only the
     * first is asking about provenance.
     */
    private val STRUCTURE_MARKERS = setOf(
        // Provenance and possession.
        "from", "saved", "shared", "screenshot", "screenshots", "clipboard",
        // Asking, which implies a sentence rather than a topic.
        "what", "which", "show", "find", "where", "when", "did", "i",
        // Vague reference, the M14 case — "that laptop I saw".
        "that", "the", "thing", "things", "stuff", "one",
        // Time, though the parser finds these independently.
        "yesterday", "today", "week", "month", "year", "ago", "last", "recent"
    )

    /**
     * True when the query should be taken literally, with no model call.
     *
     * Short queries qualify outright. Longer ones qualify when they read as a topic
     * rather than a request — no structure markers means there is nothing for a
     * model to pull apart.
     */
    fun isSimple(query: String): Boolean {
        val words = query.trim().lowercase().split(Regex("""\s+""")).filter { it.isNotEmpty() }

        if (words.isEmpty()) return true
        if (words.size <= SIMPLE_WORD_LIMIT) return true

        return words.none { word -> word.trim('.', ',', '?', '!', '\'') in STRUCTURE_MARKERS }
    }
}
