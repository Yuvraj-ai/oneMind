package com.onemind.app.domain.search

import com.onemind.app.domain.model.Memory
import com.onemind.app.domain.processing.StageStatus

/**
 * Assembles the searchable text for a Memory.
 *
 * Separate from the stage that persists it because this is the part with
 * judgement in it — what counts as searchable, and what a query is likely to
 * match — and that judgement deserves to be testable without a database.
 */
object SearchDocument {

    /**
     * Build the full-text document for a Memory.
     *
     * Returns an empty string when there is nothing to index, which the caller
     * treats as "write no row" rather than "write an empty row". An empty FTS
     * document would still match nothing, but it would also make `COUNT(*)` on the
     * index a lie about how much is searchable.
     *
     * Only SUCCESS-status derived data is included. A FAILED OCR result holds no
     * text, and a NOT_SUPPORTED summary holds an empty string — indexing either
     * would add nothing but would make the index's contents depend on stage
     * outcomes in a way that is hard to reason about later.
     */
    fun build(memory: Memory): String {
        val derived = memory.derived
        val parts = mutableListOf<String>()

        // What the user typed or pasted. Highest-signal text there is: they chose
        // these words themselves.
        memory.userText().trim().takeIf { it.isNotEmpty() }?.let(parts::add)

        // The summary says what the Memory is *about*, so it tends to match the
        // vague, descriptive queries this app exists to serve. Included early.
        derived.summary
            ?.takeIf { it.status == StageStatus.SUCCESS }
            ?.summaryText
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.let(parts::add)

        // Text read off screenshots. Often the only text a screenshot-heavy
        // Memory has at all.
        derived.ocrResults
            .filter { it.status == StageStatus.SUCCESS }
            .map { it.extractedText.trim() }
            .filter { it.isNotEmpty() }
            .forEach(parts::add)

        // What a model saw in photos, for Memories whose images carry no text.
        derived.visionResults
            .filter { it.status == StageStatus.SUCCESS }
            .map { it.description.trim() }
            .filter { it.isNotEmpty() }
            .forEach(parts::add)

        // Entity names, so searching a person or product finds Memories that
        // mention them even when the surrounding wording differs.
        derived.entities
            .map { it.name.trim() }
            .filter { it.isNotEmpty() }
            .forEach(parts::add)

        // Links. The locked product decisions list URLs among the things a user
        // may search for, so the path is indexed and not only the host: someone
        // looking for "seriouseats.com/ramen" should find it.
        //
        // The query string is dropped, which is the whole reason this is a third
        // projection rather than reusing `normalizedUrl`. That field keeps the
        // query deliberately, because it often carries a page's identity and
        // dropping it would merge different links — but for search it contributes
        // only tracking parameters and session ids, which match queries by
        // accident and never on purpose.
        //
        // `domain` is indexed alongside it because it has `www.` stripped, and a
        // query for "example.com" should find a link to "www.example.com".
        derived.urls
            .flatMap { url ->
                listOf(url.domain.trim(), url.normalizedUrl.substringBefore('?').trim())
            }
            .filter { it.isNotEmpty() }
            .distinct()
            .forEach(parts::add)

        // Category names, so a query naming a category finds its Memories even
        // when no body text uses the word.
        derived.categories
            .map { it.name.trim() }
            .filter { it.isNotEmpty() }
            .forEach(parts::add)

        return parts.joinToString("\n")
    }
}
