package com.example.data.repository

import android.util.Log
import com.example.data.ai.AgentPersona
import com.example.data.ai.AiModelProfile
import com.example.data.ai.AiReviewProvider
import com.example.data.ai.AuditEngineMode
import com.example.data.ai.ChatTurn
import com.example.data.local.AuditRunEntity
import com.example.data.local.BackendConfig
import com.example.data.local.BackendPreferences
import com.example.data.local.ChatMessageEntity
import com.example.data.local.FindingEntity
import com.example.data.local.PatchForgeDao
import com.example.data.local.PatchStatus
import com.example.data.local.PendingSyncActionEntity
import com.example.data.remote.AnalyzeRequest
import com.example.data.remote.FastApiClientFactory
import com.example.data.remote.HealthResponse
import com.example.data.remote.PatchApplyRequest
import com.example.data.remote.WorkerEvent
import com.example.data.remote.WorkerWebSocketClient
import com.example.data.remote.WsConnectionState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.util.UUID

private const val TAG = "PatchForgeRepository"

data class BranchDispatchResult(
    val dispatchedOnline: Boolean,
    val message: String,
    val commitSha: String? = null
)

class PatchForgeRepository(
    private val dao: PatchForgeDao,
    private val preferences: BackendPreferences,
    private val aiProvider: AiReviewProvider,
    private val wsClient: WorkerWebSocketClient
) {
    val auditRuns: Flow<List<AuditRunEntity>> = dao.observeAuditRuns()
    val allFindings: Flow<List<FindingEntity>> = dao.observeAllFindings()
    val pendingSyncActions: Flow<List<PendingSyncActionEntity>> = dao.observePendingSyncActions()
    val chatMessages: Flow<List<ChatMessageEntity>> = dao.observeChatMessages()
    val backendConfig: Flow<BackendConfig> = preferences.configFlow
    val wsConnectionState: Flow<WsConnectionState> = wsClient.connectionState
    val workerEvents: Flow<List<WorkerEvent>> = wsClient.events

    val activeProviderName: String
        get() = aiProvider.providerName

    fun observeFindingsForRun(runId: String): Flow<List<FindingEntity>> =
        dao.observeFindingsForRun(runId)

    suspend fun saveBackendConfig(
        baseUrl: String,
        bearerToken: String,
        defaultTargetBranch: String,
        orchestratorMode: String,
        autoRetryOffline: Boolean
    ) {
        preferences.saveBackendConfig(
            baseUrl = baseUrl,
            bearerToken = bearerToken,
            defaultTargetBranch = defaultTargetBranch,
            orchestratorMode = orchestratorMode,
            autoRetryOffline = autoRetryOffline
        )
    }

    suspend fun probeFastApiHealth(baseUrl: String, bearerToken: String): Result<HealthResponse> {
        val service = FastApiClientFactory.createService(baseUrl, bearerToken)
            ?: return Result.failure(IllegalArgumentException("Invalid FastAPI URL. Must start with http:// or https://"))
        return try {
            val health = service.getHealth()
            wsClient.recordLocalTelemetry(
                worker = health.workerBackend,
                eventType = "HEALTH_OK",
                message = "FastAPI online · ${health.activeWorkers} workers · provider=${health.provider}"
            )
            Result.success(health)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "FastAPI /health probe failed", e)
            wsClient.recordLocalTelemetry(
                worker = "rest-client",
                eventType = "OFFLINE",
                message = "FastAPI unreachable (${e.localizedMessage ?: "connection refused"}). Local Gemini engine remains active."
            )
            Result.failure(e)
        }
    }

    fun connectWorkerStream(baseUrl: String, bearerToken: String) {
        wsClient.connect(baseUrl, bearerToken)
    }

    fun disconnectWorkerStream() {
        wsClient.disconnect()
    }

    /**
     * Executes dual-engine audit via Gemini Structured Outputs (Firebase AI Logic), persists
     * locally in Room, and optionally mirrors the run to FastAPI when FASTAPI_HYBRID is active.
     */
    suspend fun executeDualEngineAudit(
        filePath: String,
        payload: String,
        engineMode: AuditEngineMode,
        modelProfile: AiModelProfile
    ): AuditRunEntity {
        val runId = "RUN-${UUID.randomUUID().toString().take(8).uppercase()}"
        wsClient.recordLocalTelemetry(
            worker = "gemini-orchestrator",
            eventType = "AUDIT_START",
            jobId = runId,
            message = "Executing ${engineMode.id} on $filePath via ${modelProfile.modelId}" +
                if (modelProfile.highThinking) " [ThinkingLevel.HIGH]" else ""
        )

        val structured = aiProvider.runStructuredAudit(
            filePath = filePath,
            payload = payload,
            engineMode = engineMode,
            modelProfile = modelProfile
        )

        val config = preferences.configFlow.first()
        var syncStatus = "LOCAL_ENGINE"
        var remoteJobId: String? = null

        if (config.orchestratorMode == "FASTAPI_HYBRID") {
            val service = FastApiClientFactory.createService(config.baseUrl, config.bearerToken)
            val request = AnalyzeRequest(
                filePath = filePath,
                content = payload,
                engineMode = engineMode.id,
                modelId = modelProfile.modelId,
                highThinking = modelProfile.highThinking
            )
            if (service != null) {
                try {
                    val remoteJob = service.submitAnalysis(request)
                    syncStatus = "SYNCED"
                    remoteJobId = remoteJob.jobId
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    syncStatus = "QUEUED_OFFLINE"
                    enqueueOfflineAction(
                        actionType = "REMOTE_ANALYZE",
                        endpoint = "${config.baseUrl}/api/v1/reviews/analyze",
                        targetBranch = config.defaultTargetBranch,
                        commitMessage = "Audit sync: $filePath",
                        payloadJson = FastApiClientFactory.moshi.adapter(AnalyzeRequest::class.java).toJson(request),
                        error = e.localizedMessage ?: "FastAPI unreachable"
                    )
                }
            }
        }

        val runEntity = AuditRunEntity(
            runId = runId,
            title = filePath.substringAfterLast("/").ifBlank { filePath },
            filePath = filePath,
            engineMode = engineMode.id,
            inputPayload = payload,
            modelId = modelProfile.modelId,
            highThinkingEnabled = modelProfile.highThinking,
            summary = structured.summary,
            riskScore = structured.riskScore,
            maxNestingDepth = structured.astMetrics.maxNestingDepth,
            cyclomaticPeak = structured.astMetrics.cyclomaticPeak,
            astHotspots = structured.astMetrics.hotspots.joinToString(", "),
            syncStatus = syncStatus,
            remoteJobId = remoteJobId
        )

        val findingEntities = structured.findings.mapIndexed { idx, f ->
            FindingEntity(
                findingId = "$runId-F${idx + 1}",
                runId = runId,
                filePath = f.filePath,
                lineBounds = f.lineBounds,
                threatCategory = f.threatCategory,
                severity = f.severity,
                description = f.description,
                replacementSnippet = f.replacementSnippet,
                originalSnippet = f.originalSnippet,
                engineType = f.engineType,
                packageOrSymbol = f.packageOrSymbol,
                cyclomaticComplexity = f.cyclomaticComplexity,
                patchStatus = PatchStatus.PENDING
            )
        }

        dao.insertAuditRun(runEntity)
        if (findingEntities.isNotEmpty()) {
            dao.insertFindings(findingEntities)
        }

        wsClient.recordLocalTelemetry(
            worker = "gemini-orchestrator",
            eventType = "AUDIT_COMPLETE",
            jobId = runId,
            message = "Structured audit complete: ${findingEntities.size} findings, risk=${structured.riskScore}/100"
        )
        return runEntity
    }

    suspend fun updateFindingPatchStatus(findingId: String, status: String) {
        dao.updateFindingPatchStatus(findingId, status)
    }

    /**
     * Dispatches unified Git patch to FastAPI backend `POST /api/v1/patches/apply`.
     * If backend is offline/unreachable, cleanly queues the mutation in Room for retry.
     */
    suspend fun dispatchPatchesToBranch(
        findings: List<FindingEntity>,
        targetBranch: String,
        commitMessage: String
    ): BranchDispatchResult {
        val config = preferences.configFlow.first()
        val unifiedDiff = PatchGenerator.buildCombinedPatchBundle(findings)
        val request = PatchApplyRequest(
            targetBranch = targetBranch.trim().ifBlank { config.defaultTargetBranch },
            commitMessage = commitMessage.trim().ifBlank { "chore(patchforge): apply self-healing remediation patches" },
            unifiedDiff = unifiedDiff,
            findingIds = findings.map { it.findingId }
        )
        val payloadJson = FastApiClientFactory.moshi.adapter(PatchApplyRequest::class.java).toJson(request)
        val service = FastApiClientFactory.createService(config.baseUrl, config.bearerToken)

        if (service != null) {
            try {
                val response = service.applyPatch(request)
                findings.forEach { dao.updateFindingPatchStatus(it.findingId, PatchStatus.APPLIED) }
                wsClient.recordLocalTelemetry(
                    worker = "git-healer",
                    eventType = "PATCH_APPLIED",
                    message = "Applied ${findings.size} patch(es) to ${request.targetBranch} (${response.commitSha ?: "pushed"})"
                )
                return BranchDispatchResult(
                    dispatchedOnline = true,
                    message = response.message ?: "Applied to ${request.targetBranch}",
                    commitSha = response.commitSha
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "FastAPI patch apply unreachable; queuing offline", e)
                findings.forEach { dao.updateFindingPatchStatus(it.findingId, PatchStatus.QUEUED_SYNC) }
                enqueueOfflineAction(
                    actionType = "PATCH_APPLY",
                    endpoint = "${config.baseUrl}/api/v1/patches/apply",
                    targetBranch = request.targetBranch,
                    commitMessage = request.commitMessage,
                    payloadJson = payloadJson,
                    error = e.localizedMessage ?: "FastAPI backend offline"
                )
                return BranchDispatchResult(
                    dispatchedOnline = false,
                    message = "Backend offline — queued ${findings.size} patch(es) for ${request.targetBranch} in Retry Queue."
                )
            }
        } else {
            findings.forEach { dao.updateFindingPatchStatus(it.findingId, PatchStatus.QUEUED_SYNC) }
            enqueueOfflineAction(
                actionType = "PATCH_APPLY",
                endpoint = "${config.baseUrl}/api/v1/patches/apply",
                targetBranch = request.targetBranch,
                commitMessage = request.commitMessage,
                payloadJson = payloadJson,
                error = "FastAPI endpoint not configured"
            )
            return BranchDispatchResult(
                dispatchedOnline = false,
                message = "No valid FastAPI endpoint — queued in Offline Retry Queue."
            )
        }
    }

    private suspend fun enqueueOfflineAction(
        actionType: String,
        endpoint: String,
        targetBranch: String,
        commitMessage: String,
        payloadJson: String,
        error: String
    ) {
        val action = PendingSyncActionEntity(
            actionId = "SYNC-${UUID.randomUUID().toString().take(8).uppercase()}",
            actionType = actionType,
            targetEndpoint = endpoint,
            targetBranch = targetBranch,
            commitMessage = commitMessage,
            payloadJson = payloadJson,
            status = "QUEUED",
            retryCount = 0,
            lastError = error
        )
        dao.insertSyncAction(action)
        wsClient.recordLocalTelemetry(
            worker = "offline-queue",
            eventType = "QUEUED",
            jobId = action.actionId,
            message = "Buffered $actionType for $targetBranch ($error)"
        )
    }

    suspend fun retryPendingSyncQueue(): Pair<Int, Int> {
        val config = preferences.configFlow.first()
        val service = FastApiClientFactory.createService(config.baseUrl, config.bearerToken)
        val pending = dao.getRetryableSyncActions()
        if (pending.isEmpty()) return 0 to 0

        var succeeded = 0
        var failed = 0

        val patchAdapter = FastApiClientFactory.moshi.adapter(PatchApplyRequest::class.java)
        val analyzeAdapter = FastApiClientFactory.moshi.adapter(AnalyzeRequest::class.java)

        for (item in pending) {
            dao.updateSyncActionStatus(item.actionId, "RETRYING", item.retryCount + 1, item.lastError)
            if (service == null) {
                dao.updateSyncActionStatus(item.actionId, "FAILED", item.retryCount + 1, "Invalid FastAPI URL")
                failed++
                continue
            }
            try {
                when (item.actionType) {
                    "PATCH_APPLY" -> {
                        val req = patchAdapter.fromJson(item.payloadJson)
                            ?: throw IllegalStateException("Corrupt queued patch payload")
                        service.applyPatch(req)
                        req.findingIds.forEach { fid ->
                            dao.updateFindingPatchStatus(fid, PatchStatus.APPLIED)
                        }
                    }
                    "REMOTE_ANALYZE" -> {
                        val req = analyzeAdapter.fromJson(item.payloadJson)
                            ?: throw IllegalStateException("Corrupt queued analyze payload")
                        service.submitAnalysis(req)
                    }
                }
                dao.updateSyncActionStatus(item.actionId, "COMPLETED", item.retryCount + 1, null)
                succeeded++
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                dao.updateSyncActionStatus(
                    item.actionId,
                    "FAILED",
                    item.retryCount + 1,
                    e.localizedMessage ?: "Retry failed"
                )
                failed++
            }
        }

        wsClient.recordLocalTelemetry(
            worker = "offline-queue",
            eventType = "RETRY_PASS",
            message = "Retry pass complete: $succeeded synced, $failed remaining."
        )
        return succeeded to failed
    }

    suspend fun purgeCompletedSyncActions() = dao.purgeCompletedSyncActions()

    suspend fun deleteSyncAction(actionId: String) = dao.deleteSyncAction(actionId)

    // Multi-turn Gemini Chat Persistence & Streaming
    suspend fun appendUserChatMessage(
        content: String,
        modelProfile: AiModelProfile,
        persona: AgentPersona
    ) {
        dao.insertChatMessage(
            ChatMessageEntity(
                messageId = "MSG-${UUID.randomUUID().toString().take(8)}",
                role = "user",
                content = content,
                modelId = modelProfile.modelId,
                agentPersona = persona.label,
                highThinkingUsed = modelProfile.highThinking
            )
        )
    }

    suspend fun appendModelChatMessage(
        content: String,
        modelProfile: AiModelProfile,
        persona: AgentPersona
    ) {
        dao.insertChatMessage(
            ChatMessageEntity(
                messageId = "MSG-${UUID.randomUUID().toString().take(8)}",
                role = "model",
                content = content,
                modelId = modelProfile.modelId,
                agentPersona = persona.label,
                highThinkingUsed = modelProfile.highThinking
            )
        )
    }

    suspend fun getChatHistoryTurns(): List<ChatTurn> {
        return dao.getChatMessagesSnapshot().map { ChatTurn(role = it.role, content = it.content) }
    }

    fun streamAgentChat(
        history: List<ChatTurn>,
        userMessage: String,
        persona: AgentPersona,
        modelProfile: AiModelProfile,
        auditContext: String?
    ): Flow<String> = aiProvider.streamChatReply(
        history = history,
        userMessage = userMessage,
        persona = persona,
        modelProfile = modelProfile,
        auditContext = auditContext
    )

    suspend fun clearChatHistory() = dao.clearChatHistory()
}
