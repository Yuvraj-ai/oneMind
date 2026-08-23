# Local-First AI Memory Assistant — Locked Product & Architecture Decisions (M1–M5)

> **Purpose:** This document captures the product and architectural decisions explicitly locked during ideation for M1–M5. It is intended to be a durable handoff/context document for future implementation work with Codex or another coding agent.
>
> **Scope:** This document records decisions, constraints, UX behavior, processing boundaries, and intentionally deferred ideas. It does **not** freeze exact Kotlin class names, database schemas, package names, UI styling, or implementation libraries unless explicitly stated.

---

# 0. Product Context

We are building a **native, local-first personal knowledge and memory assistant for Android**, inspired initially by OnePlus Mind Space but explicitly intended to develop its **own identity and capabilities beyond Mind Space**.

The application should make it extremely easy for a user to capture information from their Android device and turn it into persistent, searchable, AI-enriched personal memory.

The core philosophy established so far is:

> **Capture should be simple and user-controlled. Processing should be asynchronous. A memory can contain multiple modalities. Raw user content and derived AI intelligence should remain conceptually separate.**

The app is intended to support:

- screenshots and screen captures
- content shared from other Android applications
- clipboard content
- manually composed memories
- images and multimodal memories
- on-device OCR
- optional vision-capable LLM enrichment
- structured metadata extraction
- later semantic retrieval and AI synthesis

The initial technical direction remains:

- Kotlin
- Android 11+ / API 30+
- Jetpack Compose
- Kotlin Coroutines
- StateFlow / SharedFlow
- MVVM/MVI-style architecture
- Hilt
- local-first storage and processing
- pluggable cloud/local LLM architecture
- user-controlled AI provider/model selection

---

# 1. Global Architectural Principles Locked So Far

These principles apply across M1–M5.

## 1.1 User intent should be explicit

The application should not aggressively infer that every piece of information the user interacts with should become a memory.

Examples:

- Copying something does **not** automatically save it.
- The app should not passively monitor the clipboard and create memories.
- We are not currently building an Accessibility-Service-based passive screen observer.
- The user explicitly triggers capture through a defined action.

The goal is a useful memory assistant, not an application that silently records everything.

---

## 1.2 Capture and processing are separate systems

A critical architectural decision:

> **Capture/ingestion must finish quickly and independently from AI processing.**

The app should not make the user wait for:

- OCR
- vision analysis
- metadata extraction
- categorization
- summarization
- chunking
- embeddings
- vector indexing

before considering the content saved.

Conceptually:

```text
Input
  ↓
Memory Intake
  ↓
Persist user content
  ↓
Return/acknowledge quickly
  ↓
Asynchronous processing
```

This is a core architectural principle.

---

## 1.3 Raw content is never equivalent to derived intelligence

The application should preserve the distinction between:

### User/source content

Examples:

- original text
- pasted text
- captured screenshot/image
- shared image
- attachment

and:

### Derived information

Examples:

- OCR text
- image description
- URLs
- entities
- dates
- locations
- categories
- summaries
- embeddings

Derived data can be regenerated or marked stale without changing the underlying user content.

---

## 1.4 A memory is multimodal

A memory is **not** restricted to one content type.

A single memory may contain:

```text
Text
+
Image
+
URLs
+
OCR
+
Vision description
+
Dates
+
Entities
+
Locations
+
Other derived metadata
```

For example, a user may create one memory containing pasted text and an image. That remains **one logical memory**, with unified metadata derived from all available content.

We should not automatically split such content into separate memories.

---

## 1.5 One common ingestion pipeline

M1, M2 and M3 should feed a common memory-intake architecture.

Conceptually:

```text
                    INGESTION SOURCES
                           │
          ┌────────────────┼────────────────┐
          │                │                │
          ▼                ▼                ▼
     Screen Capture    Share/Import    Manual/Clipboard
          │                │                │
          └────────────────┼────────────────┘
                           ▼
                    Memory Intake
                           ▼
                    Persist Content
                           ▼
                 Async Processing Pipeline
```

The source-specific logic should primarily concern how content enters the application. Downstream processing should operate on a common memory representation.

---

# 2. M1 — Explicit Screen Capture

## Status

**LOCKED**

## Definition

M1 is the application's **explicit screen-capture ingestion mechanism**.

The user intentionally tells the application:

> “Remember what is currently on my screen.”

M1 is not passive screen surveillance.

---

## 2.1 Supported trigger concepts

We agreed that M1 can have multiple user-facing triggers that lead to the same conceptual screen-capture operation.

Potential triggers include:

### A. Android's normal screenshot mechanism

Examples:

- Power + Volume Down
- OEM screenshot gesture
- Android/OEM screenshot functionality
- other system-provided screenshot actions

The resulting screenshot may then become available to the application through appropriate Android mechanisms.

### B. Application-provided capture action

The application can eventually provide a direct capture action, potentially through mechanisms such as:

- Quick Settings tile
- app shortcut
- notification/action surface
- another Android-compliant capture trigger
- potentially a gesture or overlay mechanism if technically and policy compliant

The exact UI trigger is **not yet frozen**.

The important decision is:

> Different triggers may exist, but they all represent the same product concept: explicit screen capture.

---

## 2.2 MediaProjection

`MediaProjection` was identified as an important underlying Android mechanism for implementing application-controlled screen capture.

The conceptual flow is:

```text
User requests capture
        ↓
Android capture authorization
        ↓
MediaProjection
        ↓
Captured display/image
        ↓
Memory Intake
```

Important constraint:

- Android's screen-capture permission/privacy model must be respected.
- The application must not silently capture the user's screen without appropriate user authorization.
- We should design around explicit user intent.

The exact MediaProjection implementation is an engineering decision for implementation phase.

---

## 2.3 M1 does NOT mean continuous monitoring

We explicitly do **not** want:

```text
User opens app
      ↓
App continuously watches screen
```

or:

```text
User changes screen
      ↓
App automatically creates memories
```

This is outside the current M1 scope.

---

## 2.4 Accessibility Service is NOT part of the current baseline

Accessibility Service was considered as a possible way to observe application UI/context.

We deliberately decided **not to include this in M1–M5**.

Reasons include:

- privacy implications
- permission complexity
- Android/Google Play policy considerations
- inconsistent accessibility data across apps
- unnecessary complexity for the current product direction

It can remain a future experimental/backlog concept, but it is **not part of the current locked architecture**.

---

## 2.5 M1 output

M1 should ultimately produce a captured visual/content input that enters the same Memory Intake Pipeline used by other ingestion sources.

The screen capture mechanism should not itself be responsible for:

- OCR
- embeddings
- summarization
- metadata extraction
- LLM reasoning

Those happen downstream asynchronously.

---

# 3. M2 — Android Share / Import Ingestion

## Status

**LOCKED**

## Definition

M2 combines the previously considered “share from another app” and “import an existing screenshot/image/file” concepts.

The unified principle is:

> **The user explicitly gives content to our application through Android's Share/Import mechanisms.**

Examples:

```text
Gallery
  ↓
Share
  ↓
Our App
```

```text
WhatsApp / Messaging App
  ↓
Share
  ↓
Our App
```

```text
Chrome
  ↓
Share
  ↓
Our App
```

```text
File Manager
  ↓
Share / Open With
  ↓
Our App
```

---

## 3.1 Possible content types

M2 should be designed to accept compatible content such as:

- images
- text
- URLs
- PDFs
- documents
- other supported MIME types

The exact supported MIME-type matrix can be expanded during implementation.

---

## 3.2 Existing screenshots

A screenshot already stored in the Gallery is simply another share/import input.

We do not need a completely separate “screenshot import architecture.”

---

## 3.3 M2 user intent

The user explicitly selects Share/Open With/import and chooses our application.

Therefore M2 naturally represents explicit intent.

No passive observation is required.

---

## 3.4 M2 output

All incoming content enters the same Memory Intake Pipeline:

```text
Android Share/Import
       ↓
Memory Intake
       ↓
Persist content
       ↓
Async processing
```

M2 should not have a separate AI-processing pipeline.

---

# 4. M3 — Clipboard & Manual Content Capture

## Status

**LOCKED**

M3 was intentionally simplified to avoid over-engineering clipboard intent detection.

There are **two simple entry points**.

---

# 4.1 M3 Method 1 — Quick Settings “Save Clipboard”

The user can copy anything normally.

Nothing is automatically saved when copying occurs.

The user later explicitly invokes a Quick Settings action:

> **Save Clipboard**

The application then reads the most recent clipboard content and saves it as a memory.

Flow:

```text
User copies content
        ↓
Android clipboard
        ↓
User taps Quick Settings: Save Clipboard
        ↓
Application reads latest clipboard
        ↓
Create/persist memory
        ↓
Notification
        ↓
Async processing
```

---

## 4.1.1 Clipboard behavior

The application should **not**:

- continuously monitor clipboard contents
- maintain a private clipboard history
- automatically save every copied item
- infer whether a copied item is “important”

The user explicitly triggers “Save Clipboard.”

---

## 4.1.2 Confirmation notification

After successful capture, the application should provide a notification indicating that the clipboard content was saved.

Conceptually:

> **Saved to Memory**  
> Clipboard content has been saved.

Potential future actions could include:

- Open Memory
- Undo

but those are not locked requirements yet.

---

# 4.2 M3 Method 2 — In-App Manual Memory Composer

The main application has a prominent **+** action.

The user taps:

```text
Main Memory Screen
        ↓
       [+]
        ↓
Memory Composer
```

The composer can support:

- text entry
- pasting clipboard contents
- adding images
- eventually adding files/other content

The exact UI is not frozen.

---

## 4.2.1 No Save button

We explicitly decided **not** to require a Save button.

The composer uses automatic persistence based on inactivity.

### Auto-save rule

After approximately **3 seconds of user inactivity**, the current composer state is automatically persisted.

Conceptually:

```text
User types/pastes/adds image
        ↓
User stops interacting
        ↓
3 seconds inactivity
        ↓
Persist current draft/state
```

This is **auto-save**, not processing.

---

## 4.2.2 Processing begins when user leaves the composer

Processing should not begin simply because the 3-second auto-save timer fires.

The separation is:

```text
3 seconds inactivity
        ↓
Auto-save state
        ↓
User continues editing if desired
```

Only when the user actually leaves the composer and returns to the main screen should the memory become eligible for processing:

```text
User leaves composer
        ↓
Memory committed
        ↓
Async processing begins
```

This allows the user to continue adding information without competing with background processing.

---

## 4.2.3 Editing an existing memory

A memory is mutable.

If the user later opens an existing memory and adds information, it should remain the **same logical memory**, not become a duplicate.

Example:

```text
Memory A
"Research Qwen models."
```

Later:

```text
Memory A
"Research Qwen models.
GitHub: https://..."
```

The application should update the same memory.

---

## 4.2.4 Derived data becomes stale after edits

When source content changes, derived information can no longer automatically be assumed current.

For example:

```text
READY
  ↓
User edits
  ↓
Derived data stale
  ↓
Reprocessing
  ↓
New embedding
  ↓
Updated metadata
  ↓
Updated summary
  ↓
READY
```

We should not blindly add a second embedding and leave the old representation active.

The exact invalidation/versioning implementation is not frozen yet.

---

## 4.2.5 Memory processing states

The following conceptual states were discussed:

```text
DRAFT
  ↓
SAVED
  ↓
PROCESSING
  ↓
READY
```

When a ready memory is modified:

```text
READY
  ↓
EDITED
  ↓
PROCESSING
  ↓
READY
```

Failure can be represented as:

```text
PROCESSING
  ↓
FAILED
```

The exact enum names are implementation decisions, but the state-machine concept is useful.

---

# 5. Shared Processing Architecture for M1–M3

All ingestion methods should converge on a common architecture.

```text
                    INPUT SOURCES
                         │
       ┌─────────────────┼──────────────────┐
       │                 │                  │
       ▼                 ▼                  ▼
   M1 Screen         M2 Share          M3 Clipboard/
    Capture           Import           Manual Composer
       │                 │                  │
       └─────────────────┼──────────────────┘
                         ▼
                   Memory Intake
                         ▼
                  Persist Raw Input
                         ▼
                  Background Jobs
                         │
        ┌────────────────┼────────────────┐
        ▼                ▼                ▼
       OCR            Metadata          Vision
        │                │                │
        └────────────────┼────────────────┘
                         ▼
                     Chunking
                         ▼
                    Embedding
                         ▼
                   Vector Index
```

The exact WorkManager/coroutine architecture can be finalized during implementation.

---

# 6. M4 — Multimodal Image Understanding

## Status

**LOCKED**

M4 was initially described as OCR but was intentionally expanded.

The final concept is:

> **M4 is multimodal image understanding: on-device OCR plus optional vision-capable LLM image understanding.**

OCR is one enrichment source, not the complete interpretation of an image.

---

# 6.1 Image processing flow

Whenever a memory contains an image:

```text
Image
  │
  ├───────────────┐
  │               │
  ▼               ▼
Local OCR      Vision Capability?
  │               │
  │          ┌────┴────┐
  │          │         │
  │         YES        NO
  │          │         │
  │          ▼         ▼
  │      Vision LLM  NOT_SUPPORTED
  │          │
  └──────┬───┘
         ▼
  Enriched Memory
```

---

# 6.2 OCR

OCR should initially use an on-device solution such as **Google ML Kit Text Recognition**.

Important principle:

> OCR should run locally by default and should not require a cloud LLM.

OCR can process:

- screenshots
- photographs
- documents
- receipts
- posters
- tickets
- menus
- other images containing text

The original image is not conceptually replaced by OCR text.

Instead:

```text
Original Image
+
OCR Text
```

are both part of the memory representation.

---

# 6.3 OCR may produce no useful text

A valid image can contain little or no text.

For example:

```text
Photograph of a mountain
```

OCR may produce:

```text
none
```

This does **not** mean the memory is invalid.

The image remains useful through other enrichment mechanisms.

---

# 6.4 Optional vision LLM

If the user's configured LLM/model supports vision, the image can also be sent to that vision-capable model to produce an image description.

Example:

```text
OCR Text:
none

Image Description:
"A photograph of a snow-covered mountain range
under a cloudy sky."
```

Another example:

```text
OCR Text:
"Margherita Pizza ₹399
Farmhouse Pizza ₹499"

Image Description:
"A restaurant menu displaying several pizza
options and their prices."
```

---

# 6.5 Vision support must be capability-aware

The application should not assume that the user's selected model supports vision.

The system should determine whether the configured provider/model supports image input.

Possible states include:

```text
SUCCESS
NONE / EMPTY
NOT_SUPPORTED
PROCESSING
FAILED
```

Important semantic distinction:

### `none` / `empty`

Processing was available and completed, but nothing useful was detected.

Example:

```text
OCR:
EMPTY
```

### `not_supported`

The capability was not available.

Example:

```text
Vision:
NOT_SUPPORTED
```

### `processing`

The capability exists but work has not completed.

### `failed`

Processing was attempted but failed.

---

# 6.6 Vision provider selection

The vision model does not necessarily have to be identical to the user's primary text LLM.

However:

> **The application must never silently upload an image to a cloud provider merely because that provider happens to be configured.**

If the user has a text-only local model selected and no approved vision-capable provider is enabled, vision processing should simply be unavailable.

Potential future UI:

> Vision processing unavailable with current model.

or:

> Enable cloud image understanding.

The exact settings UX is not yet frozen.

---

# 6.7 Image description vs object metadata

We explicitly decided not to turn every object observed by a vision model into permanent M5 metadata.

For example, if a photograph contains:

- 12 people
- 8 laptops
- chairs
- a projector
- a logo

we do not automatically create dozens of permanent object records.

Those observations can remain inside the image description.

Object-level indexing/search can be considered later as a separate capability.

---

# 6.8 Original image storage and cleanup

We discussed storage constraints and agreed that permanent retention of the full-resolution original is not necessary for the initial product design.

The intended lifecycle is:

```text
Captured image
      ↓
Temporary original
      ↓
OCR + optional vision processing
      ↓
Image optimization
      ↓
Canonical stored image
      ↓
Delete temporary original
```

The application should process the image at the available/original quality before compression where practical.

After processing, the image can be:

- resized to a sensible resolution
- compressed
- stored in a storage-efficient format such as WebP/JPEG where appropriate
- retained as the canonical memory image

A thumbnail can also be generated for the memory feed.

Conceptually:

```text
Temporary Original
        ↓
Processing
        ↓
Canonical Memory Image
        ↓
Thumbnail
```

The original temporary capture does not need to remain indefinitely.

---

# 6.9 Future capability philosophy for image storage

We explicitly decided **not to design today's storage architecture around hypothetical future AI models**.

If a new capability or model appears later:

> New captures can use the new capability.

We do not need to guarantee that every historical memory automatically receives every future enrichment.

A future reprocessing/migration feature can be added if it becomes useful, but it is not a current requirement.

---

# 6.10 M4 output

An image memory may therefore have:

```text
Image Memory
├── Canonical Image
├── OCR
│   ├── status
│   └── extracted text
└── Vision
    ├── status
    ├── provider/model (if applicable)
    └── description
```

This information becomes input to M5 and later processing.

---

# 7. M5 — Unified Metadata Extraction

## Status

**CONCEPTUALLY LOCKED**

The conceptual behavior of M5 is locked. The exact database/schema representation remains intentionally open until downstream features are designed.

Definition:

> **M5 extracts and consolidates useful structured metadata from all available content within a memory.**

This includes:

- user-provided text
- OCR text
- image descriptions
- URLs
- source information
- other available content

A single multimodal memory gets **unified metadata**.

---

# 7.1 M5 does not create separate memories per modality

Suppose one memory contains:

```text
Pasted Text
+
Image
```

The application should not automatically create:

```text
Memory A = pasted text
Memory B = image
```

Instead:

```text
Memory A
├── Text
├── Image
├── OCR
├── Vision Description
└── Unified Metadata
```

---

# 7.2 Core metadata groups

## A. Identity metadata

Deterministic application-level information:

- memory ID
- creation timestamp
- update timestamp

---

## B. Source metadata

Where the memory came from:

- source type
- source application where available
- MIME/content types

Possible source types include concepts such as:

- SCREENSHOT
- SHARE
- CLIPBOARD
- MANUAL

The exact enum names are not yet frozen.

---

## C. Content metadata

Basic information about the memory:

- content type
- text/image/attachment counts
- MIME types
- basic content statistics where useful

A memory can have multiple content types.

Example:

```text
contentTypes:
[
  TEXT,
  IMAGE
]
```

---

## D. URL/link metadata

URLs should be first-class extracted metadata.

Example:

```text
URL:
https://github.com/example/project
```

Potential structured fields:

- raw URL
- normalized URL
- domain
- basic URL classification where confidently detectable

We should not initially build an enormous URL taxonomy.

---

## E. Temporal metadata

Dates and times mentioned by the content should be extracted.

Important distinction:

### Capture time

When the memory was saved:

```text
capturedAt
```

### Mentioned/event time

A date/time contained in the memory:

```text
September 15
10:00 AM
```

These must not be conflated.

A screenshot captured today can contain an event occurring next month.

---

## F. Entity metadata

Potential entity types include:

- people
- organizations/companies
- products
- places
- events
- technologies
- other useful named entities

Example:

```text
Google → ORGANIZATION
OpenAI → ORGANIZATION
Gemini → PRODUCT
AI Summit 2026 → EVENT
Bangalore → LOCATION
```

M5 should initially focus on **entity detection**, not building a full relationship/knowledge graph.

A future knowledge/relationship layer can consume these entities.

---

## G. Location metadata

Locations can be extracted from content such as:

- pasted text
- OCR
- URLs
- image descriptions
- documents

Examples:

```text
Bangalore
Mountain View
Phoenix Mall
```

We explicitly do **not** want to silently collect precise device location simply because the application could potentially access it.

For the baseline, location should primarily come from the content itself.

---

## H. Image-derived metadata

Image information from M4 should remain associated with the same memory.

For example:

```text
image:
  ocr:
    status: SUCCESS
    text: "AI Summit 2026..."

  vision:
    status: SUCCESS
    description: "A conference scene..."
```

This is not a separate memory.

---

## I. Processing metadata

The system should track the state of derived processing such as:

- OCR
- Vision
- Embedding
- Summary

Potential information:

- status
- processing timestamp
- provider/model where relevant

The exact representation is not yet frozen.

---

# 7.3 M5 should not own other features

M5 deliberately does **not** become responsible for everything AI can infer.

### Categorization

Owned by the later categorization feature (M10), not M5.

M5 may extract entities such as:

```text
Google
Qwen
Bangalore
```

but M10 can decide:

```text
Category = AI
```

### Summarization

Owned by the summarization feature (M9).

### Semantic relationships / knowledge graph

Future feature.

### Object-level image analysis

Future feature.

### General AI reasoning

Handled by the LLM layer.

This separation keeps M5 manageable.

---

# 7.4 Raw vs derived metadata

We explicitly distinguish:

### Raw/deterministic metadata

Directly observed or known:

```text
sourceType = SCREENSHOT
mimeType = image/png
createdAt = ...
```

No AI confidence score is necessary.

### Derived/inferred metadata

Produced through OCR, NLP, vision, or other AI processing:

```text
Entity = Google
Category = AI
Location = Bangalore
```

Confidence/provenance can be useful here.

---

# 7.5 Provenance

We identified provenance as potentially important:

> **Where did this metadata come from?**

For example:

```text
Entity:
  Google

Sources:
  - pasted text
  - OCR
  - vision
```

Or:

```text
Entity:
  Google
Source:
  OCR
Confidence:
  0.94
```

The exact schema for provenance and confidence is **intentionally not frozen yet**.

The concept should remain available when the final metadata model is designed.

---

# 7.6 Confidence

Confidence is not necessary for deterministic metadata.

For example:

```text
sourceType = SCREENSHOT
```

does not need a confidence value.

But AI-derived information may benefit from confidence:

```text
Entity:
  Apple
Type:
  ORGANIZATION
Confidence:
  0.82
```

The exact confidence model and thresholds are not yet locked.

---

# 7.7 Example: complex multimodal memory

Consider a user creating one memory containing:

### Pasted text

```text
Check this project:
https://github.com/example/project

The launch is on September 15 at 10 AM in Bangalore.
Google and OpenAI are both involved.
```

### Uploaded image

A conference photograph containing:

- people
- laptops
- presentation screen
- Google logo
- event signage
- lots of objects

M5 should conceptually produce something like:

```text
Memory
│
├── Content
│   ├── Text
│   └── Image
│
├── URLs
│   └── https://github.com/example/project
│
├── Temporal
│   ├── September 15
│   └── 10:00 AM
│
├── Locations
│   └── Bangalore
│
├── Entities
│   ├── Google → ORGANIZATION
│   ├── OpenAI → ORGANIZATION
│   └── AI Summit 2026 → EVENT
│
├── Image Understanding
│   ├── OCR → "AI Summit 2026..."
│   └── Vision → "A conference scene..."
│
└── Source/Processing Metadata
```

The actual pasted text and image remain the primary memory content.

Metadata is the structured interpretation used for:

- retrieval
- filtering
- navigation
- linking
- future AI reasoning

---

# 8. M1–M5 Final Architecture

The current locked architecture can be summarized as:

```text
                         USER
                          │
             ┌────────────┼─────────────┐
             │            │             │
             ▼            ▼             ▼
        M1 Screen      M2 Share      M3 Manual/
         Capture        Import       Clipboard
             │            │             │
             └────────────┼─────────────┘
                          ▼
                   MEMORY INTAKE
                          │
                          ▼
                 PERSIST USER CONTENT
                          │
                          ▼
                 ASYNC PROCESSING
                          │
             ┌────────────┼────────────┐
             │            │            │
             ▼            ▼            ▼
        M4 OCR       M4 Vision      Other
             │            │
             └────────────┼────────────┘
                          ▼
                 M5 METADATA
                          │
          ┌───────────────┼────────────────┐
          │               │                │
          ▼               ▼                ▼
        URLs          Entities          Dates
          │               │                │
          └───────────────┼────────────────┘
                          ▼
                 Future M6+ Processing
```

---

# 9. Explicitly Deferred Decisions

The following are **not locked yet** and should not be invented by an implementation agent without discussion:

## Screen capture
- Exact Quick Settings/shortcut implementation
- Exact MediaProjection architecture
- Whether/how a custom gesture is exposed
- Exact screenshot detection/import mechanism

## Clipboard
- Exact Quick Settings tile implementation
- Clipboard MIME-type edge cases
- Notification actions such as Undo/Open

## Manual composer
- Exact Compose UI
- Exact 3-second debounce implementation
- Exact behavior when reopening a draft
- Exact UI for editing an existing memory
- Whether memory edit history/versioning is retained
- Exact processing-state UI

## Image processing
- Exact image resolution limits
- Exact compression quality
- Exact WebP/JPEG policy
- Exact thumbnail dimensions
- Exact temporary-file lifecycle implementation
- Exact vision provider/model routing

## Metadata
- Exact database schema
- Exact entity taxonomy
- Exact URL classification taxonomy
- Exact temporal data model
- Exact location representation
- Exact confidence model
- Exact provenance representation
- Exact deduplication/entity-resolution logic

These should be decided only when they become necessary.

---

# 10. Product Principles to Preserve During Implementation

When implementing M1–M5, future coding agents should preserve these principles:

1. **Do not introduce passive surveillance.**
2. **Do not automatically save clipboard contents merely because they were copied.**
3. **Keep user intent explicit.**
4. **Do not require a Save button in the manual composer.**
5. **Auto-save composer changes after approximately 3 seconds of inactivity.**
6. **Do not start heavy processing merely because auto-save occurred.**
7. **Start processing when the user leaves the composer.**
8. **Persist user content before expensive AI processing.**
9. **Run processing asynchronously.**
10. **A memory can contain multiple modalities.**
11. **Do not split a multimodal memory into separate memories automatically.**
12. **Preserve source content separately from derived AI data.**
13. **Do not silently upload images to cloud AI providers.**
14. **Vision processing is optional and capability-aware.**
15. **OCR can legitimately return no text.**
16. **Vision can legitimately be unavailable.**
17. **Do not turn every visual object into permanent metadata.**
18. **Do not make M5 responsible for categorization, summarization, or general reasoning.**
19. **Keep metadata useful and bounded rather than collecting everything available.**
20. **Do not design current storage around hypothetical future AI capabilities.**
21. **If a new capability is introduced later, it can initially apply to new captures.**
22. **Avoid prematurely freezing implementation details that have not been explicitly decided.**

---

# 11. Current Locked Feature Definitions

## M1 — Explicit Screen Capture

Allow the user to explicitly capture the current screen using Android's screenshot mechanisms and/or application-provided capture actions. The implementation may use MediaProjection where appropriate. Screen capture is user-initiated and is not continuous passive monitoring.

## M2 — Android Share / Import

Allow users to explicitly send content from other Android applications or import existing content through Android's Share/Open With mechanisms. Images, text, URLs, documents and other supported MIME types can enter the common Memory Intake Pipeline.

## M3 — Clipboard & Manual Content Capture

Provide two explicit mechanisms:

1. **Quick Settings → Save Clipboard:** reads the most recent clipboard content, saves it as a memory, and confirms via notification.
2. **App → + → Manual Composer:** supports text, clipboard pasting, images and future content types. The composer auto-saves after approximately 3 seconds of inactivity without requiring a Save button. Processing begins when the user leaves the composer. Existing memories can be edited without creating duplicates.

## M4 — Multimodal Image Understanding

For images, run local OCR and, when an explicitly configured vision-capable LLM is available, optional vision analysis. Store OCR and vision results independently alongside the image. Distinguish empty results, unsupported capabilities, processing, success and failure. Optimize images after processing to reduce storage usage.

## M5 — Unified Metadata Extraction

Extract and consolidate structured metadata from all content within a memory, including source information, URLs, dates/times, locations, entities, basic content information, image-derived information and processing state. Keep metadata distinct from the source content and from later features such as categorization and summarization.

---

# 12. Handoff Note for Codex

When implementation begins, Codex should treat this document as the **product-level source of truth for M1–M5 decisions**.

Codex should:

- implement the locked behavior
- avoid adding passive capture behavior
- avoid inventing unapproved features
- keep capture and processing decoupled
- preserve multimodal memory semantics
- preserve source content separately from derived data
- make processing asynchronous
- design interfaces so future M6+ features can consume M5 metadata
- keep implementation details modular so unresolved decisions can be changed later

Codex should **not assume that every conceptual field in this document must immediately become a database column**. The final persistence schema should be designed after considering the requirements of the downstream memory, retrieval, categorization, summarization and AI features.

---

# 13. One-Sentence Product Model

The current product can be summarized as:

> **The user explicitly gives the application something worth remembering; the application immediately preserves it as one multimodal memory, enriches it asynchronously using local and optionally configured AI capabilities, extracts structured metadata from all available content, and keeps the resulting memory searchable and extensible without silently observing the user's device.**
