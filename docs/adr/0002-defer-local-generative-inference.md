# ADR-0002: Defer local generative inference; ship embeddings and cloud only

## Status

Accepted

## Context

ADR-0001 committed to a fully-local default experience using generative models
of 2B parameters or less. Research (`docs/research/2026-08-on-device-inference.md`)
established that the premise does not hold today:

- **There is no stable on-device LLM inference API for Android.** Google's own
  documentation places the MediaPipe LLM Inference API in maintenance-only mode
  and recommends LiteRT-LM, which ships as `0.0.0-alpha05`. The recommended path
  is alpha; the working path is deprecated.
- Google states the API "does not reliably support device emulators", so local
  generative inference cannot be verified in our test environment at all.
- `LocalModelProvider` has always been a stub. `generateText` and
  `describeImage` both return `NotImplementedError`.
- Every model URL shipped in ticket #5 was fabricated and fails. A user cannot
  complete first launch.

Meanwhile two things *are* solid:

- `com.google.ai.edge.litert:litert` is at a stable `2.2.0`, updated 2026-08-13,
  and runs the `.tflite` embedding models. Embeddings are buildable on firm
  ground.
- `CloudModelProvider.generateText` already works against any OpenAI-compatible
  endpoint.

The user's standing directive is to use only dependency versions known to give
stable performance. No local generative runtime satisfies that.

## Decision

**Local generative inference is deferred.** Specifically:

1. The model registry offers **no local generative models** for now. Offering a
   1.5GB download that then cannot run is worse than offering nothing.
2. The registry does offer the **embedding model**, which is real, verified,
   ungated, and runs on stable LiteRT 2.2.0.
3. **Cloud** is the path for generative enrichment (summaries, categories,
   entity and date extraction, image description). Opt-in, as before.
4. When a Memory cannot be enriched because no generative provider is
   configured, stages record `NOT_SUPPORTED`. That machinery already exists and
   is already tested.
5. Local generative inference returns as its own effort, informed by fresh
   research, once either LiteRT-LM reaches a stable release or the project
   consciously accepts a pre-release runtime.

## Consequences

### Positive

- Every shipped dependency is a stable release.
- First launch works. Today it cannot.
- The download is ~115MB rather than ~1.5GB.
- Search (Phase 4) is unaffected: it needs embeddings, which are stable.
- OCR is unaffected: ML Kit is stable and entirely local, so screenshots remain
  readable with no provider at all.

### Negative

- **The local-first promise is partially unmet in v1.** A user who trusts nobody
  gets capture, OCR, and semantic search, but no summaries, categories, or image
  descriptions. That is a real reduction against ADR-0001.
- Users wanting full enrichment must configure a cloud provider, which is the
  thing the product's identity is a reaction against.
- Prompts for #12, #14 and #15 will be tuned against cloud models, and may need
  retuning when a small local model is introduced.

### Mitigation

The reduction is smaller than it looks. The two capabilities that make a
screenshot findable — OCR and vector search — are both fully local and fully
stable. What is deferred is the interpretive layer, not retrieval.

## Deferred design question: how a user chooses models

Not decided here, deliberately, because it should be decided with a working
runtime in hand rather than in the abstract.

The current onboarding presents one flat list where a model's vision support is
a green subtitle. That conflates two decisions and misrepresents a tradeoff as a
bonus:

- Text-capable and vision-capable models are different models.
  `Qwen2.5-1.5B-Instruct` (1524MB) is text-only. `LFM2.5-VL-450M` (537MB) does
  vision but is a third the parameters, so its text output will be materially
  worse.
- `ProviderManager` holds exactly one provider and unloads the previous on
  switch, so "a text model *and* a vision model" is currently impossible.
- On a 6GB floor, two resident generative models plus an embedding model is
  roughly 2.2GB, which is likely untenable without load/unload between pipeline
  stages.

When local generative inference is revisited, the options to weigh are:

1. **Two explicit choices** — a text model and an optional vision model. Honest,
   but doubles download and memory, and needs sequential load/unload.
2. **One choice, framed as a tradeoff** — group the list by what it gives up
   rather than badging vision as a bonus. Cheapest, but no user can have both.
3. **Text first, vision as a later opt-in** — onboarding picks text only;
   Settings offers image understanding as a separate download. Keeps first
   launch lean and makes the tradeoff explicit at the point of choosing.

Option 3 looks strongest on current information, but it should not be settled
until the runtime question is.

## Alternatives considered

- **Adopt `tasks-genai` 0.10.35 (deprecated).** More mature than the alpha and
  more recently updated (April 2026 vs October 2025). Rejected for now: building
  on a path Google has explicitly told developers to migrate off invites a
  rewrite, and it cannot be verified on an emulator.
- **Adopt `litertlm` 0.0.0-alpha05.** Google's stated direction. Rejected for
  now: an alpha with no update since October 2025 fails the stability bar.
- **Third-party llama.cpp JNI wrappers.** No first-party Android artifact
  exists. Rejected: a core capability should not rest on someone else's
  unofficial native build.
- **Ship the broken registry and fix it later.** Rejected: first launch fails
  outright.
