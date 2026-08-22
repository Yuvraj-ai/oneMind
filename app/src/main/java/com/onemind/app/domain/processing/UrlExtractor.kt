package com.onemind.app.domain.processing

import com.onemind.app.domain.model.DerivedSource
import com.onemind.app.domain.model.ExtractedUrl

/**
 * Pulls links out of text.
 *
 * Deterministic and entirely local, which is why it is separated from the parts of
 * metadata extraction that need a model: a Memory full of links is still usefully
 * enriched for a user who configured no provider at all.
 *
 * Pure functions, so the awkward cases — trailing punctuation, query strings,
 * duplicates, adjacent links — are cheap to pin down in tests.
 */
object UrlExtractor {

    /**
     * Matches http and https URLs.
     *
     * The trailing character class deliberately excludes whitespace, quotes,
     * angle brackets and backticks. Sentence-ending punctuation is handled
     * afterwards rather than here, because a full stop is legal inside a URL and
     * only ambiguous at the end.
     */
    private val URL_PATTERN = Regex(
        """https?://[^\s<>"'`\[\]{}|\\^]+""",
        RegexOption.IGNORE_CASE
    )

    /**
     * Characters that are legal in a URL but almost always sentence punctuation
     * when they end one. Brackets are excluded by the pattern already.
     */
    private const val TRAILING_PUNCTUATION = ".,;:!?"

    /**
     * Extract links from each source, keeping track of where each came from.
     *
     * De-duplicated by normalised form, so the same link written twice — or once
     * in the user's text and again in a screenshot of it — is stored once.
     */
    fun extractAll(
        memoryId: Long,
        sources: List<Pair<DerivedSource, String>>
    ): List<ExtractedUrl> {
        val seen = mutableSetOf<String>()
        val result = mutableListOf<ExtractedUrl>()

        sources.forEach { (_, text) ->
            extract(memoryId, text).forEach { url ->
                if (seen.add(url.normalizedUrl)) result.add(url)
            }
        }

        return result
    }

    /** Extract links from a single piece of text, in order of appearance. */
    fun extract(memoryId: Long, text: String): List<ExtractedUrl> {
        if (text.isBlank()) return emptyList()

        return URL_PATTERN.findAll(text)
            .map { it.value }
            .map(::trimTrailingPunctuation)
            .filter { it.isNotBlank() }
            .mapNotNull { raw ->
                val normalized = normalize(raw) ?: return@mapNotNull null
                ExtractedUrl(
                    memoryId = memoryId,
                    rawUrl = raw,
                    normalizedUrl = normalized,
                    domain = domainOf(raw) ?: return@mapNotNull null
                )
            }
            .toList()
    }

    /**
     * Strip punctuation that belongs to the sentence rather than the link.
     *
     * A closing bracket is also dropped when unbalanced, which catches the common
     * `(see https://example.com/page)` shape.
     */
    private fun trimTrailingPunctuation(url: String): String {
        var result = url

        while (result.isNotEmpty()) {
            val last = result.last()
            when {
                last in TRAILING_PUNCTUATION -> result = result.dropLast(1)
                last == ')' && result.count { it == '(' } < result.count { it == ')' } ->
                    result = result.dropLast(1)
                else -> return result
            }
        }

        return result
    }

    /**
     * Canonical form for comparison: lowercased scheme and host, no default port,
     * no fragment, no trailing slash on the path.
     *
     * The query string is kept. It frequently carries the identity of the page,
     * so dropping it would merge genuinely different links.
     */
    fun normalize(url: String): String? {
        val match = Regex(
            """^(https?)://([^/?#\s]+)([^?#\s]*)(\?[^#\s]*)?""",
            RegexOption.IGNORE_CASE
        ).find(url) ?: return null

        val scheme = match.groupValues[1].lowercase()
        var host = match.groupValues[2].lowercase()
        val path = match.groupValues[3].trimEnd('/')
        val query = match.groupValues[4]

        // A default port says nothing, so it should not make two links differ.
        host = when {
            scheme == "http" && host.endsWith(":80") -> host.removeSuffix(":80")
            scheme == "https" && host.endsWith(":443") -> host.removeSuffix(":443")
            else -> host
        }

        if (host.isEmpty()) return null

        return "$scheme://$host$path$query"
    }

    /** Host without port or `www.`, for grouping links by site. */
    fun domainOf(url: String): String? {
        val match = Regex("""^https?://([^/?#\s]+)""", RegexOption.IGNORE_CASE)
            .find(url) ?: return null

        val host = match.groupValues[1]
            .lowercase()
            .substringBefore(':')
            .removePrefix("www.")

        return host.ifEmpty { null }
    }
}
