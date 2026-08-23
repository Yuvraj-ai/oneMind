# 04: Image handling (optimize + thumbnail + storage)

**GitHub Issue:** https://github.com/Yuvraj-ai/oneMind/issues/4

**What to build:** Image optimization pipeline: compress to WebP, cap resolution, generate thumbnail, store in internal storage, clean up temp files. Makes image memories storage-efficient and fast to render.

**Blocked by:** #1 (Project scaffold), #3 (Manual Composer)

**Status:** ready-for-agent

- [ ] Images compressed to WebP at configurable resolution cap (e.g. 1920px longest edge)
- [ ] Thumbnail generated at smaller size (e.g. 256px) for feed cards
- [ ] Both canonical image and thumbnail stored in app-internal storage
- [ ] Database stores file paths, not raw bytes
- [ ] Original temp file deleted after processing
- [ ] Compression quality is a named constant (tunable)
- [ ] Resolution cap is a named constant (tunable)
- [ ] Thumbnail dimensions are a named constant (tunable)
- [ ] Feed cards show thumbnail; full view shows canonical image
- [ ] Deleting a Memory deletes associated image files
- [ ] Handles edge cases: very large images, very small images, corrupt images
- [ ] Processing runs off main thread
- [ ] Multiple images each get their own canonical + thumbnail pair
