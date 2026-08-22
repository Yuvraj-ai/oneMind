# oneMind

**A local-first memory assistant for Android.** Save the things you want to remember, and find them later by describing them the way you actually remember them.

[![License: AGPL v3](https://img.shields.io/badge/License-AGPL_v3-blue.svg)](LICENSE)
[![Android](https://img.shields.io/badge/Android-11%2B-3DDC84.svg)](#requirements)

---

## The problem

You screenshot something useful. You share a link to yourself. You copy a passage from an article. Weeks later you know you saved it — you just have no idea *where*, and no memory of the exact words.

oneMind is the thing that remembers for you. It reads what you saved, works out what it was about, and lets you find it by describing it loosely: *"that laptop I saw with the good GPU"*, *"AI stuff I saved from Chrome last week"*.

## What it does

**Capture, from anywhere**
- Share to oneMind from any app — links, text, one image or many
- Screenshot straight into it from a Quick Settings tile
- Save whatever you last copied from a second Quick Settings tile
- Or just type

**Understand, on its own**

Every saved memory goes through a pipeline that reads text out of your screenshots (on-device OCR), describes your photos, pulls out links, dates and named things, files it under broad categories, writes a one-line summary of what it is about, and builds a vector so it can be found by meaning.

**Find it again, by describing it**

One search box. No filter menus. It handles exact terms, meaning, vague references, and time — together:

| You type | It works out |
|---|---|
| `Qwen3` | exact term, instantly |
| `things about running AI on phones` | meaning — finds *"Deploying quantized LLMs with ONNX Runtime Mobile"* |
| `AI stuff I saved from Chrome last week` | topic **+** source **+** date range |
| `what did I save yesterday` | just the date range |

**Browse it**
- A reverse-chronological feed
- A timeline grouped by Today / Yesterday / This Week / This Month / Older
- Filter by where a memory came from

## Privacy

This is the part worth reading carefully, because it is the reason the app is built the way it is.

**Your memories never leave your device unless you ask them to.** Everything is stored in a local SQLite database and local files. There is no oneMind server, no account, and no telemetry.

**AI enrichment is the one exception, and it is yours to control.** Summaries, categories, entity extraction and image descriptions need a language model. On first launch you choose:

- **No provider.** Capture, OCR, browsing and semantic search all work. The interpretive features are simply absent, and the app says so instead of pretending.
- **Your own cloud provider.** Any OpenAI-compatible endpoint — OpenAI, Groq, Together, or your own Ollama on your own machine. You supply the URL, key and model name. oneMind talks to that endpoint and nowhere else.

**On-device generative inference is deliberately not offered yet.** No stable Android runtime for it exists — see [ADR-0002](docs/adr/0002-defer-local-generative-inference.md) for the research behind that decision. Rather than ship something that half-works, the app is honest that the choice is currently between a cloud provider you pick, or no AI enrichment at all.

**Semantic search is always local.** It uses a ~6MB sentence-embedding model downloaded once on first launch and run entirely on-device, so meaning-based search works with no provider and no network.

**What the two Quick Settings tiles do and do not do:**
- Screen capture asks Android for permission *every single time*, captures one frame, and releases the permission before the memory is even written. There is no continuous screen monitoring.
- The clipboard tile reads your clipboard only when you tap it. It never registers a clipboard listener.

**Known limitation:** your cloud provider's API key is currently stored unencrypted in app-private storage. It is unreadable by other apps on a non-rooted device, but it is not encrypted at rest. See [Known issues](#known-issues).

## Install

### From a release

1. Grab the latest `.apk` from [Releases](https://github.com/Yuvraj-ai/oneMind/releases)
2. Android will ask you to allow installing from an unknown source — this is expected for any app not distributed through Google Play
3. Install and open

Releases are signed with a consistent key, so updates install cleanly over an existing copy. Verify the SHA-256 checksum published alongside each release if you want to be sure the file is intact.

### Requirements

| | |
|---|---|
| **Android** | 11 or newer (API 30+) |
| **RAM** | 6GB recommended |
| **Storage** | ~50MB for the app, plus ~6MB for the search model, plus whatever your memories occupy |
| **Architecture** | arm64-v8a or armeabi-v7a — essentially every phone. x86 is not shipped, so the release APK will not install on an emulator; [build from source](BUILDING.md) for that. |
| **Network** | Once, to fetch the search model. After that, only if you configure a cloud provider. |

## Build from source

See [BUILDING.md](BUILDING.md) for toolchain setup, then:

```bash
./gradlew assembleDebug          # debug APK
./gradlew testDebugUnitTest      # 603 unit tests
./gradlew connectedDebugAndroidTest   # 56 instrumented tests, needs a device
./gradlew lintDebug              # static analysis
```

`assembleDebug` matters more than it looks: it is what validates the Hilt dependency graph. `compileDebugKotlin` does not.

## How it is built

**Kotlin, Jetpack Compose, Hilt, Room, WorkManager.** Single-module, layered as domain → data → ui, with the domain layer holding no Android dependencies so it is testable on the JVM.

**Enrichment is a pipeline of ordered stages.** Each reads what earlier ones wrote:

```
OCR → Vision → Metadata → Embedding → Categorization → Summarization → Indexing
```

Stage order is the declaration order of an enum, so it cannot be got wrong by a stringly-typed lookup. Every stage records its own outcome, which is how the UI can distinguish *"no provider configured"* from *"ran and found nothing"* from *"not processed yet"* — three states that look identical if you only store the result.

**Retrieval is one system, not three.** A query is decomposed into intent, then keyword search (SQLite FTS4) and vector search (cosine over local embeddings) run concurrently, their scores are normalised and blended, hard filters for time and source are applied, and what survives a relevance threshold is ranked. Below that threshold the answer is *"No memories found"* — never a weak match padded in to fill the screen.

**Original content is never replaced by what a machine inferred about it.** Summaries, categories and descriptions are derived data, stored separately, and can be thrown away and rebuilt at any time. Your memory is authoritative.

**Migrations are written by hand and tested.** Four schema versions so far, each migration tested for preservation of existing memories, including the full v1→v4 chain for someone upgrading from the first release. `fallbackToDestructiveMigration` is never used: a memory is something you asked the app to remember, so dropping the database on a schema change is not an acceptable failure mode.

### Documentation

| | |
|---|---|
| [BUILDING.md](BUILDING.md) | Toolchain setup and verification commands |
| [RELEASING.md](RELEASING.md) | How to cut a release |
| [CONTEXT.md](../CONTEXT.md) | Project glossary — the vocabulary the code uses |
| [docs/adr/](docs/adr/) | Architecture decision records |

## Testing

659 tests: **603 unit** (JVM, including Robolectric) and **56 instrumented** (real device, covering Room migrations, DAO behaviour, ML Kit OCR and the embedding model).

Notable coverage, because these are the parts where being wrong is quiet rather than loud:
- Every migration, individually and chained, asserting existing memories survive
- A parity test proving the migration's backfill SQL and the Kotlin indexer agree on what text is searchable — two implementations of one rule that would otherwise drift silently
- A generative test over 400 randomised model replies asserting assigned categories are always a subset of the shipped vocabulary
- Temporal parsing across year boundaries, leap years, midnight, and timezones, with a fixed clock so results do not depend on when CI runs

**Not covered:** the MediaProjection screen-capture flow end to end. An emulator cannot grant capture consent non-interactively. The frame arithmetic behind it is thoroughly tested; the capture session itself needs a manual run on a device.

## Known issues

- **API keys are stored unencrypted** in app-private storage. Should be `EncryptedSharedPreferences` or the Keystore.
- **Screen capture is unverified by automated test.** See above.
- **No UI tests.** Compose behaviour is verified by inspection, not by instrumentation.
- **Search relevance thresholds are set from limited measurement.** They will need tuning against real collections; the constants are named and documented for exactly that reason.
- `Category.parentId` exists in the schema but is unpopulated and read by nothing — reserved so that adding hierarchy later needs no migration.

## Contributing

Issues and pull requests are welcome.

If you are changing anything in `data/local/`, read [RELEASING.md](RELEASING.md) on schema versioning first — a migration mistake is the one class of bug that destroys user data rather than merely annoying them.

Before opening a PR, please make sure `testDebugUnitTest`, `assembleDebug` and `lintDebug` all pass.

## License

[GNU Affero General Public License v3.0](LICENSE).

AGPL rather than a permissive license on purpose: oneMind's premise is that you can verify what happens to your own memories. Anyone who runs a modified version as a service has to publish their changes, so that premise cannot be quietly removed downstream.
