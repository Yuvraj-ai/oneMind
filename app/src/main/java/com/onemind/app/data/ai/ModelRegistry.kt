package com.onemind.app.data.ai

import com.onemind.app.domain.model.EmbeddingModelInfo
import com.onemind.app.domain.model.LlmCapability
import com.onemind.app.domain.model.ModelFormat
import com.onemind.app.domain.model.ModelInfo
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The models oneMind can download.
 *
 * Every entry here is verified: the URL resolves unauthenticated, and the size is
 * the real `content-length`. `ModelRegistryTest` asserts that, because the first
 * version of this file was populated from recall and every single URL was wrong,
 * while the tests passed by only ever checking that six entries existed.
 *
 * See `docs/research/2026-08-on-device-inference.md` for how each fact was
 * established, and ADR-0002 for why there are no local generative models here.
 */
@Singleton
class ModelRegistry @Inject constructor() {

    /**
     * Local generative models: none, deliberately.
     *
     * There is no stable on-device LLM inference runtime for Android. Google has
     * put the MediaPipe LLM Inference API into maintenance-only mode and points
     * at LiteRT-LM, which is `0.0.0-alpha05`. Offering a 1.5GB download that then
     * cannot run is worse than offering nothing, so generative enrichment comes
     * from a cloud provider the user opts into. See ADR-0002.
     *
     * [candidateLocalModels] records what to reach for when this is revisited.
     */
    val generativeModels: List<ModelInfo> = emptyList()

    /**
     * The embedding model. Small, ungated, and runs on MediaPipe's stable
     * `tasks-text` 1.0.0 Text Embedder.
     *
     * Universal Sentence Encoder, chosen after measurement. The obvious first
     * pick, MediaPipe's `bert_embedder`, is a generic BERT feature extractor, and
     * on-device testing showed it unusable for retrieval: unrelated text
     * ("a recipe for lemon drizzle cake") scored 0.97 against "running AI models
     * locally on a phone", while genuinely related text scored 0.86. Every pair
     * scored near 1.0, which is the signature of vectors clustered in a narrow
     * cone. Raw BERT embeddings are known to be poor for sentence similarity;
     * Universal Sentence Encoder is trained for exactly that.
     *
     * Not Gecko: it ships a separate `sentencepiece.model`, so it would mean
     * implementing SentencePiece in Kotlin. Not EmbeddingGemma: every Gemma
     * repository is gated, and oneMind promises no accounts.
     *
     * [outputDimensions] comes from a real on-device run
     * (`EmbeddingGeneratorTest`), not from documentation. The generator reads
     * dimensionality from the loaded model and does not trust this number.
     */
    val embeddingModel: EmbeddingModelInfo = EmbeddingModelInfo(
        id = "mediapipe-universal-sentence-encoder",
        displayName = "Universal Sentence Encoder",
        downloadSizeMb = 6,
        downloadUrl = "https://storage.googleapis.com/mediapipe-models/text_embedder/universal_sentence_encoder/float32/latest/universal_sentence_encoder.tflite",
        outputDimensions = 100,
        format = ModelFormat.LITERT
    )

    /**
     * Whether any local generative model can be offered.
     *
     * The onboarding flow reads this instead of assuming the list is non-empty,
     * so restoring local models later is a registry change rather than a UI
     * change.
     */
    val hasLocalGenerativeModels: Boolean
        get() = generativeModels.isNotEmpty()

    fun getCompatibleModels(availableRamMb: Int): List<ModelInfo> =
        generativeModels.filter { it.requiredRamMb <= availableRamMb }

    /**
     * Largest model that fits with headroom for the OS and the app itself.
     * Returns null while [generativeModels] is empty.
     */
    fun getRecommendedModel(availableRamMb: Int): ModelInfo? {
        val headroom = OS_HEADROOM_MB
        return generativeModels
            .filter { it.requiredRamMb <= (availableRamMb - headroom) }
            .maxByOrNull { it.parameterCountB }
    }

    fun getModelById(id: String): ModelInfo? = generativeModels.find { it.id == id }

    companion object {
        /** Reserved for the OS and oneMind itself when sizing a model. */
        const val OS_HEADROOM_MB = 1024

        /**
         * Verified candidates for when local generative inference is revisited.
         *
         * Not offered to users: no runtime can execute them yet. Kept here so the
         * research does not have to be repeated, and so the URL test keeps
         * checking they still resolve. Every one is ungated and returned HTTP 200
         * on 2026-08-22, with sizes from the real `content-length`.
         *
         * Note the shape of the tradeoff, which is the deferred design question
         * in ADR-0002: the vision-capable entries are far smaller in parameter
         * count, so choosing vision currently means choosing worse text.
         */
        val candidateLocalModels: List<ModelInfo> = listOf(
            ModelInfo(
                id = "qwen2.5-0.5b-instruct-q8",
                displayName = "Qwen2.5 0.5B Instruct",
                parameterCountB = 0.5f,
                downloadSizeMb = 521,
                downloadUrl = "https://huggingface.co/litert-community/Qwen2.5-0.5B-Instruct/resolve/main/Qwen2.5-0.5B-Instruct_multi-prefill-seq_q8_ekv1280.task",
                quantizationFormat = "q8",
                requiredRamMb = 1536,
                capabilities = setOf(LlmCapability.TEXT_GENERATION),
                format = ModelFormat.MEDIAPIPE_TASK
            ),
            ModelInfo(
                id = "qwen2.5-1.5b-instruct-q8",
                displayName = "Qwen2.5 1.5B Instruct",
                parameterCountB = 1.5f,
                downloadSizeMb = 1524,
                downloadUrl = "https://huggingface.co/litert-community/Qwen2.5-1.5B-Instruct/resolve/main/Qwen2.5-1.5B-Instruct_multi-prefill-seq_q8_ekv1280.task",
                quantizationFormat = "q8",
                requiredRamMb = 3072,
                capabilities = setOf(LlmCapability.TEXT_GENERATION),
                format = ModelFormat.MEDIAPIPE_TASK
            ),
            ModelInfo(
                id = "lfm2.5-vl-450m-int8",
                displayName = "LFM2.5 VL 450M",
                parameterCountB = 0.45f,
                downloadSizeMb = 537,
                downloadUrl = "https://huggingface.co/litert-community/LFM2.5-VL-450M/resolve/main/LFM2.5-VL-450M_int8.litertlm",
                quantizationFormat = "int8",
                requiredRamMb = 1536,
                capabilities = setOf(LlmCapability.TEXT_GENERATION, LlmCapability.VISION),
                format = ModelFormat.LITERT_LM
            ),
            ModelInfo(
                id = "qwen2-vl-2b",
                displayName = "Qwen2 VL 2B",
                parameterCountB = 2.0f,
                downloadSizeMb = 1701,
                downloadUrl = "https://huggingface.co/litert-community/Qwen2-VL-2B/resolve/main/Qwen2-VL-2B.litertlm",
                quantizationFormat = "mixed",
                requiredRamMb = 3584,
                capabilities = setOf(LlmCapability.TEXT_GENERATION, LlmCapability.VISION),
                format = ModelFormat.LITERT_LM
            )
        )
    }
}
