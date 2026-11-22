package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.ai.AiModelProfile
import com.example.data.ai.AuditEngineMode
import com.example.data.local.AuditFinding
import com.example.data.local.AuditRunEntity
import com.example.data.local.PatchStatus
import com.example.data.repository.InspectionTemplates
import com.example.ui.components.EngineBadge
import com.example.ui.components.ForgePanel
import com.example.ui.components.RiskGaugeMeter
import com.example.ui.components.SectionHeaderLabel
import com.example.ui.components.SeverityBadge
import com.example.ui.components.StatusLed
import com.example.ui.theme.ForgeTheme
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.viewmodel.AuditExecutionState
import com.example.ui.viewmodel.BackendHealthState
import com.example.ui.viewmodel.PatchForgeViewModel
import com.example.ui.viewmodel.SearchGroundingState

@Composable
fun ScanWorkspaceScreen(
    viewModel: PatchForgeViewModel,
    onNavigateToPatches: () -> Unit,
    modifier: Modifier = Modifier
) {
    val editor by viewModel.scanEditor.collectAsStateWithLifecycle()
    val auditState by viewModel.auditState.collectAsStateWithLifecycle()
    val searchState by viewModel.searchState.collectAsStateWithLifecycle()
    val runs by viewModel.auditRuns.collectAsStateWithLifecycle()
    val activeRun by viewModel.activeRun.collectAsStateWithLifecycle()
    val findings by viewModel.activeRunFindings.collectAsStateWithLifecycle()
    val healthState by viewModel.healthState.collectAsStateWithLifecycle()
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val forge = ForgeTheme.colors

    var editorExpanded by remember { mutableStateOf(true) }
    var searchQuery by rememberSaveable { mutableStateOf("") }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 840.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Industrial Forged-Steel Header Banner
            item(key = "header_banner") {
                ForgeHeaderBanner(
                    healthState = healthState,
                    activeModelBadge = editor.modelProfile.badge,
                    runCount = runs.size
                )
            }

            // Dual-Engine Input & Configuration Panel
            item(key = "scanner_config") {
                ForgePanel(elevated = false) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { editorExpanded = !editorExpanded }
                            .padding(vertical = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SectionHeaderLabel(
                            title = "Dual-Engine Target Intake",
                            trailingText = if (editorExpanded) "COLLAPSE" else "EXPAND",
                            modifier = Modifier.weight(1f)
                        )
                    }

                    AnimatedVisibility(visible = editorExpanded) {
                        Column(
                            modifier = Modifier.padding(top = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.scan_quick_fixtures).uppercase(),
                                style = MaterialTheme.typography.labelSmall,
                                color = forge.telemetryMuted
                            )
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                InspectionTemplates.all.forEach { template ->
                                    val isCurrent = editor.filePath == template.filePath
                                    FilterChip(
                                        selected = isCurrent,
                                        onClick = { viewModel.loadTemplate(template) },
                                        label = {
                                            Text(
                                                text = template.label,
                                                style = MaterialTheme.typography.labelMedium
                                            )
                                        },
                                        shape = MaterialTheme.shapes.extraSmall,
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = forge.amberContainer,
                                            selectedLabelColor = forge.amber
                                        ),
                                        border = FilterChipDefaults.filterChipBorder(
                                            enabled = true,
                                            selected = isCurrent,
                                            borderColor = forge.borderHairline,
                                            selectedBorderColor = forge.amber
                                        ),
                                        modifier = Modifier.testTag("fixture_${template.id}")
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                AuditEngineMode.entries.forEach { mode ->
                                    val selected = editor.engineMode == mode
                                    FilterChip(
                                        selected = selected,
                                        onClick = { viewModel.selectEngineMode(mode) },
                                        label = {
                                            Text(
                                                text = mode.label,
                                                style = MaterialTheme.typography.labelMedium,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = when (mode) {
                                                    AuditEngineMode.DUAL_ENGINE -> Icons.Default.Memory
                                                    AuditEngineMode.SECURITY_ONLY -> Icons.Default.Security
                                                    AuditEngineMode.AGENTIC_ONLY -> Icons.Default.Terminal
                                                },
                                                contentDescription = null,
                                                modifier = Modifier.size(15.dp)
                                            )
                                        },
                                        shape = MaterialTheme.shapes.extraSmall,
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = forge.tacticalBlueContainer,
                                            selectedLabelColor = forge.tacticalBlue,
                                            selectedLeadingIconColor = forge.tacticalBlue
                                        ),
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("engine_${mode.id.lowercase()}")
                                    )
                                }
                            }

                            OutlinedTextField(
                                value = editor.filePath,
                                onValueChange = viewModel::updateFilePath,
                                label = {
                                    Text(
                                        text = stringResource(R.string.scan_input_file_label),
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                },
                                singleLine = true,
                                textStyle = MaterialTheme.typography.bodyMedium.copy(
                                    fontFamily = JetBrainsMonoFamily
                                ),
                                shape = MaterialTheme.shapes.extraSmall,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = forge.amber,
                                    unfocusedBorderColor = forge.borderHairline,
                                    focusedContainerColor = MaterialTheme.colorScheme.background,
                                    unfocusedContainerColor = MaterialTheme.colorScheme.background
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("scan_file_path_input")
                            )

                            OutlinedTextField(
                                value = editor.payload,
                                onValueChange = viewModel::updatePayload,
                                label = {
                                    Text(
                                        text = stringResource(R.string.scan_input_code_label),
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                },
                                minLines = 5,
                                maxLines = 10,
                                textStyle = MaterialTheme.typography.labelMedium.copy(
                                    fontFamily = JetBrainsMonoFamily,
                                    fontSize = 11.5.sp,
                                    lineHeight = 16.sp
                                ),
                                shape = MaterialTheme.shapes.extraSmall,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = forge.amber,
                                    unfocusedBorderColor = forge.borderHairline,
                                    focusedContainerColor = MaterialTheme.colorScheme.background,
                                    unfocusedContainerColor = MaterialTheme.colorScheme.background
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("scan_payload_input")
                            )

                            // High Thinking Mode Toggle (gemini-4-carbon + ThinkingLevel.HIGH)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(MaterialTheme.shapes.extraSmall)
                                    .background(
                                        if (editor.modelProfile.highThinking) {
                                            forge.amberContainer.copy(alpha = 0.55f)
                                        } else {
                                            MaterialTheme.colorScheme.surfaceVariant
                                        }
                                    )
                                    .border(
                                        1.dp,
                                        if (editor.modelProfile.highThinking) forge.amber else forge.borderHairline,
                                        MaterialTheme.shapes.extraSmall
                                    )
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Psychology,
                                        contentDescription = "High Thinking Mode",
                                        tint = if (editor.modelProfile.highThinking) forge.amber else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            text = stringResource(R.string.scan_high_thinking_label),
                                            style = MaterialTheme.typography.titleSmall,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = if (editor.modelProfile.highThinking) {
                                                "ThinkingLevel.HIGH active · deep supply-chain & AST synthesis"
                                            } else {
                                                "Standard tier (${editor.modelProfile.modelId}) · switch for deep reasoning"
                                            },
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                Switch(
                                    checked = editor.modelProfile.highThinking,
                                    onCheckedChange = viewModel::toggleAuditHighThinking,
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                                        checkedTrackColor = forge.amber
                                    ),
                                    modifier = Modifier.testTag("scan_high_thinking_switch")
                                )
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                AiModelProfile.entries.forEach { profile ->
                                    val selected = editor.modelProfile == profile
                                    FilterChip(
                                        selected = selected,
                                        onClick = { viewModel.selectAuditModel(profile) },
                                        label = {
                                            Text(
                                                text = profile.badge,
                                                style = MaterialTheme.typography.labelSmall
                                            )
                                        },
                                        shape = MaterialTheme.shapes.extraSmall,
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = if (profile.highThinking) forge.amberContainer else forge.tacticalBlueContainer,
                                            selectedLabelColor = if (profile.highThinking) forge.amber else forge.tacticalBlue
                                        )
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    val isAnalyzing = auditState is AuditExecutionState.Analyzing
                    Button(
                        onClick = { viewModel.runDualEngineAudit() },
                        enabled = !isAnalyzing,
                        shape = MaterialTheme.shapes.extraSmall,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = forge.amber,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("execute_audit_button")
                    ) {
                        Icon(
                            imageVector = if (isAnalyzing) Icons.Default.Bolt else Icons.Default.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isAnalyzing) {
                                stringResource(R.string.scan_running)
                            } else {
                                "${stringResource(R.string.scan_run_audit).uppercase()} · ${editor.engineMode.label.uppercase()}"
                            },
                            style = MaterialTheme.typography.labelLarge
                        )
                    }

                    when (val state = auditState) {
                        is AuditExecutionState.Analyzing -> {
                            Spacer(modifier = Modifier.height(10.dp))
                            LinearProgressIndicator(
                                modifier = Modifier.fillMaxWidth(),
                                color = forge.amber,
                                trackColor = forge.borderHairline
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Executing Gemini Structured Output (${state.modelLabel})… enforcing AuditFinding JSON schema.",
                                style = MaterialTheme.typography.labelSmall,
                                color = forge.amber
                            )
                        }
                        is AuditExecutionState.Error -> {
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(MaterialTheme.shapes.extraSmall)
                                    .background(forge.criticalRedContainer)
                                    .border(1.dp, forge.criticalRed, MaterialTheme.shapes.extraSmall)
                                    .padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ErrorOutline,
                                    contentDescription = "Audit Error",
                                    tint = forge.criticalRed,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = state.message,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                        else -> Unit
                    }
                }
            }

            // Live Google Search Grounding (gemini-4-hydrogen + Tool.googleSearch())
            item(key = "google_search_grounding") {
                ForgePanel(elevated = false, accentBorder = forge.tacticalBlue.copy(alpha = 0.5f)) {
                    SectionHeaderLabel(
                        title = stringResource(R.string.scan_search_title),
                        trailingText = "GEMINI-4-HYDROGEN · LIVE WEB"
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = {
                                Text(
                                    text = stringResource(R.string.scan_search_placeholder),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            },
                            singleLine = true,
                            shape = MaterialTheme.shapes.extraSmall,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = forge.tacticalBlue,
                                unfocusedBorderColor = forge.borderHairline,
                                focusedContainerColor = MaterialTheme.colorScheme.background,
                                unfocusedContainerColor = MaterialTheme.colorScheme.background
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("search_grounding_input")
                        )

                        Button(
                            onClick = { viewModel.verifyWithGoogleSearch(searchQuery) },
                            enabled = searchQuery.isNotBlank() && searchState !is SearchGroundingState.Searching,
                            shape = MaterialTheme.shapes.extraSmall,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = forge.tacticalBlue,
                                contentColor = MaterialTheme.colorScheme.onSecondary
                            ),
                            contentPadding = PaddingValues(horizontal = 12.dp),
                            modifier = Modifier
                                .height(54.dp)
                                .testTag("search_grounding_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Public,
                                contentDescription = stringResource(R.string.scan_search_action),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = stringResource(R.string.scan_search_action).uppercase(),
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                    }

                    when (val state = searchState) {
                        SearchGroundingState.Idle -> Unit
                        SearchGroundingState.Searching -> {
                            Spacer(modifier = Modifier.height(10.dp))
                            LinearProgressIndicator(
                                modifier = Modifier.fillMaxWidth(),
                                color = forge.tacticalBlue,
                                trackColor = forge.borderHairline
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Grounding against live CVE / registry advisories via Google Search…",
                                style = MaterialTheme.typography.labelSmall,
                                color = forge.tacticalBlue
                            )
                        }
                        is SearchGroundingState.Error -> {
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = state.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = forge.criticalRed
                            )
                        }
                        is SearchGroundingState.Success -> {
                            Spacer(modifier = Modifier.height(10.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(MaterialTheme.shapes.extraSmall)
                                    .background(MaterialTheme.colorScheme.background)
                                    .border(1.dp, forge.borderHairline, MaterialTheme.shapes.extraSmall)
                                    .padding(10.dp)
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(
                                        text = state.result.answer,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    if (state.result.sources.isNotEmpty()) {
                                        HorizontalDivider(color = forge.borderHairline)
                                        Text(
                                            text = "VERIFIED SOURCES (${state.result.sources.size})",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = forge.tacticalBlue
                                        )
                                        state.result.sources.take(5).forEach { source ->
                                            Surface(
                                                onClick = {
                                                    try {
                                                        context.startActivity(
                                                            Intent(Intent.ACTION_VIEW, Uri.parse(source.uri))
                                                        )
                                                    } catch (_: Exception) {
                                                        clipboard.setText(AnnotatedString(source.uri))
                                                        viewModel.notifyMessage("Copied source URL to clipboard.")
                                                    }
                                                },
                                                shape = MaterialTheme.shapes.extraSmall,
                                                color = MaterialTheme.colorScheme.surface,
                                                border = BorderStroke(1.dp, forge.borderHairline),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Column(modifier = Modifier.weight(1f)) {
                                                        Text(
                                                            text = source.title,
                                                            style = MaterialTheme.typography.labelMedium,
                                                            color = forge.tacticalBlue,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                        Text(
                                                            text = source.uri,
                                                            style = MaterialTheme.typography.labelSmall.copy(
                                                                fontFamily = JetBrainsMonoFamily
                                                            ),
                                                            color = forge.telemetryMuted,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                    }
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Icon(
                                                        imageVector = Icons.Default.OpenInNew,
                                                        contentDescription = "Open source link",
                                                        tint = forge.tacticalBlue,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Recent Runs Selector
            if (runs.isNotEmpty()) {
                item(key = "run_history_strip") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        runs.take(8).forEach { run ->
                            val selected = activeRun?.runId == run.runId
                            FilterChip(
                                selected = selected,
                                onClick = { viewModel.selectRun(run.runId) },
                                label = {
                                    Text(
                                        text = "${run.runId} · ${run.title} (${run.riskScore})",
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                },
                                shape = MaterialTheme.shapes.extraSmall,
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = forge.tacticalBlueContainer,
                                    selectedLabelColor = forge.tacticalBlue
                                )
                            )
                        }
                    }
                }
            }

            // Active Run Telemetry & AST Summary Plate
            activeRun?.let { run ->
                item(key = "active_run_summary_${run.runId}") {
                    ActiveRunTelemetryCard(
                        run = run,
                        totalFindings = findings.size,
                        onOpenPatches = onNavigateToPatches
                    )
                }

                item(key = "finding_filters") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf("ALL", "CRITICAL", "HIGH", "MEDIUM", "LOW").forEach { sev ->
                            FilterChip(
                                selected = editor.severityFilter == sev,
                                onClick = { viewModel.setSeverityFilter(sev) },
                                label = {
                                    Text(text = sev, style = MaterialTheme.typography.labelSmall)
                                },
                                shape = MaterialTheme.shapes.extraSmall
                            )
                        }
                    }
                }
            }

            // Structured Findings List (or Empty State)
            if (findings.isEmpty()) {
                item(key = "empty_findings") {
                    ForgePanel {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Security,
                                contentDescription = null,
                                tint = forge.tacticalBlue,
                                modifier = Modifier.size(32.dp)
                            )
                            Column {
                                Text(
                                    text = stringResource(R.string.scan_empty_findings_title),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = stringResource(R.string.scan_empty_findings_body),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            } else {
                items(findings, key = { it.id }) { finding ->
                    StructuredFindingCard(
                        finding = finding,
                        onApprove = { viewModel.approveFinding(finding.id) },
                        onVerifyCve = {
                            val target = finding.packageOrSymbol.ifBlank { finding.filePath }
                            val query = "Latest CVE security advisory & safe patched version for $target (${finding.threatCategory})"
                            searchQuery = query
                            viewModel.verifyWithGoogleSearch(query)
                        },
                        onCopyPatch = {
                            val diff = viewModel.buildPatchForFinding(finding)
                            clipboard.setText(AnnotatedString(diff))
                            viewModel.notifyMessage("Copied unified Git patch for ${finding.filePath} (${finding.lineBounds})")
                        },
                        onInspectPatch = onNavigateToPatches
                    )
                }
            }
        }
    }
}

@Composable
private fun ForgeHeaderBanner(
    healthState: BackendHealthState,
    activeModelBadge: String,
    runCount: Int
) {
    val forge = ForgeTheme.colors
    val (ledLabel, ledColor) = when (healthState) {
        is BackendHealthState.Online -> "FASTAPI ONLINE" to forge.verifiedGreen
        is BackendHealthState.Probing -> "PROBING…" to forge.amber
        is BackendHealthState.Offline -> "LOCAL GEMINI" to forge.tacticalBlue
        BackendHealthState.Unchecked -> "GEMINI + CLOUD" to forge.amber
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .border(1.dp, forge.borderStrong, MaterialTheme.shapes.small)
            .height(112.dp)
    ) {
        Image(
            painter = painterResource(id = R.drawable.img_forge_header_1795243659173),
            contentDescription = "PatchForge forged steel surface",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .alpha(0.55f)
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background.copy(alpha = 0.58f))
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "PATCHFORGE",
                        style = MaterialTheme.typography.headlineMedium.copy(letterSpacing = 1.6.sp),
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = stringResource(R.string.scan_header_subtitle),
                        style = MaterialTheme.typography.labelSmall,
                        color = forge.amber
                    )
                }
                StatusLed(label = ledLabel, color = ledColor)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "MODEL // $activeModelBadge",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "AUDITS LOGGED: $runCount",
                    style = MaterialTheme.typography.labelSmall,
                    color = forge.tacticalBlue
                )
            }
        }
    }
}

@Composable
private fun ActiveRunTelemetryCard(
    run: AuditRunEntity,
    totalFindings: Int,
    onOpenPatches: () -> Unit
) {
    val forge = ForgeTheme.colors
    ForgePanel(elevated = true, accentBorder = forge.borderStrong) {
        SectionHeaderLabel(
            title = "Telemetry · ${run.runId}",
            trailingText = "${run.modelId}${if (run.highThinkingEnabled) " · HIGH THINK" else ""}"
        )
        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            RiskGaugeMeter(riskScore = run.riskScore)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = run.filePath,
                    style = MaterialTheme.typography.labelMedium,
                    color = forge.amber,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = run.summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))
        HorizontalDivider(color = forge.borderHairline)
        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TelemetryStat(label = "FINDINGS", value = "$totalFindings")
            TelemetryStat(label = "CYCLOMATIC", value = "${run.cyclomaticPeak}")
            TelemetryStat(label = "MAX NESTING", value = "${run.maxNestingDepth}")
            OutlinedButton(
                onClick = onOpenPatches,
                shape = MaterialTheme.shapes.extraSmall,
                border = BorderStroke(1.dp, forge.amber),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                modifier = Modifier.testTag("jump_to_patches_button")
            ) {
                Text(
                    text = "PATCHES",
                    style = MaterialTheme.typography.labelSmall,
                    color = forge.amber
                )
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = "Open Patch Workbench",
                    tint = forge.amber,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}

@Composable
private fun TelemetryStat(label: String, value: String) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = ForgeTheme.colors.telemetryMuted
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium.copy(fontFamily = JetBrainsMonoFamily),
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun StructuredFindingCard(
    finding: AuditFinding,
    onApprove: () -> Unit,
    onVerifyCve: () -> Unit,
    onCopyPatch: () -> Unit,
    onInspectPatch: () -> Unit
) {
    val forge = ForgeTheme.colors
    val accentColor = when (finding.severity.uppercase()) {
        "CRITICAL" -> forge.criticalRed
        "HIGH" -> forge.amber
        "MEDIUM" -> forge.tacticalBlue
        else -> forge.verifiedGreen
    }

    ForgePanel(accentBorder = accentColor.copy(alpha = 0.55f)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SeverityBadge(severity = finding.severity)
                EngineBadge(engineType = finding.engineType)
            }
            Text(
                text = finding.threatCategory,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = accentColor
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.extraSmall)
                .background(MaterialTheme.colorScheme.background)
                .border(1.dp, forge.borderHairline, MaterialTheme.shapes.extraSmall)
                .padding(horizontal = 8.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${finding.filePath}:${finding.lineBounds}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (finding.packageOrSymbol.isNotBlank()) {
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = finding.packageOrSymbol,
                    style = MaterialTheme.typography.labelSmall,
                    color = forge.tacticalBlue
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = finding.description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )

        if (finding.replacementSnippet.isNotBlank()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "REPLACEMENT SNIPPET",
                style = MaterialTheme.typography.labelSmall,
                color = forge.verifiedGreen
            )
            Spacer(modifier = Modifier.height(4.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.extraSmall)
                    .background(forge.diffAddedBg)
                    .border(1.dp, forge.verifiedGreen.copy(alpha = 0.45f), MaterialTheme.shapes.extraSmall)
                    .padding(8.dp)
            ) {
                Text(
                    text = finding.replacementSnippet,
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontFamily = JetBrainsMonoFamily,
                        fontSize = 11.sp
                    ),
                    color = forge.diffAddedInk
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val isApproved = finding.patchStatus == PatchStatus.APPROVED || finding.patchStatus == PatchStatus.APPLIED
            OutlinedButton(
                onClick = onApprove,
                enabled = !isApproved,
                shape = MaterialTheme.shapes.extraSmall,
                border = BorderStroke(1.dp, if (isApproved) forge.verifiedGreen else forge.amber),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = if (isApproved) forge.verifiedGreen else forge.amber,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(modifier = Modifier.width(5.dp))
                Text(
                    text = if (isApproved) finding.patchStatus else "APPROVE",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isApproved) forge.verifiedGreen else forge.amber,
                    maxLines = 1
                )
            }

            OutlinedButton(
                onClick = onVerifyCve,
                shape = MaterialTheme.shapes.extraSmall,
                border = BorderStroke(1.dp, forge.tacticalBlue),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Verify CVE with Google Search",
                    tint = forge.tacticalBlue,
                    modifier = Modifier.size(15.dp)
                )
            }

            OutlinedButton(
                onClick = onCopyPatch,
                shape = MaterialTheme.shapes.extraSmall,
                border = BorderStroke(1.dp, forge.borderStrong),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = "Copy Git patch",
                    modifier = Modifier.size(15.dp)
                )
            }

            OutlinedButton(
                onClick = onInspectPatch,
                shape = MaterialTheme.shapes.extraSmall,
                border = BorderStroke(1.dp, forge.borderStrong),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Build,
                    contentDescription = "Open Patch Workbench",
                    tint = forge.amber,
                    modifier = Modifier.size(15.dp)
                )
            }
        }
    }
}
