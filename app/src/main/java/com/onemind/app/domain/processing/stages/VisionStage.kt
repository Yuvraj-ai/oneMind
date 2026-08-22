package com.onemind.app.domain.processing.stages

import com.onemind.app.domain.model.Memory
import com.onemind.app.domain.model.VisionResult
import com.onemind.app.domain.processing.*
import com.onemind.app.domain.repository.DerivedDataRepository
import javax.inject.Inject

/**
 * Describes the images in a Memory, when the user's chosen model can.
 *
 * This is what makes a Memory holding only a photograph findable at all. OCR
 * reads text off a screenshot; nothing reads a picture of a mountain, and without
 * a description that Memory is retrievable only by its capture date.
 *
 * Capability-aware by design. A user running a small local text-only model has
 * made a legitimate choice, so the absence of vision is recorded as
 * [StageStatus.NOT_SUPPORTED] rather than reported as a failure. Those two mean
 * very different things to someone looking at their own Memory.
 */
class VisionStage @Inject constructor(
    private val imageDescriber: ImageDescriber,
    private val derivedDataRepository: DerivedDataRepository
) : ProcessingStage {

    override val id = StageId.VISION

    override suspend fun process(memory: Memory): StageResult {
        val images = memory.imageBlocks()
        if (images.isEmpty()) return StageResult.Skipped

        // Asked before any image is touched. Nothing leaves the device unless a
        // provider the user chose is able and configured to receive it.
        if (!imageDescriber.isAvailable()) {
            derivedDataRepository.saveVisionResults(
                images.map { block ->
                    VisionResult(
                        memoryId = memory.id,
                        contentBlockId = block.id,
                        status = StageStatus.NOT_SUPPORTED,
                        description = "",
                        providerModel = null
                    )
                }
            )
            return StageResult.NotSupported
        }

        val model = imageDescriber.modelIdentifier()
        val results = images.map { block ->
            describeInto(
                memoryId = memory.id,
                contentBlockId = block.id,
                imagePath = block.content,
                model = model
            )
        }

        derivedDataRepository.saveVisionResults(results)

        return aggregatePerImageStatuses(results.map { it.status }, stageLabel = "Vision")
    }

    private suspend fun describeInto(
        memoryId: Long,
        contentBlockId: Long,
        imagePath: String,
        model: String?
    ): VisionResult {
        val described = imageDescriber.describe(imagePath, DESCRIPTION_PROMPT)

        val description = described.getOrNull()?.trim().orEmpty()
        val status = when {
            described.isFailure -> StageStatus.FAILED
            description.isEmpty() -> StageStatus.EMPTY
            else -> StageStatus.SUCCESS
        }

        return VisionResult(
            memoryId = memoryId,
            contentBlockId = contentBlockId,
            status = status,
            description = description,
            // Recorded only when there is a description to attribute.
            providerModel = model.takeIf { status == StageStatus.SUCCESS }
        )
    }

    companion object {
        /**
         * Asks for what is visibly there and nothing more.
         *
         * The restraint is deliberate. A description that speculates about mood or
         * intent would be indexed and searched as though it were fact, so the
         * prompt forbids inventing what the image does not show. Kept as a
         * constant so it can be tuned without hunting through logic.
         */
        const val DESCRIPTION_PROMPT =
            "Describe this image in one or two factual sentences. " +
                "State only what is visibly present. " +
                "Do not speculate about context, mood, or intent."
    }
}
