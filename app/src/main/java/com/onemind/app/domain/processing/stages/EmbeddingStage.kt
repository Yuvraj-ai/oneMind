package com.onemind.app.domain.processing.stages

import com.onemind.app.domain.model.Memory
import com.onemind.app.domain.model.MemoryEmbedding
import com.onemind.app.domain.processing.*
import com.onemind.app.domain.repository.DerivedDataRepository
import javax.inject.Inject

/**
 * Gives a Memory a vector, so it can be found by meaning rather than by exact
 * wording.
 *
 * This is the stage that completes the fully-local path: capture, then OCR, then
 * embed, and the Memory is searchable with no provider configured and nothing
 * having left the device. Everything after this in the pipeline needs a
 * generative model; this does not.
 *
 * Embeds the Memory's prose — what the user typed, what OCR read off the images,
 * what a model described. Deliberately not the extracted entity list: the model
 * embeds sentences, and appending "Google, OpenAI, Bangalore" would dilute the
 * semantic signal with keyword soup. Entities serve retrieval better as a
 * separate structured signal, which is why they are indexed by name.
 */
class EmbeddingStage @Inject constructor(
    private val embeddingGenerator: EmbeddingGenerator,
    private val derivedDataRepository: DerivedDataRepository
) : ProcessingStage {

    override val id = StageId.EMBEDDING

    override suspend fun process(memory: Memory): StageResult {
        val text = embeddableText(memory)
        if (text.isBlank()) {
            // An image with no readable text and no description. There is nothing
            // to embed, and embedding an empty string would put a meaningless
            // vector in the index.
            return StageResult.Empty
        }

        val generated = embeddingGenerator.embed(text)
        val vector = generated.getOrElse { error ->
            return StageResult.Failed(
                reason = error.message ?: "Embedding failed",
                cause = error
            )
        }

        derivedDataRepository.saveEmbedding(
            MemoryEmbedding(
                memoryId = memory.id,
                vector = vector,
                // From the model, never a constant. A model swap that changes the
                // vector width is then visible in the data rather than silent.
                dimensions = vector.size,
                modelId = embeddingGenerator.modelId
            )
        )

        return StageResult.Success
    }

    /**
     * The prose to embed, joined in a stable order.
     *
     * Order is fixed so that re-processing an unchanged Memory produces the same
     * vector, which keeps the index stable across edits elsewhere.
     */
    private fun embeddableText(memory: Memory): String =
        memory.allText()
            .map { (_, text) -> text.trim() }
            .filter { it.isNotEmpty() }
            .joinToString(separator = "\n")
}
