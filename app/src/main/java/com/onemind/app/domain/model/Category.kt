package com.onemind.app.domain.model

/**
 * One entry in the controlled category vocabulary.
 *
 * Assignments reference [id] rather than [name], so renaming a category later
 * cannot orphan the Memories filed under it.
 */
data class Category(
    val id: Long = 0,
    val name: String,
    /**
     * Parent category, for nesting.
     *
     * Always null in v1 and nothing reads it. It exists because the dictionary is
     * a table the application seeds, so the column is free now, whereas adding it
     * later would mean a migration over user data for a purely additive change.
     * No hierarchy behaviour is implemented — treat a non-null value as
     * unsupported until it is.
     */
    val parentId: Long? = null
)
