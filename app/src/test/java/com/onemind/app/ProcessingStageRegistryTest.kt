package com.onemind.app

import com.onemind.app.domain.processing.EmbeddingGenerator
import com.onemind.app.domain.processing.ImageDescriber
import com.onemind.app.domain.processing.ProcessingStageRegistry
import com.onemind.app.domain.processing.StageId
import com.onemind.app.domain.processing.TextGenerator
import com.onemind.app.domain.processing.TextRecognizer
import com.onemind.app.domain.processing.stages.CategorizationStage
import com.onemind.app.domain.processing.stages.EmbeddingStage
import com.onemind.app.domain.processing.stages.MetadataExtractionStage
import com.onemind.app.domain.processing.stages.OcrStage
import com.onemind.app.domain.processing.stages.SearchIndexStage
import com.onemind.app.domain.processing.stages.SummarizationStage
import com.onemind.app.domain.processing.stages.VisionStage
import com.onemind.app.domain.repository.DerivedDataRepository
import com.onemind.app.domain.repository.SearchIndexRepository
import io.mockk.mockk
import org.junit.Assert.*
import org.junit.Test

/**
 * Ordering of the *real* stages, as opposed to the fakes
 * [ProcessingPipelineTest] uses.
 *
 * Worth its own test because the ordering is implicit: it comes from the
 * declaration order of [StageId], not from anything visible at the call site, and
 * the dependencies between stages are real. Metadata extraction reads OCR text
 * and image descriptions; embedding reads all three; summarization reads
 * everything before it. Get the order wrong and each stage silently sees less
 * than it should.
 */
class ProcessingStageRegistryTest {

    private val recognizer: TextRecognizer = mockk()
    private val describer: ImageDescriber = mockk()
    private val generator: EmbeddingGenerator = mockk()
    private val textGenerator: TextGenerator = mockk()
    private val derived: DerivedDataRepository = mockk()
    private val searchIndex: SearchIndexRepository = mockk()

    private fun ocr() = OcrStage(recognizer, derived)
    private fun vision() = VisionStage(describer, derived)
    private fun metadata() = MetadataExtractionStage(textGenerator, derived)
    private fun embedding() = EmbeddingStage(generator, derived)
    private fun categorization() = CategorizationStage(textGenerator, derived)
    private fun summarization() = SummarizationStage(textGenerator, derived)
    private fun indexing() = SearchIndexStage(searchIndex)

    private fun allStages() =
        setOf(indexing(), summarization(), categorization(), embedding(), metadata(), vision(), ocr())

    @Test
    fun `stages run OCR then vision then metadata then embedding then categorization then summarization then indexing`() {
        val ordered = ProcessingStageRegistry(allStages()).all()

        assertEquals(
            listOf(
                StageId.OCR,
                StageId.VISION,
                StageId.METADATA,
                StageId.EMBEDDING,
                StageId.CATEGORIZATION,
                StageId.SUMMARIZATION,
                StageId.INDEXING
            ),
            ordered.map { it.id }
        )
    }

    @Test
    fun `indexing runs last, since it indexes what every other stage produced`() {
        // Anything ordered after it would be invisible to search.
        val ordered = ProcessingStageRegistry(allStages()).all().map { it.id }

        assertEquals(ordered.size - 1, ordered.indexOf(StageId.INDEXING))
    }

    @Test
    fun `metadata runs after OCR and vision, whose text it reads`() {
        val ordered = ProcessingStageRegistry(allStages()).all().map { it.id }

        assertTrue(ordered.indexOf(StageId.METADATA) > ordered.indexOf(StageId.OCR))
        assertTrue(ordered.indexOf(StageId.METADATA) > ordered.indexOf(StageId.VISION))
    }

    @Test
    fun `embedding runs after both OCR and vision, whose output it reads`() {
        val ordered = ProcessingStageRegistry(allStages()).all().map { it.id }

        assertTrue(ordered.indexOf(StageId.EMBEDDING) > ordered.indexOf(StageId.OCR))
        assertTrue(ordered.indexOf(StageId.EMBEDDING) > ordered.indexOf(StageId.VISION))
    }

    @Test
    fun `summarization runs after every stage whose output it reads`() {
        // It is no longer last — indexing follows it — so the meaningful assertion
        // is its position relative to the stages it actually consumes, not the end
        // of the list.
        val ordered = ProcessingStageRegistry(allStages()).all().map { it.id }

        assertTrue(ordered.indexOf(StageId.SUMMARIZATION) > ordered.indexOf(StageId.OCR))
        assertTrue(ordered.indexOf(StageId.SUMMARIZATION) > ordered.indexOf(StageId.VISION))
        assertTrue(ordered.indexOf(StageId.SUMMARIZATION) > ordered.indexOf(StageId.METADATA))
    }

    @Test
    fun `order does not depend on the order stages were bound`() {
        val oneWay = ProcessingStageRegistry(
            setOf(ocr(), vision(), metadata(), embedding(), categorization(), summarization(), indexing())
        ).all().map { it.id }
        val otherWay = ProcessingStageRegistry(
            setOf(indexing(), summarization(), categorization(), embedding(), metadata(), vision(), ocr())
        ).all().map { it.id }

        assertEquals(oneWay, otherWay)
    }

    @Test
    fun `every registered stage is returned`() {
        assertEquals(7, ProcessingStageRegistry(allStages()).all().size)
    }

    @Test
    fun `categorization runs after OCR and vision, whose text it reads`() {
        val ordered = ProcessingStageRegistry(allStages()).all().map { it.id }

        assertTrue(ordered.indexOf(StageId.CATEGORIZATION) > ordered.indexOf(StageId.OCR))
        assertTrue(ordered.indexOf(StageId.CATEGORIZATION) > ordered.indexOf(StageId.VISION))
    }

    @Test
    fun `an empty registry is legal`() {
        assertTrue(ProcessingStageRegistry(emptySet()).all().isEmpty())
    }

    @Test
    fun `stage ids are unique across registered stages`() {
        val ids = ProcessingStageRegistry(allStages()).all().map { it.id }

        // Two stages sharing an id would make their relative order arbitrary.
        assertEquals(ids.size, ids.toSet().size)
    }
}
