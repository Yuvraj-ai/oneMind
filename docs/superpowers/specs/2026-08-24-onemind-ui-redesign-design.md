# oneMind redesign: shade-safe capture, actionable events, M3 Expressive UI

**Date:** 2026-08-24
**Status:** approved
**Branch:** `my-extra-work`
**Issues:** one per defect / feature slice, filed before implementation starts
**Ships in:** unreleased — no version bump, tag, or APK without an explicit ask
**Reference:** `../../../../design-reference/` (workspace root, outside this repo) —
`DESIGN-GUIDE.md` is the guide, `styles.css` is the token sheet, each `*.html` is a screen

---

## Why

Three unrelated things, done in this order because the first two are broken behaviour
and the third is cosmetic:

1. **The QS-tile screenshot captures the notification shade**, not the app underneath.
   Two defects in the same call path, one of which also mislabels every capture's
   source app.
2. **The Events tab can be looked at but not acted on.** The design reference gives
   every event four states and two actions; the shipped screen has one action and no
   way to dismiss anything.
3. **The UI does not look like the design reference at all.** No ember palette, no
   expressive motion, no Timeline or Search destination, `dynamicColor = true`.

Scope boundary, stated by the user and binding on the whole of phase 3: *"don't
change the backend code logic for the ui redesign."* Phases 1 and 2 are explicitly
exempt — they **are** the requested behaviour changes. Phase 3 touches `ui/` and
`res/font` only; bundled fonts need no new Gradle dependency.

---

## Phase 1 — the screenshot captures the shade

### Root cause

`ScreenCaptureTileService.onClick()` calls `startService(ACTION_TAKE_SCREENSHOT)` and
returns. `ScreenCaptureAccessibilityService.onStartCommand` then posts
`takeScreenshotNow()` behind a fixed 500 ms delay. The KDoc at
`ScreenCaptureAccessibilityService.kt:104` explains the delay like this:

> The shade collapses automatically after onClick returns, but the animation takes
> ~300-500ms.

**It does not collapse.** `TileService` has no such contract — only
`startActivityAndCollapse` collapses, and that path is used solely for the
accessibility-settings redirect. So the 500 ms is spent waiting next to a shade that
is still fully open, and `takeScreenshot()` faithfully captures the display it is
given.

### The second defect, one line away

Pulling the shade down emits `TYPE_WINDOW_STATE_CHANGED` with `packageName =
"com.android.systemui"`. `onAccessibilityEvent` (`:95-101`) rejects only *our own*
package:

```kotlin
if (pkg != packageName) { foregroundPackage = pkg }
```

so by capture time `foregroundPackage` is `com.android.systemui`. Every tile-initiated
screenshot is persisted with the wrong `sourcePackage`, and the feed's source filter
gains a phantom "System UI" entry. This is the same bug wearing a different hat and is
fixed in the same commit.

### Design

Dismissing the shade is a privileged action; an `AccessibilityService` is the only
component in this app that can perform it. So the fix stays where the capture already
lives.

**`performGlobalAction(GLOBAL_ACTION_DISMISS_NOTIFICATION_SHADE)`** — API 31
(`Build.VERSION_CODES.S`). `minSdk` is 30 and stays 30: raising the floor drops
devices, which is a product decision nobody asked for. On API 30 the call is skipped
and behaviour falls back to today's blind delay. Per the user: *"use API 31, if API 30
is not possible no problem."* The API-30 path is therefore explicitly **not fixed**,
and says so in a comment rather than pretending otherwise.

**Do not replace one guess with a shorter guess.** The shade-open event proves that
SystemUI window transitions are delivered to this service, so the collapse is
observable. Extract the decision into a pure, unit-testable tracker:

`capture/ShadeTracker.kt` — no Android imports beyond the event's package name, which
arrives as a `String`:

```kotlin
class ShadeTracker {
    fun onWindowStateChanged(pkg: String)   // records what is in front
    val isShadeInFront: Boolean             // last seen was SystemUI
    val lastAppPackage: String?             // last seen that was neither SystemUI nor us
}
```

`onAccessibilityEvent` feeds it; `foregroundPackage` becomes `lastAppPackage`, so
SystemUI can never be recorded as the source app.

Capture sequence on `ACTION_TAKE_SCREENSHOT`:

1. If `SDK_INT >= S` and `tracker.isShadeInFront`, call
   `performGlobalAction(GLOBAL_ACTION_DISMISS_NOTIFICATION_SHADE)`.
2. Arm a one-shot continuation that fires on **whichever comes first**: the next
   window-state change to a non-SystemUI package, or a `SHADE_COLLAPSE_TIMEOUT_MS`
   deadline (the existing 500 ms, demoted from mechanism to backstop).
3. Capture once. The continuation is idempotent — a `takeScreenshot()` fired twice
   would persist two Memories for one tap.

Both paths run on the main looper via the existing `Handler`, so "whichever comes
first" needs no locking beyond the `@Volatile` already there.

### What is not testable, said plainly

`ShadeTracker` and the one-shot latch are unit-testable on the JVM. The
`performGlobalAction` call and the resulting collapse are not — asserting them needs
a real shade on a real display. That step is verified **manually on device**:
pull the shade, tap the tile, confirm the saved Memory shows the app underneath and
carries that app's package, not `com.android.systemui`. The spec records this as a
manual step rather than claiming coverage it does not have.

---

## Phase 2 — Events: reject and add to calendar

### No migration is required

An earlier reading of this called for schema v5 → v6. That was wrong.
`detected_events.status` is a `TEXT` column and `EventStatus` is persisted by name;
adding `REJECTED` and `IN_CALENDAR` changes no column, index, or Room schema hash.
**The database stays at version 5 and `app/schemas/…/5.json` is unchanged.**

What *does* have to change is the six SQL string literals — across five of `EventDao`'s
queries — that hardcode `'UPCOMING'` and `'EXPIRED'`. Those literals are now an
incomplete enumeration, and they are the only real hazard in this phase: nothing fails
to compile, the queries simply stop meaning what their names say.

### State model

```
UPCOMING ──[add to calendar]──▶ IN_CALENDAR
   │                                  │
   ├──[reject]──▶ REJECTED            │
   │                 │                │
   │            [undo reject]         │
   │                 │                │
   ◀─────────────────┘                │
   │                                  │
   └──[time passes]──▶ EXPIRED ◀──────┘
```

Presented as two lists, matching `events.html`:

| List | Statuses | Order |
|---|---|---|
| **Upcoming** | `UPCOMING`, `IN_CALENDAR` | `eventTime` ascending |
| **Expired & rejected** | `EXPIRED`, `REJECTED` | `eventTime` descending |

`IN_CALENDAR` stays in the upcoming list — adding to a calendar is an export, not a
dismissal, so the event remains something coming up.

### Three interactions this feature breaks if left alone

**1. `expireOverdue` only moves `UPCOMING`.** An `IN_CALENDAR` event whose time passes
would sit at the top of the upcoming list forever, showing a date in the past. The
`UPDATE` widens to `status IN ('UPCOMING', 'IN_CALENDAR')`.

`REJECTED` is deliberately excluded. It already renders in the past list, and
expiring it would silently strip the undo affordance. A rejected event whose time
passes and is *then* undone returns to `UPCOMING` with a past time, and the next
`expireOverdue` moves it to `EXPIRED` — self-correcting, not a special case.

**2. Reprocessing erases the user's decision.** `EventRepositoryImpl.replaceEventsForMemory`
deletes every row for the Memory and re-inserts. Its KDoc defends this correctly for
*content*: reprocessing re-derives dates from scratch and appending would leave events
behind describing text that no longer exists. But status is not derived from content —
it is the user's judgement about it. As written, a retry or an edit resurrects a
rejected event as `UPCOMING` and re-arms its reminders.

Fix inside `EventRepositoryImpl`, where the storage detail already lives: read the
existing rows before deleting, then for each re-derived event whose `eventTime` matches
one of them, carry forward `status` **and** `remindersScheduledAt`. Matching on
`eventTime` rather than `id` is the point — the ids are new, the instant is the
identity. `EventDetectionStage` and the rest of `domain/` learn nothing about this.

Events with no time match are new, and arrive `UPCOMING` with null reminders, exactly
as today. An empty replacement list still clears the Memory.

**3. Rejecting does not stop reminders already enqueued.** `getUnscheduledReminders`
filters `status = 'UPCOMING'`, so a rejected event gets no *new* reminders — but the
ones already in WorkManager still fire, and the user gets notified about a thing they
just declined.

`cancelForEvent(eventId)` was deleted in #33 for a good reason that no longer applies:
it was called only from the delete path, where the row is already gone, making a
lookup-based cancel a no-op that looked like it worked. On reject the row is still
there. It comes back, cancelling by unique work name:

```kotlin
fun cancelForEvent(eventId: Long) {
    val wm = WorkManager.getInstance(context)
    ReminderLead.entries.forEach { wm.cancelUniqueWork(uniqueWorkName(eventId, it)) }
}
```

Enumerating `ReminderLead` rather than tagging per event keeps the existing
`uniqueWorkName` contract as the single source of naming. The old KDoc's warning is
preserved as a note on *why* the memory-scoped variant must stay tag-based.

**`IN_CALENDAR` keeps its oneMind reminders.** `ACTION_INSERT` opens the calendar app's
own add-event screen, which the user may cancel; oneMind never learns the outcome.
Cancelling reminders on export would therefore sometimes remove the only reminder the
user has, based on an action that may not have completed. A possible duplicate
notification is the lesser failure.

### Surface changes

`EventStatus` gains `REJECTED, IN_CALENDAR`.

`EventDao`:
- `observeUpcoming()` → `status IN ('UPCOMING','IN_CALENDAR')`, `eventTime ASC`
- `observeExpired()` → `status IN ('EXPIRED','REJECTED')`, `eventTime DESC`
- `expireOverdue(now)` → widened as above
- `countUpcoming()` → widened to match `observeUpcoming`, so the hero's "{n} upcoming"
  counts the same rows the list shows
- `getUnscheduledReminders()` → left on `UPCOMING` alone, deliberately: an event is
  only owed reminders while it is still plain upcoming
- new `updateStatus(eventId, status)` — the only write the three verbs need. No
  `getById`: `undoReject` returns an event to `UPCOMING` unconditionally and lets
  `expireOverdue` correct a past time on the next pass, so nothing reads a row back.

`EventRepository` (interface, `domain/`) gains three verbs in the user's language, not
the storage's:
- `suspend fun reject(eventId: Long)`
- `suspend fun undoReject(eventId: Long)`
- `suspend fun markAddedToCalendar(eventId: Long)`

`EventsViewModel` exposes those, keeps `exportToCalendar` returning the `Intent`
(the screen still launches it, so the ViewModel stays free of `Context`), and calls
`markAddedToCalendar` after the intent is dispatched. Reminder cancellation on reject is
invoked from `EventRepositoryImpl`, not the ViewModel — same reasoning as #33, the
repository is the one seam every caller passes through, and `MemoryRepositoryImpl:161`
already injects `EventReminderScheduler` for exactly this. It is a constructor change, so
`assembleDebug` is mandatory before the commit lands.

### Mock fields that turn out to have backing data

`events.html` shows a `MapPin` location line and up to three category chips per event
card. `DetectedEvent` has neither field, which earlier looked like a cut. It is not:

- **Categories** already exist — `memory_categories` joins `categories` to the Memory,
  and the event carries `memoryId`.
- **Location** already exists — `EntityType.PLACE` is one of the seven extracted entity
  types, so a Memory's `PLACE` entities are available on the same key.

Both are read-only and require no new extraction and no pipeline change. They differ in
what it costs to read them:

- **Categories are free.** `MemoryRepository.getMemoriesByIds(ids)` already batches them
  into `Memory.derived.categories`.
- **Entities are not.** That same method deliberately hydrates only summaries and
  categories — its comment explains that a query per row would cost "on every keystroke
  rather than every scroll", because search shares the path. `getMemoryById` does load
  entities, but calling it once per event is exactly the per-row cost that comment
  rejects, and widening `getMemoriesByIds` would push the cost onto search.

So one narrow addition: `MemoryRepository.getEntitiesByMemoryIds(ids): Map<Long,
List<ExtractedEntity>>`, backed by a new batched `DerivedDataDao` query alongside the
existing `getSummaries(memoryIds)`. Domain-typed and unfiltered — which entity type
matters is the caller's policy, not the data layer's.

`EventsViewModel` then assembles a UI-layer `EventCardUi` per event:

```kotlin
val location = entities[event.memoryId]
    ?.firstOrNull { it.entityType == EntityType.PLACE }?.name
val chips = memory.derived.categories.take(3)
```

`DetectedEvent` is untouched. Where a Memory has no `PLACE` entity the pin is simply
absent, exactly as the mock's optional treatment implies.

`EventsUiState`'s two lists change type from `List<DetectedEvent>` to
`List<EventCardUi>`; each item still carries its `DetectedEvent` so the actions have an
id to act on.

---

## Phase 3 — M3 Expressive redesign

Presentation only. No file under `domain/`, `data/`, or `capture/` changes in this
phase.

### Theme

`ui/theme/` grows from one file to five:

| File | Contents |
|---|---|
| `Colors.kt` | Every `styles.css` `:root` token as a `Color`, baked from oklch to sRGB once, with the source oklch in a trailing comment |
| `Type.kt` | Outfit + Figtree families, `Typography` per DESIGN-GUIDE §5.2 |
| `Shapes.kt` | `Shapes(extraLarge=32, large=24, medium=16)`, the three asymmetric card shapes verbatim from §5.5, and `CookieShape` |
| `Brushes.kt` | `emberGradient` (135° linear, 3 stops) and `haloGradient` (radial, 120%×90% at 50% 0%) |
| `OneMindTheme.kt` | Rewritten |

The baked constants (computed from the oklch values, not eyeballed):

```
background #140A08   foreground #F8EBE7   surface1 #1F1310   surface2 #2A1B18
surface3   #352421   surface4   #44302B   card     #271916   popover  #2A1B17
primary    #EF8D67   onPrimary  #290C06   primaryContainer #593124  onPC #FFE1D1
accent     #F7CBC7   onAccent   #2A130F   mutedForeground  #BCA9A3
border     #433431   outline    #62514C   destructive #ED5350  onDestructive #FFF6F3
success    #57BC80   warning    #E9B452
ember      #833F29 → #442321 → #2C1A16    halo #5C2F1F @ 85% → transparent
```

Two changes to `OneMindTheme`'s defaults, both required for the palette to survive:

- **`dynamicColor` default `true` → `false`.** With it left on, Material You replaces
  the ember scheme on every Android 12+ device — which is to say, all of them, since
  `minSdk` is 30 and virtually every target is 12+. DESIGN-GUIDE §5.1 is explicit that
  the brand palette wins. The parameter stays, so a future user-facing toggle has
  somewhere to land.
- **`darkTheme` default `isSystemInDarkTheme()` → `true`.** The reference is
  dark-first. A light scheme is still provided, but as the option rather than the
  default.

`MaterialTheme` is called through the five-argument overload with
`motionScheme = MotionScheme.expressive()`. Verified present in material3 1.4.0
(Compose BOM 2026.06.01) by inspecting the resolved AAR.

### Fonts: bundled, not the Google Fonts provider

Outfit (400/500/600/700) and Figtree (400/500/600) ship as TTFs in `res/font` with a
`FontFamily` per typeface. The downloadable-fonts provider was rejected: it needs a
network fetch and a provider app present at runtime, which contradicts oneMind's
offline, nothing-leaves-the-device stance, and it reflows text on cold start while the
request is in flight. Cost is roughly 300 KB of APK, paid once.

Seven files (four Outfit weights, three Figtree) rather than variable fonts, because
`res/font` weight selection across the `minSdk` 30 range is more predictable with
static faces.

### Screens

`440.dp` width constraint is a single shared modifier applied at each screen's root,
not repeated per component.

| Route | Screen | State |
|---|---|---|
| `feed` | `FeedScreen` | Rewritten — hero, search pill (navigates), segmented nav, filter chips, 2-column bento grid, FAB |
| `timeline` | `TimelineScreen` | **New** — same hero pattern, day sections on a rail with dots, all cards `md` |
| `events` | `EventsScreen` | Rewritten — hero, two lists, four card states, per-state actions |
| `search` | `SearchScreen` | **New** — large bar, empty state with suggestion pills, result count + grid |
| `composer` | `ComposerScreen` | Restyled — 52vh composer, bottom toolbar, draft pill |
| `memory/{id}` | `MemoryDetailScreen` | Restyled — summary block, section cards, image placeholder |
| `onboarding` | `OnboardingScreen` | Restyled — model pill rows with size badges, sticky CTA |
| `settings` | `SettingsScreen` | Restyled — labelled sections, fields, 56×32 switch, segmented Test/Use cloud, storage card |

`NavRoutes` gains `TIMELINE` and `SEARCH`. Feed, Timeline and Events form a connected
segmented group and navigate between each other with `launchSingleTop` and a
`popUpTo` on the group's start, so the three do not stack. `EventsScreen` keeps its
`Scaffold` + back affordance from #37 — the segmented group is in addition to it, not a
replacement.

### Shared components — `ui/components/`

`PressMorph.kt` (a `Modifier` factory: scale 0.96 + corner morph to 40 dp on press,
driven by the expressive spring), `WavyProgress.kt`, `HeroHeader.kt`,
`SectionNav.kt`, `CategoryChip.kt`, `StateChip.kt`, `StatusPill.kt`,
`CookieThumb.kt`, `StaggeredEntrance.kt`, `PhoneFrame.kt`.

Two guide items cannot be built as written. Verified by `javap` against the resolved
material3 1.4.0 AAR: `LinearWavyProgressIndicator`, `ButtonGroup`, `LoadingIndicator`
and `SplitButton` ship **token classes only, no public composable**.

- **Wavy progress** → custom `Canvas` on an `infiniteTransition`, 24 dp wavelength,
  1.6 s loop. DESIGN-GUIDE §3.3 sanctions this fallback explicitly.
- **Connected button group** → `SingleChoiceSegmentedButtonRow` + `SegmentedButton`,
  which is what §5.4 already maps it to.

`MotionScheme.expressive()`, the five-argument `MaterialTheme` overload,
`SegmentedButton` and `LargeFloatingActionButton` are all present and used directly.

### Bento sizing

`index.html` sizes feed cards from a `size` field on its mock `Memory`. The real
`Memory` has no such field and is not getting one — sizing is a layout concern, so it
is derived in the UI layer: first Memory with an image → `lg`, then alternating
`md`/`sm`. Timeline and Events force `md`, as the reference does.

### One presentation-layer restructure

`FeedViewModel` currently owns search: the debounced query flow, `SearchOrchestrator`,
`searchResults`, `searchTerms`, `isSearching`, plus an unused private
`searchMemories`. The reference puts search on its own screen behind a bar that is a
navigation affordance on the feed.

That search state moves to a new `SearchViewModel` over the same `SearchOrchestrator`,
unchanged. Behaviour is identical, relocated; the dead private method goes with it, and
`FeedViewModel` is left with the feed. `SearchOrchestrator`, `FtsQuery` and everything
below them are untouched.

---

## Testing

| Phase | JVM unit | Instrumented | Manual |
|---|---|---|---|
| 1 | `ShadeTracker`: SystemUI never becomes `lastAppPackage`; `isShadeInFront` transitions; latch fires once on event, once on timeout, never twice | — | Shade open → tile tap → correct app captured, correct `sourcePackage` |
| 2 | Status transition verbs; `EventCardUi` assembly | `expireOverdue` moves `IN_CALENDAR` and leaves `REJECTED`; `replaceEventsForMemory` preserves status + `remindersScheduledAt` on an `eventTime` match and not otherwise; reject cancels enqueued reminders | Add-to-calendar intent opens the calendar app |
| 3 | — | Compose: segmented nav selection and destination; each of the four event states renders its documented actions | Visual comparison against each `*.html` |

`assembleDebug` after every phase — it is the only check that validates the Hilt graph,
and phase 2 adds a constructor dependency. Full instrumented suite on the `onemind_test`
AVD before phase 3 is called done; the existing 86 tests must stay green.

The reprocessing-preservation test is the one that matters most: it is the defect this
feature would otherwise introduce, and it is invisible until a user retries a Memory.

---

## Commits and issues

One issue per slice, filed before its implementation, `(#N)` in the commit subject,
one commit each. Attribution is the user alone — no `Co-Authored-By` or generated-by
trailer.

| Slice | Phase |
|---|---|
| Tile screenshot captures the notification shade; SystemUI recorded as source app | 1 |
| Events cannot be rejected or marked as added to calendar | 2 |
| Reprocessing a Memory resets a rejected event to upcoming | 2 |
| Rejecting an event leaves its reminders enqueued | 2 |
| Ember theme, expressive motion, bundled Outfit/Figtree | 3 |
| Shared expressive components | 3 |
| Feed redesign | 3 |
| Timeline destination | 3 |
| Search destination | 3 |
| Events redesign | 3 |
| Composer, memory detail, onboarding, settings restyle | 3 |

Nothing here is released. No version bump, tag, APK, or GitHub release without an
explicit ask.

---

## Open items deliberately left out

- **Events stay cloud-only.** `MetadataExtractionStage:41` returns early when
  `!textGenerator.isAvailable()`, so dates — and therefore events — are extracted only
  with a provider key configured. This is why previous attempts appeared to fail: the
  feature worked, but nothing ever reached it. The user has chosen to configure a key
  rather than add a local date parser. No parser is added.
- **`eventAllDay`** appears in the reference's mock data. `DetectedEvent` has no such
  field and gaining one is a pipeline change; every event renders with a time.
- **API 30 shade dismissal.** Not possible without raising `minSdk`; accepted.
- **Light theme fidelity.** A light scheme exists so the parameter is honest, but the
  reference is dark-only and the light values are derived, not designed.
