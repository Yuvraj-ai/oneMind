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

    val ALL = arrayOf(MIGRATION_1_2)
}
