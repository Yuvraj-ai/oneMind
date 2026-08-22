# On-device inference for Android: what is actually available

**Date:** 2026-08-22
**Why:** Ticket #5 shipped a `ModelRegistry` populated from recall rather than
verification. Every download URL in it was wrong. This establishes ground truth
before any of it is rewritten.

Facts below are marked **[verified]** when checked directly against the source
that owns them, and **[inference]** when reasoned rather than confirmed.

---

## 1. The registry we shipped is entirely broken

**[verified]** Every model URL in `ModelRegistry` fails:

| URL in registry | HTTP |
|---|---|
| `google/gemma-3-1b-it-int4/.../gemma-3-1b-it-int4.task` | `401` |
| `Qwen/Qwen3-0.6B-GGUF/.../qwen3-0.6b-q4_k_m.gguf` | `404` |
| `sentence-transformers/all-MiniLM-L6-v2/.../model_quantized.onnx` | `404` |

Checked with `curl -sL -o /dev/null -w "%{http_code}"` on 2026-08-22.

Consequence: a user cannot complete first launch. They pick a model, the
download fails. `ModelRegistryTest` passes throughout, because it asserts six
models exist and that RAM filtering works — never that a URL resolves.

---

## 2. There is no stable on-device LLM inference API for Android

This is the central finding and it changes the plan.

**[verified]** Google's own LLM Inference guide carries this notice:

> ⚠️ **Important: LLM Inference API Update.** The MediaPipe LLM Inference API
> (Android, iOS, and Web) is now in maintenance-only mode. New features and
> optimizations will be focused on LiteRT-LM. We recommend migrating your
> projects to LiteRT-LM to ensure continued support and performance.

Source: https://developers.google.com/edge/mediapipe/solutions/genai/llm_inference

**[verified]** Versions, from Google's Maven `maven-metadata.xml`:

| Artifact | Latest | `lastUpdated` | Maturity |
|---|---|---|---|
| `com.google.mediapipe:tasks-genai` | `0.10.35` | 2026-04-27 | pre-1.0, maintenance-only |
| `com.google.ai.edge.litertlm:litertlm` | `0.0.0-alpha05` | 2025-10-24 | **alpha** |
| `com.google.ai.edge.litert:litert` | `2.2.0` | 2026-08-13 | **stable 2.x** |
| `com.microsoft.onnxruntime:onnxruntime-android` | `1.22.0` | — | stable 1.x |

So the *recommended* path is alpha, and the *working* path is deprecated. Note
the ordering that matters: **`tasks-genai` was updated more recently
(April 2026) than its own alpha successor (October 2025).** Maintenance-only
still means actively patched, and it is the more current of the two.

**[verified]** An additional constraint from the Android guide:

> The LLM Inference API is optimized for high-end Android devices, such as
> Pixel 8 and Samsung S23 or later, and **does not reliably support device
> emulators**.

Source: https://ai.google.dev/edge/mediapipe/solutions/genai/llm_inference/android

This means local LLM inference **cannot be verified on our emulator**. Any
inference implementation needs a physical device to test.

**[verified]** No first-party llama.cpp Android artifact exists on Maven
Central. Only third-party wrappers (`com.johnsnowlabs.nlp:jsl-llamacpp-*`,
`us.ihmc:llamacpp-javacpp`), which would mean depending on someone else's JNI
build or maintaining our own via NDK.

---

## 3. Embeddings *do* have a stable path

**[verified]** `com.google.ai.edge.litert:litert` is at `2.2.0`, updated
2026-08-13 — a genuine stable major version, updated within the last fortnight.
The embedding models on LiteRT Community ship as `.tflite`, which this runtime
consumes.

This matters: embeddings are what semantic search (#13, Phase 4) needs, and they
can be built on stable ground even while text generation cannot.

---

## 4. Real models, verified

**[verified]** `litert-community` on HuggingFace is Google's official
distribution org. Google's docs confirm it:

> Models hosted on the LiteRT Community page are available in a
> MediaPipe-friendly format and don't require any additional conversion or
> compilation steps.

### Gating is the deciding constraint

**[verified]** via `https://huggingface.co/api/models/<repo>`:

| Repo | `gated` | Usable without an account? |
|---|---|---|
| `litert-community/Gemma3-1B-IT` | `auto` | **No** |
| `litert-community/Gemma2-2B-IT` | `auto` | **No** |
| `litert-community/embeddinggemma-300m` | `auto` | **No** |
| `litert-community/Qwen2.5-0.5B-Instruct` | `False` | Yes |
| `litert-community/Qwen2.5-1.5B-Instruct` | `False` | Yes |
| `litert-community/Qwen2-VL-2B` | `False` | Yes |
| `litert-community/LFM2.5-VL-450M` | `False` | Yes |
| `litert-community/Gecko-110m-en` | `False` | Yes |
| `litert-community/LFM2.5-Embedding-350M` | `False` | Yes |

**Every Gemma model is gated.** That is why the original Gemma URL returned
`401` rather than `404` — the path may be fine, but it needs an authenticated,
licence-accepting request. oneMind promises no accounts, so **Gemma is
unusable** unless we ask users for a HuggingFace token, which contradicts the
product.

This inverts ticket #5's registry, which was Gemma-first.

### Verified downloadable files and real sizes

**[verified]** `HTTP 200` unauthenticated, sizes from `content-length`:

| Model | File | Size | Purpose |
|---|---|---|---|
| Qwen2.5-0.5B-Instruct | `..._multi-prefill-seq_q8_ekv1280.task` | **521 MB** | text |
| Qwen2.5-1.5B-Instruct | `..._multi-prefill-seq_q8_ekv1280.task` | **1524 MB** | text |
| Qwen2-VL-2B | `Qwen2-VL-2B.litertlm` | **1701 MB** | **vision** |
| LFM2.5-VL-450M | `LFM2.5-VL-450M_int8.litertlm` | **537 MB** | **vision** |
| Gecko-110m-en | `Gecko_512_quant.tflite` | **115 MB** | embeddings |
| LFM2.5-Embedding-350M | `..._wi8fc.tflite` | **354 MB** | embeddings |

Compare to the sizes ticket #5 invented: it claimed 900 MB for a 1.7B model;
the real 1.5B is 1524 MB. The estimates were not close.

### On-device vision is real

**[verified]** `Qwen2-VL-2B` and `LFM2.5-VL-450M` are both
`pipeline_tag: image-text-to-text`, both ungated, both downloadable. Google's
docs also document multimodal prompting for the LLM Inference API.

This corrects an assumption I had been carrying: vision does **not** require a
cloud provider. `LFM2.5-VL-450M` at 537 MB is small enough for the target
device.

### Format note

**[verified]** Two formats coexist. `.task` is the MediaPipe Task Bundle;
`.litertlm` is the newer LiteRT-LM format. The vision models are `.litertlm`
only, and the text Qwen models offer `.task`.

**[inference]** Since `.litertlm` appears to be LiteRT-LM's native format and
the vision models ship only in it, using on-device vision may require the alpha
LiteRT-LM runtime rather than `tasks-genai`. **Not confirmed** — this needs a
device test, and it is the single most important open question.

---

## 5. What this means for oneMind

### Contradiction with a locked product decision

`CONTEXT.md` states models are max 2B parameters and that the default
experience is fully local. Both survive contact with these facts — Qwen2.5-1.5B
and the VL models fit. But "stable" does not: there is no stable runtime to
execute them.

### The registry must be rebuilt Qwen-first

Gating makes this forced, not preferred. A registry offering Gemma cannot work
without asking for a HuggingFace token.

### Sizes must be corrected

Real files are 1.5–3x larger than #5 claimed. This affects the download UX and
the "recommended model for your RAM" logic, which was tuned against fiction.

### Local inference cannot be verified on our emulator

Google states the API does not reliably support emulators. Any local inference
work is unverifiable here, which is a material change to how it should be
sequenced and reviewed.

---

## 6. Open questions needing a decision

1. **Which runtime?** `tasks-genai` 0.10.35 (deprecated, more recently updated,
   more mature) or `litertlm` 0.0.0-alpha05 (Google's recommendation, alpha,
   staler)? Neither is stable. This is a product risk decision, not a technical
   one.
2. **Does on-device vision need LiteRT-LM?** The `.litertlm`-only distribution
   of the VL models suggests yes. Needs a device test.
3. **Ship cloud-first?** Cloud text generation already works. Local could follow
   once a stable runtime exists.
4. **How is any of this verified** without a physical device?

---

## 7. Sources

- MediaPipe LLM Inference guide (maintenance-only notice):
  https://developers.google.com/edge/mediapipe/solutions/genai/llm_inference
- LLM Inference Android guide (emulator limitation, multimodal):
  https://ai.google.dev/edge/mediapipe/solutions/genai/llm_inference/android
- LiteRT Community model org: https://huggingface.co/litert-community
- HuggingFace model API (gating, file lists): `https://huggingface.co/api/models/<repo>`
- Google Maven metadata: `https://dl.google.com/dl/android/maven2/<path>/maven-metadata.xml`
- Maven Central search: `https://search.maven.org/solrsearch/select`

All HTTP checks performed 2026-08-22.
