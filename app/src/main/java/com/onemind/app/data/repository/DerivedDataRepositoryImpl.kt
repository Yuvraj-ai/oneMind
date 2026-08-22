package com.onemind.app.data.repository

import com.onemind.app.data.local.dao.CategoryDao
import com.onemind.app.data.local.dao.DerivedDataDao
import com.onemind.app.data.local.entity.CategoryMapper.toDomain
import com.onemind.app.data.local.entity.CategoryMapper.toEntity
import com.onemind.app.data.local.entity.DerivedMapper.toDomain
import com.onemind.app.data.local.entity.DerivedMapper.toEntity
import com.onemind.app.domain.model.*
import com.onemind.app.domain.repository.DerivedDataRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DerivedDataRepositoryImpl @Inject constructor(
    private val dao: DerivedDataDao,
    private val categoryDao: CategoryDao
) : DerivedDataRepository {

    override suspend fun getDerivedData(memoryId: Long): DerivedData {
        return DerivedData(
            ocrResults = dao.getOcrResults(memoryId).map { it.toDomain() },
            visionResults = dao.getVisionResults(memoryId).map { it.toDomain() },
            urls = dao.getUrls(memoryId).map { it.toDomain() },
            dates = dao.getDates(memoryId).map { it.toDomain() },
            entities = dao.getEntities(memoryId).map { it.toDomain() },
            summary = dao.getSummary(memoryId)?.toDomain(),
            categories = categoryDao.getCategoriesForMemory(memoryId).map { it.toDomain() },
            categorization = categoryDao.getCategorization(memoryId)?.toDomain()
        )
    }

    override suspend fun getSummaries(memoryIds: List<Long>): Map<Long, MemorySummary> {
        if (memoryIds.isEmpty()) return emptyMap()
        return dao.getSummaries(memoryIds)
            .associate { it.memoryId to it.toDomain() }
    }

    override suspend fun saveOcrResults(results: List<OcrResult>) {
        if (results.isEmpty()) return
        dao.insertOcrResults(results.map { it.toEntity() })
    }

    override suspend fun saveVisionResults(results: List<VisionResult>) {
        if (results.isEmpty()) return
        dao.insertVisionResults(results.map { it.toEntity() })
    }

    override suspend fun saveUrls(urls: List<ExtractedUrl>) {
        if (urls.isEmpty()) return
        dao.insertUrls(urls.map { it.toEntity() })
    }

    override suspend fun saveDates(dates: List<ExtractedDate>) {
        if (dates.isEmpty()) return
        dao.insertDates(dates.map { it.toEntity() })
    }

    override suspend fun saveEntities(entities: List<ExtractedEntity>) {
        if (entities.isEmpty()) return
        dao.insertEntities(entities.map { it.toEntity() })
    }

    override suspend fun saveSummary(summary: MemorySummary) {
        dao.upsertSummary(summary.toEntity())
    }

    override suspend fun saveEmbedding(embedding: MemoryEmbedding) {
        dao.upsertEmbedding(embedding.toEntity())
    }

    override suspend fun getEmbedding(memoryId: Long): MemoryEmbedding? {
        return dao.getEmbedding(memoryId)?.toDomain()
    }

    override suspend fun getAllEmbeddings(): List<MemoryEmbedding> {
        return dao.getAllEmbeddings().map { it.toDomain() }
    }

    override suspend fun clearDerivedData(memoryId: Long) {
        dao.clearAllDerivedData(memoryId)
        // Assignments are derived and go; the vocabulary is application-owned and
        // stays. Clearing it here would empty the dictionary on any edit.
        categoryDao.deleteAssignments(memoryId)
        categoryDao.deleteCategorization(memoryId)
    }

    override suspend fun getAllCategories(): List<Category> =
        categoryDao.getAllCategories().map { it.toDomain() }

    override suspend fun saveCategories(memoryId: Long, categoryIds: List<Long>) {
        categoryDao.replaceAssignments(memoryId, categoryIds)
    }

    override suspend fun saveCategorizationResult(result: CategorizationResult) {
        categoryDao.upsertCategorization(result.toEntity())
    }

    override suspend fun getCategoriesFor(memoryIds: List<Long>): Map<Long, List<Category>> {
        if (memoryIds.isEmpty()) return emptyMap()
        return categoryDao.getCategoriesForMemories(memoryIds)
            .groupBy { it.memoryId }
            .mapValues { (_, rows) ->
                rows.map { Category(id = it.id, name = it.name, parentId = it.parentId) }
            }
    }
}
