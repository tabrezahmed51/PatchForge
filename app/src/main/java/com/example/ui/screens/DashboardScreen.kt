package com.example.ui.screens

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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.core.AgentPermissionMode
import com.example.data.local.AuditFinding
import com.example.data.local.GitForgeProvider
import com.example.data.local.PatchStatus
import com.example.data.local.TokenClassifier
import com.example.ui.components.ForgeDynamicHeader
import com.example.ui.components.HudCornerFrame
import com.example.ui.components.PostureReactorGauge
import com.example.ui.components.SectionHeaderLabel
import com.example.ui.components.SegmentedSeverityBar
import com.example.ui.components.StatusLed
import com.example.ui.components.SubsystemReadinessStrip
import com.example.ui.components.TacticalCommandTile
import com.example.ui.components.TacticalGridBackdrop
import com.example.ui.components.TacticalPermissionDeck
import com.example.ui.components.TelemetrySparkWave
import com.example.ui.components.VulnerabilityTrend30DayWidget
import com.example.ui.components.bringIntoViewOnImeFocus
import com.example.ui.theme.ForgeTheme
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.viewmodel.PatchForgeViewModel
import com.example.ui.viewmodel.RepoInspectionState

@Composable
fun DashboardScreen(
    viewModel: PatchForgeViewModel,
    userDisplayName: String,
    onNavigateToScan: () -> Unit,
    onNavigateToPatches: () -> Unit,
    onNavigateToAgent: () -> Unit,
    modifier: Modifier = Modifier
) {
    val runs by viewModel.auditRuns.collectAsStateWithLifecycle()
    val findings by viewModel.allFindings.collectAsStateWithLifecycle()
    val permissionMode by viewModel.permissionMode.collectAsStateWithLifecycle()
    val pendingApprovals by viewModel.pendingAgentActions.collectAsStateWithLifecycle()
    val tokenStatuses by viewModel.forgeTokenStatuses.collectAsStateWithLifecycle()
    val repoState by viewModel.repoInspectionState.collectAsStateWithLifecycle()
    val isTermuxInstalled by viewModel.isTermuxInstalled.collectAsStateWithLifecycle()
    val forge = ForgeTheme.colors

    var selectedProviderId by rememberSaveable { mutableStateOf(GitForgeProvider.GITHUB.id) }
    var repoInput by rememberSaveable { mutableStateOf("") }
    var branchInput by rememberSaveable { mutableStateOf("") }
    var tokenInput by rememberSaveable { mutableStateOf("") }
    var showToken by rememberSaveable { mutableStateOf(false) }

    val selectedProvider = GitForgeProvider.entries.firstOrNull { it.id == selectedProviderId }
        ?: GitForgeProvider.GITHUB
    val currentTokenStatus = tokenStatuses[selectedProvider]
    val liveDetectedKind = TokenClassifier.classify(selectedProvider, tokenInput)

    val criticalCount = findings.count { it.severity.equals("CRITICAL", ignoreCase = true) }
    val highCount = findings.count { it.severity.equals("HIGH", ignoreCase = true) }
    val mediumCount = findings.count { it.severity.equals("MEDIUM", ignoreCase = true) }
    val lowCount = (findings.size - criticalCount - highCount - mediumCount).coerceAtLeast(0)
    val pendingPatches = findings.count {
        it.patchStatus == PatchStatus.PENDING || it.patchStatus == PatchStatus.APPROVED
    }
    val configuredVaultCount = tokenStatuses.values.count { it.isConfigured }

    // Security Posture Integrity score (100 = clean; penalized by unresolved findings)
    val unresolvedFindings = findings.filter { it.patchStatus != PatchStatus.APPLIED }
    val penalty = unresolvedFindings.sumOf {
        when (it.severity.uppercase()) {
            "CRITICAL" -> 26
            "HIGH" -> 14
            "MEDIUM" -> 6
            else -> 2
        }
    }
    val integrityScore = (100 - penalty).coerceIn(8, 100)
    val (postureLabel, postureColor) = when {
        criticalCount > 0 && unresolvedFindings.any { it.severity.equals("CRITICAL", true) } ->
            "CRITICAL" to forge.criticalRed
        integrityScore < 65 -> "ELEVATED" to forge.amber
        integrityScore < 90 -> "GUARDED" to forge.tacticalBlue
        else -> "NOMINAL" to forge.verifiedGreen
    }

    val permColor = when (permissionMode) {
        AgentPermissionMode.AUTONOMOUS -> forge.verifiedGreen
        AgentPermissionMode.ASK_WHEN_NEEDED -> forge.amber
        AgentPermissionMode.ALWAYS_ASK -> forge.tacticalBlue
    }
    val riskSeries = runs.reversed().map { it.riskScore }

    val listState = rememberLazyListState()
    val focusManager = LocalFocusManager.current

    TacticalGridBackdrop(
        modifier = modifier.fillMaxSize(),
        accentGlowAlpha = 0.11f
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.TopCenter
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 840.dp)
                    .imePadding(),
                contentPadding = PaddingValues(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 1. Compact Tactical HUD Header
                item(key = "dashboard_hero") {
                    ForgeDynamicHeader(
                        title = stringResource(R.string.dashboard_title),
                        compactSubtitle = stringResource(R.string.dashboard_subtitle),
                        statusLabel = permissionMode.shortLabel,
                        statusColor = permColor,
                        infoDetail = stringResource(R.string.dashboard_info_detail),
                        metadataChips = listOf(
                            "OPERATOR" to userDisplayName.uppercase(),
                            "RUNS" to "${runs.size}",
                            "GATE" to if (pendingApprovals.isNotEmpty()) "${pendingApprovals.size} PENDING" else "READY"
                        ),
                        embossedTitle = true,
                        modifier = Modifier.testTag("dashboard_dynamic_header")
                    )
                }

                // 2. Live Subsystem Readiness Telemetry Strip
                item(key = "dashboard_subsystem_strip") {
                    SubsystemReadinessStrip(
                        items = listOf(
                            Triple(
                                "VAULT",
                                "$configuredVaultCount/4 ARMED",
                                if (configuredVaultCount > 0) forge.verifiedGreen else forge.amber
                            ),
                            Triple("CLOUD", "SYNCED", forge.tacticalBlue),
                            Triple("GATE", permissionMode.shortLabel, permColor),
                            Triple(
                                "SHELL",
                                if (isTermuxInstalled) "TERMUX" else "SANDBOX",
                                if (isTermuxInstalled) forge.verifiedGreen else forge.tacticalBlue
                            )
                        )
                    )
                }

                // 3. HERO WIDGET: Security Posture Integrity Reactor + Telemetry Grid + Risk Waveform
                item(key = "dashboard_metrics") {
                    HudCornerFrame(
                        cornerAccent = postureColor,
                        borderColor = forge.borderStrong,
                        elevated = true
                    ) {
                        SectionHeaderLabel(
                            title = "Security Posture Reactor",
                            trailingText = "${runs.size} RUNS · ${findings.size} FINDINGS"
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            PostureReactorGauge(
                                integrityScore = integrityScore,
                                statusLabel = postureLabel,
                                accentColor = postureColor,
                                diameter = 112.dp
                            )

                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    HudTelemetryTile(
                                        label = "CRITICAL",
                                        value = "$criticalCount",
                                        accent = if (criticalCount > 0) forge.criticalRed else forge.verifiedGreen,
                                        modifier = Modifier.weight(1f)
                                    )
                                    HudTelemetryTile(
                                        label = "HIGH",
                                        value = "$highCount",
                                        accent = if (highCount > 0) forge.amber else forge.tacticalBlue,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    HudTelemetryTile(
                                        label = "PATCHES",
                                        value = "$pendingPatches",
                                        accent = forge.tacticalBlue,
                                        modifier = Modifier.weight(1f)
                                    )
                                    HudTelemetryTile(
                                        label = "GATES",
                                        value = "${pendingApprovals.size}",
                                        accent = if (pendingApprovals.isNotEmpty()) forge.amber else forge.telemetryMuted,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        SegmentedSeverityBar(
                            critical = criticalCount,
                            high = highCount,
                            medium = mediumCount,
                            low = lowCount
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        TelemetrySparkWave(riskScores = riskSeries)
                    }
                }

                // 3b. 30-Day Security Vulnerability Trend Visualization
                item(key = "dashboard_vulnerability_trend_30d") {
                    VulnerabilityTrend30DayWidget(
                        runs = runs,
                        findings = findings,
                        onNavigateToScan = onNavigateToScan
                    )
                }

                // 4. Futuristic Touch Command Launchpad Grid
                item(key = "dashboard_command_launchpad") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TacticalCommandTile(
                            title = "SCANNER",
                            telemetryLabel = "${runs.size} AUDITS",
                            icon = Icons.Default.Radar,
                            accentColor = forge.amber,
                            onClick = onNavigateToScan,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("dash_open_scan")
                        )
                        TacticalCommandTile(
                            title = "PATCHES",
                            telemetryLabel = if (pendingPatches > 0) "$pendingPatches QUEUED" else "ALL CLEAR",
                            icon = Icons.Default.Build,
                            accentColor = forge.verifiedGreen,
                            onClick = onNavigateToPatches,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("dash_open_patches")
                        )
                        TacticalCommandTile(
                            title = "SHELL",
                            telemetryLabel = if (pendingApprovals.isNotEmpty()) "${pendingApprovals.size} GATE" else "TUI READY",
                            icon = Icons.Default.Terminal,
                            accentColor = forge.tacticalBlue,
                            onClick = onNavigateToAgent,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("dash_open_shell")
                        )
                    }
                }

                // 5. Avionics-Style Global Agent Permission Governance Deck
                item(key = "dashboard_permission_modes") {
                    TacticalPermissionDeck(
                        currentMode = permissionMode,
                        onSelectMode = viewModel::setPermissionMode
                    )
                }

                // 6. Multi-Forge Git Repository & Encrypted Access Token Vault
                item(key = "dashboard_git_forge_integration") {
                    HudCornerFrame(
                        cornerAccent = forge.tacticalBlue,
                        borderColor = forge.tacticalBlue.copy(alpha = 0.55f),
                        elevated = true
                    ) {
                        SectionHeaderLabel(
                            title = "Git Forge & Token Vault",
                            trailingText = "KEYSTORE AES-256"
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "GitHub, GitLab, Codeberg & HF · AES-256 on-device.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        // Provider selector chips
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            GitForgeProvider.entries.forEach { provider ->
                                val selected = selectedProvider == provider
                                val configured = tokenStatuses[provider]?.isConfigured == true
                                FilterChip(
                                    selected = selected,
                                    onClick = {
                                        selectedProviderId = provider.id
                                        tokenInput = ""
                                    },
                                    leadingIcon = if (configured) {
                                        {
                                            Icon(
                                                imageVector = Icons.Default.Lock,
                                                contentDescription = "Token saved",
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    } else null,
                                    label = {
                                        Text(text = provider.label, style = MaterialTheme.typography.labelSmall)
                                    },
                                    shape = MaterialTheme.shapes.extraSmall,
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = forge.tacticalBlueContainer,
                                        selectedLabelColor = forge.tacticalBlue,
                                        selectedLeadingIconColor = forge.verifiedGreen
                                    ),
                                    modifier = Modifier.testTag("forge_provider_${provider.id.lowercase()}")
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Stored Token Status Banner
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(3.dp))
                                .background(forge.recessedWell)
                                .border(
                                    1.dp,
                                    if (currentTokenStatus?.isConfigured == true) forge.verifiedGreen.copy(alpha = 0.5f)
                                    else forge.borderHairline,
                                    RoundedCornerShape(3.dp)
                                )
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = if (currentTokenStatus?.isConfigured == true) Icons.Default.CheckCircle else Icons.Default.Key,
                                    contentDescription = null,
                                    tint = if (currentTokenStatus?.isConfigured == true) forge.verifiedGreen else forge.telemetryMuted,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = if (currentTokenStatus?.isConfigured == true) {
                                            "${currentTokenStatus.detectedKind} (${currentTokenStatus.maskedPreview})"
                                        } else {
                                            "No ${selectedProvider.label} token · Public repos open"
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (currentTokenStatus?.isConfigured == true) forge.verifiedGreen else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    if (tokenInput.isNotBlank()) {
                                        Text(
                                            text = "Detected: $liveDetectedKind",
                                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
                                            color = forge.amber
                                        )
                                    }
                                }
                            }
                            if (currentTokenStatus?.isConfigured == true) {
                                IconButton(
                                    onClick = { viewModel.clearForgeToken(selectedProvider) },
                                    modifier = Modifier.testTag("clear_forge_token_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.DeleteOutline,
                                        contentDescription = "Clear stored token",
                                        tint = forge.criticalRed
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Access Token Integration Input (keyboard auto-scroll enabled)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .bringIntoViewOnImeFocus(extraBottomPadding = 28.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = tokenInput,
                                onValueChange = { tokenInput = it },
                                label = {
                                    Text(
                                        text = "${selectedProvider.label} Access Token",
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                },
                                placeholder = {
                                    Text(
                                        text = selectedProvider.tokenHint,
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                },
                                singleLine = true,
                                visualTransformation = if (showToken) VisualTransformation.None else PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Password,
                                    autoCorrectEnabled = false,
                                    imeAction = ImeAction.Done
                                ),
                                keyboardActions = KeyboardActions(
                                    onDone = { focusManager.clearFocus() }
                                ),
                                trailingIcon = {
                                    IconButton(onClick = { showToken = !showToken }) {
                                        Icon(
                                            imageVector = if (showToken) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                            contentDescription = if (showToken) "Hide token" else "Show token"
                                        )
                                    }
                                },
                                textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFamily),
                                shape = MaterialTheme.shapes.extraSmall,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = forge.amber,
                                    unfocusedBorderColor = forge.borderHairline,
                                    focusedContainerColor = forge.recessedWell,
                                    unfocusedContainerColor = forge.recessedWell
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("git_access_token_input")
                            )

                            Button(
                                onClick = {
                                    if (tokenInput.isNotBlank()) {
                                        viewModel.saveForgeToken(selectedProvider, tokenInput)
                                        tokenInput = ""
                                        focusManager.clearFocus()
                                    }
                                },
                                enabled = tokenInput.isNotBlank(),
                                shape = MaterialTheme.shapes.extraSmall,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = forge.amber,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                ),
                                modifier = Modifier
                                    .height(54.dp)
                                    .testTag("save_forge_token_button")
                            ) {
                                Text("SAVE", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        HorizontalDivider(color = forge.borderHairline)
                        Spacer(modifier = Modifier.height(12.dp))

                        // Repository URL / Slug Input
                        OutlinedTextField(
                            value = repoInput,
                            onValueChange = { repoInput = it },
                            label = { Text("Repository URL or owner/repo", style = MaterialTheme.typography.labelSmall) },
                            placeholder = {
                                Text(text = selectedProvider.exampleRepo, style = MaterialTheme.typography.labelSmall)
                            },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Uri,
                                autoCorrectEnabled = false,
                                imeAction = ImeAction.Next
                            ),
                            textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = JetBrainsMonoFamily),
                            shape = MaterialTheme.shapes.extraSmall,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = forge.tacticalBlue,
                                unfocusedBorderColor = forge.borderHairline,
                                focusedContainerColor = forge.recessedWell,
                                unfocusedContainerColor = forge.recessedWell
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .bringIntoViewOnImeFocus(extraBottomPadding = 24.dp)
                                .testTag("repo_url_input")
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .bringIntoViewOnImeFocus(extraBottomPadding = 28.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = branchInput,
                                onValueChange = { branchInput = it },
                                label = { Text("Branch (Optional)", style = MaterialTheme.typography.labelSmall) },
                                placeholder = { Text("default branch", style = MaterialTheme.typography.labelSmall) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(
                                    autoCorrectEnabled = false,
                                    imeAction = ImeAction.Search
                                ),
                                keyboardActions = KeyboardActions(
                                    onSearch = {
                                        focusManager.clearFocus()
                                        viewModel.inspectForgeRepository(
                                            provider = selectedProvider,
                                            repoInput = repoInput,
                                            branchOverride = branchInput
                                        )
                                    }
                                ),
                                textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFamily),
                                shape = MaterialTheme.shapes.extraSmall,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = forge.tacticalBlue,
                                    unfocusedBorderColor = forge.borderHairline,
                                    focusedContainerColor = forge.recessedWell,
                                    unfocusedContainerColor = forge.recessedWell
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("repo_branch_input")
                            )

                            Button(
                                onClick = {
                                    focusManager.clearFocus()
                                    viewModel.inspectForgeRepository(
                                        provider = selectedProvider,
                                        repoInput = repoInput,
                                        branchOverride = branchInput
                                    )
                                },
                                enabled = repoState !is RepoInspectionState.Loading,
                                shape = MaterialTheme.shapes.extraSmall,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = forge.tacticalBlue,
                                    contentColor = MaterialTheme.colorScheme.onSecondary
                                ),
                                modifier = Modifier
                                    .height(54.dp)
                                    .testTag("inspect_repo_button")
                            ) {
                                Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = stringResource(R.string.dashboard_inspect_repo).uppercase(),
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                                )
                            }
                        }

                        // Repository Inspection Loading / Error / File Browser State
                        RepoInspectionResultSection(
                            repoState = repoState,
                            onLoadFile = { path, autoAudit ->
                                viewModel.loadForgeFileIntoScanner(
                                    filePath = path,
                                    autoRunAudit = autoAudit,
                                    onReadyNavigateToScan = onNavigateToScan
                                )
                            }
                        )
                    }
                }

                // 7. Live Threat Feed / Zero-Threat Standby Card
                item(key = "dashboard_recent_findings") {
                    DashboardThreatFeedPanel(
                        findings = findings,
                        onNavigateToPatches = onNavigateToPatches,
                        onNavigateToScan = onNavigateToScan
                    )
                }
            }
        }
    }
}

@Composable
private fun HudTelemetryTile(
    label: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    val forge = ForgeTheme.colors
    val shape = RoundedCornerShape(3.dp)
    Row(
        modifier = modifier
            .height(52.dp)
            .clip(shape)
            .background(forge.recessedWell)
            .border(1.dp, forge.borderHairline, shape)
            .padding(end = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(34.dp)
                .background(accent)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column {
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontFamily = JetBrainsMonoFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                ),
                color = accent,
                maxLines = 1
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = JetBrainsMonoFamily,
                    fontSize = 9.sp,
                    letterSpacing = 0.5.sp
                ),
                color = forge.telemetryMuted,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun RepoInspectionResultSection(
    repoState: RepoInspectionState,
    onLoadFile: (filePath: String, autoAudit: Boolean) -> Unit
) {
    val forge = ForgeTheme.colors
    when (repoState) {
        RepoInspectionState.Idle -> Unit
        is RepoInspectionState.Loading -> {
            Spacer(modifier = Modifier.height(10.dp))
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth(),
                color = forge.tacticalBlue,
                trackColor = forge.borderHairline
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Inspecting ${repoState.target} via HTTPS Forge API…",
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
                color = forge.tacticalBlue
            )
        }
        is RepoInspectionState.Error -> {
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(3.dp))
                    .background(forge.criticalRedContainer)
                    .border(1.dp, forge.criticalRed, RoundedCornerShape(3.dp))
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.ErrorOutline, contentDescription = "Repo error", tint = forge.criticalRed, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = repoState.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
        is RepoInspectionState.Loaded -> {
            val snap = repoState.snapshot
            Spacer(modifier = Modifier.height(12.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(3.dp))
                    .background(forge.recessedWell)
                    .border(1.dp, forge.borderStrong, RoundedCornerShape(3.dp))
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "${snap.parsedRepo.provider.label.uppercase()} // ${snap.displayName}",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = forge.amber,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Branch: ${snap.defaultBranch} · ${snap.visibility} · ★ ${snap.stars} · Issues: ${snap.openIssues}",
                            style = MaterialTheme.typography.labelSmall,
                            color = forge.tacticalBlue
                        )
                    }
                    StatusLed(
                        label = if (snap.authenticatedRead) "AUTH OK" else "PUBLIC",
                        color = if (snap.authenticatedRead) forge.verifiedGreen else forge.amber
                    )
                }

                if (snap.description.isNotBlank()) {
                    Text(
                        text = snap.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (repoState.isLoadingFile) {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth(),
                        color = forge.amber,
                        trackColor = forge.borderHairline
                    )
                }

                HorizontalDivider(color = forge.borderHairline)
                Text(
                    text = "REPO FILES (${snap.files.size}) · TAP AUDIT",
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
                    color = forge.telemetryMuted
                )

                snap.files.take(12).forEach { file ->
                    Surface(
                        shape = RoundedCornerShape(3.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(
                            1.dp,
                            if (file.isPriorityManifest) forge.amber.copy(alpha = 0.5f) else forge.borderHairline
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = if (file.isPriorityManifest) Icons.Default.Code else Icons.Default.FolderOpen,
                                contentDescription = null,
                                tint = if (file.isPriorityManifest) forge.amber else forge.telemetryMuted,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = file.path,
                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedButton(
                                onClick = { onLoadFile(file.path, false) },
                                shape = MaterialTheme.shapes.extraSmall,
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                border = BorderStroke(1.dp, forge.borderStrong)
                            ) {
                                Text("LOAD", style = MaterialTheme.typography.labelSmall)
                            }
                            Button(
                                onClick = { onLoadFile(file.path, true) },
                                shape = MaterialTheme.shapes.extraSmall,
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = forge.amber,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                )
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(2.dp))
                                Text("AUDIT", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DashboardThreatFeedPanel(
    findings: List<AuditFinding>,
    onNavigateToPatches: () -> Unit,
    onNavigateToScan: () -> Unit
) {
    val forge = ForgeTheme.colors
    if (findings.isEmpty()) {
        HudCornerFrame(
            cornerAccent = forge.verifiedGreen,
            borderColor = forge.borderHairline,
            showTopRail = false
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(forge.verifiedGreenContainer)
                            .border(1.dp, forge.verifiedGreen.copy(alpha = 0.6f), RoundedCornerShape(3.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.VerifiedUser,
                            contentDescription = null,
                            tint = forge.verifiedGreen,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "PERIMETER CLEAR // 0 FINDINGS",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontFamily = JetBrainsMonoFamily,
                                fontWeight = FontWeight.Bold
                            ),
                            color = forge.verifiedGreen
                        )
                        Text(
                            text = "Inspect a repo above or run a scan.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                OutlinedButton(
                    onClick = onNavigateToScan,
                    shape = MaterialTheme.shapes.extraSmall,
                    border = BorderStroke(1.dp, forge.amber),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    modifier = Modifier.height(48.dp)
                ) {
                    Text(
                        text = "SCAN",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = forge.amber
                    )
                }
            }
        }
    } else {
        HudCornerFrame(
            cornerAccent = forge.amber,
            borderColor = forge.borderStrong,
            showTopRail = true
        ) {
            SectionHeaderLabel(
                title = "Live Threat Feed (${findings.size})",
                trailingText = "ROOM + FIRESTORE"
            )
            Spacer(modifier = Modifier.height(8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                findings.take(4).forEach { f ->
                    val sevColor = when (f.severity.uppercase()) {
                        "CRITICAL" -> forge.criticalRed
                        "HIGH" -> forge.amber
                        "MEDIUM" -> forge.tacticalBlue
                        else -> forge.verifiedGreen
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(3.dp))
                            .background(forge.recessedWell)
                            .border(1.dp, forge.borderHairline, RoundedCornerShape(3.dp))
                            .clickable(onClick = onNavigateToPatches)
                            .padding(end = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .width(3.5.dp)
                                .height(54.dp)
                                .background(sevColor)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(vertical = 8.dp)
                        ) {
                            Text(
                                text = "[${f.severity}] ${f.threatCategory} · ${f.filePath}:${f.lineBounds}",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = JetBrainsMonoFamily,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = sevColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = f.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        IconButton(onClick = onNavigateToPatches) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = "Inspect patch",
                                tint = forge.tacticalBlue,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
