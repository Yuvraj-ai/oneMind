# Phase 3 Spec: Remaining Capture Paths + Browsing Views

**Status:** ready-for-agent  
**Blocked by:** Phase 2 (Processing Pipeline)

---

## Problem Statement

The user can only save memories by manually opening the app and typing/pasting into the composer. The most common real-world capture scenarios — screenshotting something interesting, sharing a link from Chrome, or quickly saving clipboard content — require leaving the app's manual flow. Without these, oneMind is a note-taking app, not a memory assistant. Additionally, memories have enriched metadata (timestamps, source info, categories) but no way to browse by those dimensions yet.

## Solution

Add the remaining capture paths (Screen Capture, Share/Import, Quick Settings Clipboard), which all feed into the same Memory Intake Pipeline built in Phase 1-2. Then expose chronological browsing and source filtering as additional ways to view the memory feed — leveraging metadata already being extracted.

## User Stories

### Screen Capture (M1)

1. As a user, I want to capture my current screen as a memory using Android's screenshot mechanism, so that I can save what I'm looking at without switching apps.
2. As a user, I want to trigger a capture via a Quick Settings tile, so that I have a fast one-tap action from anywhere.
3. As a user, I want the app to request screen capture permission clearly and only when I initiate it, so that I understand and control what's being recorded.
4. As a user, I want a confirmation notification after screen capture, so that I know the memory was saved.
5. As a user, I want screen captures to enter the same processing pipeline as manual memories, so that they get OCR, metadata, categories, and summaries automatically.
6. As a user, I do NOT want the app to continuously monitor my screen, so that my privacy is preserved.

### Share/Import (M2)

7. As a user, I want to share content from any app (Chrome, WhatsApp, Gallery, etc.) to oneMind via Android's Share menu, so that saving a memory is one tap from anywhere.
8. As a user sharing an image, I want it saved as a Memory with full image processing (OCR + vision), so that shared images are as rich as screenshots.
9. As a user sharing a URL, I want the link saved as structured metadata in a Memory, so that I can find it later.
10. As a user sharing text, I want it saved as a text Memory, so that copied passages from articles are captured.
11. As a user sharing multiple items (e.g. multiple images from Gallery), I want them saved as one Memory with multiple content blocks, so that related content stays together.
12. As a user, I want the app to support common MIME types (image/*, text/*, application/pdf, URLs), so that most share actions work.
13. As a user importing an existing screenshot from Gallery, I want it treated identically to a share, so that there's no separate "import" concept.

### Quick Settings Clipboard (M3 remaining)

14. As a user, I want a "Save Clipboard" Quick Settings tile, so that I can save whatever I last copied with one tap without opening the app.
15. As a user who taps "Save Clipboard", I want a notification confirming what was saved, so that I trust the action worked.
16. As a user, I do NOT want the app to automatically save every clipboard copy, so that only my explicit saves become memories.

### Chronological Browsing (M7)

17. As a user, I want to browse memories grouped by date (Today, Yesterday, This Week, Earlier), so that I can find things by when I saved them.
18. As a user, I want timestamps clearly visible on memory cards, so that temporal context is always available.
19. As a user, I want a chronological view accessible as a tab or view mode, so that I can switch between the main feed and time-based browsing.

### Source Filtering (M8)

20. As a user, I want to see which app a memory came from (Chrome, WhatsApp, etc.) when that information is available, so that I have context about the memory's origin.
21. As a user, I want to filter memories by source app, so that I can see "everything I saved from Chrome."
22. As a user, I do NOT want the app to show a source when it can't be reliably determined, so that I'm never misled about where something came from.
23. As a user, I want source displayed as an icon or label on memory cards, so that it's visible at a glance.

## Implementation Decisions

### M1: Screen Capture

- Use MediaProjection API for application-controlled capture.
- Expose capture via:
  - A Quick Settings tile (TileService) that triggers capture permission → screenshot → persist.
  - Potentially an app shortcut (future, not required for initial implementation).
- Flow: User taps QS tile → if no active MediaProjection session, request permission → capture screen → save bitmap → create Memory (source: SCREENSHOT) → notify → enqueue processing.
- MediaProjection session should be short-lived: capture one frame and release. Not a continuous recording.
- Handle Android 14+ restrictions on MediaProjection (foreground service requirement, user consent per session).
- Store source type as SCREENSHOT.

### M2: Share/Import

- Register intent filters in the manifest for supported MIME types:
  - `image/*` (JPEG, PNG, WebP)
  - `text/plain`
  - `text/*` (other text types)
  - `application/pdf` (basic support — extract text if feasible)
  - URL schemes (via text/plain containing URLs)
- Share receiver Activity: receives the intent, extracts content (text, URIs, streams), creates Memory content blocks, persists, shows brief confirmation, enqueues processing.
- Multiple items (ACTION_SEND_MULTIPLE): create one Memory with multiple content blocks.
- Source detection: read the calling package from the intent's referrer or `callingPackage`. If unavailable, source remains unknown (never fabricate).
- Store source type as SHARE + source package name where available.

### M3: Quick Settings Clipboard

- Implement a TileService for "Save Clipboard."
- On tap: read ClipboardManager's primary clip.
- Support clip types: plain text, HTML text, URI (treat as link).
- Create Memory with clipboard content, source type CLIPBOARD.
- Show notification: "Saved to Memory" with memory preview.
- Do NOT register a ClipboardManager listener. Only read clipboard on explicit tile tap.

### M7: Chronological Browsing

- Add a view mode or tab showing memories grouped by date sections.
- Grouping: Today, Yesterday, This Week, This Month, Older (or similar natural groups).
- Uses the existing `createdAt` timestamp — no new data required.
- Accessible from the main feed via tab/toggle.

### M8: Source Filtering

- Source metadata already extracted and stored (source type + package name) from Phase 1/2.
- Add a source filter accessible from the feed (filter chips, tab, or section).
- Show source app icon (resolve from package name) + app name.
- Group: "Chrome (12)", "WhatsApp (8)", "Screenshots (23)", "Manual (15)".
- If source is unknown, memories appear under "Unknown" or are simply unfiltered.
- Source info displayed on memory cards as a small icon.

### Notification System

- Capture confirmations use Android's standard notification system.
- Notification channel: "Memory Captures" (user can mute independently).
- Notification content: "Saved to Memory" + preview text or thumbnail.
- Tap notification → opens the newly created memory.

### Memory Intake Pipeline Integration

- All new capture paths (M1, M2, M3 clipboard) feed into the same MemoryRepository.create() path.
- They only differ in:
  - How content is obtained (MediaProjection / Intent / ClipboardManager)
  - What source metadata is attached
- Processing pipeline from Phase 2 handles everything downstream identically.

## Testing Decisions

### Seams to test

1. **Share receiver seam**: Given an incoming Intent with various MIME types → Memory created with correct content blocks and source metadata.
2. **MediaProjection capture seam**: Given permission granted → bitmap captured → Memory created with SCREENSHOT source. Given permission denied → graceful failure, no memory created.
3. **Clipboard capture seam**: Given clipboard with text/URI → Memory created with correct content. Given empty clipboard → user notified, no memory created.
4. **Chronological grouping seam**: Given memories with various timestamps → correct date-group assignment.
5. **Source filter seam**: Given memories with various sources → correct grouping and filtering.

### Testing approach

- Unit tests for intent parsing, clipboard reading logic, date grouping.
- Integration tests for full capture-to-persistence flow (mock MediaProjection, mock ClipboardManager).
- UI tests for chronological view rendering and source filter interaction.
- Edge case tests: empty clipboard, unsupported MIME type, permission denial.

## Out of Scope

- Search functionality (M12-15) — Phase 4
- Accessibility Service-based capture — explicitly excluded per architecture decisions
- Continuous screen monitoring — explicitly excluded
- Notification actions (Undo, Open from notification) — future polish
- PDF text extraction — best-effort in Phase 3, full PDF support is future
- App shortcuts beyond Quick Settings — future
- Custom gestures for capture — future

## Further Notes

- MediaProjection on Android 14+ requires a foreground service with type `mediaProjection`. This adds manifest permissions and a persistent notification during capture. Design the UX so the foreground service is extremely short-lived (capture → release within seconds).
- The Quick Settings tile for Screen Capture and the tile for Save Clipboard are two separate tiles. The user adds whichever they want to their QS panel.
- Source detection from Share intents is best-effort. On some Android versions/OEMs, `callingPackage` is null. Never fabricate a source — "Unknown" is an acceptable state.
- Chronological browsing and source filtering are relatively lightweight implementations — they're views over data that already exists. They should not require significant new data structures.
