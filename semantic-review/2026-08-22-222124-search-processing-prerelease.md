# Correctness review: search and processing-pipeline packages

A read of every file in `domain/search` (18 files) and `domain/processing` (21 files), plus the callers that decide whether a finding is reachable: `FeedViewModel`, `SearchResultCard`, `SearchIndexDao`, `MemorySearchIndexEntity`, `MemoryRepositoryImpl`, `ProcessingWorker`, `ProcessingScheduler`. The packages are careful — the sanitisation, half-open temporal ranges, division guards, and stage ordering all hold up under scrutiny. The defects that remain are concentrated in three places: a month-name rule that fires on ordinary English, a tokenisation disagreement between the FTS index and the code that scores it, and a pipeline state machine with no way out of `PROCESSING`.

Watch for: **(confirmed)** a bare "may" anywhere in a query becomes a hard date filter that excludes almost everything; **(confirmed)** `KeywordScoring` scores 0 and discards rows that FTS matched on a token after a dot ("js" against "node.js"), which is the exact failure its own docstring says must not happen; **(confirmed)** a Memory whose worker is stopped mid-run is stranded in `PROCESSING` forever, with a permanent spinner and no retry path.

**Verdict**: NEEDS_CHANGES

## High-level view

The temporal parser is the weakest link, and not because of the boundary cases it was clearly designed around — those are right, and well tested. Its month-name rule matches a bare month name with no preposition required, so the English modal verb "may" resolves to a calendar month and `SearchOrchestrator` applies it as an absolute filter. The same parser slices the user's original query using character offsets taken from a lowercased copy of it, which is only sound while lowercasing preserves length.

Keyword scoring reimplements tokenisation rather than sharing SQLite's. `FtsQuery` keeps `.` inside a term so that domains survive as one unit; FTS4's `simple` tokenizer treats `.` as a separator. The two therefore disagree about what a document contains, in one direction only: prefix queries work, queries for a segment after a dot are found by the index and then thrown away by the scorer's own filter. The same disagreement means the pure-OR contract `FtsQuery` documents is not what SQLite actually evaluates for a dotted term.

Ranking is arithmetically sound — nothing exceeds `[0, 1]`, no division by zero — but the relevance threshold is set below the floor that any one- or two-term keyword match can produce, so for the most common query shapes it filters nothing and the "no results is better than misleading results" guarantee does not hold.

The pipeline's stage ordering and re-read-between-stages discipline are correct: every stage genuinely sees what its predecessors persisted. The state machine around it is not. `PROCESSING` is written before the first stage and is not an eligible entry state, `markFailed` is only reachable from the worker's exhausted-retry branch, and the UI only offers retry on `FAILED` — so any interruption between those two transitions is terminal. The stage loop's `catch (Exception)` also swallows `CancellationException`, which turns cooperative cancellation into a recorded stage failure and breaks the worker's own failure handling.

`FtsQuery`'s allow-list holds. I could not construct any input that reaches `MATCH` as syntax, and the DAO binds the expression as a parameter, so there is no injection surface. No regex in either package can backtrack catastrophically.

<details>
<summary>Issues (12)</summary>

1. **Bare month name as hard filter** — `parseMonthName` matches "may" and "march" with no preposition required, producing a hard `createdAt` filter that excludes nearly everything. Require a preposition or a nearby year for the ambiguous names, or drop "may"/"march" from bare matching.
2. **Matched text also corrupts the semantic query** — `query.replace(matchedText, " ")` replaces every occurrence, so a spurious "may" match rewrites "maybe" to " be". Replace only at the matched offset.
3. **Scorer discards rows FTS matched after a dot** — `documentTerms.count { it.startsWith(queryTerm) }` cannot match "js" against the term "node.js", so `KeywordSearcher`'s `score > 0.0` filter drops the row. Have `FtsQuery.documentTerms` split on `.` as well, or match query terms against dot-separated sub-tokens.
4. **Memory stranded in `PROCESSING`** — nothing transitions a Memory out of `PROCESSING` when the worker is stopped mid-run, and neither the pipeline nor the UI accepts it as an entry state. Add `PROCESSING` recovery: reconcile stale rows on startup, or let `markFailed` run outside the cancelled scope.
5. **`CancellationException` recorded as stage failure** — `catch (e: Exception)` in the stage loop catches cancellation. Rethrow `CancellationException` before the generic catch; do the same in `ProcessingWorker.doWork`.
6. **`markFailed` cannot run after cancellation** — it is a suspend call invoked from an already-cancelled coroutine, so `FAILED` is never written. Wrap in `withContext(NonCancellable)`.
7. **`MINIMUM_RELEVANCE` unreachable for short queries** — 0.15 sits below the 0.17 floor of a two-term single-match and the 0.352 floor of a one-term match, so it filters nothing for the commonest queries. Raise it, or make coverage of a one-term query mean less than 1.0.
8. **Query indices taken from a lowercased copy** — `TemporalExpressionParser` slices `original` with offsets from `lower`; `İ` (U+0130) lowercases to two characters, shifting every later offset and throwing `StringIndexOutOfBoundsException`. Match case-insensitively against the original instead of pre-lowercasing.
9. **Same lowercase shift in `SnippetExtractor`** — `findMatches` builds ranges in a lowercased copy and applies them to the original; degrades to misplaced or silently dropped highlights rather than crashing. Same fix.
10. **Partial OCR/vision failure reported as `Empty`** — four failed images plus one empty yields `StageResult.Empty`, so the failures are invisible. Add an `any { FAILED } && none { SUCCESS } -> Failed` branch.
11. **Category boost clamped away at the top** — a perfect keyword plus perfect semantic plus boost is 1.05, coerced to 1.0, so the boost cannot break the tie it exists to break. Apply the boost before normalising, or reserve headroom.
12. **`FtsQuery` pure-OR contract unverified against SQLite** — a dotted term reaches the FTS parser as two tokens, so `github.com* OR ramen*` is not a flat OR. No instrumented test issues a `MATCH` built by `FtsQuery`. Add one.

</details>

<details>
<summary>Details</summary>

### "may" becomes a date filter

`TemporalExpressionParser.kt:132`:

```kotlin
val match = Regex("""\b(?:in\s+)?(${MONTHS.keys.joinToString("|")})\b""")
    .find(lower) ?: return null
```

The `in` is optional, so any standalone month name matches. `MONTHS` contains "may" and "march", both of which are far more common in English as a verb than as a month.

Trigger: `the ramen recipe I may have saved`. No named-relative phrase matches, so `parse` falls through to `parseMonthName` (line 53), which returns the whole of the most recent May. `SearchOrchestrator.satisfiesConstraints` treats that as absolute:

```kotlin
intent.temporal?.let { window ->
    if (memory.createdAt !in window) return false
}
```

Every Memory not created in that one month is discarded before ranking, and the user sees "No memories found" with nothing to explain it. The parser's own docstring names this failure mode — "A wrong date filter hides the Memory the user is looking for and gives them no clue why" — and the ambiguity check it does perform (`Ambiguous input returns null rather than a guess`) does not cover this case. `QueryUnderstanding.kt:56` runs the temporal parser unconditionally, including on one-word queries, so there is no complexity gate in front of it.

The damage compounds through `QueryUnderstanding.kt:60`:

```kotlin
query.replace(it.matchedText, " ")
```

`replace` is global. For the query `may maybe`, `matchedText` is `"may"`, and the semantic query becomes `"be"`. The temporal offsets are already known at that point, so replacing at the matched range instead of by value costs nothing.

`\b` does protect against the substring cases — "mayor" and "maybe" are not matched as month names themselves — so the fix is about the standalone word only.

### Index and scorer disagree about dots

`MemorySearchIndexEntity.kt:39` is a bare `@Fts4`, so the table uses SQLite's `simple` tokenizer, which treats every non-alphanumeric ASCII byte — including `.` — as a separator. A document containing `node.js` is indexed as two tokens, `node` and `js`.

`FtsQuery.kt:31` deliberately keeps dots:

```kotlin
private val ALLOWED = Regex("""[^\p{L}\p{N}.]+""")
```

so `FtsQuery.documentTerms("node.js")` returns `["node.js"]` — one term, not two.

Now search for `js`. `FtsQuery.build` produces `js*`; FTS matches the row on its `js` token and returns it. Then `KeywordScoring.kt:64`:

```kotlin
val occurrences = documentTerms.count { it.startsWith(queryTerm) }
```

`"node.js".startsWith("js")` is false, so `matchedTermCount` stays 0, `score` returns 0.0 (line 73), and `KeywordSearcher.kt:47` drops the row:

```kotlin
.filter { it.score > 0.0 }
```

`KeywordScoring`'s docstring describes precisely this: *"a row matched on a prefix could score zero and be dropped by a threshold — found by the index, then discarded by our own arithmetic."* The prefix direction it was written to protect works; the suffix-after-a-dot direction does not, which is why the asymmetry survived.

Reachable inputs, all plausible for an app that indexes OCR text and URLs: `js` against "node.js", `png` against "screenshot.png", `pdf` against "report.pdf", `kt` against "Main.kt", `com` against "github.com". Two-character terms clear `MIN_TERM_LENGTH`, so none of these are filtered earlier.

Confidence: confirmed on the Kotlin side. The FTS4 tokenizer behaviour is standard SQLite, but no test in the repository exercises it — see the last item below.

### Nothing recovers a Memory from `PROCESSING`

`ProcessingPipeline.kt:39-44` writes `PROCESSING` before any stage runs, and refuses to re-enter from it:

```kotlin
if (entryState !in ELIGIBLE_ENTRY_STATES) {
    return PipelineOutcome.NotEligible(entryState)
}
memoryRepository.transitionState(memoryId, ProcessingState.PROCESSING)
```

`ELIGIBLE_ENTRY_STATES` is `{SAVED, EDITED, FAILED}`. The only transition out of `PROCESSING` other than the `READY` at line 70 is `markFailed`, which is reached solely from `ProcessingWorker`'s exhausted-retry branch. `FeedViewModel.retryProcessing` returns early for anything that is not `FAILED`.

So an interruption between line 44 and line 70 is terminal. That is not a rare path. `ProcessingScheduler` sets `setRequiresBatteryNotLow(true)`, and WorkManager stops a running worker when its constraint stops being satisfied; process death does the same. On the rescheduled run, `run()` re-reads the Memory, sees `PROCESSING`, returns `NotEligible`, and `ProcessingWorker` treats that as `Result.success()` by design. The Memory keeps the `PROCESSING` spinner in the feed indefinitely, with no user action available.

Confidence: confirmed for the code paths. Whether WorkManager reschedules or cancels in the specific battery case is worth confirming on-device, but process death alone reaches the same state.

### Cancellation recorded as a stage failure

`ProcessingPipeline.kt:60-67`:

```kotlin
results[stage.id] = try {
    stage.process(current)
} catch (e: Exception) {
    StageResult.Failed(...)
}
```

`kotlinx.coroutines.CancellationException` is a `java.util.concurrent.CancellationException`, which extends `IllegalStateException` and therefore `Exception`. A cancelled stage is recorded as having errored, and the loop continues to the next stage — where the un-guarded `getMemoryById` at line 57 throws the next `CancellationException` and escapes.

`ProcessingWorker.doWork` catches that with its own `catch (_: Exception)`, and on the final attempt calls `pipeline.markFailed(memoryId)` — a suspend call issued from an already-cancelled coroutine, which throws before writing anything. So the branch that exists to record failure cannot run at the moment it is needed. `withContext(NonCancellable)` inside `markFailed` fixes that half; rethrowing `CancellationException` ahead of both generic catches fixes the other.

### The relevance threshold does not bite

`ResultRanking.kt:68` sets `MINIMUM_RELEVANCE = 0.15`, described as the line that enforces *"weakly related Memories must not be shown merely to populate the screen."* Working the arithmetic back through `KeywordScoring`'s constants: a one-term query that matches at all scores 0.88 to 1.00, which after `KEYWORD_WEIGHT` contributes 0.352 to 0.400. A two-term query matching one term scores 0.425 to 0.500, contributing 0.170 to 0.200. Only at four terms with one match (0.085 to 0.100) does a keyword-only result fall below the threshold. For one- and two-word queries — the commonest kind, and the ones `QueryComplexity` routes straight through — every row FTS returns clears the threshold. Prefix matching widens this further: the query `ai` reaches full coverage against a document whose only relevant token is "airport", because `KeywordScoring` mirrors the `*` in the MATCH expression.

The secondary effect is on ordering. Keyword-only single-term results all land in the 0.352–0.400 band, so the `createdAt` tiebreaker at line 125 decides far more comparisons than "recency breaks ties" suggests.

### Lowercased offsets applied to the original string

`TemporalExpressionParser.kt:53` lowercases the query, then every rule slices the original with offsets from that copy — line 68, line 117, line 146:

```kotlin
val lower = query.lowercase()
...
original.substring(index, index + phrase.length)
```

`String.lowercase()` applies full Unicode case mapping, and `İ` (U+0130) maps to two code units (`i` + U+0307 combining dot). So `lower` can be longer than `query`, and every offset past the `İ` is shifted.

Concrete trigger: `İSTANBUL trip last week`. `query.length` is 23; `lower.length` is 24. `"last week"` sits at `lower` index 15, so `original.substring(15, 24)` is called on a 23-character string and throws `StringIndexOutOfBoundsException`. That escapes `parse` → `QueryUnderstanding.understand` → `SearchOrchestrator.search` → the unguarded `viewModelScope.launch` in `FeedViewModel`, which is a crash on a keystroke — the class of failure `FtsQuery`'s docstring is explicitly written to prevent. Short of a crash, the offsets silently return the wrong `matchedText`, so the wrong words are stripped from the semantic query.

Matching case-insensitively against `query` — `RegexOption.IGNORE_CASE` for the regex rules, `indexOf(phrase, ignoreCase = true)` for the named ones — removes the second coordinate system entirely.

`SnippetExtractor.findMatches` has the same shape (`lower` at line 82, ranges applied to `flat` at lines 61-73) but degrades rather than crashes. I traced the window invariant: the two adjustments at lines 155-156 guarantee `start ≤ match.first ≤ match.last ≤ end - 1` for any `targetLength`, and the highlight filter at line 73 keeps every emitted range inside the returned string. So the worst outcome is highlights landing on the wrong characters or being dropped. Cosmetic, but the same one-line fix.

### `FtsQuery`'s pure-OR contract is not what SQLite evaluates

`FtsQuery`'s docstring is emphatic that terms are ORed, *"because with AND, a single typo in a multi-word query returns nothing."* But `build` emits `github.com* OR ramen*`, and the FTS query parser tokenises `github.com*` with the table's own tokenizer — which splits on `.`. What actually reaches the matcher is `github` and `com*` as adjacent tokens (implicit AND) alongside an explicit `OR`, and the grouping then depends on FTS3 operator precedence rather than on the flat OR the code intends.

`FtsQueryTest` asserts `build("github.com") == "github.com*"`, which is about the builder's output string and says nothing about how SQLite reads it. No test in `androidTest` issues a `MATCH` built by `FtsQuery` against a real FTS4 table, so neither this nor the tokenisation mismatch above is covered. An instrumented test that indexes a document containing `node.js` and `github.com`, then asserts what `build("js")`, `build("com")`, and `build("github.com ramen")` return, would pin all three down.

Confidence: likely. The tokenizer split is certain; the resulting precedence is what needs verifying.

### Checked and correct

**No injection surface.** `SearchIndexDao.match` binds `:ftsExpression` as a parameter, and `FtsQuery`'s allow-list keeps only `\p{L}`, `\p{N}`, and `.`. Leading and trailing dots are trimmed, so every emitted term begins with a letter or digit and cannot be read as an operator or a prefix wildcard. Lowercasing neutralises `AND`/`OR`/`NOT`/`NEAR`, which FTS4 only recognises in uppercase. I could not construct an input that reaches `MATCH` as syntax — the semantic problems above are about what the expression *means*, not whether it parses.

**No catastrophic backtracking.** Every regex in both packages is a single-quantifier character class or a fixed alternation with no nested quantifiers: `[^\p{L}\p{N}.]+`, `\s+`, `URL_PATTERN`, `normalize`'s group chain, the temporal alternations, and `SummarizationStage.PREAMBLE` (all groups optional, none nested).

**No division by zero, no score above 1.** `KeywordScoring` guards both empty-list cases before dividing; its maximum is exactly `0.85 + 0.15 = 1.0`. `EmbeddingGenerator.similarity` returns 0 for a zero-norm vector. `rescaleSemantic` divides by a constant. `ResultRanking` coerces (see the boost issue above for the cost of that).

**Temporal ranges do not overlap or gap.** Every range is half-open and built through `atStartOfDay(zone)`, so consecutive days abut exactly at local midnight and DST transitions are handled by `ZonedDateTime`. The zone is a parameter defaulting to `ZoneId.systemDefault()` at call time, which matches the stated intent that "yesterday" means the user's yesterday.

**`NAMED` ordering holds.** I checked every pair: no earlier phrase is a substring of a later one, so nothing shadows. "the day before yesterday" correctly precedes "yesterday", and "last week" is not reachable from "last seven days". Out-of-range counts in the "N days ago" rules return null via `toIntOrNull` and fall through; in-range absurd counts (`2000000000 days ago`) produce a nonsensical but non-throwing range, since the result stays inside `LocalDate`'s year bounds.

**Stage ordering assumptions hold.** `StageId` declaration order is OCR → VISION → METADATA → EMBEDDING → CATEGORIZATION → SUMMARIZATION → INDEXING; `ProcessingStageRegistry` sorts by `ordinal`; and the pipeline re-reads the Memory before each stage, so every stage genuinely sees what its predecessors persisted. `EmbeddingStage` reads `allText()` — user text plus successful OCR plus successful vision — all written before it runs. `SearchIndexStage` is last and sees the summary. `MetadataExtractionStage` correctly notes that it runs before URLs are stored and bounds plain text instead of building a `BoundedAnalysisInput`.

**Category boost is reachable.** `MemoryRepositoryImpl.getMemoriesByIds` hydrates categories and summaries in batched lookups, so `ResultRanking`'s `memory.derived.categories` is populated at the point it is read. Worth stating because a `DerivedData.EMPTY` here would have made the boost dead code.

**`CategorizationStage.match` is latently brittle but not broken.** It splits the model's response on `,` and replaces `[ ] { } " '` with newlines before the closed-set lookup, so any vocabulary name containing those characters could never be matched. No name in the current `CategoryDictionary` does.

</details>

<details>
<summary>File map</summary>

Reviewed in full — no changes made.

`domain/search/`: `FtsQuery`, `KeywordMatch`, `KeywordScoring`, `KeywordSearcher`, `QueryComplexity`, `QueryDecompositionParser`, `QueryUnderstanding`, `ResultRanking`, `SearchDocument`, `SearchIntent`, `SearchOrchestrator`, `SearchResult`, `SnippetExtractor`, `TemporalExpression`, `TemporalExpressionParser`, `VectorMatch`, `VectorSearcher`

`domain/processing/`: `BoundedAnalysisInput`, `EmbeddingGenerator`, `ImageDescriber`, `MetadataResponseParser`, `PerImageAggregation`, `ProcessingPipeline`, `ProcessingStage`, `ProcessingStageRegistry`, `StageId`, `StageResult`, `StageStatus`, `TextGenerator`, `TextRecognizer`, `UrlExtractor`, and all seven `stages/`

Read for reachability: `ui/feed/FeedViewModel`, `ui/feed/SearchResultCard`, `data/local/dao/SearchIndexDao`, `data/local/entity/MemorySearchIndexEntity`, `data/repository/MemoryRepositoryImpl`, `data/repository/SearchIndexRepositoryImpl`, `data/processing/ProcessingWorker`, `data/processing/ProcessingScheduler`, `domain/model/Memory`, `domain/model/DerivedData`, `domain/categories/CategoryDictionary`

</details>
