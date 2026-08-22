package com.onemind.app

import com.onemind.app.domain.processing.StageResult
import com.onemind.app.domain.processing.StageStatus.*
import com.onemind.app.domain.processing.aggregatePerImageStatuses
import org.junit.Assert.*
import org.junit.Test

/**
 * The rule OCR and vision both use to turn many per-image outcomes into one
 * stage result. Tested directly so the rule is pinned down in one place rather
 * than inferred from two stages' worth of tests.
 */
class PerImageAggregationTest {

    private fun aggregate(vararg statuses: com.onemind.app.domain.processing.StageStatus) =
        aggregatePerImageStatuses(statuses.toList(), stageLabel = "OCR")

    @Test
    fun `one success is success`() {
        assertEquals(StageResult.Success, aggregate(SUCCESS))
    }

    @Test
    fun `partial success is success`() {
        assertEquals(StageResult.Success, aggregate(FAILED, SUCCESS, EMPTY))
    }

    @Test
    fun `a single success among many failures still counts`() {
        assertEquals(StageResult.Success, aggregate(FAILED, FAILED, FAILED, SUCCESS))
    }

    @Test
    fun `a clean sweep of failures fails`() {
        val result = aggregate(FAILED, FAILED)
        assertTrue(result is StageResult.Failed)
        assertTrue((result as StageResult.Failed).reason.contains("2 image"))
    }

    @Test
    fun `the failure message names the stage`() {
        val result = aggregatePerImageStatuses(listOf(FAILED), stageLabel = "Vision")
        assertTrue((result as StageResult.Failed).reason.startsWith("Vision"))
    }

    @Test
    fun `everything empty is Empty, not Failed`() {
        assertEquals(StageResult.Empty, aggregate(EMPTY, EMPTY))
    }

    @Test
    fun `a mix of empty and failed is Empty, since some images ran clean`() {
        assertEquals(StageResult.Empty, aggregate(EMPTY, FAILED))
    }

    @Test
    fun `all unsupported is Empty at this level`() {
        // Vision handles the unsupported case before it reaches here; if such a
        // status did arrive it is not a failure.
        assertEquals(StageResult.Empty, aggregate(NOT_SUPPORTED, NOT_SUPPORTED))
    }

    @Test
    fun `no images at all is Skipped`() {
        assertEquals(StageResult.Skipped, aggregatePerImageStatuses(emptyList(), "OCR"))
    }
}
