package com.onemind.app.domain.processing

/**
 * Identity of each enrichment stage.
 *
 * Declaration order *is* execution order: later stages consume the derived data
 * earlier ones persist (metadata extraction reads OCR text; summarisation reads
 * both). Adding a stage means adding a constant here in the right position, so
 * ordering can never be expressed as a stringly-typed lookup that silently
 * misplaces an unrecognised stage.
 */
enum class StageId {
    OCR,
    VISION,
    METADATA,
    EMBEDDING,
    CATEGORIZATION,
    SUMMARIZATION
}
