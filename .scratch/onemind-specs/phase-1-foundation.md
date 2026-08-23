# Phase 1 Spec: Foundation — Storage + LLM Infrastructure + Basic Capture

**Status:** ready-for-agent

---

## Problem Statement

A user wants to save things they encounter on their phone (text, links, images) so they can find them later. Today they screenshot, copy to notes, or bookmark — then forget where they put it. There is no single, local-first system that captures, persists, and enriches personal information on-device without requiring cloud accounts or surrendering data sovereignty.

## Solution

Build the foundational layer of oneMind: a local persistence system for Memories, a pluggable LLM infrastructure (local models by default), the simplest capture path (manual composer), and a basic feed to view saved Memories. This gives a complete end-to-end loop — the user can save something, see it, and the system is ready to enrich it.

## User Stories

1. As a user launching oneMind for the first time, I want to choose my AI model from a curated list of local options, so that I control what runs on my device.
2. As a user on first launch, I want to see model sizes and understand what I'm downloading, so that I can make an informed choice based on my device and network.
3. As a user, I want the model download to show progress, so that I know how long to wait.
4. As a user, I want to skip local model selection and configure a cloud provider instead, so that I can use more powerful models if I choose.
5. As a user, I want to open the app and immediately see a search bar and my saved memories, so that finding things is the primary experience.
6. As a user, I want to tap a "+" button to open a memory composer, so that I can manually save text, links, or images.
7. As a user composing a memory, I want my content auto-saved after ~3 seconds of inactivity, so that I never lose what I'm typing without needing a save button.
8. As a user, I want processing to begin only when I leave the composer, so that background work doesn't interfere with my editing.
9. As a user, I want to paste clipboard content into the composer, so that I can save copied text or links.
10. As a user, I want to add one or more images to a memory alongside text, so that a single Memory can be multimodal.
11. As a user, I want to see my saved memories in a scrollable feed, so that I can browse what I've captured.
12. As a user, I want each memory card to show a preview (text snippet, image thumbnail, or both), so that I can visually identify memories.
13. As a user, I want to tap a memory to open and view its full content, so that I can read everything I saved.
14. As a user, I want to edit an existing memory by opening it and adding more content, so that I can append information without creating duplicates.
15. As a user, I want the app to work completely offline after the initial model download, so that my memories are never dependent on internet connectivity.
16. As a user, I want my memories stored entirely on my device, so that no third party has access to my personal information.
17. As a user, I want the LLM provider to be changeable later in settings, so that I'm not locked into my first-launch choice.
18. As a user, I want to see memory creation timestamps, so that I have temporal context for what I saved.
19. As a user, I want the app to acknowledge when a memory is saved (visual confirmation), so that I trust the capture succeeded.
20. As a user, I want the memory feed sorted by most recent first, so that I see my latest captures at the top.
21. As a user, I want images stored in an optimized format with thumbnails, so that storage usage stays reasonable.
22. As a user, I want the composer to support both plain text and URLs (detected and displayed as links), so that link content is identifiable.
23. As a user, I want the app to feel fast — capture and feed loading should not block on AI processing, so that the app is responsive regardless of background work.
24. As a user with a 6GB RAM device, I want the local model to run without crashing or freezing the phone, so that AI enrichment doesn't degrade my device experience.
25. As a user, I want to delete a memory, so that I can remove things I no longer need.

## Implementation Decisions

### Core Memory Data Model

- A Memory is a single persisted entity containing: an ID, creation timestamp, update timestamp, a list of content blocks (text, image, URL), source type (MANUAL for Phase 1), and a processing state enum.
- Content blocks are ordered and typed. A Memory can contain multiple blocks of different types.
- The Memory data model must be extensible for future fields (OCR results, embeddings, categories, summaries) without schema-breaking migrations.
- Use Room (SQLite) for structured persistence. Images stored as files on internal storage, referenced by path in the database.

### Memory States

- The processing state machine: DRAFT → SAVED → PROCESSING → READY / FAILED
- On edit of a READY memory: READY → EDITED → PROCESSING → READY
- Phase 1 will transition to SAVED and stay there (processing pipeline is Phase 2). The state field exists but no processing runs yet.

### LLM Provider Infrastructure

- Define a provider interface that abstracts: model loading, text generation, capability reporting (supports vision? supports embeddings?).
- Implement a LocalModelProvider using an on-device inference runtime (ONNX Runtime Mobile, llama.cpp via JNI, or MediaPipe LLM Inference — exact runtime is an engineering choice).
- Implement a CloudModelProvider stub that accepts API key + endpoint configuration (OpenAI-compatible API format as baseline).
- Model registry: a hardcoded list of 6 approved local models (2x 1B, 2x 1.5B, 2x 2B) with metadata (name, parameter count, download size, download URL, quantization format).
- Model download manager: download with progress reporting, resume capability, integrity verification.

### Embedding Model

- A separate, team-selected embedding model (small, efficient, ~30-50MB).
- Loaded alongside the generative model but managed independently.
- Produces fixed-dimension vectors (e.g. 384-dim) for later vector search.
- In Phase 1, the embedding model is downloaded and ready but not yet wired into the processing pipeline (that's Phase 2).

### First-Launch Flow

- On first launch: onboarding screen explaining what oneMind does (1-2 screens max).
- Model selection screen: show the 6 local model options with name, size, and a "recommended" badge on the best size for the device's RAM.
- Alternative path: "Use a cloud provider instead" link → settings screen for API key entry.
- Model download screen with progress bar, estimated time, cancel option.
- After download completes → land on the main memory feed (empty state with prompt to save first memory).

### Manual Composer (M3 partial)

- Accessible via a FAB (+) on the main feed.
- Supports: text input, image attachment (from gallery picker), paste from clipboard.
- Auto-save: debounce timer (~3 seconds inactivity) persists current state as DRAFT.
- On back-navigation / leaving composer: transition to SAVED.
- No explicit save button.
- Editing: tapping an existing memory opens the same composer pre-filled. Edits update the same Memory entity.

### Memory Feed (M6 shell)

- Main screen: search bar at top (non-functional in Phase 1 — placeholder), memory list below.
- Memories displayed as cards: thumbnail (if image), text snippet (truncated), timestamp.
- Sorted by most recent first (reverse chronological).
- Tap to open full memory view.
- Long-press or swipe for delete action.
- Empty state: friendly message + prompt to create first memory.

### Image Handling

- Images picked via Android's photo picker or system gallery intent.
- On save: generate a canonical compressed image (WebP, reasonable resolution cap) + thumbnail.
- Store images in app-internal storage. Database stores file path references.
- Original temp file cleaned up after optimization.

### Architecture

- MVVM with Hilt for DI.
- Repository pattern: MemoryRepository abstracts Room + file storage.
- ViewModels expose StateFlow for reactive UI.
- Coroutines for all async work.
- Navigation: Jetpack Compose Navigation (single activity).

## Testing Decisions

### What makes a good test here

Tests verify behavior through the public interface of each module. They should survive internal refactoring. Test names use domain vocabulary from CONTEXT.md.

### Seams to test

1. **Memory Intake seam**: Given content input (text, image, mixed) → MemoryRepository produces a correctly persisted Memory with correct state, content blocks, and timestamps.
2. **LLM Provider interface seam**: Given a loaded model and a prompt → provider returns generated text. Given an unsupported capability request → provider reports NOT_SUPPORTED.
3. **Composer auto-save seam**: Given user input followed by inactivity → Memory transitions to DRAFT. Given back-navigation → Memory transitions to SAVED.
4. **Model download seam**: Given a model selection → download manager reports progress, handles interruption, verifies integrity on completion.

### Testing approach

- Unit tests for Repository, Provider interface, state machine transitions.
- Integration tests for Room database operations + file storage.
- UI tests (Compose testing) for composer auto-save timing and feed rendering.
- No end-to-end tests against real LLM inference in CI (too slow/heavy). Mock the provider interface.

## Out of Scope

- Screen capture (M1) — requires MediaProjection, Phase 3
- Share/Import (M2) — requires intent filters, Phase 3
- Quick Settings clipboard tile — Phase 3
- OCR / Vision processing (M4) — Phase 2
- Metadata extraction (M5) — Phase 2
- Embedding generation and vector indexing — Phase 2 (model downloaded but not wired)
- Categorization (M10) — Phase 2
- Summarization (M11) — Phase 2
- Search functionality (M12-15) — Phase 4
- Chronological browsing UI (M7) — Phase 3
- Source filtering (M8) — Phase 3
- Backup / export
- User-created collections
- Any cloud sync
- Notifications

## Further Notes

- The embedding model should be downloaded alongside the generative model during first-launch. Both are needed for Phase 2, and downloading them together avoids a second download-and-wait experience later.
- The processing pipeline orchestrator does not need to exist yet, but the Memory state machine should already have the PROCESSING and READY states defined — Phase 2 will activate them.
- The model registry (list of 6 models) should be updatable via a simple config/JSON update mechanism in future, but for v1 it can be hardcoded.
- Image optimization parameters (resolution cap, WebP quality, thumbnail size) should be constants that can be tuned later, not buried in logic.
- The search bar in the feed is visible but non-functional in Phase 1. It establishes the search-first UX pattern from day one.
