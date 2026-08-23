# 01: Project scaffold + Memory data model

**GitHub Issue:** https://github.com/Yuvraj-ai/oneMind/issues/1

**What to build:** A compilable Android project (Kotlin, Compose, Hilt, Room, Coroutines) with the core Memory entity, content blocks, state enum, and MemoryRepository that can create/read/update/delete memories.

**Blocked by:** None (can start immediately).

**Status:** ready-for-agent

- [ ] Android project compiles: Kotlin, Jetpack Compose, Hilt, Room, Coroutines, single-activity architecture
- [ ] Memory entity defined in Room: ID, createdAt, updatedAt, sourceType enum (MANUAL for now), processingState enum (DRAFT, SAVED, PROCESSING, READY, EDITED, FAILED)
- [ ] ContentBlock entity: ordered list per Memory, typed (TEXT, IMAGE, URL), content field, optional metadata
- [ ] MemoryRepository interface + Room implementation: create, read (single + list), update, delete
- [ ] Image file storage utility: save to internal storage, return path, delete
- [ ] State machine enforced: only valid transitions allowed
- [ ] Unit tests for Repository CRUD operations
- [ ] Unit tests for state machine transitions (valid + invalid)
- [ ] Integration tests for Room + file storage working together
- [ ] Hilt modules wired: database, repository, file storage all injectable
- [ ] Navigation shell: single NavHost with placeholder destinations (Feed, Composer, Settings, Onboarding)
