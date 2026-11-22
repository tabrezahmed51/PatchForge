package com.example.ui.viewmodel

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.R
import com.example.data.ai.AgentPersona
import com.example.data.ai.AiModelProfile
import com.example.data.ai.AuditEngineMode
import com.example.data.ai.GeminiAiLogicProvider
import com.example.data.ai.GroundedSearchResult
import com.example.data.ai.ThirdPartyMultimodalClient
import com.example.data.local.AiApiIntegrationConfig
import com.example.data.local.AuditFinding
import com.example.data.local.AuditRunEntity
import com.example.data.local.BackendConfig
import com.example.data.local.BackendPreferences
import com.example.data.local.ChatMessageEntity
import com.example.data.local.PatchForgeDatabase
import com.example.data.local.PatchStatus
import com.example.data.local.PendingSyncActionEntity
import com.example.data.remote.CloudAuditFinding
import com.example.data.remote.CloudAuditRun
import com.example.data.remote.FirestoreSyncRepository
import com.example.data.remote.HealthResponse
import com.example.data.remote.WorkerEvent
import com.example.data.remote.WorkerWebSocketClient
import com.example.data.remote.WsConnectionState
import com.example.data.repository.InspectionTemplate
import com.example.data.repository.InspectionTemplates
import com.example.data.repository.PatchForgeRepository
import com.example.data.repository.PatchGenerator
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val VM_TAG = "PatchForgeViewModel"

sealed interface AuditExecutionState {
    data object Idle : AuditExecutionState
    data class Analyzing(val modelLabel: String, val highThinking: Boolean) : AuditExecutionState
    data class Success(val runId: String, val findingCount: Int) : AuditExecutionState
    data class Error(val message: String) : AuditExecutionState
}

sealed interface ChatStreamState {
    data object Idle : ChatStreamState
    data object Thinking : ChatStreamState
    data class Streaming(val partialText: String) : ChatStreamState
    data class Error(val message: String) : ChatStreamState
}

sealed interface BackendHealthState {
    data object Unchecked : BackendHealthState
    data object Probing : BackendHealthState
    data class Online(val health: HealthResponse) : BackendHealthState
    data class Offline(val reason: String) : BackendHealthState
}

sealed interface SearchGroundingState {
    data object Idle : SearchGroundingState
    data object Searching : SearchGroundingState
    data class Success(val result: GroundedSearchResult) : SearchGroundingState
    data class Error(val message: String) : SearchGroundingState
}

sealed interface MultimodalState {
    data object Idle : MultimodalState
    data object Running : MultimodalState
    data class Success(val output: String, val providerLabel: String) : MultimodalState
    data class Error(val message: String) : MultimodalState
}

sealed interface CloudSyncUiState<out T> {
    data object Loading : CloudSyncUiState<Nothing>
    data class Success<T>(val data: T) : CloudSyncUiState<T>
    data class Error(val message: String) : CloudSyncUiState<Nothing>
}

data class ScanEditorState(
    val filePath: String = InspectionTemplates.all.first().filePath,
    val payload: String = InspectionTemplates.all.first().content,
    val engineMode: AuditEngineMode = AuditEngineMode.DUAL_ENGINE,
    val modelProfile: AiModelProfile = AiModelProfile.HYDROGEN_GENERAL,
    val selectedRunId: String? = null,
    val severityFilter: String = "ALL",
    val engineFilter: String = "ALL"
)

data class AgentUiState(
    val persona: AgentPersona = AgentPersona.SUPPLY_CHAIN_AUDITOR,
    val modelProfile: AiModelProfile = AiModelProfile.CARBON_HIGH_THINKING,
    val attachActiveAuditContext: Boolean = true,
    val streamState: ChatStreamState = ChatStreamState.Idle,
    val isTranscribingAudio: Boolean = false
)

class PatchForgeViewModel(
    private val repository: PatchForgeRepository,
    private val currentUserId: String,
    private val userDisplayName: String,
    private val userEmail: String?
) : ViewModel() {

    val activeProviderName: String = repository.activeProviderName

    val auditRuns: StateFlow<List<AuditRunEntity>> = repository.auditRuns
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val allFindings: StateFlow<List<AuditFinding>> = repository.allFindings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val pendingSyncActions: StateFlow<List<PendingSyncActionEntity>> = repository.pendingSyncActions
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val chatMessages: StateFlow<List<ChatMessageEntity>> = repository.chatMessages
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val backendConfig: StateFlow<BackendConfig> = repository.backendConfig
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BackendConfig())

    val aiIntegrationConfig: StateFlow<AiApiIntegrationConfig> = repository.aiIntegrationConfig
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AiApiIntegrationConfig())

    val wsConnectionState: StateFlow<WsConnectionState> = repository.wsConnectionState
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WsConnectionState.Disconnected)

    val workerEvents: StateFlow<List<WorkerEvent>> = repository.workerEvents
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // Two-tier Firestore Flow error unwrapping protecting Compose collectors
    val cloudRunsState: StateFlow<CloudSyncUiState<List<CloudAuditRun>>> =
        repository.observeCloudAuditRuns(currentUserId)
            .map<List<CloudAuditRun>, CloudSyncUiState<List<CloudAuditRun>>> { CloudSyncUiState.Success(it) }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val root = firestoreError ?: error
                Log.w(VM_TAG, "Error observing cloud audit runs", root)
                emit(CloudSyncUiState.Error(root.message ?: "Cloud sync unavailable"))
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CloudSyncUiState.Loading)

    val cloudFindingsState: StateFlow<CloudSyncUiState<List<CloudAuditFinding>>> =
        repository.observeCloudFindings(currentUserId)
            .map<List<CloudAuditFinding>, CloudSyncUiState<List<CloudAuditFinding>>> { CloudSyncUiState.Success(it) }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val root = firestoreError ?: error
                Log.w(VM_TAG, "Error observing cloud findings", root)
                emit(CloudSyncUiState.Error(root.message ?: "Cloud findings sync unavailable"))
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CloudSyncUiState.Loading)

    private val _scanEditor = MutableStateFlow(ScanEditorState())
    val scanEditor: StateFlow<ScanEditorState> = _scanEditor.asStateFlow()

    private val _auditState = MutableStateFlow<AuditExecutionState>(AuditExecutionState.Idle)
    val auditState: StateFlow<AuditExecutionState> = _auditState.asStateFlow()

    private val _agentState = MutableStateFlow(AgentUiState())
    val agentState: StateFlow<AgentUiState> = _agentState.asStateFlow()

    private val _searchState = MutableStateFlow<SearchGroundingState>(SearchGroundingState.Idle)
    val searchState: StateFlow<SearchGroundingState> = _searchState.asStateFlow()

    private val _multimodalState = MutableStateFlow<MultimodalState>(MultimodalState.Idle)
    val multimodalState: StateFlow<MultimodalState> = _multimodalState.asStateFlow()

    private val _healthState = MutableStateFlow<BackendHealthState>(BackendHealthState.Unchecked)
    val healthState: StateFlow<BackendHealthState> = _healthState.asStateFlow()

    private val _isRetryingQueue = MutableStateFlow(false)
    val isRetryingQueue: StateFlow<Boolean> = _isRetryingQueue.asStateFlow()

    private val _snackbarEvents = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val snackbarEvents: SharedFlow<String> = _snackbarEvents.asSharedFlow()

    val activeRun: StateFlow<AuditRunEntity?> = combine(auditRuns, _scanEditor) { runs, editor ->
        runs.firstOrNull { it.runId == editor.selectedRunId } ?: runs.firstOrNull()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val activeRunFindings: StateFlow<List<AuditFinding>> = combine(
        activeRun,
        allFindings,
        _scanEditor
    ) { run, findings, editor ->
        val runFindings = if (run != null) findings.filter { it.runId == run.runId } else findings
        runFindings.filter { f ->
            val sevOk = editor.severityFilter == "ALL" || f.severity.equals(editor.severityFilter, ignoreCase = true)
            val engOk = editor.engineFilter == "ALL" || f.engineType.equals(editor.engineFilter, ignoreCase = true)
            sevOk && engOk
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private var activeChatJob: Job? = null

    init {
        viewModelScope.launch {
            repository.ensureCloudUserProfile(userDisplayName, userEmail)
        }
    }

    fun updateFilePath(path: String) {
        _scanEditor.value = _scanEditor.value.copy(filePath = path)
    }

    fun updatePayload(payload: String) {
        _scanEditor.value = _scanEditor.value.copy(payload = payload)
    }

    fun selectEngineMode(mode: AuditEngineMode) {
        _scanEditor.value = _scanEditor.value.copy(engineMode = mode)
    }

    fun selectAuditModel(profile: AiModelProfile) {
        _scanEditor.value = _scanEditor.value.copy(modelProfile = profile)
    }

    fun toggleAuditHighThinking(enabled: Boolean) {
        val nextProfile = if (enabled) AiModelProfile.CARBON_HIGH_THINKING else AiModelProfile.HYDROGEN_GENERAL
        _scanEditor.value = _scanEditor.value.copy(modelProfile = nextProfile)
    }

    fun loadTemplate(template: InspectionTemplate) {
        _scanEditor.value = _scanEditor.value.copy(
            filePath = template.filePath,
            payload = template.content,
            engineMode = template.recommendedEngine
        )
        _snackbarEvents.tryEmit("Loaded ${template.label} target into scanner.")
    }

    fun selectRun(runId: String) {
        _scanEditor.value = _scanEditor.value.copy(selectedRunId = runId)
    }

    fun setSeverityFilter(severity: String) {
        _scanEditor.value = _scanEditor.value.copy(severityFilter = severity)
    }

    fun runDualEngineAudit() {
        val editor = _scanEditor.value
        val trimmedPayload = editor.payload.trim()
        val targetPath = editor.filePath.trim().ifBlank { "untitled_manifest.txt" }
        if (trimmedPayload.isBlank()) {
            _auditState.value = AuditExecutionState.Error("Paste a manifest, lockfile, or PR diff before executing.")
            return
        }

        viewModelScope.launch {
            _auditState.value = AuditExecutionState.Analyzing(
                modelLabel = editor.modelProfile.badge,
                highThinking = editor.modelProfile.highThinking
            )
            try {
                val run = repository.executeDualEngineAudit(
                    filePath = targetPath,
                    payload = trimmedPayload,
                    engineMode = editor.engineMode,
                    modelProfile = editor.modelProfile
                )
                _scanEditor.value = _scanEditor.value.copy(
                    selectedRunId = run.runId,
                    severityFilter = "ALL",
                    engineFilter = "ALL"
                )
                val count = allFindings.value.count { it.runId == run.runId }
                _auditState.value = AuditExecutionState.Success(run.runId, count)
                _snackbarEvents.tryEmit("Audit ${run.runId} complete · Risk ${run.riskScore}/100")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(VM_TAG, "Audit execution failed", e)
                _auditState.value = AuditExecutionState.Error(
                    e.localizedMessage ?: "Audit failed. Verify connectivity and retry."
                )
            }
        }
    }

    // Google Search Grounding (gemini-4-hydrogen + googleSearch tool)
    fun verifyWithGoogleSearch(query: String) {
        val clean = query.trim()
        if (clean.isBlank()) return
        viewModelScope.launch {
            _searchState.value = SearchGroundingState.Searching
            try {
                val result = repository.runSearchGroundedVerification(clean)
                _searchState.value = SearchGroundingState.Success(result)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(VM_TAG, "Search grounding failed", e)
                _searchState.value = SearchGroundingState.Error(
                    e.localizedMessage ?: "Google Search verification failed."
                )
            }
        }
    }

    // Audio Transcription (gemini-3.5-transcribe)
    fun transcribeVoiceClip(audioBytes: ByteArray, onTranscriptReady: (String) -> Unit) {
        viewModelScope.launch {
            _agentState.value = _agentState.value.copy(isTranscribingAudio = true)
            try {
                val transcript = repository.transcribeVoiceAudio(audioBytes)
                onTranscriptReady(transcript)
                _snackbarEvents.tryEmit("Transcribed via gemini-3.5-transcribe.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(VM_TAG, "Audio transcription failed", e)
                _snackbarEvents.tryEmit("Transcription failed: ${e.localizedMessage ?: "error"}")
            } finally {
                _agentState.value = _agentState.value.copy(isTranscribingAudio = false)
            }
        }
    }

    // 3rd-Party AI API Multimodal Integration Settings + Live Workbench
    fun saveAiApiConfig(config: AiApiIntegrationConfig) {
        viewModelScope.launch {
            repository.saveAiIntegrationConfig(config)
            _snackbarEvents.tryEmit("Saved AI provider config: ${config.activeVendor.label}")
        }
    }

    fun clearThirdPartyApiKeys() {
        viewModelScope.launch {
            repository.clearAllThirdPartyKeys()
            _snackbarEvents.tryEmit("Cleared all 3rd-party API keys. Reverted to Native Gemini.")
        }
    }

    fun runMultimodalWorkbench(prompt: String, image: Bitmap?, modelProfile: AiModelProfile) {
        val cleanPrompt = prompt.trim()
        if (cleanPrompt.isBlank()) {
            _multimodalState.value = MultimodalState.Error("Enter a multimodal prompt or transcribe audio first.")
            return
        }
        viewModelScope.launch {
            _multimodalState.value = MultimodalState.Running
            try {
                val (output, providerLabel) = repository.runMultimodalInference(cleanPrompt, image, modelProfile)
                _multimodalState.value = MultimodalState.Success(output, providerLabel)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(VM_TAG, "Multimodal workbench failed", e)
                _multimodalState.value = MultimodalState.Error(
                    e.localizedMessage ?: "Multimodal request failed."
                )
            }
        }
    }

    // Self-Healing Patch Actions
    fun approveFinding(findingId: String) {
        viewModelScope.launch {
            repository.updateFindingPatchStatus(findingId, PatchStatus.APPROVED)
            _snackbarEvents.tryEmit("Patch $findingId marked APPROVED.")
        }
    }

    fun dismissFinding(findingId: String) {
        viewModelScope.launch {
            repository.updateFindingPatchStatus(findingId, PatchStatus.DISMISSED)
            _snackbarEvents.tryEmit("Finding $findingId dismissed.")
        }
    }

    fun resetFindingStatus(findingId: String) {
        viewModelScope.launch {
            repository.updateFindingPatchStatus(findingId, PatchStatus.PENDING)
        }
    }

    fun buildPatchForFinding(finding: AuditFinding): String =
        PatchGenerator.buildUnifiedDiff(finding)

    fun buildApprovedBundle(): String {
        val approved = allFindings.value.filter {
            it.patchStatus == PatchStatus.APPROVED || it.patchStatus == PatchStatus.QUEUED_SYNC
        }
        return PatchGenerator.buildCombinedPatchBundle(approved)
    }

    fun dispatchApprovedPatches(targetBranch: String, commitMessage: String) {
        val targetFindings = allFindings.value.filter { it.patchStatus == PatchStatus.APPROVED }
        if (targetFindings.isEmpty()) {
            _snackbarEvents.tryEmit("Approve at least one patch before dispatching to branch.")
            return
        }
        viewModelScope.launch {
            val result = repository.dispatchPatchesToBranch(
                findings = targetFindings,
                targetBranch = targetBranch,
                commitMessage = commitMessage
            )
            _snackbarEvents.tryEmit(result.message)
        }
    }

    fun dispatchSinglePatch(finding: AuditFinding, targetBranch: String, commitMessage: String) {
        viewModelScope.launch {
            val result = repository.dispatchPatchesToBranch(
                findings = listOf(finding),
                targetBranch = targetBranch,
                commitMessage = commitMessage
            )
            _snackbarEvents.tryEmit(result.message)
        }
    }

    // Multi-turn Gemini Agent Chat
    fun selectAgentPersona(persona: AgentPersona) {
        _agentState.value = _agentState.value.copy(persona = persona)
    }

    fun selectAgentModel(profile: AiModelProfile) {
        _agentState.value = _agentState.value.copy(modelProfile = profile)
    }

    fun toggleAttachAuditContext(enabled: Boolean) {
        _agentState.value = _agentState.value.copy(attachActiveAuditContext = enabled)
    }

    fun sendChatMessage(rawText: String) {
        val message = rawText.trim()
        if (message.isBlank()) return

        activeChatJob?.cancel()
        val currentAgent = _agentState.value

        activeChatJob = viewModelScope.launch {
            val historySnapshot = repository.getChatHistoryTurns()
            repository.appendUserChatMessage(
                content = message,
                modelProfile = currentAgent.modelProfile,
                persona = currentAgent.persona
            )

            val contextSummary = if (currentAgent.attachActiveAuditContext) {
                buildActiveAuditContextString()
            } else null

            _agentState.value = _agentState.value.copy(streamState = ChatStreamState.Thinking)
            val buffer = StringBuilder()

            try {
                repository.streamAgentChat(
                    history = historySnapshot,
                    userMessage = message,
                    persona = currentAgent.persona,
                    modelProfile = currentAgent.modelProfile,
                    auditContext = contextSummary
                ).collect { delta ->
                    buffer.append(delta)
                    _agentState.value = _agentState.value.copy(
                        streamState = ChatStreamState.Streaming(buffer.toString())
                    )
                }

                val finalReply = buffer.toString().trim()
                if (finalReply.isNotEmpty()) {
                    repository.appendModelChatMessage(
                        content = finalReply,
                        modelProfile = currentAgent.modelProfile,
                        persona = currentAgent.persona
                    )
                    _agentState.value = _agentState.value.copy(streamState = ChatStreamState.Idle)
                } else {
                    _agentState.value = _agentState.value.copy(
                        streamState = ChatStreamState.Error("Gemini returned an empty response.")
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(VM_TAG, "Agent chat error", e)
                _agentState.value = _agentState.value.copy(
                    streamState = ChatStreamState.Error(
                        e.localizedMessage ?: "Gemini chat stream failed."
                    )
                )
            }
        }
    }

    fun clearChatHistory() {
        activeChatJob?.cancel()
        viewModelScope.launch {
            repository.clearChatHistory()
            _agentState.value = _agentState.value.copy(streamState = ChatStreamState.Idle)
            _snackbarEvents.tryEmit("Agent conversation history cleared.")
        }
    }

    private fun buildActiveAuditContextString(): String? {
        val run = activeRun.value ?: return null
        val findings = allFindings.value.filter { it.runId == run.runId }
        return buildString {
            appendLine("Run ID: ${run.runId} | File: ${run.filePath} | Risk: ${run.riskScore}/100")
            appendLine("Summary: ${run.summary}")
            appendLine("AST Peak Cyclomatic: ${run.cyclomaticPeak} | Max Nesting: ${run.maxNestingDepth}")
            findings.take(8).forEach { f ->
                appendLine(
                    "- [${f.severity}] ${f.threatCategory} @ ${f.filePath}:${f.lineBounds} (${f.packageOrSymbol}): ${f.description}"
                )
            }
        }
    }

    // FastAPI Control Plane & Offline Retry Queue
    fun saveAndProbeBackend(
        baseUrl: String,
        bearerToken: String,
        defaultTargetBranch: String,
        orchestratorMode: String,
        autoRetryOffline: Boolean
    ) {
        viewModelScope.launch {
            repository.saveBackendConfig(
                baseUrl = baseUrl,
                bearerToken = bearerToken,
                defaultTargetBranch = defaultTargetBranch,
                orchestratorMode = orchestratorMode,
                autoRetryOffline = autoRetryOffline
            )
            _healthState.value = BackendHealthState.Probing
            val result = repository.probeFastApiHealth(baseUrl, bearerToken)
            _healthState.value = result.fold(
                onSuccess = { health ->
                    _snackbarEvents.tryEmit("FastAPI online · ${health.workerBackend} ready")
                    BackendHealthState.Online(health)
                },
                onFailure = { err ->
                    val reason = err.localizedMessage ?: "FastAPI unreachable"
                    _snackbarEvents.tryEmit("FastAPI offline ($reason) · Local Gemini + Firestore active")
                    BackendHealthState.Offline(reason)
                }
            )
        }
    }

    fun connectWorkerStream() {
        val config = backendConfig.value
        repository.connectWorkerStream(config.baseUrl, config.bearerToken)
    }

    fun disconnectWorkerStream() {
        repository.disconnectWorkerStream()
    }

    fun retryOfflineQueue() {
        viewModelScope.launch {
            _isRetryingQueue.value = true
            try {
                val (synced, remaining) = repository.retryPendingSyncQueue()
                _snackbarEvents.tryEmit("Retry complete: $synced synced, $remaining remaining.")
            } finally {
                _isRetryingQueue.value = false
            }
        }
    }

    fun purgeCompletedQueueItems() {
        viewModelScope.launch {
            repository.purgeCompletedSyncActions()
            _snackbarEvents.tryEmit("Completed sync records purged.")
        }
    }

    fun deleteQueueItem(actionId: String) {
        viewModelScope.launch {
            repository.deleteSyncAction(actionId)
        }
    }

    fun notifyMessage(message: String) {
        _snackbarEvents.tryEmit(message)
    }

    override fun onCleared() {
        super.onCleared()
        repository.disconnectWorkerStream()
    }

    class Factory(
        private val context: Context,
        private val currentUserId: String,
        private val userDisplayName: String,
        private val userEmail: String?
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val appContext = context.applicationContext
            val databaseId = appContext.getString(R.string.firestore_database_id)
            val firestoreDb = FirebaseFirestore.getInstance(databaseId)
            val db = PatchForgeDatabase.getInstance(appContext)
            val prefs = BackendPreferences(appContext)
            val aiProvider = GeminiAiLogicProvider()
            val thirdPartyClient = ThirdPartyMultimodalClient()
            val firestoreSync = FirestoreSyncRepository(firestoreDb)
            val wsScope = kotlinx.coroutines.CoroutineScope(
                kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO
            )
            val wsClient = WorkerWebSocketClient(wsScope)
            val repo = PatchForgeRepository(
                dao = db.patchForgeDao(),
                preferences = prefs,
                aiProvider = aiProvider,
                thirdPartyClient = thirdPartyClient,
                firestoreSync = firestoreSync,
                wsClient = wsClient
            )
            return PatchForgeViewModel(
                repository = repo,
                currentUserId = currentUserId,
                userDisplayName = userDisplayName,
                userEmail = userEmail
            ) as T
        }
    }
}
