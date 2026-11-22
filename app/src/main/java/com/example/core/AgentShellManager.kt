package com.example.core

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Three global agentic permission modes governing shell command & repository action execution.
 */
enum class AgentPermissionMode(
    val id: String,
    val label: String,
    val shortLabel: String,
    val summary: String
) {
    AUTONOMOUS(
        id = "AUTONOMOUS",
        label = "Autonomous Control",
        shortLabel = "AUTONOMOUS",
        summary = "Auto-runs read and workspace commands. Destructive commands and remote repo pushes still require explicit safety confirmation."
    ),
    ASK_WHEN_NEEDED(
        id = "ASK_WHEN_NEEDED",
        label = "Ask When Needed",
        shortLabel = "ASK WHEN NEEDED",
        summary = "Auto-runs safe read-only inspection commands. Requires operator approval before file writes, installs, or destructive actions."
    ),
    ALWAYS_ASK(
        id = "ALWAYS_ASK",
        label = "Always Ask Before Acting",
        shortLabel = "ALWAYS ASK",
        summary = "Requires explicit operator approval before every shell command, file modification, or repository action."
    );

    companion object {
        fun fromId(id: String?): AgentPermissionMode =
            entries.firstOrNull { it.id == id } ?: ASK_WHEN_NEEDED
    }
}

enum class ShellEnvironmentType(
    val id: String,
    val label: String,
    val description: String
) {
    ANDROID_SANDBOX(
        id = "ANDROID_SANDBOX",
        label = "Local Sandbox",
        description = "On-device private workspace shell (no root or external app required)"
    ),
    TERMUX_BRIDGE(
        id = "TERMUX_BRIDGE",
        label = "Termux Bridge",
        description = "Dispatches commands to Termux (com.termux) via RUN_COMMAND intent"
    ),
    UBUNTU_REMOTE(
        id = "UBUNTU_REMOTE",
        label = "Ubuntu Runner",
        description = "Executes commands in remote Ubuntu / FastAPI container (/api/v1/terminal/exec)"
    );

    companion object {
        fun fromId(id: String?): ShellEnvironmentType =
            entries.firstOrNull { it.id == id } ?: ANDROID_SANDBOX
    }
}

enum class CommandRiskLevel(val label: String) {
    READ_ONLY("READ-ONLY"),
    STATE_MUTATING("MUTATING"),
    DESTRUCTIVE("DESTRUCTIVE")
}

enum class PolicyDecision {
    AUTO_EXECUTE,
    REQUIRE_APPROVAL
}

data class PolicyEvaluation(
    val riskLevel: CommandRiskLevel,
    val decision: PolicyDecision,
    val reason: String
)

data class PendingAgentAction(
    val actionId: String = "ACT-${UUID.randomUUID().toString().take(8).uppercase()}",
    val command: String,
    val rationale: String,
    val environment: ShellEnvironmentType,
    val riskLevel: CommandRiskLevel,
    val policyReason: String,
    val createdAt: Long = System.currentTimeMillis()
)

data class TerminalLogEntry(
    val entryId: String = "TERM-${UUID.randomUUID().toString().take(8).uppercase()}",
    val command: String,
    val environment: ShellEnvironmentType,
    val riskLevel: CommandRiskLevel,
    val exitCode: Int,
    val output: String,
    val approvedByOperator: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Evaluates shell commands against the active [AgentPermissionMode].
 * Consequential / destructive commands ALWAYS require explicit approval even in AUTONOMOUS mode.
 */
object AgentCommandPolicyEngine {

    private val destructivePatterns = listOf(
        Regex("""\brm\s+-[a-zA-Z]*r[a-zA-Z]*f|\brm\s+-rf\b|\brm\s+-fr\b""", RegexOption.IGNORE_CASE),
        Regex("""\bmkfs\b|\bdd\s+if=|\bshred\b|\bwipefs\b|\bfdisk\b""", RegexOption.IGNORE_CASE),
        Regex("""\bchmod\s+-R\s+777\b|\bchown\s+-R\s+root\b""", RegexOption.IGNORE_CASE),
        Regex("""\bgit\s+push\b.*(--force|-f)\b|\bgit\s+reset\s+--hard\b""", RegexOption.IGNORE_CASE),
        Regex("""\b(shutdown|reboot|halt|poweroff|killall|pkill\s+-9)\b""", RegexOption.IGNORE_CASE),
        Regex("""\b(curl|wget)\b.*\|\s*(sh|bash|zsh)\b""", RegexOption.IGNORE_CASE),
        Regex(""":\(\)\s*\{\s*:\|:\s*&\s*\};:"""),
        Regex("""\bsudo\b""", RegexOption.IGNORE_CASE)
    )

    private val readOnlyPrefixes = setOf(
        "ls", "pwd", "whoami", "uname", "id", "date", "cat", "head", "tail",
        "wc", "find", "grep", "egrep", "stat", "du", "df", "echo", "which",
        "realpath", "basename", "dirname", "tree"
    )

    private val mutatingTokens = listOf(">", ">>", "&&", "||", ";", "|", "$(", "`")

    fun classifyRisk(rawCommand: String): CommandRiskLevel {
        val cmd = rawCommand.trim()
        if (cmd.isEmpty()) return CommandRiskLevel.READ_ONLY

        if (destructivePatterns.any { it.containsMatchIn(cmd) }) {
            return CommandRiskLevel.DESTRUCTIVE
        }

        // Subshell chaining or output redirection is treated as state-mutating at minimum
        if (mutatingTokens.any { cmd.contains(it) }) {
            return CommandRiskLevel.STATE_MUTATING
        }

        val parts = cmd.split(Regex("\\s+"))
        val head = parts.firstOrNull()?.lowercase().orEmpty()
        if (head == "git") {
            val sub = parts.getOrNull(1)?.lowercase().orEmpty()
            return when (sub) {
                "status", "log", "diff", "branch", "show", "rev-parse", "remote" -> CommandRiskLevel.READ_ONLY
                else -> CommandRiskLevel.STATE_MUTATING
            }
        }

        if (head in readOnlyPrefixes) {
            return CommandRiskLevel.READ_ONLY
        }

        return CommandRiskLevel.STATE_MUTATING
    }

    fun evaluate(command: String, mode: AgentPermissionMode): PolicyEvaluation {
        val risk = classifyRisk(command)
        return when {
            risk == CommandRiskLevel.DESTRUCTIVE -> PolicyEvaluation(
                riskLevel = risk,
                decision = PolicyDecision.REQUIRE_APPROVAL,
                reason = "Destructive or high-impact command detected. Explicit operator confirmation is mandatory in all modes."
            )
            mode == AgentPermissionMode.ALWAYS_ASK -> PolicyEvaluation(
                riskLevel = risk,
                decision = PolicyDecision.REQUIRE_APPROVAL,
                reason = "Global mode is ALWAYS ASK BEFORE ACTING."
            )
            mode == AgentPermissionMode.ASK_WHEN_NEEDED && risk != CommandRiskLevel.READ_ONLY -> PolicyEvaluation(
                riskLevel = risk,
                decision = PolicyDecision.REQUIRE_APPROVAL,
                reason = "State-mutating command requires approval in ASK WHEN NEEDED mode."
            )
            else -> PolicyEvaluation(
                riskLevel = risk,
                decision = PolicyDecision.AUTO_EXECUTE,
                reason = "Authorized under ${mode.label} (${risk.label})."
            )
        }
    }
}

/**
 * Multi-environment command execution bridge supporting:
 * 1. Private Android Sandbox (`filesDir/patchforge_workspace`) with sanitized env & timeout.
 * 2. Termux Intent Bridge (`com.termux.RUN_COMMAND`) when Termux is installed.
 * 3. Remote Ubuntu / FastAPI Runner (`POST /api/v1/terminal/exec`).
 */
class AgentShellManager(private val context: Context) {

    private val workspaceDir: File by lazy {
        File(context.filesDir, "patchforge_workspace").apply {
            if (!exists()) mkdirs()
        }
    }

    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    fun isTermuxInstalled(): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(
                    TERMUX_PACKAGE,
                    PackageManager.PackageInfoFlags.of(0)
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(TERMUX_PACKAGE, 0)
            }
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        } catch (_: Exception) {
            false
        }
    }

    fun getWorkspacePath(): String = workspaceDir.absolutePath

    suspend fun writeWorkspaceFile(relativePath: String, content: String): Result<File> =
        withContext(Dispatchers.IO) {
            try {
                val cleanRel = relativePath.trim().removePrefix("/")
                if (cleanRel.isEmpty() || cleanRel.contains("..")) {
                    return@withContext Result.failure(IllegalArgumentException("Invalid workspace path."))
                }
                val target = File(workspaceDir, cleanRel).canonicalFile
                val baseCanonical = workspaceDir.canonicalFile
                val basePrefix = baseCanonical.path + File.separator
                if (!target.path.startsWith(basePrefix)) {
                    return@withContext Result.failure(SecurityException("Path escapes sandbox workspace."))
                }
                target.parentFile?.mkdirs()
                target.writeText(content)
                Result.success(target)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun execute(
        command: String,
        environment: ShellEnvironmentType,
        fastApiBaseUrl: String,
        fastApiBearerToken: String,
        approvedByOperator: Boolean
    ): TerminalLogEntry = withContext(Dispatchers.IO) {
        val risk = AgentCommandPolicyEngine.classifyRisk(command)
        when (environment) {
            ShellEnvironmentType.ANDROID_SANDBOX -> runInAndroidSandbox(command, risk, approvedByOperator)
            ShellEnvironmentType.TERMUX_BRIDGE -> dispatchToTermux(command, risk, approvedByOperator)
            ShellEnvironmentType.UBUNTU_REMOTE -> runInRemoteUbuntu(
                command = command,
                risk = risk,
                baseUrl = fastApiBaseUrl,
                bearerToken = fastApiBearerToken,
                approvedByOperator = approvedByOperator
            )
        }
    }

    private fun runInAndroidSandbox(
        command: String,
        risk: CommandRiskLevel,
        approvedByOperator: Boolean
    ): TerminalLogEntry {
        return try {
            val pb = ProcessBuilder("/system/bin/sh", "-c", command)
                .directory(workspaceDir)
                .redirectErrorStream(true)
            // Sanitize child process environment so app tokens/secrets are never exposed
            val env = pb.environment()
            env["HOME"] = workspaceDir.absolutePath
            env["PATCHFORGE_WORKSPACE"] = workspaceDir.absolutePath

            val process = pb.start()
            val finished = process.waitFor(COMMAND_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            if (!finished) {
                process.destroyForcibly()
                return TerminalLogEntry(
                    command = command,
                    environment = ShellEnvironmentType.ANDROID_SANDBOX,
                    riskLevel = risk,
                    exitCode = 124,
                    output = "[TIMEOUT] Command exceeded ${COMMAND_TIMEOUT_SECONDS}s sandbox limit and was terminated.",
                    approvedByOperator = approvedByOperator
                )
            }
            val rawOutput = process.inputStream.bufferedReader().use { it.readText() }
            val truncated = if (rawOutput.length > MAX_OUTPUT_CHARS) {
                rawOutput.take(MAX_OUTPUT_CHARS) + "\n… [output truncated at ${MAX_OUTPUT_CHARS} chars]"
            } else {
                rawOutput.ifBlank { "(exit ${process.exitValue()}, no output)" }
            }
            TerminalLogEntry(
                command = command,
                environment = ShellEnvironmentType.ANDROID_SANDBOX,
                riskLevel = risk,
                exitCode = process.exitValue(),
                output = truncated,
                approvedByOperator = approvedByOperator
            )
        } catch (e: Exception) {
            TerminalLogEntry(
                command = command,
                environment = ShellEnvironmentType.ANDROID_SANDBOX,
                riskLevel = risk,
                exitCode = 1,
                output = "[SANDBOX ERROR] ${e.localizedMessage ?: "Failed to spawn shell process."}",
                approvedByOperator = approvedByOperator
            )
        }
    }

    private fun dispatchToTermux(
        command: String,
        risk: CommandRiskLevel,
        approvedByOperator: Boolean
    ): TerminalLogEntry {
        if (!isTermuxInstalled()) {
            return TerminalLogEntry(
                command = command,
                environment = ShellEnvironmentType.TERMUX_BRIDGE,
                riskLevel = risk,
                exitCode = 127,
                output = "Termux ($TERMUX_PACKAGE) is not installed on this device.\n" +
                    "Setup steps:\n" +
                    "1. Install Termux from F-Droid or GitHub Releases.\n" +
                    "2. Enable external commands in ~/.termux/termux.properties: allow-external-apps=true\n" +
                    "3. Optional Ubuntu rootfs inside Termux: pkg install proot-distro && proot-distro install ubuntu\n" +
                    "Alternatively, switch to 'Local Sandbox' or 'Ubuntu Runner' above.",
                approvedByOperator = approvedByOperator
            )
        }

        return try {
            val intent = Intent().apply {
                setClassName(TERMUX_PACKAGE, TERMUX_RUN_COMMAND_SERVICE)
                action = ACTION_RUN_COMMAND
                putExtra("com.termux.RUN_COMMAND_PATH", "/data/data/com.termux/files/usr/bin/bash")
                putExtra("com.termux.RUN_COMMAND_ARGUMENTS", arrayOf("-lc", command))
                putExtra("com.termux.RUN_COMMAND_WORKDIR", "/data/data/com.termux/files/home")
                putExtra("com.termux.RUN_COMMAND_BACKGROUND", false)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
            TerminalLogEntry(
                command = command,
                environment = ShellEnvironmentType.TERMUX_BRIDGE,
                riskLevel = risk,
                exitCode = 0,
                output = "[TERMUX DISPATCHED] Sent RUN_COMMAND intent to Termux bash session. Inspect Termux terminal window for interactive output.",
                approvedByOperator = approvedByOperator
            )
        } catch (e: SecurityException) {
            TerminalLogEntry(
                command = command,
                environment = ShellEnvironmentType.TERMUX_BRIDGE,
                riskLevel = risk,
                exitCode = 126,
                output = "[TERMUX PERMISSION REQUIRED] Enable 'allow-external-apps=true' in ~/.termux/termux.properties and grant com.termux.permission.RUN_COMMAND in Android App Settings.",
                approvedByOperator = approvedByOperator
            )
        } catch (e: Exception) {
            TerminalLogEntry(
                command = command,
                environment = ShellEnvironmentType.TERMUX_BRIDGE,
                riskLevel = risk,
                exitCode = 1,
                output = "[TERMUX BRIDGE ERROR] ${e.localizedMessage ?: "Unable to dispatch command to Termux."}",
                approvedByOperator = approvedByOperator
            )
        }
    }

    private fun runInRemoteUbuntu(
        command: String,
        risk: CommandRiskLevel,
        baseUrl: String,
        bearerToken: String,
        approvedByOperator: Boolean
    ): TerminalLogEntry {
        val cleanBase = baseUrl.trim().trimEnd('/')
        if (!cleanBase.startsWith("http://") && !cleanBase.startsWith("https://")) {
            return TerminalLogEntry(
                command = command,
                environment = ShellEnvironmentType.UBUNTU_REMOTE,
                riskLevel = risk,
                exitCode = 1,
                output = "[UBUNTU RUNNER UNCONFIGURED] Configure a valid FastAPI/Ubuntu runner URL in Settings -> Backend & Sync.",
                approvedByOperator = approvedByOperator
            )
        }
        return try {
            val payload = JSONObject().apply {
                put("command", command)
                put("shell", "/bin/bash")
                put("timeout_seconds", COMMAND_TIMEOUT_SECONDS)
            }
            val reqBuilder = Request.Builder()
                .url("$cleanBase/api/v1/terminal/exec")
                .post(payload.toString().toRequestBody(jsonMediaType))
            if (bearerToken.isNotBlank()) {
                reqBuilder.header("Authorization", "Bearer ${bearerToken.trim()}")
            }
            httpClient.newCall(reqBuilder.build()).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    return TerminalLogEntry(
                        command = command,
                        environment = ShellEnvironmentType.UBUNTU_REMOTE,
                        riskLevel = risk,
                        exitCode = resp.code,
                        output = "[UBUNTU RUNNER HTTP ${resp.code}] ${body.take(600).ifBlank { "Endpoint unavailable" }}",
                        approvedByOperator = approvedByOperator
                    )
                }
                val json = JSONObject(body)
                val exitCode = json.optInt("exit_code", 0)
                val stdout = json.optString("stdout", "")
                val stderr = json.optString("stderr", "")
                val combined = buildString {
                    if (stdout.isNotBlank()) appendLine(stdout.trimEnd())
                    if (stderr.isNotBlank()) appendLine("[stderr]\n${stderr.trimEnd()}")
                }.ifBlank { "(exit $exitCode, no output)" }.take(MAX_OUTPUT_CHARS)

                TerminalLogEntry(
                    command = command,
                    environment = ShellEnvironmentType.UBUNTU_REMOTE,
                    riskLevel = risk,
                    exitCode = exitCode,
                    output = combined,
                    approvedByOperator = approvedByOperator
                )
            }
        } catch (e: Exception) {
            TerminalLogEntry(
                command = command,
                environment = ShellEnvironmentType.UBUNTU_REMOTE,
                riskLevel = risk,
                exitCode = 1,
                output = "[UBUNTU RUNNER OFFLINE] ${e.localizedMessage ?: "Connection failed"}. Switch to Local Sandbox or verify FastAPI Ubuntu runner.",
                approvedByOperator = approvedByOperator
            )
        }
    }

    companion object {
        private const val TERMUX_PACKAGE = "com.termux"
        private const val TERMUX_RUN_COMMAND_SERVICE = "com.termux.app.RunCommandService"
        private const val ACTION_RUN_COMMAND = "com.termux.RUN_COMMAND"
        private const val COMMAND_TIMEOUT_SECONDS = 15L
        private const val MAX_OUTPUT_CHARS = 32_000
    }
}
