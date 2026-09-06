package com.onemind.app.data.local.dao

import androidx.room.*
import com.onemind.app.data.local.entity.*

/**
 * Reads and writes everything the Processing Pipeline infers.
 *
 * One DAO rather than one per table, because these tables share a lifecycle:
 * the pipeline writes them together and clearing them on edit has to be atomic
 * across all of them.
 */
@Dao
interface DerivedDataDao {

    // --- writes -----------------------------------------------------------

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOcrResults(results: List<OcrResultEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVisionResults(results: List<VisionResultEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUrls(urls: List<ExtractedUrlEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDates(dates: List<ExtractedDateEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEntities(entities: List<ExtractedEntityEntity>)

    /** REPLACE on a memoryId primary key makes this an upsert. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSummary(summary: MemorySummaryEntity)

    /** REPLACE on a memoryId primary key is what prevents duplicate vectors. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertEmbedding(embedding: MemoryEmbeddingEntity)

    // --- reads ------------------------------------------------------------

    @Query("SELECT * FROM ocr_results WHERE memoryId = :memoryId")
    suspend fun getOcrResults(memoryId: Long): List<OcrResultEntity>

    @Query("SELECT * FROM vision_results WHERE memoryId = :memoryId")
    suspend fun getVisionResults(memoryId: Long): List<VisionResultEntity>

    @Query("SELECT * FROM extracted_urls WHERE memoryId = :memoryId")
    suspend fun getUrls(memoryId: Long): List<ExtractedUrlEntity>

    @Query("SELECT * FROM extracted_dates WHERE memoryId = :memoryId")
    suspend fun getDates(memoryId: Long): List<ExtractedDateEntity>

    @Query("SELECT * FROM extracted_entities WHERE memoryId = :memoryId")
    suspend fun getEntities(memoryId: Long): List<ExtractedEntityEntity>

    @Query("SELECT * FROM memory_summaries WHERE memoryId = :memoryId")
    suspend fun getSummary(memoryId: Long): MemorySummaryEntity?

    @Query("SELECT * FROM memory_embeddings WHERE memoryId = :memoryId")
    suspend fun getEmbedding(memoryId: Long): MemoryEmbeddingEntity?

    /** Every stored vector. Phase 4 vector search scans these. */
    @Query("SELECT * FROM memory_embeddings")
    suspend fun getAllEmbeddings(): List<MemoryEmbeddingEntity>

    /** Summaries for many Memories at once, so the feed needs one query. */
    @Query("SELECT * FROM memory_summaries WHERE memoryId IN (:memoryIds)")
    suspend fun getSummaries(memoryIds: List<Long>): List<MemorySummaryEntity>

    /**
     * Entities for many Memories at once, so a list of events needs one query.
     *
     * Sits beside [getSummaries] and exists for the same reason: a round trip per
     * rendered row is a cost paid on every scroll. Returns every type — the caller
     * picks out the one it cares about.
     */
    @Query("SELECT * FROM extracted_entities WHERE memoryId IN (:memoryIds)")
    suspend fun getEntitiesForMemories(memoryIds: List<Long>): List<ExtractedEntityEntity>

    // --- clearing ---------------------------------------------------------

    @Query("DELETE FROM ocr_results WHERE memoryId = :memoryId")
    suspend fun deleteOcrResults(memoryId: Long)

    @Query("DELETE FROM vision_results WHERE memoryId = :memoryId")
    suspend fun deleteVisionResults(memoryId: Long)

    @Query("DELETE FROM extracted_urls WHERE memoryId = :memoryId")
    suspend fun deleteUrls(memoryId: Long)

    @Query("DELETE FROM extracted_dates WHERE memoryId = :memoryId")
    suspend fun deleteDates(memoryId: Long)

    @Query("DELETE FROM extracted_entities WHERE memoryId = :memoryId")
    suspend fun deleteEntities(memoryId: Long)

    @Query("DELETE FROM memory_summaries WHERE memoryId = :memoryId")
    suspend fun deleteSummary(memoryId: Long)

    @Query("DELETE FROM memory_embeddings WHERE memoryId = :memoryId")
    suspend fun deleteEmbedding(memoryId: Long)

    /**
     * Drop every derived record for a Memory, in one transaction.
     *
     * Called when source content changes: the old inferences describe text that
     * no longer exists, so they go before the pipeline reruns. Atomic so a
     * crash mid-clear cannot leave a Memory holding half-stale enrichments.
     */
    @Transaction
    suspend fun clearAllDerivedData(memoryId: Long) {
        deleteOcrResults(memoryId)
        deleteVisionResults(memoryId)
        deleteUrls(memoryId)
        deleteDates(memoryId)
        deleteEntities(memoryId)
        deleteSummary(memoryId)
        deleteEmbedding(memoryId)
    }
}
