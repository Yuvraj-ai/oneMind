package com.onemind.app

import com.onemind.app.domain.model.ProcessingState
import com.onemind.app.domain.model.ProcessingState.*
import org.junit.Assert.*
import org.junit.Test

/**
 * Tests for the ProcessingState state machine.
 * Verifies all valid transitions and rejects invalid ones.
 */
class ProcessingStateTest {

    @Test
    fun `DRAFT can transition to SAVED`() {
        assertTrue(ProcessingState.isValidTransition(DRAFT, SAVED))
    }

    @Test
    fun `SAVED can transition to PROCESSING`() {
        assertTrue(ProcessingState.isValidTransition(SAVED, PROCESSING))
    }

    @Test
    fun `PROCESSING can transition to READY`() {
        assertTrue(ProcessingState.isValidTransition(PROCESSING, READY))
    }

    @Test
    fun `PROCESSING can transition to FAILED`() {
        assertTrue(ProcessingState.isValidTransition(PROCESSING, FAILED))
    }

    @Test
    fun `READY can transition to EDITED`() {
        assertTrue(ProcessingState.isValidTransition(READY, EDITED))
    }

    @Test
    fun `EDITED can transition to PROCESSING`() {
        assertTrue(ProcessingState.isValidTransition(EDITED, PROCESSING))
    }

    @Test
    fun `FAILED can transition to PROCESSING (retry)`() {
        assertTrue(ProcessingState.isValidTransition(FAILED, PROCESSING))
    }

    // Invalid transitions

    @Test
    fun `DRAFT cannot transition to PROCESSING`() {
        assertFalse(ProcessingState.isValidTransition(DRAFT, PROCESSING))
    }

    @Test
    fun `DRAFT cannot transition to READY`() {
        assertFalse(ProcessingState.isValidTransition(DRAFT, READY))
    }

    @Test
    fun `SAVED cannot transition to READY`() {
        assertFalse(ProcessingState.isValidTransition(SAVED, READY))
    }

    @Test
    fun `SAVED cannot transition to DRAFT`() {
        assertFalse(ProcessingState.isValidTransition(SAVED, DRAFT))
    }

    @Test
    fun `READY cannot transition to SAVED`() {
        assertFalse(ProcessingState.isValidTransition(READY, SAVED))
    }

    @Test
    fun `READY cannot transition to PROCESSING directly`() {
        assertFalse(ProcessingState.isValidTransition(READY, PROCESSING))
    }

    @Test
    fun `PROCESSING cannot transition to DRAFT`() {
        assertFalse(ProcessingState.isValidTransition(PROCESSING, DRAFT))
    }

    @Test
    fun `FAILED cannot transition to READY directly`() {
        assertFalse(ProcessingState.isValidTransition(FAILED, READY))
    }
}
