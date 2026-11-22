package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Terminal
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.core.AgentPermissionMode
import com.example.data.local.GitForgeProvider
import com.example.data.local.PatchStatus
import com.example.data.local.TokenClassifier
import com.example.ui.components.ForgeDynamicHeader
import com.example.ui.components.ForgePanel
import com.example.ui.components.SectionHeaderLabel
import com.example.ui.components.StatusLed
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
    val pendingPatches = findings.count { it.patchStatus == PatchStatus.PENDING || it.patchStatus == PatchStatus.APPROVED }

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
            // 1. Compact Dynamic Header with Collapsible Telemetry Drawer
            item(key = "dashboard_hero") {
                val permColor = when (permissionMode) {
                    AgentPermissionMode.AUTONOMOUS -> forge.verifiedGreen
                    AgentPermissionMode.ASK_WHEN_NEEDED -> forge.amber
                    AgentPermissionMode.ALWAYS_ASK -> forge.tacticalBlue
                }
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
                    modifier = Modifier.testTag("dashboard_dynamic_header")
                )
            }

            // 2. Operational Telemetry Strip + Quick Launch Actions
            item(key = "dashboard_metrics") {
                ForgePanel(elevated = true, accentBorder = forge.borderStrong) {
                    SectionHeaderLabel(
                        title = "Security Posture Telemetry",
                        trailingText = "${runs.size} RUNS LOGGED"
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        MetricTile(
                            label = "CRITICAL",
                            value = "$criticalCount",
                            accent = if (criticalCount > 0) forge.criticalRed else forge.verifiedGreen,
                            modifier = Modifier.weight(1f)
                        )
                        MetricTile(
                            label = "HIGH",
                            value = "$highCount",
                            accent = if (highCount > 0) forge.amber else forge.tacticalBlue,
                            modifier = Modifier.weight(1f)
                        )
                        MetricTile(
                            label = "PATCHES",
                            value = "$pendingPatches",
                            accent = forge.tacticalBlue,
                            modifier = Modifier.weight(1f)
                        )
                        MetricTile(
                            label = "APPROVALS",
                            value = "${pendingApprovals.size}",
                            accent = if (pendingApprovals.isNotEmpty()) forge.amber else forge.telemetryMuted,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = onNavigateToScan,
                            shape = MaterialTheme.shapes.extraSmall,
                            border = BorderStroke(1.dp, forge.amber),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("dash_open_scan")
                        ) {
                            Icon(Icons.Default.Radar, contentDescription = null, tint = forge.amber, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(5.dp))
                            Text("SCANNER", style = MaterialTheme.typography.labelSmall, color = forge.amber)
                        }
                        OutlinedButton(
                            onClick = onNavigateToPatches,
                            shape = MaterialTheme.shapes.extraSmall,
                            border = BorderStroke(1.dp, forge.borderStrong),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("dash_open_patches")
                        ) {
                            Icon(Icons.Default.Build, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(5.dp))
                            Text("PATCHES", style = MaterialTheme.typography.labelSmall)
                        }
                        OutlinedButton(
                            onClick = onNavigateToAgent,
                            shape = MaterialTheme.shapes.extraSmall,
                            border = BorderStroke(1.dp, forge.tacticalBlue),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("dash_open_shell")
                        ) {
                            Icon(Icons.Default.Terminal, contentDescription = null, tint = forge.tacticalBlue, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(5.dp))
                            Text("SHELL", style = MaterialTheme.typography.labelSmall, color = forge.tacticalBlue)
                        }
                    }
                }
            }

            // 3. Global Agent Permission Mode Control
            item(key = "dashboard_permission_modes") {
                ForgePanel(accentBorder = forge.amber.copy(alpha = 0.5f)) {
                    SectionHeaderLabel(
                        title = "Global Agent Permission Mode",
                        trailingText = permissionMode.shortLabel
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AgentPermissionMode.entries.forEach { mode ->
                            val selected = permissionMode == mode
                            FilterChip(
                                selected = selected,
                                onClick = { viewModel.setPermissionMode(mode) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Security,
                                        contentDescription = null,
                                        modifier = Modifier.size(15.dp)
                                    )
                                },
                                label = {
                                    Text(text = mode.label, style = MaterialTheme.typography.labelSmall)
                                },
                                shape = MaterialTheme.shapes.extraSmall,
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = forge.amberContainer,
                                    selectedLabelColor = forge.amber,
                                    selectedLeadingIconColor = forge.amber
                                ),
                                modifier = Modifier.testTag("perm_mode_${mode.id.lowercase()}")
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = permissionMode.summary,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // 4. Multi-Forge Git Repository & Encrypted Access Token Integration
            item(key = "dashboard_git_forge_integration") {
                ForgePanel(elevated = true, accentBorder = forge.tacticalBlue.copy(alpha = 0.6f)) {
                    SectionHeaderLabel(
                        title = "Git Forge & Token Vault",
                        trailingText = "KEYSTORE AES-256"
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "GitHub (Fine-Grained / Classic), GitLab, Codeberg & HF. Tokens stay encrypted on-device.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
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
                            .clip(MaterialTheme.shapes.extraSmall)
                            .background(MaterialTheme.colorScheme.background)
                            .border(1.dp, forge.borderHairline, MaterialTheme.shapes.extraSmall)
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
                                        "No ${selectedProvider.label} token stored (public repos still inspectable)"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (currentTokenStatus?.isConfigured == true) forge.verifiedGreen else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (tokenInput.isNotBlank()) {
                                    Text(
                                        text = "Detected: $liveDetectedKind",
                                        style = MaterialTheme.typography.labelSmall,
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

                    // Access Token Integration Input
                    Row(
                        modifier = Modifier.fillMaxWidth(),
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
                                focusedContainerColor = MaterialTheme.colorScheme.background,
                                unfocusedContainerColor = MaterialTheme.colorScheme.background
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
                        textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = JetBrainsMonoFamily),
                        shape = MaterialTheme.shapes.extraSmall,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = forge.tacticalBlue,
                            unfocusedBorderColor = forge.borderHairline,
                            focusedContainerColor = MaterialTheme.colorScheme.background,
                            unfocusedContainerColor = MaterialTheme.colorScheme.background
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("repo_url_input")
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = branchInput,
                            onValueChange = { branchInput = it },
                            label = { Text("Branch (Optional)", style = MaterialTheme.typography.labelSmall) },
                            placeholder = { Text("default branch", style = MaterialTheme.typography.labelSmall) },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFamily),
                            shape = MaterialTheme.shapes.extraSmall,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = forge.tacticalBlue,
                                unfocusedBorderColor = forge.borderHairline,
                                focusedContainerColor = MaterialTheme.colorScheme.background,
                                unfocusedContainerColor = MaterialTheme.colorScheme.background
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("repo_branch_input")
                        )

                        Button(
                            onClick = {
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
                    when (val state = repoState) {
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
                                text = "Inspecting ${state.target} via HTTPS Forge API…",
                                style = MaterialTheme.typography.labelSmall,
                                color = forge.tacticalBlue
                            )
                        }
                        is RepoInspectionState.Error -> {
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
                                Icon(Icons.Default.ErrorOutline, contentDescription = "Repo error", tint = forge.criticalRed, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = state.message,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                        is RepoInspectionState.Loaded -> {
                            val snap = state.snapshot
                            Spacer(modifier = Modifier.height(12.dp))
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(MaterialTheme.shapes.extraSmall)
                                    .background(MaterialTheme.colorScheme.background)
                                    .border(1.dp, forge.borderStrong, MaterialTheme.shapes.extraSmall)
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

                                if (state.isLoadingFile) {
                                    LinearProgressIndicator(
                                        modifier = Modifier.fillMaxWidth(),
                                        color = forge.amber,
                                        trackColor = forge.borderHairline
                                    )
                                }

                                HorizontalDivider(color = forge.borderHairline)
                                Text(
                                    text = "INSPECTABLE FILES (${snap.files.size}) — TAP AUDIT TO SCAN & PROPOSE FIXES",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = forge.telemetryMuted
                                )

                                snap.files.take(12).forEach { file ->
                                    Surface(
                                        shape = MaterialTheme.shapes.extraSmall,
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
                                                onClick = {
                                                    viewModel.loadForgeFileIntoScanner(
                                                        filePath = file.path,
                                                        autoRunAudit = false,
                                                        onReadyNavigateToScan = onNavigateToScan
                                                    )
                                                },
                                                shape = MaterialTheme.shapes.extraSmall,
                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                                border = BorderStroke(1.dp, forge.borderStrong)
                                            ) {
                                                Text("LOAD", style = MaterialTheme.typography.labelSmall)
                                            }
                                            Button(
                                                onClick = {
                                                    viewModel.loadForgeFileIntoScanner(
                                                        filePath = file.path,
                                                        autoRunAudit = true,
                                                        onReadyNavigateToScan = onNavigateToScan
                                                    )
                                                },
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
            }

            // 5. Recent Findings Snapshot
            if (findings.isNotEmpty()) {
                item(key = "dashboard_recent_findings") {
                    ForgePanel {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            SectionHeaderLabel(
                                title = "Recent Audit Findings (${findings.size})",
                                trailingText = "ROOM + FIRESTORE",
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        findings.take(4).forEach { f ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "[${f.severity}] ${f.threatCategory} · ${f.filePath}:${f.lineBounds}",
                                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
                                        color = if (f.severity == "CRITICAL") forge.criticalRed else forge.amber,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
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
    }
}

@Composable
private fun MetricTile(
    label: String,
    value: String,
    accent: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier
) {
    val forge = ForgeTheme.colors
    Column(
        modifier = modifier
            .clip(MaterialTheme.shapes.extraSmall)
            .background(MaterialTheme.colorScheme.background)
            .border(1.dp, forge.borderHairline, MaterialTheme.shapes.extraSmall)
            .padding(horizontal = 8.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge.copy(
                fontFamily = JetBrainsMonoFamily,
                fontWeight = FontWeight.Bold
            ),
            color = accent
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.5.sp),
            color = forge.telemetryMuted,
            maxLines = 1
        )
    }
}
