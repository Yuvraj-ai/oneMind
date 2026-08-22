package com.onemind.app

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.onemind.app.data.local.CategorySeeder
import com.onemind.app.data.local.Migrations
import com.onemind.app.data.local.OneMindDatabase
import com.onemind.app.domain.categories.CategoryDictionary
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Verifies the schema migrations against the real exported schemas.
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

    // --- v2 -> v3: the category vocabulary --------------------------------

    @Test
    fun migrate2To3_preservesExistingMemoriesAndTheirDerivedData() {
        // A Memory saved and enriched by a v2 build.
        helper.createDatabase(TEST_DB, 2).use { db ->
            db.execSQL(
                """
                INSERT INTO memories (id, createdAt, updatedAt, sourceType, processingState, sourcePackage)
                VALUES (7, 1700000000000, 1700000000000, 'MANUAL', 'READY', NULL)
                """.trimIndent()
            )
            db.execSQL(
                """
                INSERT INTO content_blocks (id, memoryId, position, type, content, thumbnailPath, metadata)
                VALUES (1, 7, 0, 'TEXT', 'Recipe for tonkotsu ramen', NULL, NULL)
                """.trimIndent()
            )
            db.execSQL(
                """
                INSERT INTO memory_summaries (memoryId, summaryText, status, generatedAt, providerModel)
                VALUES (7, 'A ramen recipe.', 'SUCCESS', 1700000000001, 'gpt-4o-mini')
                """.trimIndent()
            )
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 3, true, Migrations.MIGRATION_2_3)

        db.query("SELECT content FROM content_blocks WHERE memoryId = 7").use { c ->
            assertTrue("the pre-existing Memory should still be there", c.moveToFirst())
            assertEquals("Recipe for tonkotsu ramen", c.getString(0))
        }
        // Enrichments from v2 must survive too: adding categories is no reason to
        // discard a summary the user already has.
        db.query("SELECT summaryText FROM memory_summaries WHERE memoryId = 7").use { c ->
            assertTrue("the existing summary should survive", c.moveToFirst())
            assertEquals("A ramen recipe.", c.getString(0))
        }
    }

    @Test
    fun migrate2To3_seedsTheCategoryVocabulary() {
        helper.createDatabase(TEST_DB, 2).close()

        val db = helper.runMigrationsAndValidate(TEST_DB, 3, true, Migrations.MIGRATION_2_3)

        db.query("SELECT COUNT(*) FROM categories").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(
                "every dictionary category should be seeded",
                CategoryDictionary.ALL.size, c.getInt(0)
            )
        }
    }

    @Test
    fun migrate2To3_seedsEveryDictionaryNameExactly() {
        helper.createDatabase(TEST_DB, 2).close()

        val db = helper.runMigrationsAndValidate(TEST_DB, 3, true, Migrations.MIGRATION_2_3)

        // Names must match the dictionary character for character: the stage
        // matches model output against these rows, so a mangled seed would make a
        // category permanently unassignable.
        val seeded = mutableListOf<String>()
        db.query("SELECT name FROM categories ORDER BY id").use { c ->
            while (c.moveToNext()) seeded.add(c.getString(0))
        }

        assertEquals(CategoryDictionary.ALL, seeded)
    }

    @Test
    fun migrate2To3_letsAMemoryBeCategorised() {
        helper.createDatabase(TEST_DB, 2).use { db ->
            db.execSQL(
                """
                INSERT INTO memories (id, createdAt, updatedAt, sourceType, processingState, sourcePackage)
                VALUES (7, 1, 1, 'MANUAL', 'READY', NULL)
                """.trimIndent()
            )
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 3, true, Migrations.MIGRATION_2_3)

        // Writing through the join proves the schema is usable, not merely present.
        val categoryId = db.query("SELECT id FROM categories WHERE name = 'Food & Cooking'")
            .use { c ->
                assertTrue("Food & Cooking should be seeded", c.moveToFirst())
                c.getLong(0)
            }
        db.execSQL("INSERT INTO memory_categories (memoryId, categoryId) VALUES (7, ?)", arrayOf<Any?>(categoryId))
        db.execSQL(
            """
            INSERT INTO memory_categorization (memoryId, status, processedAt, providerModel)
            VALUES (7, 'SUCCESS', 2, 'gpt-4o-mini')
            """.trimIndent()
        )

        db.query(
            """
            SELECT c.name FROM categories c
            JOIN memory_categories mc ON mc.categoryId = c.id
            WHERE mc.memoryId = 7
            """.trimIndent()
        ).use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("Food & Cooking", c.getString(0))
        }
    }

    @Test
    fun migrate1To3_worksAsOneUpgradeForSomeoneWhoSkippedV2() {
        // Someone who has not opened the app since v1 upgrades straight to v3.
        // Room chains the migrations, and the Memory must survive both.
        helper.createDatabase(TEST_DB, 1).use { db ->
            db.execSQL(
                """
                INSERT INTO memories (id, createdAt, updatedAt, sourceType, processingState, sourcePackage)
                VALUES (3, 1700000000000, 1700000000000, 'SHARE', 'READY', 'com.android.chrome')
                """.trimIndent()
            )
            db.execSQL(
                """
                INSERT INTO content_blocks (id, memoryId, position, type, content, thumbnailPath, metadata)
                VALUES (1, 3, 0, 'URL', 'https://example.com/article', NULL, NULL)
                """.trimIndent()
            )
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 3, true, *Migrations.ALL)

        db.query("SELECT content FROM content_blocks WHERE memoryId = 3").use { c ->
            assertTrue("the v1 Memory should survive two migrations", c.moveToFirst())
            assertEquals("https://example.com/article", c.getString(0))
        }
        db.query("SELECT COUNT(*) FROM categories").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(CategoryDictionary.ALL.size, c.getInt(0))
        }
    }

    @Test
    fun migrate2To3_thenOpeningWithRoomWorks() = runCatching {
        helper.createDatabase(TEST_DB, 2).close()
        helper.runMigrationsAndValidate(TEST_DB, 3, true, Migrations.MIGRATION_2_3)

        val room = Room.databaseBuilder(
            ApplicationProvider.getApplicationContext(),
            OneMindDatabase::class.java,
            TEST_DB
        ).addMigrations(*Migrations.ALL).build()

        room.openHelper.writableDatabase
        room.close()
    }.getOrThrow()

    @Test
    fun seedingIsIdempotent_soAPartiallySeededDatabaseIsRecoverable() {
        // The state a migration crashing midway would leave. Seeding again must
        // not duplicate rows, which the unique index on name would reject anyway.
        helper.createDatabase(TEST_DB, 2).close()
        val db = helper.runMigrationsAndValidate(TEST_DB, 3, true, Migrations.MIGRATION_2_3)

        CategorySeeder.seed(db)
        CategorySeeder.seed(db)

        db.query("SELECT COUNT(*) FROM categories").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(CategoryDictionary.ALL.size, c.getInt(0))
        }
    }
}
