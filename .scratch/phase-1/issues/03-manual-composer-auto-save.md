# 03: Manual Composer with auto-save

**GitHub Issue:** https://github.com/Yuvraj-ai/oneMind/issues/3

**What to build:** The Memory Composer — text input, image attachment, clipboard paste, auto-save after ~3s inactivity, no save button. First end-to-end user flow: create → see in feed → reopen → edit.

**Blocked by:** #1 (Project scaffold), #2 (Memory Feed UI)

**Status:** ready-for-agent

- [ ] FAB (+) on feed navigates to composer screen
- [ ] Composer supports text input (multiline, no character limit)
- [ ] Composer supports pasting from clipboard (text, URLs)
- [ ] Composer supports attaching images via Android photo picker
- [ ] Multiple content blocks can coexist in one Memory
- [ ] Auto-save: after ~3 seconds of inactivity, persisted as DRAFT
- [ ] No explicit Save button
- [ ] On leaving composer: state transitions DRAFT → SAVED
- [ ] Visual confirmation when memory is saved
- [ ] Existing memory opens in same composer pre-filled
- [ ] Edits update same entity (no duplicates)
- [ ] On edit of READY memory: state → EDITED
- [ ] URLs in text visually distinguished
- [ ] Handles configuration changes without losing content
- [ ] Content blocks ordered as user added them
