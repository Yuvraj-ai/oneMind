# Phase 2 Spec: Processing Pipeline — Make Memories Intelligent

**Status:** ready-for-agent  
**Blocked by:** Phase 1 (Foundation)

---

## Problem Statement

Memories captured in Phase 1 are stored but inert — just raw text and images sitting in a database. The user can browse them but cannot search semantically, see categories, get summaries, or benefit from any AI understanding. The "I know I saved it somewhere" problem remains unsolved until memories are enriched with machine-understandable representations.

## Solution

Build the asynchronous Processing Pipeline that takes every SAVED Memory and enriches it: OCR for images, optional vision analysis, structured metadata extraction, embedding generation for vector search, automatic categorization from a controlled dictionary, and bounded summarization. All processing happens in the background without blocking the user.

## User Stories

1. As a user, I want my saved memories to be automatically processed in the background, so that I don't have to manually tag or organize anything.
2. As a user, I want to see a processing indicator on memories that haven't finished enrichment, so that I know the system is working.
3. As a user who saved a screenshot, I want the text in that image automatically extracted via OCR, so that I can later find it by searching for that text.
4. As a user with a vision-capable model, I want images described in natural language, so that photos without text are still understandable to the system.
5. As a user without a vision-capable model, I want the app to gracefully skip vision analysis rather than error, so that my experience isn't broken.
6. As a user, I want URLs in my memories detected and stored as structured metadata, so that link-heavy memories are parseable.
7. As a user, I want dates and times mentioned in my content extracted, so that temporal retrieval can work later.
8. As a user, I want named entities (people, companies, products, places) extracted from my content, so that the system understands what my memories are about.
9. As a user, I want each memory to receive an embedding vector, so that semantic search can find it by meaning rather than exact words.
10. As a user, I want my memories automatically categorized from a controlled set of topics, so that I can later filter or search by category.
11. As a user, I want a single memory to have multiple categories, so that content about "AI travel conference" appears under both Technology and Travel.
12. As a user, I want a concise summary generated for each memory, so that I can quickly understand what a memory contains without opening it.
13. As a user, I want summaries shown on memory cards in the feed, so that browsing is more informative than just seeing truncated raw text.
14. As a user who edits a memory, I want the derived information (embeddings, categories, summary) to be regenerated, so that enrichment stays current.
15. As a user, I want failed processing to not destroy my memory, so that capture is never lost due to an AI error.
16. As a user, I want processing to be efficient with my battery, so that saving 20 memories doesn't drain my phone.
17. As a user, I want the system to handle memories with no useful text (e.g. a scenic photo with no OCR text), so that such memories still get categorized and described.
18. As a user, I want the same quality of enrichment regardless of whether my memory is text-only, image-only, or multimodal, so that every memory type is a first-class citizen.
19. As a user, I want large memories (many images, long text) to be processed without sending everything to the LLM, so that processing stays fast and within model limits.
20. As a user, I want to see my memory transition from "processing" to "ready" in the feed, so that I know when enrichment is complete.

## Implementation Decisions

### Processing Pipeline Orchestrator

- Use WorkManager for background job scheduling (survives app kills, respects battery).
- A Memory transitions from SAVED → PROCESSING when its job starts, and PROCESSING → READY on completion.
- Pipeline stages run sequentially per memory: OCR → Vision → Metadata Extraction → Embedding → Categorization → Summarization.
- Each stage is independently failable — a failed OCR should not prevent embedding generation from the text content.
- On edit of a READY memory: mark derived data stale, re-enqueue for full reprocessing.
- Batch-aware: if 20 memories arrive at once, process them one at a time (not all concurrently) to avoid resource exhaustion.

### M4: OCR

- Use Google ML Kit Text Recognition (on-device, no cloud).
- Run on every image content block in a Memory.
- Store results per-image: status (SUCCESS / EMPTY / FAILED) + extracted text.
- EMPTY is valid (a scenic photo has no text). FAILED means the recognizer errored.
- OCR text becomes input for metadata extraction and embedding.

### M4: Vision Analysis

- Check whether the Active AI Provider reports vision capability.
- If supported: send image to the provider with a prompt requesting a concise description.
- If not supported: mark vision status as NOT_SUPPORTED (not FAILED).
- Store: status + description text + provider/model used.
- Vision description becomes input for metadata extraction and embedding.
- Never silently upload images — only use the user's configured provider.

### M5: Metadata Extraction

- Input: all available text for a Memory (user text + OCR text + vision descriptions).
- Extract via LLM prompt or rule-based parsing (hybrid approach):
  - URLs: regex-based detection, store raw URL + domain.
  - Temporal references: LLM-assisted extraction of dates/times mentioned in content (distinct from capture timestamp).
  - Entities: LLM-assisted extraction of people, organizations, products, places, events, technologies.
  - Source metadata: already known from capture path (MANUAL, SCREENSHOT, SHARE, CLIPBOARD).
- Store entity extractions with type classification.
- Confidence scores optional — store if the LLM provides them, don't fabricate them.

### Embedding Generation

- Use the team-selected embedding model (downloaded in Phase 1).
- Input: concatenation of meaningful text representations (user text + OCR + vision description + extracted entity names).
- Output: fixed-dimension vector stored in local vector database.
- Vector storage: use a lightweight local vector DB suitable for Android (e.g. ObjectBox vector, custom HNSW implementation over SQLite, or a flat index for v1 at thousands-scale).
- On memory edit: delete old embedding, generate new one.

### M10: Categorization

- Maintain a Category Dictionary as a structured resource (JSON/room table) within the app.
- Dictionary contains ~30-50 broad categories covering common topics (Technology, Finance, Travel, Shopping, Entertainment, Education, Health, Food, Sports, Science, Work, Personal, News, etc.).
- Prompt the Active AI Provider: given the memory's text representations + metadata, select all applicable categories from the dictionary.
- The LLM selects — it never invents new category names.
- A Memory can have multiple categories.
- Store as a many-to-many relationship between Memory and Category.

### M11: Summarization

- Construct the Bounded AI-Analysis Input:
  - Text: first N characters/tokens of concatenated user text + OCR (configurable limit).
  - Images: information from first ~5-6 images (OCR text + vision description).
  - URLs: first ~5-6 URLs with any available title metadata.
- Prompt the Active AI Provider: "What is this memory generally about? One concise paragraph."
- Store the summary as a derived field on the Memory.
- Summary is displayed on memory cards in the feed.
- On memory edit: invalidate and regenerate.

### Processing State Visibility

- Memory cards in the feed show a subtle processing indicator (spinner, progress dots) when state is PROCESSING.
- Once READY: show the summary on the card, categories as chips/tags.
- On FAILED: show a retry option. Memory content remains accessible regardless.

### Stale Data Handling

- When source content changes (user edits): all derived data is marked stale.
- Re-enqueue for full pipeline reprocessing.
- Old embedding is deleted from vector store before new one is inserted (no duplicates).
- Old summary/categories are replaced, not appended.

## Testing Decisions

### Seams to test

1. **Processing Pipeline orchestrator seam**: Given a SAVED Memory → pipeline runs all stages in order → Memory reaches READY with all derived fields populated. Given a stage failure → Memory reaches READY with partial enrichment (failed stage is marked FAILED, others succeed).
2. **OCR seam**: Given an image → ML Kit produces text or EMPTY. Given a corrupt image → produces FAILED.
3. **LLM Provider seam (for vision, metadata, categorization, summarization)**: Given a prompt → returns structured output. Given NOT_SUPPORTED capability → skips gracefully.
4. **Embedding seam**: Given text input → embedding model produces a vector of correct dimensionality. Given empty input → handles gracefully.
5. **Category assignment seam**: Given memory content + Category Dictionary → LLM returns only categories that exist in the dictionary. Never invents new ones.
6. **Stale data seam**: Given an edit to a READY memory → all derived fields are regenerated, old embedding removed.

### Testing approach

- Unit tests for each pipeline stage in isolation (mock LLM provider, mock ML Kit).
- Integration test for full pipeline flow with a mock provider.
- Property test for categorization: output must always be a subset of the Category Dictionary.
- State machine tests: verify all valid transitions and rejection of invalid ones.

## Out of Scope

- Screen capture (M1) — Phase 3
- Share/Import (M2) — Phase 3
- Quick Settings clipboard tile — Phase 3
- Search / retrieval (M12-15) — Phase 4
- Knowledge graph / entity relationships — future
- Object-level image indexing — future
- Proactive memory surfacing — future
- Category Dictionary design (the exact list of ~30-50 categories) — a design task within this phase, but the spec doesn't freeze the list
- Exact LLM prompts — engineering decisions, will be iterated

## Further Notes

- The Category Dictionary should be designed as one of the first tasks in this phase. It needs to be broad enough to cover real-world content but small enough that the LLM can reliably select from it. Start with ~30 categories, expand to ~50 if gaps appear during testing.
- The Processing Pipeline should be designed to easily add new stages in future (e.g. a "related memories" linking stage). Stages should be pluggable.
- At v1 scale (thousands of memories), a flat or IVF-flat vector index is fine. HNSW is overkill until 10K+ memories. Don't over-engineer the vector storage.
- Processing should respect device idle/charging state where practical (WorkManager constraints), but should not defer so aggressively that memories sit unprocessed for hours. A reasonable balance: process immediately if on WiFi/charging, process within 15 minutes otherwise.
