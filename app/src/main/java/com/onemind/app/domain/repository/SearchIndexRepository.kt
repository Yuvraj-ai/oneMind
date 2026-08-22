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
}
