package com.onemind.app

import com.onemind.app.data.events.EventReminderScheduler
import com.onemind.app.domain.events.ReminderLead
import org.junit.Assert.*
import org.junit.Test

/**
 * The identity a reminder job is enqueued under.
 *
 * Small surface, and the only part of the scheduler that can be tested without a
 * device — but it is where the duplicate-notification bug lives. Reminders are
 * enqueued as unique work so that re-running `scheduleAll` converges on the same
 * jobs instead of stacking new ones, and "unique" is only as good as this name:
 *
 * - drop the event from it and one event's reminders evict another's;
 * - drop the lead from it and an event's two leads collapse into one, so the user
 *   gets a single reminder where they were promised two;
 * - make it vary between calls and nothing dedupes at all, which is the v0.1.2
 *   behaviour this replaces.
 *
 * Each of those still produces notifications, just the wrong ones, which is why
 * none of it was noticed.
 */
class EventReminderWorkNameTest {

    @Test
    fun `the same event and lead always name the same work`() {
        assertEquals(
            EventReminderScheduler.uniqueWorkName(42, ReminderLead.TWO_HOURS),
            EventReminderScheduler.uniqueWorkName(42, ReminderLead.TWO_HOURS)
        )
    }

    @Test
    fun `an event's two leads are named separately`() {
        assertNotEquals(
            EventReminderScheduler.uniqueWorkName(42, ReminderLead.TWO_DAYS),
            EventReminderScheduler.uniqueWorkName(42, ReminderLead.TWO_HOURS)
        )
    }

    @Test
    fun `different events are named separately`() {
        assertNotEquals(
            EventReminderScheduler.uniqueWorkName(42, ReminderLead.TWO_HOURS),
            EventReminderScheduler.uniqueWorkName(43, ReminderLead.TWO_HOURS)
        )
    }

    @Test
    fun `every event and lead combination is distinct`() {
        val names = (1L..20L).flatMap { eventId ->
            ReminderLead.entries.map { EventReminderScheduler.uniqueWorkName(eventId, it) }
        }

        assertEquals(names.size, names.toSet().size)
    }

    @Test
    fun `no event id can be mistaken for another by concatenation`() {
        // "event_reminder_1" + "2_HOURS" must not collide with
        // "event_reminder_12" + "_HOURS". A separator is what prevents it, and its
        // absence is invisible until two unrelated events start evicting each other.
        val allIds = (1L..1_000L).flatMap { eventId ->
            ReminderLead.entries.map { EventReminderScheduler.uniqueWorkName(eventId, it) }
        }

        assertEquals(allIds.size, allIds.toSet().size)
    }
}
