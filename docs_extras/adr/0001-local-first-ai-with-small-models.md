# ADR-0001: Local-first AI with small general-purpose models

## Status

Accepted

## Context

oneMind needs generative AI capabilities (summarization, categorization, image understanding) to enrich Memories. The choice is between requiring cloud AI, bundling a large model, or offering small downloadable local models.

The target device has 6GB+ RAM and runs Android 11+. The core value proposition is data sovereignty — the user controls where their data goes.

## Decision

- The default AI experience is **fully local** using general-purpose models at or below 2B parameters.
- The app offers a curated list: 2 models each at 1B, 1.5B, and 2B parameter sizes (6 total options).
- Models are **downloaded after install** over WiFi or mobile data. No model is bundled in the APK.
- All offered models are **general-purpose** — not split by role (same model handles summarization, categorization, vision if capable).
- The **embedding model** is a separate, team-selected model optimized for efficiency. Not user-configurable.
- Cloud AI providers are **opt-in** — user can configure them if they prefer, but the app is fully functional without them.

## Consequences

### Positive
- App works without internet after model download
- No user data leaves device by default
- APK stays small (Play Store compliant)
- User has genuine choice and control

### Negative
- First-launch experience requires a model download (hundreds of MB, takes time)
- 2B models are significantly less capable than cloud models (GPT-4, Claude, Gemini)
- Summarization and categorization quality will be lower than cloud alternatives
- Vision capability may be limited or unavailable in smallest models
- Team must curate and test model compatibility across diverse Android devices

### Risks
- Model quality may frustrate users who expect cloud-level AI
- Download abandonment on first launch (user gives up waiting)
- Storage pressure on devices with limited space

## Alternatives considered

1. **Cloud-only**: rejected — contradicts data sovereignty value
2. **Bundled model in APK**: rejected — APK size would exceed Play Store limits and waste space for users who prefer cloud
3. **No AI until configured**: rejected — creates an empty first-run experience where core features don't work
