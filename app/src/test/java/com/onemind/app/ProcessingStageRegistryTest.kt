package com.onemind.app

import com.onemind.app.domain.processing.EmbeddingGenerator
import com.onemind.app.domain.processing.ImageDescriber
import com.onemind.app.domain.processing.ProcessingStageRegistry
import com.onemind.app.domain.processing.StageId
import com.onemind.app.domain.processing.TextRecognizer
import com.onemind.app.domain.processing.stages.EmbeddingStage
import com.onemind.app.domain.processing.stages.OcrStage
import com.onemind.app.domain.processing.stages.VisionStage
import com.onemind.app.domain.repository.DerivedDataRepository
import io.mockk.mockk
import org.junit.Assert.*
import org.junit.Test

/**
 * Ordering of the *real* stages, as opposed to the fakes
 * [ProcessingPipelineTest] uses.
 *
 * Worth its own test because the ordering is implicit: it comes from the
 * declaration order of [StageId], not from anything visible at the call site, and
 * every stage added from here on relies on it. The dependencies are real: vision
 * reads nothing from OCR but must not precede it in the recorded order, and
 * embedding reads both OCR text and vision descriptions, so it must run last of
 * the three.
 */
class ProcessingStageRegistryTest {

    private val recognizer: TextRecognizer = mockk()
    private val describer: ImageDescriber = mockk()
    private val generator: EmbeddingGenerator = mockk()
    private val derived: DerivedDataRepository = mockk()

    private fun ocr() = OcrStage(recognizer, derived)
    private fun vision() = VisionStage(describer, derived)
    private fun embedding() = EmbeddingStage(generator, derived)

    @Test
    fun `stages run OCR then vision then embedding`() {
        val ordered = ProcessingStageRegistry(setOf(embedding(), vision(), ocr())).all()

        assertEquals(
            listOf(StageId.OCR, StageId.VISION, StageId.EMBEDDING),
            ordered.map { it.id }
        )
    }

    @Test
    fun `embedding runs after both OCR and vision, whose output it reads`() {
        val ordered = ProcessingStageRegistry(setOf(embedding(), ocr(), vision())).all()
            .map { it.id }

        assertTrue(ordered.indexOf(StageId.EMBEDDING) > ordered.indexOf(StageId.OCR))
        assertTrue(ordered.indexOf(StageId.EMBEDDING) > ordered.indexOf(StageId.VISION))
    }

    @Test
    fun `order does not depend on the order stages were bound`() {
        val oneWay = ProcessingStageRegistry(setOf(ocr(), vision(), embedding())).all().map { it.id }
        val otherWay = ProcessingStageRegistry(setOf(embedding(), vision(), ocr())).all().map { it.id }

        assertEquals(oneWay, otherWay)
    }

    @Test
    fun `every registered stage is returned`() {
        assertEquals(3, ProcessingStageRegistry(setOf(ocr(), vision(), embedding())).all().size)
    }

    @Test
    fun `an empty registry is legal`() {
        assertTrue(ProcessingStageRegistry(emptySet()).all().isEmpty())
    }

    @Test
    fun `stage ids are unique across registered stages`() {
        val ids = ProcessingStageRegistry(setOf(ocr(), vision(), embedding())).all().map { it.id }

        // Two stages sharing an id would make their relative order arbitrary.
        assertEquals(ids.size, ids.toSet().size)
    }
}
