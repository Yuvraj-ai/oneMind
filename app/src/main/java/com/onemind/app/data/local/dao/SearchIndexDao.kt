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
}
