package com.onemind.app.data.repository

import com.onemind.app.data.local.dao.CategoryDao
import com.onemind.app.data.local.dao.DerivedDataDao
import com.onemind.app.data.local.dao.MemoryDao
import com.onemind.app.data.local.dao.SearchIndexDao
import com.onemind.app.data.local.entity.CategoryMapper
import com.onemind.app.data.local.entity.DerivedMapper
import com.onemind.app.data.local.entity.EntityMapper.toDomain
import com.onemind.app.data.local.entity.EntityMapper.toEntity
import com.onemind.app.domain.model.Category
import com.onemind.app.domain.model.DerivedData
import com.onemind.app.domain.model.Memory
import com.onemind.app.domain.model.ProcessingState
import com.onemind.app.domain.repository.InvalidStateTransitionException
import com.onemind.app.domain.repository.MemoryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MemoryRepositoryImpl @Inject constructor(
    private val memoryDao: MemoryDao,
    private val derivedDataDao: DerivedDataDao,
    private val categoryDao: CategoryDao,
    private val searchIndexDao: SearchIndexDao
) : MemoryRepository {

    /**
     * Feed stream. Carries the summary and categories but not the rest of the
     * derived data: cards show those two, and loading OCR text and entity lists
     * for every row would be paid on every scroll for data nothing on screen
     * reads.
     *
     * Both extras are fetched in batched queries rather than per row, because a
     * query per card is a cost the feed pays on every scroll. Batches are chunked:
     * SQLite's `IN` is expanded to one bind parameter per id, and the limit on API
     * 30 is 999. A user with a thousand Memories would otherwise hit
     * "too many SQL variables" and see an empty feed.
     */
    override fun observeAllMemories(): Flow<List<Memory>> {
        return memoryDao.observeAllMemories().map { rows ->
            if (rows.isEmpty()) return@map emptyList()

            val ids = rows.map { it.memory.id }
            val summaries = ids.chunked(SQL_VARIABLE_LIMIT)
                .flatMap { derivedDataDao.getSummaries(it) }
                .associateBy { it.memoryId }
            val categories = ids.chunked(SQL_VARIABLE_LIMIT)
                .flatMap { categoryDao.getCategoriesForMemories(it) }
                .groupBy { it.memoryId }
                .mapValues { (_, catRows) ->
                    catRows.map { Category(id = it.id, name = it.name, parentId = it.parentId) }
                }

            rows.map { row ->
                val memory = row.toDomain()
                val summary = summaries[row.memory.id]
                val memoryCategories = categories[row.memory.id].orEmpty()
                if (summary == null && memoryCategories.isEmpty()) return@map memory

                with(DerivedMapper) {
                    memory.copy(
                        derived = DerivedData(
                            summary = summary?.toDomain(),
                            categories = memoryCategories
                        )
                    )
                }
            }
        }
    }

    /**
     * A whole Memory, enrichments included.
     *
     * The pipeline relies on this: it re-reads the Memory between stages so each
     * stage can read what the ones before it wrote.
     */
    override suspend fun getMemoryById(id: Long): Memory? {
        val row = memoryDao.getMemoryById(id) ?: return null
        return row.toDomain().copy(derived = loadDerivedData(id))
    }

    override suspend fun getMemoriesByIds(ids: List<Long>): List<Memory> {
        if (ids.isEmpty()) return emptyList()

        val rows = ids.chunked(SQL_VARIABLE_LIMIT).flatMap { memoryDao.getMemoriesByIds(it) }
        if (rows.isEmpty()) return emptyList()

        // Same two batched lookups the feed uses, for the same reason: results are
        // rendered with the feed's card, and a query per row would cost on every
        // keystroke rather than every scroll.
        val presentIds = rows.map { it.memory.id }
        val summaries = presentIds.chunked(SQL_VARIABLE_LIMIT)
            .flatMap { derivedDataDao.getSummaries(it) }
            .associateBy { it.memoryId }
        val categories = presentIds.chunked(SQL_VARIABLE_LIMIT)
            .flatMap { categoryDao.getCategoriesForMemories(it) }
            .groupBy { it.memoryId }
            .mapValues { (_, catRows) ->
                catRows.map { Category(id = it.id, name = it.name, parentId = it.parentId) }
            }

        return rows.map { row ->
            val memory = row.toDomain()
            val summary = summaries[row.memory.id]
            val memoryCategories = categories[row.memory.id].orEmpty()
            if (summary == null && memoryCategories.isEmpty()) {
                memory
            } else {
                with(DerivedMapper) {
                    memory.copy(
                        derived = DerivedData(
                            summary = summary?.toDomain(),
                            categories = memoryCategories
                        )
                    )
                }
            }
        }
    }

    override suspend fun createMemory(memory: Memory): Long {
        val entity = memory.toEntity()
        val blockEntities = memory.contentBlocks.mapIndexed { index, block ->
            block.toEntity(memoryId = 0).copy(position = index)
        }
        return memoryDao.insertMemoryWithBlocks(entity, blockEntities)
    }

    override suspend fun updateMemory(memory: Memory) {
        val entity = memory.toEntity().copy(updatedAt = Instant.now().toEpochMilli())
        val blockEntities = memory.contentBlocks.mapIndexed { index, block ->
            block.toEntity(memoryId = memory.id).copy(position = index)
        }
        memoryDao.updateMemoryWithBlocks(entity, blockEntities)
    }

    override suspend fun deleteMemory(id: Long) {
        // The relational child tables cascade from the memories foreign key. The
        // search index does not, and cannot: memory_search_index is an FTS4 virtual
        // table, and SQLite does not support foreign keys on virtual tables. Left
        // alone, the deleted Memory's text stays matchable — a privacy problem, and
        // one that also crowds genuine matches out of the query's LIMIT.
        searchIndexDao.delete(id)
        memoryDao.deleteMemory(id)
    }

    override suspend fun transitionState(memoryId: Long, newState: ProcessingState) {
        val memoryWithBlocks = memoryDao.getMemoryById(memoryId)
            ?: throw IllegalArgumentException("Memory not found: $memoryId")

        val currentState = memoryWithBlocks.memory.processingState

        if (!ProcessingState.isValidTransition(currentState, newState)) {
            throw InvalidStateTransitionException(memoryId, currentState, newState)
        }

        memoryDao.updateProcessingState(
            memoryId = memoryId,
            state = newState,
            updatedAt = Instant.now().toEpochMilli()
        )
    }

    private suspend fun loadDerivedData(memoryId: Long): DerivedData = with(DerivedMapper) {
        DerivedData(
            ocrResults = derivedDataDao.getOcrResults(memoryId).map { it.toDomain() },
            visionResults = derivedDataDao.getVisionResults(memoryId).map { it.toDomain() },
            urls = derivedDataDao.getUrls(memoryId).map { it.toDomain() },
            dates = derivedDataDao.getDates(memoryId).map { it.toDomain() },
            entities = derivedDataDao.getEntities(memoryId).map { it.toDomain() },
            summary = derivedDataDao.getSummary(memoryId)?.toDomain(),
            categories = with(CategoryMapper) {
                categoryDao.getCategoriesForMemory(memoryId).map { it.toDomain() }
            },
            categorization = with(CategoryMapper) {
                categoryDao.getCategorization(memoryId)?.toDomain()
            }
        )
    }

    companion object {
        /**
         * Ids per `IN` clause.
         *
         * Room expands `IN (:ids)` to one bind parameter per element, and SQLite
         * caps those at 999 — a limit that has been raised in later versions but not
         * in the one shipping on API 30, which is this app's floor. Chunking below it
         * keeps the feed working past a thousand Memories, which is the scale the app
         * is explicitly built for.
         */
        internal const val SQL_VARIABLE_LIMIT = 900
    }
}
