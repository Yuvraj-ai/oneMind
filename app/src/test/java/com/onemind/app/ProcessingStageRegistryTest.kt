package com.onemind.app

import com.onemind.app.domain.processing.ImageDescriber
import com.onemind.app.domain.processing.ProcessingStageRegistry
import com.onemind.app.domain.processing.StageId
import com.onemind.app.domain.processing.TextRecognizer
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
 * every stage added from here on relies on it. Vision must follow OCR, and
 * metadata extraction will need both.
 */
class ProcessingStageRegistryTest {

    private val recognizer: TextRecognizer = mockk()
    private val describer: ImageDescriber = mockk()
    private val derived: DerivedDataRepository = mockk()

    private fun ocr() = OcrStage(recognizer, derived)
    private fun vision() = VisionStage(describer, derived)

    @Test
    fun `OCR runs before vision`() {
        val ordered = ProcessingStageRegistry(setOf(vision(), ocr())).all()

        assertEquals(listOf(StageId.OCR, StageId.VISION), ordered.map { it.id })
    }

    @Test
    fun `order does not depend on the order stages were bound`() {
        val oneWay = ProcessingStageRegistry(setOf(ocr(), vision())).all().map { it.id }
        val otherWay = ProcessingStageRegistry(setOf(vision(), ocr())).all().map { it.id }

        assertEquals(oneWay, otherWay)
    }

    @Test
    fun `every registered stage is returned`() {
        assertEquals(2, ProcessingStageRegistry(setOf(ocr(), vision())).all().size)
    }

    @Test
    fun `an empty registry is legal`() {
        assertTrue(ProcessingStageRegistry(emptySet()).all().isEmpty())
    }

    @Test
    fun `stage ids are unique across registered stages`() {
        val ids = ProcessingStageRegistry(setOf(ocr(), vision())).all().map { it.id }

        // Two stages sharing an id would make their relative order arbitrary.
        assertEquals(ids.size, ids.toSet().size)
    }
}
