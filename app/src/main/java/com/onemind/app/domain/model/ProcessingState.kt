package com.onemind.app.domain.model

/**
 * Processing state machine for a Memory.
 *
 * Valid transitions:
 * - DRAFT -> SAVED (user leaves composer)
 * - SAVED -> PROCESSING (processing pipeline picks it up)
 * - PROCESSING -> PROCESSING (an interrupted run resumes; see below)
 * - PROCESSING -> READY (all processing completed)
 * - PROCESSING -> FAILED (processing error, or a stale run swept at startup)
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
            // The self-transition is what makes an interrupted run recoverable.
            // Without it, a worker killed between claiming a Memory and finishing it
            // left that Memory in PROCESSING forever: the retry saw a state it
            // considered already-claimed, reported success without doing anything,
            // and the card span indefinitely with no way back. Mutual exclusion comes
            // from WorkManager's single serial chain, not from refusing re-entry.
            PROCESSING to setOf(PROCESSING, READY, FAILED),
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
