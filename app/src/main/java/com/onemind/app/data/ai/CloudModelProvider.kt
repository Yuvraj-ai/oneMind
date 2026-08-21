package com.onemind.app.data.ai

import com.onemind.app.domain.model.LlmCapability
import com.onemind.app.domain.model.LlmProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject

/**
 * Cloud LLM provider using OpenAI-compatible chat completions API.
 * Works with OpenAI, together.ai, Groq, local Ollama, or any
 * service exposing the /v1/chat/completions endpoint.
 */
class CloudModelProvider @Inject constructor() : LlmProvider {

    private var config: CloudConfig? = null
    private var _isLoaded: Boolean = false

    override val name: String
        get() = config?.modelName ?: "Cloud Provider (not configured)"

    override val isLoaded: Boolean
        get() = _isLoaded

    override fun capabilities(): Set<LlmCapability> {
        val caps = mutableSetOf(LlmCapability.TEXT_GENERATION)
        if (config?.supportsVision == true) caps.add(LlmCapability.VISION)
        return caps
    }

    override suspend fun generateText(prompt: String, maxTokens: Int): Result<String> {
        val cfg = config ?: return Result.failure(IllegalStateException("Not configured"))
        if (!_isLoaded) return Result.failure(IllegalStateException("Not loaded"))

        return withContext(Dispatchers.IO) {
            try {
                val response = callChatCompletions(cfg, prompt, maxTokens)
                Result.success(response)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    override suspend fun generateEmbedding(text: String): Result<FloatArray> {
        return Result.failure(UnsupportedOperationException("Use EmbeddingProvider for embeddings"))
    }

    override suspend fun describeImage(imagePath: String, prompt: String?): Result<String> {
        if (LlmCapability.VISION !in capabilities()) {
            return Result.failure(UnsupportedOperationException("Vision not supported by configured model"))
        }
        // Vision via cloud would require multipart/image upload — future implementation
        return Result.failure(NotImplementedError("Cloud vision not yet implemented"))
    }

    override suspend fun load() {
        if (config == null) throw IllegalStateException("Not configured. Call configure() first.")
        _isLoaded = true
    }

    override suspend fun unload() {
        _isLoaded = false
    }

    /**
     * Configure the cloud provider with API credentials and model info.
     */
    fun configure(cloudConfig: CloudConfig) {
        this.config = cloudConfig
    }

    /**
     * Make a chat completions API call.
     */
    private fun callChatCompletions(cfg: CloudConfig, prompt: String, maxTokens: Int): String {
        val url = URL("${cfg.baseUrl}/v1/chat/completions")
        val connection = url.openConnection() as HttpURLConnection

        connection.apply {
            requestMethod = "POST"
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Authorization", "Bearer ${cfg.apiKey}")
            doOutput = true
            connectTimeout = 30_000
            readTimeout = 60_000
        }

        val requestBody = JSONObject().apply {
            put("model", cfg.modelName)
            put("max_tokens", maxTokens)
            put("messages", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", prompt)
                })
            })
        }

        OutputStreamWriter(connection.outputStream).use { writer ->
            writer.write(requestBody.toString())
            writer.flush()
        }

        val responseCode = connection.responseCode
        if (responseCode != 200) {
            val error = connection.errorStream?.bufferedReader()?.readText() ?: "Unknown error"
            throw RuntimeException("API error ($responseCode): $error")
        }

        val responseBody = connection.inputStream.bufferedReader().readText()
        val json = JSONObject(responseBody)
        return json
            .getJSONArray("choices")
            .getJSONObject(0)
            .getJSONObject("message")
            .getString("content")
    }
}

/**
 * Configuration for a cloud LLM provider.
 */
data class CloudConfig(
    /** Base URL (e.g. "https://api.openai.com" or "http://localhost:11434") */
    val baseUrl: String,
    /** API key / bearer token */
    val apiKey: String,
    /** Model identifier (e.g. "gpt-4o-mini", "llama-3.1-8b") */
    val modelName: String,
    /** Whether this model supports vision/image inputs */
    val supportsVision: Boolean = false
)
