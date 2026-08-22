package com.onemind.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.onemind.app.data.local.entity.MemorySearchIndexEntity

/**
 * Reads and writes the full-text search index.
 *
 * Querying it is #24's job; this ticket only needs to keep it correct.
 */
@Dao
interface SearchIndexDao {

    /**
     * Write a Memory's index row, replacing any previous one.
     *
     * REPLACE rather than insert-or-update because reprocessing must not union
     * with a previous run's text: if a Memory's content was edited, the old OCR
     * output describes an image that may no longer be attached.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(row: MemorySearchIndexEntity)

    @Query("DELETE FROM memory_search_index WHERE rowid = :memoryId")
    suspend fun delete(memoryId: Long)

    @Query("SELECT COUNT(*) FROM memory_search_index")
    suspend fun count(): Int

    /** Present for tests and the backfill's own verification. */
    @Query("SELECT searchableText FROM memory_search_index WHERE rowid = :memoryId")
    suspend fun getText(memoryId: Long): String?

    /**
     * Rows matching an FTS expression, with their documents.
     *
     * The document comes back alongside the id because relevance has to be
     * computed from it — FTS4 has no BM25 to ask — and because #29 needs the text
     * to build a matching snippet. Fetching it here avoids a second query per
     * result.
     *
     * [ftsExpression] must come from [com.onemind.app.domain.search.FtsQuery.build],
     * never from raw user input: MATCH takes a grammar, and unsanitised text in it
     * is a syntax error rather than a failed search.
     */
    @Query(
        """
        SELECT rowid AS memoryId, searchableText
        FROM memory_search_index
        WHERE memory_search_index MATCH :ftsExpression
        LIMIT :limit
        """
    )
    suspend fun match(ftsExpression: String, limit: Int): List<SearchIndexRow>
}

/** A matching index row: which Memory, and the text that matched. */
data class SearchIndexRow(
    val memoryId: Long,
    val searchableText: String
)
