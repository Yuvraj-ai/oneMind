package com.onemind.app.data.repository

import com.onemind.app.data.local.dao.SearchIndexDao
import com.onemind.app.data.local.entity.MemorySearchIndexEntity
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
}
