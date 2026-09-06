# Events: Reject and Add to Calendar — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let a user reject a detected event or mark it as added to their calendar, and make those decisions survive reprocessing and stop reminders that no longer apply.

**Architecture:** `EventStatus` gains `REJECTED` and `IN_CALENDAR`. `EventDao`'s hardcoded status literals widen to sets. `EventRepository` gains three verbs in the user's language (`reject`, `undoReject`, `markAddedToCalendar`), implemented in `EventRepositoryImpl`, which is also where reminder cancellation and status preservation live — the repository is the one seam every caller passes through. `EventsViewModel` assembles a UI-layer `EventCardUi` carrying the location and category chips the mock shows, both read from data that already exists.

**Tech Stack:** Kotlin 2.1.0, Room 2.7.1, Hilt 2.53.1 (KSP), WorkManager 2.10.0, Jetpack Compose (BOM 2026.06.01, material3 1.4.0), JUnit4, MockK, Compose UI test, Robolectric-free instrumented tests on the `onemind_test` AVD.

## Global Constraints

- **Branch: `my-extra-work`.** Never commit to `main`; never merge or fast-forward from `main`.
- **Commit attribution is the user alone.** No `Co-Authored-By`, no `Generated with`, no AI trailer of any kind.
- **No release.** No version bump, no tag, no APK, no GitHub release, unless explicitly asked.
- **One commit per issue**, with `(#N)` in the subject. Issues are filed **before** their implementation and closed afterwards with an AI-attributed comment.
- **GitHub access is curl, not `gh`.** `gh` is not installed. Read the token into a variable and never echo it:
  `export GITHUB_PAT=$(grep '^GITHUB_PAT=' /home/imyuvi/projects/codingagents/.env | cut -d= -f2- | tr -d '\r\n')`
  Repo is `Yuvraj-ai/oneMind`. **Never run `git remote -v` or `git config --get remote.origin.url`** — the token is embedded in origin's URL in plaintext.
- **Build invocation**, always from `/home/imyuvi/projects/codingagents/oneMind`:
  `JAVA_HOME=/home/imyuvi/.local/jdks/jdk-17.0.20.1+1 ./gradlew <task>`
  `java` is not on `PATH`.
- **`assembleDebug` is mandatory** after any constructor or DI change — it is the only check that validates the Hilt graph.
- **Instrumented tests are runnable here.** Boot the `onemind_test` AVD. Never report them unverifiable for want of a device.
- **The database stays at version 5.** `EventStatus` is persisted as TEXT by enum name, so new values change no column, index, or Room schema hash. `app/schemas/com.onemind.app.data.local.OneMindDatabase/5.json` must be **unchanged** at the end of this plan; if a build rewrites it, something else went wrong.
- **Test source layout is flat.** `app/src/test/java/com/onemind/app/*.kt` and `app/src/androidTest/java/com/onemind/app/*.kt`. No subpackages.
- TDD: write the failing test, run it and watch it fail for the right reason, then the minimal code to pass.

## File Structure

| File | Responsibility | Change |
|---|---|---|
| `app/src/main/java/com/onemind/app/domain/model/DetectedEvent.kt` | `EventStatus` enum | Modify — two new values |
| `app/src/main/java/com/onemind/app/data/local/dao/EventDao.kt` | Event row queries | Modify — widen 6 literals, add `updateStatus` |
| `app/src/main/java/com/onemind/app/domain/repository/EventRepository.kt` | Domain seam | Modify — three verbs |
| `app/src/main/java/com/onemind/app/data/repository/EventRepositoryImpl.kt` | Storage + reminder cleanup | Modify — verbs, status preservation, cancel |
| `app/src/main/java/com/onemind/app/data/events/EventReminderScheduler.kt` | WorkManager enqueue/cancel | Modify — reintroduce `cancelForEvent` |
| `app/src/main/java/com/onemind/app/data/local/dao/DerivedDataDao.kt` | Derived-data queries | Modify — batched entity read |
| `app/src/main/java/com/onemind/app/domain/repository/MemoryRepository.kt` | Memory seam | Modify — `getEntitiesByMemoryIds` |
| `app/src/main/java/com/onemind/app/data/repository/MemoryRepositoryImpl.kt` | Memory storage | Modify — implement the above |
| `app/src/main/java/com/onemind/app/ui/events/EventCardUi.kt` | **New** — one event as the screen needs it | Create |
| `app/src/main/java/com/onemind/app/ui/events/EventsViewModel.kt` | Events presentation state | Modify — verbs, card assembly |
| `app/src/main/java/com/onemind/app/ui/events/EventsScreen.kt` | Events UI | Modify — actions, status pills, undo |
| `app/src/test/java/com/onemind/app/EventCardAssemblyTest.kt` | **New** — card assembly, JVM | Create |
| `app/src/androidTest/java/com/onemind/app/EventStatusTransitionTest.kt` | **New** — DAO + repo behaviour | Create |
| `app/src/androidTest/java/com/onemind/app/EventsScreenTest.kt` | Existing screen test | Modify — fake repo, new assertions |
| `app/src/androidTest/java/com/onemind/app/EventReminderSchedulerTest.kt` | Scheduler behaviour | Modify — `cancelForEvent` tests |

Three issues, three commits:

| Issue | Tasks | Subject |
|---|---|---|
| A | 1–5 | Events cannot be rejected or marked as added to calendar |
| B | 6 | Reprocessing a Memory resets a rejected event to upcoming |
| C | 7 | Rejecting an event leaves its reminders enqueued |

Tasks inside one issue are separate because each has its own test cycle, but the commit lands only at the issue boundary. That is a deliberate deviation from writing-plans' commit-per-task: the project rule is one commit per issue, and the user's rule wins.

## GitHub helper

Every issue command in this plan uses this shape. It works under `fish` as well as
`bash`, reads the token from `.env` without ever echoing it, and avoids quoting a JSON
body through the shell.

```bash
python3 - <<'PY'
import json, re, urllib.request
pat = re.search(r'^GITHUB_PAT=(.*)$',
                open('/home/imyuvi/projects/codingagents/.env').read(), re.M).group(1).strip()
def api(path, data=None, method=None):
    req = urllib.request.Request(
        f"https://api.github.com/repos/Yuvraj-ai/oneMind{path}",
        data=None if data is None else json.dumps(data).encode(),
        method=method,
        headers={"Authorization": f"Bearer {pat}",
                 "Accept": "application/vnd.github+json",
                 "Content-Type": "application/json"})
    return json.load(urllib.request.urlopen(req))
# ... body of the specific call goes here
PY
```

---

## Task 1: `EventStatus` gains two values; `EventDao` stops hardcoding one

**Files:**
- Modify: `app/src/main/java/com/onemind/app/domain/model/DetectedEvent.kt:28-33`
- Modify: `app/src/main/java/com/onemind/app/data/local/dao/EventDao.kt:20-24`, `:38-39`, `:57-61`
- Test: `app/src/androidTest/java/com/onemind/app/EventStatusTransitionTest.kt` (create)

**Interfaces:**
- Consumes: nothing.
- Produces:
  - `enum class EventStatus { UPCOMING, EXPIRED, REJECTED, IN_CALENDAR }`
  - `suspend fun EventDao.updateStatus(eventId: Long, status: EventStatus)`
  - `suspend fun EventDao.restoreToUpcoming(eventId: Long)`
  - `observeUpcoming()` now emits `UPCOMING` **and** `IN_CALENDAR`; `observeExpired()` emits `EXPIRED` **and** `REJECTED`; `expireOverdue(now)` moves `UPCOMING` **and** `IN_CALENDAR`; `countUpcoming()` counts both upcoming statuses.

**Why `restoreToUpcoming` exists and the design spec does not mention it.** The spec says
`updateStatus` is "the only write the three verbs need". That is wrong, and it was found
while writing this plan. Rejecting cancels the event's enqueued reminders (Task 7) but
leaves `remindersScheduledAt` set. `getUnscheduledReminders()` skips any event with a
non-null `remindersScheduledAt`, so an un-rejected event would be skipped forever and
never be reminded about again — undo would silently return a *reminderless* event. Undo
therefore has to clear the mark as well as the status, and that is one atomic `UPDATE`,
not two.

- [ ] **Step 1: Add the two enum values**

The values must exist before a test can name them, so this comes first. It changes no
behaviour on its own — nothing produces either value yet.

In `app/src/main/java/com/onemind/app/domain/model/DetectedEvent.kt`, replace:

```kotlin
enum class EventStatus {
    /** The event time has not yet passed. */
    UPCOMING,
    /** The event time has passed. Kept for history. */
    EXPIRED
}
```

with:

```kotlin
/**
 * Where an event stands, combining what time has done to it with what the user has
 * decided about it.
 *
 * Persisted as TEXT by name, with no explicit converter, which is why adding a value
 * here costs no migration and leaves the schema at version 5. The cost is elsewhere:
 * any SQL that spells a status out as a literal becomes an incomplete enumeration the
 * moment this grows, and nothing fails to compile when it does.
 */
enum class EventStatus {
    /** The event time has not yet passed. */
    UPCOMING,
    /** The event time has passed. Kept for history. */
    EXPIRED,
    /** The user declined this event. Reversible, and shown alongside expired ones. */
    REJECTED,
    /**
     * The user exported this event to their calendar app.
     *
     * Still upcoming — exporting is not dismissing — so it stays in the upcoming list
     * and still expires when its time passes.
     */
    IN_CALENDAR
}
```

- [ ] **Step 2: Write the failing test**

Create `app/src/androidTest/java/com/onemind/app/EventStatusTransitionTest.kt`:

```kotlin
package com.onemind.app

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.onemind.app.data.local.OneMindDatabase
import com.onemind.app.data.local.dao.EventDao
import com.onemind.app.data.local.dao.MemoryDao
import com.onemind.app.data.local.entity.DetectedEventEntity
import com.onemind.app.data.local.entity.MemoryEntity
import com.onemind.app.domain.model.EventStatus
import com.onemind.app.domain.model.ProcessingState
import com.onemind.app.domain.model.SourceType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Duration
import java.time.Instant

/**
 * That a user's decision about an event is respected by every query that reads one.
 *
 * `EventStatus` is persisted by name into a TEXT column, so growing it costs no
 * migration and — this is the hazard — breaks nothing at compile time. Six SQL string
 * literals across five queries spelled `'UPCOMING'` and `'EXPIRED'` out by hand; each
 * silently became an incomplete enumeration. These tests are what makes that visible.
 */
@RunWith(AndroidJUnit4::class)
class EventStatusTransitionTest {

    private lateinit var database: OneMindDatabase
    private lateinit var dao: EventDao
    private lateinit var memoryDao: MemoryDao

    private var memoryId: Long = 0

    @Before
    fun setup() = runTest {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            OneMindDatabase::class.java
        ).allowMainThreadQueries().build()
        dao = database.eventDao()
        memoryDao = database.memoryDao()
        memoryId = newMemory()
    }

    @After
    fun teardown() {
        database.close()
    }

    private suspend fun newMemory(): Long = memoryDao.insertMemoryWithBlocks(
        MemoryEntity(
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
            sourceType = SourceType.MANUAL,
            processingState = ProcessingState.SAVED
        ),
        emptyList()
    )

    private suspend fun insertEvent(
        ahead: Duration = Duration.ofDays(3),
        status: EventStatus = EventStatus.UPCOMING,
        title: String = "AI Summit",
        remindersScheduledAt: Long? = null
    ): Long = dao.insert(
        DetectedEventEntity(
            memoryId = memoryId,
            eventTime = Instant.now().plus(ahead).toEpochMilli(),
            eventTitle = title,
            status = status,
            remindersScheduledAt = remindersScheduledAt
        )
    )

    @Test
    fun upcomingListIncludesEventsAddedToTheCalendar() = runTest {
        val id = insertEvent(status = EventStatus.IN_CALENDAR)

        // Exporting to a calendar is not dismissing. The event is still coming up, so
        // it stays where the user can see it.
        assertEquals(listOf(id), dao.observeUpcoming().first().map { it.id })
    }

    @Test
    fun pastListIncludesRejectedEvents() = runTest {
        val id = insertEvent(status = EventStatus.REJECTED)

        assertEquals(listOf(id), dao.observeExpired().first().map { it.id })
        assertTrue(dao.observeUpcoming().first().isEmpty())
    }

    @Test
    fun expireOverdueMovesAnEventTheUserAddedToTheirCalendar() = runTest {
        val id = insertEvent(ahead = Duration.ofDays(-1), status = EventStatus.IN_CALENDAR)

        assertEquals(1, dao.expireOverdue(System.currentTimeMillis()))

        // Left alone this event would sit at the top of the upcoming list forever,
        // showing a date in the past.
        assertEquals(EventStatus.EXPIRED, dao.getEventsForMemory(memoryId).single().status)
        assertEquals(listOf(id), dao.observeExpired().first().map { it.id })
    }

    @Test
    fun expireOverdueLeavesARejectedEventRejected() = runTest {
        insertEvent(ahead = Duration.ofDays(-1), status = EventStatus.REJECTED)

        assertEquals(0, dao.expireOverdue(System.currentTimeMillis()))

        // Deliberate: a rejected event already renders in the past list, and expiring
        // it would silently strip the undo affordance. Undoing it returns it to
        // UPCOMING with a past time, and the next sweep expires it then.
        assertEquals(EventStatus.REJECTED, dao.getEventsForMemory(memoryId).single().status)
    }

    @Test
    fun countUpcomingCountsTheSameRowsTheUpcomingListShows() = runTest {
        insertEvent(status = EventStatus.UPCOMING)
        insertEvent(status = EventStatus.IN_CALENDAR, title = "Dentist")
        insertEvent(status = EventStatus.REJECTED, title = "Standup")

        assertEquals(2, dao.countUpcoming())
        assertEquals(2, dao.observeUpcoming().first().size)
    }

    @Test
    fun updateStatusWritesTheGivenStatus() = runTest {
        val id = insertEvent()

        dao.updateStatus(id, EventStatus.REJECTED)

        assertEquals(EventStatus.REJECTED, dao.getEventsForMemory(memoryId).single().status)
    }

    @Test
    fun restoreToUpcomingAlsoForgetsThatRemindersWereScheduled() = runTest {
        val id = insertEvent(status = EventStatus.REJECTED, remindersScheduledAt = 1_000L)

        dao.restoreToUpcoming(id)

        val row = dao.getEventsForMemory(memoryId).single()
        assertEquals(EventStatus.UPCOMING, row.status)
        // Both halves matter. Rejecting cancelled the enqueued jobs but left the mark
        // set; without clearing it, getUnscheduledReminders would skip this event
        // forever and undo would hand back an event nothing will ever remind about.
        assertNull(row.remindersScheduledAt)
        assertEquals(listOf(id), dao.getUnscheduledReminders().map { it.id })
    }

    @Test
    fun unscheduledRemindersStillIgnoresEverythingButPlainUpcoming() = runTest {
        insertEvent(status = EventStatus.IN_CALENDAR)
        insertEvent(status = EventStatus.REJECTED, title = "Dentist")

        // Left on UPCOMING alone on purpose: an event is only *owed* reminders while
        // it is still plain upcoming. An IN_CALENDAR event keeps the reminders it
        // already has (they are never cancelled), it just does not earn new ones.
        assertTrue(dao.getUnscheduledReminders().isEmpty())
    }
}
```

- [ ] **Step 3: Run the test and watch it fail**

Boot the AVD first if it is not running:

```bash
JAVA_HOME=/home/imyuvi/.local/jdks/jdk-17.0.20.1+1 ./gradlew connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.onemind.app.EventStatusTransitionTest
```

Expected: **compilation failure**, `Unresolved reference: updateStatus` and
`Unresolved reference: restoreToUpcoming`. That is the correct red — the queries do not
exist yet. The status-widening tests cannot run until compilation succeeds, which is
why Step 4 does both.

- [ ] **Step 4: Widen the literals and add the two writes**

In `app/src/main/java/com/onemind/app/data/local/dao/EventDao.kt`, replace:

```kotlin
    @Query("SELECT * FROM detected_events WHERE status = 'UPCOMING' ORDER BY eventTime ASC")
    fun observeUpcoming(): Flow<List<DetectedEventEntity>>

    @Query("SELECT * FROM detected_events WHERE status = 'EXPIRED' ORDER BY eventTime DESC")
    fun observeExpired(): Flow<List<DetectedEventEntity>>
```

with:

```kotlin
    /**
     * Everything still ahead of the user, soonest first.
     *
     * `IN_CALENDAR` belongs here: exporting an event to a calendar app is not
     * dismissing it, so it stays visible and still expires on time.
     */
    @Query(
        """
        SELECT * FROM detected_events
        WHERE status IN ('UPCOMING', 'IN_CALENDAR')
        ORDER BY eventTime ASC
        """
    )
    fun observeUpcoming(): Flow<List<DetectedEventEntity>>

    /**
     * History: events time has passed, and events the user declined.
     *
     * `REJECTED` shares this list rather than disappearing, because rejecting is
     * reversible and a row nothing renders cannot be undone.
     */
    @Query(
        """
        SELECT * FROM detected_events
        WHERE status IN ('EXPIRED', 'REJECTED')
        ORDER BY eventTime DESC
        """
    )
    fun observeExpired(): Flow<List<DetectedEventEntity>>
```

Replace:

```kotlin
    @Query("UPDATE detected_events SET status = 'EXPIRED' WHERE status = 'UPCOMING' AND eventTime < :now")
    suspend fun expireOverdue(now: Long): Int
```

with:

```kotlin
    @Query(
        """
        UPDATE detected_events SET status = 'EXPIRED'
        WHERE status IN ('UPCOMING', 'IN_CALENDAR') AND eventTime < :now
        """
    )
    suspend fun expireOverdue(now: Long): Int
```

Replace:

```kotlin
    @Query("SELECT COUNT(*) FROM detected_events WHERE status = 'UPCOMING'")
    suspend fun countUpcoming(): Int
```

with:

```kotlin
    /** Kept in step with [observeUpcoming], so a count never disagrees with a list. */
    @Query("SELECT COUNT(*) FROM detected_events WHERE status IN ('UPCOMING', 'IN_CALENDAR')")
    suspend fun countUpcoming(): Int

    /** The single write behind rejecting an event and marking one as exported. */
    @Query("UPDATE detected_events SET status = :status WHERE id = :eventId")
    suspend fun updateStatus(eventId: Long, status: EventStatus)

    /**
     * Return an event to plain upcoming and forget its reminders were ever scheduled.
     *
     * One statement rather than two writes, because the two halves are one decision.
     * Rejecting cancels the enqueued jobs but leaves `remindersScheduledAt` set;
     * [getUnscheduledReminders] skips any event that has it, so without clearing it
     * here an un-rejected event would never be reminded about again.
     */
    @Query(
        """
        UPDATE detected_events SET status = 'UPCOMING', remindersScheduledAt = NULL
        WHERE id = :eventId
        """
    )
    suspend fun restoreToUpcoming(eventId: Long)
```

`getUnscheduledReminders()` is **not** changed. Its `status = 'UPCOMING'` filter is
correct as written and the test `unscheduledRemindersStillIgnoresEverythingButPlainUpcoming`
pins that.

- [ ] **Step 5: Run the test and watch it pass**

```bash
JAVA_HOME=/home/imyuvi/.local/jdks/jdk-17.0.20.1+1 ./gradlew connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.onemind.app.EventStatusTransitionTest
```

Expected: 8 tests, 0 failures.

- [ ] **Step 6: Confirm the schema did not move**

```bash
git status --short app/schemas/
```

Expected: **no output.** If `5.json` shows as modified, an entity or index changed by
accident — revert and find out what. Do not add a version 6.

---

## Task 2: Three verbs on `EventRepository`

**Files:**
- Modify: `app/src/main/java/com/onemind/app/domain/repository/EventRepository.kt:39` (append)
- Modify: `app/src/main/java/com/onemind/app/data/repository/EventRepositoryImpl.kt:34-35` (append)
- Modify: `app/src/androidTest/java/com/onemind/app/EventsScreenTest.kt:175-186`
- Test: `app/src/androidTest/java/com/onemind/app/EventStatusTransitionTest.kt` (extend)

**Interfaces:**
- Consumes: `EventDao.updateStatus`, `EventDao.restoreToUpcoming`, `EventStatus.REJECTED`, `EventStatus.IN_CALENDAR` (Task 1).
- Produces, on `EventRepository`:
  - `suspend fun reject(eventId: Long)`
  - `suspend fun undoReject(eventId: Long)`
  - `suspend fun markAddedToCalendar(eventId: Long)`

**The compile break to expect.** `EventsScreenTest` at `:175` holds a hand-rolled
`FakeEventRepository`. The instant the interface grows, `androidTest` stops compiling.
It is fixed in this same task, in Step 3, and the fake is rewritten to model the
transitions rather than hold two fixed lists — Task 5's tests need it to.
`EventDetectionStageTest` uses `mockk(relaxed = true)` and needs no change.

- [ ] **Step 1: Write the failing test**

Append to `app/src/androidTest/java/com/onemind/app/EventStatusTransitionTest.kt`,
inside the class:

```kotlin
    // --- the repository verbs ---------------------------------------------

    @Test
    fun rejectingMovesAnEventOutOfUpcomingAndIntoThePastList() = runTest {
        val repository = EventRepositoryImpl(dao)
        val id = insertEvent()

        repository.reject(id)

        assertTrue(repository.observeUpcoming().first().isEmpty())
        assertEquals(listOf(id), repository.observeExpired().first().map { it.id })
    }

    @Test
    fun undoingARejectionBringsTheEventBack() = runTest {
        val repository = EventRepositoryImpl(dao)
        val id = insertEvent()
        repository.reject(id)

        repository.undoReject(id)

        assertEquals(listOf(id), repository.observeUpcoming().first().map { it.id })
        assertTrue(repository.observeExpired().first().isEmpty())
    }

    @Test
    fun undoingARejectionLetsTheEventEarnRemindersAgain() = runTest {
        val repository = EventRepositoryImpl(dao)
        val id = insertEvent(remindersScheduledAt = 1_000L)
        repository.reject(id)

        repository.undoReject(id)

        // Rejecting cancelled the jobs. If undo left the mark in place, nothing would
        // ever re-arm them and the user would get an event back with no reminders.
        assertEquals(listOf(id), dao.getUnscheduledReminders().map { it.id })
    }

    @Test
    fun markingAnEventAddedToCalendarKeepsItUpcoming() = runTest {
        val repository = EventRepositoryImpl(dao)
        val id = insertEvent()

        repository.markAddedToCalendar(id)

        assertEquals(listOf(id), repository.observeUpcoming().first().map { it.id })
        assertEquals(
            EventStatus.IN_CALENDAR,
            dao.getEventsForMemory(memoryId).single().status
        )
    }
```

Add these imports to the file's import block:

```kotlin
import com.onemind.app.data.repository.EventRepositoryImpl
```

- [ ] **Step 2: Run the test and watch it fail**

```bash
JAVA_HOME=/home/imyuvi/.local/jdks/jdk-17.0.20.1+1 ./gradlew connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.onemind.app.EventStatusTransitionTest
```

Expected: compilation failure, `Unresolved reference: reject`.

- [ ] **Step 3: Add the verbs**

In `app/src/main/java/com/onemind/app/domain/repository/EventRepository.kt`, append
inside the interface, after `expireOverdue`:

```kotlin

    /**
     * The user declined this event.
     *
     * Reversible, and named for what the user did rather than for the column it
     * writes — `domain` states intent, and which status that becomes is the
     * implementation's business, the same reasoning that keeps `Instant` on this
     * side of the seam and epoch millis on the other.
     */
    suspend fun reject(eventId: Long)

    /**
     * Take back a rejection, returning the event to plain upcoming.
     *
     * Unconditional: an event rejected before its time and undone after it comes back
     * upcoming with a past time, and the next [expireOverdue] moves it on. That is
     * self-correcting, and cheaper than reading the row back to decide.
     */
    suspend fun undoReject(eventId: Long)

    /**
     * The user exported this event to their calendar app.
     *
     * Still upcoming — a calendar entry is a copy, not a dismissal — and its oneMind
     * reminders are deliberately left alone: `ACTION_INSERT` opens the calendar app's
     * own add screen and oneMind never learns whether the user saved or cancelled, so
     * cancelling here would sometimes remove the only reminder they have.
     */
    suspend fun markAddedToCalendar(eventId: Long)
```

In `app/src/main/java/com/onemind/app/data/repository/EventRepositoryImpl.kt`, append
inside the class, after `expireOverdue`:

```kotlin

    override suspend fun reject(eventId: Long) =
        dao.updateStatus(eventId, EventStatus.REJECTED)

    override suspend fun undoReject(eventId: Long) =
        dao.restoreToUpcoming(eventId)

    override suspend fun markAddedToCalendar(eventId: Long) =
        dao.updateStatus(eventId, EventStatus.IN_CALENDAR)
```

and add the import:

```kotlin
import com.onemind.app.domain.model.EventStatus
```

- [ ] **Step 4: Rewrite the screen test's fake so `androidTest` compiles again**

In `app/src/androidTest/java/com/onemind/app/EventsScreenTest.kt`, replace the whole
`FakeEventRepository` class (`:168-186`, KDoc included) with:

```kotlin
    /**
     * Enough of an [EventRepository] to render against, and to watch change.
     *
     * Hand-rolled rather than Room-backed: what is under test is the screen, and a
     * real database would only add ways for it to fail for reasons that have nothing
     * to do with layout. It holds one list and derives the two the screen reads,
     * because the status transitions are what the action tests are about — two fixed
     * lists could not express an event moving between them.
     */
    private class FakeEventRepository : EventRepository {
        private val all = MutableStateFlow<List<DetectedEvent>>(emptyList())

        fun emitUpcoming(events: List<DetectedEvent>) = merge(events)

        fun emitExpired(events: List<DetectedEvent>) =
            merge(events.map { it.copy(status = EventStatus.EXPIRED) })

        private fun merge(events: List<DetectedEvent>) {
            all.value = all.value.filterNot { row -> events.any { it.id == row.id } } + events
        }

        private fun setStatus(eventId: Long, status: EventStatus) {
            all.value = all.value.map { if (it.id == eventId) it.copy(status = status) else it }
        }

        override suspend fun replaceEventsForMemory(memoryId: Long, events: List<DetectedEvent>) = Unit

        override fun observeUpcoming(): Flow<List<DetectedEvent>> = all.map { list ->
            list.filter {
                it.status == EventStatus.UPCOMING || it.status == EventStatus.IN_CALENDAR
            }.sortedBy { it.eventTime }
        }

        override fun observeExpired(): Flow<List<DetectedEvent>> = all.map { list ->
            list.filter {
                it.status == EventStatus.EXPIRED || it.status == EventStatus.REJECTED
            }.sortedByDescending { it.eventTime }
        }

        override suspend fun expireOverdue(now: Instant): Int = 0
        override suspend fun reject(eventId: Long) = setStatus(eventId, EventStatus.REJECTED)
        override suspend fun undoReject(eventId: Long) = setStatus(eventId, EventStatus.UPCOMING)
        override suspend fun markAddedToCalendar(eventId: Long) =
            setStatus(eventId, EventStatus.IN_CALENDAR)
    }
```

Add the import:

```kotlin
import kotlinx.coroutines.flow.map
```

The four existing tests keep calling `emitUpcoming` / `emitExpired` and are otherwise
untouched by this task.

- [ ] **Step 5: Run the tests and watch them pass**

```bash
JAVA_HOME=/home/imyuvi/.local/jdks/jdk-17.0.20.1+1 ./gradlew connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.package=com.onemind.app
```

Expected: the full instrumented suite green — 86 existing plus the 12 in
`EventStatusTransitionTest`.

---

## Task 3: Batched entity read for the location line

**Files:**
- Modify: `app/src/main/java/com/onemind/app/data/local/dao/DerivedDataDao.kt:70` (append after `getSummaries`)
- Modify: `app/src/main/java/com/onemind/app/domain/repository/MemoryRepository.kt:24` (append after `getMemoriesByIds`)
- Modify: `app/src/main/java/com/onemind/app/data/repository/MemoryRepositoryImpl.kt:128` (append after `getMemoriesByIds`)
- Test: `app/src/androidTest/java/com/onemind/app/DerivedDataDaoTest.kt` (extend)

**Interfaces:**
- Consumes: nothing from earlier tasks.
- Produces:
  - `suspend fun DerivedDataDao.getEntitiesForMemories(memoryIds: List<Long>): List<ExtractedEntityEntity>`
  - `suspend fun MemoryRepository.getEntitiesByMemoryIds(ids: List<Long>): Map<Long, List<ExtractedEntity>>`

**Why not reuse what exists.** `getMemoriesByIds` deliberately hydrates only summaries
and categories; its comment rejects per-row cost because search shares that path and
would pay it on every keystroke. `getMemoryById` does load entities, but calling it once
per event is exactly that per-row cost. So: one narrow batched addition, domain-typed and
unfiltered — which entity type matters is the caller's policy, not the data layer's.

- [ ] **Step 1: Write the failing test**

Append to `app/src/androidTest/java/com/onemind/app/DerivedDataDaoTest.kt`, immediately
after `summariesCanBeFetchedForManyMemoriesAtOnce`:

```kotlin
    @Test
    fun entitiesCanBeFetchedForManyMemoriesAtOnce() = runTest {
        val other = memoryDao.insertMemoryWithBlocks(
            MemoryEntity(
                createdAt = 1L, updatedAt = 1L,
                sourceType = SourceType.MANUAL, processingState = ProcessingState.SAVED
            ),
            emptyList()
        )
        dao.insertEntities(
            listOf(
                ExtractedEntityEntity(
                    memoryId = memoryId, name = "Moscone Center",
                    entityType = EntityType.PLACE, confidence = 0.8f,
                    source = DerivedSource.OCR
                ),
                ExtractedEntityEntity(
                    memoryId = other, name = "Google",
                    entityType = EntityType.ORGANIZATION, confidence = null,
                    source = DerivedSource.USER_TEXT
                )
            )
        )

        // One query for many Memories, so the events list does not pay a round trip
        // per card. Unfiltered: the caller decides that PLACE is the one it wants.
        val fetched = dao.getEntitiesForMemories(listOf(memoryId, other))

        assertEquals(2, fetched.size)
        assertEquals(
            setOf(memoryId, other),
            fetched.map { it.memoryId }.toSet()
        )
    }
```

- [ ] **Step 2: Run the test and watch it fail**

```bash
JAVA_HOME=/home/imyuvi/.local/jdks/jdk-17.0.20.1+1 ./gradlew connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.onemind.app.DerivedDataDaoTest
```

Expected: compilation failure, `Unresolved reference: getEntitiesForMemories`.

- [ ] **Step 3: Add the DAO query**

In `app/src/main/java/com/onemind/app/data/local/dao/DerivedDataDao.kt`, immediately
after `getSummaries`:

```kotlin

    /**
     * Entities for many Memories at once, so a list of events needs one query.
     *
     * Sits beside [getSummaries] and exists for the same reason: a round trip per
     * rendered row is a cost paid on every scroll. Returns every type — the caller
     * picks out the one it cares about.
     */
    @Query("SELECT * FROM extracted_entities WHERE memoryId IN (:memoryIds)")
    suspend fun getEntitiesForMemories(memoryIds: List<Long>): List<ExtractedEntityEntity>
```

- [ ] **Step 4: Add the repository method**

In `app/src/main/java/com/onemind/app/domain/repository/MemoryRepository.kt`, after
`getMemoriesByIds`:

```kotlin

    /**
     * The extracted entities of several Memories, keyed by Memory.
     *
     * Separate from [getMemoriesByIds] on purpose. That method carries the summary and
     * categories and nothing else, because search shares it and would pay any widening
     * on every keystroke. Callers that genuinely need entities ask for them here, and
     * only they pay.
     *
     * A Memory with no entities is absent from the map rather than mapped to an empty
     * list; callers should use `orEmpty()`.
     */
    suspend fun getEntitiesByMemoryIds(ids: List<Long>): Map<Long, List<ExtractedEntity>>
```

and the import:

```kotlin
import com.onemind.app.domain.model.ExtractedEntity
```

In `app/src/main/java/com/onemind/app/data/repository/MemoryRepositoryImpl.kt`, after
`getMemoriesByIds`:

```kotlin

    override suspend fun getEntitiesByMemoryIds(ids: List<Long>): Map<Long, List<ExtractedEntity>> {
        if (ids.isEmpty()) return emptyMap()

        // Chunked for the same reason every other batched read here is: Room expands
        // `IN (:ids)` to one bind parameter per element and SQLite caps those at 999.
        return with(DerivedMapper) {
            ids.chunked(SQL_VARIABLE_LIMIT)
                .flatMap { derivedDataDao.getEntitiesForMemories(it) }
                .map { it.toDomain() }
                .groupBy { it.memoryId }
        }
    }
```

and the import:

```kotlin
import com.onemind.app.domain.model.ExtractedEntity
```

- [ ] **Step 5: Run the test and watch it pass**

```bash
JAVA_HOME=/home/imyuvi/.local/jdks/jdk-17.0.20.1+1 ./gradlew connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.onemind.app.DerivedDataDaoTest
```

Expected: all `DerivedDataDaoTest` tests pass, including the new one.

---

## Task 4: `EventCardUi` and the ViewModel that assembles it

**Files:**
- Create: `app/src/main/java/com/onemind/app/ui/events/EventCardUi.kt`
- Modify: `app/src/main/java/com/onemind/app/ui/events/EventsViewModel.kt` (whole file)
- Test: `app/src/test/java/com/onemind/app/EventCardAssemblyTest.kt` (create)

**Interfaces:**
- Consumes: `MemoryRepository.getEntitiesByMemoryIds` (Task 3), `EventRepository.reject` / `undoReject` / `markAddedToCalendar` (Task 2).
- Produces:
  - `data class EventCardUi(val event: DetectedEvent, val location: String?, val categories: List<Category>)`
  - `object EventCardAssembly { const val MAX_CHIPS = 3; fun assemble(events, entities, categories): List<EventCardUi> }`
  - `EventsUiState(upcomingEvents: List<EventCardUi>, expiredEvents: List<EventCardUi>, isLoading: Boolean)`
  - `EventsViewModel.reject(eventId: Long)`, `.undoReject(eventId: Long)`, `.markAddedToCalendar(eventId: Long)`, unchanged `.exportToCalendar(event: DetectedEvent): Intent`
  - `EventsViewModel` constructor becomes `(events: EventRepository, memories: MemoryRepository, clock: Clock)`

The assembly is a pure object rather than a private ViewModel method so it can be tested
on the JVM, the same shape as `ReminderPlanner` and `DateGrouping`. **This is a
constructor change, so `assembleDebug` is mandatory before Issue A's commit.**

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/com/onemind/app/EventCardAssemblyTest.kt`:

```kotlin
package com.onemind.app

import com.onemind.app.domain.model.Category
import com.onemind.app.domain.model.DerivedSource
import com.onemind.app.domain.model.DetectedEvent
import com.onemind.app.domain.model.EntityType
import com.onemind.app.domain.model.ExtractedEntity
import com.onemind.app.ui.events.EventCardAssembly
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

/**
 * That an event card shows the location and categories the mock asks for, from data
 * the app already had.
 *
 * `DetectedEvent` has neither field, which looked at first like two cuts. It is not:
 * a Memory's `PLACE` entities and its categories are both reachable from `memoryId`,
 * with no new extraction and no pipeline change. This is where that assembly lives so
 * it can be checked without a device.
 */
class EventCardAssemblyTest {

    private val at: Instant = Instant.parse("2026-09-15T09:00:00Z")

    private fun event(id: Long, memoryId: Long) = DetectedEvent(
        id = id,
        memoryId = memoryId,
        eventTime = at,
        eventTitle = "AI Summit"
    )

    private fun entity(memoryId: Long, name: String, type: EntityType) = ExtractedEntity(
        memoryId = memoryId,
        name = name,
        entityType = type,
        confidence = null,
        source = DerivedSource.OCR
    )

    private fun category(id: Long, name: String) = Category(id = id, name = name)

    @Test
    fun theLocationComesFromThePlaceEntity() {
        val cards = EventCardAssembly.assemble(
            events = listOf(event(1L, memoryId = 7L)),
            entities = mapOf(7L to listOf(entity(7L, "Moscone Center", EntityType.PLACE))),
            categories = emptyMap()
        )

        assertEquals("Moscone Center", cards.single().location)
    }

    @Test
    fun otherEntityTypesAreNotMistakenForALocation() {
        val cards = EventCardAssembly.assemble(
            events = listOf(event(1L, memoryId = 7L)),
            entities = mapOf(
                7L to listOf(
                    entity(7L, "Google", EntityType.ORGANIZATION),
                    entity(7L, "Sundar", EntityType.PERSON)
                )
            ),
            categories = emptyMap()
        )

        assertNull(cards.single().location)
    }

    @Test
    fun aMemoryWithNoEntitiesSimplyHasNoLocation() {
        val cards = EventCardAssembly.assemble(
            events = listOf(event(1L, memoryId = 7L)),
            entities = emptyMap(),
            categories = emptyMap()
        )

        // The mock treats the pin as optional, so absence is a rendering decision,
        // not a hole to fill with a placeholder.
        assertNull(cards.single().location)
    }

    @Test
    fun atMostThreeCategoryChipsAreCarried() {
        val cards = EventCardAssembly.assemble(
            events = listOf(event(1L, memoryId = 7L)),
            entities = emptyMap(),
            categories = mapOf(
                7L to listOf(
                    category(1L, "Work"), category(2L, "Travel"),
                    category(3L, "Tech"), category(4L, "Finance")
                )
            )
        )

        assertEquals(
            listOf("Work", "Travel", "Tech"),
            cards.single().categories.map { it.name }
        )
    }

    @Test
    fun eachEventReadsOnlyItsOwnMemorysData() {
        val cards = EventCardAssembly.assemble(
            events = listOf(event(1L, memoryId = 7L), event(2L, memoryId = 8L)),
            entities = mapOf(
                7L to listOf(entity(7L, "Moscone Center", EntityType.PLACE)),
                8L to listOf(entity(8L, "Dentist on 5th", EntityType.PLACE))
            ),
            categories = mapOf(7L to listOf(category(1L, "Work")))
        )

        assertEquals("Moscone Center", cards[0].location)
        assertEquals(listOf("Work"), cards[0].categories.map { it.name })
        assertEquals("Dentist on 5th", cards[1].location)
        assertTrue(cards[1].categories.isEmpty())
    }

    @Test
    fun theEventItselfIsCarriedThroughUntouched() {
        val original = event(1L, memoryId = 7L)

        val card = EventCardAssembly.assemble(listOf(original), emptyMap(), emptyMap()).single()

        // The actions need an id to act on, so the card carries the event rather than
        // flattening it into display strings.
        assertSame(original, card.event)
    }
}
```

- [ ] **Step 2: Run the test and watch it fail**

```bash
JAVA_HOME=/home/imyuvi/.local/jdks/jdk-17.0.20.1+1 ./gradlew testDebugUnitTest \
  --tests "com.onemind.app.EventCardAssemblyTest"
```

Expected: compilation failure, `Unresolved reference: EventCardAssembly`.

- [ ] **Step 3: Create `EventCardUi.kt`**

Create `app/src/main/java/com/onemind/app/ui/events/EventCardUi.kt`:

```kotlin
package com.onemind.app.ui.events

import com.onemind.app.domain.model.Category
import com.onemind.app.domain.model.DetectedEvent
import com.onemind.app.domain.model.EntityType
import com.onemind.app.domain.model.ExtractedEntity

/**
 * One event as the Events screen draws it.
 *
 * Carries the [DetectedEvent] whole rather than flattening it into strings, because
 * every action on the card needs its id and its status. The two extra fields are not
 * on `DetectedEvent` and deliberately are not being added to it: they belong to the
 * Memory the event is a lens on, and reading them is a presentation concern.
 */
data class EventCardUi(
    val event: DetectedEvent,
    /** The Memory's first PLACE entity, or null when it named no place. */
    val location: String? = null,
    /** At most [EventCardAssembly.MAX_CHIPS] of the Memory's categories. */
    val categories: List<Category> = emptyList()
)

/**
 * Joins events to the Memory data their cards show.
 *
 * Pure and separate from the ViewModel so it can be checked on the JVM, the same
 * reasoning that put `ReminderPlanner` and `DateGrouping` in their own files. It takes
 * maps rather than a repository because deciding *what* a card shows and deciding
 * *how many queries that costs* are different problems.
 */
object EventCardAssembly {

    /** How many category chips fit a card before it starts wrapping. */
    const val MAX_CHIPS = 3

    fun assemble(
        events: List<DetectedEvent>,
        entities: Map<Long, List<ExtractedEntity>>,
        categories: Map<Long, List<Category>>
    ): List<EventCardUi> = events.map { event ->
        EventCardUi(
            event = event,
            // First rather than best: entities carry a confidence, but it is nullable
            // and often absent, so ranking on it would mostly be ranking on nothing.
            location = entities[event.memoryId]
                ?.firstOrNull { it.entityType == EntityType.PLACE }
                ?.name,
            categories = categories[event.memoryId].orEmpty().take(MAX_CHIPS)
        )
    }
}
```

- [ ] **Step 4: Run the test and watch it pass**

```bash
JAVA_HOME=/home/imyuvi/.local/jdks/jdk-17.0.20.1+1 ./gradlew testDebugUnitTest \
  --tests "com.onemind.app.EventCardAssemblyTest"
```

Expected: 6 tests, 0 failures.

- [ ] **Step 5: Rewrite `EventsViewModel`**

Replace the whole of `app/src/main/java/com/onemind/app/ui/events/EventsViewModel.kt`
with:

```kotlin
package com.onemind.app.ui.events

import android.content.Intent
import android.provider.CalendarContract
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.onemind.app.domain.model.DetectedEvent
import com.onemind.app.domain.repository.EventRepository
import com.onemind.app.domain.repository.MemoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Duration
import java.time.Instant
import javax.inject.Inject

@HiltViewModel
class EventsViewModel @Inject constructor(
    private val events: EventRepository,
    private val memories: MemoryRepository,
    private val clock: Clock
) : ViewModel() {

    private val _uiState = MutableStateFlow(EventsUiState())
    val uiState: StateFlow<EventsUiState> = _uiState.asStateFlow()

    init {
        expireOverdueEvents()
        observeEvents()
    }

    private fun expireOverdueEvents() {
        viewModelScope.launch {
            events.expireOverdue(Instant.now(clock))
        }
    }

    private fun observeEvents() {
        viewModelScope.launch {
            events.observeUpcoming().collect { upcoming ->
                val cards = hydrate(upcoming)
                _uiState.update { it.copy(upcomingEvents = cards, isLoading = false) }
            }
        }
        viewModelScope.launch {
            events.observeExpired().collect { expired ->
                val cards = hydrate(expired)
                _uiState.update { it.copy(expiredEvents = cards) }
            }
        }
    }

    /**
     * Attach the Memory data a card shows: a location, and up to three categories.
     *
     * Two batched queries per emission, not two per card. Both are keyed on
     * `memoryId`, and one Memory can own several events, so the ids are deduplicated
     * before either call.
     */
    private suspend fun hydrate(list: List<DetectedEvent>): List<EventCardUi> {
        if (list.isEmpty()) return emptyList()

        val memoryIds = list.map { it.memoryId }.distinct()
        val entities = memories.getEntitiesByMemoryIds(memoryIds)
        val categories = memories.getMemoriesByIds(memoryIds)
            .associate { it.id to it.derived.categories }

        return EventCardAssembly.assemble(list, entities, categories)
    }

    /** The user declined this event. Reversible from the same card. */
    fun reject(eventId: Long) {
        viewModelScope.launch { events.reject(eventId) }
    }

    /** Take back a rejection. */
    fun undoReject(eventId: Long) {
        viewModelScope.launch { events.undoReject(eventId) }
    }

    /**
     * Record that the calendar app's add-event screen was opened for this event.
     *
     * Called by the screen *after* the intent is dispatched, because whether it could
     * be dispatched at all is something only the screen — which holds the `Context` —
     * can know. What the user then did in the calendar app is not observable, so this
     * records the export, not a confirmed calendar entry.
     */
    fun markAddedToCalendar(eventId: Long) {
        viewModelScope.launch { events.markAddedToCalendar(eventId) }
    }

    /**
     * Export an event to the user's calendar app via ACTION_INSERT.
     *
     * This does not require any permission — it launches the calendar app's own
     * "add event" screen, and the user confirms or cancels. That is the right UX:
     * oneMind detected the event, but the user decides whether to commit it to
     * their actual calendar and set their own reminders there.
     *
     * Returns the `Intent` rather than launching it, which is what keeps a `Context`
     * out of this class.
     */
    fun exportToCalendar(event: DetectedEvent): Intent {
        val begin = event.eventTime.toEpochMilli()
        return Intent(Intent.ACTION_INSERT).apply {
            data = CalendarContract.Events.CONTENT_URI
            putExtra(CalendarContract.Events.TITLE, event.eventTitle)
            putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, begin)
            putExtra(CalendarContract.EXTRA_EVENT_END_TIME, begin + DEFAULT_DURATION.toMillis())
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
    }

    private companion object {
        /** What to suggest when the content gave us a start but no end. */
        val DEFAULT_DURATION: Duration = Duration.ofHours(1)
    }
}

data class EventsUiState(
    val upcomingEvents: List<EventCardUi> = emptyList(),
    val expiredEvents: List<EventCardUi> = emptyList(),
    val isLoading: Boolean = true
)
```

- [ ] **Step 6: Verify the Hilt graph and the JVM suite**

```bash
JAVA_HOME=/home/imyuvi/.local/jdks/jdk-17.0.20.1+1 ./gradlew assembleDebug testDebugUnitTest
```

Expected: BUILD SUCCESSFUL. `EventsScreen.kt` will not compile yet — it still passes
`DetectedEvent` where `EventCardUi` is now required, and constructs the ViewModel with
two arguments. Task 5 fixes both, so if `assembleDebug` fails here with errors *only* in
`EventsScreen.kt`, continue to Task 5 and re-run this command at its end. Any other
error, especially a Hilt one about `MemoryRepository`, must be fixed before moving on.

---

## Task 5: The Events screen gains actions

**Files:**
- Modify: `app/src/main/java/com/onemind/app/ui/events/EventsScreen.kt` (whole file)
- Modify: `app/src/androidTest/java/com/onemind/app/EventsScreenTest.kt:68-82`, `:153`, and append tests

**Interfaces:**
- Consumes: `EventCardUi`, `EventsUiState`, `EventsViewModel.reject` / `undoReject` / `markAddedToCalendar` / `exportToCalendar` (Task 4).
- Produces: no Kotlin API. User-visible strings later tasks and phase 3 depend on:
  `"Upcoming"`, `"Expired & rejected"`, `"Events"`, `"No upcoming events"`,
  content descriptions `"Back"`, `"Add to calendar"`, `"Reject"`, and the labels
  `"In calendar"`, `"Rejected"`, `"Undo"`.

**Scope note.** This is phase 2, not phase 3. Keep the existing Material3 idiom —
`Scaffold`, `TopAppBar`, `Card`, `IconButton`. The expressive restyle of this screen is
a separate slice in `docs/superpowers/plans/2026-08-24-m3-expressive-redesign.md`, and
doing it here would make this commit unreviewable.

- [ ] **Step 1: Write the failing tests**

In `app/src/androidTest/java/com/onemind/app/EventsScreenTest.kt`:

First, update `renderScreen` (`:68-82`) to supply the ViewModel's new dependency:

```kotlin
    private fun renderScreen() {
        composeRule.activity.runOnUiThread {
            composeRule.activity.enableEdgeToEdge()
        }
        composeRule.setContent {
            OneMindTheme {
                EventsScreen(
                    onNavigateToMemory = {},
                    onNavigateBack = { backPresses++ },
                    // Relaxed: this screen's Memory-side data is the location line and
                    // the category chips, and neither is what these tests are about.
                    // An empty map and an empty list are exactly the "Memory named no
                    // place and had no categories" case, which must render.
                    viewModel = EventsViewModel(repository, mockk(relaxed = true), Clock.systemUTC())
                )
            }
        }
        composeRule.waitForIdle()
    }
```

Add the import:

```kotlin
import io.mockk.mockk
```

Second, in `eventsAreListedUnderTheirHeadings` (`:153`), change:

```kotlin
        composeRule.onNodeWithText("Past events").assertIsDisplayed()
```

to:

```kotlin
        composeRule.onNodeWithText("Expired & rejected").assertIsDisplayed()
```

Third, append these tests inside the class:

```kotlin
    @Test
    fun rejectingAnEventMovesItToThePastList() {
        repository.emitUpcoming(listOf(event("Dentist on Thursday")))
        renderScreen()

        composeRule.onNodeWithContentDescription("Reject").performClick()
        composeRule.waitForIdle()

        // Not deleted — rejecting is reversible, and a row nothing renders cannot be
        // undone.
        composeRule.onNodeWithText("Expired & rejected").assertIsDisplayed()
        composeRule.onNodeWithText("Rejected").assertIsDisplayed()
        composeRule.onNodeWithText("Dentist on Thursday").assertIsDisplayed()
    }

    @Test
    fun aRejectedEventCanBeUndone() {
        repository.emitUpcoming(listOf(event("Dentist on Thursday")))
        renderScreen()
        composeRule.onNodeWithContentDescription("Reject").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Undo").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Upcoming").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Reject").assertIsDisplayed()
    }

    @Test
    fun anEventAddedToTheCalendarStaysUpcomingAndSaysSo() {
        repository.emitUpcoming(
            listOf(event("AI Summit", status = EventStatus.IN_CALENDAR))
        )
        renderScreen()

        composeRule.onNodeWithText("Upcoming").assertIsDisplayed()
        composeRule.onNodeWithText("In calendar").assertIsDisplayed()
        // Its only remaining transition is expiry, so neither action is offered.
        composeRule.onNodeWithContentDescription("Add to calendar").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Reject").assertDoesNotExist()
    }

    @Test
    fun anExpiredEventOffersNoActions() {
        repository.emitExpired(
            listOf(event("Concert last week", at = Instant.now().minus(Duration.ofDays(7))))
        )
        renderScreen()

        composeRule.onNodeWithText("Concert last week").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Add to calendar").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Reject").assertDoesNotExist()
        composeRule.onNodeWithText("Undo").assertDoesNotExist()
    }
```

Add the import:

```kotlin
import androidx.compose.ui.test.assertDoesNotExist
```

and widen the `event` helper to take a status:

```kotlin
    private fun event(
        title: String,
        at: Instant = Instant.now().plus(Duration.ofDays(3)),
        status: EventStatus = EventStatus.UPCOMING
    ) = DetectedEvent(
        id = title.hashCode().toLong(),
        memoryId = 1L,
        eventTime = at,
        eventTitle = title,
        status = status
    )
```

- [ ] **Step 2: Run the tests and watch them fail**

```bash
JAVA_HOME=/home/imyuvi/.local/jdks/jdk-17.0.20.1+1 ./gradlew connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.onemind.app.EventsScreenTest
```

Expected: compilation failure in `EventsScreen.kt` (the ViewModel's new parameter, and
`EventsUiState`'s lists now holding `EventCardUi`).

- [ ] **Step 3: Rewrite `EventsScreen.kt`**

Replace the whole of `app/src/main/java/com/onemind/app/ui/events/EventsScreen.kt` with:

```kotlin
package com.onemind.app.ui.events

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.onemind.app.domain.model.DetectedEvent
import com.onemind.app.domain.model.EventStatus
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * The Events tab.
 *
 * Two lists. Upcoming holds events still ahead, including ones the user has exported
 * to their calendar — exporting is a copy, not a dismissal. Below it, expired events
 * and rejected ones share a list, because a rejection is reversible and an event
 * nothing renders cannot be undone.
 *
 * The [Scaffold] is not decoration. `MainActivity` calls `enableEdgeToEdge()`, so
 * every destination owns its own window insets, and a `Scaffold` with a
 * [TopAppBar] is how every other screen here handles them. Without one this screen
 * drew its first row under the status bar, on top of the system clock, and had no
 * back affordance at all — alone among pushed destinations.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventsScreen(
    onNavigateToMemory: (Long) -> Unit,
    onNavigateBack: () -> Unit,
    viewModel: EventsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Events") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        // Every branch is inside the Scaffold, including loading and empty. An early
        // return would have taken the top bar with it, and with it the way back —
        // stranding a user who opened Events before saving anything with a date.
        when {
            uiState.isLoading -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }

            uiState.upcomingEvents.isEmpty() && uiState.expiredEvents.isEmpty() ->
                EmptyEventsState(modifier = Modifier.padding(paddingValues))

            else -> EventList(
                uiState = uiState,
                modifier = Modifier.padding(paddingValues),
                onTapEvent = onNavigateToMemory,
                onAddToCalendar = { event ->
                    // Mark only if the calendar app actually opened. A device with no
                    // calendar app at all should not end up with an event claiming to
                    // be in one. What the user does *inside* that app is not
                    // observable either way.
                    runCatching { context.startActivity(viewModel.exportToCalendar(event)) }
                        .onSuccess { viewModel.markAddedToCalendar(event.id) }
                },
                onReject = { event -> viewModel.reject(event.id) },
                onUndoReject = { event -> viewModel.undoReject(event.id) }
            )
        }
    }
}

@Composable
private fun EventList(
    uiState: EventsUiState,
    modifier: Modifier = Modifier,
    onTapEvent: (Long) -> Unit,
    onAddToCalendar: (DetectedEvent) -> Unit,
    onReject: (DetectedEvent) -> Unit,
    onUndoReject: (DetectedEvent) -> Unit
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (uiState.upcomingEvents.isNotEmpty()) {
            item {
                Text(
                    text = "Upcoming",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            items(uiState.upcomingEvents, key = { it.event.id }) { card ->
                EventCard(
                    card = card,
                    onTap = { onTapEvent(card.event.memoryId) },
                    onAddToCalendar = { onAddToCalendar(card.event) },
                    onReject = { onReject(card.event) },
                    onUndoReject = { onUndoReject(card.event) }
                )
            }
        }

        if (uiState.expiredEvents.isNotEmpty()) {
            item {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Expired & rejected",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            items(uiState.expiredEvents, key = { it.event.id }) { card ->
                EventCard(
                    card = card,
                    onTap = { onTapEvent(card.event.memoryId) },
                    onAddToCalendar = { onAddToCalendar(card.event) },
                    onReject = { onReject(card.event) },
                    onUndoReject = { onUndoReject(card.event) }
                )
            }
        }
    }
}

@Composable
private fun EventCard(
    card: EventCardUi,
    onTap: () -> Unit,
    onAddToCalendar: () -> Unit,
    onReject: () -> Unit,
    onUndoReject: () -> Unit
) {
    val status = card.event.status
    val isPast = status == EventStatus.EXPIRED || status == EventStatus.REJECTED
    val alpha = if (isPast) 0.6f else 1f

    Card(
        onClick = onTap,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isPast) {
                MaterialTheme.colorScheme.surfaceContainerLow
            } else {
                MaterialTheme.colorScheme.surfaceContainerHigh
            }
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = card.event.eventTitle,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = formatEventTime(card.event.eventTime),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = alpha)
                    )

                    if (card.location != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Place,
                                // Null: the place name beside it already says this,
                                // and a screen reader should not hear "place" twice.
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = card.location,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha)
                            )
                        }
                    }
                }

                EventActions(
                    status = status,
                    onAddToCalendar = onAddToCalendar,
                    onReject = onReject,
                    onUndoReject = onUndoReject
                )
            }

            if (card.categories.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    card.categories.forEach { category ->
                        CategoryChip(name = category.name, alpha = alpha)
                    }
                }
            }
        }
    }
}

/**
 * What a card offers, by where its event stands.
 *
 * Exhaustive over [EventStatus] without an `else`, deliberately: a fifth status should
 * stop the build here rather than quietly render a card nobody can act on.
 */
@Composable
private fun EventActions(
    status: EventStatus,
    onAddToCalendar: () -> Unit,
    onReject: () -> Unit,
    onUndoReject: () -> Unit
) {
    when (status) {
        EventStatus.UPCOMING -> Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onAddToCalendar) {
                Icon(
                    imageVector = Icons.Default.CalendarMonth,
                    contentDescription = "Add to calendar",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            IconButton(onClick = onReject) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Reject",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Its only remaining transition is expiry, so there is nothing to offer.
        EventStatus.IN_CALENDAR -> StatusPill(
            label = "In calendar",
            container = MaterialTheme.colorScheme.primaryContainer,
            content = MaterialTheme.colorScheme.onPrimaryContainer
        )

        EventStatus.REJECTED -> Row(verticalAlignment = Alignment.CenterVertically) {
            StatusPill(
                label = "Rejected",
                container = MaterialTheme.colorScheme.surfaceContainerHighest,
                content = MaterialTheme.colorScheme.onSurfaceVariant
            )
            TextButton(onClick = onUndoReject) { Text("Undo") }
        }

        EventStatus.EXPIRED -> Unit
    }
}

@Composable
private fun StatusPill(label: String, container: Color, content: Color) {
    Surface(shape = MaterialTheme.shapes.small, color = container) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = content,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

/**
 * A category, as a label rather than an `AssistChip`.
 *
 * No click semantics on purpose: the whole card is already clickable, and a nested
 * clickable would give a screen reader a second target that does the same thing.
 */
@Composable
private fun CategoryChip(name: String, alpha: Float) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = alpha)
    ) {
        Text(
            text = name,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = alpha),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun EmptyEventsState(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "No upcoming events",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "When you save something with a future date,\nit will appear here automatically",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

private fun formatEventTime(at: Instant): String {
    val formatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
        .withZone(ZoneId.systemDefault())
    return formatter.format(at)
}
```

`StatusPill` takes `Color`, so add this import to the block above:

```kotlin
import androidx.compose.ui.graphics.Color
```

- [ ] **Step 4: Run the tests and watch them pass**

```bash
JAVA_HOME=/home/imyuvi/.local/jdks/jdk-17.0.20.1+1 ./gradlew connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.onemind.app.EventsScreenTest
```

Expected: 8 tests, 0 failures.

- [ ] **Step 5: Full verification for Issue A**

```bash
JAVA_HOME=/home/imyuvi/.local/jdks/jdk-17.0.20.1+1 ./gradlew assembleDebug testDebugUnitTest
JAVA_HOME=/home/imyuvi/.local/jdks/jdk-17.0.20.1+1 ./gradlew connectedDebugAndroidTest
git status --short app/schemas/
```

Expected: BUILD SUCCESSFUL on all three Gradle tasks, and no output from `git status`.

- [ ] **Step 6: File Issue A**

```bash
python3 - <<'PY'
import json, re, urllib.request
pat = re.search(r'^GITHUB_PAT=(.*)$',
                open('/home/imyuvi/projects/codingagents/.env').read(), re.M).group(1).strip()
body = {
  "title": "Events cannot be rejected or marked as added to calendar",
  "body": """The Events screen detects events and offers a single action: export to a
calendar app. There is no way to decline an event that was detected wrongly or that the
user does not care about, and no way to see that one has already been exported — tapping
"add to calendar" a second time silently opens the calendar app again.

`EventStatus` only has `UPCOMING` and `EXPIRED`, so the model has nowhere to record
either decision.

**Fix.** Add `REJECTED` and `IN_CALENDAR`. Rejected events join the past list, where an
undo returns them to upcoming; events added to a calendar stay in the upcoming list with
a status pill, because exporting is a copy and not a dismissal.

No migration: `detected_events.status` is TEXT persisted by enum name, so the schema
stays at version 5. The real hazard is the six SQL literals across five `EventDao`
queries that spell `'UPCOMING'` and `'EXPIRED'` out by hand — nothing fails to compile
when the enum grows, the queries just stop meaning what their names say.

The cards also gain the location line and category chips the design reference shows.
Both read data that already exists — `EntityType.PLACE` entities and `memory_categories`
— via one new batched query each. No pipeline change, no new extraction.

Design: `docs/superpowers/specs/2026-08-24-onemind-ui-redesign-design.md`
Plan: `docs/superpowers/plans/2026-08-24-events-reject-and-calendar.md`"""
}
req = urllib.request.Request(
    "https://api.github.com/repos/Yuvraj-ai/oneMind/issues",
    data=json.dumps(body).encode(),
    headers={"Authorization": f"Bearer {pat}", "Accept": "application/vnd.github+json",
             "Content-Type": "application/json"})
print("issue", json.load(urllib.request.urlopen(req))["number"])
PY
```

Note the number it prints; call it `A` below.

- [ ] **Step 7: Commit Issue A**

Confirm the branch first — this must never land on `main`:

```bash
git rev-parse --abbrev-ref HEAD
```

Expected: `my-extra-work`.

```bash
git add app/src/main/java/com/onemind/app/domain/model/DetectedEvent.kt \
        app/src/main/java/com/onemind/app/data/local/dao/EventDao.kt \
        app/src/main/java/com/onemind/app/data/local/dao/DerivedDataDao.kt \
        app/src/main/java/com/onemind/app/domain/repository/EventRepository.kt \
        app/src/main/java/com/onemind/app/domain/repository/MemoryRepository.kt \
        app/src/main/java/com/onemind/app/data/repository/EventRepositoryImpl.kt \
        app/src/main/java/com/onemind/app/data/repository/MemoryRepositoryImpl.kt \
        app/src/main/java/com/onemind/app/ui/events/EventCardUi.kt \
        app/src/main/java/com/onemind/app/ui/events/EventsViewModel.kt \
        app/src/main/java/com/onemind/app/ui/events/EventsScreen.kt \
        app/src/test/java/com/onemind/app/EventCardAssemblyTest.kt \
        app/src/androidTest/java/com/onemind/app/EventStatusTransitionTest.kt \
        app/src/androidTest/java/com/onemind/app/EventsScreenTest.kt \
        app/src/androidTest/java/com/onemind/app/DerivedDataDaoTest.kt

git commit -F - <<'MSG'
feat(events): let a user reject an event or mark it added to calendar (#A)

`EventStatus` gains REJECTED and IN_CALENDAR. Rejecting is reversible and puts
the event in the past list beside expired ones, because an event nothing renders
cannot be undone. Adding to a calendar leaves it upcoming — an export is a copy,
not a dismissal — with a pill saying so.

No migration. The status column is TEXT written by enum name, so the schema is
untouched at version 5. What did have to change is the six SQL literals across
five EventDao queries that spelled the two old statuses out by hand: each became
an incomplete enumeration the moment the enum grew, and none of them stopped
compiling. EventStatusTransitionTest is what makes that visible.

undoReject clears remindersScheduledAt as well as the status. Rejecting cancels
the enqueued jobs but leaves the mark set, and getUnscheduledReminders skips any
event that has it — so without this, undo would hand back an event nothing would
ever remind about again.

Cards also gain the location line and category chips from the design reference.
Both were already in the database: a Memory's PLACE entities and its categories,
reachable from the event's memoryId. Entities needed one new batched read rather
than a widening of getMemoriesByIds, which search shares and would have paid for
on every keystroke.
MSG
```

Replace `#A` with the real issue number before running. The commit message must carry
**no** `Co-Authored-By` and no generated-by trailer.

- [ ] **Step 8: Close Issue A**

```bash
python3 - <<'PY'
import json, re, urllib.request
NUMBER = 0  # <- set to A
pat = re.search(r'^GITHUB_PAT=(.*)$',
                open('/home/imyuvi/projects/codingagents/.env').read(), re.M).group(1).strip()
def post(path, data, method=None):
    req = urllib.request.Request(
        f"https://api.github.com/repos/Yuvraj-ai/oneMind{path}",
        data=json.dumps(data).encode(), method=method,
        headers={"Authorization": f"Bearer {pat}", "Accept": "application/vnd.github+json",
                 "Content-Type": "application/json"})
    return json.load(urllib.request.urlopen(req))

post(f"/issues/{NUMBER}/comments", {"body": """Fixed.

`EventStatus` gained `REJECTED` and `IN_CALENDAR`; the six hardcoded status literals
across five `EventDao` queries were widened to sets, and `updateStatus` /
`restoreToUpcoming` added. `EventRepository` gained `reject`, `undoReject` and
`markAddedToCalendar`. Cards now carry a location and up to three category chips,
assembled by a pure `EventCardAssembly` from one batched entity read and the categories
`getMemoriesByIds` already returned.

Worth recording: `undoReject` had to clear `remindersScheduledAt`, not just the status.
The design spec said `updateStatus` was the only write the verbs needed; that was wrong.
`getUnscheduledReminders` skips any event with a non-null mark, so undo would have
returned an event that nothing would ever remind about again.

The schema stayed at version 5, as intended — status is TEXT written by enum name.

Verified: `assembleDebug`, `testDebugUnitTest`, and the full instrumented suite on the
`onemind_test` AVD.

*Investigated and fixed by an AI agent (Claude), reviewed against the design spec.*"""})
post(f"/issues/{NUMBER}", {"state": "closed"}, method="PATCH")
print("closed", NUMBER)
PY
```

---

## Task 6: Reprocessing must not erase the user's decision

**Files:**
- Modify: `app/src/main/java/com/onemind/app/data/repository/EventRepositoryImpl.kt:19-26`
- Test: `app/src/androidTest/java/com/onemind/app/EventStatusTransitionTest.kt` (extend)

**Interfaces:**
- Consumes: `EventStatus.REJECTED` (Task 1), `EventRepositoryImpl` (Task 2).
- Produces: no signature change. `replaceEventsForMemory(memoryId, events)` keeps its
  behaviour for content and gains preservation of `status` and `remindersScheduledAt`
  across a replace, matched on `eventTime`.

**The defect.** `replaceEventsForMemory` deletes every row for the Memory and re-inserts.
Its KDoc defends that correctly for *content*: reprocessing re-derives dates from scratch,
and appending would leave events behind describing text that no longer exists. But status
is not derived from content — it is the user's judgement about it. As written, a retry or
an edit resurrects a rejected event as `UPCOMING` and re-arms its reminders. It is
invisible until a user retries a Memory, which is why it gets its own issue.

- [ ] **Step 1: File Issue B**

Filed before the fix, per project rule.

```bash
python3 - <<'PY'
import json, re, urllib.request
pat = re.search(r'^GITHUB_PAT=(.*)$',
                open('/home/imyuvi/projects/codingagents/.env').read(), re.M).group(1).strip()
body = {
  "title": "Reprocessing a Memory resets a rejected event to upcoming",
  "body": """`EventRepositoryImpl.replaceEventsForMemory` deletes every event row for a
Memory and re-inserts from what the pipeline just derived. Anything the user decided
about those events goes with the old rows.

So: reject an event, then retry that Memory's enrichment or edit its text, and the
rejected event comes back as `UPCOMING` — and, because `remindersScheduledAt` is null on
the new row, `scheduleAll()` re-arms the reminders the rejection cancelled. The user
declined it and gets notified anyway.

The KDoc's defence of replace-not-append is right about *content*: reprocessing
re-derives dates from scratch, and appending would leave events behind describing text
that no longer exists. It just does not hold for status, which is not derived from
content at all.

**Fix.** Read the existing rows before deleting, and carry `status` and
`remindersScheduledAt` forward onto whichever re-derived event lands on the same
`eventTime`. Matching on the instant rather than the id is the point — the ids are new
on every replace, the instant is the event's identity. Events with no time match are
new and arrive `UPCOMING` with null reminders, exactly as today, and an empty
replacement list still clears the Memory.

Contained entirely in `EventRepositoryImpl`, where the storage detail already lives;
`EventDetectionStage` and the rest of `domain/` learn nothing about it."""
}
req = urllib.request.Request(
    "https://api.github.com/repos/Yuvraj-ai/oneMind/issues",
    data=json.dumps(body).encode(),
    headers={"Authorization": f"Bearer {pat}", "Accept": "application/vnd.github+json",
             "Content-Type": "application/json"})
print("issue", json.load(urllib.request.urlopen(req))["number"])
PY
```

Note the number; call it `B`.

- [ ] **Step 2: Write the failing test**

Append inside `EventStatusTransitionTest`:

```kotlin
    // --- reprocessing ------------------------------------------------------

    /** The same event, re-derived, as the pipeline would hand it back. */
    private fun rederived(at: Instant, title: String = "AI Summit") = DetectedEvent(
        memoryId = memoryId,
        eventTime = at,
        eventTitle = title
    )

    @Test
    fun reprocessingKeepsARejectedEventRejected() = runTest {
        val repository = EventRepositoryImpl(dao)
        val at = Instant.now().plus(Duration.ofDays(3))
        val id = dao.insert(
            DetectedEventEntity(memoryId = memoryId, eventTime = at.toEpochMilli(), eventTitle = "AI Summit")
        )
        repository.reject(id)

        repository.replaceEventsForMemory(memoryId, listOf(rederived(at)))

        // Without this the user's rejection is undone by a retry they did not connect
        // to it, and the event they declined starts reminding them again.
        assertEquals(EventStatus.REJECTED, dao.getEventsForMemory(memoryId).single().status)
    }

    @Test
    fun reprocessingKeepsTheRemindersScheduledMark() = runTest {
        val repository = EventRepositoryImpl(dao)
        val at = Instant.now().plus(Duration.ofDays(3))
        dao.insert(
            DetectedEventEntity(
                memoryId = memoryId, eventTime = at.toEpochMilli(),
                eventTitle = "AI Summit", remindersScheduledAt = 1_000L
            )
        )

        repository.replaceEventsForMemory(memoryId, listOf(rederived(at)))

        // Carried with the status, and for the same reason: dropping it would make
        // scheduleAll() enqueue a second set of reminders under new ids for an event
        // that already has them.
        assertEquals(1_000L, dao.getEventsForMemory(memoryId).single().remindersScheduledAt)
        assertTrue(dao.getUnscheduledReminders().isEmpty())
    }

    @Test
    fun anEventAtANewTimeIsTreatedAsNew() = runTest {
        val repository = EventRepositoryImpl(dao)
        val original = Instant.now().plus(Duration.ofDays(3))
        val id = dao.insert(
            DetectedEventEntity(
                memoryId = memoryId, eventTime = original.toEpochMilli(),
                eventTitle = "AI Summit", remindersScheduledAt = 1_000L
            )
        )
        repository.reject(id)

        val moved = Instant.now().plus(Duration.ofDays(5))
        repository.replaceEventsForMemory(memoryId, listOf(rederived(moved)))

        // The user rejected an event on the 3rd. This is one on the 5th — the text
        // changed under it, so it is a different event and inherits nothing.
        val row = dao.getEventsForMemory(memoryId).single()
        assertEquals(EventStatus.UPCOMING, row.status)
        assertNull(row.remindersScheduledAt)
    }

    @Test
    fun anEmptyReplacementStillClearsTheMemory() = runTest {
        val repository = EventRepositoryImpl(dao)
        val id = insertEvent()
        repository.reject(id)

        repository.replaceEventsForMemory(memoryId, emptyList())

        // A Memory whose dates were edited away must end up with no events. Carrying
        // status forward must not turn this into an insert-only path.
        assertTrue(dao.getEventsForMemory(memoryId).isEmpty())
    }

    @Test
    fun onlyTheMatchingEventInheritsAStatus() = runTest {
        val repository = EventRepositoryImpl(dao)
        val kept = Instant.now().plus(Duration.ofDays(3))
        val other = Instant.now().plus(Duration.ofDays(4))
        val keptId = dao.insert(
            DetectedEventEntity(memoryId = memoryId, eventTime = kept.toEpochMilli(), eventTitle = "AI Summit")
        )
        repository.reject(keptId)

        repository.replaceEventsForMemory(
            memoryId,
            listOf(rederived(kept), rederived(other, title = "Dentist"))
        )

        val byTime = dao.getEventsForMemory(memoryId).associateBy { it.eventTime }
        assertEquals(EventStatus.REJECTED, byTime[kept.toEpochMilli()]!!.status)
        assertEquals(EventStatus.UPCOMING, byTime[other.toEpochMilli()]!!.status)
    }
```

Add the import:

```kotlin
import com.onemind.app.domain.model.DetectedEvent
```

- [ ] **Step 3: Run the tests and watch them fail**

```bash
JAVA_HOME=/home/imyuvi/.local/jdks/jdk-17.0.20.1+1 ./gradlew connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.onemind.app.EventStatusTransitionTest
```

Expected: `reprocessingKeepsARejectedEventRejected` fails with
`expected:<REJECTED> but was:<UPCOMING>`, `reprocessingKeepsTheRemindersScheduledMark`
fails with `expected:<1000> but was:<null>`, and `onlyTheMatchingEventInheritsAStatus`
fails. The other two pass — they pin behaviour that already works and must survive.

- [ ] **Step 4: Preserve status across the replace**

In `app/src/main/java/com/onemind/app/data/repository/EventRepositoryImpl.kt`, replace:

```kotlin
    override suspend fun replaceEventsForMemory(memoryId: Long, events: List<DetectedEvent>) {
        // Clear first and unconditionally: a Memory whose dates were edited away
        // must end up with no events, which an insert-only path cannot express.
        dao.deleteForMemory(memoryId)
        if (events.isNotEmpty()) {
            dao.insertAll(events.map { it.toEntity() })
        }
    }
```

with:

```kotlin
    override suspend fun replaceEventsForMemory(memoryId: Long, events: List<DetectedEvent>) {
        // Status is the user's judgement about an event, not something re-derived from
        // the Memory's text, so it has to outlive the rows that carry it. Read before
        // deleting. Keyed on eventTime rather than id because the ids are new on every
        // replace and the instant is what identifies the event; two events at the same
        // instant in one Memory would collapse here, which is a case the pipeline does
        // not produce and which would be indistinguishable to a user anyway.
        val previous = dao.getEventsForMemory(memoryId).associateBy { it.eventTime }

        // Clear first and unconditionally: a Memory whose dates were edited away
        // must end up with no events, which an insert-only path cannot express.
        dao.deleteForMemory(memoryId)
        if (events.isEmpty()) return

        dao.insertAll(
            events.map { event ->
                val entity = event.toEntity()
                val carried = previous[entity.eventTime] ?: return@map entity
                // remindersScheduledAt travels with the status: dropping it would have
                // scheduleAll() enqueue a second set of reminders under new ids for an
                // event that already has them.
                entity.copy(
                    status = carried.status,
                    remindersScheduledAt = carried.remindersScheduledAt
                )
            }
        )
    }
```

The row's `id` is deliberately *not* carried — the old row is gone, and `event.toEntity()`
leaves `id = 0` so Room assigns a new one.

- [ ] **Step 5: Run the tests and watch them pass**

```bash
JAVA_HOME=/home/imyuvi/.local/jdks/jdk-17.0.20.1+1 ./gradlew connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.onemind.app.EventStatusTransitionTest
```

Expected: 17 tests, 0 failures.

- [ ] **Step 6: Verify nothing else moved**

```bash
JAVA_HOME=/home/imyuvi/.local/jdks/jdk-17.0.20.1+1 ./gradlew assembleDebug testDebugUnitTest
JAVA_HOME=/home/imyuvi/.local/jdks/jdk-17.0.20.1+1 ./gradlew connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.onemind.app.EventDetectionToReminderTest
```

Expected: both BUILD SUCCESSFUL. The second matters because it is the end-to-end
detection-to-reminder path that runs `replaceEventsForMemory` for real.

- [ ] **Step 7: Commit and close Issue B**

```bash
git add app/src/main/java/com/onemind/app/data/repository/EventRepositoryImpl.kt \
        app/src/androidTest/java/com/onemind/app/EventStatusTransitionTest.kt

git commit -F - <<'MSG'
fix(events): keep a user's decision across reprocessing (#B)

replaceEventsForMemory deleted every row for a Memory and re-inserted, so a
retry or an edit resurrected a rejected event as UPCOMING and — with a null
remindersScheduledAt on the new row — got scheduleAll to re-arm the reminders
the rejection had cancelled.

The KDoc's defence of replace-not-append still holds for content: reprocessing
re-derives dates from scratch and appending would leave events behind describing
text that no longer exists. It does not hold for status, which is not derived
from content at all.

So: read the rows before deleting and carry status and remindersScheduledAt onto
whichever re-derived event lands on the same eventTime. Matched on the instant,
not the id, because the ids are new on every replace. An event at a new time is
a new event and inherits nothing, and an empty list still clears the Memory.

Contained in EventRepositoryImpl. EventDetectionStage and the rest of domain/
learn nothing about it.
MSG
```

Then close, with `NUMBER` set to `B` and this comment body:

```
Fixed. `replaceEventsForMemory` now reads the Memory's existing rows before deleting and
carries `status` and `remindersScheduledAt` onto whichever re-derived event lands on the
same `eventTime`.

Matched on the instant rather than the id, since the ids are new on every replace. An
event at a different time inherits nothing — the text moved under it, so it is a
different event — and an empty replacement list still clears the Memory, which is the
behaviour the original unconditional delete existed to guarantee.

Five instrumented tests in `EventStatusTransitionTest` cover it, including the two that
pin the behaviour that already worked and had to survive. `EventDetectionToReminderTest`
re-run as well, since it is the end-to-end path that calls this for real.

*Investigated and fixed by an AI agent (Claude), reviewed against the design spec.*
```

---

## Task 7: Rejecting an event cancels its reminders

**Files:**
- Modify: `app/src/main/java/com/onemind/app/data/events/EventReminderScheduler.kt:108-123`
- Modify: `app/src/main/java/com/onemind/app/data/repository/EventRepositoryImpl.kt:14-17` and the `reject` override
- Modify: `app/src/androidTest/java/com/onemind/app/EventReminderSchedulerTest.kt` (append tests)
- Modify: `app/src/androidTest/java/com/onemind/app/EventStatusTransitionTest.kt` (setup + one test)
- Modify: `app/src/androidTest/java/com/onemind/app/EventDetectionToReminderTest.kt:121`

**Interfaces:**
- Consumes: `EventRepositoryImpl.reject` (Task 2), `EventReminderScheduler.uniqueWorkName` (existing).
- Produces:
  - `fun EventReminderScheduler.cancelForEvent(eventId: Long)`
  - `EventRepositoryImpl` constructor becomes `(dao: EventDao, reminders: EventReminderScheduler)`

**Why the deleted method comes back.** `cancelForEvent` was removed in #33 for a reason
that no longer applies: it was called only from the delete path, where the Memory's event
rows have already cascaded away, so a lookup-based cancel was a no-op that looked like it
worked. On reject the row is still there. The memory-scoped `cancelForMemory` stays
tag-based for exactly the old reason, and its KDoc keeps saying so.

**Constructor change → `assembleDebug` is mandatory.** There is no DI cycle:
`EventReminderScheduler` depends on `Context` and `EventDao` only, and
`MemoryRepositoryImpl` already injects it.

- [ ] **Step 1: File Issue C**

```bash
python3 - <<'PY'
import json, re, urllib.request
pat = re.search(r'^GITHUB_PAT=(.*)$',
                open('/home/imyuvi/projects/codingagents/.env').read(), re.M).group(1).strip()
body = {
  "title": "Rejecting an event leaves its reminders enqueued",
  "body": """Rejecting an event sets its status to `REJECTED`, which is enough to stop it
earning *new* reminders — `getUnscheduledReminders` filters on `UPCOMING`. It does
nothing about the reminders already sitting in WorkManager. Those still fire, and the
user is notified about something they explicitly declined, days after declining it.

**Fix.** Reintroduce `EventReminderScheduler.cancelForEvent(eventId)`, cancelling by
unique work name across every `ReminderLead`, and call it from `EventRepositoryImpl.reject`.

`cancelForEvent` was deleted in #33 for a reason that no longer applies. It was called
only from the delete path, where the Memory's event rows have already cascaded away —
making a lookup-based cancel a no-op that looked like it worked. On reject the row is
still there. `cancelForMemory` stays tag-based for exactly the old reason and is not
touched.

Enumerating `ReminderLead` rather than adding a per-event tag keeps `uniqueWorkName` as
the single source of naming.

Called from the repository rather than the ViewModel, on the same reasoning as #33: the
repository is the one seam every caller passes through, and `MemoryRepositoryImpl`
already injects the scheduler for exactly this. It is a constructor change, so
`assembleDebug` has to pass before it lands."""
}
req = urllib.request.Request(
    "https://api.github.com/repos/Yuvraj-ai/oneMind/issues",
    data=json.dumps(body).encode(),
    headers={"Authorization": f"Bearer {pat}", "Accept": "application/vnd.github+json",
             "Content-Type": "application/json"})
print("issue", json.load(urllib.request.urlopen(req))["number"])
PY
```

Note the number; call it `C`.

- [ ] **Step 2: Write the failing scheduler tests**

Append inside `EventReminderSchedulerTest`, after the `cancelForMemory` tests:

```kotlin
    @Test
    fun cancelForEvent_cancelsBothLeadsOfThatEvent() = runTest {
        val eventId = insertEvent(Duration.ofDays(10))
        scheduler.scheduleAll()

        scheduler.cancelForEvent(eventId)

        // `single()` rather than a predicate over the list, for the same reason as the
        // cancelForMemory tests: an empty list would make any "all cancelled"
        // assertion vacuously true.
        assertEquals(
            WorkInfo.State.CANCELLED,
            jobsFor(eventId, ReminderLead.TWO_DAYS).single().state
        )
        assertEquals(
            WorkInfo.State.CANCELLED,
            jobsFor(eventId, ReminderLead.TWO_HOURS).single().state
        )
    }

    @Test
    fun cancelForEvent_leavesTheOtherEventsOfTheSameMemoryAlone() = runTest {
        // One screenshot can mention two dates, so one Memory can own several events.
        // Rejecting one of them must not silence the other.
        val rejected = insertEvent(Duration.ofDays(10))
        val kept = insertEvent(Duration.ofDays(20))
        scheduler.scheduleAll()

        scheduler.cancelForEvent(rejected)

        assertEquals(
            WorkInfo.State.CANCELLED,
            jobsFor(rejected, ReminderLead.TWO_HOURS).single().state
        )
        assertEquals(
            WorkInfo.State.ENQUEUED,
            jobsFor(kept, ReminderLead.TWO_HOURS).single().state
        )
    }
```

- [ ] **Step 3: Run them and watch them fail**

```bash
JAVA_HOME=/home/imyuvi/.local/jdks/jdk-17.0.20.1+1 ./gradlew connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.onemind.app.EventReminderSchedulerTest
```

Expected: compilation failure, `Unresolved reference: cancelForEvent`.

- [ ] **Step 4: Reintroduce `cancelForEvent`**

In `app/src/main/java/com/onemind/app/data/events/EventReminderScheduler.kt`, replace
the last paragraph of `cancelForMemory`'s KDoc:

```kotlin
     * Its predecessor, `cancelForEvent`, took an event id, was never called from
     * anywhere, and could not have been called correctly from the one place that
     * needed it. This is what v0.1.2 meant to have.
     */
    fun cancelForMemory(memoryId: Long) {
        WorkManager.getInstance(context).cancelAllWorkByTag(memoryTag(memoryId))
    }
```

with:

```kotlin
     * Tag-based *here specifically*, and [cancelForEvent] is not: that one runs while
     * the row is still present, this one runs after it is gone.
     */
    fun cancelForMemory(memoryId: Long) {
        WorkManager.getInstance(context).cancelAllWorkByTag(memoryTag(memoryId))
    }

    /**
     * Drop the reminders belonging to one event, for when the user rejects it.
     *
     * Rejecting sets a status `getUnscheduledReminders` filters out, so the event earns
     * no *new* reminders — but the ones already enqueued would still fire, and the user
     * would be notified days later about something they had just declined.
     *
     * This existed in v0.1.2, was deleted in #33, and comes back because the reason it
     * went away does not apply here. It was called only from the delete path, where the
     * Memory's event rows have already cascaded away, making a lookup-based cancel a
     * no-op that looked like it worked. On reject the row is still there.
     *
     * Enumerating [ReminderLead] rather than cancelling a per-event tag keeps
     * [uniqueWorkName] the single source of naming; a lead that earned no job simply
     * cancels a name nothing was enqueued under, which WorkManager treats as a no-op.
     */
    fun cancelForEvent(eventId: Long) {
        val workManager = WorkManager.getInstance(context)
        ReminderLead.entries.forEach { lead ->
            workManager.cancelUniqueWork(uniqueWorkName(eventId, lead))
        }
    }
```

- [ ] **Step 5: Run the scheduler tests and watch them pass**

```bash
JAVA_HOME=/home/imyuvi/.local/jdks/jdk-17.0.20.1+1 ./gradlew connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.onemind.app.EventReminderSchedulerTest
```

Expected: all pass, including the two new ones.

- [ ] **Step 6: Write the failing repository test**

`EventStatusTransitionTest` now needs a real scheduler over test WorkManager, because
`EventRepositoryImpl` is about to require one.

In `app/src/androidTest/java/com/onemind/app/EventStatusTransitionTest.kt`, replace the
`setup` method with:

```kotlin
    private lateinit var scheduler: EventReminderScheduler
    private lateinit var workManager: WorkManager

    @Before
    fun setup() = runTest {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()

        database = Room.inMemoryDatabaseBuilder(context, OneMindDatabase::class.java)
            .allowMainThreadQueries().build()
        dao = database.eventDao()
        memoryDao = database.memoryDao()

        // Far-future events only, so every reminder keeps a non-zero initial delay and
        // stays ENQUEUED — the same reason EventReminderSchedulerTest does this. A
        // zero delay would have SynchronousExecutor try to construct an @HiltWorker
        // that has no factory here.
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            Configuration.Builder().setExecutor(SynchronousExecutor()).build()
        )
        workManager = WorkManager.getInstance(context)
        scheduler = EventReminderScheduler(context, dao)

        memoryId = newMemory()
    }

    /** The production wiring, constructed by hand — the app has no Hilt test runner. */
    private fun repository() = EventRepositoryImpl(dao, scheduler)
```

Replace every `val repository = EventRepositoryImpl(dao)` in the existing tests with
`val repository = repository()`.

Add these imports:

```kotlin
import androidx.work.Configuration
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.onemind.app.data.events.EventReminderScheduler
import com.onemind.app.domain.events.ReminderLead
```

Then append this test:

```kotlin
    @Test
    fun rejectingCancelsTheRemindersAlreadyEnqueued() = runTest {
        val repository = repository()
        val id = insertEvent(ahead = Duration.ofDays(10))
        scheduler.scheduleAll()

        repository.reject(id)

        // The status alone only stops *new* reminders. Without the cancel, these fire
        // days later about something the user just declined.
        listOf(ReminderLead.TWO_DAYS, ReminderLead.TWO_HOURS).forEach { lead ->
            assertEquals(
                "lead $lead",
                WorkInfo.State.CANCELLED,
                workManager.getWorkInfosForUniqueWork(
                    EventReminderScheduler.uniqueWorkName(id, lead)
                ).get().single().state
            )
        }
    }
```

- [ ] **Step 7: Run it and watch it fail**

```bash
JAVA_HOME=/home/imyuvi/.local/jdks/jdk-17.0.20.1+1 ./gradlew connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.onemind.app.EventStatusTransitionTest
```

Expected: compilation failure — `EventRepositoryImpl` takes one argument, not two.

- [ ] **Step 8: Wire the scheduler into the repository**

In `app/src/main/java/com/onemind/app/data/repository/EventRepositoryImpl.kt`, replace:

```kotlin
@Singleton
class EventRepositoryImpl @Inject constructor(
    private val dao: EventDao
) : EventRepository {
```

with:

```kotlin
@Singleton
class EventRepositoryImpl @Inject constructor(
    private val dao: EventDao,
    private val reminders: EventReminderScheduler
) : EventRepository {
```

and replace:

```kotlin
    override suspend fun reject(eventId: Long) =
        dao.updateStatus(eventId, EventStatus.REJECTED)
```

with:

```kotlin
    override suspend fun reject(eventId: Long) {
        dao.updateStatus(eventId, EventStatus.REJECTED)
        // The status alone only stops the event earning *new* reminders; the ones
        // already in WorkManager would still fire. Cancelled here rather than in the
        // ViewModel for the reason #33 established: the repository is the one seam
        // every caller passes through.
        reminders.cancelForEvent(eventId)
    }
```

Add the import:

```kotlin
import com.onemind.app.data.events.EventReminderScheduler
```

- [ ] **Step 9: Fix the other hand-wired construction**

In `app/src/androidTest/java/com/onemind/app/EventDetectionToReminderTest.kt:121`,
replace:

```kotlin
            EventRepositoryImpl(database.eventDao()),
```

with:

```kotlin
            EventRepositoryImpl(database.eventDao(), scheduler),
```

`scheduler` is already in scope, assigned at `:100`.

- [ ] **Step 10: Run everything**

```bash
JAVA_HOME=/home/imyuvi/.local/jdks/jdk-17.0.20.1+1 ./gradlew assembleDebug testDebugUnitTest
JAVA_HOME=/home/imyuvi/.local/jdks/jdk-17.0.20.1+1 ./gradlew connectedDebugAndroidTest
git status --short app/schemas/
```

Expected: BUILD SUCCESSFUL on both Gradle invocations, no output from `git status`.
`assembleDebug` is what proves Hilt can still build the graph after the constructor
change; a failure here naming `EventReminderScheduler` means a cycle, and the fix is to
move the cancel behind an interface rather than to relax the binding.

- [ ] **Step 11: Commit and close Issue C**

```bash
git add app/src/main/java/com/onemind/app/data/events/EventReminderScheduler.kt \
        app/src/main/java/com/onemind/app/data/repository/EventRepositoryImpl.kt \
        app/src/androidTest/java/com/onemind/app/EventReminderSchedulerTest.kt \
        app/src/androidTest/java/com/onemind/app/EventStatusTransitionTest.kt \
        app/src/androidTest/java/com/onemind/app/EventDetectionToReminderTest.kt

git commit -F - <<'MSG'
fix(events): cancel an event's reminders when the user rejects it (#C)

Rejecting set a status getUnscheduledReminders filters out, which stops the
event earning new reminders and does nothing about the ones already enqueued.
Those still fired, notifying the user days later about something they had
explicitly declined.

cancelForEvent comes back. It was deleted in #33 because its only caller was the
delete path, where the Memory's event rows have already cascaded away — a
lookup-based cancel that was a no-op dressed as a fix. On reject the row is
still there, which is exactly the case it was always meant for. cancelForMemory
stays tag-based for the old reason and is untouched.

Cancelling by unique work name across every ReminderLead keeps uniqueWorkName as
the single source of naming; a lead that earned no job cancels a name nothing
was enqueued under, which WorkManager treats as a no-op.

Called from EventRepositoryImpl rather than the ViewModel, on #33's reasoning:
the repository is the one seam every caller passes through. That is a
constructor change, so assembleDebug was run to prove the Hilt graph still
builds.
MSG
```

Then close, with `NUMBER` set to `C` and this comment body:

```
Fixed. `EventReminderScheduler.cancelForEvent(eventId)` is back, cancelling by unique
work name across every `ReminderLead`, and `EventRepositoryImpl.reject` calls it.

The reason #33 deleted it does not apply here: it was a no-op on the delete path because
the rows had already cascaded away, but on reject the row is still present. That is the
case it was written for in the first place. `cancelForMemory` stays tag-based and is
unchanged.

Two instrumented tests on the scheduler (both leads cancelled; a sibling event of the
same Memory left ENQUEUED) plus one at the repository level that rejects a real event and
asserts its jobs are CANCELLED.

The constructor change was verified with `assembleDebug` before this landed, and the full
instrumented suite is green on the `onemind_test` AVD.

*Investigated and fixed by an AI agent (Claude), reviewed against the design spec.*
```

---

## Done when

- [ ] Three issues filed, implemented, and closed with AI-attributed comments.
- [ ] Three commits on `my-extra-work`, each with `(#N)` in the subject and no
      `Co-Authored-By` or generated-by trailer.
- [ ] `assembleDebug`, `testDebugUnitTest`, and the full `connectedDebugAndroidTest`
      suite green on the `onemind_test` AVD — the 86 pre-existing instrumented tests
      plus 18 new ones, and 6 new JVM tests.
- [ ] `git status --short app/schemas/` prints nothing. The database is still at
      version 5.
- [ ] Manual check on the device: open Events, tap add-to-calendar on an upcoming
      event, confirm the calendar app's add screen opens and the card then reads
      "In calendar"; reject another and confirm it moves to "Expired & rejected" with
      an Undo that brings it back.
- [ ] Nothing released. No version bump, tag, APK, or GitHub release.

## Known limitation, recorded deliberately

An event marked `IN_CALENDAR` before `scheduleAll()` has ever run for it will never earn
oneMind reminders, because `getUnscheduledReminders` filters on `UPCOMING` alone. The
window is narrow — app start and every completed pipeline run both call `scheduleAll()`,
so by the time the Events screen is on screen the event almost always has its reminders —
and in that case the user has just put the event in their own calendar, which has its own
reminders. Widening the filter would close it; the design spec chose not to, on the
grounds that an event is only *owed* reminders while it is still plain upcoming. Left as
specified rather than changed here.

