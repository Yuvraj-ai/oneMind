package com.onemind.app.data.ai

import android.content.Context
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.text.textembedder.TextEmbedder
import com.onemind.app.domain.processing.EmbeddingGenerator
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * On-device embeddings via MediaPipe's Text Embedder.
 *
 * Chosen over driving LiteRT directly because MediaPipe handles tokenization and
 * tensor preprocessing inside the task. Gecko, the alternative, ships a separate
 * `sentencepiece.model` and would have meant implementing SentencePiece in
 * Kotlin for no gain.
 *
 * `com.google.mediapipe:tasks-text` is at a stable `1.0.0`. Note it is a
 * different artifact from `tasks-genai`: the LLM Inference maintenance-only
 * notice does not apply here.
 *
 * Fully local. Embeddings never require a configured provider and nothing leaves
 * the device, which is what keeps semantic search working for a user who trusts
 * nobody.
 */
@Singleton
class MediaPipeEmbeddingGenerator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val modelDownloadManager: ModelDownloadManager,
    private val modelRegistry: ModelRegistry
) : EmbeddingGenerator {

    private var embedder: TextEmbedder? = null
    private var detectedDimensions: Int? = null

    /** Serialises load and inference; the native embedder is not thread-safe. */
    private val lock = Mutex()

    override val isReady: Boolean
        get() = embedder != null

    override val dimensions: Int?
        get() = detectedDimensions

    override val modelId: String
        get() = modelRegistry.embeddingModel.id

    override suspend fun load(): Result<Unit> = withContext(Dispatchers.IO) {
        lock.withLock {
            if (embedder != null) return@withLock Result.success(Unit)

            val path = modelDownloadManager.getModelPath(modelId)
                ?: return@withLock Result.failure(
                    IllegalStateException("Embedding model has not been downloaded")
                )

            if (!File(path).exists()) {
                return@withLock Result.failure(
                    IllegalStateException("Embedding model file is missing: $path")
                )
            }

            try {
                val options = TextEmbedder.TextEmbedderOptions.builder()
                    .setBaseOptions(BaseOptions.builder().setModelAssetPath(path).build())
                    // L2 normalisation makes cosine similarity a dot product and
                    // puts every vector on the same scale, so scores stay
                    // comparable across memories.
                    .setL2Normalize(true)
                    .setQuantize(false)
                    .build()

                embedder = TextEmbedder.createFromOptions(context, options)
                Result.success(Unit)
            } catch (e: Exception) {
                embedder = null
                Result.failure(e)
            }
        }
    }

    override suspend fun unload() {
        withContext(Dispatchers.IO) {
            lock.withLock {
                embedder?.close()
                embedder = null
                detectedDimensions = null
            }
        }
    }

    override suspend fun embed(text: String): Result<FloatArray> = withContext(Dispatchers.Default) {
        if (text.isBlank()) {
            return@withContext Result.failure(
                IllegalArgumentException("Cannot embed blank text")
            )
        }

        // Load on first use rather than requiring callers to sequence it.
        if (embedder == null) {
            load().onFailure { return@withContext Result.failure(it) }
        }

        lock.withLock {
            val active = embedder
                ?: return@withLock Result.failure(IllegalStateException("Embedder not loaded"))

            try {
                val result = active.embed(text)
                val embedding = result.embeddingResult().embeddings().firstOrNull()
                    ?: return@withLock Result.failure(
                        IllegalStateException("Embedder returned no embedding")
                    )

                val vector = embedding.floatEmbedding()
                if (vector.isEmpty()) {
                    return@withLock Result.failure(
                        IllegalStateException("Embedder returned an empty vector")
                    )
                }

                // Learned from the model on first success, never assumed.
                detectedDimensions = vector.size

                Result.success(vector)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
}
