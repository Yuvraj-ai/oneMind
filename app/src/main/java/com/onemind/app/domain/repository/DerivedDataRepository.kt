package com.onemind.app.domain.repository

import com.onemind.app.domain.model.*

/**
 * Reads and writes what the Processing Pipeline infers about a Memory.
 *
 * Separate from [MemoryRepository] on purpose. That one owns content the user
 * gave us and must not lose; this one owns machine inferences that can be thrown
 * away and rebuilt. Keeping the two behind different interfaces means no caller
 * can casually treat a summary as though it were the Memory.
 */
interface DerivedDataRepository {

    /** Everything inferred about a Memory. [DerivedData.EMPTY] if nothing yet. */
    suspend fun getDerivedData(memoryId: Long): DerivedData

    /** Summaries for several Memories in one query, for the feed. */
    suspend fun getSummaries(memoryIds: List<Long>): Map<Long, MemorySummary>

    suspend fun saveOcrResults(results: List<OcrResult>)

    suspend fun saveVisionResults(results: List<VisionResult>)

    suspend fun saveUrls(urls: List<ExtractedUrl>)

    suspend fun saveDates(dates: List<ExtractedDate>)

    suspend fun saveEntities(entities: List<ExtractedEntity>)

    /** Replaces any existing summary for the Memory. */
    suspend fun saveSummary(summary: MemorySummary)

    /**
     * The whole category vocabulary.
     *
     * There is no matching write. The vocabulary is seeded and immutable at
     * runtime, which is what makes it a *controlled* vocabulary rather than a set
     * of labels a model can grow.
     */
    suspend fun getAllCategories(): List<Category>

    /**
     * Replace a Memory's category assignments.
     *
     * Ids must come from [getAllCategories]. A foreign key makes an unknown id a
     * failure at the storage layer rather than a silently orphaned row.
     */
    suspend fun saveCategories(memoryId: Long, categoryIds: List<Long>)

    /** Records how categorization went, including that no provider was available. */
    suspend fun saveCategorizationResult(result: CategorizationResult)

    /** Categories for several Memories in one query, for the feed. */
    suspend fun getCategoriesFor(memoryIds: List<Long>): Map<Long, List<Category>>

    /**
     * Replaces any existing embedding for the Memory, so re-embedding can never
     * leave two active vectors behind.
     */
    suspend fun saveEmbedding(embedding: MemoryEmbedding)

    suspend fun getEmbedding(memoryId: Long): MemoryEmbedding?

    /** Every stored vector. Phase 4 vector search reads this. */
    suspend fun getAllEmbeddings(): List<MemoryEmbedding>

    /**
     * Drop every derived record for a Memory.
     *
     * Called when source content changes: the old inferences describe text that
     * no longer exists.
     *
     * Clears category *assignments* but never the vocabulary itself, which the
     * application owns and no Memory's edit may touch.
     */
    suspend fun clearDerivedData(memoryId: Long)
}
