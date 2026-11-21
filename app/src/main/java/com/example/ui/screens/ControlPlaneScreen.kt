package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sync
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.local.PendingSyncActionEntity
import com.example.data.remote.WsConnectionState
import com.example.ui.components.ForgePanel
import com.example.ui.components.SectionHeaderLabel
import com.example.ui.components.StatusLed
import com.example.ui.theme.ForgeTheme
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.viewmodel.BackendHealthState
import com.example.ui.viewmodel.PatchForgeViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ControlPlaneScreen(
    viewModel: PatchForgeViewModel,
    modifier: Modifier = Modifier
) {
    val config by viewModel.backendConfig.collectAsStateWithLifecycle()
    val healthState by viewModel.healthState.collectAsStateWithLifecycle()
    val wsState by viewModel.wsConnectionState.collectAsStateWithLifecycle()
    val workerEvents by viewModel.workerEvents.collectAsStateWithLifecycle()
    val queueItems by viewModel.pendingSyncActions.collectAsStateWithLifecycle()
    val isRetrying by viewModel.isRetryingQueue.collectAsStateWithLifecycle()
    val forge = ForgeTheme.colors

    var baseUrl by rememberSaveable { mutableStateOf(config.baseUrl) }
    var bearerToken by rememberSaveable { mutableStateOf(config.bearerToken) }
    var defaultBranch by rememberSaveable { mutableStateOf(config.defaultTargetBranch) }
    var orchestratorMode by rememberSaveable { mutableStateOf(config.orchestratorMode) }

    LaunchedEffect(config) {
        if (baseUrl.isBlank()) baseUrl = config.baseUrl
        if (defaultBranch.isBlank()) defaultBranch = config.defaultTargetBranch
    }

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
            // FastAPI REST Control Plane Card
            item(key = "fastapi_config_panel") {
                ForgePanel(elevated = true, accentBorder = forge.borderStrong) {
                    Text(
                        text = stringResource(R.string.control_title).uppercase(),
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(R.string.control_subtitle),
                        style = MaterialTheme.typography.labelSmall,
                        color = forge.amber
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Health Status Strip
                    val (statusLabel, statusColor, statusDetail) = when (val h = healthState) {
                        BackendHealthState.Unchecked -> Triple(
                            "UNPROBED",
                            forge.telemetryMuted,
                            "Probe /health to verify FastAPI + Redis/Celery or asyncio workers."
                        )
                        BackendHealthState.Probing -> Triple(
                            "PROBING…",
                            forge.amber,
                            "Contacting $baseUrl/health"
                        )
                        is BackendHealthState.Online -> Triple(
                            "ONLINE · ${h.health.workerBackend.uppercase()}",
                            forge.verifiedGreen,
                            "Service=${h.health.service} · Workers=${h.health.activeWorkers} · DB=${if (h.health.dbConnected) "OK" else "DOWN"} · Provider=${h.health.provider}"
                        )
                        is BackendHealthState.Offline -> Triple(
                            "OFFLINE (LOCAL GEMINI ACTIVE)",
                            forge.amber,
                            "Fallback active: ${h.reason}. Mutations buffer safely in Room Retry Queue."
                        )
                    }

                    StatusLed(label = statusLabel, color = statusColor)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = statusDetail,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (healthState is BackendHealthState.Probing) {
                        Spacer(modifier = Modifier.height(8.dp))
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth(),
                            color = forge.amber,
                            trackColor = forge.borderHairline
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = baseUrl,
                        onValueChange = { baseUrl = it },
                        label = {
                            Text(
                                text = stringResource(R.string.control_endpoint_label),
                                style = MaterialTheme.typography.labelSmall
                            )
                        },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = JetBrainsMonoFamily),
                        shape = MaterialTheme.shapes.extraSmall,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = forge.amber,
                            unfocusedBorderColor = forge.borderHairline,
                            focusedContainerColor = MaterialTheme.colorScheme.background,
                            unfocusedContainerColor = MaterialTheme.colorScheme.background
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("fastapi_url_input")
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = bearerToken,
                        onValueChange = { bearerToken = it },
                        label = {
                            Text(
                                text = stringResource(R.string.control_token_label),
                                style = MaterialTheme.typography.labelSmall
                            )
                        },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = JetBrainsMonoFamily),
                        shape = MaterialTheme.shapes.extraSmall,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = forge.amber,
                            unfocusedBorderColor = forge.borderHairline,
                            focusedContainerColor = MaterialTheme.colorScheme.background,
                            unfocusedContainerColor = MaterialTheme.colorScheme.background
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("fastapi_token_input")
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Orchestration Mode (Provider Adaptability)
                    Text(
                        text = "MODEL ORCHESTRATION ROUTING",
                        style = MaterialTheme.typography.labelSmall,
                        color = forge.telemetryMuted
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = orchestratorMode == "NATIVE_GEMINI",
                            onClick = { orchestratorMode = "NATIVE_GEMINI" },
                            label = { Text("Native Gemini", style = MaterialTheme.typography.labelSmall) },
                            shape = MaterialTheme.shapes.extraSmall,
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = forge.amberContainer,
                                selectedLabelColor = forge.amber
                            ),
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = orchestratorMode == "FASTAPI_HYBRID",
                            onClick = { orchestratorMode = "FASTAPI_HYBRID" },
                            label = { Text("FastAPI Hybrid Sync", style = MaterialTheme.typography.labelSmall) },
                            shape = MaterialTheme.shapes.extraSmall,
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = forge.tacticalBlueContainer,
                                selectedLabelColor = forge.tacticalBlue
                            ),
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Button(
                        onClick = {
                            viewModel.saveAndProbeBackend(
                                baseUrl = baseUrl,
                                bearerToken = bearerToken,
                                defaultTargetBranch = defaultBranch,
                                orchestratorMode = orchestratorMode,
                                autoRetryOffline = true
                            )
                        },
                        shape = MaterialTheme.shapes.extraSmall,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = forge.amber,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("probe_health_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Dns,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.control_save_probe).uppercase(),
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }

            // Provider-Agnostic Architecture Telemetry
            item(key = "provider_telemetry") {
                ForgePanel {
                    SectionHeaderLabel(
                        title = "Provider-Agnostic Adapter",
                        trailingText = "ACTIVE"
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Hub,
                            contentDescription = null,
                            tint = forge.tacticalBlue,
                            modifier = Modifier.size(22.dp)
                        )
                        Column {
                            Text(
                                text = viewModel.activeProviderName,
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Contract: AiReviewProvider · Enforces JSON Schema (file_path, line_bounds, threat_category, severity, description, replacement_snippet)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Live WebSocket Background Worker Stream Terminal
            item(key = "websocket_terminal") {
                val isConnected = wsState is WsConnectionState.Connected || wsState is WsConnectionState.Connecting
                val (wsLabel, wsColor) = when (val s = wsState) {
                    WsConnectionState.Disconnected -> "WS DISCONNECTED" to forge.telemetryMuted
                    WsConnectionState.Connecting -> "WS DIALING…" to forge.amber
                    is WsConnectionState.Connected -> "WS STREAM LIVE" to forge.verifiedGreen
                    is WsConnectionState.Reconnecting -> "WS RETRY #${s.attempt}" to forge.amber
                    is WsConnectionState.Error -> "WS OFFLINE" to forge.criticalRed
                }

                ForgePanel {
                    SectionHeaderLabel(
                        title = "WebSocket Worker Telemetry",
                        trailingText = "${workerEvents.size} EVENTS"
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        StatusLed(label = wsLabel, color = wsColor)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (isConnected) {
                                OutlinedButton(
                                    onClick = { viewModel.disconnectWorkerStream() },
                                    shape = MaterialTheme.shapes.extraSmall,
                                    border = BorderStroke(1.dp, forge.criticalRed),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    modifier = Modifier.testTag("ws_disconnect_button")
                                ) {
                                    Icon(
                                        Icons.Default.LinkOff,
                                        contentDescription = null,
                                        tint = forge.criticalRed,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("DISCONNECT", style = MaterialTheme.typography.labelSmall, color = forge.criticalRed)
                                }
                            } else {
                                OutlinedButton(
                                    onClick = { viewModel.connectWorkerStream() },
                                    shape = MaterialTheme.shapes.extraSmall,
                                    border = BorderStroke(1.dp, forge.tacticalBlue),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    modifier = Modifier.testTag("ws_connect_button")
                                ) {
                                    Icon(
                                        Icons.Default.Link,
                                        contentDescription = null,
                                        tint = forge.tacticalBlue,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("CONNECT WS", style = MaterialTheme.typography.labelSmall, color = forge.tacticalBlue)
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.extraSmall)
                            .background(MaterialTheme.colorScheme.background)
                            .border(1.dp, forge.borderHairline, MaterialTheme.shapes.extraSmall)
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (workerEvents.isEmpty()) {
                            Text(
                                text = "// No telemetry events yet. Execute an audit or connect to ws://<host>/ws/events.",
                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
                                color = forge.telemetryMuted
                            )
                        } else {
                            val fmt = SimpleDateFormat("HH:mm:ss", Locale.US)
                            workerEvents.takeLast(12).reversed().forEach { evt ->
                                val time = fmt.format(Date(evt.timestamp))
                                Text(
                                    text = "[$time] [${evt.worker}/${evt.eventType}] ${evt.message}",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontFamily = JetBrainsMonoFamily,
                                        fontSize = 10.5.sp,
                                        lineHeight = 14.sp
                                    ),
                                    color = when (evt.eventType) {
                                        "OFFLINE", "TRANSPORT_ERR" -> forge.amber
                                        "HEALTH_OK", "AUDIT_COMPLETE", "PATCH_APPLIED", "CONNECTED" -> forge.verifiedGreen
                                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // Offline Sync & Retry Queue Header
            item(key = "offline_queue_header") {
                ForgePanel {
                    SectionHeaderLabel(
                        title = "Offline Sync & Retry Queue",
                        trailingText = "${queueItems.size} ITEMS"
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { viewModel.retryOfflineQueue() },
                            enabled = !isRetrying && queueItems.any { it.status == "QUEUED" || it.status == "FAILED" },
                            shape = MaterialTheme.shapes.extraSmall,
                            border = BorderStroke(1.dp, forge.amber),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("retry_queue_button")
                        ) {
                            Icon(
                                imageVector = if (isRetrying) Icons.Default.Sync else Icons.Default.Refresh,
                                contentDescription = null,
                                tint = forge.amber,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = stringResource(R.string.control_retry_all).uppercase(),
                                style = MaterialTheme.typography.labelSmall,
                                color = forge.amber
                            )
                        }

                        OutlinedButton(
                            onClick = { viewModel.purgeCompletedQueueItems() },
                            shape = MaterialTheme.shapes.extraSmall,
                            border = BorderStroke(1.dp, forge.borderStrong),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("purge_queue_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.CleaningServices,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = stringResource(R.string.control_clear_queue).uppercase(),
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }

                    if (queueItems.isEmpty()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = stringResource(R.string.control_queue_empty),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            items(queueItems, key = { it.actionId }) { item ->
                SyncQueueItemCard(
                    item = item,
                    onDelete = { viewModel.deleteQueueItem(item.actionId) }
                )
            }
        }
    }
}

@Composable
private fun SyncQueueItemCard(
    item: PendingSyncActionEntity,
    onDelete: () -> Unit
) {
    val forge = ForgeTheme.colors
    val statusColor = when (item.status) {
        "COMPLETED" -> forge.verifiedGreen
        "FAILED" -> forge.criticalRed
        "RETRYING" -> forge.tacticalBlue
        else -> forge.amber
    }

    ForgePanel(accentBorder = statusColor.copy(alpha = 0.55f), contentPadding = 12.dp) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${item.actionId} · ${item.actionType}",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = statusColor
                )
                Text(
                    text = "Branch: ${item.targetBranch} · Retries: ${item.retryCount}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Default.DeleteOutline,
                    contentDescription = "Remove queued sync item",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (!item.lastError.isNullOrBlank()) {
            HorizontalDivider(color = forge.borderHairline, modifier = Modifier.padding(vertical = 6.dp))
            Text(
                text = "Last Error: ${item.lastError}",
                style = MaterialTheme.typography.labelSmall,
                color = forge.amber,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
