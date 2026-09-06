package com.onemind.app

import com.onemind.app.domain.events.ReminderLead
import com.onemind.app.domain.events.ReminderPlanner
import org.junit.Assert.*
import org.junit.Test
import java.time.Duration
import java.time.Instant

/**
 * Which reminders an event earns, and how long until each one fires.
 *
 * Every case is expressed as "an event this far from now", because the decision
 * depends on nothing else. `now` is fixed so results do not depend on when CI runs.
 *
 * The boundaries are the point of this test. Each band's edge has a wrong answer
 * that still produces *a* reminder — a "in 2 hours" notification fired at the
 * moment of saving, say — which is the kind of bug that survives being tried once
 * by hand. The v0.1.2 scheduler guarded both of its branches with
 * `isAfter(now)`, so anything under two hours away silently earned nothing at all.
 */
class ReminderPlannerTest {

    private val now: Instant = Instant.parse("2026-08-24T12:00:00Z")

    /** Plan for an event [remaining] from [now]. */
    private fun plan(remaining: Duration) =
        ReminderPlanner.plan(eventTime = now.plus(remaining), now = now)

    // --- nothing to remind about --------------------------------------------

    @Test
    fun `an event in the past earns no reminders`() {
        assertEquals(emptyList<Any>(), plan(Duration.ofHours(-3)))
    }

    @Test
    fun `an event happening exactly now earns no reminders`() {
        assertEquals(emptyList<Any>(), plan(Duration.ZERO))
    }

    @Test
    fun `an event inside the noise floor earns no reminders`() {
        assertEquals(emptyList<Any>(), plan(Duration.ofMinutes(4)))
    }

    // --- happening soon: the defect this fixes ------------------------------

    @Test
    fun `an event ninety minutes away is announced immediately`() {
        val reminders = plan(Duration.ofMinutes(90))

        assertEquals(1, reminders.size)
        assertEquals(ReminderLead.IMMEDIATE, reminders[0].lead)
        assertEquals(Duration.ZERO, reminders[0].delay)
    }

    @Test
    fun `an event at the noise floor is announced immediately`() {
        val reminders = plan(Duration.ofMinutes(5))

        assertEquals(1, reminders.size)
        assertEquals(ReminderLead.IMMEDIATE, reminders[0].lead)
        assertEquals(Duration.ZERO, reminders[0].delay)
    }

    @Test
    fun `an event just inside two hours is announced immediately`() {
        val reminders = plan(Duration.ofMinutes(119))

        assertEquals(1, reminders.size)
        assertEquals(ReminderLead.IMMEDIATE, reminders[0].lead)
    }

    // --- the two-hour lead --------------------------------------------------

    @Test
    fun `an event exactly two hours away gets a two-hour reminder now, not an immediate one`() {
        val reminders = plan(Duration.ofHours(2))

        assertEquals(1, reminders.size)
        assertEquals(ReminderLead.TWO_HOURS, reminders[0].lead)
        assertEquals(Duration.ZERO, reminders[0].delay)
    }

    @Test
    fun `an event six hours away waits four hours for its two-hour reminder`() {
        val reminders = plan(Duration.ofHours(6))

        assertEquals(1, reminders.size)
        assertEquals(ReminderLead.TWO_HOURS, reminders[0].lead)
        assertEquals(Duration.ofHours(4), reminders[0].delay)
    }

    @Test
    fun `an event just inside two days gets only the two-hour reminder`() {
        val reminders = plan(Duration.ofHours(47))

        assertEquals(1, reminders.size)
        assertEquals(ReminderLead.TWO_HOURS, reminders[0].lead)
        assertEquals(Duration.ofHours(45), reminders[0].delay)
    }

    // --- both leads ---------------------------------------------------------

    @Test
    fun `an event exactly two days away gets both leads, the first firing now`() {
        val reminders = plan(Duration.ofDays(2))

        assertEquals(2, reminders.size)
        assertEquals(ReminderLead.TWO_DAYS, reminders[0].lead)
        assertEquals(Duration.ZERO, reminders[0].delay)
        assertEquals(ReminderLead.TWO_HOURS, reminders[1].lead)
        assertEquals(Duration.ofHours(46), reminders[1].delay)
    }

    @Test
    fun `an event five days away gets both leads`() {
        val reminders = plan(Duration.ofDays(5))

        assertEquals(2, reminders.size)
        assertEquals(ReminderLead.TWO_DAYS, reminders[0].lead)
        assertEquals(Duration.ofDays(3), reminders[0].delay)
        assertEquals(ReminderLead.TWO_HOURS, reminders[1].lead)
        assertEquals(Duration.ofDays(4).plusHours(22), reminders[1].delay)
    }

    // --- invariants across the whole range ----------------------------------

    @Test
    fun `reminders are ordered soonest first`() {
        val reminders = plan(Duration.ofDays(30))

        assertTrue(reminders[0].delay < reminders[1].delay)
    }

    @Test
    fun `no reminder is ever scheduled for the past`() {
        // Walk the whole interesting range a minute at a time. A negative delay is
        // an initial delay WorkManager would clamp to zero, turning any planning
        // mistake into a notification the user gets the instant they save.
        for (minutes in 0L..(3 * 24 * 60) step 1) {
            plan(Duration.ofMinutes(minutes)).forEach { reminder ->
                assertFalse(
                    "negative delay at $minutes minutes out: $reminder",
                    reminder.delay.isNegative
                )
            }
        }
    }

    @Test
    fun `every reminder fires no later than the event itself`() {
        for (minutes in 0L..(3 * 24 * 60) step 1) {
            val remaining = Duration.ofMinutes(minutes)
            plan(remaining).forEach { reminder ->
                assertTrue(
                    "reminder after the event at $minutes minutes out: $reminder",
                    reminder.delay <= remaining
                )
            }
        }
    }
}
