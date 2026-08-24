package com.onemind.app.domain.events

import java.time.Duration
import java.time.Instant

/** How far ahead of an event a reminder is meant to arrive. */
enum class ReminderLead {
    /** Two days out. Time to prepare. */
    TWO_DAYS,

    /** Two hours out. Time to leave. */
    TWO_HOURS,

    /**
     * Right now, because there is no lead left worth waiting for.
     *
     * Saving something that happens in ninety minutes is the case this exists for:
     * every scheduled lead is already in the past, so the only useful moment to say
     * anything is the moment of saving.
     */
    IMMEDIATE
}

/** A reminder to fire, and how long from now until it should. */
data class PlannedReminder(
    val lead: ReminderLead,
    val delay: Duration
)

/**
 * Decides which reminders an event earns and when each should fire.
 *
 * Pure, and separate from the scheduler on purpose. Mixing this decision into
 * `EventReminderScheduler` is what made it untestable, and being untestable is how
 * v0.1.2 shipped a "happening soon" notification that its own KDoc promised and its
 * code never sent: both branches were guarded by "is this lead still in the future",
 * so an event two hours out or less fell through both and earned nothing.
 *
 * Nothing here touches WorkManager, Android, or the ambient clock. [now] is a
 * parameter for the same reason it is on `TemporalExpressionParser.parse` — a test
 * that has to sit and wait is a test nobody runs.
 */
object ReminderPlanner {

    /**
     * How close an event has to be before reminding about it is just noise.
     *
     * A notification arriving in the same breath as the save it describes tells the
     * user something they demonstrably already know. Named and separate so it can be
     * tuned without touching the bands below.
     */
    val NOISE_FLOOR: Duration = Duration.ofMinutes(5)

    /** The lead times we schedule ahead of an event, longest first. */
    val TWO_DAYS: Duration = Duration.ofDays(2)
    val TWO_HOURS: Duration = Duration.ofHours(2)

    /**
     * The reminders [eventTime] earns, given the current time is [now].
     *
     * Ordered soonest first. An empty list is a legitimate answer and means the
     * event is past, or so close that any reminder would be noise.
     *
     * Each band's upper boundary belongs to the band above it: an event exactly two
     * hours away gets a `TWO_HOURS` reminder with no delay rather than an
     * `IMMEDIATE` one. Same notification, honestly labelled.
     */
    fun plan(eventTime: Instant, now: Instant): List<PlannedReminder> {
        val remaining = Duration.between(now, eventTime)

        // Detection should never produce a past event, but the planner is the last
        // thing between a bad row and a notification, so it does not take that on
        // trust.
        if (remaining < NOISE_FLOOR) return emptyList()

        if (remaining < TWO_HOURS) {
            return listOf(PlannedReminder(ReminderLead.IMMEDIATE, Duration.ZERO))
        }

        val twoHours = PlannedReminder(ReminderLead.TWO_HOURS, remaining - TWO_HOURS)

        if (remaining < TWO_DAYS) return listOf(twoHours)

        return listOf(
            PlannedReminder(ReminderLead.TWO_DAYS, remaining - TWO_DAYS),
            twoHours
        )
    }
}
