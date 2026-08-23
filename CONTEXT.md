# oneMind — Domain Glossary

> A native, local-first personal memory assistant for Android. The user explicitly gives the application something worth remembering; the application preserves it, enriches it asynchronously, and makes it retrievable later.

---

## Core Concepts

### Memory

One atomic unit of saved information. A Memory is created by a single capture event and may contain multiple modalities (text, image, URLs) within it. A Memory can be edited/appended to after creation — it remains the same Memory. There is no concept of merging or splitting Memories.

### Capture

The explicit, user-initiated act of giving content to oneMind. Capture is always intentional — the app never passively observes the device. Three ingestion paths exist: Screen Capture (M1), Share/Import (M2), and Clipboard/Manual Composer (M3).

### Memory Intake Pipeline

The common architecture that all ingestion paths feed into. Source-specific logic concerns how content enters the app; downstream processing operates on a unified Memory representation.

### Processing Pipeline

The asynchronous background system that enriches a Memory after capture. Processing never blocks capture. It includes OCR, vision analysis, metadata extraction, embedding generation, categorization, and summarization.

### Source Content

The original user-provided or captured material: text, images, screenshots, links. Authoritative and immutable (except by user edit). Never replaced or overwritten by derived data.

### Derived Information

AI-generated enrichments: OCR text, image descriptions, summaries, titles, categories, embeddings, extracted entities, detected events. Can be regenerated, marked stale, or updated without affecting Source Content.

### Title

A 3-8 word heading generated alongside the summary. The summary says what a Memory is *about*; the title is what you *call* it in a list. Generated in the same LLM call as the summary, so it costs no extra latency. Falls back gracefully: a Memory whose provider was unavailable or whose model ignored the format simply has no title, and the card shows the summary snippet instead.

### Detected Event

A Memory containing a future date/time. Not a separate thing a user saves — it is a *lens* on an existing Memory. A Memory becomes event-bearing when the pipeline finds an `ExtractedDate` whose `parsedInstant` is after the Memory's `createdAt` and after the current time. It *expires* when that instant passes, but the Memory persists; only its event status changes from UPCOMING to EXPIRED. Past dates are never events — "I went to a concert last week" is a Memory about something that happened, not something to be reminded of.

### Bounded AI-Analysis Input

A size-limited, representative subset of a Memory's content, constructed for LLM processing. Prevents sending arbitrarily large Memories to the AI model. Bounded by configurable limits on text length, image count (~5-6), and URL count (~5-6).

---

## AI Architecture

### Active AI Provider

The user's chosen generative LLM. Selected on first launch from a curated list of local models, or a user-configured cloud model. The app ships no bundled model; models are downloaded over network after install.

### Local Model

A general-purpose LLM (max 2B parameters) that runs on-device. The curated list offers 2 models each at 1B, 1.5B, and 2B parameter sizes. All general-purpose — not split by role.

### Embedding Model

A separate, smaller model chosen by the development team for generating vector representations. Selected for efficiency and broad device compatibility. Not user-configurable.

### Vision Capability

Optional image understanding via a vision-capable model. Capability-aware: the system checks whether the Active AI Provider supports image input before attempting. Never silently uploads to cloud.

---

## Retrieval

### Unified Retrieval

The single search system (not separate engines) that combines exact/keyword search, semantic/vector search, contextual interpretation, and temporal constraints. Exposed through one natural-language search bar with no manual filter controls.

### Category (Controlled)

A label assigned to a Memory from a predefined Category Dictionary maintained by the application. The LLM selects from the dictionary — it cannot invent new categories. A Memory may have multiple Categories.

### Category Dictionary

The application-owned taxonomy of broad topic labels. Controlled vocabulary prevents category fragmentation. Used for both categorization and query interpretation.

---

## User & Device

### Target User

People who save links, texts, and screenshots of things they find important — then lose track of it because they never organized it properly. The "I know I saved it somewhere" user.

### Minimum Device

Android 11+ (API 30+), minimum 6GB RAM. Users below this threshold are not the target customer.

### Data Sovereignty

oneMind's core value: the user chooses what to trust. Fully functional offline with local models. Cloud is opt-in, never forced. No accounts required. The app gets out of the user's way.

---

## Product Boundaries

### What oneMind IS

A personal memory retrieval system. Capture → enrich → retrieve.

### What oneMind IS NOT

- Not a chatbot or general-purpose AI assistant
- Not a passive surveillance tool
- Not a note-taking app (no folders, no manual organization in v1)
- Not a cloud-first service

### Explicitly Deferred

- User-created collections/folders (future)
- Proactive memory surfacing / reminders (future)
- Cross-device sync or backup (acknowledged, unsolved)
- Data portability / export (post-v1)
- Web or desktop companion (not planned)

---

## Processing States

A Memory moves through:

```
DRAFT → SAVED → PROCESSING → READY
```

On edit:

```
READY → EDITED → PROCESSING → READY
```

On failure:

```
PROCESSING → FAILED
```

---

## Technical Stack (Locked)

- Kotlin
- Android 11+ / API 30+
- Jetpack Compose
- Kotlin Coroutines + StateFlow/SharedFlow
- MVVM/MVI architecture
- Hilt (DI)
- On-device OCR: Google ML Kit
- On-device embeddings: team-selected model
- Local vector storage
- Pluggable LLM layer (local + cloud)

---

## Feature Reference

| ID | Feature | One-line |
|----|---------|----------|
| M1 | Screen Capture | Explicit user-triggered screen capture via system screenshot or app action |
| M2 | Share/Import | Content received via Android Share/Open With |
| M3 | Clipboard/Manual | Quick Settings clipboard save + in-app composer with auto-save |
| M4 | Image Understanding | On-device OCR + optional vision LLM analysis |
| M5 | Metadata Extraction | Unified structured metadata from all Memory content |
| M6 | Memory Feed | Central browsable memory experience, search-first |
| M7 | Chronological | Time-based browsing and retrieval |
| M8 | Source Filtering | Filter by originating app when reliably available |
| M9 | Local AI | On-device AI analysis without cloud dependency |
| M10 | Categorization | Controlled-vocabulary category assignment |
| M11 | Summarization | Bounded AI-generated summary per Memory |
| M12 | Search | Natural-language search bar, no manual filters |
| M13 | Semantic Search | Vector-based meaning retrieval |
| M14 | Contextual Retrieval | Vague/indirect reference understanding |
| M15 | Temporal Retrieval | Time-aware natural-language constraints |
