package com.onemind.app.data.processing

import com.onemind.app.data.local.dao.MemoryDao
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Releases Memories claimed by a run that is no longer alive.
 *
 * A worker can die between claiming a Memory and finishing it — the process is
 * killed, or WorkManager stops it because the battery dropped below the constraint.
 * The Memory is left in PROCESSING with nothing to clear it, so its card spins
 * forever and the retry affordance never appears, because retry is offered only for
 * FAILED.
 *
 * Moving those to FAILED is not pretending they failed for a good reason. It is the
 * honest state: enrichment did not complete, and FAILED is the state the user can
 * act on. The alternative — resuming automatically — would re-run work that may have
 * been stopped deliberately, on a device whose battery is still low.
 *
 * Runs once at application start, which is the only moment we can be confident no
 * worker from a previous process is still alive.
 */
@Singleton
class StaleProcessingSweeper @Inject constructor(
    private val memoryDao: MemoryDao
) {

    /**
     * Fail any Memory that has been PROCESSING longer than [STALE_AFTER_MILLIS].
     *
     * The age check is what keeps this from interfering with a live pipeline: a
     * worker WorkManager starts moments after launch has a fresh `updatedAt` and is
     * left alone.
     *
     * @return how many were released.
     */
    suspend fun sweep(now: Instant = Instant.now()): Int {
        val cutoff = now.toEpochMilli() - STALE_AFTER_MILLIS
        return memoryDao.failStaleProcessing(before = cutoff, now = now.toEpochMilli())
    }

    companion object {
        /**
         * How long a Memory may legitimately stay in PROCESSING.
         *
         * Generous on purpose. A Memory with a dozen screenshots, cloud vision calls
         * and a summary can take minutes, and sweeping a live run would be worse than
         * leaving a dead one — the user would see a failure for work that was about to
         * succeed.
         */
        const val STALE_AFTER_MILLIS = 15 * 60 * 1000L
    }
}
