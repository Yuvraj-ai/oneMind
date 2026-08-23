# M11–M15 — Locked Product & Architecture Decisions
## Local-First AI Memory Assistant for Android

**Document purpose:** Implementation handoff for Codex. This records the product-level decisions locked for M11 through M15. Treat these as requirements unless a later product decision explicitly changes them.

---

# 1. Scope

This document covers:

- **M11 — Content Summarization**
- **M12 — Memory Search**
- **M13 — Semantic Search**
- **M14 — Contextual Retrieval**
- **M15 — Temporal Retrieval**

These features build on the earlier memory pipeline:

```text
Capture
  ↓
Content Processing
  ├── Text
  ├── OCR
  ├── Image understanding
  ├── Metadata
  ├── Embeddings
  └── Categories
  ↓
M11 Summary / AI-derived understanding
  ↓
M12–M15 Unified Memory Retrieval
```

The product is a **memory system**, not a general-purpose chatbot. Search is primarily responsible for finding memories. Answer generation/synthesis is not part of M12–M15.

---

# 2. M11 — Content Summarization

## 2.1 Purpose

M11 generates a concise representation of what a saved memory is generally about.

The summary serves two audiences:

1. **The user** — quickly understand what a memory contains without opening every item.
2. **The retrieval/AI system** — provide an additional compact semantic representation that can help understand and retrieve the memory.

The summary is **not** intended to exhaustively describe every item in a memory.

## 2.2 Core Principle

A memory can contain a very large amount of information, for example:

- 10+ images
- 20–30 URLs
- large copied-text sections
- OCR from screenshots
- image descriptions
- other metadata

The application must **not** blindly send all of this information to an LLM for summarization.

Instead, after normal memory processing completes, the application constructs a **bounded representative AI-analysis input**.

```text
Saved Memory
    ↓
Normal processing completes
    ↓
Build bounded AI-analysis input
    ↓
LLM / active AI provider
    ↓
Summary + other AI-derived information
```

The bounded input is an optimization and representation layer. It does not modify or delete the original memory.

---

# 3. M11 — Bounded AI Analysis Input

## 3.1 Text

Do not send unlimited text to the summarization model.

Use a predefined maximum amount of text.

```text
Large captured text
       ↓
bounded text sample
       ↓
AI analysis
```

The exact character/token limit is intentionally **not locked** at the product-ideation stage.

Codex/backend implementation should determine a sensible configurable limit based on:

- model context limits
- token cost
- latency
- device capabilities
- local model limitations

Do not hard-code an arbitrary product-level number merely because an example used one.

## 3.2 Images

A memory may contain many images.

Do not send every image to the LLM/vision model.

Use only a bounded number of representative images for the AI-analysis pass.

Current product concept:

- Analyze approximately the first **5–6 images** initially.
- The exact configurable number remains an implementation setting.

For selected images, available information may include:

- OCR text
- vision/image description
- other already-derived information

The original images remain part of the memory. The selection limit applies only to the AI-analysis request.

## 3.3 URLs

A memory can contain a large number of links.

Do not deeply process every URL through the AI model.

Use a bounded strategy:

```text
First N URLs
    ↓
More detailed processing

Remaining URLs
    ↓
Lightweight information such as titles
    ↓
Bounded title list
```

The initial product concept uses approximately the first **5–6 URLs** for deeper consideration, while lightweight information such as titles from additional URLs can provide broader context up to a defined limit.

Exact N and title limits are implementation-level decisions.

---

# 4. M11 — Shared AI Analysis Context

The bounded representation should not be treated as a summarization-only pipeline.

It is intended to provide a compact, representative context that can support:

- summary generation
- LLM-based metadata/entity extraction where needed
- event detection in M16 and future derived features

Conceptually:

```text
Memory
 ├── Complete deterministic information
 │
 └── Bounded AI-analysis representation
       ├── bounded text
       ├── selected image information
       ├── selected URL information
       └── relevant existing metadata
                    ↓
                  AI/LLM
                    ↓
       ┌────────────┼────────────┐
       ↓            ↓            ↓
    Summary      Metadata     Other derived
                              information
```

Do not create unnecessary independent LLM-processing pipelines if the same bounded context can serve multiple derived features.

---

# 5. M11 — Summary Characteristics

The generated summary should answer:

> **What is this memory generally about?**

It should **not** attempt to enumerate everything.

Example:

A memory containing 15 screenshots, 27 URLs, 8,000 characters, and Android/local-LLM content should produce something conceptually like:

> “A collection of resources about building local AI applications on Android, including model recommendations, Android ML frameworks, and reference articles.”

It should not produce a long item-by-item inventory.

---

# 6. Original Content Must Be Preserved

The summary is derived information.

It must never replace the source memory.

```text
Original Memory
    │
    ├── FULL content preserved
    │
    └── Bounded AI analysis
             ↓
          Summary
```

If the user needs details, they can open the original memory and inspect all its content.

---

# 7. M11 — Summary and Memory Updates

A memory can potentially be updated later by adding content.

Therefore, a previously generated summary can become stale.

Implementation should treat the summary as derived data that can be invalidated/recomputed when the underlying memory changes.

Product-level rule:

```text
Memory changes
     ↓
Existing derived summary may no longer represent memory
     ↓
Summary should be regenerated when appropriate
```

The exact regeneration mechanism/timing is a backend decision.

---

# 8. M12 — Memory Search

## 8.1 Purpose

M12 provides the central search capability for the user's accumulated memories.

Definition:

> **Allow users to search their accumulated memories using exact terms, semantic meaning, and natural-language queries.**

The search experience should be centered around the application's main search bar.

---

# 9. M12 — Three Search Capabilities

M12 supports:

### A. Keyword / Exact Search

The user can search for exact terms, phrases, names, URLs, etc.

Example:

```text
Qwen3
```

The system should be capable of finding memories containing that information.

### B. Semantic Search

The query does not need to use the exact wording contained in the memory.

Example:

```text
things I saved about running AI models locally on Android
```

can retrieve a memory containing:

```text
Deploying quantized LLMs using ONNX Runtime Mobile on Android
```

### C. Natural-Language Search

The search bar accepts natural-language requests rather than requiring a keyword-only query.

Example:

```text
Show me the AI stuff I saved from Chrome last week.
```

The system should derive useful intent/context from the query.

---

# 10. M12 — Natural-Language Context Acts as Filtering

There is intentionally **no required manual filter UI** for M12.

Do not build the search experience around separate controls such as source/category/date selectors.

Instead, the user can write:

```text
Find the AI stuff I saved from Chrome last week.
```

The retrieval system can interpret:

```text
Topic/category:
AI

Source:
Chrome

Temporal constraint:
Last week
```

The exact query parser and implementation are backend decisions.

---

# 11. M12 — No Manual Search Filters

This is explicitly locked.

The user should primarily interact with the search system through the natural-language search field.

Manual filter controls are **not** part of the current M12 product definition.

This keeps the experience simple and aligned with the search-first UI direction established in M6.

---

# 12. M12 — Searchable Memory Representations

Search should not operate only against raw captured text.

The retrieval system can use the complete available memory representation, including:

```text
Memory
├── Original text
├── OCR text
├── Image description
├── M11 summary
├── URLs
├── Metadata
├── Categories
├── Timestamp
├── Source
└── Embedding/vector representation
```

Different memory types will have different useful signals.

Examples:

- A screenshot may be found through OCR.
- A photo may be found through image description.
- A large saved collection may be found through its summary.
- A memory may be constrained by timestamp.
- A source-specific query may use source metadata.
- A conceptual query may use embeddings.

---

# 13. M12 — No-Result Behavior

If there are no sufficiently relevant results:

```text
Search
  ↓
No reliable matches
  ↓
“No memories found.”
```

Do **not** show unrelated or weakly related memories merely to populate the results screen.

The application may provide a query-refinement suggestion such as:

> Did you mean “local LLMs”?

or:

> Did you mean “Android AI models”?

However:

**Do not automatically display loosely related memories as fallback results.**

The user can refine the query and search again.

---

# 14. M12 — Search Does Not Generate Answers

M12 is a retrieval feature, not a chatbot.

Locked behavior:

```text
Search Query
     ↓
Memory Retrieval
     ↓
Ranked/Relevant Memories
     ↓
Display Results
```

It should **not currently** do:

```text
Question
  ↓
Retrieve memories
  ↓
LLM synthesis
  ↓
Generated answer
```

A future feature could potentially build on retrieval, but answer generation is not part of M12–M15.

The product identity remains a personal memory/knowledge retrieval system rather than another general-purpose AI assistant.

---

# 15. M13 — Semantic Search

## Status: LOCKED

Definition:

> **Search by meaning rather than requiring exact textual matches, using local embeddings/vector retrieval.**

The existing local embedding infrastructure is used for semantic retrieval.

Conceptually:

```text
User query
    ↓
Query embedding
    ↓
Local vector search
    ↓
Semantically similar memories
    ↓
Ranking
    ↓
Results
```

Semantic search is an additional retrieval mechanism. It does **not** replace keyword/exact search.

---

# 16. M13 — Example

User:

```text
things about running AI models on phones
```

Memory:

```text
Deploying quantized LLMs using ONNX Runtime Mobile on Android
```

Even if the exact phrase “running AI models on phones” does not occur, the memory can be retrieved because the concepts are semantically related.

---

# 17. M14 — Contextual Retrieval

## Status: LOCKED

Definition:

> **Understand vague or indirect natural-language references to memories rather than requiring exact keywords.**

Example:

```text
that laptop I saw
```

The user may not know:

- product name
- exact page title
- URL
- exact wording
- exact date

The system should use available memory representations and metadata to determine what the user is likely referring to.

Potential signals include:

```text
Semantic meaning
Memory summary
Image description
OCR
Categories
Metadata
Timestamp/context when relevant
```

The system then retrieves and ranks candidate memories.

---

# 18. M14 — Contextual Retrieval Is Not Just Vector Similarity

A contextual query may contain references and implied meaning.

Example:

```text
that laptop I saw with the good GPU
```

Possible interpretation:

```text
Object/topic:
Laptop

Additional semantic clue:
Good/high-performance GPU

Reference language:
“that”
```

The system can combine these signals when retrieving memories.

The exact contextual/query-understanding implementation is intentionally not locked.

---

# 19. M15 — Temporal Retrieval

## Status: LOCKED

Definition:

> **Understand time-related natural-language queries and use stored memory timestamps to constrain retrieval.**

Examples:

```text
What did I save yesterday?
```

```text
That thing I saw last week.
```

```text
Show me the laptop stuff I saved two days ago.
```

```text
What did I save in July about AI?
```

The system should convert temporal language into useful time constraints against stored memory timestamps.

Conceptually:

```text
User Query
    ↓
Temporal understanding
    ↓
Time constraint
    ↓
Memory retrieval
```

---

# 20. M15 — Combined Temporal + Semantic Retrieval

Example:

```text
AI stuff I saved last week
```

Possible query interpretation:

```text
Semantic intent:
Artificial Intelligence

Temporal constraint:
Last week
```

The retrieval engine should use both constraints.

This is not a separate search system. Temporal retrieval is one dimension of unified retrieval.

---

# 21. M13 + M14 + M15 Are One Unified Retrieval System

This is an important architectural decision.

Do **not** build three unrelated search engines.

The intended architecture is:

```text
                         SEARCH QUERY
                              │
                              ↓
                     Query Understanding
                              │
             ┌────────────────┼────────────────┐
             ↓                ↓                ↓
        Exact terms      Semantic meaning   Context/time
             │                │                │
             ↓                ↓                ↓
       Keyword search     Vector search    Metadata/
                                            constraints
             └────────────────┼────────────────┘
                              ↓
                     Candidate Memories
                              ↓
                           Ranking
                              ↓
                      Search Results
```

M13, M14 and M15 are capabilities within the same retrieval system.

---

# 22. Combined Example

Consider:

> “That laptop I saw last week that had a really good GPU.”

The system can derive:

```text
Contextual clue:
“that laptop I saw”

Semantic concepts:
Laptop
High-performance GPU

Temporal constraint:
Last week
```

Then:

```text
Query
 ↓
Context understanding
 ↓
Semantic retrieval
 ↓
Temporal filtering
 ↓
Candidate memories
 ↓
Ranking
 ↓
Relevant memories
```

This is the intended behavior.

---

# 23. Relationship Between M11 and M12–M15

M11 provides a compact semantic description that can become another useful representation for retrieval.

Example:

```text
Original memory:
Many screenshots + URLs + text

Summary:
Collection of resources about local AI
 development on Android.
```

A future query:

```text
local AI Android resources
```

may retrieve the memory using the summary's semantic information.

However:

**The summary must not replace the original content or original embeddings.**

It is an additional derived representation.

---

# 24. Relationship Between M10 and M12–M15

M10 categories are another structured retrieval signal.

Example:

```text
Query:
AI stuff I saved last week
```

Potential interpretation:

```text
Category:
Artificial Intelligence

Time:
Last week
```

The retrieval engine can combine this with semantic retrieval.

Categories are not the only retrieval signal.

---

# 25. Unified Retrieval Model

At the product level, the eventual search system can conceptually combine:

```text
User Query
│
├── Exact terms
├── Semantic meaning
├── Contextual references
├── Temporal constraints
├── Categories
├── Source
└── Other metadata
        ↓
Unified Retrieval
        ↓
Candidate memories
        ↓
Ranking
        ↓
Results
```

The following are intentionally **not locked** at the product stage:

- exact ranking formula
- vector similarity weighting
- keyword/vector weighting
- reranking algorithm
- minimum relevance threshold
- number of candidates retrieved
- query parsing implementation
- embedding model implementation details
- database query implementation
- indexing strategy
- fallback threshold behavior

These belong to backend/engineering design.

---

# 26. Important Product Boundaries

Codex should preserve these boundaries during implementation.

### Do not turn M11 into exhaustive summarization

The goal is a useful high-level description, not an inventory of every item.

### Do not send arbitrarily large memories to an LLM

Use bounded AI-analysis input.

### Do not delete original information after AI processing

The original memory remains authoritative.

### Do not make M12 a chatbot

Search retrieves memories.

### Do not require manual search filters

Natural-language search provides context.

### Do not replace keyword search with semantic search

Both exact and semantic retrieval are needed.

### Do not build separate search engines for M13, M14 and M15

They are dimensions of unified retrieval.

### Do not show weak results just because search returned zero strong results

“No memories found” is preferable to misleading results.

### Do not invent missing information

If information is unavailable, it remains unknown.

---

# 27. Final Locked Feature Summary

| Feature | Locked Product Behavior |
|---|---|
| **M11 — Content Summarization** | Generate a concise high-level description of a memory using a bounded representative AI-analysis input. |
| **M12 — Memory Search** | Central search experience supporting exact, semantic, and natural-language memory retrieval. |
| **M13 — Semantic Search** | Retrieve memories by meaning using local embeddings/vector retrieval. |
| **M14 — Contextual Retrieval** | Understand vague/indirect references using semantic meaning and available memory information. |
| **M15 — Temporal Retrieval** | Understand natural-language time references and use memory timestamps for retrieval. |

---

# 28. Final End-to-End Concept

```text
                    USER CAPTURES MEMORY
                            │
                            ↓
                   MEMORY PROCESSING
                            │
        ┌───────────────────┼───────────────────┐
        ↓                   ↓                   ↓
      Content            Metadata           Embedding
        │                   │                   │
        ├── Text            ├── Source         └── Vector
        ├── OCR             ├── Timestamp
        ├── Images          ├── URLs
        └── Description     └── Categories
                            │
                            ↓
                  BOUNDED AI ANALYSIS
                            │
                            ↓
                         M11
                       SUMMARY
                            │
                            ↓
                  COMPLETE MEMORY
                            │
                            ↓
                       USER SEARCH
                            │
                            ↓
                  ┌─────────┴─────────┐
                  │                   │
             M12 SEARCH         QUERY UNDERSTANDING
                                      │
                       ┌──────────────┼──────────────┐
                       ↓              ↓              ↓
                     M13            M14            M15
                   Semantic       Contextual      Temporal
                    Search        Retrieval       Retrieval
                       │              │              │
                       └──────────────┼──────────────┘
                                      ↓
                             UNIFIED RETRIEVAL
                                      ↓
                                  RANKING
                                      ↓
                              MEMORY RESULTS
```

---

# 29. Implementation Philosophy for Codex

When implementing M11–M15:

1. **Preserve the original memory as the authoritative source.**
2. Treat summaries, categories, metadata, and descriptions as **derived information**.
3. Keep AI analysis bounded and configurable.
4. Reuse existing processing outputs instead of repeatedly analyzing raw data.
5. Build search as a unified retrieval layer rather than independent feature implementations.
6. Keep exact search, semantic search, contextual interpretation, and temporal constraints composable.
7. Keep search result generation separate from future LLM answer generation.
8. Do not prematurely hard-code product decisions where implementation details were intentionally left open.
9. Prefer local processing for retrieval operations wherever practical.
10. Preserve enough structured information that future retrieval capabilities can be added without reprocessing the original memory.

---

# 30. Locked Status

The following are **locked at the product level**:

- M11 Content Summarization
- M12 Memory Search
- M13 Semantic Search
- M14 Contextual Retrieval
- M15 Temporal Retrieval

The detailed backend implementation remains open for engineering design.

**Do not reinterpret these features as separate disconnected systems. M11 creates useful derived context; M12 is the search experience; M13–M15 are complementary retrieval capabilities operating through a unified retrieval architecture.**
