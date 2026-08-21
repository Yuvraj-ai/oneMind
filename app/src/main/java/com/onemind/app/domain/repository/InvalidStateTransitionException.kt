package com.onemind.app.domain.repository

import com.onemind.app.domain.model.ProcessingState

/**
 * Thrown when an invalid state transition is attempted on a Memory.
 */
class InvalidStateTransitionException(
    val memoryId: Long,
    val from: ProcessingState,
    val to: ProcessingState
) : IllegalStateException(
    "Invalid state transition for Memory $memoryId: $from -> $to"
)
