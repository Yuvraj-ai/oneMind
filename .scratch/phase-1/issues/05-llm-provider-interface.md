# 05: LLM Provider interface + model registry

**GitHub Issue:** https://github.com/Yuvraj-ai/oneMind/issues/5

**What to build:** The pluggable AI infrastructure: provider interface (text generation, capability reporting), LocalModelProvider (on-device inference), CloudModelProvider stub (OpenAI-compatible), and hardcoded registry of 6 approved local models + embedding model.

**Blocked by:** #1 (Project scaffold + Memory data model)

**Status:** ready-for-agent

- [ ] LlmProvider interface: generateText, reportCapabilities, isLoaded, load, unload
- [ ] Capability enum: TEXT_GENERATION, VISION, EMBEDDINGS
- [ ] LocalModelProvider using on-device runtime (ONNX/llama.cpp/MediaPipe)
- [ ] LocalModelProvider loads model from file path, reports capabilities from metadata
- [ ] CloudModelProvider: accepts baseUrl + apiKey, OpenAI-compatible chat completions
- [ ] ModelRegistry: 6 models (2x1B, 2x1.5B, 2x2B) with displayName, parameterCount, downloadSizeMb, downloadUrl, quantizationFormat, requiredRamMb
- [ ] EmbeddingModel entry: name, size, downloadUrl, outputDimensions
- [ ] ProviderManager singleton: holds active provider, allows switching
- [ ] Unit tests for provider interface contract
- [ ] Unit tests for model registry filtering by device RAM
- [ ] All DI wired through Hilt
