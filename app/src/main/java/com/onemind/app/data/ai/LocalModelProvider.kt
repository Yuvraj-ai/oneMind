package com.onemind.app.data.ai

import android.content.Context
import com.onemind.app.domain.model.LlmCapability
import com.onemind.app.domain.model.LlmProvider
import com.onemind.app.domain.model.ModelInfo
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Local on-device LLM provider using MediaPipe LLM Inference API.
 *
 * This implementation wraps the MediaPipe inference runtime.
 * Model files are stored in app-internal storage after download.
 *
 * Note: The actual MediaPipe LlmInference calls are stubbed here
 * since the MediaPipe dependency needs the actual .aar from Google.
 * The interface and lifecycle are correct — swap in real calls when
 * integrating the MediaPipe dependency.
 */
class LocalModelProvider @Inject constructor(
    @ApplicationContext private val context: Context
) : LlmProvider {

    private var modelInfo: ModelInfo? = null
    private var _isLoaded: Boolean = false

    // In real implementation: private var llmInference: LlmInference? = null

    override val name: String
        get() = modelInfo?.displayName ?: "Local Model (not loaded)"

    override val isLoaded: Boolean
        get() = _isLoaded

    override fun capabilities(): Set<LlmCapability> {
        return modelInfo?.capabilities ?: emptySet()
    }

    override suspend fun generateText(prompt: String, maxTokens: Int): Result<String> {
        if (!_isLoaded) return Result.failure(IllegalStateException("Model not loaded"))

        return withContext(Dispatchers.Default) {
            try {
                // TODO: Replace with actual MediaPipe LlmInference.generateResponse(prompt)
                // val response = llmInference?.generateResponse(prompt)
                //     ?: return@withContext Result.failure(Exception("Inference returned null"))
                // Result.success(response)

                Result.failure(NotImplementedError("MediaPipe inference not yet integrated. Requires com.google.mediapipe:tasks-genai dependency."))
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    override suspend fun generateEmbedding(text: String): Result<FloatArray> {
        // Embeddings use a separate model — see EmbeddingProvider
        return Result.failure(UnsupportedOperationException("Use EmbeddingProvider for embeddings"))
    }

    override suspend fun describeImage(imagePath: String, prompt: String?): Result<String> {
        if (!_isLoaded) return Result.failure(IllegalStateException("Model not loaded"))
        if (LlmCapability.VISION !in capabilities()) {
            return Result.failure(UnsupportedOperationException("Model does not support vision"))
        }

        return withContext(Dispatchers.Default) {
            try {
                // TODO: Replace with actual MediaPipe vision inference
                Result.failure(NotImplementedError("Vision inference not yet integrated"))
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    override suspend fun load() {
        withContext(Dispatchers.IO) {
            val info = modelInfo ?: throw IllegalStateException("No model configured. Call configure() first.")
            val modelPath = getModelFilePath(info.id)

            // TODO: Initialize MediaPipe LlmInference with model path
            // val options = LlmInference.LlmInferenceOptions.builder()
            //     .setModelPath(modelPath)
            //     .setMaxTokens(1024)
            //     .build()
            // llmInference = LlmInference.createFromOptions(context, options)

            _isLoaded = true
        }
    }

    override suspend fun unload() {
        withContext(Dispatchers.IO) {
            // TODO: llmInference?.close()
            // llmInference = null
            _isLoaded = false
        }
    }

    /**
     * Configure which model this provider will load.
     * Must be called before load().
     */
    fun configure(model: ModelInfo) {
        this.modelInfo = model
    }

    /**
     * Get the file path where a downloaded model is stored.
     */
    fun getModelFilePath(modelId: String): String {
        return "${context.filesDir}/models/$modelId"
    }

    /**
     * Check if a model file has been downloaded.
     */
    fun isModelDownloaded(modelId: String): Boolean {
        val file = java.io.File(getModelFilePath(modelId))
        return file.exists() && file.length() > 0
    }
}
