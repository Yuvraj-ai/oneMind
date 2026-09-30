# Material 3 Expressive UI Redesign Specification

- **Date:** 2026-09-10
- **Status:** Approved
- **Scope:** Complete end-to-end UI redesign of oneMind using Material 3 Expressive specifications and tokens.
- **Authority:** `material-3-expressive` skill (supersedes legacy design-reference).

---

## 1. Executive Summary & Goals

This specification details the end-to-end redesign of **oneMind**, an on-device personal memory and knowledge capture Android application, to align with Google's **Material 3 Expressive** design system.

### Key Decisions
1. **Intensity Level:** **Excellent** — Elevates visual expression through rich container colors, expressive typography contrast, distinctive shape asymmetry, and deliberate hero moments, while strictly preserving familiar Android navigation patterns and 48dp minimum touch targets.
2. **Color Theming & Dynamic Color:** **Dynamic Color enabled by default** on Android 12+ (API 31+), extracting harmonious tonal palettes from the user's wallpaper. An upgraded, high-contrast **Expressive Ember** palette serves as the dark and light fallback when dynamic color is disabled or unavailable.
3. **Primary Hero Moments:** **Capture & Discovery** — Anchored by an **M3 Expressive Floating Action Dock** with a Hero Large FAB for instant memory creation, paired with an **Asymmetric Bento Grid** and connected filter chips on the Feed.
4. **Motion & Accessibility:** Physics-based spring animations for spatial morphs and press interactions, with strict enforcement of reduced-motion fallbacks (instant transitions when system animator duration scale is 0).
5. **Layer Discipline:** Strictly confined to the presentation layer (`app/src/main/java/com/onemind/app/ui/`). The data layer, domain models, database schemas, Room migrations, background workers, and ViewModel state contracts remain completely unchanged.

---

## 2. Foundations & Token Architecture (`ui/theme/`)

### 2.1 Dynamic Color & Expressive Ember Palette (`Colors.kt`, `OneMindTheme.kt`)
- `OneMindTheme(darkTheme: Boolean = isSystemInDarkTheme(), dynamicColor: Boolean = true, content: @Composable () -> Unit)`
- When `dynamicColor == true` and running on API 31+, Compose `dynamicDarkColorScheme(context)` or `dynamicLightColorScheme(context)` is applied.
- When `dynamicColor == false` or on API < 31, the fallback is **Expressive Ember**:
  - **Dark Scheme:**
    - `surface`: `#121316` (Deep Charcoal Obsidian)
    - `surfaceContainerLowest`: `#0D0E11`
    - `surfaceContainerLow`: `#18191E`
    - `surfaceContainer`: `#202228` (Standard cards and segmented containers)
    - `surfaceContainerHigh`: `#2A2C34` (Elevated cards and toolbars)
    - `surfaceContainerHighest`: `#353742` (Active pill backgrounds, badge anchors)
    - `primary`: `#FF7A45` (Vibrant Ember Orange)
    - `onPrimary`: `#481500`
    - `primaryContainer`: `#5C2008`
    - `onPrimaryContainer`: `#FFDBCF`
    - `secondaryContainer`: `#3E2723`
    - `onSecondaryContainer`: `#F5D6CB`
    - `tertiary`: `#F5A623` (Warm Amber / Ochre)
    - `tertiaryContainer`: `#3D2E14`
    - `onTertiaryContainer`: `#FFE099`
    - `outline`: `#8C8E99`
    - `outlineVariant`: `#44464F`
  - **Light Scheme:**
    - `surface`: `#FAF9F6` (Warm Porcelain)
    - `surfaceContainer`: `#F0ECE5`
    - `surfaceContainerHigh`: `#E6E1D9`
    - `primary`: `#A33E15` (Terracotta Ember)
    - `onPrimary`: `#FFFFFF`
    - `primaryContainer`: `#FFDBCF`
    - `onPrimaryContainer`: `#3B0900`
- Contrast Ratios: Validated ≥ 4.5:1 for normal text and ≥ 3:1 for graphical UI components.

### 2.2 Expressive Typography Scale (`Type.kt`)
- Full M3 Expressive type scale:
  - `displayLarge`: 57sp, line height 64sp, tracking -0.25sp
  - `headlineLarge`: 32sp, line height 40sp, bold
  - `headlineMedium`: 28sp, line height 36sp, semi-bold (Screen Hero Titles)
  - `titleLarge`: 22sp, line height 28sp, medium (Bento card titles, dialog headers)
  - `titleMedium`: 16sp, line height 24sp, semi-bold
  - `titleSmall`: 14sp, line height 20sp, medium (Segmented button labels)
  - `bodyLarge`: 16sp, line height 24sp (Note body content, composer editor)
  - `bodyMedium`: 14sp, line height 20sp (Metadata, secondary captions)
  - `bodySmall`: 12sp, line height 16sp (Timestamps, source labels)
  - `labelLarge`: 14sp, line height 20sp, semi-bold (Action buttons, chip labels)
  - `labelMedium`: 12sp, line height 16sp, medium (Status tags, pill badges)

### 2.3 Expressive Shapes Scale (`Shapes.kt`)
- Semantic corner radii:
  - `extraSmall`: 4dp (Status dots, indicators)
  - `small`: 8dp (Tags, subtle chips)
  - `medium`: 16dp (Standard memory cards, text input fields)
  - `large`: 24dp (Prominent containers, hero media frames)
  - `extraLarge`: 28dp (Dialogs, bottom sheets, search pill)
  - `full`: 100dp / CircleShape (Floating toolbar, FAB, pill chips)
  - `AsymmetricCardShape`: `RoundedCornerShape(topStart = 28.dp, topEnd = 16.dp, bottomEnd = 28.dp, bottomStart = 16.dp)` for top-emphasis visual memories.

### 2.4 Expressive Motion System (`Motion.kt`)
- **Spatial Spring:** `spring<Float>(dampingRatio = 0.75f, stiffness = 380f)` for layout changes, card expansions, dialog entrances.
- **Effects Spring:** `spring<Float>(dampingRatio = 0.85f, stiffness = 1500f)` for press scale feedback and icon morphs.
- **Reduced Motion Fallback:**
  - Helper `rememberReducedMotion()` reads system animation scale via `Settings.Global.ANIMATOR_DURATION_SCALE`.
  - When scale is `0f`, animations bypass spring physics and transition instantly using `snap()`.

---

## 3. Navigation Shell & Component System (`ui/components/`, `ui/navigation/`)

### 3.1 Expressive Top Bar (`ExpressiveTopBar.kt`)
- Replaces inconsistent top bars across screens.
- Supports centered and small variants with 64dp height.
- Styled with `surfaceContainer` background and subtle scroll elevation.
- Houses navigation icons (Back, Settings) rendered in 40dp pill icon containers (`ExpressiveIconButton`) with 48dp touch targets.

### 3.2 Connected Button Group (`SectionNav.kt`)
- Connects the three primary discovery views: `Feed`, `Timeline`, and `Events`.
- Built on `SingleChoiceSegmentedButtonRow(space = 4.dp)`.
- Selected segment: `primary` container with `onPrimary` text, animated pill indicator.
- Inactive segments: `surfaceContainer` background with `onSurfaceVariant` text.
- Minimum touch target: 48dp height.

### 3.3 M3 Expressive Floating Action Dock (`FloatingActionDock.kt`)
- Floating toolbar container positioned at the bottom center of `Feed` and `Timeline`.
- Height: 64dp, margin: 16dp, corner radius: 32dp (full pill).
- Container styling: `surfaceContainerHigh`, 3dp elevation, `outlineVariant` hairline border.
- Elements:
  - Quick action slot: Quick search button, filter toggle.
  - Hero Large FAB: 56dp primary button with `+` icon, spring press morph.
- Scroll behavior: Gently tucks or collapses to avoid obscuring cards during fast scroll.

### 3.4 Navigation Graph Integrity (`OneMindNavHost.kt`, `SectionNavigation.kt`)
- Single-top navigation between Feed, Timeline, and Events with state preservation (`popUpTo(FEED) { saveState = true }`, `launchSingleTop = true`, `restoreState = true`).
- Back button on all secondary destinations navigates back cleanly.

---

## 4. Destination & Screen Experiences

### 4.1 Feed Screen (`FeedScreen.kt`, `BentoCard.kt`, `SourceFilterRow.kt`)
- **Top:** `HeroHeader` with total memory count eyebrow and settings icon, followed by 28dp `SearchPill` that navigates directly to `NavRoutes.SEARCH`.
- **Navigation:** `SectionNav` segmented button group switching between Feed, Timeline, and Events.
- **Source Filtering:** Horizontal scrollable row of expressive `FilterChip` pills with `secondaryContainer` active state.
- **Bento Grid:** 2-column grid utilizing `BentoSizing`:
  - Newest media memory rendered with `AsymmetricCardShape` spanning 2 columns.
  - Alternating 1-column medium cards (16dp rounded corners) with press-scale interaction.
  - Expressive empty state card when no memories exist.
- **Bottom:** Floating Action Dock with Hero FAB.

### 4.2 Timeline Screen (`TimelineScreen.kt`, `DateGrouping.kt`)
- **Top:** `SectionNav` for swift tab switching.
- **Layout:** Vertical timeline with continuous connecting spine line (`outlineVariant`).
- **Date Anchors:** Sticky pill chips (`surfaceContainerHighest`) showing date headers (e.g. "Today", "Yesterday", "August 2026").
- **Cards:** Memory cards connected to the spine via subtle dot indicators, displaying thumbnail previews, note excerpts, and source badges.

### 4.3 Events Screen (`EventsScreen.kt`, `EventCardUi.kt`)
- **Top:** `SectionNav` and clean header.
- **Agenda Hierarchy:**
  - Hero Upcoming Event Card: Styled with `primaryContainer` and `onPrimaryContainer` typography for the next imminent event.
  - General Event List: `surfaceContainer` cards showing date, time, location, title, and link back to the originating Memory.

### 4.4 Search Screen (`SearchScreen.kt`, `SearchResultCard.kt`)
- **Header:** Full-width docked search bar with back navigation, real-time input, and clear icon.
- **Search Progress:** M3 Expressive linear progress indicator active during vector embedding searches.
- **Results:** Expressive cards showing match snippet, highlighted terms, and semantic match similarity chips.
- **Suggestions:** Expressive category pills ("Screenshots", "Recent Notes", "Extracted Events") when search query is blank.

### 4.5 Composer Screen (`ComposerScreen.kt`)
- **Header:** `ExpressiveTopBar` with Cancel and "Save" filled button.
- **Body:** Clean, distraction-free note editor with `bodyLarge` typography.
- **Tooling Pill:** Floating toolbar docked above the soft keyboard with image attachment, tag picker, and reminder shortcuts.

### 4.6 Memory Detail Screen (`MemoryDetailScreen.kt`)
- **Header:** `ExpressiveTopBar` with Back arrow, Share action, and overflow menu.
- **Hero Media:** Large rounded image frame (24dp corners) for visual memories.
- **Metadata Cards:** Grouped cards for OCR extracted text, AI summary, creation timestamp, and source links.
- **Bottom Action Dock:** Floating pill toolbar offering Edit, Share, and Delete actions with 28dp confirmation dialog.

### 4.7 Settings Screen (`SettingsScreen.kt`)
- **Grouped Preference Sections:** General, On-Device AI Models, Storage & Cache, About.
- **M3 Expressive Cards:** Grouped items with 16dp rounded corners, icon avatars, and switch controls.
- **Pill Row Links:** "Change local model" rendered as a distinct pill card.
- **Dialogs:** 28dp rounded corners for model selector and delete confirmation.

### 4.8 Onboarding Flow (`OnboardingScreen.kt` & sub-screens)
- All 5 onboarding steps (`WelcomeScreen`, `PermissionsScreen`, `ModelSelectionScreen`, `CloudConfigScreen`, `DownloadScreen`) wrapped in `PhoneFrame`.
- Hero headers, expressive badge icons, and high-contrast primary CTA buttons.
- Model download screen features an M3 Expressive progress indicator with bytes downloaded and percentage.

---

## 5. Verification & Delivery Plan

1. **Static Analysis & Compilation:**
   - Run compilation with Java 17: `JAVA_HOME=/home/imyuvi/.local/jdks/jdk-17.0.20.1+1 ./gradlew assembleDebug`.
   - Validates that Compose UI, Hilt dependency injection, and Room schemas compile cleanly.
2. **JVM Unit Tests:**
   - Execute the test suite: `JAVA_HOME=/home/imyuvi/.local/jdks/jdk-17.0.20.1+1 ./gradlew testDebugUnitTest`.
   - Target: 100% pass rate across all 676+ unit tests.
3. **Artifact Packaging & Delivery:**
   - Verify debug APK exists at `app/build/outputs/apk/debug/app-debug.apk`.
   - Copy APK to target release directory: `/home/imyuvi/onemind-releases/onemind-0.4.0-testing.apk`.
   - Generate SHA-256 checksum file: `/home/imyuvi/onemind-releases/onemind-0.4.0-testing.apk.sha256`.
