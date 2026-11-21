package com.example.data.ai

import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.ai.GenerativeModel
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.ThinkingLevel
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import com.google.firebase.ai.type.thinkingConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

private const val TAG = "GeminiAiLogicProvider"

class GeminiAiLogicProvider : AiReviewProvider {
    override val providerName: String = "Firebase AI Logic (Native Gemini)"

    // Strict JSON Schema enforcing required review finding fields:
    // file_path, line_bounds, threat_category, severity, description, replacement_snippet
    private val auditResponseSchema: Schema by lazy {
        Schema.obj(
            properties = mapOf(
                "summary" to Schema.string(
                    description = "Concise executive operational summary of detected supply-chain and code risks."
                ),
                "risk_score" to Schema.integer(
                    description = "Composite risk score from 0 (clean) to 100 (critical breach/defect)."
                ),
                "ast_metrics" to Schema.obj(
                    properties = mapOf(
                        "max_nesting_depth" to Schema.integer(description = "Maximum control-flow nesting depth observed."),
                        "cyclomatic_peak" to Schema.integer(description = "Highest cyclomatic complexity score observed."),
                        "hotspots" to Schema.array(
                            items = Schema.string(),
                            description = "Short symbol or dependency hotspot identifiers."
                        )
                    )
                ),
                "findings" to Schema.array(
                    description = "List of actionable security and semantic code review findings.",
                    items = Schema.obj(
                        properties = mapOf(
                            "file_path" to Schema.string(
                                description = "Exact file path where the issue occurs."
                            ),
                            "line_bounds" to Schema.string(
                                description = "Line range formatted as L<start>-L<end> (e.g., L12-L18)."
                            ),
                            "threat_category" to Schema.enumeration(
                                values = listOf(
                                    "TYPOSQUATTING",
                                    "INSTALL_SCRIPT",
                                    "TELEMETRY_EXFIL",
                                    "ABANDONED_PKG",
                                    "CVE_VULNERABILITY",
                                    "AST_COMPLEXITY",
                                    "SEMANTIC_LOGIC",
                                    "LINT_CONFIG"
                                ),
                                description = "Exact threat or code review category."
                            ),
                            "severity" to Schema.enumeration(
                                values = listOf("CRITICAL", "HIGH", "MEDIUM", "LOW"),
                                description = "Operational severity level."
                            ),
                            "description" to Schema.string(
                                description = "Precise technical explanation of the threat, CVE, or AST/semantic bug."
                            ),
                            "original_snippet" to Schema.string(
                                description = "Exact problematic lines extracted verbatim from the input."
                            ),
                            "replacement_snippet" to Schema.string(
                                description = "Clean, drop-in replacement code or manifest lines that fix the finding."
                            ),
                            "engine" to Schema.enumeration(
                                values = listOf("SECURITY_ENGINE", "AGENTIC_REVIEW"),
                                description = "Which dual-engine pipeline produced this finding."
                            ),
                            "package_or_symbol" to Schema.string(
                                description = "Affected dependency name or function/class symbol."
                            ),
                            "cyclomatic_complexity" to Schema.integer(
                                description = "Estimated cyclomatic complexity for the affected block (1 if manifest)."
                            )
                        )
                    )
                )
            )
        )
    }

    private fun buildStructuredAuditModel(modelProfile: AiModelProfile): GenerativeModel {
        val config = generationConfig {
            responseMimeType = "application/json"
            responseSchema = auditResponseSchema
            if (modelProfile.highThinking) {
                // High Thinking mode on gemini-4-carbon: set ThinkingLevel.HIGH and do NOT set maxOutputTokens.
                thinkingConfig = thinkingConfig {
                    thinkingLevel = ThinkingLevel.HIGH
                }
            }
        }
        return Firebase.ai.generativeModel(
            modelName = modelProfile.modelId,
            generationConfig = config,
            systemInstruction = content {
                text(
                    """
                    You are PatchForge's Dual-Engine Analysis Orchestrator combining:
                    1. SECURITY_ENGINE (Socket.dev style): Inspects package.json, requirements.txt, go.mod, and Gradle files for typosquatting, suspicious install scripts, telemetry exfiltration, abandoned packages, insecure transport, and CVEs.
                    2. AGENTIC_REVIEW (Open-Rabbit style): Inspects source code and PR diffs for AST structural complexity, cyclomatic hotspots, race conditions, security flaws, semantic bugs, and config/lint regressions.
                    Every finding MUST include accurate file_path, line_bounds (L<start>-L<end>), threat_category, severity, description, original_snippet, and a drop-in replacement_snippet suitable for generating a unified Git patch.
                    """.trimIndent()
                )
            }
        )
    }

    private fun buildChatModel(
        persona: AgentPersona,
        modelProfile: AiModelProfile,
        auditContext: String?
    ): GenerativeModel {
        val instructionBuilder = StringBuilder(persona.systemInstruction)
        if (!auditContext.isNullOrBlank()) {
            instructionBuilder.append("\n\nActive PatchForge Audit Context:\n")
            instructionBuilder.append(auditContext)
        }

        val config = if (modelProfile.highThinking) {
            generationConfig {
                // High Thinking mode on gemini-4-carbon: set ThinkingLevel.HIGH and do NOT set maxOutputTokens.
                thinkingConfig = thinkingConfig {
                    thinkingLevel = ThinkingLevel.HIGH
                }
            }
        } else {
            generationConfig {
                temperature = 0.35f
            }
        }

        return Firebase.ai.generativeModel(
            modelName = modelProfile.modelId,
            generationConfig = config,
            systemInstruction = content { text(instructionBuilder.toString()) }
        )
    }

    override suspend fun runStructuredAudit(
        filePath: String,
        payload: String,
        engineMode: AuditEngineMode,
        modelProfile: AiModelProfile
    ): StructuredAuditResult {
        val engineDirective = when (engineMode) {
            AuditEngineMode.DUAL_ENGINE ->
                "Run BOTH SECURITY_ENGINE (supply-chain/manifest audit) and AGENTIC_REVIEW (AST/semantic PR review) checks."
            AuditEngineMode.SECURITY_ONLY ->
                "Focus exclusively on SECURITY_ENGINE supply-chain risks (typosquatting, install scripts, telemetry exfiltration, abandoned packages, CVEs)."
            AuditEngineMode.AGENTIC_ONLY ->
                "Focus exclusively on AGENTIC_REVIEW AST structural complexity, semantic logic defects, concurrency hazards, and lint/config self-healing."
        }

        val numberedPayload = payload.lines()
            .mapIndexed { idx, line -> "${idx + 1}: $line" }
            .joinToString("\n")

        val prompt = """
            Target File Path: $filePath
            Engine Mode: ${engineMode.id}
            Directive: $engineDirective

            Analyze the following numbered payload and return strictly structured JSON findings with concrete drop-in replacement_snippet fixes.
            Payload:
            $numberedPayload
        """.trimIndent()

        val timeoutMs = if (modelProfile.highThinking) 90_000L else 60_000L

        try {
            val model = buildStructuredAuditModel(modelProfile)
            val response = withTimeout(timeoutMs) {
                model.generateContent(prompt)
            }
            val rawJson = response.text
                ?: throw IllegalStateException("Gemini returned an empty structured audit payload.")
            return parseStructuredAuditJson(rawJson, fallbackFilePath = filePath)
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Structured audit timed out (${timeoutMs}ms)", e)
            throw IllegalStateException(
                "Gemini inference timed out after ${timeoutMs / 1000}s. Retry or switch to Rapid Triage.",
                e
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Structured audit failed on ${modelProfile.modelId}", e)
            throw e
        }
    }

    override fun streamChatReply(
        history: List<ChatTurn>,
        userMessage: String,
        persona: AgentPersona,
        modelProfile: AiModelProfile,
        auditContext: String?
    ): Flow<String> = flow {
        val timeoutMs = if (modelProfile.highThinking) 90_000L else 60_000L
        val model = buildChatModel(persona, modelProfile, auditContext)
        val sdkHistory = history.map { turn ->
            content(role = turn.role) { text(turn.content) }
        }
        val chat = model.startChat(history = sdkHistory)

        try {
            withTimeout(timeoutMs) {
                chat.sendMessageStream(userMessage).collect { chunk ->
                    val delta = chunk.text.orEmpty()
                    if (delta.isNotEmpty()) {
                        emit(delta)
                    }
                }
            }
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Chat stream timed out (${timeoutMs}ms)", e)
            throw IllegalStateException("Agent stream timed out after ${timeoutMs / 1000}s.", e)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Chat stream failed on ${modelProfile.modelId}", e)
            throw e
        }
    }

    // Direct JSON parsing without regex or markdown fence stripping (responseSchema guarantees raw JSON).
    private fun parseStructuredAuditJson(
        jsonString: String,
        fallbackFilePath: String
    ): StructuredAuditResult {
        val root = JSONObject(jsonString)
        val summary = root.optString("summary", "Audit completed.")
        val riskScore = root.optInt("risk_score", 0).coerceIn(0, 100)

        val astObj = root.optJSONObject("ast_metrics")
        val hotspotsJson = astObj?.optJSONArray("hotspots")
        val hotspots = buildList {
            if (hotspotsJson != null) {
                for (i in 0 until hotspotsJson.length()) {
                    add(hotspotsJson.optString(i))
                }
            }
        }
        val astMetrics = AstMetrics(
            maxNestingDepth = astObj?.optInt("max_nesting_depth", 0) ?: 0,
            cyclomaticPeak = astObj?.optInt("cyclomatic_peak", 1) ?: 1,
            hotspots = hotspots.filter { it.isNotBlank() }
        )

        val findingsArray = root.optJSONArray("findings")
        val findings = buildList {
            if (findingsArray != null) {
                for (i in 0 until findingsArray.length()) {
                    val item = findingsArray.getJSONObject(i)
                    add(
                        StructuredFinding(
                            filePath = item.optString("file_path", fallbackFilePath).ifBlank { fallbackFilePath },
                            lineBounds = item.optString("line_bounds", "L1-L1").ifBlank { "L1-L1" },
                            threatCategory = item.optString("threat_category", "SEMANTIC_LOGIC"),
                            severity = item.optString("severity", "MEDIUM").uppercase(),
                            description = item.optString("description", ""),
                            replacementSnippet = item.optString("replacement_snippet", ""),
                            originalSnippet = item.optString("original_snippet", ""),
                            engineType = item.optString("engine", "AGENTIC_REVIEW"),
                            packageOrSymbol = item.optString("package_or_symbol", ""),
                            cyclomaticComplexity = item.optInt("cyclomatic_complexity", 1).coerceAtLeast(1)
                        )
                    )
                }
            }
        }

        return StructuredAuditResult(
            summary = summary,
            riskScore = riskScore,
            astMetrics = astMetrics,
            findings = findings
        )
    }
}
