package com.onemind.app.domain.repository

import com.onemind.app.domain.model.Memory
import com.onemind.app.domain.model.ProcessingState
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for Memory CRUD and state management.
 * This is the public seam through which all Memory operations occur.
 */
interface MemoryRepository {

    /**
     * Observe all memories as a reactive stream, ordered most recent first.
     */
    fun observeAllMemories(): Flow<List<Memory>>

    /**
     * Several Memories at once, for hydrating search results.
     *
     * Carries the summary and categories, like the feed stream, since results are
     * rendered with the same card. Order is unspecified; the caller reorders.
     */
    suspend fun getMemoriesByIds(ids: List<Long>): List<Memory>

    /**
     * Get a single memory by ID. Returns null if not found.
     */
    suspend fun getMemoryById(id: Long): Memory?

    /**
     * Create a new Memory with its content blocks. Returns the generated ID.
     */
    suspend fun createMemory(memory: Memory): Long

    /**
     * Update an existing Memory's content blocks and metadata.
     * Does NOT change processing state — use [transitionState] for that.
     */
    suspend fun updateMemory(memory: Memory)

    /**
     * Delete a Memory and everything that belongs only to it: its content blocks,
     * its derived data, its search index row, any reminders still queued for events
     * detected in it, and any enrichment queued for it that has not run.
     *
     * The last three do not cascade — two are not rows at all — so this is the one
     * seam that gets them, and every delete path goes through here.
     *
     * Callers are responsible for cleaning up image files separately
     * via [ImageFileStorage].
     */
    suspend fun deleteMemory(id: Long)

    /**
     * Transition a Memory's processing state.
     * Throws [InvalidStateTransitionException] if the transition is not valid.
     */
    suspend fun transitionState(memoryId: Long, newState: ProcessingState)
}
