package com.example.data.remote

import android.util.Base64
import com.example.data.local.GitForgeProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

data class ParsedForgeRepo(
    val provider: GitForgeProvider,
    val owner: String,
    val repo: String,
    val hfEntityType: String = "models", // models, spaces, datasets (Hugging Face only)
    val branch: String? = null
) {
    val fullPath: String get() = "$owner/$repo"
    val webUrl: String
        get() = when (provider) {
            GitForgeProvider.GITHUB -> "https://github.com/$owner/$repo"
            GitForgeProvider.GITLAB -> "https://gitlab.com/$owner/$repo"
            GitForgeProvider.CODEBERG -> "https://codeberg.org/$owner/$repo"
            GitForgeProvider.HUGGINGFACE -> {
                if (hfEntityType == "models") "https://huggingface.co/$owner/$repo"
                else "https://huggingface.co/$hfEntityType/$owner/$repo"
            }
        }
}

data class ForgeRepoFile(
    val path: String,
    val sizeBytes: Long = 0L,
    val sha: String? = null,
    val isPriorityManifest: Boolean = false
)

data class ForgeRepoSnapshot(
    val parsedRepo: ParsedForgeRepo,
    val displayName: String,
    val description: String,
    val defaultBranch: String,
    val visibility: String,
    val stars: Int,
    val openIssues: Int,
    val files: List<ForgeRepoFile>,
    val authenticatedRead: Boolean
)

data class ForgeCommitResult(
    val provider: GitForgeProvider,
    val filePath: String,
    val branch: String,
    val commitSha: String?,
    val message: String
)

/**
 * Strict allowlisted URL & slug parser preventing SSRF, path traversal, and token leakage to untrusted hosts.
 */
object GitForgeUrlParser {
    private val allowedSlugSegment = Regex("^[A-Za-z0-9._-]{1,120}$")

    fun parse(rawInput: String, fallbackProvider: GitForgeProvider, branchOverride: String? = null): Result<ParsedForgeRepo> {
        val cleaned = rawInput.trim().removeSuffix("/").removeSuffix(".git")
        if (cleaned.isEmpty()) {
            return Result.failure(IllegalArgumentException("Enter a repository URL or owner/repo path."))
        }
        if (cleaned.contains("..")) {
            return Result.failure(IllegalArgumentException("Invalid repository path: traversal segments are not allowed."))
        }

        val cleanBranch = branchOverride?.trim()?.takeIf { it.isNotEmpty() }

        return if (cleaned.startsWith("http://", ignoreCase = true) || cleaned.startsWith("https://", ignoreCase = true)) {
            val uri = try {
                URI(cleaned)
            } catch (_: Exception) {
                return Result.failure(IllegalArgumentException("Malformed repository URL."))
            }
            if (!uri.scheme.equals("https", ignoreCase = true)) {
                return Result.failure(IllegalArgumentException("Only HTTPS repository URLs are permitted."))
            }
            val host = (uri.host ?: "").lowercase()
            val provider = when (host) {
                "github.com", "www.github.com" -> GitForgeProvider.GITHUB
                "gitlab.com", "www.gitlab.com" -> GitForgeProvider.GITLAB
                "codeberg.org", "www.codeberg.org" -> GitForgeProvider.CODEBERG
                "huggingface.co", "www.huggingface.co" -> GitForgeProvider.HUGGINGFACE
                else -> return Result.failure(
                    IllegalArgumentException("Unsupported host '$host'. Supported: github.com, gitlab.com, codeberg.org, huggingface.co.")
                )
            }
            val segments = (uri.path ?: "").split("/").filter { it.isNotBlank() }
            parseSegments(provider, segments, cleanBranch)
        } else {
            val segments = cleaned.split("/").filter { it.isNotBlank() }
            parseSegments(fallbackProvider, segments, cleanBranch)
        }
    }

    private fun parseSegments(
        provider: GitForgeProvider,
        segments: List<String>,
        branch: String?
    ): Result<ParsedForgeRepo> {
        if (provider == GitForgeProvider.HUGGINGFACE && segments.size >= 3 &&
            (segments[0] == "spaces" || segments[0] == "datasets")
        ) {
            val entityType = segments[0]
            val owner = segments[1]
            val repo = segments[2]
            if (!isValidSegment(owner) || !isValidSegment(repo)) {
                return Result.failure(IllegalArgumentException("Invalid Hugging Face repository identifier."))
            }
            return Result.success(ParsedForgeRepo(provider, owner, repo, hfEntityType = entityType, branch = branch))
        }

        if (provider == GitForgeProvider.GITLAB && segments.size >= 2) {
            // GitLab supports nested groups (group/subgroup/repo)
            val allValid = segments.all { isValidSegment(it) }
            if (!allValid) return Result.failure(IllegalArgumentException("Invalid GitLab project path."))
            val repo = segments.last()
            val owner = segments.dropLast(1).joinToString("/")
            return Result.success(ParsedForgeRepo(provider, owner, repo, branch = branch))
        }

        if (segments.size < 2) {
            return Result.failure(IllegalArgumentException("Expected owner/repo format (for example, owner/project)."))
        }
        val owner = segments[0]
        val repo = segments[1]
        if (!isValidSegment(owner) || !isValidSegment(repo)) {
            return Result.failure(IllegalArgumentException("Repository owner and name contain unsupported characters."))
        }
        return Result.success(ParsedForgeRepo(provider, owner, repo, branch = branch))
    }

    private fun isValidSegment(segment: String): Boolean =
        allowedSlugSegment.matches(segment) && segment != "." && segment != ".."
}

/**
 * Live multi-forge REST client for GitHub (fine-grained + classic PAT), GitLab, Codeberg, and Hugging Face.
 * Never logs Authorization or PRIVATE-TOKEN headers.
 */
class GitForgeClient {

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .writeTimeout(25, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    private val priorityFileNames = setOf(
        "package.json",
        "package-lock.json",
        "requirements.txt",
        "pyproject.toml",
        "go.mod",
        "build.gradle.kts",
        "build.gradle",
        "pom.xml",
        "cargo.toml",
        "dockerfile",
        "docker-compose.yml",
        "readme.md"
    )

    suspend fun inspectRepository(
        parsedRepo: ParsedForgeRepo,
        accessToken: String?
    ): Result<ForgeRepoSnapshot> = withContext(Dispatchers.IO) {
        try {
            when (parsedRepo.provider) {
                GitForgeProvider.GITHUB -> inspectGitHub(parsedRepo, accessToken)
                GitForgeProvider.GITLAB -> inspectGitLab(parsedRepo, accessToken)
                GitForgeProvider.CODEBERG -> inspectCodeberg(parsedRepo, accessToken)
                GitForgeProvider.HUGGINGFACE -> inspectHuggingFace(parsedRepo, accessToken)
            }
        } catch (e: Exception) {
            Result.failure(IllegalStateException(sanitizeNetworkError(e.localizedMessage)))
        }
    }

    suspend fun fetchFileContent(
        parsedRepo: ParsedForgeRepo,
        branch: String,
        filePath: String,
        accessToken: String?
    ): Result<String> = withContext(Dispatchers.IO) {
        val safePath = filePath.trim().removePrefix("/")
        if (safePath.isEmpty() || safePath.contains("..")) {
            return@withContext Result.failure(IllegalArgumentException("Invalid file path."))
        }
        try {
            when (parsedRepo.provider) {
                GitForgeProvider.GITHUB -> fetchGitHubFile(parsedRepo, branch, safePath, accessToken)
                GitForgeProvider.GITLAB -> fetchGitLabFile(parsedRepo, branch, safePath, accessToken)
                GitForgeProvider.CODEBERG -> fetchCodebergFile(parsedRepo, branch, safePath, accessToken)
                GitForgeProvider.HUGGINGFACE -> fetchHuggingFaceFile(parsedRepo, branch, safePath, accessToken)
            }
        } catch (e: Exception) {
            Result.failure(IllegalStateException(sanitizeNetworkError(e.localizedMessage)))
        }
    }

    /**
     * Commits an operator-approved fix directly to a remote file on GitHub, GitLab, or Codeberg.
     * Callers MUST obtain explicit user approval before invoking this mutating API.
     */
    suspend fun commitFileFix(
        parsedRepo: ParsedForgeRepo,
        branch: String,
        filePath: String,
        updatedContent: String,
        commitMessage: String,
        accessToken: String?
    ): Result<ForgeCommitResult> = withContext(Dispatchers.IO) {
        val token = accessToken?.trim().orEmpty()
        if (token.isEmpty()) {
            return@withContext Result.failure(
                IllegalStateException("A write-scoped ${parsedRepo.provider.label} access token is required to commit fixes.")
            )
        }
        val safePath = filePath.trim().removePrefix("/")
        if (safePath.isEmpty() || safePath.contains("..")) {
            return@withContext Result.failure(IllegalArgumentException("Invalid target file path."))
        }

        try {
            when (parsedRepo.provider) {
                GitForgeProvider.GITHUB -> commitGitHubFile(parsedRepo, branch, safePath, updatedContent, commitMessage, token)
                GitForgeProvider.GITLAB -> commitGitLabFile(parsedRepo, branch, safePath, updatedContent, commitMessage, token)
                GitForgeProvider.CODEBERG -> commitCodebergFile(parsedRepo, branch, safePath, updatedContent, commitMessage, token)
                GitForgeProvider.HUGGINGFACE -> Result.failure(
                    UnsupportedOperationException(
                        "Direct single-file commit via Hugging Face REST API is not enabled on-device; copy the generated unified .patch or dispatch via your Ubuntu/FastAPI runner."
                    )
                )
            }
        } catch (e: Exception) {
            Result.failure(IllegalStateException(sanitizeNetworkError(e.localizedMessage)))
        }
    }

    // --- GitHub (supports Fine-Grained PAT github_pat_... & Classic PAT ghp_...) ---

    private fun inspectGitHub(repo: ParsedForgeRepo, token: String?): Result<ForgeRepoSnapshot> {
        val metaReq = buildGitHubRequest("https://api.github.com/repos/${repo.owner}/${repo.repo}", token)
        val metaJson = executeJson(metaReq).getOrElse { return Result.failure(it) }
        val defaultBranch = repo.branch ?: metaJson.optString("default_branch", "main").ifBlank { "main" }

        val treeUrl = "https://api.github.com/repos/${repo.owner}/${repo.repo}/git/trees/${urlEncode(defaultBranch)}?recursive=1"
        val files = executeJson(buildGitHubRequest(treeUrl, token)).fold(
            onSuccess = { treeJson ->
                val arr = treeJson.optJSONArray("tree") ?: JSONArray()
                buildList {
                    for (i in 0 until arr.length()) {
                        val item = arr.optJSONObject(i) ?: continue
                        if (item.optString("type") == "blob") {
                            val path = item.optString("path")
                            if (path.isNotBlank()) {
                                add(
                                    ForgeRepoFile(
                                        path = path,
                                        sizeBytes = item.optLong("size", 0L),
                                        sha = item.optString("sha").takeIf { it.isNotBlank() },
                                        isPriorityManifest = isPriorityFile(path)
                                    )
                                )
                            }
                        }
                    }
                }.sortedWith(compareByDescending<ForgeRepoFile> { it.isPriorityManifest }.thenBy { it.path }).take(60)
            },
            onFailure = { emptyList() }
        )

        return Result.success(
            ForgeRepoSnapshot(
                parsedRepo = repo.copy(branch = defaultBranch),
                displayName = metaJson.optString("full_name", repo.fullPath),
                description = metaJson.optString("description", "No repository description."),
                defaultBranch = defaultBranch,
                visibility = if (metaJson.optBoolean("private", false)) "PRIVATE" else "PUBLIC",
                stars = metaJson.optInt("stargazers_count", 0),
                openIssues = metaJson.optInt("open_issues_count", 0),
                files = files,
                authenticatedRead = !token.isNullOrBlank()
            )
        )
    }

    private fun fetchGitHubFile(repo: ParsedForgeRepo, branch: String, path: String, token: String?): Result<String> {
        val encodedPath = path.split("/").joinToString("/") { urlEncode(it) }
        val url = "https://api.github.com/repos/${repo.owner}/${repo.repo}/contents/$encodedPath?ref=${urlEncode(branch)}"
        val json = executeJson(buildGitHubRequest(url, token)).getOrElse { return Result.failure(it) }
        val contentBase64 = json.optString("content", "").replace("\n", "").replace("\r", "")
        if (contentBase64.isBlank()) {
            return Result.failure(IllegalStateException("File content was empty or binary."))
        }
        val decoded = String(Base64.decode(contentBase64, Base64.DEFAULT), StandardCharsets.UTF_8)
        return Result.success(decoded.take(MAX_FILE_CHARS))
    }

    private fun commitGitHubFile(
        repo: ParsedForgeRepo,
        branch: String,
        path: String,
        updatedContent: String,
        commitMessage: String,
        token: String
    ): Result<ForgeCommitResult> {
        val encodedPath = path.split("/").joinToString("/") { urlEncode(it) }
        val url = "https://api.github.com/repos/${repo.owner}/${repo.repo}/contents/$encodedPath"
        // Retrieve current blob SHA for optimistic concurrency update
        val existingSha = executeJson(buildGitHubRequest("$url?ref=${urlEncode(branch)}", token))
            .getOrNull()?.optString("sha")?.takeIf { it.isNotBlank() }

        val bodyJson = JSONObject().apply {
            put("message", commitMessage)
            put("content", Base64.encodeToString(updatedContent.toByteArray(StandardCharsets.UTF_8), Base64.NO_WRAP))
            put("branch", branch)
            if (existingSha != null) put("sha", existingSha)
        }
        val req = Request.Builder()
            .url(url)
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .header("Authorization", "Bearer $token")
            .header("User-Agent", "PatchForge-Android")
            .put(bodyJson.toString().toRequestBody(jsonMediaType))
            .build()

        val resp = executeJson(req).getOrElse { return Result.failure(it) }
        val sha = resp.optJSONObject("commit")?.optString("sha")?.take(10)
        return Result.success(
            ForgeCommitResult(
                provider = GitForgeProvider.GITHUB,
                filePath = path,
                branch = branch,
                commitSha = sha,
                message = "Committed fix to ${repo.fullPath}@$branch${sha?.let { " ($it)" } ?: ""}"
            )
        )
    }

    private fun buildGitHubRequest(url: String, token: String?): Request {
        val builder = Request.Builder()
            .url(url)
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .header("User-Agent", "PatchForge-Android")
        if (!token.isNullOrBlank()) {
            builder.header("Authorization", "Bearer ${token.trim()}")
        }
        return builder.get().build()
    }

    // --- GitLab ---

    private fun inspectGitLab(repo: ParsedForgeRepo, token: String?): Result<ForgeRepoSnapshot> {
        val projectId = urlEncode(repo.fullPath)
        val metaJson = executeJson(buildGitLabRequest("https://gitlab.com/api/v4/projects/$projectId", token))
            .getOrElse { return Result.failure(it) }
        val defaultBranch = repo.branch ?: metaJson.optString("default_branch", "main").ifBlank { "main" }

        val treeUrl = "https://gitlab.com/api/v4/projects/$projectId/repository/tree?ref=${urlEncode(defaultBranch)}&recursive=true&per_page=60"
        val files = executeJsonArray(buildGitLabRequest(treeUrl, token)).fold(
            onSuccess = { arr ->
                buildList {
                    for (i in 0 until arr.length()) {
                        val item = arr.optJSONObject(i) ?: continue
                        if (item.optString("type") == "blob") {
                            val path = item.optString("path")
                            if (path.isNotBlank()) {
                                add(
                                    ForgeRepoFile(
                                        path = path,
                                        sha = item.optString("id").takeIf { it.isNotBlank() },
                                        isPriorityManifest = isPriorityFile(path)
                                    )
                                )
                            }
                        }
                    }
                }.sortedWith(compareByDescending<ForgeRepoFile> { it.isPriorityManifest }.thenBy { it.path }).take(60)
            },
            onFailure = { emptyList() }
        )

        return Result.success(
            ForgeRepoSnapshot(
                parsedRepo = repo.copy(branch = defaultBranch),
                displayName = metaJson.optString("path_with_namespace", repo.fullPath),
                description = metaJson.optString("description", "No repository description."),
                defaultBranch = defaultBranch,
                visibility = metaJson.optString("visibility", "public").uppercase(),
                stars = metaJson.optInt("star_count", 0),
                openIssues = metaJson.optInt("open_issues_count", 0),
                files = files,
                authenticatedRead = !token.isNullOrBlank()
            )
        )
    }

    private fun fetchGitLabFile(repo: ParsedForgeRepo, branch: String, path: String, token: String?): Result<String> {
        val projectId = urlEncode(repo.fullPath)
        val fileId = urlEncode(path)
        val url = "https://gitlab.com/api/v4/projects/$projectId/repository/files/$fileId/raw?ref=${urlEncode(branch)}"
        return executeRawText(buildGitLabRequest(url, token)).map { it.take(MAX_FILE_CHARS) }
    }

    private fun commitGitLabFile(
        repo: ParsedForgeRepo,
        branch: String,
        path: String,
        updatedContent: String,
        commitMessage: String,
        token: String
    ): Result<ForgeCommitResult> {
        val projectId = urlEncode(repo.fullPath)
        val fileId = urlEncode(path)
        val url = "https://gitlab.com/api/v4/projects/$projectId/repository/files/$fileId"
        val bodyJson = JSONObject().apply {
            put("branch", branch)
            put("content", updatedContent)
            put("commit_message", commitMessage)
        }
        val req = Request.Builder()
            .url(url)
            .header("PRIVATE-TOKEN", token)
            .header("User-Agent", "PatchForge-Android")
            .put(bodyJson.toString().toRequestBody(jsonMediaType))
            .build()
        executeJson(req).getOrElse { return Result.failure(it) }
        return Result.success(
            ForgeCommitResult(
                provider = GitForgeProvider.GITLAB,
                filePath = path,
                branch = branch,
                commitSha = null,
                message = "Committed fix to GitLab ${repo.fullPath}@$branch"
            )
        )
    }

    private fun buildGitLabRequest(url: String, token: String?): Request {
        val builder = Request.Builder()
            .url(url)
            .header("User-Agent", "PatchForge-Android")
        if (!token.isNullOrBlank()) {
            builder.header("PRIVATE-TOKEN", token.trim())
        }
        return builder.get().build()
    }

    // --- Codeberg (Gitea / Forgejo v1 API) ---

    private fun inspectCodeberg(repo: ParsedForgeRepo, token: String?): Result<ForgeRepoSnapshot> {
        val metaJson = executeJson(buildCodebergRequest("https://codeberg.org/api/v1/repos/${repo.owner}/${repo.repo}", token))
            .getOrElse { return Result.failure(it) }
        val defaultBranch = repo.branch ?: metaJson.optString("default_branch", "main").ifBlank { "main" }

        val treeUrl = "https://codeberg.org/api/v1/repos/${repo.owner}/${repo.repo}/git/trees/${urlEncode(defaultBranch)}?recursive=1"
        val files = executeJson(buildCodebergRequest(treeUrl, token)).fold(
            onSuccess = { treeJson ->
                val arr = treeJson.optJSONArray("tree") ?: JSONArray()
                buildList {
                    for (i in 0 until arr.length()) {
                        val item = arr.optJSONObject(i) ?: continue
                        if (item.optString("type") == "blob") {
                            val path = item.optString("path")
                            if (path.isNotBlank()) {
                                add(
                                    ForgeRepoFile(
                                        path = path,
                                        sizeBytes = item.optLong("size", 0L),
                                        sha = item.optString("sha").takeIf { it.isNotBlank() },
                                        isPriorityManifest = isPriorityFile(path)
                                    )
                                )
                            }
                        }
                    }
                }.sortedWith(compareByDescending<ForgeRepoFile> { it.isPriorityManifest }.thenBy { it.path }).take(60)
            },
            onFailure = { emptyList() }
        )

        return Result.success(
            ForgeRepoSnapshot(
                parsedRepo = repo.copy(branch = defaultBranch),
                displayName = metaJson.optString("full_name", repo.fullPath),
                description = metaJson.optString("description", "No repository description."),
                defaultBranch = defaultBranch,
                visibility = if (metaJson.optBoolean("private", false)) "PRIVATE" else "PUBLIC",
                stars = metaJson.optInt("stars_count", 0),
                openIssues = metaJson.optInt("open_issues_count", 0),
                files = files,
                authenticatedRead = !token.isNullOrBlank()
            )
        )
    }

    private fun fetchCodebergFile(repo: ParsedForgeRepo, branch: String, path: String, token: String?): Result<String> {
        val encodedPath = path.split("/").joinToString("/") { urlEncode(it) }
        val url = "https://codeberg.org/api/v1/repos/${repo.owner}/${repo.repo}/raw/$encodedPath?ref=${urlEncode(branch)}"
        return executeRawText(buildCodebergRequest(url, token)).map { it.take(MAX_FILE_CHARS) }
    }

    private fun commitCodebergFile(
        repo: ParsedForgeRepo,
        branch: String,
        path: String,
        updatedContent: String,
        commitMessage: String,
        token: String
    ): Result<ForgeCommitResult> {
        val encodedPath = path.split("/").joinToString("/") { urlEncode(it) }
        val url = "https://codeberg.org/api/v1/repos/${repo.owner}/${repo.repo}/contents/$encodedPath"
        val existingSha = executeJson(buildCodebergRequest("$url?ref=${urlEncode(branch)}", token))
            .getOrNull()?.optString("sha")?.takeIf { it.isNotBlank() }

        val bodyJson = JSONObject().apply {
            put("branch", branch)
            put("content", Base64.encodeToString(updatedContent.toByteArray(StandardCharsets.UTF_8), Base64.NO_WRAP))
            put("message", commitMessage)
            if (existingSha != null) put("sha", existingSha)
        }
        val req = Request.Builder()
            .url(url)
            .header("Authorization", "token $token")
            .header("User-Agent", "PatchForge-Android")
            .put(bodyJson.toString().toRequestBody(jsonMediaType))
            .build()
        val resp = executeJson(req).getOrElse { return Result.failure(it) }
        val sha = resp.optJSONObject("commit")?.optString("sha")?.take(10)
        return Result.success(
            ForgeCommitResult(
                provider = GitForgeProvider.CODEBERG,
                filePath = path,
                branch = branch,
                commitSha = sha,
                message = "Committed fix to Codeberg ${repo.fullPath}@$branch"
            )
        )
    }

    private fun buildCodebergRequest(url: String, token: String?): Request {
        val builder = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .header("User-Agent", "PatchForge-Android")
        if (!token.isNullOrBlank()) {
            builder.header("Authorization", "token ${token.trim()}")
        }
        return builder.get().build()
    }

    // --- Hugging Face Hub ---

    private fun inspectHuggingFace(repo: ParsedForgeRepo, token: String?): Result<ForgeRepoSnapshot> {
        val apiBase = "https://huggingface.co/api/${repo.hfEntityType}/${repo.owner}/${repo.repo}"
        val metaJson = executeJson(buildHuggingFaceRequest(apiBase, token))
            .getOrElse { return Result.failure(it) }
        val defaultBranch = repo.branch ?: "main"

        val treeUrl = "$apiBase/tree/${urlEncode(defaultBranch)}?recursive=true"
        val files = executeJsonArray(buildHuggingFaceRequest(treeUrl, token)).fold(
            onSuccess = { arr ->
                buildList {
                    for (i in 0 until arr.length()) {
                        val item = arr.optJSONObject(i) ?: continue
                        if (item.optString("type") == "file") {
                            val path = item.optString("path")
                            if (path.isNotBlank()) {
                                add(
                                    ForgeRepoFile(
                                        path = path,
                                        sizeBytes = item.optLong("size", 0L),
                                        sha = item.optString("oid").takeIf { it.isNotBlank() },
                                        isPriorityManifest = isPriorityFile(path)
                                    )
                                )
                            }
                        }
                    }
                }.sortedWith(compareByDescending<ForgeRepoFile> { it.isPriorityManifest }.thenBy { it.path }).take(60)
            },
            onFailure = { emptyList() }
        )

        return Result.success(
            ForgeRepoSnapshot(
                parsedRepo = repo.copy(branch = defaultBranch),
                displayName = metaJson.optString("id", repo.fullPath),
                description = metaJson.optString("pipeline_tag", "Hugging Face ${repo.hfEntityType.removeSuffix("s")} repository"),
                defaultBranch = defaultBranch,
                visibility = if (metaJson.optBoolean("private", false)) "PRIVATE" else "PUBLIC",
                stars = metaJson.optInt("likes", 0),
                openIssues = 0,
                files = files,
                authenticatedRead = !token.isNullOrBlank()
            )
        )
    }

    private fun fetchHuggingFaceFile(repo: ParsedForgeRepo, branch: String, path: String, token: String?): Result<String> {
        val prefix = if (repo.hfEntityType == "models") "" else "${repo.hfEntityType}/"
        val encodedPath = path.split("/").joinToString("/") { urlEncode(it) }
        val url = "https://huggingface.co/$prefix${repo.owner}/${repo.repo}/raw/${urlEncode(branch)}/$encodedPath"
        return executeRawText(buildHuggingFaceRequest(url, token)).map { it.take(MAX_FILE_CHARS) }
    }

    private fun buildHuggingFaceRequest(url: String, token: String?): Request {
        val builder = Request.Builder()
            .url(url)
            .header("User-Agent", "PatchForge-Android")
        if (!token.isNullOrBlank()) {
            builder.header("Authorization", "Bearer ${token.trim()}")
        }
        return builder.get().build()
    }

    // --- Shared HTTP helpers ---

    private fun executeJson(request: Request): Result<JSONObject> {
        client.newCall(request).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                return Result.failure(IllegalStateException(formatHttpError(resp.code, body)))
            }
            return Result.success(JSONObject(body))
        }
    }

    private fun executeJsonArray(request: Request): Result<JSONArray> {
        client.newCall(request).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                return Result.failure(IllegalStateException(formatHttpError(resp.code, body)))
            }
            return Result.success(JSONArray(body))
        }
    }

    private fun executeRawText(request: Request): Result<String> {
        client.newCall(request).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                return Result.failure(IllegalStateException(formatHttpError(resp.code, body)))
            }
            return Result.success(body)
        }
    }

    private fun isPriorityFile(path: String): Boolean {
        val fileName = path.substringAfterLast("/").lowercase()
        return fileName in priorityFileNames ||
            fileName.endsWith(".kt") ||
            fileName.endsWith(".py") ||
            fileName.endsWith(".ts") ||
            fileName.endsWith(".go")
    }

    private fun urlEncode(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8.name()).replace("+", "%20")

    private fun formatHttpError(code: Int, body: String): String {
        val apiMessage = try {
            JSONObject(body).optString("message").takeIf { it.isNotBlank() }
                ?: JSONObject(body).optString("error").takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            null
        }
        return when (code) {
            401 -> "Authentication failed (401). Check your access token scope or expiration."
            403 -> "Access denied or rate limited (403). Add a valid access token with read/write scope."
            404 -> "Repository, branch, or file not found (404). For private repos, add an access token."
            else -> "Forge API error ($code)${apiMessage?.let { ": $it" } ?: ""}"
        }
    }

    private fun sanitizeNetworkError(raw: String?): String =
        raw?.take(220) ?: "Unable to contact Git forge API. Verify network connectivity."

    companion object {
        private const val MAX_FILE_CHARS = 48_000
    }
}
