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

data class BackendConfig(
    val baseUrl: String = "http://10.0.2.2:8000",
    val bearerToken: String = "",
    val defaultTargetBranch: String = "fix/patchforge-remediation",
    val orchestratorMode: String = "NATIVE_GEMINI", // NATIVE_GEMINI, FASTAPI_HYBRID
    val autoRetryOffline: Boolean = true
)

class BackendPreferences(private val context: Context) {
    private object Keys {
        val BASE_URL = stringPreferencesKey("fastapi_base_url")
        val BEARER_TOKEN = stringPreferencesKey("fastapi_bearer_token")
        val DEFAULT_BRANCH = stringPreferencesKey("default_target_branch")
        val ORCHESTRATOR_MODE = stringPreferencesKey("orchestrator_mode")
        val AUTO_RETRY = booleanPreferencesKey("auto_retry_offline")
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
}
