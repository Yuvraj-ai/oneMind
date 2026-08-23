# 02: Memory Feed UI (empty + populated states)

**GitHub Issue:** https://github.com/Yuvraj-ai/oneMind/issues/2

**What to build:** The main screen with a search bar placeholder, a scrollable list of Memory cards (thumbnail, text snippet, timestamp), empty state prompt, tap-to-open full memory view, and delete action.

**Blocked by:** #1 (Project scaffold + Memory data model)

**Status:** ready-for-agent

- [ ] Main screen renders with a search bar at top (non-functional placeholder)
- [ ] Memory list loads from MemoryRepository via ViewModel + StateFlow
- [ ] Memory cards display: text snippet (truncated ~2 lines), image thumbnail, creation timestamp
- [ ] Feed sorted reverse-chronological (most recent first)
- [ ] Empty state: friendly message + visual prompt to create first memory
- [ ] FAB (+) button visible at bottom-right (navigates to composer)
- [ ] Tap memory card → navigates to full memory detail view
- [ ] Long-press or swipe → delete action with confirmation
- [ ] Delete removes memory from database + associated image files
- [ ] Feed updates reactively (Flow/StateFlow collection)
- [ ] UI follows Material 3 design language
- [ ] Loading state shown while memories are being fetched
