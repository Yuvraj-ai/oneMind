# Events reminders: correctness and a testable seam

**Date:** 2026-08-23
**Status:** approved
**Issues:** one per defect, filed before implementation starts
**Ships in:** 0.1.4 (patch — no schema change)

---

## Why

The events feature shipped in `3f6f0a8` (v0.1.2) with **zero tests**. Five defects
were found by reading its call sites. Four are user-visible; the fifth is what made
the other four impossible to test.

| # | Defect | Evidence |
|---|---|---|
| 1 | A newly detected event gets **no reminders until the next app start** | `scheduleAll()` has exactly one caller, `OneMindApplication.kt:67`. Its KDoc claims it is *"called from app start and after each pipeline run that detects events"* — nothing in `domain/` or `data/processing/` references the scheduler. |
| 2 | Deleting a Memory **leaves its reminders enqueued** | `cancelForEvent` documents itself as being for *"when the Memory is deleted"* and has **no callers**. The row cascades; the WorkManager jobs do not. |
| 3 | Reminders can **fire twice** | `scheduleAll` enqueues, *then* marks — two steps, no transaction — and uses `enqueue`, not `enqueueUniqueWork`. Process death between the steps re-selects the event on next boot. |
| 4 | An event **under 2h away gets no reminder at all** | Both branches in `scheduleForEvent` are guarded by `isAfter(now)`. The KDoc promises a *"happening soon"* notification. Unimplemented. |
| 5 | `EventDetectionStage` **breaks the layering rule** | It sits in `domain/` but imports `data.local.dao.EventDao` and `data.local.entity.DetectedEventEntity`. The other seven stages take a `domain.repository` interface. It also calls `Instant.now()` inline, so it cannot be driven from a fixed clock. |

Defect 1 is the worst: it is the feature's headline promise silently not working.
Save something happening tomorrow, never restart the app, get nothing.

## Outcome

Reminders are scheduled the moment an event is detected; they do not outlive the
Memory; they do not duplicate; an event happening soon says so immediately; and the
whole thing is unit-testable on the JVM.

---

## Design

### The structural move

`EventReminderScheduler` currently mixes the **decision** (which reminders, at what
delays) with the **mechanism** (WorkManager). That is why none of it is testable, and
it is the reason defect 4 could ship.

The codebase already documents the fix as its own idiom. `ProcessingWorker`:

> Deliberately thin: it unwraps the memory id, hands off to `ProcessingPipeline`,
> and decides only whether the attempt is worth retrying. All orchestration
> behaviour lives in the pipeline, where it is testable without the framework.

So: extract the decision into a pure module, leave a thin adapter.

**Rejected alternatives.** Moving `work-testing` to `testImplementation` and driving
the existing scheduler under Robolectric would test the real enqueue, but leaves
logic and mechanism tangled, adds a slow dependency, and asserts WorkManager's
behaviour as much as ours. Instrumented-only tests need a device on every run and
contradict the stated preference above. Both were considered and dropped.

### New: `domain/events/ReminderPlanner.kt`

Pure. No Android, no WorkManager, no ambient clock.

```kotlin
enum class ReminderLead { TWO_DAYS, TWO_HOURS, IMMEDIATE }

data class PlannedReminder(val lead: ReminderLead, val delay: Duration)

fun plan(eventTime: Instant, now: Instant): List<PlannedReminder>
```

| Time remaining | Result | Why |
|---|---|---|
| ≤ 0 | none | Not an event. Detection should never produce one, but the planner must not trust that. |
| < 5 min | none | A notification at the moment of saving is noise, not a reminder. |
| < 2h | one `IMMEDIATE`, delay 0 | Fixes defect 4. If you save something happening in 90 minutes, being told now is the only useful moment left. |
| 2h – 2 days | one `TWO_HOURS`, delay `remaining − 2h` | The 2-day lead is already in the past. |
| ≥ 2 days | `TWO_DAYS` at `remaining − 2d`, **and** `TWO_HOURS` at `remaining − 2h` | Both leads still ahead. |

Boundaries are inclusive at the top: exactly 2h remaining yields a `TWO_HOURS`
reminder with zero delay, which is correct and correctly labelled.

The 5-minute floor is a new constant, named and documented, so it can be tuned.

### New: `domain/repository/EventRepository.kt`

Interface in `domain/repository/`, implementation in `data/repository/`, `@Binds`
`@Singleton` in `RepositoryModule` — following `DerivedDataRepository` exactly.

```kotlin
interface EventRepository {
    suspend fun replaceEventsForMemory(memoryId: Long, events: List<DetectedEvent>)
    suspend fun eventsForMemory(memoryId: Long): List<DetectedEvent>
    fun observeUpcoming(): Flow<List<DetectedEvent>>
    fun observeExpired(): Flow<List<DetectedEvent>>
    suspend fun expireOverdue(now: Instant): Int
    suspend fun unscheduledReminders(): List<DetectedEvent>
    suspend fun markRemindersScheduled(eventId: Long, at: Instant)
}
```

This is what finally carries `DetectedEvent` — the domain model that has existed
since `3f6f0a8`, fully written and documented, referenced by nothing but its own
file and one KDoc. `EventRepositoryImpl` owns the entity↔domain mapping and is the
only place that knows `eventTime` is stored as epoch millis.

### The five fixes

**1. Schedule on detection.** `ProcessingWorker` calls `eventReminderScheduler.scheduleAll()`
after a successful `pipeline.run(memoryId)`. The worker is the right seam:
`EventDetectionStage` lives in `domain` and must not know WorkManager exists. This is
the same reasoning that put `ProviderRestorer` in the worker. Correct the KDoc that
claimed this already happened.

**2. Cancel on delete.** Each request gains a second, memory-scoped tag
(`event_reminder_memory_<memoryId>`) alongside the existing per-event tag. A new
`cancelForMemory(memoryId)` cancels by that tag, called from
`FeedViewModel.confirmDelete` beside the existing `processingScheduler.cancel(memory.id)`.

Tag-based deliberately: it needs no database lookup and still works after the row has
cascaded away, which is the order deletion actually happens in.

`cancelForEvent` is **deleted**. It has never had a caller, and `cancelForMemory` covers
the only case that exists. Keeping an untested, uncalled cancellation path beside a
tested one is how the next reader learns the wrong thing.

**3. Idempotent enqueue.** `enqueueUniqueWork` with a deterministic name per event and
lead (`event_reminder_<eventId>_<lead>`) and `ExistingWorkPolicy.REPLACE`. Re-running
`scheduleAll` then converges on the same two jobs instead of stacking duplicates, even
when the mark-write was lost to process death. This is the pattern
`ProcessingScheduler.enqueue` already uses for the same reason.

The enqueue-then-mark ordering is left as-is: with a unique name it is no longer a
correctness problem, and making it transactional across a DB write and a WorkManager
write is not something either API supports.

**4. Happening soon.** Falls out of `ReminderPlanner` returning `IMMEDIATE`. The
worker's existing `KEY_REMINDER_TYPE` gains that case and its notification copy. Delete
the dead `val data` in `scheduleForEvent`, built and never used because both branches
construct their own.

**5. Layering.** `EventDetectionStage` takes `EventRepository` and a `now: Instant`
parameter defaulted to `Instant.now()` — the pattern `TemporalExpressionParser.parse`
already uses, which is what lets its tests pin time and not depend on when CI runs. Its
`data.local` imports go. `EventsViewModel`, `EventsScreen` and `EventsUiState` move to
`DetectedEvent`. `OneMindApplication` stops calling `EventDao` directly.

### Testing

| Test | Where | Covers |
|---|---|---|
| `ReminderPlannerTest` | JVM | Every row of the table above, at and either side of each boundary. This is where defect 4 gets pinned. |
| `EventDetectionStageTest` | JVM, mocked repository, fixed `now` | Future-only filter, `isEventTime` filter, null `parsedInstant`, previous events cleared on reprocess, `Empty` when nothing found. |
| `EventDaoTest` | Instrumented | The queries, including `expireOverdue` and `getUnscheduledReminders`. |
| `EventReminderSchedulerTest` | Instrumented, `work-testing` | Re-running `scheduleAll` does not double-enqueue; `cancelForMemory` removes both leads. Uses the dependency already declared at `app/build.gradle.kts:238` and used by nothing. |

RED before GREEN throughout: each defect gets a test that fails for the documented
reason before the fix lands.

---

## Out of scope

**Event titles.** `EventDetectionStage` sets `eventTitle = date.rawText.ifBlank { title }`,
so the Events tab shows the raw date string — "September 15" — as the event's name
rather than the Memory's title. That reads wrong in a list, but it is a product
judgement rather than one of these five defects, and changing it changes the UI. Noted,
not touched.

**The `detected_events.status` default discrepancy.** `MIGRATION_4_5` creates the column
`DEFAULT 'UPCOMING'`; `5.json` records no default. Latent — Room is not comparing
defaults here and every write supplies `status` explicitly. Recorded in issue #31.
Fixing it means either editing a shipped migration (frozen, per `RELEASING.md`) or a
no-op migration purely to align a default.

**Everything else on the backlog:** plaintext API key, `MINIMUM_RELEVANCE`, non-atomic
`transitionState` and `clearDerivedData`, retry duplicating derived data, UI tests, the
missing ADR for the Accessibility Service switch.
