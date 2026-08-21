package com.onemind.app.data.ai

import com.onemind.app.domain.model.EmbeddingModelInfo
import com.onemind.app.domain.model.LlmCapability
import com.onemind.app.domain.model.ModelFormat
import com.onemind.app.domain.model.ModelInfo
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Hardcoded registry of approved local models.
 * 6 generative models (2x 1B, 2x 1.5B, 2x 2B) + 1 embedding model.
 *
 * In future versions, this can be updated via a remote config mechanism.
 */
@Singleton
class ModelRegistry @Inject constructor() {

    /**
     * All available generative models, sorted by parameter count.
     */
    val generativeModels: List<ModelInfo> = listOf(
        // 1B models
        ModelInfo(
            id = "gemma3-1b-int4",
            displayName = "Gemma 3 1B",
            parameterCountB = 1.0f,
            downloadSizeMb = 540,
            downloadUrl = "https://huggingface.co/google/gemma-3-1b-it-int4/resolve/main/gemma-3-1b-it-int4.task",
            quantizationFormat = "int4",
            requiredRamMb = 2048,
            capabilities = setOf(LlmCapability.TEXT_GENERATION),
            format = ModelFormat.MEDIAPIPE
        ),
        ModelInfo(
            id = "qwen3-0.6b-int4",
            displayName = "Qwen 3 0.6B",
            parameterCountB = 0.6f,
            downloadSizeMb = 400,
            downloadUrl = "https://huggingface.co/Qwen/Qwen3-0.6B-GGUF/resolve/main/qwen3-0.6b-q4_k_m.gguf",
            quantizationFormat = "int4",
            requiredRamMb = 1536,
            capabilities = setOf(LlmCapability.TEXT_GENERATION),
            format = ModelFormat.GGUF
        ),
        // 1.5B models
        ModelInfo(
            id = "qwen3-1.7b-int4",
            displayName = "Qwen 3 1.7B",
            parameterCountB = 1.7f,
            downloadSizeMb = 900,
            downloadUrl = "https://huggingface.co/Qwen/Qwen3-1.7B-GGUF/resolve/main/qwen3-1.7b-q4_k_m.gguf",
            quantizationFormat = "int4",
            requiredRamMb = 2560,
            capabilities = setOf(LlmCapability.TEXT_GENERATION),
            format = ModelFormat.GGUF
        ),
        ModelInfo(
            id = "gemma3-1b-int8",
            displayName = "Gemma 3 1B (int8)",
            parameterCountB = 1.0f,
            downloadSizeMb = 1080,
            downloadUrl = "https://huggingface.co/google/gemma-3-1b-it-int8/resolve/main/gemma-3-1b-it-int8.task",
            quantizationFormat = "int8",
            requiredRamMb = 3072,
            capabilities = setOf(LlmCapability.TEXT_GENERATION),
            format = ModelFormat.MEDIAPIPE
        ),
        // 2B models
        ModelInfo(
            id = "gemma3-4b-int4",
            displayName = "Gemma 3 4B (int4)",
            parameterCountB = 4.0f,
            downloadSizeMb = 2100,
            downloadUrl = "https://huggingface.co/google/gemma-3-4b-it-int4/resolve/main/gemma-3-4b-it-int4.task",
            quantizationFormat = "int4",
            requiredRamMb = 4096,
            capabilities = setOf(LlmCapability.TEXT_GENERATION, LlmCapability.VISION),
            format = ModelFormat.MEDIAPIPE
        ),
        ModelInfo(
            id = "qwen3-4b-int4",
            displayName = "Qwen 3 4B",
            parameterCountB = 4.0f,
            downloadSizeMb = 2200,
            downloadUrl = "https://huggingface.co/Qwen/Qwen3-4B-GGUF/resolve/main/qwen3-4b-q4_k_m.gguf",
            quantizationFormat = "int4",
            requiredRamMb = 4096,
            capabilities = setOf(LlmCapability.TEXT_GENERATION),
            format = ModelFormat.GGUF
        )
    )

    /**
     * The team-selected embedding model.
     * Small, efficient, runs on all target devices (6GB+ RAM).
     */
    val embeddingModel: EmbeddingModelInfo = EmbeddingModelInfo(
        id = "all-minilm-l6-v2",
        displayName = "MiniLM-L6-v2 (Embeddings)",
        downloadSizeMb = 46,
        downloadUrl = "https://huggingface.co/sentence-transformers/all-MiniLM-L6-v2/resolve/main/onnx/model_quantized.onnx",
        outputDimensions = 384,
        format = ModelFormat.MEDIAPIPE
    )

    /**
     * Filter models that can run on the device based on available RAM.
     */
    fun getCompatibleModels(availableRamMb: Int): List<ModelInfo> {
        return generativeModels.filter { it.requiredRamMb <= availableRamMb }
    }

    /**
     * Get the recommended model for the given RAM.
     * Picks the largest model that fits comfortably (with 1GB headroom).
     */
    fun getRecommendedModel(availableRamMb: Int): ModelInfo? {
        val headroom = 1024 // 1GB buffer for OS + app
        return generativeModels
            .filter { it.requiredRamMb <= (availableRamMb - headroom) }
            .maxByOrNull { it.parameterCountB }
    }

    fun getModelById(id: String): ModelInfo? {
        return generativeModels.find { it.id == id }
    }
}
