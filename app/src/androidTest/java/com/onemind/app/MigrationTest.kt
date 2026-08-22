package com.onemind.app

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.onemind.app.data.local.Migrations
import com.onemind.app.data.local.OneMindDatabase
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Verifies the v1 -> v2 migration against the real exported v1 schema.
 *
 * The point is not that the new tables appear: Room would tell us that. The point
 * is that **existing Memories survive**. A Memory is something the user asked us
 * to remember, so a schema change that silently drops one is the worst bug this
 * app could have.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    private companion object {
        const val TEST_DB = "migration-test.db"
    }

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        OneMindDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory()
    )

    @Test
    fun migrate1To2_preservesExistingMemoriesAndTheirContent() {
        // A Memory saved by a v1 build of the app.
        helper.createDatabase(TEST_DB, 1).use { db ->
            db.execSQL(
                """
                INSERT INTO memories (id, createdAt, updatedAt, sourceType, processingState, sourcePackage)
                VALUES (1, 1700000000000, 1700000000000, 'MANUAL', 'READY', NULL)
                """.trimIndent()
            )
            db.execSQL(
                """
                INSERT INTO content_blocks (id, memoryId, position, type, content, thumbnailPath, metadata)
                VALUES (1, 1, 0, 'TEXT', 'Research Qwen models', NULL, NULL)
                """.trimIndent()
            )
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 2, true, Migrations.MIGRATION_1_2)

        db.query("SELECT content, processingState FROM memories JOIN content_blocks ON memories.id = content_blocks.memoryId").use { c ->
            assertTrue("the pre-existing Memory should still be there", c.moveToFirst())
            assertEquals("Research Qwen models", c.getString(0))
            assertEquals("READY", c.getString(1))
            assertEquals("and there should be exactly one", 1, c.count)
        }
    }

    @Test
    fun migrate1To2_addsDerivedTablesReadyForWriting() {
        helper.createDatabase(TEST_DB, 1).use { db ->
            db.execSQL(
                """
                INSERT INTO memories (id, createdAt, updatedAt, sourceType, processingState, sourcePackage)
                VALUES (1, 1, 1, 'MANUAL', 'SAVED', NULL)
                """.trimIndent()
            )
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 2, true, Migrations.MIGRATION_1_2)

        // Writing into a new table proves the schema is usable, not merely present.
        db.execSQL(
            """
            INSERT INTO memory_summaries (memoryId, summaryText, status, generatedAt, providerModel)
            VALUES (1, 'about Qwen', 'SUCCESS', 2, 'm1')
            """.trimIndent()
        )
        db.query("SELECT summaryText FROM memory_summaries WHERE memoryId = 1").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("about Qwen", c.getString(0))
        }
    }

    @Test
    fun migrate1To2_thenOpeningWithRoomWorks() = runCatching {
        helper.createDatabase(TEST_DB, 1).close()
        helper.runMigrationsAndValidate(TEST_DB, 2, true, Migrations.MIGRATION_1_2)

        // Opening through Room proves the migrated schema matches what the
        // entities declare; a mismatch throws here.
        val room = Room.databaseBuilder(
            ApplicationProvider.getApplicationContext(),
            OneMindDatabase::class.java,
            TEST_DB
        ).addMigrations(*Migrations.ALL).build()

        room.openHelper.writableDatabase
        room.close()
    }.getOrThrow()
}
