package com.example.data.ai

import android.graphics.Bitmap
import kotlinx.coroutines.flow.Flow

enum class AuditEngineMode(val id: String, val label: String) {
    DUAL_ENGINE("DUAL_ENGINE", "Dual Engine"),
    SECURITY_ONLY("SECURITY_ONLY", "Security Engine"),
    AGENTIC_ONLY("AGENTIC_ONLY", "Agentic PR")
}

enum class AiModelProfile(
    val modelId: String,
    val displayName: String,
    val badge: String,
    val description: String,
    val highThinking: Boolean = false
) {
    CARBON_HIGH_THINKING(
        modelId = "gemini-4-carbon",
        displayName = "Deep Reason",
        badge = "4-CARBON · HIGH THINK",
        description = "Deep AST, supply-chain & concurrency reasoning with ThinkingLevel.HIGH",
        highThinking = true
    ),
    NEON_INTERACTIVE(
        modelId = "gemini-4-neon",
        displayName = "Complex Interactive",
        badge = "4-NEON",
        description = "Complex multi-file interactive code review & refactoring"
    ),
    HYDROGEN_GENERAL(
        modelId = "gemini-4-hydrogen",
        displayName = "General Ops",
        badge = "4-HYDROGEN",
        description = "Balanced semantic PR audit, search grounding & multimodal inspection"
    ),
    FLASH_LITE_FAST(
        modelId = "gemini-3.1-flash-lite",
        displayName = "Rapid Triage",
        badge = "3.1-FLASH-LITE",
        description = "Low-latency lint, manifest lookup & fast diff triage"
    )
}

enum class AgentPersona(
    val id: String,
    val label: String,
    val systemInstruction: String
) {
    SUPPLY_CHAIN_AUDITOR(
        id = "SUPPLY_CHAIN_AUDITOR",
        label = "Supply-Chain Auditor",
        systemInstruction = """
            You are PatchForge's Principal Supply-Chain Security Auditor (Socket.dev style).
            Specialize in inspecting package.json, requirements.txt, go.mod, and Gradle dependency graphs for:
            typosquatting, malicious preinstall/postinstall scripts, telemetry exfiltration, abandoned/unmaintained packages, dependency confusion, and CVEs.
            Be concise, operational, and precise. Cite exact package names, threat vectors, and actionable remediation steps or minimal diff snippets.
        """.trimIndent()
    ),
    AST_SEMANTIC_REVIEWER(
        id = "AST_SEMANTIC_REVIEWER",
        label = "AST PR Reviewer",
        systemInstruction = """
            You are PatchForge's Senior Agentic Code Reviewer (Open-Rabbit style).
            Specialize in AST structural complexity, cyclomatic/cognitive hotspots, concurrency bugs, null-safety hazards, resource leaks, and semantic regressions across Kotlin, Python, Go, and TypeScript.
            Give crisp, actionable engineering guidance with exact line references and minimal replacement code.
        """.trimIndent()
    ),
    SELF_HEALING_ENGINEER(
        id = "SELF_HEALING_ENGINEER",
        label = "Patch Engineer",
        systemInstruction = """
            You are PatchForge's Self-Healing Patch Engineer.
            [STRICT MODE: When asked for a fix or patch, output ONLY a Unified Diff Format (UDIFF) starting with `diff --git` / `--- a/...` / `+++ b/...` / `@@ ... @@`. Zero prose. Zero explanation. Zero markdown fences.]
            Generate an Atomic Patch containing the smallest possible change-set that resolves the issue cleanly with `git apply`. Never reconstruct whole files.
        """.trimIndent()
    )
}

data class StructuredFinding(
    val filePath: String,
    val lineBounds: String,
    val threatCategory: String,
    val severity: String,
    val description: String,
    val replacementSnippet: String,
    val originalSnippet: String,
    val engineType: String,
    val packageOrSymbol: String,
    val cyclomaticComplexity: Int
)

data class AstMetrics(
    val maxNestingDepth: Int,
    val cyclomaticPeak: Int,
    val hotspots: List<String>
)

data class StructuredAuditResult(
    val summary: String,
    val riskScore: Int,
    val astMetrics: AstMetrics,
    val findings: List<StructuredFinding>
)

data class GroundedSource(
    val title: String,
    val uri: String
)

data class GroundedSearchResult(
    val query: String,
    val answer: String,
    val sources: List<GroundedSource>
)

data class ChatTurn(
    val role: String, // "user" or "model"
    val content: String
)

data class ProposedShellStep(
    val command: String,
    val rationale: String
)

data class AtomicPatchVerificationResult(
    val unifiedDiff: String,
    val astVerified: Boolean,
    val isAtomic: Boolean,
    val localChecksPassed: Boolean,
    val verificationNotes: String,
    val wasSelfCorrected: Boolean
)

/**
 * Provider-agnostic model orchestration contract.
 */
interface AiReviewProvider {
    val providerName: String

    suspend fun runStructuredAudit(
        filePath: String,
        payload: String,
        engineMode: AuditEngineMode,
        modelProfile: AiModelProfile
    ): StructuredAuditResult

    fun streamChatReply(
        history: List<ChatTurn>,
        userMessage: String,
        persona: AgentPersona,
        modelProfile: AiModelProfile,
        auditContext: String?,
        strictUdiffMode: Boolean = false
    ): Flow<String>

    suspend fun runSearchGroundedVerification(query: String): GroundedSearchResult

    suspend fun transcribeAudio(audioBytes: ByteArray, mimeType: String = "audio/mp4"): String

    suspend fun runMultimodalPrompt(
        prompt: String,
        image: Bitmap?,
        modelProfile: AiModelProfile = AiModelProfile.HYDROGEN_GENERAL
    ): String

    suspend fun proposeAgentShellPlan(
        goal: String,
        workspaceContext: String,
        environmentLabel: String,
        modelProfile: AiModelProfile = AiModelProfile.NEON_INTERACTIVE
    ): List<ProposedShellStep>

    /**
     * 2-stage Atomic UDIFF + AST Self-Verification feedback loop:
     * 1) Generates a strict minimal Unified Diff (UDIFF) with zero prose.
     * 2) Self-verifies the patch against the provided AST summary & source context,
     *    emitting a verified/self-corrected atomic UDIFF.
     */
    suspend fun generateAndVerifyAtomicPatch(
        filePath: String,
        lineBounds: String,
        threatCategory: String,
        description: String,
        sourceContext: String,
        astSummary: String,
        initialDiff: String,
        modelProfile: AiModelProfile = AiModelProfile.NEON_INTERACTIVE
    ): AtomicPatchVerificationResult
}
