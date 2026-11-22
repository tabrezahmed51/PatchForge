package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.local.AuditFinding
import com.example.data.local.PatchStatus
import com.example.ui.components.EngineBadge
import com.example.ui.components.ForgePanel
import com.example.ui.components.MonospaceDiffView
import com.example.ui.components.SectionHeaderLabel
import com.example.ui.components.SeverityBadge
import com.example.ui.components.StatusLed
import com.example.ui.theme.ForgeTheme
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.viewmodel.PatchForgeViewModel

@Composable
fun PatchWorkbenchScreen(
    viewModel: PatchForgeViewModel,
    modifier: Modifier = Modifier
) {
    val findings by viewModel.allFindings.collectAsStateWithLifecycle()
    val config by viewModel.backendConfig.collectAsStateWithLifecycle()
    val clipboard = LocalClipboardManager.current
    val forge = ForgeTheme.colors

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
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Workbench Header & Branch Dispatch Controls
            item(key = "patch_workbench_header") {
                ForgePanel(elevated = true, accentBorder = forge.borderStrong) {
                    Text(
                        text = stringResource(R.string.patches_title).uppercase(),
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(R.string.patches_subtitle),
                        style = MaterialTheme.typography.labelSmall,
                        color = forge.amber
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        StatusLed(
                            label = "APPROVED: $approvedCount",
                            color = forge.verifiedGreen,
                            modifier = Modifier.weight(1f)
                        )
                        StatusLed(
                            label = "PENDING: $pendingCount",
                            color = forge.amber,
                            modifier = Modifier.weight(1f)
                        )
                        StatusLed(
                            label = "SYNCED/Q: $queuedOrAppliedCount",
                            color = forge.tacticalBlue,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

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

                    Spacer(modifier = Modifier.height(8.dp))

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

                    Spacer(modifier = Modifier.height(12.dp))

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
                                    viewModel.notifyMessage("Copied unified .patch bundle for git apply.")
                                }
                            },
                            shape = MaterialTheme.shapes.extraSmall,
                            border = BorderStroke(1.dp, forge.borderStrong),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("copy_bundle_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "Copy Approved Patch Bundle",
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "COPY BUNDLE",
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
                                .height(48.dp)
                                .testTag("dispatch_branch_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.CloudUpload,
                                contentDescription = "Dispatch Approved Patches to Target Branch",
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "APPLY TO BRANCH",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
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
                                modifier = Modifier.size(30.dp)
                            )
                            Column {
                                Text(
                                    text = stringResource(R.string.patches_empty_title),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(4.dp))
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
                    PatchDiffCard(
                        finding = finding,
                        unifiedDiff = viewModel.buildPatchForFinding(finding),
                        onApprove = { viewModel.approveFinding(finding.id) },
                        onDismiss = { viewModel.dismissFinding(finding.id) },
                        onReset = { viewModel.resetFindingStatus(finding.id) },
                        onCopy = {
                            clipboard.setText(AnnotatedString(viewModel.buildPatchForFinding(finding)))
                            viewModel.notifyMessage("Copied Git patch for ${finding.filePath} (${finding.lineBounds})")
                        },
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
    onApprove: () -> Unit,
    onDismiss: () -> Unit,
    onReset: () -> Unit,
    onCopy: () -> Unit,
    onDispatchSingle: () -> Unit
) {
    val forge = ForgeTheme.colors
    val statusColor = when (finding.patchStatus) {
        PatchStatus.APPROVED, PatchStatus.APPLIED -> forge.verifiedGreen
        PatchStatus.QUEUED_SYNC -> forge.tacticalBlue
        PatchStatus.DISMISSED -> forge.telemetryMuted
        else -> forge.amber
    }

    ForgePanel(accentBorder = statusColor.copy(alpha = 0.6f)) {
        SectionHeaderLabel(
            title = "${finding.id} · ${finding.threatCategory}",
            trailingText = finding.patchStatus
        )
        Spacer(modifier = Modifier.height(8.dp))

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
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }

        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = finding.description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(10.dp))
        MonospaceDiffView(unifiedDiff = unifiedDiff)
        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (finding.patchStatus == PatchStatus.DISMISSED) {
                OutlinedButton(
                    onClick = onReset,
                    shape = MaterialTheme.shapes.extraSmall,
                    border = BorderStroke(1.dp, forge.borderStrong),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Restore, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("RESTORE", style = MaterialTheme.typography.labelSmall)
                }
            } else {
                OutlinedButton(
                    onClick = onApprove,
                    enabled = finding.patchStatus != PatchStatus.APPROVED && finding.patchStatus != PatchStatus.APPLIED,
                    shape = MaterialTheme.shapes.extraSmall,
                    border = BorderStroke(1.dp, forge.verifiedGreen),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                    modifier = Modifier.weight(1f)
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

                OutlinedButton(
                    onClick = onDismiss,
                    shape = MaterialTheme.shapes.extraSmall,
                    border = BorderStroke(1.dp, forge.borderStrong),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
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
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
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
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.CloudUpload,
                    contentDescription = "Dispatch single patch to branch",
                    tint = forge.tacticalBlue,
                    modifier = Modifier.size(15.dp)
                )
            }
        }
    }
}
