package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Publish
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.local.AuditFinding
import com.example.data.local.PatchStatus
import com.example.ui.components.EngineBadge
import com.example.ui.components.ForgeDynamicHeader
import com.example.ui.components.ForgePanel
import com.example.ui.components.MonospaceDiffView
import com.example.ui.components.SectionHeaderLabel
import com.example.ui.components.SeverityBadge
import com.example.ui.components.StatusLed
import com.example.ui.theme.ForgeTheme
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.viewmodel.PatchForgeViewModel
import com.example.ui.viewmodel.PatchVerificationUiState

@Composable
fun PatchWorkbenchScreen(
    viewModel: PatchForgeViewModel,
    modifier: Modifier = Modifier
) {
    val findings by viewModel.allFindings.collectAsStateWithLifecycle()
    val config by viewModel.backendConfig.collectAsStateWithLifecycle()
    val pendingRemoteFix by viewModel.pendingRemoteFix.collectAsStateWithLifecycle()
    val isCommittingRemoteFix by viewModel.isCommittingRemoteFix.collectAsStateWithLifecycle()
    val verificationStates by viewModel.patchVerificationStates.collectAsStateWithLifecycle()
    val clipboard = LocalClipboardManager.current
    val forge = ForgeTheme.colors

    var dispatchConfigExpanded by rememberSaveable { mutableStateOf(false) }

    pendingRemoteFix?.let { fix ->
        AlertDialog(
            onDismissRequest = { if (!isCommittingRemoteFix) viewModel.cancelRemoteRepoFix() },
            containerColor = MaterialTheme.colorScheme.surface,
            title = {
                Text(
                    text = "CONFIRM REMOTE COMMIT",
                    style = MaterialTheme.typography.titleMedium,
                    color = forge.amber
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Approval Required: Commit this atomic fix to ${fix.parsedRepo.provider.label} (${fix.parsedRepo.fullPath} @ ${fix.branch}).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "File: ${fix.filePath}\nMessage: ${fix.commitMessage}",
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
                        color = forge.tacticalBlue
                    )
                    MonospaceDiffView(unifiedDiff = fix.diffPreview)
                }
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.confirmRemoteRepoFix() },
                    enabled = !isCommittingRemoteFix,
                    shape = MaterialTheme.shapes.extraSmall,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = forge.amber,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    modifier = Modifier.testTag("confirm_remote_commit_button")
                ) {
                    Text(if (isCommittingRemoteFix) "COMMITTING…" else "APPROVE & COMMIT")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { viewModel.cancelRemoteRepoFix() },
                    enabled = !isCommittingRemoteFix
                ) {
                    Text("CANCEL", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        )
    }

    var targetBranch by rememberSaveable { mutableStateOf(config.defaultTargetBranch) }
    var commitMessage by rememberSaveable {
        mutableStateOf("fix(patchforge): remediate supply-chain & AST findings")
    }

    LaunchedEffect(config.defaultTargetBranch) {
        if (targetBranch.isBlank()) {
            targetBranch = config.defaultTargetBranch
        }
    }

    val approvedCount = findings.count { it.patchStatus == PatchStatus.APPROVED }
    val queuedOrAppliedCount = findings.count {
        it.patchStatus == PatchStatus.QUEUED_SYNC || it.patchStatus == PatchStatus.APPLIED
    }
    val pendingCount = findings.count { it.patchStatus == PatchStatus.PENDING }

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
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // 1. Compact Dynamic Header with Collapsible Info Drawer
            item(key = "patch_dynamic_header") {
                ForgeDynamicHeader(
                    title = stringResource(R.string.patches_title),
                    compactSubtitle = stringResource(R.string.patches_subtitle),
                    statusLabel = "$approvedCount READY",
                    statusColor = if (approvedCount > 0) forge.verifiedGreen else forge.amber,
                    infoDetail = stringResource(R.string.patches_info_detail),
                    metadataChips = listOf(
                        "BRANCH" to targetBranch.ifBlank { "main" },
                        "PENDING" to "$pendingCount",
                        "SYNCED" to "$queuedOrAppliedCount"
                    ),
                    modifier = Modifier.testTag("patches_dynamic_header")
                )
            }

            // 2. Compact Dispatch Bar + Collapsible Branch/Commit Drawer
            item(key = "patch_workbench_controls") {
                ForgePanel(elevated = false, accentBorder = forge.borderStrong, contentPadding = 12.dp) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        StatusLed(
                            label = "OK: $approvedCount",
                            color = forge.verifiedGreen,
                            modifier = Modifier.weight(1f)
                        )
                        StatusLed(
                            label = "WAIT: $pendingCount",
                            color = forge.amber,
                            modifier = Modifier.weight(1f)
                        )
                        StatusLed(
                            label = "SYNC: $queuedOrAppliedCount",
                            color = forge.tacticalBlue,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                val bundle = viewModel.buildApprovedBundle()
                                if (bundle.isBlank()) {
                                    viewModel.notifyMessage("Approve at least one patch to copy bundle.")
                                } else {
                                    clipboard.setText(AnnotatedString(bundle))
                                    viewModel.notifyMessage("Copied atomic .patch bundle for git apply.")
                                }
                            },
                            shape = MaterialTheme.shapes.extraSmall,
                            border = BorderStroke(1.dp, forge.borderStrong),
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp)
                                .testTag("copy_bundle_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "Copy Approved Patch Bundle",
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = stringResource(R.string.patches_copy_bundle).uppercase(),
                                style = MaterialTheme.typography.labelSmall
                            )
                        }

                        Button(
                            onClick = {
                                viewModel.dispatchApprovedPatches(
                                    targetBranch = targetBranch,
                                    commitMessage = commitMessage
                                )
                            },
                            shape = MaterialTheme.shapes.extraSmall,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = forge.amber,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp)
                                .testTag("dispatch_branch_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.CloudUpload,
                                contentDescription = "Dispatch Approved Patches to Target Branch",
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = stringResource(R.string.patches_dispatch_branch).uppercase(),
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { dispatchConfigExpanded = !dispatchConfigExpanded }
                            .padding(vertical = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "TARGET: ${targetBranch.ifBlank { "main" }}",
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
                            color = forge.telemetryMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = if (dispatchConfigExpanded) "HIDE OPTIONS" else "BRANCH OPTIONS",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = forge.tacticalBlue
                        )
                    }

                    AnimatedVisibility(visible = dispatchConfigExpanded) {
                        Column(
                            modifier = Modifier.padding(top = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = targetBranch,
                                onValueChange = { targetBranch = it },
                                label = {
                                    Text(
                                        text = stringResource(R.string.patches_target_branch_label),
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
                                    .testTag("target_branch_input")
                            )

                            OutlinedTextField(
                                value = commitMessage,
                                onValueChange = { commitMessage = it },
                                label = {
                                    Text(
                                        text = stringResource(R.string.patches_commit_msg_label),
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                },
                                singleLine = true,
                                textStyle = MaterialTheme.typography.bodySmall,
                                shape = MaterialTheme.shapes.extraSmall,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = forge.amber,
                                    unfocusedBorderColor = forge.borderHairline,
                                    focusedContainerColor = MaterialTheme.colorScheme.background,
                                    unfocusedContainerColor = MaterialTheme.colorScheme.background
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("commit_message_input")
                            )
                        }
                    }
                }
            }

            if (findings.isEmpty()) {
                item(key = "empty_patches") {
                    ForgePanel {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Build,
                                contentDescription = null,
                                tint = forge.amber,
                                modifier = Modifier.size(28.dp)
                            )
                            Column {
                                Text(
                                    text = stringResource(R.string.patches_empty_title),
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = stringResource(R.string.patches_empty_body),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            } else {
                items(findings, key = { it.id }) { finding ->
                    val verifyState = verificationStates[finding.id]
                    val currentDiff = (verifyState as? PatchVerificationUiState.Verified)?.result?.unifiedDiff
                        ?: viewModel.buildPatchForFinding(finding)

                    PatchDiffCard(
                        finding = finding,
                        unifiedDiff = currentDiff,
                        verificationState = verifyState,
                        onVerifyAst = { viewModel.verifyAtomicPatchAgainstAst(finding) },
                        onApprove = { viewModel.approveFinding(finding.id) },
                        onDismiss = { viewModel.dismissFinding(finding.id) },
                        onReset = { viewModel.resetFindingStatus(finding.id) },
                        onCopy = {
                            clipboard.setText(AnnotatedString(currentDiff))
                            viewModel.notifyMessage("Copied atomic UDIFF for ${finding.filePath} (${finding.lineBounds})")
                        },
                        onCommitRemoteRepo = { viewModel.requestRemoteRepoFix(finding) },
                        onDelete = { viewModel.deleteFinding(finding.id) },
                        onDispatchSingle = {
                            viewModel.dispatchSinglePatch(
                                finding = finding,
                                targetBranch = targetBranch,
                                commitMessage = "fix(${finding.threatCategory.lowercase()}): ${finding.filePath} ${finding.lineBounds}"
                            )
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun PatchDiffCard(
    finding: AuditFinding,
    unifiedDiff: String,
    verificationState: PatchVerificationUiState?,
    onVerifyAst: () -> Unit,
    onApprove: () -> Unit,
    onDismiss: () -> Unit,
    onReset: () -> Unit,
    onCopy: () -> Unit,
    onCommitRemoteRepo: () -> Unit,
    onDelete: () -> Unit,
    onDispatchSingle: () -> Unit
) {
    val forge = ForgeTheme.colors
    val statusColor = when (finding.patchStatus) {
        PatchStatus.APPROVED, PatchStatus.APPLIED -> forge.verifiedGreen
        PatchStatus.QUEUED_SYNC -> forge.tacticalBlue
        PatchStatus.DISMISSED -> forge.telemetryMuted
        else -> forge.amber
    }

    ForgePanel(accentBorder = statusColor.copy(alpha = 0.6f), contentPadding = 12.dp) {
        SectionHeaderLabel(
            title = "${finding.id} · ${finding.threatCategory}",
            trailingText = finding.patchStatus
        )
        Spacer(modifier = Modifier.height(6.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SeverityBadge(severity = finding.severity)
            EngineBadge(engineType = finding.engineType)
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "${finding.filePath}:${finding.lineBounds}",
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = finding.description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface
        )

        // AST Self-Verification Feedback Banner
        when (verificationState) {
            PatchVerificationUiState.Verifying -> {
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = forge.tacticalBlue,
                    trackColor = forge.borderHairline
                )
                Text(
                    text = "Running 2-stage Atomic UDIFF + AST Self-Verification…",
                    style = MaterialTheme.typography.labelSmall,
                    color = forge.tacticalBlue
                )
            }
            is PatchVerificationUiState.Verified -> {
                val res = verificationState.result
                val ok = res.astVerified && res.isAtomic
                val color = if (ok) forge.verifiedGreen else forge.amber
                Spacer(modifier = Modifier.height(8.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.extraSmall)
                        .background(if (ok) forge.verifiedGreenContainer else forge.amberContainer)
                        .border(1.dp, color.copy(alpha = 0.6f), MaterialTheme.shapes.extraSmall)
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = buildString {
                            append(if (ok) "AST VERIFIED · ATOMIC UDIFF" else "AST CHECK · REVIEW ADVISED")
                            if (res.wasSelfCorrected) append(" · REFINED")
                        },
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = color
                    )
                    Text(
                        text = res.verificationNotes,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
            is PatchVerificationUiState.Failed -> {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "AST verify error: ${verificationState.message}",
                    style = MaterialTheme.typography.labelSmall,
                    color = forge.criticalRed
                )
            }
            null -> Unit
        }

        Spacer(modifier = Modifier.height(8.dp))
        MonospaceDiffView(unifiedDiff = unifiedDiff)
        Spacer(modifier = Modifier.height(8.dp))

        // Action Row 1: Primary AST Verify + Approve/Restore
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            OutlinedButton(
                onClick = onVerifyAst,
                enabled = verificationState !is PatchVerificationUiState.Verifying,
                shape = MaterialTheme.shapes.extraSmall,
                border = BorderStroke(1.dp, forge.tacticalBlue),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(42.dp)
                    .testTag("verify_ast_${finding.id}")
            ) {
                Icon(
                    imageVector = Icons.Default.VerifiedUser,
                    contentDescription = "Self-verify atomic patch against AST",
                    tint = forge.tacticalBlue,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = if (verificationState is PatchVerificationUiState.Verified) "RE-VERIFY AST" else "AST VERIFY",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = forge.tacticalBlue,
                    maxLines = 1
                )
            }

            if (finding.patchStatus == PatchStatus.DISMISSED) {
                OutlinedButton(
                    onClick = onReset,
                    shape = MaterialTheme.shapes.extraSmall,
                    border = BorderStroke(1.dp, forge.borderStrong),
                    modifier = Modifier
                        .weight(1f)
                        .height(42.dp)
                ) {
                    Icon(Icons.Default.Restore, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("RESTORE", style = MaterialTheme.typography.labelSmall)
                }
            } else {
                OutlinedButton(
                    onClick = onApprove,
                    enabled = finding.patchStatus != PatchStatus.APPROVED && finding.patchStatus != PatchStatus.APPLIED,
                    shape = MaterialTheme.shapes.extraSmall,
                    border = BorderStroke(1.dp, forge.verifiedGreen),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(42.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Approve patch",
                        tint = forge.verifiedGreen,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("APPROVE", style = MaterialTheme.typography.labelSmall, color = forge.verifiedGreen)
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Action Row 2: Secondary Tool Buttons (Dismiss, Copy, Dispatch, Commit Remote, Delete)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (finding.patchStatus != PatchStatus.DISMISSED) {
                OutlinedButton(
                    onClick = onDismiss,
                    shape = MaterialTheme.shapes.extraSmall,
                    border = BorderStroke(1.dp, forge.borderStrong),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Dismiss patch",
                        modifier = Modifier.size(15.dp)
                    )
                }
            }

            OutlinedButton(
                onClick = onCopy,
                shape = MaterialTheme.shapes.extraSmall,
                border = BorderStroke(1.dp, forge.borderStrong),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = "Copy patch diff",
                    modifier = Modifier.size(15.dp)
                )
            }

            OutlinedButton(
                onClick = onDispatchSingle,
                shape = MaterialTheme.shapes.extraSmall,
                border = BorderStroke(1.dp, forge.tacticalBlue),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Default.CloudUpload,
                    contentDescription = "Dispatch single patch to branch",
                    tint = forge.tacticalBlue,
                    modifier = Modifier.size(15.dp)
                )
            }

            OutlinedButton(
                onClick = onCommitRemoteRepo,
                shape = MaterialTheme.shapes.extraSmall,
                border = BorderStroke(1.dp, forge.amber),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Default.Publish,
                    contentDescription = "Propose and commit fix directly to Git forge",
                    tint = forge.amber,
                    modifier = Modifier.size(15.dp)
                )
            }

            OutlinedButton(
                onClick = onDelete,
                shape = MaterialTheme.shapes.extraSmall,
                border = BorderStroke(1.dp, forge.borderHairline),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Default.DeleteOutline,
                    contentDescription = "Delete finding",
                    tint = forge.telemetryMuted,
                    modifier = Modifier.size(15.dp)
                )
            }
        }
    }
}
