package com.example.data.remote

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class HealthResponse(
    @Json(name = "status") val status: String = "ok",
    @Json(name = "service") val service: String = "patchforge-fastapi",
    @Json(name = "worker_backend") val workerBackend: String = "celery-redis",
    @Json(name = "active_workers") val activeWorkers: Int = 0,
    @Json(name = "db_connected") val dbConnected: Boolean = true,
    @Json(name = "provider") val provider: String = "google-genai"
)

@JsonClass(generateAdapter = true)
data class AnalyzeRequest(
    @Json(name = "file_path") val filePath: String,
    @Json(name = "content") val content: String,
    @Json(name = "engine_mode") val engineMode: String,
    @Json(name = "model_id") val modelId: String,
    @Json(name = "high_thinking") val highThinking: Boolean
)

@JsonClass(generateAdapter = true)
data class RemoteFindingDto(
    @Json(name = "file_path") val filePath: String,
    @Json(name = "line_bounds") val lineBounds: String,
    @Json(name = "threat_category") val threatCategory: String,
    @Json(name = "severity") val severity: String,
    @Json(name = "description") val description: String,
    @Json(name = "replacement_snippet") val replacementSnippet: String,
    @Json(name = "original_snippet") val originalSnippet: String = "",
    @Json(name = "engine") val engine: String = "AGENTIC_REVIEW",
    @Json(name = "package_or_symbol") val packageOrSymbol: String = "",
    @Json(name = "cyclomatic_complexity") val cyclomaticComplexity: Int = 1
)

@JsonClass(generateAdapter = true)
data class AnalyzeJobResponse(
    @Json(name = "job_id") val jobId: String,
    @Json(name = "status") val status: String,
    @Json(name = "summary") val summary: String = "",
    @Json(name = "risk_score") val riskScore: Int = 0,
    @Json(name = "findings") val findings: List<RemoteFindingDto> = emptyList()
)

@JsonClass(generateAdapter = true)
data class PatchApplyRequest(
    @Json(name = "target_branch") val targetBranch: String,
    @Json(name = "commit_message") val commitMessage: String,
    @Json(name = "unified_diff") val unifiedDiff: String,
    @Json(name = "finding_ids") val findingIds: List<String>
)

@JsonClass(generateAdapter = true)
data class PatchApplyResponse(
    @Json(name = "status") val status: String,
    @Json(name = "commit_sha") val commitSha: String? = null,
    @Json(name = "branch") val branch: String? = null,
    @Json(name = "message") val message: String? = null
)

@JsonClass(generateAdapter = true)
data class WorkerEvent(
    @Json(name = "event_type") val eventType: String = "TELEMETRY",
    @Json(name = "worker") val worker: String = "asyncio-worker",
    @Json(name = "job_id") val jobId: String = "",
    @Json(name = "message") val message: String = "",
    @Json(name = "timestamp") val timestamp: Long = System.currentTimeMillis()
)
