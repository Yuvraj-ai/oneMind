package com.onemind.app.domain.search

/**
 * Turns whatever the user typed into a safe FTS4 MATCH expression.
 *
 * ## Why this is not optional
 *
 * FTS4's MATCH grammar treats `"`, `*`, `:`, `^`, `(`, `)`, `-` and the bare
 * uppercase words `AND`, `OR`, `NOT`, `NEAR` as syntax. A user typing an
 * apostrophe, a colon, or the word "OR" is not attempting an injection — they are
 * typing normally — but passing it through raw produces a SQLite syntax error,
 * which surfaces as a crash on a keystroke. Search that crashes when you type
 * `don't` is worse than no search.
 *
 * The approach is allow-listing rather than escaping. Escaping means enumerating
 * every character SQLite considers special and staying correct as that set
 * changes; allow-listing means deciding what a search term *is*. Anything outside
 * letters, digits, and the few characters that appear inside real search terms is
 * discarded.
 *
 * ## Why OR rather than AND
 *
 * Terms are joined with OR, not AND. With AND, a single typo in a multi-word query
 * returns nothing — "reciepe ramen" would find neither, even though "ramen" alone
 * would have found the right Memory. Since oneMind exists for people who half-remember
 * what they saved, a query where most words are right must still work. Relevance
 * ordering, not exclusion, is what separates good matches from weak ones, and that
 * is [KeywordScoring]'s job.
 */
object FtsQuery {

    /**
     * Characters kept inside a term.
     *
     * Letters and digits only. Dots are **separators**, not term characters, which
     * matters more than it looks: the FTS4 table uses SQLite's `simple` tokeniser,
     * which splits on every non-alphanumeric character. It stores "node.js" as two
     * tokens, `node` and `js`. If this kept the dot, a query for `js` would be
     * matched by FTS and then scored zero by [KeywordScoring] against the term
     * `node.js`, and the row would be dropped — found by the index, discarded by our
     * own arithmetic. The two tokenisers have to agree.
     */
    private val ALLOWED = Regex("""[^\p{L}\p{N}]+""")

    /** Below this, a term matches so much that it is noise rather than a filter. */
    private const val MIN_TERM_LENGTH = 2

    /**
     * Words dropped from queries.
     *
     * Not premature tuning — a consequence of the product being natural-language
     * first. The locked decisions centre search on queries like "show me the AI
     * stuff I saved from Chrome last week", which is mostly words that carry no
     * information about which Memory is wanted. Left in, each one counts toward
     * coverage exactly as much as "AI" does, so a Memory containing "the" and
     * "from" would score two-tenths of a perfect match while being irrelevant.
     *
     * Prefix matching makes it worse rather than better: `or*` matches "order",
     * "organic", and "original", so a stopword does not merely add noise, it adds
     * noise that hits almost every document.
     *
     * Filtered from queries only. Documents keep their stopwords, because scoring
     * only ever looks for query terms and stripping them from stored text would
     * mean reindexing to change this list.
     */
    private val STOPWORDS = setOf(
        // Articles, conjunctions, prepositions.
        "the", "and", "or", "not", "but", "for", "nor", "yet", "so",
        "of", "in", "on", "at", "to", "from", "by", "with", "about",
        "into", "over", "after", "before", "up", "down", "out", "off",
        // Pronouns and possessives that show up in spoken-style queries.
        "me", "my", "mine", "you", "your", "it", "its", "that", "this",
        "these", "those", "there", "here", "them", "they",
        // Verbs of asking and saving, which appear in nearly every such query.
        "is", "are", "was", "were", "be", "been", "am",
        "do", "did", "does", "have", "has", "had",
        "show", "find", "get", "want", "need", "see", "saw", "seen",
        "save", "saved", "saw", "look", "looking", "search",
        // Interrogatives.
        "what", "which", "who", "when", "where", "why", "how",
        // Filler.
        "some", "any", "all", "thing", "things", "stuff", "one", "ones"
    )

    /**
     * Tokenise text into terms, preserving repeats.
     *
     * Lowercasing serves two purposes: FTS4's default tokeniser is
     * case-insensitive so nothing is lost, and it neutralises the operators, which
     * FTS4 only recognises in uppercase. A user typing "AI OR ML" gets three
     * ordinary terms rather than a parse error.
     *
     * Repeats are kept because scoring a *document* needs to know how often a term
     * occurs. Callers building a query want [terms] instead.
     */
    fun tokenize(raw: String): List<String> =
        raw.lowercase()
            .split(ALLOWED)
            .filter { it.length >= MIN_TERM_LENGTH }

    /**
     * Distinct, information-bearing terms, for building a query.
     *
     * Deduplicated because searching "ramen ramen" should not weight the term
     * twice, and stopword-filtered so a conversational query is scored on the words
     * that identify a Memory rather than the words that carry the sentence.
     */
    fun terms(raw: String): List<String> =
        tokenize(raw).filterNot { it in STOPWORDS }.distinct()

    /**
     * Terms of a *document*, for scoring.
     *
     * Keeps stopwords, unlike [terms]. Scoring only ever looks for the query's
     * terms, so a stopword in a document is never consulted — and stripping them at
     * index time would mean reindexing every Memory to revise the list.
     */
    fun documentTerms(raw: String): List<String> = tokenize(raw)

    /**
     * Build a MATCH expression, or null when the query has nothing usable in it.
     *
     * Null is meaningfully different from an empty string: it tells the caller not
     * to run a query at all, rather than to run one that matches nothing. A user
     * who has typed only punctuation should see their feed, not "no results".
     *
     * Every term gets a `*` suffix so results appear while the user is still
     * typing — "ram" finds "ramen" — which is what makes the search feel live
     * rather than requiring a complete word before anything happens.
     */
    fun build(raw: String): String? {
        val terms = terms(raw)
        if (terms.isEmpty()) return null
        return terms.joinToString(" OR ") { "$it*" }
    }
}
