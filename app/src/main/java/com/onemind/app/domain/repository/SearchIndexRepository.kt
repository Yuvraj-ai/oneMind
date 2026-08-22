package com.onemind.app.domain.repository

/**
 * Keeps the full-text search index in step with Memories.
 *
 * Deliberately not part of [DerivedDataRepository]. That one holds inferences
 * *about* a Memory that the UI reads and displays; this holds a derived copy of
 * text that only the search path reads and nothing displays. Keeping them apart
 * means no screen can accidentally render the index, and the index can be rebuilt
 * wholesale without touching anything a user sees.
 */
interface SearchIndexRepository {

    /** Write or replace a Memory's search document. */
    suspend fun index(memoryId: Long, document: String)

    /** Drop a Memory's search document. */
    suspend fun remove(memoryId: Long)

    /** How many Memories are indexed. Used by the backfill and its tests. */
    suspend fun indexedCount(): Int

    /**
     * Memories matching an FTS expression, with their documents.
     *
     * [ftsExpression] must come from
     * [com.onemind.app.domain.search.FtsQuery.build] — MATCH takes a grammar, and
     * raw user text in it is a syntax error rather than a failed search.
     */
    suspend fun match(ftsExpression: String, limit: Int): List<IndexedDocument>
}

/**
 * A Memory's indexed text, as returned by a search.
 *
 * Declared here rather than reusing the DAO's row type so the domain does not have
 * to know that storage exists.
 */
data class IndexedDocument(
    val memoryId: Long,
    val searchableText: String
)
