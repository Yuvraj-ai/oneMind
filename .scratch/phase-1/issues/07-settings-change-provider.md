# 07: Settings — change LLM provider

**GitHub Issue:** https://github.com/Yuvraj-ai/oneMind/issues/7

**What to build:** Settings screen to change AI provider post-setup: switch local models, configure cloud, see active model, manage cached model storage. User is never locked to first-launch choice.

**Blocked by:** #5 (LLM Provider interface), #6 (First-launch onboarding)

**Status:** ready-for-agent

- [ ] Settings accessible from main feed (gear icon or menu)
- [ ] Current active model displayed: name, type, parameter count / endpoint
- [ ] "Change local model" shows model list, selecting triggers download + swap
- [ ] "Configure cloud provider" option: API key + endpoint + test connection
- [ ] Switching local→cloud: unloads local model, activates CloudModelProvider
- [ ] Switching cloud→local: triggers download if not cached, loads on completion
- [ ] Previously downloaded models cached (no re-download)
- [ ] "Delete cached models" option with confirmation + size display
- [ ] Provider change takes effect immediately for future processing
- [ ] Settings persisted via DataStore
- [ ] UI shows storage used by cached models
