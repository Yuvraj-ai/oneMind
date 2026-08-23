# 06: First-launch onboarding + model download flow

**GitHub Issue:** https://github.com/Yuvraj-ai/oneMind/issues/6

**What to build:** First-time user experience: onboarding screens, model selection with "recommended" badge, cloud alternative path, model download with progress/resume/cancel. After completion, user lands on the main memory feed.

**Blocked by:** #5 (LLM Provider interface + model registry)

**Status:** ready-for-agent

- [ ] First launch detected (DataStore flag)
- [ ] 1-2 onboarding screens explaining oneMind
- [ ] Model selection screen: 6 models with displayName, parameterCount, downloadSizeMb
- [ ] "Recommended" badge on best model for device RAM
- [ ] "Use a cloud provider instead" alternative path
- [ ] Download screen: progress bar, percentage, MB downloaded/total, cancel button
- [ ] Download supports resume on interruption
- [ ] Download verifies file integrity on completion (checksum)
- [ ] Embedding model downloaded alongside generative model
- [ ] On complete: model loaded, onboarding flag set, navigate to main feed
- [ ] On cloud config: CloudModelProvider configured, navigate to main feed
- [ ] Cancel returns to model selection cleanly
- [ ] Network-aware: warns on metered connection
- [ ] Handles download failure (retry button, error message)
