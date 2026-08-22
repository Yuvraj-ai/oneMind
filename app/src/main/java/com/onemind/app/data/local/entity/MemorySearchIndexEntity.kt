package com.onemind.app.data.local.entity

import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.PrimaryKey

/**
 * Full-text search index. One row per Memory, holding everything searchable
 * about it concatenated into a single document.
 *
 * ## Why denormalised rather than Room's external-content FTS
 *
 * A Memory's searchable text is spread across six tables: `content_blocks` (what
 * the user typed), `ocr_results` (text read off screenshots), `vision_results`
 * (image descriptions), `memory_summaries`, `extracted_entities` (names), and
 * `extracted_urls` (domains). Room's `@Fts4(contentEntity = ...)` binds an FTS
 * table to exactly **one** entity, so it cannot express a document assembled from
 * six.
 *
 * The alternative — six FTS tables queried with a UNION — is worse in two
 * concrete ways. Relevance scoring would be per-table and incomparable, so
 * ranking across them would require inventing a normalisation that FTS is already
 * doing correctly within one table. And every query would fan out six ways
 * against tables of very different sizes.
 *
 * So the text is duplicated. That is a real cost, paid deliberately: it buys one
 * coherent relevance score per Memory from a single `MATCH`.
 *
 * ## Consistency
 *
 * The duplicate is only correct if it is rebuilt whenever the source changes,
 * which is why indexing is a pipeline stage rather than a repository detail — the
 * pipeline already owns "derived data is stale, rebuild it".
 *
 * `rowid` is the Memory's id. FTS4 tables have an implicit integer `rowid`, and
 * reusing the Memory id for it means the join back to `memories` needs no extra
 * column and no extra index.
 */
@Fts4
@Entity(tableName = "memory_search_index")
data class MemorySearchIndexEntity(
    /**
     * Maps to the FTS table's implicit `rowid`, and is the Memory's own id.
     *
     * Room requires the name `rowid` for this to bind to the FTS rowid rather
     * than being treated as an indexed text column.
     */
    @PrimaryKey
    @androidx.room.ColumnInfo(name = "rowid")
    val memoryId: Long,

    /**
     * Every searchable string for this Memory, joined by newlines.
     *
     * Deliberately one column rather than one per source. FTS4 can weight columns,
     * but doing so would mean committing now to how much a summary is worth
     * against OCR text, with no data to justify the numbers. A single column keeps
     * relevance in FTS's hands until there is a reason to override it.
     */
    val searchableText: String
)
