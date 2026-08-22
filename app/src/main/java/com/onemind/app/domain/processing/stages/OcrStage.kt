package com.onemind.app.domain.processing.stages

import com.onemind.app.domain.model.Memory
import com.onemind.app.domain.model.OcrResult
import com.onemind.app.domain.processing.*
import com.onemind.app.domain.repository.DerivedDataRepository
import javax.inject.Inject

/**
 * Reads text out of every image in a Memory.
 *
 * The first stage in the pipeline, and the one every later stage leans on: a
 * screenshot is mostly text, and until it has been read the Memory is only
 * findable by whatever the user typed alongside it.
 *
 * Results are recorded per image, so one unreadable file among five does not
 * hide the text in the other four.
 */
class OcrStage @Inject constructor(
    private val textRecognizer: TextRecognizer,
    private val derivedDataRepository: DerivedDataRepository
) : ProcessingStage {

    override val id = StageId.OCR

    override suspend fun process(memory: Memory): StageResult {
        val images = memory.imageBlocks()
        if (images.isEmpty()) return StageResult.Skipped

        val results = images.map { block ->
            recognizeInto(memoryId = memory.id, contentBlockId = block.id, imagePath = block.content)
        }

        derivedDataRepository.saveOcrResults(results)

        return aggregatePerImageStatuses(results.map { it.status }, stageLabel = "OCR")
    }

    private suspend fun recognizeInto(
        memoryId: Long,
        contentBlockId: Long,
        imagePath: String
    ): OcrResult {
        val recognized = textRecognizer.recognize(imagePath)

        val text = recognized.getOrNull()?.trim().orEmpty()
        val status = when {
            recognized.isFailure -> StageStatus.FAILED
            // Ran fine and found nothing. A photo of a mountain has no text,
            // and that is an answer, not an error.
            text.isEmpty() -> StageStatus.EMPTY
            else -> StageStatus.SUCCESS
        }

        return OcrResult(
            memoryId = memoryId,
            contentBlockId = contentBlockId,
            status = status,
            extractedText = text
        )
    }
}
