# Domain Docs

## Layout: single-context

This repo uses a single domain context:

```
/
+-- CONTEXT.md          <- glossary (created lazily when first term is resolved)
+-- docs/
|   +-- adr/            <- architecture decision records
|   +-- agents/         <- this directory (agent config)
+-- src/
```

## Consumer rules

- **CONTEXT.md** is a glossary and nothing else. No implementation details, no specs, no scratch notes.
- **ADRs** live in `docs/adr/` and follow the format: `NNNN-short-title.md`.
- Both are created **lazily**: only when there's something to write.
- All skills should read `CONTEXT.md` before writing code, to use the correct domain vocabulary.
- All skills should check `docs/adr/` before making architectural decisions, to avoid contradicting existing decisions.

## Future

When this repo grows to warrant multiple bounded contexts, upgrade to multi-context:
1. Create a `CONTEXT-MAP.md` at the root pointing to per-context `CONTEXT.md` files.
2. Move existing `CONTEXT.md` content into the appropriate context.
3. Update this file to reflect the new layout.
