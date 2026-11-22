package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.backendDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "patchforge_control_prefs"
)

enum class ThirdPartyAiVendor(
    val id: String,
    val label: String,
    val defaultBaseUrl: String,
    val defaultModel: String
) {
    NATIVE_GEMINI(
        id = "NATIVE_GEMINI",
        label = "Firebase AI Logic (Gemini)",
        defaultBaseUrl = "firebase://ai-logic",
        defaultModel = "gemini-4-hydrogen"
    ),
    OPENAI(
        id = "OPENAI",
        label = "OpenAI Multimodal",
        defaultBaseUrl = "https://api.openai.com/v1",
        defaultModel = "gpt-4o"
    ),
    ANTHROPIC(
        id = "ANTHROPIC",
        label = "Anthropic Claude",
        defaultBaseUrl = "https://api.anthropic.com/v1",
        defaultModel = "claude-3-7-sonnet-latest"
    ),
    OPENROUTER(
        id = "OPENROUTER",
        label = "OpenRouter / Custom",
        defaultBaseUrl = "https://openrouter.ai/api/v1",
        defaultModel = "openai/gpt-4o-mini"
    )
}

data class BackendConfig(
    val baseUrl: String = "http://10.0.2.2:8000",
    val bearerToken: String = "",
    val defaultTargetBranch: String = "fix/patchforge-remediation",
    val orchestratorMode: String = "NATIVE_GEMINI", // NATIVE_GEMINI, FASTAPI_HYBRID
    val autoRetryOffline: Boolean = true
)

data class AiApiIntegrationConfig(
    val activeVendorId: String = ThirdPartyAiVendor.NATIVE_GEMINI.id,
    val openAiApiKey: String = "",
    val openAiModel: String = ThirdPartyAiVendor.OPENAI.defaultModel,
    val anthropicApiKey: String = "",
    val anthropicModel: String = ThirdPartyAiVendor.ANTHROPIC.defaultModel,
    val customBaseUrl: String = ThirdPartyAiVendor.OPENROUTER.defaultBaseUrl,
    val customApiKey: String = "",
    val customModel: String = ThirdPartyAiVendor.OPENROUTER.defaultModel
) {
    val activeVendor: ThirdPartyAiVendor
        get() = ThirdPartyAiVendor.entries.firstOrNull { it.id == activeVendorId }
            ?: ThirdPartyAiVendor.NATIVE_GEMINI
}

class BackendPreferences(private val context: Context) {
    private object Keys {
        val BASE_URL = stringPreferencesKey("fastapi_base_url")
        val BEARER_TOKEN = stringPreferencesKey("fastapi_bearer_token")
        val DEFAULT_BRANCH = stringPreferencesKey("default_target_branch")
        val ORCHESTRATOR_MODE = stringPreferencesKey("orchestrator_mode")
        val AUTO_RETRY = booleanPreferencesKey("auto_retry_offline")

        // 3rd-party AI API Multimodal Integration keys (stored strictly on-device in DataStore)
        val ACTIVE_AI_VENDOR = stringPreferencesKey("active_ai_vendor")
        val OPENAI_API_KEY = stringPreferencesKey("openai_api_key")
        val OPENAI_MODEL = stringPreferencesKey("openai_model")
        val ANTHROPIC_API_KEY = stringPreferencesKey("anthropic_api_key")
        val ANTHROPIC_MODEL = stringPreferencesKey("anthropic_model")
        val CUSTOM_BASE_URL = stringPreferencesKey("custom_ai_base_url")
        val CUSTOM_API_KEY = stringPreferencesKey("custom_ai_api_key")
        val CUSTOM_MODEL = stringPreferencesKey("custom_ai_model")
    }

    val configFlow: Flow<BackendConfig> = context.backendDataStore.data.map { prefs ->
        BackendConfig(
            baseUrl = prefs[Keys.BASE_URL] ?: "http://10.0.2.2:8000",
            bearerToken = prefs[Keys.BEARER_TOKEN] ?: "",
            defaultTargetBranch = prefs[Keys.DEFAULT_BRANCH] ?: "fix/patchforge-remediation",
            orchestratorMode = prefs[Keys.ORCHESTRATOR_MODE] ?: "NATIVE_GEMINI",
            autoRetryOffline = prefs[Keys.AUTO_RETRY] ?: true
        )
    }

    val aiIntegrationFlow: Flow<AiApiIntegrationConfig> = context.backendDataStore.data.map { prefs ->
        AiApiIntegrationConfig(
            activeVendorId = prefs[Keys.ACTIVE_AI_VENDOR] ?: ThirdPartyAiVendor.NATIVE_GEMINI.id,
            openAiApiKey = prefs[Keys.OPENAI_API_KEY] ?: "",
            openAiModel = prefs[Keys.OPENAI_MODEL] ?: ThirdPartyAiVendor.OPENAI.defaultModel,
            anthropicApiKey = prefs[Keys.ANTHROPIC_API_KEY] ?: "",
            anthropicModel = prefs[Keys.ANTHROPIC_MODEL] ?: ThirdPartyAiVendor.ANTHROPIC.defaultModel,
            customBaseUrl = prefs[Keys.CUSTOM_BASE_URL] ?: ThirdPartyAiVendor.OPENROUTER.defaultBaseUrl,
            customApiKey = prefs[Keys.CUSTOM_API_KEY] ?: "",
            customModel = prefs[Keys.CUSTOM_MODEL] ?: ThirdPartyAiVendor.OPENROUTER.defaultModel
        )
    }

    suspend fun saveBackendConfig(
        baseUrl: String,
        bearerToken: String,
        defaultTargetBranch: String,
        orchestratorMode: String,
        autoRetryOffline: Boolean
    ) {
        context.backendDataStore.edit { prefs ->
            prefs[Keys.BASE_URL] = baseUrl.trim().trimEnd('/')
            prefs[Keys.BEARER_TOKEN] = bearerToken.trim()
            prefs[Keys.DEFAULT_BRANCH] = defaultTargetBranch.trim().ifBlank { "fix/patchforge-remediation" }
            prefs[Keys.ORCHESTRATOR_MODE] = orchestratorMode
            prefs[Keys.AUTO_RETRY] = autoRetryOffline
        }
    }

    suspend fun saveAiIntegrationConfig(config: AiApiIntegrationConfig) {
        context.backendDataStore.edit { prefs ->
            prefs[Keys.ACTIVE_AI_VENDOR] = config.activeVendorId
            prefs[Keys.OPENAI_API_KEY] = config.openAiApiKey.trim()
            prefs[Keys.OPENAI_MODEL] = config.openAiModel.trim().ifBlank { ThirdPartyAiVendor.OPENAI.defaultModel }
            prefs[Keys.ANTHROPIC_API_KEY] = config.anthropicApiKey.trim()
            prefs[Keys.ANTHROPIC_MODEL] = config.anthropicModel.trim().ifBlank { ThirdPartyAiVendor.ANTHROPIC.defaultModel }
            prefs[Keys.CUSTOM_BASE_URL] = config.customBaseUrl.trim().trimEnd('/')
            prefs[Keys.CUSTOM_API_KEY] = config.customApiKey.trim()
            prefs[Keys.CUSTOM_MODEL] = config.customModel.trim().ifBlank { ThirdPartyAiVendor.OPENROUTER.defaultModel }
        }
    }

    suspend fun clearAllThirdPartyKeys() {
        context.backendDataStore.edit { prefs ->
            prefs[Keys.OPENAI_API_KEY] = ""
            prefs[Keys.ANTHROPIC_API_KEY] = ""
            prefs[Keys.CUSTOM_API_KEY] = ""
            prefs[Keys.ACTIVE_AI_VENDOR] = ThirdPartyAiVendor.NATIVE_GEMINI.id
        }
    }
}
