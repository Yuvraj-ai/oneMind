package com.onemind.app

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.onemind.app.data.local.Migrations
import com.onemind.app.data.local.OneMindDatabase
import com.onemind.app.domain.model.*
import com.onemind.app.domain.processing.StageStatus
import com.onemind.app.domain.search.SearchDocument
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

/**
 * The migration backfill and [SearchDocument] must agree.
 *
 * There are two implementations of "what text is searchable for this Memory": the
 * Kotlin builder used by the indexing stage, and the SQL in `MIGRATION_3_4` used
 * to backfill existing Memories. That duplication is forced — a migration cannot
 * use the DAOs of the database it is migrating — but it is exactly the kind of
 * duplication that silently drifts.
 *
 * The failure it would cause is nasty and hard to attribute: a Memory saved before
 * the upgrade would be findable by different terms than an identical Memory saved
 * after it, and no error would ever surface. This test is the thing keeping the two
 * honest.
 */
@RunWith(AndroidJUnit4::class)
class BackfillParityTest {

    private companion object {
        const val TEST_DB = "backfill-parity-test.db"
    }

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        OneMindDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory()
    )

    @Test
    fun backfillProducesTheSameTermsAsTheKotlinBuilder() = runTest {
        // One Memory, described twice: as v3 rows for the SQL backfill to read, and
        // as a domain object for the Kotlin builder.
        helper.createDatabase(TEST_DB, 3).use { db ->
            db.execSQL(
                """
                INSERT INTO memories (id, createdAt, updatedAt, sourceType, processingState, sourcePackage)
                VALUES (1, 1700000000000, 1700000000000, 'SHARE', 'READY', 'com.android.chrome')
                """.trimIndent()
            )
            db.execSQL(
                """
                INSERT INTO content_blocks (id, memoryId, position, type, content, thumbnailPath, metadata)
                VALUES (1, 1, 0, 'TEXT', 'Notes on tonkotsu ramen', NULL, NULL)
                """.trimIndent()
            )
            db.execSQL(
                """
                INSERT INTO memory_summaries (memoryId, summaryText, status, generatedAt, providerModel)
                VALUES (1, 'A ramen recipe collection.', 'SUCCESS', 1700000000001, 'm')
                """.trimIndent()
            )
            db.execSQL(
                """
                INSERT INTO ocr_results (id, memoryId, contentBlockId, status, extractedText, processedAt)
                VALUES (1, 1, 1, 'SUCCESS', 'Simmer for twelve hours', 1)
                """.trimIndent()
            )
            db.execSQL(
                """
                INSERT INTO vision_results (id, memoryId, contentBlockId, status, description, providerModel, processedAt)
                VALUES (1, 1, 1, 'SUCCESS', 'A bowl of noodle soup', 'm', 1)
                """.trimIndent()
            )
            db.execSQL(
                """
                INSERT INTO extracted_entities (id, memoryId, name, entityType, confidence, source)
                VALUES (1, 1, 'Tokyo', 'PLACE', NULL, 'USER_TEXT')
                """.trimIndent()
            )
            db.execSQL(
                """
                INSERT INTO extracted_urls (id, memoryId, rawUrl, normalizedUrl, domain)
                VALUES (1, 1, 'https://seriouseats.com/ramen', 'https://seriouseats.com/ramen', 'seriouseats.com')
                """.trimIndent()
            )
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 4, true, Migrations.MIGRATION_3_4)
        val backfilled = db.query("SELECT searchableText FROM memory_search_index WHERE rowid = 1")
            .use { c ->
                assertTrue("the Memory should have been backfilled", c.moveToFirst())
                c.getString(0)
            }

        val equivalent = Memory(
            id = 1L,
            contentBlocks = listOf(
                ContentBlock(
                    id = 1L, memoryId = 1L, position = 0,
                    type = ContentType.TEXT, content = "Notes on tonkotsu ramen"
                )
            ),
            derived = DerivedData(
                summary = MemorySummary(
                    memoryId = 1L,
                    summaryText = "A ramen recipe collection.",
                    status = StageStatus.SUCCESS
                ),
                ocrResults = listOf(
                    OcrResult(
                        memoryId = 1L, contentBlockId = 1L, status = StageStatus.SUCCESS,
                        extractedText = "Simmer for twelve hours", processedAt = Instant.EPOCH
                    )
                ),
                visionResults = listOf(
                    VisionResult(
                        memoryId = 1L, contentBlockId = 1L, status = StageStatus.SUCCESS,
                        description = "A bowl of noodle soup", providerModel = "m",
                        processedAt = Instant.EPOCH
                    )
                ),
                entities = listOf(
                    ExtractedEntity(memoryId = 1L, name = "Tokyo", entityType = EntityType.PLACE)
                ),
                urls = listOf(
                    ExtractedUrl(
                        memoryId = 1L, rawUrl = "https://seriouseats.com/ramen",
                        normalizedUrl = "https://seriouseats.com/ramen", domain = "seriouseats.com"
                    )
                )
            )
        )

        val built = SearchDocument.build(equivalent)

        // Compared as term sets rather than strings. Ordering and separators are
        // free to differ — FTS tokenises either way — but a term present in one and
        // absent from the other means the two disagree about what is findable, and
        // that is the drift worth catching.
        fun terms(s: String) = s.lowercase()
            .split(Regex("""[^\p{L}\p{N}.]+"""))
            .filter { it.isNotBlank() }
            .toSet()

        val backfilledTerms = terms(backfilled)
        val builtTerms = terms(built)

        assertEquals(
            "backfill and SearchDocument disagree.\n" +
                "only in backfill: ${backfilledTerms - builtTerms}\n" +
                "only in builder:  ${builtTerms - backfilledTerms}",
            builtTerms,
            backfilledTerms
        )
    }
}
