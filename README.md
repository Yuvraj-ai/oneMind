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
- Screen capture takes one screenshot when you tap the tile, and nothing at any other time. It works through an Accessibility Service, which Android gates behind a switch you turn on yourself in Settings and can turn off whenever you like. The service sets `canRetrieveWindowContent="false"`, so the system never hands it the text or structure of your screen — the only thing it observes besides your tap is which app is in the foreground, so the memory can record where the screenshot came from. No continuous monitoring, no recording.
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
./gradlew testDebugUnitTest      # 598 unit tests
./gradlew connectedDebugAndroidTest   # 62 instrumented tests, needs a device
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

**Migrations are written by hand and tested.** Five schema versions so far, each migration tested for preservation of existing memories, including the full v1→v5 chain for someone upgrading from the first release. `fallbackToDestructiveMigration` is never used: a memory is something you asked the app to remember, so dropping the database on a schema change is not an acceptable failure mode.

### Documentation

| | |
|---|---|
| [BUILDING.md](BUILDING.md) | Toolchain setup and verification commands |
| [RELEASING.md](RELEASING.md) | How to cut a release |
| [CONTEXT.md](../CONTEXT.md) | Project glossary — the vocabulary the code uses |
| [docs/adr/](docs/adr/) | Architecture decision records |

## Setup

### Prerequisites

| Tool | Version | Why |
|------|---------|-----|
| JDK | 17 (Temurin recommended) | Gradle and the Kotlin compiler |
| Android SDK | Platform 35 + 36, Build Tools 36.0.0 | compileSdk 36, minSdk 30 |
| Gradle | 8.13 (wrapper included) | Just use `./gradlew` — no global install needed |

If you're starting fresh on a machine with nothing installed, see [BUILDING.md](BUILDING.md) for the full walkthrough including how to set `JAVA_HOME` and `ANDROID_HOME`.

### Quick start

```bash
git clone https://github.com/Yuvraj-ai/oneMind.git
cd oneMind

# Verify everything builds and the Hilt graph is valid
./gradlew assembleDebug

# Run the unit tests
./gradlew testDebugUnitTest

# Static analysis
./gradlew lintDebug
```

`assembleDebug` is not optional — it is what validates the Hilt dependency injection graph. `compileDebugKotlin` compiles fine even when the DI is broken, and the app crashes at launch.

### Cloud provider setup (optional)

If you want summaries, categories, image descriptions and event detection to work:

1. Get an API key from any OpenAI-compatible provider (OpenAI, Groq, Together, local Ollama)
2. Launch the app → Onboarding → "Configure a cloud provider"
3. Enter your base URL, API key, and model name

Without this, capture, OCR, search and browsing all work — only the interpretive features are absent.

### Emulator for instrumented tests

```bash
# Install an emulator image (one-time)
$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager "system-images;android-36;google_apis;x86_64"

# Create the AVD
$ANDROID_HOME/cmdline-tools/latest/bin/avdmanager create avd \
  -n onemind_test -k "system-images;android-36;google_apis;x86_64" --device "pixel_6"

# Or use the included script:
./scripts/emulator.fish start
./gradlew connectedDebugAndroidTest
./scripts/emulator.fish stop
```

The release APK is ARM-only (to keep the size at 42MB), so it won't install on an x86 emulator. Debug builds include all ABIs.

---

## Testing

598 unit tests (JVM, including Robolectric) + 62 instrumented (real device).

### Running tests

```bash
# Unit tests (fast, no device needed)
./gradlew testDebugUnitTest

# Instrumented tests (needs a running emulator or device)
./scripts/emulator.fish start
./gradlew connectedDebugAndroidTest
./scripts/emulator.fish stop

# Both + lint + build in one shot
./gradlew testDebugUnitTest assembleDebug lintDebug
```

### What the tests cover

| Area | What's tested | Why it matters |
|------|---------------|----------------|
| Room migrations | Every hop individually + full v1→v5 chain | A migration bug destroys user data |
| Backfill parity | SQL backfill vs Kotlin indexer produce the same terms | Two implementations of one rule that would otherwise drift silently |
| Category vocabulary | 400 randomised model replies → assigned set always subset of dictionary | The controlled-vocabulary invariant is the whole point of categorization |
| Temporal parsing | Year boundaries, leap years, midnight, timezone divergence | Each has a wrong answer that still returns *something* |
| FTS query safety | Every FTS4 syntax character that could crash on a keystroke | `don't`, `C++`, `AI OR ML` are normal queries that are syntax errors raw |
| Keyword scoring | Prefix matching mirrors FTS, coverage beats repetition | A row found by the index but scored 0 by our code is the worst bug |
| Processing state machine | Stuck PROCESSING is recoverable, cancellation doesn't swallow | The pre-fix bug was unrecoverable without clearing app data |
| Embedding model | Downloads, loads, produces vectors, survives unload/reload | Instrumented — the native model must actually work |

### What's NOT tested

- **Screen capture end-to-end** — an emulator cannot grant accessibility consent non-interactively
- **UI behaviour** — no Compose test rules; verified by inspection only
- **Cloud provider integration** — all tests mock `TextGenerator`; no test hits a real API
- **Real FTS4 queries from `FtsQuery.build`** — tokenisation mismatch between the code and SQLite's `simple` tokenizer was caught by review, not by a test (fix shipped, but no instrumented test pins it)

### Adding tests

Unit tests go in `app/src/test/java/com/onemind/app/`. Instrumented tests go in `app/src/androidTest/java/com/onemind/app/`.

If you're testing something that touches real Android framework (notifications, clipboard, PackageManager), use Robolectric (`@RunWith(RobolectricTestRunner::class)`) — it's already a dependency.

If you're testing Room migrations or anything that needs a real SQLite, use the instrumented suite with `MigrationTestHelper`.

## Known issues

- **API keys are stored unencrypted** in app-private storage. Should be `EncryptedSharedPreferences` or the Keystore.
- **Screen capture is unverified by automated test.** The Accessibility Service's `takeScreenshot()` path needs the service actually enabled by a user, which an emulator cannot grant non-interactively. It needs a manual run on a device.
- **No UI tests.** Compose behaviour is verified by inspection, not by instrumentation.
- **Search relevance thresholds are set from limited measurement.** They will need tuning against real collections; the constants are named and documented for exactly that reason.
- `Category.parentId` exists in the schema but is unpopulated and read by nothing — reserved so that adding hierarchy later needs no migration.

## Built with Kiro

This project was built entirely using [Kiro](https://kiro.dev), AWS's agentic IDE. It's a meaningful demonstration of what Kiro can do end-to-end on a real product, not a toy example:

**What Kiro handled across 40 commits:**
- Ideation through grilling sessions (`#grill-with-docs`) that sharpened the domain model and produced the glossary
- Specs written via `#to-spec`, then broken into implementation tickets with blocking edges via `#to-tickets`
- All 29 tickets implemented sequentially with TDD discipline — verification between each
- A 3-reviewer parallel code review sweep that found 31 real bugs, 8 of which were confirmed by failing tests written before the fix
- Architecture decisions recorded as ADRs when the reasoning was worth preserving
- Room migrations written by hand and tested, including a parity test between SQL and Kotlin implementations that Kiro's own review identified as needed

**Kiro-specific patterns that worked well:**
- `.kiro/steering/` files (Matt Pocock's skills adapted as steering) gave the agent consistent engineering discipline across sessions
- Basic Memory MCP kept project context persistent across context compactions and session boundaries
- Spec → tickets → implement → review as a structured flow rather than ad-hoc prompting
- Sub-agents dispatched in parallel for the review sweep (3 reviewers + 1 synthesiser), each scoped to a layer
- The `#diagnosing-bugs` skill's discipline (reproduce → hypothesise → instrument → fix) was used implicitly throughout: every review finding was reproduced as a failing test before being fixed

**What required human judgement:**
- Product decisions (what an "event" is, whether to use Accessibility Service vs MediaProjection, which LLM models to trust)
- The signing key (generated on the user's machine, never seen by the agent)
- Manual upgrade testing on a real device
- Choosing which review findings to fix now vs defer

## Contributing

Issues and pull requests are welcome.

If you are changing anything in `data/local/`, read [RELEASING.md](RELEASING.md) on schema versioning first — a migration mistake is the one class of bug that destroys user data rather than merely annoying them.

Before opening a PR, please make sure `testDebugUnitTest`, `assembleDebug` and `lintDebug` all pass.

## License

[GNU Affero General Public License v3.0](LICENSE).

AGPL rather than a permissive license on purpose: oneMind's premise is that you can verify what happens to your own memories. Anyone who runs a modified version as a service has to publish their changes, so that premise cannot be quietly removed downstream.
