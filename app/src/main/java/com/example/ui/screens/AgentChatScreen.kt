package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.core.AudioRecorderHelper
import com.example.data.ai.AgentPersona
import com.example.data.ai.AiModelProfile
import com.example.data.local.ChatMessageEntity
import com.example.ui.components.ForgePanel
import com.example.ui.theme.ForgeTheme
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.viewmodel.ChatStreamState
import com.example.ui.viewmodel.PatchForgeViewModel

@Composable
fun AgentChatScreen(
    viewModel: PatchForgeViewModel,
    modifier: Modifier = Modifier
) {
    val messages by viewModel.chatMessages.collectAsStateWithLifecycle()
    val agentState by viewModel.agentState.collectAsStateWithLifecycle()
    val activeRun by viewModel.activeRun.collectAsStateWithLifecycle()
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val forge = ForgeTheme.colors

    var draft by rememberSaveable { mutableStateOf("") }
    var isRecording by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val audioRecorder = remember(context) { AudioRecorderHelper(context) }

    DisposableEffect(audioRecorder) {
        onDispose {
            audioRecorder.stopAndRelease()
        }
    }

    fun startVoiceCapture() {
        audioRecorder.startRecording()
            .onSuccess {
                isRecording = true
                viewModel.notifyMessage("Recording voice note… tap Stop to transcribe via gemini-3.5-transcribe.")
            }
            .onFailure { err ->
                isRecording = false
                viewModel.notifyMessage("Microphone unavailable: ${err.localizedMessage ?: "error"}")
            }
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startVoiceCapture()
        } else {
            viewModel.notifyMessage("Microphone permission denied. Voice transcription requires RECORD_AUDIO.")
        }
    }

    val isBusy = agentState.streamState is ChatStreamState.Thinking ||
        agentState.streamState is ChatStreamState.Streaming

    LaunchedEffect(messages.size, agentState.streamState) {
        val total = messages.size + if (isBusy) 1 else 0
        if (total > 0) {
            listState.animateScrollToItem(total - 1)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 840.dp)
        ) {
            // Top Industrial Control Bar: Role + Gemini Model Tier + High Thinking Indicator
            Surface(
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, forge.borderHairline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.agent_title).uppercase(),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = stringResource(R.string.agent_subtitle),
                                style = MaterialTheme.typography.labelSmall,
                                color = if (agentState.modelProfile.highThinking) forge.amber else forge.tacticalBlue
                            )
                        }
                        IconButton(
                            onClick = { viewModel.clearChatHistory() },
                            modifier = Modifier.testTag("clear_chat_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteOutline,
                                contentDescription = stringResource(R.string.agent_clear_cd),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Persona Selector Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        AgentPersona.entries.forEach { persona ->
                            val selected = agentState.persona == persona
                            FilterChip(
                                selected = selected,
                                onClick = { viewModel.selectAgentPersona(persona) },
                                label = {
                                    Text(
                                        text = persona.label,
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

                    Spacer(modifier = Modifier.height(4.dp))

                    // Model Tier Selector
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AiModelProfile.entries.forEach { profile ->
                            val selected = agentState.modelProfile == profile
                            FilterChip(
                                selected = selected,
                                onClick = { viewModel.selectAgentModel(profile) },
                                leadingIcon = if (profile.highThinking) {
                                    {
                                        Icon(
                                            imageVector = Icons.Default.Psychology,
                                            contentDescription = null,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                } else null,
                                label = {
                                    Text(
                                        text = profile.badge,
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                },
                                shape = MaterialTheme.shapes.extraSmall,
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = if (profile.highThinking) forge.amberContainer else forge.tacticalBlueContainer,
                                    selectedLabelColor = if (profile.highThinking) forge.amber else forge.tacticalBlue,
                                    selectedLeadingIconColor = forge.amber
                                ),
                                modifier = Modifier.testTag("agent_model_${profile.modelId}")
                            )
                        }

                        FilterChip(
                            selected = agentState.attachActiveAuditContext,
                            onClick = { viewModel.toggleAttachAuditContext(!agentState.attachActiveAuditContext) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.AttachFile,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp)
                                )
                            },
                            label = {
                                Text(
                                    text = if (activeRun != null) "CTX:${activeRun?.runId}" else stringResource(R.string.agent_attach_context),
                                    style = MaterialTheme.typography.labelSmall
                                )
                            },
                            shape = MaterialTheme.shapes.extraSmall,
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = forge.verifiedGreenContainer,
                                selectedLabelColor = forge.verifiedGreen,
                                selectedLeadingIconColor = forge.verifiedGreen
                            )
                        )
                    }
                }
            }

            // Scrollable Multi-Turn Conversation Thread
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (messages.isEmpty() && !isBusy) {
                    item(key = "agent_empty") {
                        ForgePanel {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SmartToy,
                                    contentDescription = null,
                                    tint = forge.amber,
                                    modifier = Modifier.size(30.dp)
                                )
                                Column {
                                    Text(
                                        text = stringResource(R.string.agent_empty_title),
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = stringResource(R.string.agent_empty_body),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            val starters = listOf(
                                "Explain the blast radius of a malicious postinstall script in package.json.",
                                "How do I safely eliminate shell=True command injection in Python git patch runners?",
                                "Audit our active findings and prioritize which patch to merge first."
                            )
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                starters.forEach { prompt ->
                                    Surface(
                                        onClick = { viewModel.sendChatMessage(prompt) },
                                        shape = MaterialTheme.shapes.extraSmall,
                                        color = MaterialTheme.colorScheme.background,
                                        border = BorderStroke(1.dp, forge.borderHairline),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = "> $prompt",
                                            style = MaterialTheme.typography.labelMedium.copy(
                                                fontFamily = JetBrainsMonoFamily
                                            ),
                                            color = forge.tacticalBlue,
                                            modifier = Modifier.padding(10.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                items(messages, key = { it.messageId }) { msg ->
                    ChatMessagePlate(
                        message = msg,
                        onCopy = {
                            clipboard.setText(AnnotatedString(msg.content))
                            viewModel.notifyMessage("Copied agent response to clipboard.")
                        }
                    )
                }

                when (val stream = agentState.streamState) {
                    is ChatStreamState.Thinking -> {
                        item(key = "streaming_thinking") {
                            ForgePanel(accentBorder = forge.amber) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Psychology,
                                        contentDescription = null,
                                        tint = forge.amber,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = if (agentState.modelProfile.highThinking) {
                                            "DEEP REASONING (${agentState.modelProfile.modelId} · ThinkingLevel.HIGH)…"
                                        } else {
                                            "SYNTHESIZING (${agentState.modelProfile.modelId})…"
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = forge.amber
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                LinearProgressIndicator(
                                    modifier = Modifier.fillMaxWidth(),
                                    color = forge.amber,
                                    trackColor = forge.borderHairline
                                )
                            }
                        }
                    }
                    is ChatStreamState.Streaming -> {
                        item(key = "streaming_live") {
                            ForgePanel(elevated = true, accentBorder = forge.tacticalBlue) {
                                Text(
                                    text = "${agentState.persona.label.uppercase()} · STREAMING (${agentState.modelProfile.badge})",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = forge.tacticalBlue
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = stream.partialText,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                    is ChatStreamState.Error -> {
                        item(key = "chat_error") {
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
                                    contentDescription = "Chat error",
                                    tint = forge.criticalRed,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = stream.message,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                    }
                    ChatStreamState.Idle -> Unit
                }
            }

            // Live Audio Recording / Transcription Status Banner
            if (isRecording || agentState.isTranscribingAudio) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(if (isRecording) forge.criticalRedContainer else forge.tacticalBlueContainer)
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = if (isRecording) {
                            "REC ● CAPTURING VOICE AUDIO — TAP STOP TO TRANSCRIBE"
                        } else {
                            "TRANSCRIBING AUDIO VIA GEMINI-3.5-TRANSCRIBE…"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isRecording) forge.criticalRed else forge.tacticalBlue
                    )
                    if (agentState.isTranscribingAudio) {
                        Spacer(modifier = Modifier.height(4.dp))
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth(),
                            color = forge.tacticalBlue,
                            trackColor = forge.borderHairline
                        )
                    }
                }
            }

            // Bottom Operator Command Input Bar with Voice Transcription Mic + Send
            HorizontalDivider(color = forge.borderHairline)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    onClick = {
                        if (isRecording) {
                            isRecording = false
                            audioRecorder.stopAndReadBytes()
                                .onSuccess { bytes ->
                                    viewModel.transcribeVoiceClip(bytes) { transcript ->
                                        draft = if (draft.isBlank()) transcript else "$draft $transcript"
                                    }
                                }
                                .onFailure { err ->
                                    viewModel.notifyMessage(err.localizedMessage ?: "Audio capture failed.")
                                }
                        } else {
                            val hasPermission = ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.RECORD_AUDIO
                            ) == PackageManager.PERMISSION_GRANTED
                            if (hasPermission) {
                                startVoiceCapture()
                            } else {
                                micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        }
                    },
                    enabled = !agentState.isTranscribingAudio,
                    shape = MaterialTheme.shapes.extraSmall,
                    color = if (isRecording) forge.criticalRed else MaterialTheme.colorScheme.background,
                    border = BorderStroke(
                        1.dp,
                        if (isRecording) forge.criticalRed else forge.tacticalBlue
                    ),
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("chat_voice_record_button")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (isRecording) Icons.Default.Stop else Icons.Default.Mic,
                            contentDescription = if (isRecording) {
                                stringResource(R.string.agent_voice_stop_cd)
                            } else {
                                stringResource(R.string.agent_voice_record_cd)
                            },
                            tint = if (isRecording) MaterialTheme.colorScheme.onError else forge.tacticalBlue,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    placeholder = {
                        Text(
                            text = stringResource(R.string.agent_input_placeholder),
                            style = MaterialTheme.typography.bodySmall
                        )
                    },
                    maxLines = 4,
                    shape = MaterialTheme.shapes.extraSmall,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = forge.amber,
                        unfocusedBorderColor = forge.borderHairline,
                        focusedContainerColor = MaterialTheme.colorScheme.background,
                        unfocusedContainerColor = MaterialTheme.colorScheme.background
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("chat_input")
                )

                Surface(
                    onClick = {
                        if (!isBusy && draft.isNotBlank()) {
                            val toSend = draft
                            draft = ""
                            viewModel.sendChatMessage(toSend)
                        }
                    },
                    enabled = !isBusy && draft.isNotBlank(),
                    shape = MaterialTheme.shapes.extraSmall,
                    color = if (!isBusy && draft.isNotBlank()) forge.amber else forge.borderHairline,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("chat_send_button")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = stringResource(R.string.agent_send_cd),
                            tint = if (!isBusy && draft.isNotBlank()) {
                                MaterialTheme.colorScheme.onPrimary
                            } else {
                                forge.telemetryMuted
                            },
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatMessagePlate(
    message: ChatMessageEntity,
    onCopy: () -> Unit
) {
    val forge = ForgeTheme.colors
    val isUser = message.role == "user"

    ForgePanel(
        elevated = !isUser,
        accentBorder = if (isUser) forge.borderStrong else forge.tacticalBlue.copy(alpha = 0.65f),
        contentPadding = 12.dp
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = if (isUser) "OPERATOR" else message.agentPersona.uppercase(),
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = if (isUser) forge.amber else forge.tacticalBlue
                )
                if (!isUser) {
                    Text(
                        text = "// ${message.modelId}${if (message.highThinkingUsed) " [HIGH THINK]" else ""}",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (message.highThinkingUsed) forge.amber else forge.telemetryMuted
                    )
                }
            }

            if (!isUser) {
                IconButton(
                    onClick = onCopy,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Copy response",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = message.content,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
