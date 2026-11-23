package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.AgentPermissionMode
import com.example.core.CommandRiskLevel
import com.example.core.PendingAgentAction
import com.example.core.ShellEnvironmentType
import com.example.core.TerminalLogEntry
import com.example.ui.components.ForgePanel
import com.example.ui.components.StatusLed
import com.example.ui.theme.ForgeTheme
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.viewmodel.PatchForgeViewModel

/**
 * Keyboard-avoidant split TUI for Agent Shell:
 * - Compact collapsible Governance strip at top (auto-folds when IME is visible).
 * - Scrollable Live Output Console + Pending Approvals filling remaining vertical viewport.
 * - Sticky Bottom TUI Prompt bar switching cleanly between `$ CMD` and `AI PLAN`.
 */
@Composable
fun AgentTerminalSection(
    viewModel: PatchForgeViewModel,
    imeVisible: Boolean = false,
    modifier: Modifier = Modifier
) {
    val permissionMode by viewModel.permissionMode.collectAsStateWithLifecycle()
    val shellEnv by viewModel.shellEnvironment.collectAsStateWithLifecycle()
    val pendingActions by viewModel.pendingAgentActions.collectAsStateWithLifecycle()
    val terminalLogs by viewModel.terminalLogs.collectAsStateWithLifecycle()
    val isRunning by viewModel.isRunningShellAction.collectAsStateWithLifecycle()
    val isTermuxInstalled by viewModel.isTermuxInstalled.collectAsStateWithLifecycle()
    val forge = ForgeTheme.colors

    var governanceExpanded by rememberSaveable { mutableStateOf(false) }
    var aiPlannerMode by rememberSaveable { mutableStateOf(false) }
    var promptInput by rememberSaveable { mutableStateOf("") }
    val consoleListState = rememberLazyListState()

    // Auto-fold governance controls when keyboard opens so terminal output stays visible.
    LaunchedEffect(imeVisible) {
        if (imeVisible && governanceExpanded) {
            governanceExpanded = false
        }
    }

    // Keep latest terminal output visible above the input bar.
    LaunchedEffect(terminalLogs.size, pendingActions.size, imeVisible) {
        val total = pendingActions.size + terminalLogs.size
        if (total > 0) {
            consoleListState.animateScrollToItem(total)
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        // 1. Compact Collapsible Policy & Environment Bar
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { governanceExpanded = !governanceExpanded }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        StatusLed(
                            label = permissionMode.shortLabel,
                            color = when (permissionMode) {
                                AgentPermissionMode.AUTONOMOUS -> forge.verifiedGreen
                                AgentPermissionMode.ASK_WHEN_NEEDED -> forge.amber
                                AgentPermissionMode.ALWAYS_ASK -> forge.tacticalBlue
                            }
                        )
                        Text(
                            text = shellEnv.label.uppercase(),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = JetBrainsMonoFamily,
                                fontWeight = FontWeight.Bold
                            ),
                            color = forge.tacticalBlue,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (terminalLogs.isNotEmpty()) {
                            IconButton(
                                onClick = { viewModel.clearTerminalLogs() },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DeleteSweep,
                                    contentDescription = "Clear terminal output",
                                    tint = forge.telemetryMuted,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                        Icon(
                            imageVector = if (governanceExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = "Toggle shell environment settings",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                AnimatedVisibility(
                    visible = governanceExpanded && !imeVisible,
                    enter = expandVertically(),
                    exit = shrinkVertically()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.background.copy(alpha = 0.7f))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        HorizontalDivider(color = forge.borderHairline)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            AgentPermissionMode.entries.forEach { mode ->
                                val selected = permissionMode == mode
                                FilterChip(
                                    selected = selected,
                                    onClick = { viewModel.setPermissionMode(mode) },
                                    leadingIcon = {
                                        Icon(Icons.Default.Security, contentDescription = null, modifier = Modifier.size(14.dp))
                                    },
                                    label = { Text(mode.label, style = MaterialTheme.typography.labelSmall) },
                                    shape = MaterialTheme.shapes.extraSmall,
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = forge.amberContainer,
                                        selectedLabelColor = forge.amber,
                                        selectedLeadingIconColor = forge.amber
                                    ),
                                    modifier = Modifier.testTag("shell_perm_${mode.id.lowercase()}")
                                )
                            }
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            ShellEnvironmentType.entries.forEach { env ->
                                val selected = shellEnv == env
                                FilterChip(
                                    selected = selected,
                                    onClick = { viewModel.setShellEnvironment(env) },
                                    leadingIcon = {
                                        Icon(Icons.Default.Terminal, contentDescription = null, modifier = Modifier.size(14.dp))
                                    },
                                    label = { Text(env.label, style = MaterialTheme.typography.labelSmall) },
                                    shape = MaterialTheme.shapes.extraSmall,
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = forge.tacticalBlueContainer,
                                        selectedLabelColor = forge.tacticalBlue,
                                        selectedLeadingIconColor = forge.tacticalBlue
                                    ),
                                    modifier = Modifier.testTag("shell_env_${env.id.lowercase()}")
                                )
                            }
                        }

                        Text(
                            text = "${permissionMode.summary} · ${shellEnv.description}" +
                                if (shellEnv == ShellEnvironmentType.TERMUX_BRIDGE) {
                                    if (isTermuxInstalled) " [Termux detected]" else " [Install Termux]"
                                } else "",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
        HorizontalDivider(color = forge.borderHairline)

        if (isRunning) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth(),
                color = forge.amber,
                trackColor = forge.borderHairline
            )
        }

        // 2. Always-Visible Scrollable Console & Approval Queue
        LazyColumn(
            state = consoleListState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (pendingActions.isNotEmpty()) {
                items(pendingActions, key = { it.actionId }) { action ->
                    PendingApprovalCard(
                        action = action,
                        onApprove = { viewModel.approvePendingAction(action.actionId) },
                        onDeny = { viewModel.denyPendingAction(action.actionId) }
                    )
                }
            }

            if (terminalLogs.isEmpty()) {
                item(key = "console_ready_banner") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.extraSmall)
                            .background(MaterialTheme.colorScheme.surface)
                            .border(1.dp, forge.borderHairline, MaterialTheme.shapes.extraSmall)
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "// ${shellEnv.label} ready · ${permissionMode.label}",
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
                            color = forge.amber
                        )
                        Text(
                            text = "// cwd: ${viewModel.sandboxWorkspacePath}",
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
                            color = forge.telemetryMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "// Toggle '$' (shell) or 'AI' (planner) below.",
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
                            color = forge.telemetryMuted
                        )
                    }
                }
            } else {
                items(terminalLogs, key = { it.entryId }) { log ->
                    TerminalLogBlock(entry = log)
                }
            }
        }

        // 3. Sticky Bottom TUI Prompt Bar (remains visible above keyboard)
        HorizontalDivider(color = forge.borderStrong)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (!imeVisible) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf("pwd && ls -la", "uname -a", "find . -maxdepth 3", "touch audit_note.txt").forEach { quick ->
                        Surface(
                            onClick = { viewModel.requestShellCommand(quick, "Quick diagnostic command") },
                            shape = MaterialTheme.shapes.extraSmall,
                            color = MaterialTheme.colorScheme.background,
                            border = BorderStroke(1.dp, forge.borderHairline)
                        ) {
                            Text(
                                text = "$ $quick",
                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
                                color = forge.tacticalBlue,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Inline Mode Toggle: Direct `$ CMD` vs `AI PLAN`
                Surface(
                    onClick = { aiPlannerMode = !aiPlannerMode },
                    shape = MaterialTheme.shapes.extraSmall,
                    color = if (aiPlannerMode) forge.tacticalBlueContainer else forge.amberContainer,
                    border = BorderStroke(1.dp, if (aiPlannerMode) forge.tacticalBlue else forge.amber),
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("shell_input_mode_toggle")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = if (aiPlannerMode) Icons.Default.AutoAwesome else Icons.Default.Terminal,
                            contentDescription = "Switch shell prompt mode",
                            tint = if (aiPlannerMode) forge.tacticalBlue else forge.amber,
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = if (aiPlannerMode) "AI" else "$",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = JetBrainsMonoFamily,
                                fontWeight = FontWeight.Bold
                            ),
                            color = if (aiPlannerMode) forge.tacticalBlue else forge.amber
                        )
                    }
                }

                OutlinedTextField(
                    value = promptInput,
                    onValueChange = { promptInput = it },
                    placeholder = {
                        Text(
                            text = if (aiPlannerMode) "Goal for AI shell planner…" else "ls -la",
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFamily),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFamily),
                    shape = MaterialTheme.shapes.extraSmall,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = if (aiPlannerMode) forge.tacticalBlue else forge.amber,
                        unfocusedBorderColor = forge.borderHairline,
                        focusedContainerColor = MaterialTheme.colorScheme.background,
                        unfocusedContainerColor = MaterialTheme.colorScheme.background
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .testTag(if (aiPlannerMode) "agent_shell_goal_input" else "direct_shell_command_input")
                )

                Button(
                    onClick = {
                        if (promptInput.isNotBlank()) {
                            val text = promptInput
                            promptInput = ""
                            if (aiPlannerMode) {
                                viewModel.runAgentShellGoal(text)
                            } else {
                                viewModel.requestShellCommand(text, "Direct operator terminal command")
                            }
                        }
                    },
                    enabled = !isRunning && promptInput.isNotBlank(),
                    shape = MaterialTheme.shapes.extraSmall,
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (aiPlannerMode) forge.tacticalBlue else forge.amber,
                        contentColor = if (aiPlannerMode) MaterialTheme.colorScheme.onSecondary else MaterialTheme.colorScheme.onPrimary
                    ),
                    modifier = Modifier
                        .height(48.dp)
                        .testTag(if (aiPlannerMode) "run_agent_shell_goal_button" else "run_shell_command_button")
                ) {
                    Icon(
                        imageVector = if (aiPlannerMode) Icons.Default.AutoAwesome else Icons.Default.PlayArrow,
                        contentDescription = if (aiPlannerMode) "Plan and execute shell goal" else "Execute shell command",
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun PendingApprovalCard(
    action: PendingAgentAction,
    onApprove: () -> Unit,
    onDeny: () -> Unit
) {
    val forge = ForgeTheme.colors
    val riskColor = when (action.riskLevel) {
        CommandRiskLevel.DESTRUCTIVE -> forge.criticalRed
        CommandRiskLevel.STATE_MUTATING -> forge.amber
        CommandRiskLevel.READ_ONLY -> forge.tacticalBlue
    }
    ForgePanel(elevated = true, accentBorder = riskColor, contentPadding = 10.dp) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = riskColor,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "APPROVAL · ${action.riskLevel.label}",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = riskColor
                )
            }
            Text(
                text = action.environment.label.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = forge.telemetryMuted
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "$ ${action.command}",
            style = MaterialTheme.typography.labelMedium.copy(fontFamily = JetBrainsMonoFamily),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.extraSmall)
                .background(MaterialTheme.colorScheme.background)
                .border(1.dp, forge.borderHairline, MaterialTheme.shapes.extraSmall)
                .padding(8.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "${action.rationale} — ${action.policyReason}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = onApprove,
                shape = MaterialTheme.shapes.extraSmall,
                colors = ButtonDefaults.buttonColors(
                    containerColor = forge.verifiedGreen,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
                    .testTag("approve_action_${action.actionId}")
            ) {
                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(15.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("APPROVE", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
            }
            OutlinedButton(
                onClick = onDeny,
                shape = MaterialTheme.shapes.extraSmall,
                border = BorderStroke(1.dp, forge.criticalRed),
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
                    .testTag("deny_action_${action.actionId}")
            ) {
                Icon(Icons.Default.Close, contentDescription = null, tint = forge.criticalRed, modifier = Modifier.size(15.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("DENY", style = MaterialTheme.typography.labelSmall, color = forge.criticalRed)
            }
        }
    }
}

@Composable
private fun TerminalLogBlock(entry: TerminalLogEntry) {
    val forge = ForgeTheme.colors
    val statusColor = if (entry.exitCode == 0) forge.verifiedGreen else forge.criticalRed
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.extraSmall)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, forge.borderHairline, MaterialTheme.shapes.extraSmall)
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "$ ${entry.command}",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = JetBrainsMonoFamily,
                    fontWeight = FontWeight.Bold
                ),
                color = forge.amber,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "EXIT ${entry.exitCode}${if (entry.approvedByOperator) " · OK" else " · AUTO"}",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                color = statusColor
            )
        }
        Text(
            text = entry.output,
            style = MaterialTheme.typography.labelSmall.copy(
                fontFamily = JetBrainsMonoFamily,
                fontSize = 10.5.sp,
                lineHeight = 14.sp
            ),
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
