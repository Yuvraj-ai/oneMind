package com.onemind.app.data.repository

import com.onemind.app.data.local.dao.SearchIndexDao
import com.onemind.app.data.local.entity.MemorySearchIndexEntity
import com.onemind.app.domain.repository.IndexedDocument
import com.onemind.app.domain.repository.SearchIndexRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SearchIndexRepositoryImpl @Inject constructor(
    private val dao: SearchIndexDao
) : SearchIndexRepository {

    override suspend fun index(memoryId: Long, document: String) {
        dao.upsert(MemorySearchIndexEntity(memoryId = memoryId, searchableText = document))
    }

    override suspend fun remove(memoryId: Long) {
        dao.delete(memoryId)
    }

    override suspend fun indexedCount(): Int = dao.count()

    override suspend fun match(ftsExpression: String, limit: Int): List<IndexedDocument> =
        dao.match(ftsExpression, limit).map {
            IndexedDocument(memoryId = it.memoryId, searchableText = it.searchableText)
        }
}
