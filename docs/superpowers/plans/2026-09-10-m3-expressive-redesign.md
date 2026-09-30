# Material 3 Expressive UI Redesign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Redesign oneMind's entire UI end-to-end to align with Google's Material 3 Expressive design language, dynamic color, expressive components, and verified testing APK delivery.

**Architecture:** Update foundational tokens in `ui/theme/` (dynamic color, Ember fallback, expressive shapes, typography, motion physics), construct shared M3 Expressive components (`ExpressiveTopBar`, `SectionNav`, `FloatingActionDock`), and redesign all 8 destination and secondary screens while preserving standard navigation patterns, ViewModel contracts, and data-layer boundaries.

**Tech Stack:** Jetpack Compose, Material 3, Navigation Compose, Hilt, Kotlin Coroutines, Gradle (AGP 8.13.2, minSdk 30, compileSdk 36).

**Spec:** `docs/superpowers/specs/2026-09-10-m3-expressive-redesign-design.md`

## Global Constraints

- Layer discipline: Strictly modify presentation-layer code under `app/src/main/java/com/onemind/app/ui/`. Never modify `domain/`, `data/`, `capture/`, Room entities, schemas, or migrations.
- ViewModel state contracts: Do not alter existing ViewModel state types or method signatures.
- Build environment: All Gradle commands require `JAVA_HOME=/home/imyuvi/.local/jdks/jdk-17.0.20.1+1`. Run from `/home/imyuvi/projects/codingagents/oneMind/`.
- Git rules: Do not commit, bump versions in `build.gradle.kts`, or create git tags without explicit user direction.
- Testing APK delivery: Export debug APK to `/home/imyuvi/onemind-releases/onemind-0.4.0-testing.apk` with `.sha256`.

---

### Task 1: Theme Foundations & Expressive Tokens

**Files:**
- Modify: `app/src/main/java/com/onemind/app/ui/theme/Colors.kt`
- Modify: `app/src/main/java/com/onemind/app/ui/theme/Shapes.kt`
- Modify: `app/src/main/java/com/onemind/app/ui/theme/Type.kt`
- Modify: `app/src/main/java/com/onemind/app/ui/theme/Motion.kt`
- Modify: `app/src/main/java/com/onemind/app/ui/theme/OneMindTheme.kt`

**Interfaces:**
- Consumes: Compose Material 3 `ColorScheme`, `Shapes`, `Typography`.
- Produces:
  - `OneMindTheme(darkTheme: Boolean = true, dynamicColor: Boolean = true, content: @Composable () -> Unit)`
  - `EmberDarkColorScheme`, `EmberLightColorScheme` with all container roles.
  - `OneMindShapes` with `AsymmetricCardShape` and standard corner radii (4dp, 8dp, 16dp, 24dp, 28dp, 100dp).
  - `OneMindTypography` with expressive sizes and weights.
  - `SpatialSpring`, `EffectsSpring`, and `rememberReducedMotion()`.

- [ ] **Step 1: Update Colors.kt with Expressive M3 Container Roles**
Define complete dark and light palettes with `surfaceContainerLowest`, `surfaceContainerLow`, `surfaceContainer`, `surfaceContainerHigh`, `surfaceContainerHighest`, `primaryContainer`, `onPrimaryContainer`, and `outlineVariant`.

- [ ] **Step 2: Update Shapes.kt with Expressive Corner Scales & Asymmetric Card Shape**
Add `AsymmetricCardShape = RoundedCornerShape(topStart = 28.dp, topEnd = 16.dp, bottomEnd = 28.dp, bottomStart = 16.dp)` and configure `OneMindShapes`.

- [ ] **Step 3: Update Type.kt with Expressive Typography Scale**
Configure `displayLarge`, `headlineMedium` (28sp), `titleLarge` (22sp), `bodyLarge` (16sp), `labelLarge` (14sp) with appropriate line heights and weights.

- [ ] **Step 4: Update Motion.kt with Physics Springs & Reduced Motion Helper**
Add `SpatialSpring`, `EffectsSpring`, and `isReducedMotionEnabled()` checking `Settings.Global.ANIMATOR_DURATION_SCALE`.

- [ ] **Step 5: Update OneMindTheme.kt to Enable Dynamic Color by Default**
Set `dynamicColor: Boolean = true` on API 31+ using `dynamicDarkColorScheme`/`dynamicLightColorScheme`, falling back to `EmberDarkColorScheme`/`EmberLightColorScheme`.

- [ ] **Step 6: Verify Compilation**
Run: `JAVA_HOME=/home/imyuvi/.local/jdks/jdk-17.0.20.1+1 ./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL

---

### Task 2: Shared Expressive Components & Navigation Dock

**Files:**
- Create: `app/src/main/java/com/onemind/app/ui/components/FloatingActionDock.kt`
- Modify: `app/src/main/java/com/onemind/app/ui/components/ExpressiveTopBar.kt`
- Modify: `app/src/main/java/com/onemind/app/ui/components/SectionNav.kt`
- Modify: `app/src/main/java/com/onemind/app/ui/components/HeroHeader.kt`

**Interfaces:**
- Consumes: `OneMindTheme`, `SpatialSpring`, `EffectsSpring`.
- Produces:
  - `FloatingActionDock(onFabClick: () -> Unit, onSearchClick: () -> Unit, modifier: Modifier)`
  - `ExpressiveTopBar(title: String, onNavigateBack: (() -> Unit)?, actions: @Composable RowScope.() -> Unit)`
  - `SectionNav(selected: SectionDestination, onSelect: (SectionDestination) -> Unit)`

- [ ] **Step 1: Create FloatingActionDock.kt**
Implement an M3 Expressive floating toolbar: 64dp height, 32dp pill corners, `surfaceContainerHigh` background, subtle elevation and border, with a secondary search/filter button and a 56dp Hero capture FAB (`primary` container, `+` icon, spring press feedback).

- [ ] **Step 2: Polish ExpressiveTopBar.kt**
Update `ExpressiveTopBar` and `ExpressiveIconButton` with 40dp pill buttons, 48dp touch targets, `surfaceContainer` background, and support for center-aligned and small top bars.

- [ ] **Step 3: Polish SectionNav.kt**
Configure `SingleChoiceSegmentedButtonRow(space = 4.dp)` with animated pill selection indicator (`primary` container on active segment, `surfaceContainer` on inactive), 48dp tap target.

- [ ] **Step 4: Polish HeroHeader.kt**
Refine `HeroHeader` to use `headlineMedium` (28sp semi-bold) for the title and `labelMedium` for the eyebrow, with tonal container pill badges.

- [ ] **Step 5: Verify Compilation**
Run: `JAVA_HOME=/home/imyuvi/.local/jdks/jdk-17.0.20.1+1 ./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL

---

### Task 3: Feed Screen & Bento Grid Redesign

**Files:**
- Modify: `app/src/main/java/com/onemind/app/ui/feed/FeedScreen.kt`
- Modify: `app/src/main/java/com/onemind/app/ui/feed/BentoCard.kt`
- Modify: `app/src/main/java/com/onemind/app/ui/feed/SourceFilterRow.kt`

**Interfaces:**
- Consumes: `FeedViewModel`, `FeedUiState`, `FloatingActionDock`, `BentoCard`, `AsymmetricCardShape`.
- Produces: Full M3 Expressive Feed screen with search pill, segmented section nav, source filter chips, bento grid, and floating action dock.

- [ ] **Step 1: Redesign BentoCard.kt**
Apply `AsymmetricCardShape` for the primary featured card (28dp/16dp corners) with gradient scrim and relative date badge. Apply 16dp corners for medium cards. Integrate `pressScale` spring feedback.

- [ ] **Step 2: Polish SourceFilterRow.kt**
Use M3 `FilterChip` styling with `secondaryContainer` for active selection, 100dp pill corners, and smooth horizontal scroll.

- [ ] **Step 3: Integrate FloatingActionDock into FeedScreen.kt**
Replace bottom FAB with `FloatingActionDock` positioned at the bottom center with 16dp padding. Ensure the LazyVerticalGrid has adequate bottom content padding (88dp) to prevent toolbar overlap.

- [ ] **Step 4: Polish SearchPill and Empty State**
Style `SearchPill` as an expressive 28dp pill container with `surfaceContainerHigh` background and search icon. Style `EmptyState` with warm tonal card and primary CTA.

- [ ] **Step 5: Verify Compilation**
Run: `JAVA_HOME=/home/imyuvi/.local/jdks/jdk-17.0.20.1+1 ./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL

---

### Task 4: Timeline & Events Screens Redesign

**Files:**
- Modify: `app/src/main/java/com/onemind/app/ui/feed/TimelineScreen.kt`
- Modify: `app/src/main/java/com/onemind/app/ui/feed/DateGrouping.kt`
- Modify: `app/src/main/java/com/onemind/app/ui/events/EventsScreen.kt`
- Modify: `app/src/main/java/com/onemind/app/ui/events/EventCardUi.kt`

**Interfaces:**
- Consumes: `TimelineScreen`, `EventsViewModel`, `EventCardUi`, `SectionNav`.
- Produces: M3 Expressive chronological timeline with vertical spine and agenda event cards.

- [ ] **Step 1: Redesign TimelineScreen.kt & DateGrouping.kt**
Render continuous vertical line spine (`outlineVariant`). Anchor groups with sticky date badges (`surfaceContainerHighest`, 12dp rounded corners). Wrap memory items in 16dp `surfaceContainer` cards connected to the spine.

- [ ] **Step 2: Add FloatingActionDock to TimelineScreen.kt**
Anchor `FloatingActionDock` at the bottom of the timeline with quick capture FAB.

- [ ] **Step 3: Redesign EventCardUi.kt & EventsScreen.kt**
Style the nearest upcoming event with a `primaryContainer` Hero card and calendar badge. Style regular agenda items in 16dp `surfaceContainer` cards with time pill, location, and memory source link.

- [ ] **Step 4: Verify Compilation**
Run: `JAVA_HOME=/home/imyuvi/.local/jdks/jdk-17.0.20.1+1 ./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL

---

### Task 5: Search Screen Redesign

**Files:**
- Modify: `app/src/main/java/com/onemind/app/ui/search/SearchScreen.kt`
- Modify: `app/src/main/java/com/onemind/app/ui/search/SearchResultCard.kt`

**Interfaces:**
- Consumes: `SearchViewModel`, `SearchUiState`.
- Produces: M3 Expressive search surface with docked search bar, vector search progress indicator, and rich result cards.

- [ ] **Step 1: Redesign SearchScreen.kt Header & Suggestion Chips**
Create a full-width docked M3 Expressive search bar (28dp corners, `surfaceContainerHigh`) with leading back button, search input field, and clear icon. Display quick suggestion chips ("Screenshots", "Recent Notes", "Links") when query is empty.

- [ ] **Step 2: Add Linear Progress Indicator for Semantic Search**
Show an M3 linear progress bar below the search bar when vector embedding search is executing.

- [ ] **Step 3: Redesign SearchResultCard.kt**
Style search result cards in `surfaceContainer` with 16dp corners, highlighted query text spans, and semantic match score pill chips.

- [ ] **Step 4: Verify Compilation**
Run: `JAVA_HOME=/home/imyuvi/.local/jdks/jdk-17.0.20.1+1 ./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL

---

### Task 6: Composer & Memory Detail Screens Redesign

**Files:**
- Modify: `app/src/main/java/com/onemind/app/ui/composer/ComposerScreen.kt`
- Modify: `app/src/main/java/com/onemind/app/ui/feed/MemoryDetailScreen.kt`

**Interfaces:**
- Consumes: `ComposerViewModel`, `MemoryDetailViewModel`.
- Produces: Frictionless expressive capture canvas and detail inspection surface.

- [ ] **Step 1: Redesign ComposerScreen.kt**
Install `ExpressiveTopBar` with Back/Cancel and "Save" filled button in the action slot. Provide full-bleed note canvas with `bodyLarge` typography and floating accessory pill above the keyboard for attachment actions.

- [ ] **Step 2: Redesign MemoryDetailScreen.kt**
Install `ExpressiveTopBar` with Back and Share actions. Display hero media with 24dp rounded corners. Group OCR text, AI summaries, and timestamps into `surfaceContainer` cards.

- [ ] **Step 3: Install Floating Action Pill & 28dp Dialog on Memory Detail**
Add floating pill toolbar at bottom with Edit (filled icon), Share (tonal icon), and Delete (standard icon with error tint). Ensure delete confirmation dialog uses 28dp rounded corners.

- [ ] **Step 4: Verify Compilation**
Run: `JAVA_HOME=/home/imyuvi/.local/jdks/jdk-17.0.20.1+1 ./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL

---

### Task 7: Settings & Onboarding Screens Redesign

**Files:**
- Modify: `app/src/main/java/com/onemind/app/ui/settings/SettingsScreen.kt`
- Modify: `app/src/main/java/com/onemind/app/ui/onboarding/WelcomeScreen.kt`
- Modify: `app/src/main/java/com/onemind/app/ui/onboarding/PermissionsScreen.kt`
- Modify: `app/src/main/java/com/onemind/app/ui/onboarding/ModelSelectionScreen.kt`
- Modify: `app/src/main/java/com/onemind/app/ui/onboarding/CloudConfigScreen.kt`
- Modify: `app/src/main/java/com/onemind/app/ui/onboarding/DownloadScreen.kt`

**Interfaces:**
- Consumes: `SettingsViewModel`, `OnboardingViewModel`.
- Produces: Polished settings preference groups and phone-framed onboarding flow.

- [ ] **Step 1: Polish SettingsScreen.kt**
Group preferences into 16dp rounded `surfaceContainer` cards. Style "Change local model" as a `.row-link` pill card. Update all dialogs (`ModelPickerDialog`, clear database) to 28dp rounded corners.

- [ ] **Step 2: Polish Onboarding Screens**
Ensure all 5 onboarding steps are wrapped in `PhoneFrame` with `HeroHeader`, circular badge icons with `primaryContainer` backgrounds, and filled primary buttons.

- [ ] **Step 3: Redesign DownloadScreen.kt Progress Display**
Replace indeterminate indicators with M3 linear/wavy loading indicators showing bytes downloaded and percentage.

- [ ] **Step 4: Verify Compilation**
Run: `JAVA_HOME=/home/imyuvi/.local/jdks/jdk-17.0.20.1+1 ./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL

---

### Task 8: End-to-End Verification & Testing APK Delivery

**Files:**
- Output: `/home/imyuvi/onemind-releases/onemind-0.4.0-testing.apk`
- Output: `/home/imyuvi/onemind-releases/onemind-0.4.0-testing.apk.sha256`

- [ ] **Step 1: Execute JVM Unit Tests**
Run: `JAVA_HOME=/home/imyuvi/.local/jdks/jdk-17.0.20.1+1 ./gradlew testDebugUnitTest`
Expected: All 676+ tests pass with 0 failures.

- [ ] **Step 2: Assemble Debug APK to Verify Hilt DI & Bytecode**
Run: `JAVA_HOME=/home/imyuvi/.local/jdks/jdk-17.0.20.1+1 ./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL, APK produced at `app/build/outputs/apk/debug/app-debug.apk`.

- [ ] **Step 3: Export Testing APK and Compute Checksum**
Copy `app/build/outputs/apk/debug/app-debug.apk` to `/home/imyuvi/onemind-releases/onemind-0.4.0-testing.apk`.
Generate checksum: `sha256sum /home/imyuvi/onemind-releases/onemind-0.4.0-testing.apk > /home/imyuvi/onemind-releases/onemind-0.4.0-testing.apk.sha256`.

- [ ] **Step 4: Verify APK Deliverable**
Verify file size (>150MB all-ABI debug APK) and checksum match.
