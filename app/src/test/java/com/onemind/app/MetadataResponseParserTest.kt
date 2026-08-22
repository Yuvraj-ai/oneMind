package com.onemind.app

import com.onemind.app.domain.model.EntityType
import com.onemind.app.domain.processing.MetadataResponseParser
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

/**
 * Reading model output, which is the most hostile input in the codebase.
 *
 * A model will fence its JSON, preface it with prose, mislabel a type, put a
 * string where a number belongs, or return nothing usable. None of that may crash
 * a pipeline stage, and none of it may produce invented metadata — fabricated
 * entities would be indexed and searched as though they were fact.
 */
class MetadataResponseParserTest {

    // --- getting at the JSON ----------------------------------------------

    @Test
    fun `parses a bare json object`() {
        val parsed = MetadataResponseParser.parse(
            """{"entities":[{"name":"Google","type":"ORGANIZATION"}],"dates":[]}"""
        )

        assertEquals("Google", parsed!!.entities.single().name)
    }

    @Test
    fun `parses json inside a markdown fence`() {
        val parsed = MetadataResponseParser.parse(
            """
            ```json
            {"entities":[{"name":"Qwen","type":"PRODUCT"}],"dates":[]}
            ```
            """.trimIndent()
        )

        assertEquals("Qwen", parsed!!.entities.single().name)
    }

    @Test
    fun `parses json buried in prose`() {
        val parsed = MetadataResponseParser.parse(
            """Here is the extracted metadata:
               {"entities":[{"name":"Bangalore","type":"PLACE"}],"dates":[]}
               Let me know if you need more."""
        )

        assertEquals("Bangalore", parsed!!.entities.single().name)
    }

    // --- refusing to guess ------------------------------------------------

    @Test
    fun `returns null when there is no json at all`() {
        assertNull(MetadataResponseParser.parse("I cannot help with that request."))
    }

    @Test
    fun `returns null for blank input`() {
        assertNull(MetadataResponseParser.parse(""))
        assertNull(MetadataResponseParser.parse("   \n "))
    }

    @Test
    fun `returns null for malformed json rather than salvaging fragments`() {
        assertNull(MetadataResponseParser.parse("""{"entities": [{"name": "Goog"""))
    }

    @Test
    fun `valid but empty json is empty, not null`() {
        // A meaningful distinction: the model answered, and the answer was nothing.
        val parsed = MetadataResponseParser.parse("""{"entities":[],"dates":[]}""")

        assertNotNull(parsed)
        assertTrue(parsed!!.isEmpty)
    }

    @Test
    fun `an object with unrelated keys parses as empty`() {
        val parsed = MetadataResponseParser.parse("""{"summary":"something else"}""")

        assertNotNull(parsed)
        assertTrue(parsed!!.isEmpty)
    }

    // --- entities ---------------------------------------------------------

    @Test
    fun `maps every known entity type`() {
        val json = """
            {"entities":[
              {"name":"Ada","type":"PERSON"},
              {"name":"Google","type":"ORGANIZATION"},
              {"name":"Qwen","type":"PRODUCT"},
              {"name":"Bangalore","type":"PLACE"},
              {"name":"AI Summit","type":"EVENT"},
              {"name":"Kotlin","type":"TECHNOLOGY"}
            ],"dates":[]}
        """
        val types = MetadataResponseParser.parse(json)!!.entities.map { it.type }

        assertEquals(
            listOf(
                EntityType.PERSON, EntityType.ORGANIZATION, EntityType.PRODUCT,
                EntityType.PLACE, EntityType.EVENT, EntityType.TECHNOLOGY
            ),
            types
        )
    }

    @Test
    fun `type matching is case insensitive`() {
        val parsed = MetadataResponseParser.parse(
            """{"entities":[{"name":"Google","type":"organization"}],"dates":[]}"""
        )

        assertEquals(EntityType.ORGANIZATION, parsed!!.entities.single().type)
    }

    @Test
    fun `an unrecognised type becomes OTHER rather than dropping the entity`() {
        // The model found something real; it just labelled it in a way we do not
        // model. Losing the name would be worse than losing the label.
        val parsed = MetadataResponseParser.parse(
            """{"entities":[{"name":"Chai","type":"BEVERAGE"}],"dates":[]}"""
        )

        assertEquals(EntityType.OTHER, parsed!!.entities.single().type)
        assertEquals("Chai", parsed.entities.single().name)
    }

    @Test
    fun `a missing type becomes OTHER`() {
        val parsed = MetadataResponseParser.parse(
            """{"entities":[{"name":"Something"}],"dates":[]}"""
        )

        assertEquals(EntityType.OTHER, parsed!!.entities.single().type)
    }

    @Test
    fun `entities without a name are discarded`() {
        val parsed = MetadataResponseParser.parse(
            """{"entities":[{"type":"PERSON"},{"name":"","type":"PERSON"},{"name":"Ada","type":"PERSON"}],"dates":[]}"""
        )

        assertEquals(listOf("Ada"), parsed!!.entities.map { it.name })
    }

    @Test
    fun `duplicate names are collapsed, so one mention does not outweigh others`() {
        val parsed = MetadataResponseParser.parse(
            """{"entities":[{"name":"Google","type":"ORGANIZATION"},{"name":"google","type":"ORGANIZATION"}],"dates":[]}"""
        )

        assertEquals(1, parsed!!.entities.size)
    }

    @Test
    fun `non-object array items are skipped without failing the parse`() {
        val parsed = MetadataResponseParser.parse(
            """{"entities":["just a string",{"name":"Ada","type":"PERSON"}],"dates":[]}"""
        )

        assertEquals(listOf("Ada"), parsed!!.entities.map { it.name })
    }

    // --- confidence, which must never be invented -------------------------

    @Test
    fun `reads a supplied confidence`() {
        val parsed = MetadataResponseParser.parse(
            """{"entities":[{"name":"Google","type":"ORGANIZATION","confidence":0.94}],"dates":[]}"""
        )

        assertEquals(0.94f, parsed!!.entities.single().confidence!!, 0.001f)
    }

    @Test
    fun `an absent confidence stays absent rather than defaulting`() {
        val parsed = MetadataResponseParser.parse(
            """{"entities":[{"name":"Google","type":"ORGANIZATION"}],"dates":[]}"""
        )

        assertNull(parsed!!.entities.single().confidence)
    }

    @Test
    fun `an explicitly null confidence stays null`() {
        val parsed = MetadataResponseParser.parse(
            """{"entities":[{"name":"G","type":"ORGANIZATION","confidence":null}],"dates":[]}"""
        )

        assertNull(parsed!!.entities.single().confidence)
    }

    @Test
    fun `a confidence outside zero to one is rejected as meaningless`() {
        val high = MetadataResponseParser.parse(
            """{"entities":[{"name":"G","type":"ORGANIZATION","confidence":94}],"dates":[]}"""
        )
        val negative = MetadataResponseParser.parse(
            """{"entities":[{"name":"H","type":"ORGANIZATION","confidence":-0.5}],"dates":[]}"""
        )

        assertNull(high!!.entities.single().confidence)
        assertNull(negative!!.entities.single().confidence)
    }

    @Test
    fun `a numeric confidence given as a string is still read`() {
        val parsed = MetadataResponseParser.parse(
            """{"entities":[{"name":"G","type":"ORGANIZATION","confidence":"0.8"}],"dates":[]}"""
        )

        assertEquals(0.8f, parsed!!.entities.single().confidence!!, 0.001f)
    }

    @Test
    fun `a non-numeric confidence is rejected`() {
        val parsed = MetadataResponseParser.parse(
            """{"entities":[{"name":"G","type":"ORGANIZATION","confidence":"high"}],"dates":[]}"""
        )

        assertNull(parsed!!.entities.single().confidence)
    }

    // --- dates ------------------------------------------------------------

    @Test
    fun `keeps the date text exactly as it appeared`() {
        val parsed = MetadataResponseParser.parse(
            """{"entities":[],"dates":[{"text":"September 15 at 10 AM","iso8601":"2026-09-15T10:00:00Z"}]}"""
        )

        assertEquals("September 15 at 10 AM", parsed!!.dates.single().rawText)
    }

    @Test
    fun `parses a full instant`() {
        val parsed = MetadataResponseParser.parse(
            """{"entities":[],"dates":[{"text":"x","iso8601":"2026-09-15T10:00:00Z"}]}"""
        )

        assertEquals(Instant.parse("2026-09-15T10:00:00Z"), parsed!!.dates.single().instant)
    }

    @Test
    fun `parses a local date time without a zone`() {
        val parsed = MetadataResponseParser.parse(
            """{"entities":[],"dates":[{"text":"x","iso8601":"2026-09-15T10:00:00"}]}"""
        )

        assertEquals(Instant.parse("2026-09-15T10:00:00Z"), parsed!!.dates.single().instant)
    }

    @Test
    fun `parses a bare date, anchored at midnight UTC`() {
        val parsed = MetadataResponseParser.parse(
            """{"entities":[],"dates":[{"text":"September 15","iso8601":"2026-09-15"}]}"""
        )

        assertEquals(Instant.parse("2026-09-15T00:00:00Z"), parsed!!.dates.single().instant)
    }

    @Test
    fun `an unresolvable date keeps its text with a null instant`() {
        // "sometime next spring" is real information even unresolved, and the text
        // is what the user actually saw.
        val parsed = MetadataResponseParser.parse(
            """{"entities":[],"dates":[{"text":"sometime next spring","iso8601":null}]}"""
        )

        val date = parsed!!.dates.single()
        assertEquals("sometime next spring", date.rawText)
        assertNull(date.instant)
    }

    @Test
    fun `a garbage iso value yields a null instant rather than failing`() {
        val parsed = MetadataResponseParser.parse(
            """{"entities":[],"dates":[{"text":"x","iso8601":"next Tuesday-ish"}]}"""
        )

        assertNull(parsed!!.dates.single().instant)
    }

    @Test
    fun `the literal string null is treated as no value`() {
        val parsed = MetadataResponseParser.parse(
            """{"entities":[],"dates":[{"text":"x","iso8601":"null"}]}"""
        )

        assertNull(parsed!!.dates.single().instant)
    }

    @Test
    fun `dates without text are discarded`() {
        val parsed = MetadataResponseParser.parse(
            """{"entities":[],"dates":[{"iso8601":"2026-09-15"},{"text":"Sept 15","iso8601":"2026-09-15"}]}"""
        )

        assertEquals(listOf("Sept 15"), parsed!!.dates.map { it.rawText })
    }

    @Test
    fun `duplicate date texts are collapsed`() {
        val parsed = MetadataResponseParser.parse(
            """{"entities":[],"dates":[{"text":"Sept 15"},{"text":"sept 15"}]}"""
        )

        assertEquals(1, parsed!!.dates.size)
    }
}
