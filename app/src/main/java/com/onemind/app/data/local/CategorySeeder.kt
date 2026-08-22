package com.onemind.app.data.local

import androidx.sqlite.db.SupportSQLiteDatabase
import com.onemind.app.domain.categories.CategoryDictionary

/**
 * Puts the category vocabulary into the database.
 *
 * Two paths need it — a fresh install creating the schema, and an existing
 * install migrating from v2 — and they must produce identical tables. Sharing one
 * function is what guarantees that, rather than two copies of a long INSERT list
 * that drift the first time a category is added.
 */
object CategorySeeder {

    /**
     * Insert every dictionary category, skipping any already present.
     *
     * Idempotent, by way of `INSERT OR IGNORE` against the unique index on name.
     * That matters because it makes the seeder safe to call on a database that has
     * been partially seeded, which is the state a crashed migration would leave.
     *
     * Values are bound rather than interpolated, so a category name containing an
     * apostrophe cannot break the statement.
     */
    fun seed(db: SupportSQLiteDatabase) {
        CategoryDictionary.ALL.forEach { name ->
            db.execSQL(
                "INSERT OR IGNORE INTO `categories` (`name`, `parentId`) VALUES (?, NULL)",
                arrayOf<Any?>(name)
            )
        }
    }
}
