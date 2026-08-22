package com.onemind.app.domain.search

/**
 * Pulls the part of a document that explains why it matched.
 *
 * A result card that shows the opening words of a Memory tells the user nothing
 * about why the search found it. If they searched "tonkotsu" and the match came
 * from OCR text buried in the fourth screenshot, the useful thing to show is that
 * text — otherwise they have to open every result to find out which one was right,
 * which is the work the search was supposed to save them.
 *
 * Pure text arithmetic, kept out of the composable so the awkward cases can be
 * tested directly.
 */
object SnippetExtractor {

    /** Target length. A card has room for roughly two lines. */
    const val TARGET_LENGTH = 120

    /** Ellipsis used when either end is cut. */
    private const val ELLIPSIS = "\u2026"

    /**
     * A snippet and the ranges within it that matched.
     *
     * Ranges are returned rather than pre-marked text so the caller decides how to
     * render emphasis. Offsets are into [text], so they stay valid regardless of any
     * leading ellipsis.
     */
    data class Snippet(
        val text: String,
        val highlights: List<IntRange>
    )

    /**
     * Build a snippet from [document] centred on the first [queryTerms] match.
     *
     * Returns null when nothing matches, which tells the caller to fall back to the
     * summary rather than show an arbitrary opening fragment.
     */
    fun extract(
        document: String,
        queryTerms: List<String>,
        targetLength: Int = TARGET_LENGTH
    ): Snippet? {
        if (document.isBlank() || queryTerms.isEmpty()) return null

        // Newlines separate index sections; on a card they would read as a broken
        // paragraph, so they collapse to spaces. Done before locating matches so
        // offsets refer to the string actually displayed.
        val flat = document.replace(Regex("""\s+"""), " ").trim()
        if (flat.isEmpty()) return null

        val matches = findMatches(flat, queryTerms)
        if (matches.isEmpty()) return null

        val window = windowAround(matches.first(), flat, targetLength)

        val prefix = if (window.first > 0) ELLIPSIS else ""
        val suffix = if (window.last < flat.length - 1) ELLIPSIS else ""
        val body = flat.substring(window.first, window.last + 1)
        val text = prefix + body + suffix

        // Shifted into the returned string's coordinates, and clipped to matches that
        // survive the window. A highlight running past the end would crash the
        // renderer, so partial matches at the boundary are dropped rather than
        // truncated.
        val offset = prefix.length - window.first
        val highlights = matches
            .filter { it.first >= window.first && it.last <= window.last }
            .map { (it.first + offset)..(it.last + offset) }

        return Snippet(text = text, highlights = highlights)
    }

    /**
     * Locate every term occurrence, prefix-matched to agree with how the query was
     * run: FTS matched on prefixes, so highlighting only whole words would leave a
     * result visibly unexplained.
     */
    private fun findMatches(text: String, queryTerms: List<String>): List<IntRange> {
        val lower = text.lowercase()
        val found = mutableListOf<IntRange>()

        queryTerms.forEach { term ->
            if (term.isBlank()) return@forEach
            var from = 0
            while (true) {
                val at = lower.indexOf(term, from)
                if (at < 0) break
                // Only at a word start. Without this, searching "ram" would
                // highlight the middle of "program", which reads as a bug.
                if (at == 0 || !lower[at - 1].isLetterOrDigit()) {
                    // Extend to the end of the word, so a prefix match highlights
                    // "ramen" rather than the first three letters of it.
                    var end = at + term.length
                    while (end < lower.length && lower[end].isLetterOrDigit()) end++
                    found.add(at until end)
                }
                from = at + 1
            }
        }

        return mergeOverlapping(found.sortedBy { it.first })
    }

    /**
     * Merge overlapping ranges.
     *
     * Two query terms can match the same word — "ram" and "ramen" both hit "ramen" —
     * and overlapping spans would double-style the text or, depending on the
     * renderer, throw.
     */
    private fun mergeOverlapping(sorted: List<IntRange>): List<IntRange> {
        if (sorted.isEmpty()) return emptyList()
        val merged = mutableListOf(sorted.first())
        sorted.drop(1).forEach { range ->
            val last = merged.last()
            if (range.first <= last.last + 1) {
                merged[merged.lastIndex] = last.first..maxOf(last.last, range.last)
            } else {
                merged.add(range)
            }
        }
        return merged
    }

    /**
     * Choose a window of about [targetLength] containing [match].
     *
     * Centred where possible, and shifted rather than shortened at either end of the
     * document — a match in the first ten characters should still get a full-length
     * snippet, just not a centred one.
     */
    private fun windowAround(match: IntRange, text: String, targetLength: Int): IntRange {
        if (text.length <= targetLength) return 0..(text.length - 1)

        val matchCentre = (match.first + match.last) / 2
        var start = matchCentre - targetLength / 2
        var end = start + targetLength

        if (start < 0) {
            start = 0
            end = targetLength
        }
        if (end > text.length) {
            end = text.length
            start = (text.length - targetLength).coerceAtLeast(0)
        }

        // Nudge to word boundaries so the snippet does not begin or end mid-word.
        // Bounded, so a long unbroken string cannot shrink the window to nothing.
        start = nudgeForward(text, start, limit = 20)
        end = nudgeBack(text, end, limit = 20)

        // The match must survive the nudging.
        if (match.first < start) start = match.first
        if (match.last > end - 1) end = (match.last + 1).coerceAtMost(text.length)

        return start..(end - 1)
    }

    private fun nudgeForward(text: String, from: Int, limit: Int): Int {
        if (from == 0) return 0
        var i = from
        var moved = 0
        while (i < text.length && moved < limit && text[i].isLetterOrDigit()) {
            i++; moved++
        }
        // Step past the space itself.
        while (i < text.length && text[i] == ' ') i++
        return i
    }

    private fun nudgeBack(text: String, from: Int, limit: Int): Int {
        var i = from.coerceAtMost(text.length)
        var moved = 0
        while (i > 0 && moved < limit && i < text.length && text[i].isLetterOrDigit()) {
            i--; moved++
        }
        return i.coerceAtLeast(1)
    }
}
