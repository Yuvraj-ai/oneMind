package com.onemind.app

import com.onemind.app.domain.processing.StageResult
import com.onemind.app.domain.processing.StageStatus
import com.onemind.app.domain.processing.toStatus
import org.junit.Assert.*
import org.junit.Test

/**
 * The mapping between what a stage returns and what gets persisted. These two
 * vocabularies existing separately is deliberate, so the bridge between them is
 * worth pinning down.
 */
class StageStatusTest {

    @Test
    fun `success persists as SUCCESS`() {
        assertEquals(StageStatus.SUCCESS, StageResult.Success.toStatus())
    }

    @Test
    fun `empty persists as EMPTY, distinct from failure`() {
        assertEquals(StageStatus.EMPTY, StageResult.Empty.toStatus())
    }

    @Test
    fun `unsupported persists as NOT_SUPPORTED, distinct from failure`() {
        assertEquals(StageStatus.NOT_SUPPORTED, StageResult.NotSupported.toStatus())
    }

    @Test
    fun `failure persists as FAILED`() {
        assertEquals(StageStatus.FAILED, StageResult.Failed("boom").toStatus())
    }

    @Test
    fun `skipped has nothing to persist`() {
        // Nothing was acted on, so there is no record to attach a status to.
        assertNull(StageResult.Skipped.toStatus())
    }
}
