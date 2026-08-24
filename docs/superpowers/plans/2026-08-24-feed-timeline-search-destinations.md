# Feed, Timeline and Search Destinations Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Turn the feed's in-screen view toggle and inline search bar into three real destinations — Feed, Timeline and Search — and rebuild the feed as the reference's bento grid.

**Architecture:** `NavRoutes` gains `TIMELINE` and `SEARCH`. Feed, Timeline and Events become a connected segmented group that swaps rather than stacks. Title and snippet extraction moves out of `MemoryCard` into a pure `MemoryDisplay` so three screens can share it, and bento sizing becomes a pure function over the memory list. Search state moves out of `FeedViewModel` into a `SearchViewModel` over the same, untouched, `SearchOrchestrator`.

**Tech Stack:** Kotlin 2.1.0, Jetpack Compose (material3 1.4.0), Navigation Compose 2.9.0, Hilt 2.53.1, JUnit4, MockK, Compose UI test.

## Global Constraints

- **Branch: `my-extra-work`.** Never commit to `main`; never merge or fast-forward from `main`.
- **Commit attribution is the user alone.** No `Co-Authored-By`, no generated-by trailer.
- **No release.** No version bump, tag, APK, or GitHub release unless explicitly asked.
- **One commit per issue**, `(#N)` in the subject, filed before implementation, closed after with an AI-attributed comment.
- **GitHub access is curl/python, not `gh`.** Token from `/home/imyuvi/projects/codingagents/.env`, never echoed. **Never run `git remote -v`.**
- **Build invocation** from `/home/imyuvi/projects/codingagents/oneMind`: `JAVA_HOME=/home/imyuvi/.local/jdks/jdk-17.0.20.1+1 ./gradlew <task>`. `java` is not on `PATH`.
- **Presentation only.** No file under `domain/`, `data/` or `capture/` changes. `SearchOrchestrator` and `FtsQuery` live in `domain/search/` and are **not** touched — the search *state* relocates, the search itself does not change.
- **440 dp frame** via `PhoneFrame` at each screen root, once.
- **48 dp tap targets**, never shrunk.
- **Every commit leaves a working app.** The task order below exists for that reason: the feed cannot navigate to a route that has not been added yet, and search state cannot leave `FeedViewModel` before something else owns it.

## Prerequisite

`docs/superpowers/plans/2026-08-24-expressive-theme-foundation.md` must be complete. This
plan uses `PhoneFrame`, `HeroHeader`, `SectionNav`, `SectionDestination`, `CategoryChip`,
`StateChip`, `CookieThumb`, `StaggeredEntrance`, `rememberPressMorph`, `pressScale`,
`CardShapeLarge/Medium/Small`, `PillShape` and `EmberGradient`, all of which it creates.

## File Structure

| File | Responsibility | Change |
|---|---|---|
`ui/navigation/NavRoutes.kt` | Route constants | Modify — `TIMELINE`, `SEARCH` |
`ui/navigation/SectionNavigation.kt` | Destination → route, group nav policy | **Create** |
`ui/navigation/OneMindNavHost.kt` | Graph | Modify — two destinations, rewired callbacks |
`ui/feed/MemoryDisplay.kt` | Title and snippet from a Memory | **Create** |
`ui/feed/BentoSizing.kt` | Card size per grid position | **Create** |
`ui/feed/BentoCard.kt` | One memory as a bento tile | **Create** |
`ui/feed/TimelineScreen.kt` | Timeline destination | **Create** |
`ui/feed/FeedScreen.kt` | Feed destination | Rewritten |
`ui/feed/FeedViewModel.kt` | Feed state | Modify — search and view mode removed |
`ui/feed/FeedUiState.kt` | Feed state | Modify — same |
`ui/feed/MemoryCard.kt` | Old list card | **Delete** |
`ui/search/SearchViewModel.kt` | Search state | **Create** |
`ui/search/SearchScreen.kt` | Search destination | **Create** |
`ui/feed/SearchResultCard.kt` | Result card | Moved to `ui/search/`, restyled |
`app/src/test/java/com/onemind/app/BentoSizingTest.kt` | Sizing, JVM | **Create** |
`app/src/test/java/com/onemind/app/MemoryDisplayTest.kt` | Title/snippet, JVM | **Create** |
`app/src/androidTest/java/com/onemind/app/SectionNavigationTest.kt` | Group nav | **Create** |
`app/src/androidTest/java/com/onemind/app/SearchScreenTest.kt` | Search states | **Create** |

Three issues, three commits, in this order:

| Issue | Tasks | Subject | Why this order |
|---|---|---|---|
| F | 1–5 | Timeline destination | Adds the routes and the shared card infrastructure. The segmented group replaces the feed's view toggle, so Timeline is reachable the moment it exists. |
| G | 6–8 | Search destination | Search state leaves `FeedViewModel` and the feed's inline bar becomes a pill that navigates — both in one commit, so nothing is orphaned. |
| H | 9–10 | Feed redesign | Pure presentation, once both destinations exist to navigate to. |

---

## Task 1: Routes and the group's navigation policy

**Files:**
- Modify: `app/src/main/java/com/onemind/app/ui/navigation/NavRoutes.kt`
- Create: `app/src/main/java/com/onemind/app/ui/navigation/SectionNavigation.kt`

**Interfaces:**
- Consumes: `SectionDestination` (theme-foundation plan).
- Produces:
  - `NavRoutes.TIMELINE = "timeline"`, `NavRoutes.SEARCH = "search"`
  - `fun SectionDestination.route(): String`
  - `fun NavHostController.navigateToSection(destination: SectionDestination)`

- [ ] **Step 1: Add the routes**

In `app/src/main/java/com/onemind/app/ui/navigation/NavRoutes.kt`, replace:

```kotlin
    const val FEED = "feed"
    const val EVENTS = "events"
```

with:

```kotlin
    const val FEED = "feed"

    /**
     * Chronological view of the same Memories the feed shows.
     *
     * A destination rather than a mode on the feed. It was a `ViewMode` toggle inside
     * `FeedScreen`, which meant it had no route, could not be linked to, and lost its
     * selection on process death.
     */
    const val TIMELINE = "timeline"

    const val EVENTS = "events"

    /**
     * Unified retrieval, behind one bar.
     *
     * Also a destination rather than a mode, and for a stronger reason than Timeline:
     * search had its own debounced state living in `FeedViewModel`, so the feed's view
     * model was recreated with a search subsystem attached whether or not anyone
     * searched.
     */
    const val SEARCH = "search"
```

- [ ] **Step 2: Write `SectionNavigation.kt`**

```kotlin
package com.onemind.app.ui.navigation

import androidx.navigation.NavHostController
import com.onemind.app.ui.components.SectionDestination

/**
 * Which route a segmented-group destination points at.
 *
 * Kept here rather than on `SectionDestination` deliberately: that enum is a presentation
 * component and knows only its own labels, so it can be rendered — and tested — without
 * a navigation graph existing at all.
 */
fun SectionDestination.route(): String = when (this) {
    SectionDestination.FEED -> NavRoutes.FEED
    SectionDestination.TIMELINE -> NavRoutes.TIMELINE
    SectionDestination.EVENTS -> NavRoutes.EVENTS
}

/**
 * Move between the three peer destinations without stacking them.
 *
 * A plain `navigate` would push, so Feed → Timeline → Events → Feed would leave four
 * entries on the back stack and four presses of back to leave. `popUpTo(FEED)` makes the
 * feed the group's floor and `launchSingleTop` stops a destination being pushed onto
 * itself, so back always goes to the feed and then out — which is what a tab-like group
 * has to do to feel like one.
 *
 * `saveState` and `restoreState` keep each destination's scroll position across a swap.
 * Without them, returning to a feed the user had scrolled halfway down snaps it to the top,
 * which reads as the app having reloaded.
 */
fun NavHostController.navigateToSection(destination: SectionDestination) {
    navigate(destination.route()) {
        popUpTo(NavRoutes.FEED) {
            inclusive = false
            saveState = true
        }
        launchSingleTop = true
        restoreState = true
    }
}
```

- [ ] **Step 3: Confirm it compiles**

```bash
JAVA_HOME=/home/imyuvi/.local/jdks/jdk-17.0.20.1+1 ./gradlew assembleDebug
```

Expected: BUILD SUCCESSFUL.

---

## Task 2: `MemoryDisplay` — title and snippet, extracted

**Files:**
- Create: `app/src/main/java/com/onemind/app/ui/feed/MemoryDisplay.kt`
- Test: `app/src/test/java/com/onemind/app/MemoryDisplayTest.kt` (create)

**Interfaces:**
- Consumes: `Memory`, `DerivedData`, `StageStatus`.
- Produces:
  - `object MemoryDisplay` with `fun title(memory: Memory): String`, `fun snippet(memory: Memory): String`, `const val FALLBACK_TITLE = "Untitled memory"`, `const val TITLE_FALLBACK_CHARS = 60`

**Why extract.** `MemoryCard.getTextSnippet` is private and returns
`"$title — $summaryText"` as one string. The reference draws the title as a distinct
element — Outfit 17 sp, clamped to three lines — with the snippet elsewhere or not at all,
and three screens now need the same derivation. A private function returning a joined
string cannot serve any of that. Extracting also makes it JVM-testable, which it has never
been.

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/com/onemind/app/MemoryDisplayTest.kt`:

```kotlin
package com.onemind.app

import com.onemind.app.domain.model.ContentBlock
import com.onemind.app.domain.model.ContentType
import com.onemind.app.domain.model.DerivedData
import com.onemind.app.domain.model.Memory
import com.onemind.app.domain.model.MemorySummary
import com.onemind.app.domain.processing.StageStatus
import com.onemind.app.ui.feed.MemoryDisplay
import org.junit.Assert.*
import org.junit.Test

/**
 * What a card calls a Memory, and what it says about it.
 *
 * These were one private function inside `MemoryCard` that joined both into
 * `"title — summary"`. That works for a single line of body text and for nothing else:
 * the redesign draws the title as its own element in its own face, and three screens need
 * the same derivation. Pulling it out also made it testable, which it had never been.
 */
class MemoryDisplayTest {

    private fun memory(
        text: String? = null,
        summary: MemorySummary? = null
    ) = Memory(
        id = 1L,
        contentBlocks = if (text == null) emptyList() else listOf(
            ContentBlock(type = ContentType.TEXT, content = text)
        ),
        derived = if (summary == null) DerivedData.EMPTY else DerivedData(summary = summary)
    )

    private fun summary(
        title: String?,
        text: String,
        status: StageStatus = StageStatus.SUCCESS
    ) = MemorySummary(
        memoryId = 1L,
        summaryText = text,
        status = status,
        title = title
    )

    @Test
    fun theModelsTitleWinsWhenThereIsOne() {
        val m = memory(text = "some long body text", summary = summary("Dentist booking", "…"))

        assertEquals("Dentist booking", MemoryDisplay.title(m))
    }

    @Test
    fun aFailedSummarysTitleIsNotUsed() {
        // A stage that did not succeed may still have written a row. Its title describes
        // whatever it managed before failing, which is not something to put in 17 sp
        // Outfit at the top of a card.
        val m = memory(
            text = "Booked the dentist for Thursday",
            summary = summary("garbage", "", status = StageStatus.FAILED)
        )

        assertEquals("Booked the dentist for Thursday", MemoryDisplay.title(m))
    }

    @Test
    fun withNoTitleTheUsersOwnFirstLineIsTheTitle() {
        val m = memory(text = "Booked the dentist\nfor Thursday at 3", summary = null)

        // The first line, not the whole block: the rest is the snippet's job, and a
        // three-line title in a two-column grid pushes everything else off the card.
        assertEquals("Booked the dentist", MemoryDisplay.title(m))
    }

    @Test
    fun aVeryLongFirstLineIsTruncatedRatherThanClamped() {
        val long = "a".repeat(200)
        val m = memory(text = long)

        val title = MemoryDisplay.title(m)
        assertTrue("title was ${title.length} chars", title.length <= 61)
        assertTrue("truncation should be visible", title.endsWith("…"))
    }

    @Test
    fun aMemoryWithNothingReadableStillHasATitle() {
        val m = memory(text = null, summary = null)

        // An image-only Memory before OCR runs. A blank title would leave a card that
        // looks broken rather than one that looks new.
        assertEquals(MemoryDisplay.FALLBACK_TITLE, MemoryDisplay.title(m))
    }

    @Test
    fun blankTextIsTreatedAsNoText() {
        val m = memory(text = "   \n  ")

        assertEquals(MemoryDisplay.FALLBACK_TITLE, MemoryDisplay.title(m))
    }

    @Test
    fun theSnippetIsTheSummaryWhenThereIsOne() {
        val m = memory(text = "raw body", summary = summary("A title", "What this is about."))

        assertEquals("What this is about.", MemoryDisplay.snippet(m))
    }

    @Test
    fun theSnippetFallsBackToTheUsersText() {
        val m = memory(text = "raw body", summary = null)

        assertEquals("raw body", MemoryDisplay.snippet(m))
    }

    @Test
    fun theSnippetIsEmptyRatherThanRepeatingTheTitle() {
        // Title came from the user's only line of text, so there is nothing left to say.
        // Repeating it is what the old joined string effectively did.
        val m = memory(text = "Booked the dentist", summary = null)

        assertEquals("Booked the dentist", MemoryDisplay.title(m))
        assertEquals("", MemoryDisplay.snippet(m))
    }
}
```

- [ ] **Step 2: Run it and watch it fail**

```bash
JAVA_HOME=/home/imyuvi/.local/jdks/jdk-17.0.20.1+1 ./gradlew testDebugUnitTest \
  --tests "com.onemind.app.MemoryDisplayTest"
```

Expected: compilation failure, `Unresolved reference: MemoryDisplay`. If `MemorySummary`'s
constructor rejects the named arguments above, read
`app/src/main/java/com/onemind/app/domain/model/DerivedData.kt` around line 124 and adjust
the fixture — do not change `MemorySummary`.

- [ ] **Step 3: Write `MemoryDisplay.kt`**

```kotlin
package com.onemind.app.ui.feed

import com.onemind.app.domain.model.ContentType
import com.onemind.app.domain.model.Memory
import com.onemind.app.domain.processing.StageStatus

/**
 * What to call a Memory on a card, and what to say about it.
 *
 * Pure, and separate from any Composable, so the feed, the timeline and a search result
 * cannot drift into three slightly different answers — which is what happened when this
 * lived as a private function in one card and a second copy in another.
 *
 * A title and a snippet are two questions, not one. The old joined
 * `"$title — $summaryText"` could only ever be drawn as a single run of body text; the
 * reference sets the title in Outfit at 17 sp and clamps it to three lines, with the
 * snippet as separate, quieter text.
 */
object MemoryDisplay {

    /** What an image-only Memory is called before anything has read it. */
    const val FALLBACK_TITLE = "Untitled memory"

    /**
     * How much of the user's own first line can stand in for a title.
     *
     * Truncated here rather than clamped by the Composable, because a title that wraps to
     * three lines in a two-column grid pushes the chips and the footer off the card.
     */
    const val TITLE_FALLBACK_CHARS = 60

    /**
     * The model's title if it produced one, else the user's own first line.
     *
     * `StageStatus.SUCCESS` is required rather than just a non-null title: a stage that
     * failed may still have written a row, and its title describes whatever it managed
     * before failing.
     */
    fun title(memory: Memory): String {
        val summary = memory.derived.summary
        if (summary?.status == StageStatus.SUCCESS) {
            val title = summary.title?.takeIf { it.isNotBlank() }
            if (title != null) return title
        }

        val firstLine = userText(memory)
            .lineSequence()
            .firstOrNull { it.isNotBlank() }
            ?.trim()

        if (firstLine.isNullOrEmpty()) return FALLBACK_TITLE
        return if (firstLine.length <= TITLE_FALLBACK_CHARS) {
            firstLine
        } else {
            firstLine.take(TITLE_FALLBACK_CHARS) + "…"
        }
    }

    /**
     * The line under the title: the model's summary, or the user's text.
     *
     * Empty when the title already *is* the user's text, because a card that says the
     * same thing twice in two sizes looks like a rendering bug.
     */
    fun snippet(memory: Memory): String {
        val summary = memory.derived.summary
        if (summary?.status == StageStatus.SUCCESS && summary.summaryText.isNotBlank()) {
            return summary.summaryText
        }

        val text = userText(memory).trim()
        if (text.isEmpty()) return ""
        return if (text == title(memory)) "" else text
    }

    private fun userText(memory: Memory): String =
        memory.contentBlocks
            .filter { it.type == ContentType.TEXT }
            .joinToString("\n") { it.content }
}
```

- [ ] **Step 4: Run it and watch it pass**

```bash
JAVA_HOME=/home/imyuvi/.local/jdks/jdk-17.0.20.1+1 ./gradlew testDebugUnitTest \
  --tests "com.onemind.app.MemoryDisplayTest"
```

Expected: 9 tests, 0 failures.

`theSnippetIsEmptyRatherThanRepeatingTheTitle` is the one to watch: it passes only because
`title` returns the untruncated first line when it is short. If a later change truncates
unconditionally, the equality check fails and the snippet starts repeating the title. That
is the intended alarm.

---

## Task 3: `BentoSizing` — card size from grid position

**Files:**
- Create: `app/src/main/java/com/onemind/app/ui/feed/BentoSizing.kt`
- Test: `app/src/test/java/com/onemind/app/BentoSizingTest.kt` (create)

**Interfaces:**
- Consumes: `Memory`.
- Produces:
  - `enum class BentoSize { LARGE, MEDIUM, SMALL }`
  - `object BentoSizing { fun sizes(memories: List<Memory>): List<BentoSize> }`

**Why a pure function and not a field.** `index.html` sizes cards from a `size` field on
its mock `Memory`. The real `Memory` has no such field and is not getting one — how big a
card is drawn is a layout concern, and putting it in the domain model would make the
pipeline responsible for a decision the grid makes.

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/com/onemind/app/BentoSizingTest.kt`:

```kotlin
package com.onemind.app

import com.onemind.app.domain.model.ContentBlock
import com.onemind.app.domain.model.ContentType
import com.onemind.app.domain.model.Memory
import com.onemind.app.ui.feed.BentoSize
import com.onemind.app.ui.feed.BentoSizing
import org.junit.Assert.*
import org.junit.Test

/**
 * The bento rhythm: one large card for the newest image, then alternating medium and
 * small.
 *
 * Derived from position rather than stored on the Memory. `index.html` reads a `size`
 * field off its mock data; the real model has none and should not gain one — how large a
 * card is drawn is the grid's business, and putting it in the domain model would make the
 * processing pipeline responsible for a layout decision.
 */
class BentoSizingTest {

    private fun textMemory(id: Long) = Memory(
        id = id,
        contentBlocks = listOf(ContentBlock(type = ContentType.TEXT, content = "hi"))
    )

    private fun imageMemory(id: Long) = Memory(
        id = id,
        contentBlocks = listOf(ContentBlock(type = ContentType.IMAGE, content = "/tmp/a.webp"))
    )

    @Test
    fun anEmptyFeedHasNoSizes() {
        assertEquals(emptyList<BentoSize>(), BentoSizing.sizes(emptyList()))
    }

    @Test
    fun withNoImagesEveryCardAlternatesFromMedium() {
        val sizes = BentoSizing.sizes((1L..5L).map { textMemory(it) })

        assertEquals(
            listOf(
                BentoSize.MEDIUM, BentoSize.SMALL,
                BentoSize.MEDIUM, BentoSize.SMALL,
                BentoSize.MEDIUM
            ),
            sizes
        )
    }

    @Test
    fun theNewestImageGetsTheLargeCard() {
        val sizes = BentoSizing.sizes(listOf(imageMemory(1), textMemory(2), imageMemory(3)))

        // First in the list, because the feed is newest-first. Only one card is large,
        // however many images there are.
        assertEquals(BentoSize.LARGE, sizes[0])
        assertEquals(1, sizes.count { it == BentoSize.LARGE })
    }

    @Test
    fun theLargeCardIsNotCountedInTheAlternation() {
        val sizes = BentoSizing.sizes(
            listOf(textMemory(1), textMemory(2), imageMemory(3), textMemory(4), textMemory(5))
        )

        // Positions 0, 1, 3, 4 alternate as if position 2 were not there. Counting the
        // large card would put two smalls next to each other and break the rhythm at
        // exactly the point the eye is drawn to.
        assertEquals(
            listOf(
                BentoSize.MEDIUM, BentoSize.SMALL,
                BentoSize.LARGE,
                BentoSize.MEDIUM, BentoSize.SMALL
            ),
            sizes
        )
    }

    @Test
    fun anImageWithNoTextStillCountsAsAnImage() {
        val sizes = BentoSizing.sizes(listOf(imageMemory(1)))

        assertEquals(listOf(BentoSize.LARGE), sizes)
    }

    @Test
    fun sizesAlignsOneToOneWithTheInput() {
        val memories = (1L..7L).map { if (it == 4L) imageMemory(it) else textMemory(it) }

        // The grid zips these together, so a length mismatch would silently shift every
        // card's shape by one.
        assertEquals(memories.size, BentoSizing.sizes(memories).size)
    }
}
```

- [ ] **Step 2: Run it and watch it fail**

```bash
JAVA_HOME=/home/imyuvi/.local/jdks/jdk-17.0.20.1+1 ./gradlew testDebugUnitTest \
  --tests "com.onemind.app.BentoSizingTest"
```

Expected: compilation failure, `Unresolved reference: BentoSizing`.

- [ ] **Step 3: Write `BentoSizing.kt`**

```kotlin
package com.onemind.app.ui.feed

import com.onemind.app.domain.model.Memory

/**
 * How large a card is drawn in the bento grid.
 *
 * Each has its own asymmetric silhouette — see `CardShapeLarge` / `Medium` / `Small` —
 * and `LARGE` spans both grid columns.
 */
enum class BentoSize { LARGE, MEDIUM, SMALL }

/**
 * The bento rhythm.
 *
 * One large card for the newest Memory that has an image, then medium and small
 * alternating. Position, not a stored field: `index.html` reads a `size` off its mock
 * data, and adding one to the real `Memory` would make the processing pipeline the owner
 * of a decision the grid makes.
 *
 * Timeline and Events force `MEDIUM` and do not call this at all.
 */
object BentoSizing {

    fun sizes(memories: List<Memory>): List<BentoSize> {
        val largeIndex = memories.indexOfFirst { it.imageBlocks().isNotEmpty() }

        // Advanced only for the cards that participate, so the large one does not consume
        // a turn. Counting it would put two smalls side by side immediately after the
        // card the eye is already on.
        var alternation = 0

        return memories.mapIndexed { index, _ ->
            if (index == largeIndex) {
                BentoSize.LARGE
            } else {
                val size = if (alternation % 2 == 0) BentoSize.MEDIUM else BentoSize.SMALL
                alternation++
                size
            }
        }
    }
}
```

- [ ] **Step 4: Run it and watch it pass**

```bash
JAVA_HOME=/home/imyuvi/.local/jdks/jdk-17.0.20.1+1 ./gradlew testDebugUnitTest \
  --tests "com.onemind.app.BentoSizingTest"
```

Expected: 6 tests, 0 failures.

---

## Task 4: `BentoCard`

**Files:**
- Create: `app/src/main/java/com/onemind/app/ui/feed/BentoCard.kt`

**Interfaces:**
- Consumes: `MemoryDisplay` (Task 2), `BentoSize` (Task 3), `CategoryChip`, `StateChip`, `CookieThumb`, `rememberPressMorph`, `pressScale`, `CardShapeLarge/Medium/Small`, `EmberGradient`, `SourceRow` / `resolveSource`.
- Produces: `@Composable fun BentoCard(memory: Memory, size: BentoSize, onClick: () -> Unit, onLongClick: () -> Unit, onRetryProcessing: () -> Unit = {}, modifier: Modifier = Modifier)`

**Shape and press morph interact.** The rest corner is not one number — each size has its
own asymmetric silhouette — so the press morph animates a *uniform* corner and the card
swaps to `RoundedCornerShape(morph.corner)` only while pressed, returning to its
asymmetric shape at rest. Morphing four corners independently would need four
`animateDpAsState` per card and would make the signature notch disappear into a generic
rounded square on the way down.

- [ ] **Step 1: Write the file**

```kotlin
package com.onemind.app.ui.feed

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.onemind.app.domain.model.Memory
import com.onemind.app.domain.model.ProcessingState
import com.onemind.app.ui.components.CategoryChip
import com.onemind.app.ui.components.CookieThumb
import com.onemind.app.ui.components.StateChip
import com.onemind.app.ui.components.pressScale
import com.onemind.app.ui.components.rememberPressMorph
import com.onemind.app.ui.theme.CardShapeLarge
import com.onemind.app.ui.theme.CardShapeMedium
import com.onemind.app.ui.theme.CardShapeSmall
import com.onemind.app.ui.theme.EmberGradient

/** Category chips a card of each size has room for, per DESIGN-GUIDE §4. */
private fun chipBudget(size: BentoSize) = if (size == BentoSize.LARGE) 5 else 2

private fun restShape(size: BentoSize): Shape = when (size) {
    BentoSize.LARGE -> CardShapeLarge
    BentoSize.MEDIUM -> CardShapeMedium
    BentoSize.SMALL -> CardShapeSmall
}

/**
 * One Memory as a bento tile.
 *
 * Not a `Card`: the container is a `Surface` with an explicit shape, because the shape has
 * to change between the size's asymmetric silhouette at rest and a uniform morphing corner
 * while pressed, and `CardDefaults` elevation would add a shadow the design does not use —
 * DESIGN-GUIDE §2 is explicit that cards read by tonal step, not by shadow.
 *
 * The morph is uniform on purpose. Each size's rest shape has exactly one corner that
 * disagrees with the others, and animating four corners independently would dissolve that
 * notch into a generic rounded square on the way down — losing the signature at the one
 * moment the user is looking straight at it.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BentoCard(
    memory: Memory,
    size: BentoSize,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onRetryProcessing: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val interaction = remember { MutableInteractionSource() }
    val morph = rememberPressMorph(
        interactionSource = interaction,
        // Any of the three rest shapes is within a few dp of this, so the morph starts
        // from where the eye already is.
        restCorner = 32.dp
    )
    val pressed = morph.scale < 1f

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .pressScale(morph)
            .combinedClickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
                onLongClick = onLongClick,
                // Long-press was the only route to delete and carried no label, so a
                // screen-reader user could neither discover nor reach it. Kept.
                onClickLabel = "Open memory",
                onLongClickLabel = "Delete memory"
            ),
        shape = if (pressed) RoundedCornerShape(morph.corner) else restShape(size),
        color = MaterialTheme.colorScheme.secondaryContainer
    ) {
        Column {
            if (size == BentoSize.LARGE) {
                // A banner rather than a decoded thumbnail: the grid scrolls, and this
                // stands in for an image without paying to read one off disk.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                        .background(EmberGradient)
                )
            }

            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = MemoryDisplay.title(memory),
                        style = if (size == BentoSize.LARGE) {
                            MaterialTheme.typography.titleLarge
                        } else {
                            MaterialTheme.typography.titleSmall
                        },
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )

                    // Medium and small cards carry the blob instead of a banner. Large
                    // already has one, so a second image mark would just be noise.
                    if (size != BentoSize.LARGE && memory.imageBlocks().isNotEmpty()) {
                        CookieThumb(size = 44.dp)
                    }
                }

                val categories = memory.derived.categories.take(chipBudget(size))
                if (categories.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        categories.forEach { CategoryChip(name = it.name) }
                    }
                }

                CardFooter(memory = memory, onRetryProcessing = onRetryProcessing)
            }
        }
    }
}

/**
 * Source, date, a link mark, and the state chip when the state is worth saying.
 *
 * `READY` prints nothing: it is the state almost every card is in, and a chip on every
 * card would say nothing while costing a row of height on all of them.
 */
@Composable
private fun CardFooter(memory: Memory, onRetryProcessing: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        SourceRow(memory = memory)

        if (memory.derived.urls.isNotEmpty()) {
            Icon(
                imageVector = Icons.Default.Link,
                contentDescription = "Has links",
                modifier = Modifier.height(14.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (memory.processingState != ProcessingState.READY) {
            StateChip(state = memory.processingState)
        }
    }

    if (memory.processingState == ProcessingState.FAILED) {
        Surface(
            onClick = onRetryProcessing,
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.primaryContainer
        ) {
            Text(
                text = "Retry processing",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.padding(vertical = 10.dp, horizontal = 12.dp)
            )
        }
    }
}
```

- [ ] **Step 2: Reconcile `SourceRow`**

`SourceRow` already exists in `ui/feed/SourceDisplay.kt`. Read its signature:

```bash
sed -n '20,70p' app/src/main/java/com/onemind/app/ui/feed/SourceDisplay.kt
```

If it takes `(memory: Memory, modifier: Modifier = Modifier)` the call above is correct as
written. If it takes different parameters, adapt the call — do **not** change
`SourceDisplay.kt`, which the memory-detail screen also uses and which plan 5 restyles.

- [ ] **Step 3: Confirm it compiles**

```bash
JAVA_HOME=/home/imyuvi/.local/jdks/jdk-17.0.20.1+1 ./gradlew assembleDebug
```

Expected: BUILD SUCCESSFUL. `derived.urls` must exist on `DerivedData` — it does
(`ui/feed` already reads it). If `Icons.Default.Link` is unresolved, use
`Icons.Default.InsertLink`.

---

## Task 5: `TimelineScreen`, and the feed's toggle becomes the group — Issue F lands here

**Files:**
- Create: `app/src/main/java/com/onemind/app/ui/feed/TimelineScreen.kt`
- Modify: `app/src/main/java/com/onemind/app/ui/feed/FeedScreen.kt:86-98`, `:124-138`, `:327-348`, `:383-429`
- Modify: `app/src/main/java/com/onemind/app/ui/feed/FeedUiState.kt:20-21`, `:71`
- Modify: `app/src/main/java/com/onemind/app/ui/feed/FeedViewModel.kt:156-158`
- Modify: `app/src/main/java/com/onemind/app/ui/navigation/OneMindNavHost.kt`
- Test: `app/src/androidTest/java/com/onemind/app/SectionNavigationTest.kt` (create)

**Interfaces:**
- Consumes: everything from Tasks 1–4, plus `HeroHeader`, `SectionNav`, `PhoneFrame`, `StaggeredEntrance`, `DateGrouping`.
- Produces: `@Composable fun TimelineScreen(onNavigateToMemory: (Long) -> Unit, onNavigateToSection: (SectionDestination) -> Unit, viewModel: FeedViewModel = hiltViewModel())`

**What gets deleted, and why it is safe.** `ViewMode`, `FeedUiState.viewMode`,
`FeedViewModel.setViewMode`, `FeedScreen.ViewModeToggle` and `FeedScreen.TimelineView` all
exist to serve one toggle that the segmented group replaces. `ViewMode` is referenced only
by those five places — confirm with a grep before deleting. `DateGrouping` is **kept** and
reused as-is; it is the only part of the timeline that was worth having.

**Timeline reuses `FeedViewModel`.** It observes exactly the same memory stream and needs
nothing else. A `TimelineViewModel` would be a second subscription to the same Flow and a
second copy of the delete and retry plumbing. `hiltViewModel()` scopes per navigation
entry, so the two destinations get separate instances — two collectors on one Room Flow,
which is what the feed already pays for a single screen.

- [ ] **Step 1: Check what actually references `ViewMode`**

```bash
grep -rn "ViewMode\|viewMode" app/src --include="*.kt"
```

Expected: only `FeedUiState.kt`, `FeedViewModel.kt` and `FeedScreen.kt`. If a test
references it, that test is asserting the toggle and goes with it — say so in the commit
message rather than keeping the enum alive for it.

- [ ] **Step 2: Write the failing test**

Create `app/src/androidTest/java/com/onemind/app/SectionNavigationTest.kt`:

```kotlin
package com.onemind.app

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.navigation.compose.ComposeNavigator
import androidx.navigation.testing.TestNavHostController
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.onemind.app.ui.components.SectionDestination
import com.onemind.app.ui.navigation.NavRoutes
import com.onemind.app.ui.navigation.navigateToSection
import com.onemind.app.ui.navigation.route
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * That the three peer destinations swap rather than stack.
 *
 * A plain `navigate` would push, so Feed → Timeline → Events → Feed leaves four entries
 * and four presses of back to get out of a group that looks like tabs. This asserts the
 * back stack itself rather than what is on screen, because the wrong behaviour looks
 * identical until the user presses back.
 */
@RunWith(AndroidJUnit4::class)
class SectionNavigationTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var navController: TestNavHostController

    @Before
    fun setup() {
        composeRule.activity.runOnUiThread {
            navController = TestNavHostController(composeRule.activity)
            navController.navigatorProvider.addNavigator(ComposeNavigator())
        }
    }

    @Test
    fun eachDestinationMapsToItsRoute() {
        assertEquals(NavRoutes.FEED, SectionDestination.FEED.route())
        assertEquals(NavRoutes.TIMELINE, SectionDestination.TIMELINE.route())
        assertEquals(NavRoutes.EVENTS, SectionDestination.EVENTS.route())
    }
}
```

**Note on scope.** Asserting the back stack depth needs a real graph with real screens in
it, which means a Hilt test runner this project does not have. So the routing map is pinned
here, and the no-stacking behaviour is a manual verification step below. Say that plainly
rather than writing a test that appears to cover it.

Add `@get:Rule` needs `import org.junit.Rule`.

- [ ] **Step 3: Run it and watch it fail**

```bash
JAVA_HOME=/home/imyuvi/.local/jdks/jdk-17.0.20.1+1 ./gradlew connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.onemind.app.SectionNavigationTest
```

Expected: compilation failure if `androidx.navigation:navigation-testing` is not on the
classpath. If so, either add
`androidTestImplementation("androidx.navigation:navigation-testing:2.9.0")` to
`app/build.gradle.kts` via the version catalog, or drop `TestNavHostController` from this
test — the one assertion left does not need it. Prefer dropping it: a dependency added for
an unused field is worse than a smaller test.

- [ ] **Step 4: Write `TimelineScreen.kt`**

```kotlin
package com.onemind.app.ui.feed

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.onemind.app.ui.components.HeroHeader
import com.onemind.app.ui.components.PhoneFrame
import com.onemind.app.ui.components.SectionDestination
import com.onemind.app.ui.components.SectionNav
import com.onemind.app.ui.components.StaggeredEntrance
import com.onemind.app.ui.theme.PillShape
import com.onemind.app.ui.theme.Tracking

/**
 * The same Memories the feed shows, in date sections on a rail.
 *
 * Reuses [FeedViewModel] rather than getting its own. It needs precisely the feed's
 * stream and nothing else, and a `TimelineViewModel` would be a second subscription to the
 * same Room Flow plus a second copy of the delete and retry plumbing. `hiltViewModel()`
 * scopes per navigation entry, so this destination gets its own instance — one more
 * collector on a Flow the feed already collects.
 *
 * Every card is `MEDIUM`, as the reference specifies: the bento rhythm is the feed's
 * signature, and a chronological list wants a steady beat instead.
 */
@Composable
fun TimelineScreen(
    onNavigateToMemory: (Long) -> Unit,
    onNavigateToSection: (SectionDestination) -> Unit,
    viewModel: FeedViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    PhoneFrame {
        HeroHeader(eyebrow = "Chronological", title = "Back in time")

        SectionNav(
            selected = SectionDestination.TIMELINE,
            onSelect = onNavigateToSection,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
        )

        when {
            uiState.isLoading -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }

            uiState.memories.isEmpty() -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Nothing captured here yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            else -> {
                val groups = remember(uiState.memories) { DateGrouping.group(uiState.memories) }
                var position = 0

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp)
                ) {
                    groups.forEach { (group, groupMemories) ->
                        item(key = "header-${group.name}") {
                            SectionHeader(label = group.label)
                        }

                        items(groupMemories, key = { it.id }) { memory ->
                            // Captured per item so the stagger runs down the whole
                            // screen rather than restarting at each section, which
                            // would read as three separate lists appearing.
                            val index = position++
                            RailRow {
                                StaggeredEntrance(index = index) {
                                    BentoCard(
                                        memory = memory,
                                        size = BentoSize.MEDIUM,
                                        onClick = { onNavigateToMemory(memory.id) },
                                        onLongClick = { viewModel.requestDelete(memory) },
                                        onRetryProcessing = { viewModel.retryProcessing(memory) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    uiState.memoryToDelete?.let {
        DeleteConfirmationDialog(
            onConfirm = { viewModel.confirmDelete() },
            onDismiss = { viewModel.dismissDelete() }
        )
    }
}

/** `section-tag` + `section-rule`: an uppercase pill and a hairline across the rest. */
@Composable
private fun SectionHeader(label: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 28.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Surface(shape = PillShape, color = MaterialTheme.colorScheme.primaryContainer) {
            Text(
                text = label.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                letterSpacing = Tracking.Chip,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
            )
        }
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.outlineVariant
        )
    }
}

/**
 * One item hung off the rail: a 2 dp vertical line with a dot beside each card.
 *
 * The dot carries a background-coloured ring so the rail appears to pass behind it rather
 * than through it — which is the whole visual trick, and is why the ring is drawn rather
 * than the line being broken.
 */
@Composable
private fun RailRow(content: @Composable () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier.width(20.dp),
            contentAlignment = Alignment.TopCenter
        ) {
            Box(
                modifier = Modifier
                    .width(2.dp)
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.outlineVariant)
            )
            Box(
                modifier = Modifier
                    .padding(top = 22.dp)
                    .size(20.dp)
                    .clip(PillShape)
                    .background(MaterialTheme.colorScheme.background),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(PillShape)
                        .background(MaterialTheme.colorScheme.primary)
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Box(modifier = Modifier.weight(1f).padding(bottom = 12.dp)) { content() }
    }
}
```

`background` on a `Modifier` needs `import androidx.compose.foundation.background`.
`DeleteConfirmationDialog` is currently `private` in `FeedScreen.kt` — change it to
internal (drop the `private`) so both screens use one dialog rather than two copies.

- [ ] **Step 5: Replace the feed's toggle with the group and delete the dead paths**

In `app/src/main/java/com/onemind/app/ui/feed/FeedScreen.kt`:

1. Add `onNavigateToSection: (SectionDestination) -> Unit` to the parameter list, after
   `onNavigateToEvents`. Leave `onNavigateToEvents` for now — Task 9 removes it once the
   hero owns the settings button and the group owns Events.
2. Replace the `ViewModeToggle(...)` call at `:87-91` with:

```kotlin
                SectionNav(
                    selected = SectionDestination.FEED,
                    onSelect = onNavigateToSection,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
```

3. Replace the `when` branches at `:124-138` with a single branch, since there is no
   longer a mode to switch on:

```kotlin
                        else -> MemoryFeedList(
                            memories = filteredMemories,
                            onMemoryClick = { onNavigateToMemory(it.id) },
                            onMemoryLongClick = { viewModel.requestDelete(it) },
                            onRetryProcessing = { viewModel.retryProcessing(it) },
                            modifier = Modifier.fillMaxSize()
                        )
```

4. Delete the `ViewModeToggle` composable (`:327-348`) and the `TimelineView` composable
   (`:383-429`) entirely.
5. Drop `private` from `DeleteConfirmationDialog` (`:306`).
6. Add imports for `SectionDestination` and `SectionNav`; remove the now-unused
   `remember` import if nothing else in the file uses it.

In `app/src/main/java/com/onemind/app/ui/feed/FeedUiState.kt`, delete:

```kotlin
    /** View mode (#22). FEED = flat list, TIMELINE = date-grouped. */
    val viewMode: ViewMode = ViewMode.FEED,
```

and the trailing `enum class ViewMode { FEED, TIMELINE }`.

In `app/src/main/java/com/onemind/app/ui/feed/FeedViewModel.kt`, delete:

```kotlin
    fun setViewMode(mode: ViewMode) {
        _uiState.update { it.copy(viewMode = mode) }
    }
```

- [ ] **Step 6: Wire the destination into the graph**

In `app/src/main/java/com/onemind/app/ui/navigation/OneMindNavHost.kt`, replace the
`composable(NavRoutes.FEED)` block with:

```kotlin
        composable(NavRoutes.FEED) {
            FeedScreen(
                onNavigateToComposer = { navController.navigate(NavRoutes.COMPOSER) },
                onNavigateToMemory = { memoryId ->
                    navController.navigate(NavRoutes.memoryDetail(memoryId))
                },
                onNavigateToSettings = { navController.navigate(NavRoutes.SETTINGS) },
                onNavigateToEvents = { navController.navigate(NavRoutes.EVENTS) },
                onNavigateToSection = navController::navigateToSection
            )
        }

        composable(NavRoutes.TIMELINE) {
            TimelineScreen(
                onNavigateToMemory = { memoryId ->
                    navController.navigate(NavRoutes.memoryDetail(memoryId))
                },
                onNavigateToSection = navController::navigateToSection
            )
        }
```

Add `import com.onemind.app.ui.feed.TimelineScreen`.

- [ ] **Step 7: Run everything**

```bash
JAVA_HOME=/home/imyuvi/.local/jdks/jdk-17.0.20.1+1 ./gradlew assembleDebug testDebugUnitTest
JAVA_HOME=/home/imyuvi/.local/jdks/jdk-17.0.20.1+1 ./gradlew connectedDebugAndroidTest
```

Expected: BUILD SUCCESSFUL on both.

- [ ] **Step 8: Verify the back stack by hand**

The one behaviour no test here covers, so it gets checked deliberately:

```bash
JAVA_HOME=/home/imyuvi/.local/jdks/jdk-17.0.20.1+1 ./gradlew installDebug
```

Open the app, then tap Timeline → Events → Feed → Timeline → Feed. **One** press of back
should leave the app. If it takes several, `popUpTo` is not doing its job. Also scroll the
feed down, switch to Timeline and back: the feed should still be where you left it, which
is what `saveState`/`restoreState` are for.

- [ ] **Step 9: File Issue F, commit, close**

File:

```bash
python3 - <<'PY'
import json, re, urllib.request
pat = re.search(r'^GITHUB_PAT=(.*)$',
                open('/home/imyuvi/projects/codingagents/.env').read(), re.M).group(1).strip()
body = {
  "title": "Timeline destination",
  "body": """Timeline is a `ViewMode` toggle inside `FeedScreen` — two `FilterChip`s that
flip a field on `FeedUiState`. It has no route, cannot be navigated to or linked, and loses
its selection on process death. The reference makes it a peer destination in a connected
segmented group beside Feed and Events.

**Fix.** `NavRoutes` gains `TIMELINE` (and `SEARCH`, for the next slice). Feed, Timeline
and Events swap through `navigateToSection`, which pops to the feed and uses
`launchSingleTop` so the three cannot stack — otherwise a group that looks like tabs takes
four presses of back to leave. `saveState`/`restoreState` keep each destination's scroll
position, without which returning to the feed snaps it to the top and reads as a reload.

`TimelineScreen` reuses `FeedViewModel`: it needs exactly the feed's stream, and a second
view model would be a second subscription to the same Room Flow plus a second copy of the
delete and retry plumbing. `DateGrouping` is kept and reused as-is.

Two pieces come out of `MemoryCard` on the way, both now unit-tested for the first time:
title/snippet derivation, which was a private function returning `"$title — $summary"` as
one string and cannot serve a design that sets the title in its own face; and bento sizing,
which `index.html` reads off a `size` field the real `Memory` does not have and is not
getting — how large a card is drawn is the grid's business, not the pipeline's.

`ViewMode`, `setViewMode`, `ViewModeToggle` and the old `TimelineView` are deleted. They
existed for the toggle the group replaces.

Plan: `docs/superpowers/plans/2026-08-24-feed-timeline-search-destinations.md`"""
}
req = urllib.request.Request(
    "https://api.github.com/repos/Yuvraj-ai/oneMind/issues",
    data=json.dumps(body).encode(),
    headers={"Authorization": f"Bearer {pat}", "Accept": "application/vnd.github+json",
             "Content-Type": "application/json"})
print("issue", json.load(urllib.request.urlopen(req))["number"])
PY
```

Commit (substitute the real number):

```bash
git rev-parse --abbrev-ref HEAD   # must print my-extra-work

git add app/src/main/java/com/onemind/app/ui/navigation/ \
        app/src/main/java/com/onemind/app/ui/feed/ \
        app/src/test/java/com/onemind/app/MemoryDisplayTest.kt \
        app/src/test/java/com/onemind/app/BentoSizingTest.kt \
        app/src/androidTest/java/com/onemind/app/SectionNavigationTest.kt

git commit -F - <<'MSG'
feat(ui): make Timeline a destination rather than a mode (#F)

Timeline was two FilterChips flipping a field on FeedUiState. No route, no way
to link to it, and the selection was lost on process death. It is now a peer of
Feed and Events in a connected segmented group.

navigateToSection pops to the feed and uses launchSingleTop, so the three
cannot stack: without it, Feed to Timeline to Events to Feed leaves four
entries and four presses of back to leave something that looks like tabs.
saveState and restoreState keep each destination's scroll position, without
which returning to a scrolled feed snaps it to the top and reads as a reload.
Neither is covered by a test here — asserting back-stack depth needs a real
graph and a Hilt test runner this project does not have — so both are a
recorded manual check instead of a test that appears to cover them.

TimelineScreen reuses FeedViewModel. It wants exactly the feed's stream, and a
TimelineViewModel would be a second subscription to the same Room Flow plus a
second copy of the delete and retry plumbing. DateGrouping is reused unchanged;
it was the part of the old timeline worth keeping.

Two things came out of MemoryCard and are unit-tested for the first time.
MemoryDisplay: the old private helper returned "$title — $summary" as one
string, which can only be drawn as one run of body text, and the design sets
the title in Outfit at 17 sp on its own. It also returns an empty snippet when
the title already is the user's text, so a card cannot say the same thing twice
in two sizes. BentoSizing: index.html reads a size field off its mock Memory;
the real one has none and should not gain one, because how large a card is
drawn is the grid's decision and not the pipeline's. The large card is left out
of the medium/small alternation, or two smalls end up side by side right where
the eye already is.

ViewMode, setViewMode, ViewModeToggle and the old TimelineView are gone. They
existed only for the toggle the group replaces.
MSG
```

Close with:

```
Done. `NavRoutes` has `TIMELINE` and `SEARCH`; Feed, Timeline and Events swap through
`navigateToSection` with `popUpTo` + `launchSingleTop` so they cannot stack, and
`saveState`/`restoreState` so scroll survives a swap.

`TimelineScreen` reuses `FeedViewModel` and `DateGrouping`. `MemoryDisplay` and
`BentoSizing` came out of `MemoryCard` and have unit tests — 15 between them, and neither
had any before.

Two behaviours are verified by hand rather than by test, and that is recorded rather than
papered over: back-stack depth and scroll restoration both need a real navigation graph
with real screens, which needs a Hilt test runner the project does not have. Checked on
device: one press of back leaves after four swaps, and the feed keeps its scroll position.

`ViewMode` and its toggle are deleted.

*Implemented by an AI agent (Claude), reviewed against the design reference.*
```

---

## Task 6: `SearchViewModel` — search state leaves the feed

**Files:**
- Create: `app/src/main/java/com/onemind/app/ui/search/SearchViewModel.kt`
- Modify: `app/src/main/java/com/onemind/app/ui/feed/FeedViewModel.kt` — remove search
- Modify: `app/src/main/java/com/onemind/app/ui/feed/FeedUiState.kt` — remove search fields

**Interfaces:**
- Consumes: `SearchOrchestrator`, `FtsQuery`, `SearchResult`, `Memory` — all unchanged.
- Produces:
  - `data class SearchUiState(query: String = "", results: List<SearchResult> = emptyList(), terms: List<String> = emptyList(), isSearching: Boolean = false)` with `val isActive: Boolean`
  - `class SearchViewModel` with `uiState: StateFlow<SearchUiState>`, `onQueryChanged(String)`, `clear()`

**This is a move, not a rewrite.** The debounce interval, the `flatMapLatest`, the
`FtsQuery.build` null check and the `isSearchActive` semantics all transfer verbatim,
including their comments — those comments record defects that were fixed and the reasoning
is still load-bearing. The one thing that does not transfer is the unused private
`searchMemories`, which goes.

- [ ] **Step 1: Write `SearchViewModel.kt`**

```kotlin
package com.onemind.app.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.onemind.app.domain.search.FtsQuery
import com.onemind.app.domain.search.SearchOrchestrator
import com.onemind.app.domain.search.SearchResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Search, on its own screen and in its own view model.
 *
 * This state used to live in `FeedViewModel`, which meant every feed — including one
 * belonging to a user who never searched — was constructed with a debounced query flow and
 * a `SearchOrchestrator` attached. The reference puts search behind a bar on the feed that
 * is a navigation affordance, so the state follows it.
 *
 * Nothing below `SearchOrchestrator` changed. The debounce, the `flatMapLatest` and the
 * `FtsQuery.build` null check are carried over exactly, comments included, because those
 * comments record defects that were fixed and the reasoning still applies.
 */
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val searchOrchestrator: SearchOrchestrator
) : ViewModel() {

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    /**
     * Raw keystrokes, kept separate from [_uiState] so debouncing does not have to
     * reason about unrelated state changes.
     */
    private val queryFlow = MutableStateFlow("")

    init {
        observeQuery()
    }

    /**
     * Run a search a short pause after typing stops.
     *
     * `debounce` keeps a fast typist from issuing a query per keystroke;
     * `flatMapLatest` cancels a search whose results are already obsolete, which
     * matters because otherwise a slow early query can land after a fast later one
     * and overwrite it with results for text the user has moved on from.
     */
    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    private fun observeQuery() {
        viewModelScope.launch {
            queryFlow
                .debounce(SEARCH_DEBOUNCE_MS)
                .flatMapLatest { query ->
                    flow {
                        if (FtsQuery.build(query) == null) {
                            // Nothing usable typed — punctuation, or a single
                            // character. Emit no results and let the empty state show.
                            emit(emptyList<SearchResult>() to emptyList<String>())
                            return@flow
                        }
                        emit(searchOrchestrator.search(query) to FtsQuery.terms(query))
                    }
                }
                .collect { (results, terms) ->
                    _uiState.update {
                        it.copy(results = results, terms = terms, isSearching = false)
                    }
                }
        }
    }

    fun onQueryChanged(query: String) {
        _uiState.update {
            it.copy(
                query = query,
                // Only claim to be searching when there is something to search for,
                // so a stray keystroke does not flash a spinner.
                isSearching = FtsQuery.build(query) != null,
                // Drop stale results immediately rather than showing results for the
                // previous query under the new one.
                results = if (query.isBlank()) emptyList() else it.results
            )
        }
        queryFlow.value = query
    }

    fun clear() {
        _uiState.update {
            it.copy(query = "", results = emptyList(), terms = emptyList(), isSearching = false)
        }
        queryFlow.value = ""
    }

    companion object {
        /** Pause after the last keystroke before searching. */
        private const val SEARCH_DEBOUNCE_MS = 300L
    }
}

data class SearchUiState(
    /** Exactly what the user typed. */
    val query: String = "",

    /** Matches for [query], best first. Meaningless while [isSearching]. */
    val results: List<SearchResult> = emptyList(),

    /**
     * Terms the current search matched on, for highlighting snippets.
     *
     * Held here rather than recomputed per card: every result needs the same list, and
     * re-parsing the query for each one would repeat the work on every frame.
     */
    val terms: List<String> = emptyList(),

    val isSearching: Boolean = false
) {
    /**
     * True once the user has typed something the search can actually act on.
     *
     * Keyed on whether a query could be *built*, not on whether text was typed. Those
     * differ: `FtsQuery.build` returns null for a single character or a query made only of
     * stopwords, so keying on raw text made typing "the" or "a" show a hard "No memories
     * found" — telling the user their memories were missing when nothing had been searched
     * for.
     */
    val isActive: Boolean get() = FtsQuery.build(query) != null
}
```

- [ ] **Step 2: Strip search out of the feed**

In `app/src/main/java/com/onemind/app/ui/feed/FeedViewModel.kt`, delete:

- the `searchOrchestrator: SearchOrchestrator` constructor parameter
- the `searchQueryFlow` property
- the `observeSearchQuery()` call in `init` and the whole method
- the unused private `searchMemories`
- `onSearchQueryChanged` and `clearSearch`
- the `companion object` holding `SEARCH_DEBOUNCE_MS`
- the now-unused imports: `FtsQuery`, `SearchOrchestrator`, `SearchResult`,
  `ExperimentalCoroutinesApi`, `FlowPreview`, `debounce`, `flatMapLatest`, `flow`

In `app/src/main/java/com/onemind/app/ui/feed/FeedUiState.kt`, delete the whole
`// --- search (#24) ---` block (`searchQuery`, `searchResults`, `searchTerms`,
`isSearching`), the `isSearchActive` computed property, and the `FtsQuery` and
`SearchResult` imports.

**`FeedViewModel`'s constructor changes, so `assembleDebug` is mandatory** before this
commit lands — it is the only check that validates the Hilt graph.

- [ ] **Step 3: Verify the graph**

```bash
JAVA_HOME=/home/imyuvi/.local/jdks/jdk-17.0.20.1+1 ./gradlew assembleDebug
```

Expected: failures only in `FeedScreen.kt`, which still reads the removed state. Task 8
fixes it. A Hilt error naming `SearchOrchestrator` means the new binding is wrong and must
be fixed here.

---

## Task 7: `SearchScreen`

**Files:**
- Create: `app/src/main/java/com/onemind/app/ui/search/SearchScreen.kt`
- Move: `app/src/main/java/com/onemind/app/ui/feed/SearchResultCard.kt` → `app/src/main/java/com/onemind/app/ui/search/SearchResultCard.kt`
- Test: `app/src/androidTest/java/com/onemind/app/SearchScreenTest.kt` (create)

**Interfaces:**
- Consumes: `SearchViewModel`, `SearchUiState` (Task 6), `PhoneFrame`, `HeroHeader`, `PillShape`.
- Produces: `@Composable fun SearchScreen(onNavigateToMemory: (Long) -> Unit, onNavigateBack: () -> Unit, viewModel: SearchViewModel = hiltViewModel())`

**One bar, no filters.** DESIGN-GUIDE §4 and the project's locked product decisions agree:
context belongs in the query text, not in chips beside it. The suggestion pills in the
empty state are examples of *what to type*, not filters.

- [ ] **Step 1: Write the failing test**

Create `app/src/androidTest/java/com/onemind/app/SearchScreenTest.kt`:

```kotlin
package com.onemind.app

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.onemind.app.domain.search.SearchOrchestrator
import com.onemind.app.ui.search.SearchScreen
import com.onemind.app.ui.search.SearchViewModel
import com.onemind.app.ui.theme.OneMindTheme
import io.mockk.coEvery
import io.mockk.mockk
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The three things the search screen has to get right: it starts by suggesting rather
 * than by reporting nothing found, it can be left, and a query that finds nothing says so
 * without claiming the user's memories are missing.
 *
 * The third is the one with history. `isActive` keys on whether a query could be *built*,
 * not on whether text was typed, because `FtsQuery.build` returns null for a single
 * character or an all-stopword query — and keying on raw text made typing "a" announce
 * "No memories found".
 */
@RunWith(AndroidJUnit4::class)
class SearchScreenTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private var backPresses = 0

    private fun render(orchestrator: SearchOrchestrator) {
        backPresses = 0
        composeRule.setContent {
            OneMindTheme {
                SearchScreen(
                    onNavigateToMemory = {},
                    onNavigateBack = { backPresses++ },
                    viewModel = SearchViewModel(orchestrator)
                )
            }
        }
        composeRule.waitForIdle()
    }

    @Test
    fun anUntouchedScreenSuggestsRatherThanReportingNothing() {
        render(mockk(relaxed = true))

        composeRule.onNodeWithText("Try asking").assertIsDisplayed()
        // The failure this guards: an empty query is not a failed search.
        composeRule.onNodeWithText("No memories matched").assertDoesNotExist()
    }

    @Test
    fun theScreenCanBeLeft() {
        render(mockk(relaxed = true))

        composeRule.onNodeWithContentDescription("Back").performClick()

        assertEquals(1, backPresses)
    }

    @Test
    fun aSingleCharacterIsNotAFailedSearch() {
        val orchestrator = mockk<SearchOrchestrator>(relaxed = true)
        coEvery { orchestrator.search(any()) } returns emptyList()
        render(orchestrator)

        composeRule.onNodeWithText("Ask in your own words…").performTextInput("a")
        composeRule.waitForIdle()

        // FtsQuery.build returns null for one character, so nothing was searched for and
        // nothing should be reported as missing.
        composeRule.onNodeWithText("No memories matched").assertDoesNotExist()
        composeRule.onNodeWithText("Try asking").assertIsDisplayed()
    }
}
```

Add `import androidx.compose.ui.test.assertDoesNotExist`.

- [ ] **Step 2: Run it and watch it fail**

```bash
JAVA_HOME=/home/imyuvi/.local/jdks/jdk-17.0.20.1+1 ./gradlew connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.onemind.app.SearchScreenTest
```

Expected: compilation failure, `Unresolved reference: SearchScreen`.

- [ ] **Step 3: Move `SearchResultCard`**

```bash
git mv app/src/main/java/com/onemind/app/ui/feed/SearchResultCard.kt \
       app/src/main/java/com/onemind/app/ui/search/SearchResultCard.kt
```

Change its package declaration to `package com.onemind.app.ui.search` and add whatever
`com.onemind.app.ui.feed.*` imports it now needs — at minimum `MemoryDisplay` if it
derives a title, and `SourceRow` if it draws one. Its rendering is otherwise unchanged in
this task; restyling it is Task 8's business only insofar as the grid around it changes.

- [ ] **Step 4: Write `SearchScreen.kt`**

```kotlin
package com.onemind.app.ui.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.onemind.app.ui.components.HeroHeader
import com.onemind.app.ui.components.PhoneFrame
import com.onemind.app.ui.theme.PillShape
import com.onemind.app.ui.theme.Tracking

/** Examples of what to type, not filters. */
private val SUGGESTIONS = listOf(
    "that article about sleep",
    "screenshots from last week",
    "the restaurant someone recommended"
)

/**
 * Unified retrieval behind one bar.
 *
 * No filter chips, and that is a locked product decision rather than an omission: context
 * belongs in the query text. The suggestion pills below the bar are examples of things to
 * type, and tapping one types it.
 *
 * Three states, kept distinct because each asks something different of the user — wait,
 * try different words, or carry on. Collapsing them would tell someone their search failed
 * while it was still running, and the empty-query case is not a failed search at all.
 */
@Composable
fun SearchScreen(
    onNavigateToMemory: (Long) -> Unit,
    onNavigateBack: () -> Unit,
    viewModel: SearchViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    PhoneFrame {
        HeroHeader(
            eyebrow = "Unified retrieval",
            title = "What are you looking for?",
            // `leading`, not `trailing`: the reference puts the back button on its own row
            // above the eyebrow. In `trailing` it would sit beside the title on the right,
            // reading as an action rather than a way out.
            leading = {
                IconButton(onClick = onNavigateBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                }
            }
        )

        TextField(
            value = uiState.query,
            onValueChange = viewModel::onQueryChanged,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .height(64.dp),
            placeholder = { Text("Ask in your own words…") },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            trailingIcon = {
                if (uiState.query.isNotEmpty()) {
                    IconButton(onClick = viewModel::clear) {
                        Icon(Icons.Default.Close, "Clear search")
                    }
                }
            },
            singleLine = true,
            shape = MaterialTheme.shapes.extraLarge,
            colors = TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent
            )
        )

        when {
            !uiState.isActive -> SuggestionsState(
                onSuggestion = viewModel::onQueryChanged
            )

            uiState.isSearching && uiState.results.isEmpty() -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }

            uiState.results.isEmpty() -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "No memories matched",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Try describing it differently",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Text(
                        text = "${uiState.results.size} " +
                            if (uiState.results.size == 1) "memory matched" else "memories matched",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                items(uiState.results, key = { it.memory.id }) { result ->
                    SearchResultCard(
                        result = result,
                        queryTerms = uiState.terms,
                        onClick = { onNavigateToMemory(result.memory.id) },
                        onLongClick = { }
                    )
                }
            }
        }
    }
}

@Composable
private fun SuggestionsState(onSuggestion: (String) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = "Try asking",
            style = MaterialTheme.typography.labelSmall,
            letterSpacing = Tracking.Eyebrow,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        SUGGESTIONS.forEach { suggestion ->
            Surface(
                onClick = { onSuggestion(suggestion) },
                modifier = Modifier.fillMaxWidth(),
                shape = PillShape,
                color = MaterialTheme.colorScheme.surfaceContainer
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Schedule,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = suggestion,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        Text(
            text = "Keyword, meaning and time, all from one bar.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
```

- [ ] **Step 5: Run the test and watch it pass**

```bash
JAVA_HOME=/home/imyuvi/.local/jdks/jdk-17.0.20.1+1 ./gradlew connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.onemind.app.SearchScreenTest
```

Expected: 3 tests, 0 failures. `SearchOrchestrator` must be mockable — if it is a `final`
class without an interface, mockk needs `mockk<SearchOrchestrator>()` to work via the
inline agent, which `mockk-android` provides. If it fails to mock, wrap the call in the
test with a hand-rolled subclass rather than making the production class open.

---

## Task 8: Point the feed's bar at the new destination

**Files:**
- Modify: `app/src/main/java/com/onemind/app/ui/feed/FeedScreen.kt`
- Modify: `app/src/main/java/com/onemind/app/ui/navigation/OneMindNavHost.kt`

**Interfaces:**
- Consumes: `NavRoutes.SEARCH` (Task 1), `SearchScreen` (Task 7).
- Produces: `FeedScreen` gains `onNavigateToSearch: () -> Unit`; its `SearchBar`, `SearchResultsSection` and the `isSearchActive` branches are gone.

- [ ] **Step 1: Replace the inline bar with a pill that navigates**

In `app/src/main/java/com/onemind/app/ui/feed/FeedScreen.kt`:

1. Add `onNavigateToSearch: () -> Unit` to the parameter list.
2. Replace the `SearchBar(...)` call with:

```kotlin
            SearchPill(
                onClick = onNavigateToSearch,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )
```

3. Delete the private `SearchBar` composable and the private `SearchResultsSection`
   composable entirely.
4. Remove the `if (!uiState.isSearchActive)` wrapper around the browsing controls — there
   is no search state on this screen to hide them for — and delete the
   `uiState.isSearchActive -> SearchResultsSection(...)` branch from the main `when`.
5. Add the pill:

```kotlin
/**
 * A search affordance that is not a text field.
 *
 * Looks like the bar it replaces and behaves like a button, which is the point: search is
 * its own destination now, and a field here would put a second copy of the query state on
 * a screen that no longer owns any.
 */
@Composable
private fun SearchPill(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(64.dp),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "Ask for anything you saved…",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
```

6. Remove imports left unused by the deletions — `Close`, `TextField`,
   `TextFieldDefaults`, `Color`, `LazyColumn`/`items` if the results list was their only
   user, and `SearchResultCard`.

- [ ] **Step 2: Wire the destination**

In `OneMindNavHost.kt`, add `onNavigateToSearch = { navController.navigate(NavRoutes.SEARCH) }`
to the `FeedScreen(...)` call, and add:

```kotlin
        composable(NavRoutes.SEARCH) {
            SearchScreen(
                onNavigateToMemory = { memoryId ->
                    navController.navigate(NavRoutes.memoryDetail(memoryId))
                },
                onNavigateBack = { navController.popBackStack() }
            )
        }
```

with `import com.onemind.app.ui.search.SearchScreen`.

- [ ] **Step 3: Run everything**

```bash
JAVA_HOME=/home/imyuvi/.local/jdks/jdk-17.0.20.1+1 ./gradlew assembleDebug testDebugUnitTest
JAVA_HOME=/home/imyuvi/.local/jdks/jdk-17.0.20.1+1 ./gradlew connectedDebugAndroidTest
```

Expected: BUILD SUCCESSFUL on both. `assembleDebug` is what proves Hilt can still build
`FeedViewModel` without `SearchOrchestrator` and `SearchViewModel` with it.

- [ ] **Step 4: File Issue G, commit, close**

File with title **"Search destination"** and this body:

```
Search lives inside `FeedViewModel`: a debounced query flow, a `SearchOrchestrator`,
`searchResults`, `searchTerms` and `isSearching`, plus a private `searchMemories` that
nothing calls. Every feed is constructed with a search subsystem attached, including one
belonging to a user who never searches, and the feed screen hides half of itself whenever
a query is active.

The reference makes search its own screen behind a bar on the feed that is a navigation
affordance rather than a field.

**Fix.** A `SearchViewModel` over the same, untouched, `SearchOrchestrator`. The debounce,
the `flatMapLatest`, and the `FtsQuery.build` null check move across verbatim with their
comments — those comments record fixed defects and the reasoning still holds. The unused
private method does not come along. The feed's field becomes a pill that navigates.

`domain/search/` is not touched. This is a relocation of state, not a change to retrieval.

`FeedViewModel`'s constructor changes, so `assembleDebug` must pass before it lands.
```

Commit:

```bash
git add app/src/main/java/com/onemind/app/ui/search/ \
        app/src/main/java/com/onemind/app/ui/feed/ \
        app/src/main/java/com/onemind/app/ui/navigation/OneMindNavHost.kt \
        app/src/androidTest/java/com/onemind/app/SearchScreenTest.kt

git commit -F - <<'MSG'
feat(ui): give search its own destination (#G)

Search state lived in FeedViewModel: a debounced query flow, a
SearchOrchestrator, three result fields, and a private searchMemories nothing
called. Every feed was built with a search subsystem attached whether or not
anyone searched, and FeedScreen hid half of itself whenever a query went
active.

SearchViewModel now owns it, over the same SearchOrchestrator. The debounce,
the flatMapLatest and the FtsQuery.build null check move across verbatim,
comments included: those comments record defects that were fixed and the
reasoning is still load-bearing. Keying "is a search active" on whether a query
could be built rather than on whether text was typed is the one worth naming —
FtsQuery.build returns null for a single character, and keying on raw text made
typing "a" announce "No memories found".

Nothing under domain/search/ changed. This moves state, not retrieval.

The feed's text field becomes a pill that navigates. It looks like the bar it
replaces and behaves like a button, which is deliberate: a field here would put
a second copy of query state on a screen that no longer owns any.

SearchResultCard moved to ui/search/ with it. The empty state suggests things
to type rather than reporting nothing found, because an untouched search is not
a failed one.
MSG
```

Close with a comment recording the same three points, ending
`*Implemented by an AI agent (Claude), reviewed against the design reference.*`

---

## Task 9: Rebuild the feed — Issue H lands here

**Files:**
- Modify: `app/src/main/java/com/onemind/app/ui/feed/FeedScreen.kt` (whole file)
- Delete: `app/src/main/java/com/onemind/app/ui/feed/MemoryCard.kt`
- Modify: `app/src/main/java/com/onemind/app/ui/feed/SourceFilterRow.kt` — chip styling only

**Interfaces:**
- Consumes: everything above, plus `LargeFloatingActionButton`, `FabShadowColor`.
- Produces: `@Composable fun FeedScreen(onNavigateToComposer: () -> Unit, onNavigateToMemory: (Long) -> Unit, onNavigateToSettings: () -> Unit, onNavigateToSearch: () -> Unit, onNavigateToSection: (SectionDestination) -> Unit, viewModel: FeedViewModel = hiltViewModel())`

Note `onNavigateToEvents` is **gone** — the segmented group owns that navigation now, so a
second route to the same place would be two affordances for one action.

- [ ] **Step 1: Rewrite `FeedScreen.kt`**

```kotlin
package com.onemind.app.ui.feed

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.onemind.app.domain.model.Memory
import com.onemind.app.ui.components.HeroHeader
import com.onemind.app.ui.components.PhoneFrame
import com.onemind.app.ui.components.SectionDestination
import com.onemind.app.ui.components.SectionNav
import com.onemind.app.ui.components.StaggeredEntrance
import com.onemind.app.ui.theme.FabShadowColor

/**
 * The browse-first home.
 *
 * A two-column bento grid: one large card for the newest Memory with an image, then medium
 * and small alternating. Sizing is derived from position by [BentoSizing] rather than read
 * off a field, because `Memory` has no size and should not gain one.
 *
 * The search bar is a pill that navigates; search is its own destination. Events is reached
 * through the segmented group, not a top-bar icon — one affordance per action.
 */
@Composable
fun FeedScreen(
    onNavigateToComposer: () -> Unit,
    onNavigateToMemory: (Long) -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToSearch: () -> Unit,
    onNavigateToSection: (SectionDestination) -> Unit,
    viewModel: FeedViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Box(modifier = Modifier.fillMaxSize()) {
        PhoneFrame {
            HeroHeader(
                eyebrow = eyebrow(uiState.memories.size),
                title = "Everything you kept",
                trailing = {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, "Settings")
                    }
                }
            )

            SearchPill(
                onClick = onNavigateToSearch,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
            )

            SectionNav(
                selected = SectionDestination.FEED,
                onSelect = onNavigateToSection,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)
            )

            SourceFilterRow(
                options = uiState.availableSources,
                selectedFilter = uiState.sourceFilter,
                onFilterSelected = { viewModel.setSourceFilter(it) }
            )

            when {
                uiState.isLoading -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) { CircularProgressIndicator() }

                else -> {
                    val filtered = remember(uiState.memories, uiState.sourceFilter) {
                        filterMemories(uiState.memories, uiState.sourceFilter)
                    }
                    if (filtered.isEmpty()) {
                        EmptyState(
                            message = if (uiState.sourceFilter != null && uiState.memories.isNotEmpty()) {
                                "Nothing captured here yet."
                            } else {
                                "No memories yet — tap + to save your first."
                            }
                        )
                    } else {
                        BentoGrid(
                            memories = filtered,
                            onMemoryClick = { onNavigateToMemory(it.id) },
                            onMemoryLongClick = { viewModel.requestDelete(it) },
                            onRetryProcessing = { viewModel.retryProcessing(it) }
                        )
                    }
                }
            }
        }

        LargeFloatingActionButton(
            onClick = onNavigateToComposer,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp)
                .shadow(
                    elevation = 18.dp,
                    shape = MaterialTheme.shapes.extraLarge,
                    ambientColor = FabShadowColor,
                    spotColor = FabShadowColor
                ),
            containerColor = MaterialTheme.colorScheme.tertiary,
            contentColor = MaterialTheme.colorScheme.onTertiary,
            shape = MaterialTheme.shapes.extraLarge
        ) {
            Icon(Icons.Default.Add, "Create memory")
        }
    }

    uiState.memoryToDelete?.let {
        DeleteConfirmationDialog(
            onConfirm = { viewModel.confirmDelete() },
            onDismiss = { viewModel.dismissDelete() }
        )
    }
}

/** "10 memories · on device" — the count, and where they are. */
private fun eyebrow(count: Int): String {
    val noun = if (count == 1) "memory" else "memories"
    return "$count $noun · on device"
}

@Composable
private fun BentoGrid(
    memories: List<Memory>,
    onMemoryClick: (Memory) -> Unit,
    onMemoryLongClick: (Memory) -> Unit,
    onRetryProcessing: (Memory) -> Unit
) {
    val sizes = remember(memories) { BentoSizing.sizes(memories) }

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 96.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        itemsIndexed(
            items = memories,
            key = { _, memory -> memory.id },
            // The large card carries a 160 dp banner and needs the full width; the rest
            // take one column each.
            span = { index, _ ->
                if (sizes[index] == BentoSize.LARGE) GridItemSpan(2) else GridItemSpan(1)
            }
        ) { index, memory ->
            StaggeredEntrance(index = index) {
                BentoCard(
                    memory = memory,
                    size = sizes[index],
                    onClick = { onMemoryClick(memory) },
                    onLongClick = { onMemoryLongClick(memory) },
                    onRetryProcessing = { onRetryProcessing(memory) }
                )
            }
        }
    }
}

@Composable
private fun EmptyState(message: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 40.dp)
        )
    }
}

/**
 * A search affordance that is not a text field.
 *
 * Looks like the bar it replaces and behaves like a button. Search is its own destination
 * now, and a field here would put a second copy of the query state on a screen that no
 * longer owns any.
 */
@Composable
private fun SearchPill(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(64.dp),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "Ask for anything you saved…",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
internal fun DeleteConfirmationDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete memory?") },
        text = { Text("This memory and its contents will be permanently removed.") },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Delete", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

/**
 * Client-side filter over the already-loaded memories.
 *
 * Filtering happens client-side because the feed is already loaded into memory and is at
 * most a few thousand rows — querying the DB again for each filter change would thrash the
 * reactive Flow for no benefit.
 */
internal fun filterMemories(memories: List<Memory>, filter: SourceFilter?): List<Memory> {
    if (filter == null) return memories
    return memories.filter { memory ->
        memory.sourceType == filter.sourceType &&
            (filter.sourcePackage == null || memory.sourcePackage == filter.sourcePackage)
    }
}
```

- [ ] **Step 2: Delete the old card and restyle the filter chips**

```bash
git rm app/src/main/java/com/onemind/app/ui/feed/MemoryCard.kt
```

If anything still references `MemoryCard`, `assembleDebug` will say so — fix the reference
rather than restoring the file.

In `app/src/main/java/com/onemind/app/ui/feed/SourceFilterRow.kt`, change the `Row` to a
`LazyRow` (the reference scrolls this horizontally) and give each `FilterChip` a shape that
morphs with selection:

```kotlin
                shape = if (isSelected) PillShape else RoundedCornerShape(12.dp),
```

Nothing else in that file changes — the options, the counts and the callback are the
existing behaviour.

- [ ] **Step 3: Update the nav host**

Remove `onNavigateToEvents = …` from the `FeedScreen(...)` call. The `EVENTS` route stays
and is reached through `onNavigateToSection`.

- [ ] **Step 4: Run everything**

```bash
JAVA_HOME=/home/imyuvi/.local/jdks/jdk-17.0.20.1+1 ./gradlew assembleDebug testDebugUnitTest
JAVA_HOME=/home/imyuvi/.local/jdks/jdk-17.0.20.1+1 ./gradlew connectedDebugAndroidTest
```

Expected: BUILD SUCCESSFUL on both.

- [ ] **Step 5: Compare against the mock**

```bash
JAVA_HOME=/home/imyuvi/.local/jdks/jdk-17.0.20.1+1 ./gradlew installDebug
```

Open `/home/imyuvi/projects/codingagents/design-reference/index.html` in a browser beside
the running app. Check, in order: hero eyebrow and 42 sp title, the 64 dp search pill, the
connected three-segment group with Feed active, a horizontally scrolling chip row, one
large card with a banner followed by alternating medium and small, and the accent FAB with
its warm shadow. Note anything that differs; fidelity here is a comparison, not an
assertion.

- [ ] **Step 6: File Issue H, commit, close**

File with title **"Feed redesign"** and a body covering: the bento grid replacing a flat
`LazyColumn` of uniform cards; sizing derived from position rather than a field the model
does not have; `MemoryCard` deleted in favour of `BentoCard`, which uses the press morph
and the per-size asymmetric silhouettes; `LargeFloatingActionButton` in accent with the
`shadow-fab` colour, the one place the design uses a shadow at all; and Events losing its
top-bar icon because the segmented group already goes there.

Commit:

```bash
git add app/src/main/java/com/onemind/app/ui/feed/ \
        app/src/main/java/com/onemind/app/ui/navigation/OneMindNavHost.kt

git commit -F - <<'MSG'
feat(ui): rebuild the feed as the reference's bento grid (#H)

The feed was a flat LazyColumn of identical 12 dp cards under an empty top bar.
It is now a two-column bento grid under the hero: one large card spanning both
columns for the newest Memory with an image, then medium and small alternating,
each with its own asymmetric silhouette.

Sizing comes from BentoSizing, which reads position and not a field. index.html
sizes its mock cards from a `size` property; adding one to the real Memory would
put a layout decision in the domain model and make the processing pipeline
responsible for keeping it right.

MemoryCard is deleted. BentoCard replaces it, on a Surface rather than a Card
because the shape has to change between a size's rest silhouette and a uniform
morphing corner while pressed, and because CardDefaults elevation would add a
shadow the design does not use — cards read by tonal step here. The morph is
uniform on purpose: animating four corners independently dissolves the notch
into a generic rounded square at the exact moment the user is looking at it.

The FAB is a LargeFloatingActionButton in accent with the shadow-fab colour,
which is the only place in the whole design that uses a shadow at all.

Events lost its top-bar icon. The segmented group already goes there, and two
affordances for one action is one too many.
MSG
```

---

## Task 10: Reconcile the existing test suite

**Files:**
- Modify: any test that referenced removed API.

- [ ] **Step 1: Find and fix**

```bash
JAVA_HOME=/home/imyuvi/.local/jdks/jdk-17.0.20.1+1 ./gradlew testDebugUnitTest connectedDebugAndroidTest
```

Anything referencing `ViewMode`, `FeedUiState.searchQuery`, `FeedViewModel.onSearchQueryChanged`,
`MemoryCard` or `FeedScreen`'s old parameter list needs updating. Where a test was
asserting the *toggle* or the *inline search bar*, delete it and say so in the relevant
commit message — those behaviours are gone by design, and keeping a test alive for them
would keep the code alive too.

Where a test was asserting search *behaviour* (debounce, stopword handling, result
ordering), it moves to cover `SearchViewModel` instead. That behaviour did not change and
must stay covered.

- [ ] **Step 2: Confirm green**

```bash
JAVA_HOME=/home/imyuvi/.local/jdks/jdk-17.0.20.1+1 ./gradlew assembleDebug testDebugUnitTest
JAVA_HOME=/home/imyuvi/.local/jdks/jdk-17.0.20.1+1 ./gradlew connectedDebugAndroidTest
```

Fold any test fixes into whichever of the three commits caused them, rather than adding a
fourth "fix tests" commit.

---

## Done when

- [ ] Issues F, G and H filed, implemented, closed with AI-attributed comments.
- [ ] Three commits on `my-extra-work`, `(#N)` in each subject, no `Co-Authored-By` and no
      generated-by trailer.
- [ ] `assembleDebug`, `testDebugUnitTest` and the full instrumented suite green on the
      `onemind_test` AVD, with the 15 new JVM tests and the new instrumented ones.
- [ ] Manual: one press of back leaves the app after four section swaps; the feed keeps its
      scroll position across a swap; search opens from the pill and returns; the feed reads
      like `index.html`, the timeline like `timeline.html`, the search screen like
      `search.html`.
- [ ] `git diff --stat` shows nothing under `domain/`, `data/` or `capture/`.
- [ ] Nothing released.

## Deliberately not done here

- **Events still has no segmented group.** It is reachable from the group on Feed and
  Timeline but does not show one itself until the Events restyle in
  `2026-08-24-events-and-remaining-screens.md`. For one plan the group is inconsistent in
  that one direction; the alternative is restyling Events here and making this plan two
  slices too large.
- **No back-stack test.** Asserting depth needs a real graph with real screens, which needs
  a Hilt test runner this project does not have. Recorded as a manual step rather than
  covered by a test that only looks like it covers it.
- **`SourceFilterRow` keeps its behaviour.** Only the chip shape and the scroll direction
  change. Filtering remains client-side over the loaded list, for the reason its existing
  comment gives.
- **Search suggestions are static strings.** They are examples of what to type, not
  generated from the user's data — which would need a query nothing currently runs and
  would leak what is in the database into a screen shown before any authentication.
