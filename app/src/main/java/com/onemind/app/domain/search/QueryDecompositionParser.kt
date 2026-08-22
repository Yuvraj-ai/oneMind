package com.onemind.app.domain.search

import com.onemind.app.domain.model.Category
import com.onemind.app.domain.model.SourceType
import org.json.JSONObject

/**
 * Reads a model's decomposition of a query.
 *
 * Same discipline as #14's categorisation: parse permissively, validate strictly.
 * Anything the model asserts is checked against a closed set before it is believed,
 * so a malformed or inventive response degrades to fewer constraints rather than
 * wrong ones.
 *
 * Wrong constraints are the failure mode worth designing against, because they are
 * *hard filters*. An invented source or a hallucinated date silently removes the
 * Memory the user is looking for and gives them no way to tell why the search
 * "found nothing".
 */
object QueryDecompositionParser {

    /**
     * A model's proposed reading of a query, before validation.
     *
     * Deliberately all-nullable and all-optional: a model that returns only one
     * useful field should have that field used, not have the whole response
     * discarded.
     */
    data class Decomposition(
        val semanticQuery: String? = null,
        val sourceType: SourceType? = null,
        val sourcePackage: String? = null,
        val categoryNames: List<String> = emptyList()
    )

    /**
     * Parse a response, or null when nothing usable can be read from it.
     *
     * Null tells the caller to fall back to the literal query rather than to
     * proceed with an empty decomposition, which would look like "the model
     * confidently found no constraints".
     */
    fun parse(response: String): Decomposition? {
        val json = extractJsonObject(response) ?: return null

        val semantic = json.optString("semantic").trim().ifBlank { null }

        val sourceType = json.optString("sourceType").trim().ifBlank { null }
            ?.let { raw ->
                // Matched against the enum, so an invented source name is dropped
                // rather than becoming a filter that excludes everything.
                SourceType.entries.firstOrNull { it.name.equals(raw, ignoreCase = true) }
            }

        val sourcePackage = json.optString("sourcePackage").trim().ifBlank { null }

        val categories = json.optJSONArray("categories")?.let { array ->
            (0 until array.length()).mapNotNull { array.optString(it)?.trim()?.ifBlank { null } }
        }.orEmpty()

        // A response that produced nothing at all is not a decomposition.
        if (semantic == null && sourceType == null && sourcePackage == null && categories.isEmpty()) {
            return null
        }

        return Decomposition(
            semanticQuery = semantic,
            sourceType = sourceType,
            sourcePackage = sourcePackage,
            categoryNames = categories
        )
    }

    /**
     * Resolve proposed category names against the seeded vocabulary.
     *
     * Exact match after trimming and case-folding, and nothing fuzzy — the same
     * guarantee as #14, for the same reason. Approximate matching is how a model
     * introduces a category by another name, and here it would also skew ranking
     * toward whatever it half-recognised.
     */
    fun resolveCategories(names: List<String>, vocabulary: List<Category>): List<Category> {
        if (names.isEmpty() || vocabulary.isEmpty()) return emptyList()
        val byName = vocabulary.associateBy { it.name.trim().lowercase() }
        return names.mapNotNull { byName[it.trim().lowercase()] }.distinct()
    }

    /**
     * Pull the first JSON object out of a response.
     *
     * Models wrap JSON in prose and code fences regardless of instructions, so the
     * braces are located rather than the response being required to be clean.
     */
    private fun extractJsonObject(response: String): JSONObject? {
        val start = response.indexOf('{')
        val end = response.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        return try {
            JSONObject(response.substring(start, end + 1))
        } catch (e: Exception) {
            null
        }
    }
}
