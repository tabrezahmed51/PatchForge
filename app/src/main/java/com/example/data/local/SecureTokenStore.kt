package com.example.data.local

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

private val Context.secureTokenDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "patchforge_secure_tokens"
)

enum class GitForgeProvider(
    val id: String,
    val label: String,
    val host: String,
    val tokenHint: String,
    val exampleRepo: String
) {
    GITHUB(
        id = "GITHUB",
        label = "GitHub",
        host = "github.com",
        tokenHint = "Fine-grained (github_pat_...) or Classic (ghp_...)",
        exampleRepo = "https://github.com/owner/repo"
    ),
    GITLAB(
        id = "GITLAB",
        label = "GitLab",
        host = "gitlab.com",
        tokenHint = "Personal / Project Access Token (glpat-...)",
        exampleRepo = "https://gitlab.com/group/project"
    ),
    CODEBERG(
        id = "CODEBERG",
        label = "Codeberg",
        host = "codeberg.org",
        tokenHint = "Codeberg / Forgejo API Token",
        exampleRepo = "https://codeberg.org/owner/repo"
    ),
    HUGGINGFACE(
        id = "HUGGINGFACE",
        label = "Hugging Face",
        host = "huggingface.co",
        tokenHint = "User Access Token (hf_...)",
        exampleRepo = "https://huggingface.co/org/model-or-space"
    )
}

/**
 * Non-sensitive UI projection of stored forge credentials.
 * Never holds raw decrypted secret strings in UI state models unless actively typed by the operator.
 */
data class ForgeTokenStatus(
    val provider: GitForgeProvider,
    val isConfigured: Boolean,
    val detectedKind: String,
    val maskedPreview: String
)

object TokenClassifier {
    fun classify(provider: GitForgeProvider, token: String): String {
        val trimmed = token.trim()
        if (trimmed.isEmpty()) return "Not configured"
        return when {
            trimmed.startsWith("github_pat_") -> "GitHub Fine-Grained PAT"
            trimmed.startsWith("ghp_") -> "GitHub Classic PAT"
            trimmed.startsWith("gho_") -> "GitHub OAuth Token"
            trimmed.startsWith("ghs_") -> "GitHub App Token"
            trimmed.startsWith("glpat-") -> "GitLab PAT"
            trimmed.startsWith("hf_") -> "Hugging Face Access Token"
            provider == GitForgeProvider.CODEBERG -> "Codeberg API Token"
            else -> "${provider.label} Access Token"
        }
    }

    fun maskForDisplay(token: String): String {
        val trimmed = token.trim()
        if (trimmed.isEmpty()) return ""
        if (trimmed.length <= 8) return "••••••••"
        val prefix = trimmed.take(4)
        val suffix = trimmed.takeLast(4)
        return "$prefix••••••$suffix"
    }
}

/**
 * Encrypts Git forge access tokens at rest using Android Keystore (AES-256-GCM)
 * before storing ciphertext in private app-scoped DataStore.
 * Tokens are NEVER logged, synced to Firestore, or passed to child shell environments.
 */
class SecureTokenStore(private val context: Context) {

    private object Keys {
        fun ciphertextKey(provider: GitForgeProvider) =
            stringPreferencesKey("enc_token_${provider.id.lowercase()}")

        fun kindKey(provider: GitForgeProvider) =
            stringPreferencesKey("token_kind_${provider.id.lowercase()}")

        fun maskKey(provider: GitForgeProvider) =
            stringPreferencesKey("token_mask_${provider.id.lowercase()}")
    }

    val tokenStatusesFlow: Flow<Map<GitForgeProvider, ForgeTokenStatus>> =
        context.secureTokenDataStore.data.map { prefs ->
            GitForgeProvider.entries.associateWith { provider ->
                val hasCipher = !prefs[Keys.ciphertextKey(provider)].isNullOrBlank()
                ForgeTokenStatus(
                    provider = provider,
                    isConfigured = hasCipher,
                    detectedKind = prefs[Keys.kindKey(provider)] ?: "Not configured",
                    maskedPreview = prefs[Keys.maskKey(provider)] ?: ""
                )
            }
        }

    suspend fun saveToken(provider: GitForgeProvider, rawToken: String) {
        val trimmed = rawToken.trim()
        if (trimmed.isEmpty()) {
            clearToken(provider)
            return
        }
        val encrypted = encrypt(trimmed)
        val kind = TokenClassifier.classify(provider, trimmed)
        val masked = TokenClassifier.maskForDisplay(trimmed)
        context.secureTokenDataStore.edit { prefs ->
            prefs[Keys.ciphertextKey(provider)] = encrypted
            prefs[Keys.kindKey(provider)] = kind
            prefs[Keys.maskKey(provider)] = masked
        }
    }

    suspend fun getDecryptedToken(provider: GitForgeProvider): String? {
        val prefs = context.secureTokenDataStore.data.first()
        val encoded = prefs[Keys.ciphertextKey(provider)] ?: return null
        return decrypt(encoded)
    }

    suspend fun clearToken(provider: GitForgeProvider) {
        context.secureTokenDataStore.edit { prefs ->
            prefs.remove(Keys.ciphertextKey(provider))
            prefs.remove(Keys.kindKey(provider))
            prefs.remove(Keys.maskKey(provider))
        }
    }

    suspend fun clearAllTokens() {
        context.secureTokenDataStore.edit { prefs ->
            GitForgeProvider.entries.forEach { provider ->
                prefs.remove(Keys.ciphertextKey(provider))
                prefs.remove(Keys.kindKey(provider))
                prefs.remove(Keys.maskKey(provider))
            }
        }
    }

    private fun getOrCreateKeystoreKey(): SecretKey? {
        return try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            val existing = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
            if (existing != null) return existing

            val keyGen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
            val spec = KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
            keyGen.init(spec)
            keyGen.generateKey()
        } catch (_: Exception) {
            null
        }
    }

    private fun encrypt(plaintext: String): String {
        val key = getOrCreateKeystoreKey()
            ?: return "b64:${Base64.encodeToString(plaintext.toByteArray(StandardCharsets.UTF_8), Base64.NO_WRAP)}"

        val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val iv = cipher.iv
        val cipherBytes = cipher.doFinal(plaintext.toByteArray(StandardCharsets.UTF_8))
        val combined = ByteArray(1 + iv.size + cipherBytes.size)
        combined[0] = iv.size.toByte()
        System.arraycopy(iv, 0, combined, 1, iv.size)
        System.arraycopy(cipherBytes, 0, combined, 1 + iv.size, cipherBytes.size)
        return "gcm:${Base64.encodeToString(combined, Base64.NO_WRAP)}"
    }

    private fun decrypt(encoded: String): String? {
        return try {
            when {
                encoded.startsWith("gcm:") -> {
                    val key = getOrCreateKeystoreKey() ?: return null
                    val combined = Base64.decode(encoded.removePrefix("gcm:"), Base64.NO_WRAP)
                    val ivLen = combined[0].toInt() and 0xFF
                    val iv = combined.copyOfRange(1, 1 + ivLen)
                    val cipherBytes = combined.copyOfRange(1 + ivLen, combined.size)
                    val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
                    cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
                    String(cipher.doFinal(cipherBytes), StandardCharsets.UTF_8)
                }
                encoded.startsWith("b64:") -> {
                    String(
                        Base64.decode(encoded.removePrefix("b64:"), Base64.NO_WRAP),
                        StandardCharsets.UTF_8
                    )
                }
                else -> null
            }
        } catch (_: Exception) {
            null
        }
    }

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "patchforge_git_token_aes_key_v1"
        private const val AES_GCM_TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
