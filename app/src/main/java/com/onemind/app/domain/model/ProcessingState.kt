package com.onemind.app.domain.model

/**
 * Processing state machine for a Memory.
 *
 * Valid transitions:
 * - DRAFT -> SAVED (user leaves composer)
 * - SAVED -> PROCESSING (processing pipeline picks it up)
 * - PROCESSING -> READY (all processing completed)
 * - PROCESSING -> FAILED (processing error)
 * - READY -> EDITED (user modifies content)
 * - EDITED -> PROCESSING (reprocessing triggered)
 */
enum class ProcessingState {
    DRAFT,
    SAVED,
    PROCESSING,
    READY,
    EDITED,
    FAILED;

    companion object {
        private val validTransitions: Map<ProcessingState, Set<ProcessingState>> = mapOf(
            DRAFT to setOf(SAVED),
            SAVED to setOf(PROCESSING),
            PROCESSING to setOf(READY, FAILED),
            READY to setOf(EDITED),
            EDITED to setOf(PROCESSING),
            FAILED to setOf(PROCESSING) // retry
        )

        /**
         * Returns true if transitioning from [from] to [to] is a valid state change.
         */
        fun isValidTransition(from: ProcessingState, to: ProcessingState): Boolean {
            return validTransitions[from]?.contains(to) == true
        }
    }
}
