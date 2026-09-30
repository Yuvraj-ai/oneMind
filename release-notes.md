## What's new

- **Material 3 Expressive UI Redesign:** Comprehensive visual and architectural upgrade adhering to Google's Material 3 Expressive design language. Dynamic Color is enabled by default on Android 12+ with an enhanced, high-contrast Expressive Ember dark/light fallback palette.
- **Two-Column Staggered Feed:** Replaced the previous single large banner layout with an organic 2-column staggered waterfall grid (`LazyVerticalStaggeredGrid`). All memories are displayed in long rectangular cards with 28dp expressive rounded corners.
- **Full-Bleed Photo Memories:** Memory cards with images are completely covered by the photo (`ContentScale.Crop`) with a protective top gradient scrim, featuring the upload timestamp and bold memory title in high-contrast foreground typography.
- **Tonal Text Memories:** Text memories feature clean Material 3 surface containers displaying the upload timestamp, bold title, and truncated summary below.
- **Expressive Pill Section Navigation:** Redesigned `SectionNav` using an expressive pill capsule container with animated selection pill and fluid motion springs.
- **Floating Action Dock:** Floating toolbar docked at the bottom of the feed and timeline pairing a 56dp Hero capture FAB with quick search and filter affordances.
- **Timeline & Events:** Continuous vertical spine rail with sticky date badges; Events features an elevated Hero Upcoming Event Card and agenda list.
- **Docked Search & Composer:** Docked 28dp pill search bar with vector search progress indicator; clean note editor with floating accessory toolbar above the keyboard.
- **Settings & Onboarding:** Preference cards grouped in 16dp rounded containers, expressive row links, 28dp dialogs, and M3 progress indicators during downloads.

## Fixed

- Removed dark orange/brown header gradients in favor of standard Material 3 tonal elevation and surface roles.
- Removed redundant search bar on the feed in favor of the floating action dock search button.
- Memories saved by leaving the composer with the back gesture are now committed reliably however you leave the screen.
- Memory Detail delete confirmation dialog cleanly removes the memory and its associated images.

## Upgrading

- No database migration in this release — your saved memories and settings are preserved intact.

## Verify your download

sha256: 0d1521dbdc761cf4c4ef3b1ed6c2d9cd54943bcd77172433e21e80eb1f5b0650
