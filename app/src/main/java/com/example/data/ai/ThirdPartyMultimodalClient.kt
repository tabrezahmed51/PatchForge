package com.example.data.ai

import android.graphics.Bitmap
import android.util.Base64
import com.example.data.local.AiApiIntegrationConfig
import com.example.data.local.ThirdPartyAiVendor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

/**
 * Direct REST client for user-supplied 3rd-party multimodal AI API keys
 * (OpenAI, Anthropic Claude, and OpenRouter/Custom OpenAI-compatible endpoints).
 * API keys are supplied at runtime by the user in Settings and never hardcoded.
 */
class ThirdPartyMultimodalClient {
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    suspend fun runMultimodalInference(
        config: AiApiIntegrationConfig,
        prompt: String,
        image: Bitmap?
    ): String = withContext(Dispatchers.IO) {
        val base64Image = image?.let { encodeBitmapToJpegBase64(it) }
        when (config.activeVendor) {
            ThirdPartyAiVendor.OPENAI -> {
                require(config.openAiApiKey.isNotBlank()) {
                    "Enter an OpenAI API key in Settings > AI Multimodal API first."
                }
                callOpenAiCompatible(
                    baseUrl = ThirdPartyAiVendor.OPENAI.defaultBaseUrl,
                    apiKey = config.openAiApiKey,
                    model = config.openAiModel,
                    prompt = prompt,
                    base64Jpeg = base64Image
                )
            }
            ThirdPartyAiVendor.ANTHROPIC -> {
                require(config.anthropicApiKey.isNotBlank()) {
                    "Enter an Anthropic API key in Settings > AI Multimodal API first."
                }
                callAnthropicMessages(
                    apiKey = config.anthropicApiKey,
                    model = config.anthropicModel,
                    prompt = prompt,
                    base64Jpeg = base64Image
                )
            }
            ThirdPartyAiVendor.OPENROUTER -> {
                require(config.customApiKey.isNotBlank()) {
                    "Enter an OpenRouter / Custom API key in Settings > AI Multimodal API first."
                }
                val baseUrl = config.customBaseUrl.ifBlank { ThirdPartyAiVendor.OPENROUTER.defaultBaseUrl }
                callOpenAiCompatible(
                    baseUrl = baseUrl,
                    apiKey = config.customApiKey,
                    model = config.customModel,
                    prompt = prompt,
                    base64Jpeg = base64Image
                )
            }
            ThirdPartyAiVendor.NATIVE_GEMINI -> {
                throw IllegalStateException("Route NATIVE_GEMINI directly through Firebase AI Logic.")
            }
        }
    }

    private fun callOpenAiCompatible(
        baseUrl: String,
        apiKey: String,
        model: String,
        prompt: String,
        base64Jpeg: String?
    ): String {
        val endpoint = baseUrl.trimEnd('/') + "/chat/completions"
        val userContent = JSONArray().apply {
            put(JSONObject().put("type", "text").put("text", prompt))
            if (base64Jpeg != null) {
                put(
                    JSONObject()
                        .put("type", "image_url")
                        .put("image_url", JSONObject().put("url", "data:image/jpeg;base64,$base64Jpeg"))
                )
            }
        }

        val bodyJson = JSONObject()
            .put("model", model)
            .put(
                "messages",
                JSONArray()
                    .put(
                        JSONObject()
                            .put("role", "system")
                            .put("content", "You are PatchForge's Multimodal Code & Supply-Chain Security Inspector.")
                    )
                    .put(
                        JSONObject()
                            .put("role", "user")
                            .put("content", userContent)
                    )
            )

        val request = Request.Builder()
            .url(endpoint)
            .header("Authorization", "Bearer $apiKey")
            .header("Content-Type", "application/json")
            .post(bodyJson.toString().toRequestBody(jsonMediaType))
            .build()

        httpClient.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IllegalStateException("HTTP ${response.code}: ${extractApiError(raw)}")
            }
            val root = JSONObject(raw)
            val choices = root.optJSONArray("choices")
            val message = choices?.optJSONObject(0)?.optJSONObject("message")
            return message?.optString("content")?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?: throw IllegalStateException("3rd-party API returned an empty message.")
        }
    }

    private fun callAnthropicMessages(
        apiKey: String,
        model: String,
        prompt: String,
        base64Jpeg: String?
    ): String {
        val contentBlocks = JSONArray().apply {
            if (base64Jpeg != null) {
                put(
                    JSONObject()
                        .put("type", "image")
                        .put(
                            "source",
                            JSONObject()
                                .put("type", "base64")
                                .put("media_type", "image/jpeg")
                                .put("data", base64Jpeg)
                        )
                )
            }
            put(JSONObject().put("type", "text").put("text", prompt))
        }

        val bodyJson = JSONObject()
            .put("model", model)
            .put("max_tokens", 1024)
            .put("system", "You are PatchForge's Multimodal Code & Supply-Chain Security Inspector.")
            .put(
                "messages",
                JSONArray().put(
                    JSONObject()
                        .put("role", "user")
                        .put("content", contentBlocks)
                )
            )

        val request = Request.Builder()
            .url("https://api.anthropic.com/v1/messages")
            .header("x-api-key", apiKey)
            .header("anthropic-version", "2023-06-01")
            .header("Content-Type", "application/json")
            .post(bodyJson.toString().toRequestBody(jsonMediaType))
            .build()

        httpClient.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IllegalStateException("HTTP ${response.code}: ${extractApiError(raw)}")
            }
            val root = JSONObject(raw)
            val blocks = root.optJSONArray("content")
            val text = buildString {
                if (blocks != null) {
                    for (i in 0 until blocks.length()) {
                        val b = blocks.optJSONObject(i)
                        if (b?.optString("type") == "text") {
                            append(b.optString("text"))
                        }
                    }
                }
            }.trim()
            return text.ifEmpty { throw IllegalStateException("Anthropic returned an empty response.") }
        }
    }

    private fun encodeBitmapToJpegBase64(bitmap: Bitmap): String {
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 82, stream)
        return Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
    }

    private fun extractApiError(raw: String): String {
        return runCatching {
            val obj = JSONObject(raw)
            obj.optJSONObject("error")?.optString("message")
                ?.takeIf { it.isNotBlank() }
                ?: raw.take(200)
        }.getOrDefault(raw.take(200))
    }
}
