package com.onemind.app.domain.processing.stages

import com.onemind.app.domain.model.*
import com.onemind.app.domain.processing.*
import com.onemind.app.domain.repository.DerivedDataRepository
import javax.inject.Inject

/**
 * Pulls structured metadata out of everything a Memory says.
 *
 * Two halves with very different characters, deliberately kept apart:
 *
 * - **Links** are found by regex. Deterministic, entirely local, and run
 *   unconditionally. A Memory full of links is still usefully enriched for a user
 *   who configured no provider at all.
 * - **Dates and entities** need a language model. With local inference deferred
 *   (ADR-0002) that means a cloud provider the user opted into, and when there is
 *   none the stage still succeeds on the strength of the links alone.
 *
 * That split is why this stage rarely reports outright failure: losing the model
 * half costs some enrichment, not the Memory.
 */
class MetadataExtractionStage @Inject constructor(
    private val textGenerator: TextGenerator,
    private val derivedDataRepository: DerivedDataRepository
) : ProcessingStage {

    override val id = StageId.METADATA

    override suspend fun process(memory: Memory): StageResult {
        val sources = memory.allText()
        if (sources.isEmpty()) return StageResult.Skipped

        // Deterministic half, always.
        val urls = UrlExtractor.extractAll(memory.id, sources)
        if (urls.isNotEmpty()) {
            derivedDataRepository.saveUrls(urls)
        }

        // Model half, when there is a model.
        if (!textGenerator.isAvailable()) {
            return if (urls.isEmpty()) StageResult.NotSupported else StageResult.Success
        }

        val combined = sources.joinToString("\n") { (_, text) -> text }
        val response = textGenerator.generate(
            prompt = buildPrompt(combined),
            maxTokens = MAX_RESPONSE_TOKENS
        )

        val raw = response.getOrElse { error ->
            // The links are already saved, so the Memory keeps what it gained.
            return if (urls.isEmpty()) {
                StageResult.Failed(error.message ?: "Metadata extraction failed", error)
            } else {
                StageResult.Success
            }
        }

        val parsed = MetadataResponseParser.parse(raw)
            ?: return if (urls.isEmpty()) {
                StageResult.Failed("Model response contained no usable JSON")
            } else {
                StageResult.Success
            }

        saveEntities(memory.id, parsed.entities, sources)
        saveDates(memory.id, parsed.dates, sources)

        return when {
            urls.isNotEmpty() || !parsed.isEmpty -> StageResult.Success
            else -> StageResult.Empty
        }
    }

    private suspend fun saveEntities(
        memoryId: Long,
        entities: List<MetadataResponseParser.ParsedEntity>,
        sources: List<Pair<DerivedSource, String>>
    ) {
        if (entities.isEmpty()) return

        derivedDataRepository.saveEntities(
            entities.map { entity ->
                ExtractedEntity(
                    memoryId = memoryId,
                    name = entity.name,
                    entityType = entity.type,
                    confidence = entity.confidence,
                    source = attribute(entity.name, sources)
                )
            }
        )
    }

    private suspend fun saveDates(
        memoryId: Long,
        dates: List<MetadataResponseParser.ParsedDate>,
        sources: List<Pair<DerivedSource, String>>
    ) {
        if (dates.isEmpty()) return

        derivedDataRepository.saveDates(
            dates.map { date ->
                ExtractedDate(
                    memoryId = memoryId,
                    rawText = date.rawText,
                    parsedInstant = date.instant,
                    // Everything found here was mentioned *in* the content, so it
                    // is an event time. The Memory's own createdAt is the capture
                    // time and lives on the Memory, not here. Conflating the two
                    // is what makes temporal retrieval wrong.
                    isEventTime = true,
                    source = attribute(date.rawText, sources)
                )
            }
        )
    }

    /**
     * Work out which source a piece of extracted text came from.
     *
     * The model is given all the text at once, for one call rather than one per
     * source, so provenance is recovered afterwards by finding which source
     * actually contains the text. Approximate where a name appears in more than
     * one place — the first match wins — but honest, and far cheaper than
     * extracting per source.
     */
    private fun attribute(
        value: String,
        sources: List<Pair<DerivedSource, String>>
    ): DerivedSource =
        sources.firstOrNull { (_, text) -> text.contains(value, ignoreCase = true) }
            ?.first
            ?: DerivedSource.USER_TEXT

    private fun buildPrompt(text: String): String {
        // Shares the cap with every other model call rather than keeping a private
        // one that could drift. This stage runs before links are stored, so it
        // bounds plain text rather than building a full BoundedAnalysisInput.
        val bounded = BoundedAnalysisInput.boundText(text)

        return """
            Extract structured metadata from the text below.

            Reply with ONLY a JSON object, in exactly this shape:
            {
              "entities": [{"name": "...", "type": "...", "confidence": 0.0}],
              "dates": [{"text": "...", "iso8601": "..."}]
            }

            "type" must be one of: PERSON, ORGANIZATION, PRODUCT, PLACE, EVENT,
            TECHNOLOGY, OTHER.

            Rules:
            - Include only what the text explicitly states. Do not infer or invent.
            - Omit "confidence" entirely rather than guessing a value.
            - Set "iso8601" only when the date is unambiguous; otherwise use null.
            - Keep "text" for a date exactly as it appears in the source.
            - Use empty arrays if there is nothing to report.

            Text:
            $bounded
        """.trimIndent()
    }

    companion object {
        /** Enough for a substantial entity list without inviting rambling. */
        const val MAX_RESPONSE_TOKENS = 1_024
    }
}
