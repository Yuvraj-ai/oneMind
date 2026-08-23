# Phase 4 Spec: Unified Retrieval — The Payoff

**Status:** ready-for-agent  
**Blocked by:** Phase 2 (Processing Pipeline), Phase 3 (Capture Paths)

---

## Problem Statement

The user has hundreds or thousands of enriched memories — each with text, OCR, embeddings, categories, timestamps, source metadata, and summaries. But the only way to find something is scrolling the chronological feed or filtering by source. The core promise of oneMind — "I know I saved it somewhere" and the app finds it — is still undelivered. The user needs to type a vague, natural-language description of what they remember and get back the right memory.

## Solution

Build Unified Retrieval: a single search system exposed through the main search bar that combines exact keyword matching, semantic vector search, contextual reference interpretation, and temporal constraint understanding. No manual filters — the user just describes what they're looking for in natural language, and the system figures out the rest.

## User Stories

### Core Search (M12)

1. As a user, I want to type in the search bar and find relevant memories, so that I can locate something I saved without browsing manually.
2. As a user searching for "Qwen3", I want memories containing that exact term found instantly, so that keyword search works for precise terms.
3. As a user searching for "that AI model article", I want semantically related memories returned even if they don't contain those exact words, so that I don't need to remember exact phrasing.
4. As a user, I want search results ranked by relevance, so that the most likely match appears first.
5. As a user, I want search results to show a preview (thumbnail, summary snippet, source icon, timestamp), so that I can identify the right memory without opening each one.
6. As a user whose search returns no good matches, I want to see "No memories found" rather than unrelated results, so that I'm not misled.
7. As a user, I want search to feel fast (results within 1-2 seconds), so that finding memories doesn't feel sluggish.
8. As a user, I want to search across ALL my content (text, OCR, image descriptions, URLs, summaries, entity names), so that nothing is hidden from search.
9. As a user, I do NOT want separate search modes or manual filters, so that one text box handles everything.

### Semantic Search (M13)

10. As a user searching "things about running AI on phones", I want to find a memory containing "Deploying quantized LLMs using ONNX Runtime Mobile on Android", so that meaning-based matching works.
11. As a user, I want semantic search to use my locally-stored embeddings, so that search works offline without cloud calls.
12. As a user, I want semantic and keyword results blended together, so that I get the best matches regardless of method.

### Contextual Retrieval (M14)

13. As a user searching "that laptop I saw", I want the system to find a memory containing a laptop product page even though I didn't use the product name, so that vague references work.
14. As a user searching "the recipe with chicken", I want the system to use all available memory information (text, OCR, description, categories) to find the match, so that indirect references resolve correctly.
15. As a user, I want the system to handle ambiguous queries by showing the top candidates ranked, so that I can pick the right one when multiple memories might match.

### Temporal Retrieval (M15)

16. As a user searching "what did I save yesterday", I want only memories from yesterday returned, so that time-based queries work.
17. As a user searching "AI stuff from last week", I want both the temporal constraint (last week) and the semantic intent (AI) applied together, so that combined queries narrow results effectively.
18. As a user searching "that thing I saved two days ago", I want temporal understanding combined with any other context in the query, so that time is one signal among many.
19. As a user, I want relative time expressions ("yesterday", "last week", "two days ago", "in July") understood naturally, so that I don't need exact dates.
20. As a user searching "things I saved today", I want to see today's memories regardless of other query terms, so that temporal queries can stand alone.

### Query Understanding

21. As a user typing "AI stuff I saved from Chrome last week", I want the system to decompose this into: semantic intent (AI), source (Chrome), time (last week) — all from one natural-language input.
22. As a user, I want the system to handle queries that combine multiple dimensions without me needing to use a special syntax.
23. As a user making a typo ("reciepe"), I want the system to still find relevant results, so that minor errors don't break search.

## Implementation Decisions

### Query Understanding Layer

- Every search query passes through a Query Understanding module before retrieval.
- The module uses the Active AI Provider (LLM) to decompose natural-language queries into structured intent:
  - Semantic keywords/concepts (for embedding similarity)
  - Exact terms (for keyword matching)
  - Temporal constraints (parsed into date ranges)
  - Source constraints (parsed into source type/package)
  - Category hints (mapped to Category Dictionary)
- For simple queries (single word, obvious keyword), skip LLM decomposition and go straight to keyword + vector search. Only invoke the LLM for complex/ambiguous queries.
- Fallback: if the LLM is unavailable or fails, treat the entire query as keywords + compute query embedding for vector search.

### Keyword/Exact Search

- Full-text search index over: user text, OCR text, vision descriptions, summaries, entity names, URL domains.
- Use Room's FTS (Full-Text Search) support or SQLite FTS5.
- Matches on exact terms, partial matches, prefix matches.
- Returns memories with keyword match scores.

### Semantic/Vector Search (M13)

- Compute a query embedding using the same Embedding Model used for memory embeddings.
- Perform similarity search (cosine similarity) against stored memory vectors.
- Return top-K candidates with similarity scores.
- At thousands-scale: flat index scan is acceptable (<100ms for 5K vectors on modern hardware). If performance degrades, introduce IVF-flat partitioning.

### Contextual Interpretation (M14)

- Context clues in the query (demonstrative references like "that", object descriptions, partial information) are handled by the combination of:
  - LLM query decomposition (extracts the conceptual intent)
  - Semantic search (finds meaning-similar memories)
  - Metadata matching (categories, entities help disambiguate)
- No separate "contextual engine" — it's the LLM understanding + vector search + metadata working together.

### Temporal Understanding (M15)

- The Query Understanding layer parses temporal expressions into absolute date ranges:
  - "yesterday" → [start of yesterday, end of yesterday]
  - "last week" → [7 days ago, today]
  - "in July" → [July 1, July 31] of the most recent July
  - "two days ago" → [start of that day, end of that day]
- Temporal constraints become WHERE clauses on the `createdAt` field.
- Combined with other retrieval signals (semantic, keyword, source).

### Unified Ranking

- Candidate memories come from multiple sources:
  - Keyword matches (with FTS scores)
  - Vector similarity matches (with cosine scores)
  - Metadata/filter matches (binary: passes filter or not)
- Rank using a weighted combination:
  - Normalize keyword and vector scores to [0, 1]
  - Weight: configurable, starting point ~0.4 keyword + 0.6 semantic (semantic tends to be more useful for "I forgot the exact words" queries)
  - Temporal and source constraints are hard filters (not soft scoring)
  - Category matches can boost score
- Return top results above a minimum relevance threshold.
- If no results above threshold: return empty ("No memories found"), not weak matches.

### Search Results UI

- Results displayed as memory cards (same component as the feed, possibly with highlighted matching snippets).
- Each result shows: thumbnail (if image), summary or matching text snippet, source icon, timestamp, category chips.
- Results appear as the user types (debounced ~300ms).
- Tap result → opens full memory view.

### Performance Budget

- Search-to-results: <2 seconds for combined keyword + semantic search over 5K memories.
- Query embedding generation: <500ms on-device.
- LLM query decomposition (for complex queries): <2 seconds. For simple queries, skip LLM and stay under 500ms total.
- Vector search over 5K 384-dim vectors: <100ms (flat scan is fine at this scale).

### Offline Behavior

- Keyword search and vector search work fully offline (all data is local).
- LLM query decomposition works offline if using a local model.
- If no model is available (rare edge case): fall back to pure keyword + vector search without decomposition.

## Testing Decisions

### Seams to test

1. **Query Understanding seam**: Given a natural-language query → produces structured intent (keywords, temporal range, source filter, semantic concepts). Given a simple keyword → skips LLM, returns raw keywords.
2. **Keyword search seam**: Given search terms → FTS returns matching memories with scores. Given no matches → returns empty set.
3. **Vector search seam**: Given a query embedding → returns top-K memories by cosine similarity above threshold. Given a query unrelated to all memories → returns empty set.
4. **Temporal parsing seam**: Given "yesterday", "last week", "in July", "two days ago" → produces correct absolute date ranges relative to current time.
5. **Unified ranking seam**: Given keyword results + vector results + filters → produces a single ranked list. Given all results below threshold → returns empty.
6. **End-to-end retrieval seam**: Given a complex query like "AI stuff from Chrome last week" → returns memories matching all constraints.

### Testing approach

- Unit tests for temporal parser (many edge cases: year boundaries, ambiguous months).
- Unit tests for ranking/scoring normalization.
- Integration tests for FTS + vector search combination.
- Mock-LLM tests for query decomposition.
- Performance benchmarks: search latency at 1K, 5K, 10K memories.
- Quality tests: predefined queries against a set of known memories, verify correct recall.

## Out of Scope

- Answer generation / chatbot synthesis from retrieved memories — explicitly excluded (oneMind is retrieval, not a chatbot)
- Re-ranking using a separate re-ranker model — future optimization
- "Did you mean..." spelling correction — nice-to-have, not required for v1
- Search history / saved searches — future
- Search within a specific date range via manual date picker — against the "no manual filters" philosophy
- Cross-memory knowledge graph queries ("which companies appear in multiple memories?") — future
- Backup / export of search index — future

## Further Notes

- The decision to skip LLM decomposition for simple queries is critical for perceived speed. A single-word search should feel instant. Only multi-word, natural-language queries benefit from LLM interpretation.
- The minimum relevance threshold needs tuning with real data. Start conservative (show fewer, more relevant results) and relax if users complain about missing results.
- At v1 scale (thousands of memories), vector search is not the bottleneck — LLM query decomposition is. Optimize by caching common query patterns or using a lightweight intent classifier before reaching for the full LLM.
- The search bar is visible from Phase 1 (as a placeholder). In Phase 4 it becomes functional. This continuity in UX means the user already knows where to search — they just couldn't until now.
- Consider indexing the summary field particularly heavily in FTS — summaries are information-dense and likely to match user queries well.
