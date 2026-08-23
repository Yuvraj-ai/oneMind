package com.onemind.app.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Schema migrations.
 *
 * Written out by hand rather than falling back to destructive migration: a
 * Memory is something the user asked us to remember, so dropping the database on
 * a schema change is not an acceptable failure mode. Derived data alone could be
 * regenerated, but the Memories themselves could not.
 */
object Migrations {

    /**
     * v1 -> v2: add the derived-data tables.
     *
     * Purely additive. Nothing in `memories` or `content_blocks` changes, so
     * existing Memories come through untouched and simply have no enrichments
     * until the pipeline next runs over them.
     */
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `ocr_results` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `memoryId` INTEGER NOT NULL,
                    `contentBlockId` INTEGER NOT NULL,
                    `status` TEXT NOT NULL,
                    `extractedText` TEXT NOT NULL,
                    `processedAt` INTEGER NOT NULL,
                    FOREIGN KEY(`memoryId`) REFERENCES `memories`(`id`)
                        ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_ocr_results_memoryId` ON `ocr_results` (`memoryId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_ocr_results_contentBlockId` ON `ocr_results` (`contentBlockId`)")

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `vision_results` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `memoryId` INTEGER NOT NULL,
                    `contentBlockId` INTEGER NOT NULL,
                    `status` TEXT NOT NULL,
                    `description` TEXT NOT NULL,
                    `providerModel` TEXT,
                    `processedAt` INTEGER NOT NULL,
                    FOREIGN KEY(`memoryId`) REFERENCES `memories`(`id`)
                        ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_vision_results_memoryId` ON `vision_results` (`memoryId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_vision_results_contentBlockId` ON `vision_results` (`contentBlockId`)")

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `extracted_urls` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `memoryId` INTEGER NOT NULL,
                    `rawUrl` TEXT NOT NULL,
                    `normalizedUrl` TEXT NOT NULL,
                    `domain` TEXT NOT NULL,
                    FOREIGN KEY(`memoryId`) REFERENCES `memories`(`id`)
                        ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_extracted_urls_memoryId` ON `extracted_urls` (`memoryId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_extracted_urls_domain` ON `extracted_urls` (`domain`)")

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `extracted_dates` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `memoryId` INTEGER NOT NULL,
                    `rawText` TEXT NOT NULL,
                    `parsedInstant` INTEGER,
                    `isEventTime` INTEGER NOT NULL,
                    `source` TEXT NOT NULL,
                    FOREIGN KEY(`memoryId`) REFERENCES `memories`(`id`)
                        ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_extracted_dates_memoryId` ON `extracted_dates` (`memoryId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_extracted_dates_parsedInstant` ON `extracted_dates` (`parsedInstant`)")

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `extracted_entities` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `memoryId` INTEGER NOT NULL,
                    `name` TEXT NOT NULL,
                    `entityType` TEXT NOT NULL,
                    `confidence` REAL,
                    `source` TEXT NOT NULL,
                    FOREIGN KEY(`memoryId`) REFERENCES `memories`(`id`)
                        ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_extracted_entities_memoryId` ON `extracted_entities` (`memoryId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_extracted_entities_name` ON `extracted_entities` (`name`)")

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `memory_summaries` (
                    `memoryId` INTEGER PRIMARY KEY NOT NULL,
                    `summaryText` TEXT NOT NULL,
                    `status` TEXT NOT NULL,
                    `generatedAt` INTEGER NOT NULL,
                    `providerModel` TEXT,
                    FOREIGN KEY(`memoryId`) REFERENCES `memories`(`id`)
                        ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent()
            )

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `memory_embeddings` (
                    `memoryId` INTEGER PRIMARY KEY NOT NULL,
                    `vector` BLOB NOT NULL,
                    `dimensions` INTEGER NOT NULL,
                    `modelId` TEXT NOT NULL,
                    `generatedAt` INTEGER NOT NULL,
                    FOREIGN KEY(`memoryId`) REFERENCES `memories`(`id`)
                        ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent()
            )
        }
    }

    /**
     * v2 -> v3: add the category vocabulary and its join to Memories.
     *
     * Additive again: no existing table is altered, so every Memory survives with
     * its content and its existing enrichments intact. Newly-created Memories get
     * categories from the pipeline; older ones stay uncategorised until they are
     * next processed, which is the same way they behaved when derived data
     * arrived in v2.
     *
     * The vocabulary is seeded here rather than left to first use, so the table is
     * never briefly empty in a way that would let the categorization stage offer a
     * model nothing to choose from.
     */
    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `categories` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `name` TEXT NOT NULL,
                    `parentId` INTEGER
                )
                """.trimIndent()
            )
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS `index_categories_name` ON `categories` (`name`)"
            )

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `memory_categories` (
                    `memoryId` INTEGER NOT NULL,
                    `categoryId` INTEGER NOT NULL,
                    PRIMARY KEY(`memoryId`, `categoryId`),
                    FOREIGN KEY(`memoryId`) REFERENCES `memories`(`id`)
                        ON UPDATE NO ACTION ON DELETE CASCADE,
                    FOREIGN KEY(`categoryId`) REFERENCES `categories`(`id`)
                        ON UPDATE NO ACTION ON DELETE RESTRICT
                )
                """.trimIndent()
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_memory_categories_memoryId` ON `memory_categories` (`memoryId`)"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_memory_categories_categoryId` ON `memory_categories` (`categoryId`)"
            )

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `memory_categorization` (
                    `memoryId` INTEGER PRIMARY KEY NOT NULL,
                    `status` TEXT NOT NULL,
                    `processedAt` INTEGER NOT NULL,
                    `providerModel` TEXT,
                    FOREIGN KEY(`memoryId`) REFERENCES `memories`(`id`)
                        ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent()
            )

            CategorySeeder.seed(db)
        }
    }

    /**
     * v3 -> v4: add the full-text search index, and backfill it.
     *
     * Additive in schema terms, but the backfill is the point. Without it, search
     * would only find Memories saved after the upgrade, and a user with two years
     * of history would type a query, get nothing, and reasonably conclude the
     * feature is broken. Their existing Memories are exactly the ones they have
     * forgotten and most need to search.
     *
     * The backfill assembles the same document [com.onemind.app.domain.search.SearchDocument]
     * would, in SQL. That duplication is unfortunate and deliberate: running the
     * Kotlin builder here would mean loading every Memory and its derived data
     * through Room during a migration, which cannot use the DAOs of a database
     * that is still being migrated. The two are kept honest by a test that indexes
     * the same Memory both ways and compares.
     *
     * Only SUCCESS-status rows are included, matching the builder. `group_concat`
     * is fed pre-filtered rows so a FAILED OCR result contributes nothing rather
     * than an empty line.
     */
    val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // FTS4 virtual table. `content=""` is not used: this is a standalone
            // index, because its document is assembled from six tables and Room's
            // external-content mode binds to exactly one.
            db.execSQL(
                """
                CREATE VIRTUAL TABLE IF NOT EXISTS `memory_search_index`
                USING fts4(`searchableText` TEXT NOT NULL)
                """.trimIndent()
            )

            // Backfill. Built as a union of non-empty parts rather than a chain of
            // COALESCE concatenations, for a reason worth recording: with the
            // concatenation form, a Memory with nothing to index produces a string
            // of bare separators rather than an empty one, and SQLite's TRIM strips
            // only spaces by default — not newlines — so the emptiness check
            // silently fails and an empty row gets written. Selecting only non-empty
            // parts and grouping makes that class of mistake unrepresentable: a
            // Memory with no parts forms no group, so it gets no row at all.
            //
            // `sortKey` keeps section order fixed so the same data always yields the
            // same document, which matters only for reproducibility, not search.
            db.execSQL(
                """
                INSERT INTO `memory_search_index` (`rowid`, `searchableText`)
                SELECT memoryId, group_concat(part, char(10))
                FROM (
                    SELECT memoryId, 1 AS sortKey, content AS part
                    FROM content_blocks
                    WHERE type = 'TEXT' AND TRIM(content) <> ''

                    UNION ALL
                    SELECT memoryId, 2, summaryText
                    FROM memory_summaries
                    WHERE status = 'SUCCESS' AND TRIM(summaryText) <> ''

                    UNION ALL
                    SELECT memoryId, 3, extractedText
                    FROM ocr_results
                    WHERE status = 'SUCCESS' AND TRIM(extractedText) <> ''

                    UNION ALL
                    SELECT memoryId, 4, description
                    FROM vision_results
                    WHERE status = 'SUCCESS' AND TRIM(description) <> ''

                    UNION ALL
                    SELECT memoryId, 5, name
                    FROM extracted_entities
                    WHERE TRIM(name) <> ''

                    UNION ALL
                    SELECT DISTINCT memoryId, 6, domain
                    FROM extracted_urls
                    WHERE TRIM(domain) <> ''

                    -- The link's path as well as its host, because the locked
                    -- product decisions list URLs among searchable things. The
                    -- query string is cut off: `normalizedUrl` keeps it on purpose
                    -- for identity, but for search it is only tracking parameters.
                    UNION ALL
                    SELECT DISTINCT memoryId, 6,
                        CASE WHEN instr(normalizedUrl, '?') > 0
                             THEN substr(normalizedUrl, 1, instr(normalizedUrl, '?') - 1)
                             ELSE normalizedUrl END
                    FROM extracted_urls
                    WHERE TRIM(normalizedUrl) <> ''

                    UNION ALL
                    SELECT mc.memoryId, 7, c.name
                    FROM categories c
                    JOIN memory_categories mc ON mc.categoryId = c.id
                    WHERE TRIM(c.name) <> ''

                    ORDER BY memoryId, sortKey
                )
                GROUP BY memoryId
                """.trimIndent()
            )
        }
    }

    /**
     * v4 -> v5: add title to summaries and detected_events table.
     *
     * Two additive changes:
     *
     * - `title` column on `memory_summaries`. Nullable so existing rows are valid
     *   without backfill — the title is generated on the next reprocessing or on
     *   new Memories.
     *
     * - `detected_events` table. A Memory containing a future date becomes
     *   event-bearing. The event has its own lifecycle (UPCOMING → EXPIRED) and its
     *   own reminders, but it is not a separate saved thing — it is a lens on a
     *   Memory, and deleting the Memory cascades to the event.
     */
    val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // Add title column to existing summaries.
            db.execSQL("ALTER TABLE `memory_summaries` ADD COLUMN `title` TEXT DEFAULT NULL")

            // Events table.
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `detected_events` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `memoryId` INTEGER NOT NULL,
                    `eventTime` INTEGER NOT NULL,
                    `eventTitle` TEXT NOT NULL,
                    `status` TEXT NOT NULL DEFAULT 'UPCOMING',
                    `remindersScheduledAt` INTEGER,
                    FOREIGN KEY(`memoryId`) REFERENCES `memories`(`id`)
                        ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent()
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_detected_events_memoryId` ON `detected_events` (`memoryId`)"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_detected_events_status_eventTime` ON `detected_events` (`status`, `eventTime`)"
            )
        }
    }

    val ALL = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
}
