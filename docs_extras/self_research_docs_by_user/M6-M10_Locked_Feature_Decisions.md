# Mind Space Clone+ — M6–M10 Locked Product & Architecture Decisions

> **Purpose:** Handoff/context document for Codex or another implementation agent. This records the product and architectural decisions locked during ideation for M6 through M10.
>
> **Important:** This document records locked behavior and product intent. Where lower-level implementation details were deliberately left open, they should be designed during implementation rather than treated as pre-decided.

---

## Project Context

We are building a native, local-first personal knowledge and memory assistant for Android. The project is inspired by OnePlus Mind Space but is explicitly intended to develop its own identity and capabilities rather than being a direct clone.

The application captures information encountered by the user, processes it locally where possible, stores structured memories, and provides search, browsing, filtering, categorization, and AI-assisted understanding.

The broader architecture already established includes:

- Kotlin
- Android 11+ / API 30+
- Jetpack Compose
- Kotlin Coroutines
- StateFlow / SharedFlow
- MVVM/MVI-style architecture
- Hilt
- On-device OCR with ML Kit
- On-device embeddings
- Local vector storage
- Structured metadata storage
- A pluggable LLM layer supporting local and cloud models

M6–M10 define how processed memories are exposed, organized, filtered, analyzed, and categorized.

---

# M6 — Unified Memory Feed

## Status

**LOCKED**

## Feature Definition

> Provide one central place where captured memories can be browsed.

M6 establishes the application's central memory experience.

Regardless of where a memory originated, once it has been saved and processed it becomes part of the same unified memory system.

Possible origins include:

- Screenshot capture
- Shared image
- Shared text
- Clipboard capture
- Manually created memory
- Future capture mechanisms

The user should not have to think about which capture mechanism was used when later looking for a memory.

## Primary Application Entry Point

The application should **open on the search interface/search bar**.

Search is therefore a primary entry point into the memory system rather than merely a feature buried inside navigation.

Conceptually:

```text
+--------------------------------------+
| Search your memories...              |
|                                      |
| Memory                               |
| Memory                               |
| Memory                               |
|                                      |
|                              +       |
+--------------------------------------+
```

The exact visual design is not locked to this mockup. The mockup establishes the interaction model:

- App opens with search readily available.
- Memories are accessible from the same central experience.
- A plus/add action can be available for manually adding content.
- Other browsing/viewing methods are accessible separately.

## Multiple Viewing Methods

The memory system should have different ways of viewing/browsing memories through separate tabs or viewing modes.

The exact tab list is **not locked**.

The locked concept is:

> **Search is the primary entry point, while other memory-viewing methods can exist as separate tabs/views.**

Potential future views may include chronological browsing, category browsing, source-based browsing, etc., but their exact organization should be decided during UI implementation/product refinement.

## UI/UX Direction

The final frontend should be close to a hybrid of:

- Google's Material You design language
- Gemini application's interaction/design logic

The intent is:

- Modern Android-native UI
- Material-based components
- Search-first experience
- Clean information hierarchy
- Modern memory cards/lists where appropriate
- Contextual AI-oriented interactions
- Polished, native-feeling navigation

This is **design inspiration**, not a requirement to copy Google Gemini or OnePlus Mind Space.

The product must retain its own identity.

## M6 Memory Presentation

A memory may eventually expose multiple pieces of information:

```text
Memory
├── Captured content
├── Timestamp
├── Source application (if available)
├── OCR text (if applicable)
├── Image description (if available)
├── Extracted metadata
├── Embedding
└── Categories
```

Not every field is necessarily visible in the primary feed.

The feed is the central presentation layer over the underlying memory object.

## Relationship to Earlier Processing

```text
Capture
   ↓
Processing
   ↓
Memory object
   ↓
Unified Memory Feed
   ↓
Search / Browse / Filter
```

M6 should not assume that every memory is identical.

A text-only memory may have no OCR or image data.

An image memory may have OCR, image description, image metadata, categories, and an embedding.

A memory should still be usable when some derived fields are unavailable.

## M6 Explicitly NOT Locked

Do not treat these as finalized:

- Exact Compose layout
- Exact card design
- Exact tab names
- Exact tab count
- Exact search UI
- Exact navigation structure
- Exact animation system
- Exact information density

These are implementation/design decisions for later.

---

# M7 — Chronological Organization

## Status

**LOCKED**

## Feature Definition

> Memories retain timestamps and can be browsed according to when they were captured.

M7 establishes time as a first-class property of every memory.

## Timestamp Requirement

Every memory should retain relevant time information.

Example:

```text
Memory A
Captured: 21 Aug 2026, 10:32 AM

Memory B
Captured: 21 Aug 2026, 2:14 PM

Memory C
Captured: 20 Aug 2026, 7:41 PM
```

The exact database field names and storage representation are implementation details.

The product-level requirement is:

> **The memory must retain timestamp information sufficient for chronological browsing and time-based retrieval.**

## Chronological Browsing

The user should be able to browse memories according to when they were captured.

Conceptual grouping:

```text
Today
 ├── Memory
 ├── Memory
 └── Memory

Yesterday
 ├── Memory
 └── Memory

Earlier
 ├── Memory
 └── Memory
```

The exact visual presentation is not locked.

Possible implementations include:

- grouped date sections
- timeline
- chronological list
- date filters
- date-based search

## Time as a Retrieval Dimension

Timestamp data should also be usable by search/retrieval.

Example:

> "What did I save yesterday?"

Conceptually:

```text
User Query
   +
Time Constraint
   ↓
Memory Retrieval
```

This can later combine with:

- semantic search
- categories
- source filtering
- other metadata

## M7 Explicitly NOT Locked

Do not assume decisions have been made about:

- Exact timestamp schema
- UTC/local storage strategy
- Timezone edge cases
- Timeline UI
- Date-grouping UI
- Exact date query parser
- Date filter UI

These are implementation details.

---

# M8 — Source Filtering

## Status

**LOCKED**

## Feature Definition

> Filter memories according to their originating application/source.

M8 gives memories a source dimension.

Possible sources include:

- Chrome
- WhatsApp
- Telegram
- YouTube
- Gallery
- Notes
- Other Android applications
- Our own app for manually created memories

The exact set depends on Android APIs and the capture pathway.

## Source as Memory Metadata

Where Android provides reliable source information, it should be associated with the memory.

Conceptually:

```text
Memory
├── Content
├── Timestamp
├── Source
│   ├── Package name
│   ├── Application name
│   └── Other available source information
└── ...
```

The exact schema is not locked.

## Important Android Limitation

Do **not** assume every screenshot inherently contains reliable information identifying the application visible when it was captured.

In particular:

```text
Screenshot
   ≠
Guaranteed originating application metadata
```

Source information depends on how the memory entered the application and what Android exposes.

Therefore the application must not fabricate source information.

Conceptually:

```text
Source information available?
       |
   +---+---+
   |       |
  Yes      No
   |       |
 Save      Leave unavailable
 source
```

If source cannot be reliably determined, the memory should have no known source rather than an invented source.

## Source Filtering

When source information exists, users should be able to filter memories according to source.

Conceptually:

```text
All Memories
     ↓
Source Filter
     ↓
+----------+----------+----------+
| Chrome   | WhatsApp | Gallery  |
+----------+----------+----------+
```

This can later combine with:

- time filters
- categories
- semantic search
- other metadata

## Source Does Not Replace Memory Content

Source is metadata, not the memory itself.

```text
Memory
├── Captured content
├── Timestamp
├── Source
├── OCR
├── Other metadata
├── Embedding
└── Categories
```

## M8 Explicitly NOT Locked

Do not treat these as finalized:

- Exact Android source-detection mechanism for every capture path
- Exact source database schema
- Exact source-filter UI
- Exact handling of unknown source
- Exact source display design

The product requirement is that reliable source information should be retained and usable for filtering.

---

# M9 — On-Device AI Analysis

## Status

**LOCKED**

## Feature Definition

> Perform selected AI processing locally rather than requiring a cloud API.

M9 is a product-level commitment to local-first AI.

The app should be capable of performing useful AI analysis on-device rather than making cloud AI mandatory.

## Important Scope Clarification

M9 does **not** mean every operation must use a local LLM.

Different tasks can use different mechanisms.

Examples:

```text
OCR
→ ML Kit

Embedding
→ On-device embedding model

Vector search
→ Local vector database

Generative understanding
→ Local or cloud generative model
```

M9 primarily establishes the ability to perform suitable AI understanding/synthesis locally.

## Core Local-First Processing

The application should retain this local foundation:

```text
Image preprocessing
        ↓
OCR
        ↓
Metadata extraction
        ↓
Text processing/chunking
        ↓
Embedding
        ↓
Local vector storage
        ↓
Local retrieval
```

These operations do not fundamentally depend on a cloud LLM.

This allows the memory system to remain useful without a configured cloud AI provider.

## Generative AI on Device

Where a suitable local model is available, selected generative tasks may run locally.

Potential examples include:

- Image understanding
- Image description
- Text understanding
- Memory analysis
- Summarization
- Semantic interpretation
- Answering questions using retrieved memories
- Other appropriate AI synthesis tasks

The exact list and implementation can evolve.

## Local and Cloud AI Are Both Valid

The application has a pluggable LLM architecture.

Conceptually:

```text
AI Task
   ↓
+-----------------------+
|                       |
|   Local AI / Cloud AI |
|                       |
+-----------------------+
```

The local AI path is not intended to eliminate cloud AI.

The larger product direction is:

- local AI when appropriate or selected
- cloud AI when configured and preferred
- specialized local processing where it makes more sense than an LLM

The detailed model/provider-selection system is outside the M9 feature lock.

## M9 Does NOT Lock

Do not assume M9 has selected:

- A specific local LLM
- A specific parameter size
- A specific local inference framework
- A specific model packaging mechanism
- A specific model download mechanism
- A routing algorithm
- Battery scheduling policy
- Thermal policy
- RAM-management policy
- Cloud fallback behavior
- Exact prompts
- Exact inference optimizations

These are engineering decisions for later.

## M9 Core Product Lock

> **The application supports on-device AI analysis for appropriate understanding and synthesis tasks, allowing useful AI functionality to operate without requiring cloud AI, while retaining the ability to use configured cloud AI where applicable.**

---

# M10 — Automatic Categorization

## Status

**LOCKED**

## Feature Definition

> Automatically determine broad categories/topics for captured memories.

During ideation, this feature was deliberately simplified.

The application should **not** ask the LLM to invent a new category for every memory.

Instead, the application maintains a controlled category vocabulary.

## Controlled Category Dictionary

The application maintains a predefined category dictionary that is:

- broad
- reasonably comprehensive
- controlled
- reusable
- consistent across memories and queries

Example categories:

```text
Technology
Education
Travel
Finance
Shopping
Entertainment
Sports
Science
Work
Business
Food
News
Automotive
Personal
...
```

These are examples only.

The exact category dictionary is **not locked** during product ideation.

## LLM's Role

The LLM analyzes a memory and selects relevant categories from the existing dictionary.

It should **not** create arbitrary categories.

Example memory:

```text
"Qwen3 is a Mixture-of-Experts language model..."
```

Possible assigned categories:

```text
Technology
Artificial Intelligence
LLMs
Machine Learning
```

assuming these exist in the controlled dictionary.

It must not invent a new persistent category such as:

```text
Advanced Qwen Research
```

## Why Categories Are Controlled

Allowing an LLM to invent categories independently would create inconsistent taxonomy.

For example:

```text
AI
Artificial Intelligence
AI Technology
Artificial Intelligence Technology
Machine Intelligence
AI Research
```

could all describe closely related content.

A controlled dictionary prevents this category fragmentation.

The application owns the taxonomy; the LLM performs semantic assignment.

## Multiple Categories Per Memory

This is explicitly **LOCKED**.

A memory may belong to multiple categories.

There is no requirement that each memory have exactly one category.

Example:

```text
Memory:
"Planning a trip to Japan to attend an AI conference."

Categories:
Travel
Technology
Artificial Intelligence
Events
```

The exact category output depends on the final dictionary.

The important product decision is:

> **One memory can have multiple relevant category assignments.**

## Category Dictionary and Query Understanding

The same category vocabulary can later help interpret user queries.

Example:

> "Show me the AI stuff I saved."

The query-processing system can interpret the query against the same dictionary and identify relevant categories such as:

```text
Technology
Artificial Intelligence
LLMs
Machine Learning
```

Then category information can participate in retrieval.

Conceptually:

```text
User Query
    ↓
Query Understanding
    ↓
Controlled Category Dictionary
    ↓
Relevant Categories
    ↓
Memory Retrieval
```

Categories are therefore not merely UI labels. They can become one of the semantic dimensions used to locate memories.

## Categories Do Not Replace Embeddings

The category system must coexist with vector embeddings.

Two separate representations are useful:

```text
Memory
  ↓
Embedding
  ↓
Semantic similarity
```

and:

```text
Memory
  ↓
Category assignment
  ↓
Controlled semantic labels
```

A future retrieval system can combine:

```text
Semantic/vector similarity
+
Category matching
+
Timestamp filtering
+
Source filtering
+
Other metadata
```

The exact ranking/weighting/retrieval algorithm is a backend implementation decision.

## Information Available to Categorization

The categorizer can use information already extracted from the memory.

Depending on the memory type, useful inputs may include:

```text
Captured text
OCR text
Image description
Extracted metadata
Detected entities
Other processed representations
```

Example:

```text
Memory
├── Text
├── OCR
├── Image description
├── URLs
├── Dates
├── Entities
└── Source
```

The categorizer should use the meaningful available representation rather than requiring every memory to contain every field.

## Categories Are Derived Information

Category assignments are derived metadata.

They are not authoritative replacements for captured memory.

Example:

```text
Original memory:
"Qwen3..."
```

Derived information:

```text
Categories:
Technology
Artificial Intelligence
LLMs
```

If categorization is incorrect, the original captured information remains unchanged.

## M10 Backend Decisions Deliberately Deferred

The following are intentionally left for implementation/backend design:

### Exact category hierarchy

For example:

```text
Technology
 └── Artificial Intelligence
      ├── LLMs
      ├── Machine Learning
      └── Computer Vision
```

This is a possible implementation direction, not a locked exact structure.

### Dictionary size

No exact number of categories has been decided.

The dictionary should be broad enough to cover real-world topics without becoming unnecessarily huge.

### Exact category names

The production vocabulary still needs to be designed.

### Retrieval algorithm

How categories interact with:

- vector similarity
- keyword search
- metadata filters
- ranking
- reranking

is an implementation decision.

### Exact categorization prompt/output format

Not yet locked.

---

# Combined M6–M10 Memory Model

By M6–M10, a memory is more than raw captured content.

Conceptually:

```text
MEMORY
│
├── Captured Content
│
├── Timestamp
│
├── Source
│
├── OCR Text
│
├── Image Description (if available)
│
├── Extracted Metadata
│
├── Embedding
│
└── Categories
```

Not every memory has every field.

### Text-only memory

```text
Content
Timestamp
Source (if available)
Metadata
Embedding
Categories
```

### Image memory

```text
Image
Timestamp
Source (if available)
OCR
Image Description (if available)
Metadata
Embedding
Categories
```

---

# Combined User Experience

The features work together rather than existing as independent modules.

```text
                     CAPTURE
                        ↓
                  MEMORY CREATED
                        ↓
                 LOCAL PROCESSING
                        ↓
             ┌──────────┼──────────┐
             ↓          ↓          ↓
            OCR      Metadata   Image Analysis
             │          │          │
             └──────────┼──────────┘
                        ↓
                    Embedding
                        ↓
                Local Memory Store
                        ↓
              Automatic Categorization
                        ↓
                Unified Memory Feed
                        ↓
       ┌────────────────┼─────────────────┐
       ↓                ↓                 ↓
  Chronological      Source          Categories
   Browsing         Filtering        / Semantic
       M7               M8            Retrieval
                                          M10
       └────────────────┬────────────────┘
                        ↓
                     SEARCH
                        ↓
                 Memory Retrieval
                        ↓
                  AI Synthesis
                        ↓
               Local AI / Cloud AI
```

---

# Example End-to-End Memory

Suppose the user captures a screenshot from a browser.

The screenshot contains:

```text
Qwen3-30B-A3B
Mixture-of-Experts
Reasoning Model
https://example.com/qwen
```

The memory might eventually contain:

```text
Memory ID:
<unique ID>

Captured At:
<timestamp>

Source:
Chrome
(if reliably available)

OCR:
Qwen3-30B-A3B
Mixture-of-Experts
Reasoning Model
https://example.com/qwen

Detected URLs:
https://example.com/qwen

Image Description:
Screenshot of an article/model information page about Qwen3
(if vision analysis is available)

Embedding:
<384-dimensional vector>

Categories:
Technology
Artificial Intelligence
LLMs
Machine Learning
```

The user can then:

- find it through the unified feed,
- browse to the date it was captured,
- filter to Chrome memories,
- search semantically for Qwen/LLMs,
- use category-aware retrieval,
- ask an AI model a question about it,
- potentially perform AI analysis locally.

---

# Design Principles Locked Across M6–M10

## 1. The memory is the central object

Features should operate on a unified memory representation rather than creating isolated stores.

## 2. Time is first-class

Every memory should retain timestamp information sufficient for chronological organization.

## 3. Source is useful but must be trustworthy

Never invent an originating application when Android does not provide reliable source information.

## 4. Local-first remains fundamental

Core processing and retrieval should not inherently require cloud AI.

## 5. Generative AI is an optional intelligence layer

AI can enrich and synthesize memory information, but the memory itself should not depend entirely on a cloud LLM.

## 6. Categories are controlled

The LLM selects from the application's category vocabulary.

It does not continuously invent new categories.

## 7. Memories can have multiple categories

Real-world information can belong to multiple domains.

## 8. Categories and embeddings coexist

Categories provide controlled semantic labels; embeddings provide continuous semantic similarity.

## 9. Derived information does not replace original information

OCR, descriptions, summaries, categories, and other AI-derived fields are enrichment. They must not overwrite or become the authoritative representation of captured content.

## 10. Backend implementation remains flexible

Where exact implementation details were intentionally left open, do not treat examples in this document as mandatory implementation specifications.

---

# Final Locked Feature Summary

| ID | Feature | Locked Definition |
|---|---|---|
| M6 | Unified Memory Feed | One central memory system where all captured memories can be browsed; app opens on search; additional viewing methods can exist as tabs/views; UI direction follows a Material You + Gemini-style hybrid |
| M7 | Chronological Organization | Memories retain timestamps and can be browsed/retrieved according to capture time |
| M8 | Source Filtering | Memories can be filtered by originating application/source when reliable source information is available |
| M9 | On-Device AI Analysis | Selected AI understanding/synthesis can be performed locally without requiring cloud AI |
| M10 | Automatic Categorization | LLM assigns one or more categories from a predefined controlled category dictionary; it cannot invent categories; the same vocabulary can assist query interpretation and retrieval |

---

# Implementation Handoff Notes for Codex

When implementing M6–M10:

1. Treat all five features as **locked product requirements**.
2. Do not remove or weaken these behaviors without explicit product discussion.
3. Do not over-interpret example category names as the final taxonomy.
4. Do not assume screenshot source information is always available.
5. Do not make cloud AI a mandatory dependency for core local memory functionality.
6. Do not force every memory into exactly one category.
7. Do not let the categorization LLM create arbitrary persistent category names.
8. Keep categories as derived metadata rather than replacing captured content.
9. Preserve timestamps and source information as structured memory attributes.
10. Keep the architecture modular enough that local/cloud generative providers can evolve independently.
11. Keep UI implementation flexible because the exact tab structure and visual details are not yet fully specified.
12. Prefer asynchronous processing so enrichment does not block memory creation or browsing.
13. If a derived processing stage fails, the underlying memory should remain usable wherever possible.
14. Keep the vector/embedding representation independent from the category representation.
15. Treat the category dictionary as shared infrastructure that can eventually be consumed by both memory categorization and query understanding.

---

# Bottom Line

M6–M10 transform the application from a simple capture/storage tool into a structured personal memory system:

```text
CAPTURED CONTENT
      ↓
STRUCTURED MEMORY
      ↓
┌──────────────────────────────────┐
│ Time                             │
│ Source                           │
│ OCR / Image Understanding        │
│ Metadata                         │
│ Embedding                        │
│ Controlled Categories            │
└──────────────────────────────────┘
      ↓
UNIFIED MEMORY EXPERIENCE
      ↓
SEARCH + BROWSE + FILTER
      ↓
LOCAL / CLOUD AI UNDERSTANDING
```

The central product philosophy is:

> **Capture information once, preserve its context, structure it locally, make it searchable through multiple dimensions, and let AI help the user understand and retrieve it later without making cloud AI mandatory.**
