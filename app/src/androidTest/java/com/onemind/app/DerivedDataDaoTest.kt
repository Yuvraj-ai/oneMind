package com.onemind.app

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.onemind.app.data.local.OneMindDatabase
import com.onemind.app.data.local.VectorCodec
import com.onemind.app.data.local.dao.DerivedDataDao
import com.onemind.app.data.local.dao.MemoryDao
import com.onemind.app.data.local.entity.*
import com.onemind.app.domain.model.ContentType
import com.onemind.app.domain.model.DerivedSource
import com.onemind.app.domain.model.EntityType
import com.onemind.app.domain.model.ProcessingState
import com.onemind.app.domain.model.SourceType
import com.onemind.app.domain.processing.StageStatus
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DerivedDataDaoTest {

    private lateinit var database: OneMindDatabase
    private lateinit var dao: DerivedDataDao
    private lateinit var memoryDao: MemoryDao

    /** A real Memory to hang derived data off, since every table has an FK to it. */
    private var memoryId: Long = 0
    private var blockId: Long = 0

    @Before
    fun setup() = runTest {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            OneMindDatabase::class.java
        ).allowMainThreadQueries().build()
        dao = database.derivedDataDao()
        memoryDao = database.memoryDao()

        memoryId = memoryDao.insertMemoryWithBlocks(
            MemoryEntity(
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis(),
                sourceType = SourceType.MANUAL,
                processingState = ProcessingState.SAVED
            ),
            listOf(
                ContentBlockEntity(
                    memoryId = 0,
                    position = 0,
                    type = ContentType.IMAGE,
                    content = "/tmp/img.webp"
                )
            )
        )
        blockId = memoryDao.getMemoryById(memoryId)!!.contentBlocks.first().id
    }

    @After
    fun teardown() {
        database.close()
    }

    // --- round trips ------------------------------------------------------

    @Test
    fun ocrResultRoundTrips() = runTest {
        dao.insertOcrResults(
            listOf(
                OcrResultEntity(
                    memoryId = memoryId,
                    contentBlockId = blockId,
                    status = StageStatus.SUCCESS,
                    extractedText = "AI Summit 2026",
                    processedAt = 1000L
                )
            )
        )

        val stored = dao.getOcrResults(memoryId)
        assertEquals(1, stored.size)
        assertEquals("AI Summit 2026", stored.first().extractedText)
        assertEquals(StageStatus.SUCCESS, stored.first().status)
    }

    @Test
    fun ocrCanRecordEmptyAsDistinctFromFailure() = runTest {
        dao.insertOcrResults(
            listOf(
                OcrResultEntity(
                    memoryId = memoryId, contentBlockId = blockId,
                    status = StageStatus.EMPTY, extractedText = "", processedAt = 1L
                )
            )
        )
        assertEquals(StageStatus.EMPTY, dao.getOcrResults(memoryId).first().status)
    }

    @Test
    fun visionCanRecordNotSupported() = runTest {
        dao.insertVisionResults(
            listOf(
                VisionResultEntity(
                    memoryId = memoryId, contentBlockId = blockId,
                    status = StageStatus.NOT_SUPPORTED, description = "",
                    providerModel = null, processedAt = 1L
                )
            )
        )
        val stored = dao.getVisionResults(memoryId).first()
        assertEquals(StageStatus.NOT_SUPPORTED, stored.status)
        assertNull(stored.providerModel)
    }

    @Test
    fun extractedUrlRoundTrips() = runTest {
        dao.insertUrls(
            listOf(
                ExtractedUrlEntity(
                    memoryId = memoryId,
                    rawUrl = "https://github.com/example/project?ref=1",
                    normalizedUrl = "https://github.com/example/project",
                    domain = "github.com"
                )
            )
        )
        assertEquals("github.com", dao.getUrls(memoryId).first().domain)
    }

    @Test
    fun extractedDateKeepsUnresolvedTextWithNullInstant() = runTest {
        dao.insertDates(
            listOf(
                ExtractedDateEntity(
                    memoryId = memoryId,
                    rawText = "sometime next spring",
                    parsedInstant = null,
                    isEventTime = true,
                    source = DerivedSource.USER_TEXT
                )
            )
        )
        val stored = dao.getDates(memoryId).first()
        assertNull(stored.parsedInstant)
        assertEquals("sometime next spring", stored.rawText)
        assertTrue(stored.isEventTime)
    }

    @Test
    fun extractedEntityKeepsNullConfidenceWhenModelGaveNone() = runTest {
        dao.insertEntities(
            listOf(
                ExtractedEntityEntity(
                    memoryId = memoryId, name = "Google",
                    entityType = EntityType.ORGANIZATION,
                    confidence = null, source = DerivedSource.OCR
                )
            )
        )
        val stored = dao.getEntities(memoryId).first()
        assertNull(stored.confidence)
        assertEquals(EntityType.ORGANIZATION, stored.entityType)
        assertEquals(DerivedSource.OCR, stored.source)
    }

    @Test
    fun embeddingVectorSurvivesTheBlobRoundTrip() = runTest {
        val vector = FloatArray(384) { it * 0.001f }
        dao.upsertEmbedding(
            MemoryEmbeddingEntity(
                memoryId = memoryId,
                vector = VectorCodec.encode(vector),
                dimensions = 384,
                modelId = "all-minilm-l6-v2",
                generatedAt = 1L
            )
        )

        val stored = dao.getEmbedding(memoryId)!!
        assertArrayEquals(vector, VectorCodec.decode(stored.vector), 0.0f)
        assertEquals(384, stored.dimensions)
    }

    // --- replace semantics ------------------------------------------------

    @Test
    fun reEmbeddingReplacesRatherThanDuplicating() = runTest {
        val first = FloatArray(384) { 0.1f }
        val second = FloatArray(384) { 0.9f }

        dao.upsertEmbedding(
            MemoryEmbeddingEntity(memoryId, VectorCodec.encode(first), 384, "m1", 1L)
        )
        dao.upsertEmbedding(
            MemoryEmbeddingEntity(memoryId, VectorCodec.encode(second), 384, "m1", 2L)
        )

        // The memoryId primary key is what guarantees this.
        assertEquals(1, dao.getAllEmbeddings().size)
        assertArrayEquals(
            second,
            VectorCodec.decode(dao.getEmbedding(memoryId)!!.vector),
            0.0f
        )
    }

    @Test
    fun resummarisingReplacesRatherThanDuplicating() = runTest {
        dao.upsertSummary(
            MemorySummaryEntity(
                memoryId = memoryId, summaryText = "first take",
                status = StageStatus.SUCCESS, generatedAt = 1L, providerModel = "m1"
            )
        )
        dao.upsertSummary(
            MemorySummaryEntity(
                memoryId = memoryId, summaryText = "second take",
                status = StageStatus.SUCCESS, generatedAt = 2L, providerModel = "m1"
            )
        )

        assertEquals("second take", dao.getSummary(memoryId)!!.summaryText)
    }

    @Test
    fun summariesCanBeFetchedForManyMemoriesAtOnce() = runTest {
        val other = memoryDao.insertMemoryWithBlocks(
            MemoryEntity(
                createdAt = 1L, updatedAt = 1L,
                sourceType = SourceType.MANUAL, processingState = ProcessingState.SAVED
            ),
            emptyList()
        )
        dao.upsertSummary(
            MemorySummaryEntity(
                memoryId = memoryId, summaryText = "a",
                status = StageStatus.SUCCESS, generatedAt = 1L, providerModel = null
            )
        )
        dao.upsertSummary(
            MemorySummaryEntity(
                memoryId = other, summaryText = "b",
                status = StageStatus.SUCCESS, generatedAt = 1L, providerModel = null
            )
        )

        val fetched = dao.getSummaries(listOf(memoryId, other))
        assertEquals(2, fetched.size)
    }

    @Test
    fun entitiesCanBeFetchedForManyMemoriesAtOnce() = runTest {
        val other = memoryDao.insertMemoryWithBlocks(
            MemoryEntity(
                createdAt = 1L, updatedAt = 1L,
                sourceType = SourceType.MANUAL, processingState = ProcessingState.SAVED
            ),
            emptyList()
        )
        dao.insertEntities(
            listOf(
                ExtractedEntityEntity(
                    memoryId = memoryId, name = "Moscone Center",
                    entityType = EntityType.PLACE, confidence = 0.8f,
                    source = DerivedSource.OCR
                ),
                ExtractedEntityEntity(
                    memoryId = other, name = "Google",
                    entityType = EntityType.ORGANIZATION, confidence = null,
                    source = DerivedSource.USER_TEXT
                )
            )
        )

        // One query for many Memories, so the events list does not pay a round trip
        // per card. Unfiltered: the caller decides that PLACE is the one it wants.
        val fetched = dao.getEntitiesForMemories(listOf(memoryId, other))

        assertEquals(2, fetched.size)
        assertEquals(
            setOf(memoryId, other),
            fetched.map { it.memoryId }.toSet()
        )
    }

    // --- clearing and cascade --------------------------------------------

    @Test
    fun clearAllDerivedDataRemovesEveryKind() = runTest {
        seedEveryKindOfDerivedData()

        dao.clearAllDerivedData(memoryId)

        assertTrue(dao.getOcrResults(memoryId).isEmpty())
        assertTrue(dao.getVisionResults(memoryId).isEmpty())
        assertTrue(dao.getUrls(memoryId).isEmpty())
        assertTrue(dao.getDates(memoryId).isEmpty())
        assertTrue(dao.getEntities(memoryId).isEmpty())
        assertNull(dao.getSummary(memoryId))
        assertNull(dao.getEmbedding(memoryId))
    }

    @Test
    fun clearingOneMemoryLeavesAnotherUntouched() = runTest {
        seedEveryKindOfDerivedData()
        val other = memoryDao.insertMemoryWithBlocks(
            MemoryEntity(
                createdAt = 1L, updatedAt = 1L,
                sourceType = SourceType.MANUAL, processingState = ProcessingState.SAVED
            ),
            emptyList()
        )
        dao.upsertSummary(
            MemorySummaryEntity(
                memoryId = other, summaryText = "keep me",
                status = StageStatus.SUCCESS, generatedAt = 1L, providerModel = null
            )
        )

        dao.clearAllDerivedData(memoryId)

        assertNotNull(dao.getSummary(other))
        assertEquals("keep me", dao.getSummary(other)!!.summaryText)
    }

    @Test
    fun deletingTheMemoryCascadesToEveryDerivedTable() = runTest {
        seedEveryKindOfDerivedData()

        memoryDao.deleteMemory(memoryId)

        // Nothing should outlive the Memory it describes.
        assertTrue(dao.getOcrResults(memoryId).isEmpty())
        assertTrue(dao.getVisionResults(memoryId).isEmpty())
        assertTrue(dao.getUrls(memoryId).isEmpty())
        assertTrue(dao.getDates(memoryId).isEmpty())
        assertTrue(dao.getEntities(memoryId).isEmpty())
        assertNull(dao.getSummary(memoryId))
        assertNull(dao.getEmbedding(memoryId))
        assertTrue(dao.getAllEmbeddings().isEmpty())
    }

    private suspend fun seedEveryKindOfDerivedData() {
        dao.insertOcrResults(
            listOf(
                OcrResultEntity(
                    memoryId = memoryId, contentBlockId = blockId,
                    status = StageStatus.SUCCESS, extractedText = "text", processedAt = 1L
                )
            )
        )
        dao.insertVisionResults(
            listOf(
                VisionResultEntity(
                    memoryId = memoryId, contentBlockId = blockId,
                    status = StageStatus.SUCCESS, description = "a scene",
                    providerModel = "m1", processedAt = 1L
                )
            )
        )
        dao.insertUrls(
            listOf(
                ExtractedUrlEntity(
                    memoryId = memoryId, rawUrl = "https://a.com",
                    normalizedUrl = "https://a.com", domain = "a.com"
                )
            )
        )
        dao.insertDates(
            listOf(
                ExtractedDateEntity(
                    memoryId = memoryId, rawText = "Sept 15",
                    parsedInstant = 1L, isEventTime = true,
                    source = DerivedSource.USER_TEXT
                )
            )
        )
        dao.insertEntities(
            listOf(
                ExtractedEntityEntity(
                    memoryId = memoryId, name = "Google",
                    entityType = EntityType.ORGANIZATION,
                    confidence = 0.9f, source = DerivedSource.USER_TEXT
                )
            )
        )
        dao.upsertSummary(
            MemorySummaryEntity(
                memoryId = memoryId, summaryText = "about things",
                status = StageStatus.SUCCESS, generatedAt = 1L, providerModel = "m1"
            )
        )
        dao.upsertEmbedding(
            MemoryEmbeddingEntity(
                memoryId, VectorCodec.encode(FloatArray(384) { 0.5f }), 384, "e1", 1L
            )
        )
    }
}
