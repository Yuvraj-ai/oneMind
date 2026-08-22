package com.onemind.app.domain.processing

import com.onemind.app.domain.model.EntityType
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeParseException

/**
 * Reads the model's answer to the metadata prompt.
 *
 * Kept separate from the stage and free of Android types, because this is where
 * the real hostility lives: a model will wrap JSON in markdown fences, prefix it
 * with "Here is the JSON:", invent fields, return a string where a number was
 * asked for, or produce nothing usable at all. None of that should crash a
 * pipeline stage, and all of it is cheap to pin down in tests when the parsing
 * sits on its own.
 *
 * The guiding rule is that unparseable input yields *nothing*, never a guess.
 * Fabricated metadata would be indexed and searched as though it were fact.
 */
object MetadataResponseParser {

    /** What the parser managed to recover. Absent fields simply were not found. */
    data class Parsed(
        val entities: List<ParsedEntity> = emptyList(),
        val dates: List<ParsedDate> = emptyList()
    ) {
        val isEmpty: Boolean get() = entities.isEmpty() && dates.isEmpty()
    }

    data class ParsedEntity(
        val name: String,
        val type: EntityType,
        /** Null unless the model supplied a usable number. Never invented. */
        val confidence: Float?
    )

    data class ParsedDate(
        val rawText: String,
        /** Null when the model could not resolve it to a point in time. */
        val instant: Instant?
    )

    /**
     * Parse a model response.
     *
     * Returns null when no JSON object could be found at all, which the caller
     * treats as a failure. An empty [Parsed] means valid JSON that contained
     * nothing — a different and legitimate outcome.
     */
    fun parse(response: String): Parsed? {
        val json = extractJsonObject(response) ?: return null

        return Parsed(
            entities = parseEntities(json),
            dates = parseDates(json)
        )
    }

    /**
     * Find the JSON object inside whatever the model actually said.
     *
     * Handles a bare object, a ```json fenced block, and an object buried in
     * prose, by taking the span between the first `{` and the last `}`.
     */
    private fun extractJsonObject(response: String): JSONObject? {
        if (response.isBlank()) return null

        val start = response.indexOf('{')
        val end = response.lastIndexOf('}')
        if (start < 0 || end <= start) return null

        return try {
            JSONObject(response.substring(start, end + 1))
        } catch (_: Exception) {
            null
        }
    }

    private fun parseEntities(json: JSONObject): List<ParsedEntity> {
        val array = json.optJSONArray("entities") ?: return emptyList()
        val result = mutableListOf<ParsedEntity>()
        val seen = mutableSetOf<String>()

        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue

            val name = item.optString("name").trim()
            if (name.isEmpty()) continue

            // The same name twice adds nothing and would inflate its weight.
            if (!seen.add(name.lowercase())) continue

            result.add(
                ParsedEntity(
                    name = name,
                    type = parseEntityType(item.optString("type")),
                    confidence = parseConfidence(item)
                )
            )
        }

        return result
    }

    /**
     * Map the model's type string onto the closed set.
     *
     * Anything unrecognised becomes [EntityType.OTHER] rather than being dropped:
     * the model found something real, it just labelled it in a way we do not
     * model.
     */
    private fun parseEntityType(raw: String): EntityType =
        EntityType.entries.firstOrNull { it.name.equals(raw.trim(), ignoreCase = true) }
            ?: EntityType.OTHER

    /**
     * Read a confidence only if it is genuinely a number in range.
     *
     * Absent, non-numeric, or out-of-range all give null. The schema treats
     * confidence as optional precisely so it can be absent rather than guessed.
     */
    private fun parseConfidence(item: JSONObject): Float? {
        if (!item.has("confidence")) return null
        if (item.isNull("confidence")) return null

        val value = when (val raw = item.opt("confidence")) {
            is Number -> raw.toDouble()
            is String -> raw.toDoubleOrNull() ?: return null
            else -> return null
        }

        return value.toFloat().takeIf { it in 0f..1f }
    }

    private fun parseDates(json: JSONObject): List<ParsedDate> {
        val array = json.optJSONArray("dates") ?: return emptyList()
        val result = mutableListOf<ParsedDate>()
        val seen = mutableSetOf<String>()

        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue

            val text = item.optString("text").trim()
            if (text.isEmpty()) continue
            if (!seen.add(text.lowercase())) continue

            result.add(
                ParsedDate(
                    rawText = text,
                    instant = parseIso8601(item.optString("iso8601"))
                )
            )
        }

        return result
    }

    /**
     * Parse an ISO-8601 value, accepting a full timestamp or a bare date.
     *
     * A bare date is anchored at midnight UTC. That is a choice rather than a
     * fact — "September 15" has no timezone — and it is why [ParsedDate.rawText]
     * is always kept: the text the user actually saw survives regardless of how
     * it was resolved.
     */
    private fun parseIso8601(raw: String): Instant? {
        val value = raw.trim()
        if (value.isEmpty() || value.equals("null", ignoreCase = true)) return null

        return try {
            Instant.parse(value)
        } catch (_: DateTimeParseException) {
            try {
                LocalDateTime.parse(value).toInstant(ZoneOffset.UTC)
            } catch (_: DateTimeParseException) {
                try {
                    LocalDate.parse(value).atStartOfDay().toInstant(ZoneOffset.UTC)
                } catch (_: DateTimeParseException) {
                    null
                }
            }
        }
    }
}
