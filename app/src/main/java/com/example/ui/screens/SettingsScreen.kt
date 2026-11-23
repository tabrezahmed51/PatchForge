package com.example.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Sync
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.core.AudioRecorderHelper
import com.example.data.ai.AiModelProfile
import com.example.data.local.AiApiIntegrationConfig
import com.example.data.local.PendingSyncActionEntity
import com.example.data.local.ThirdPartyAiVendor
import com.example.data.remote.WsConnectionState
import com.example.ui.components.ForgePanel
import com.example.ui.components.SectionHeaderLabel
import com.example.ui.components.StatusLed
import com.example.ui.components.bringIntoViewOnImeFocus
import com.example.ui.theme.ForgeTheme
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.viewmodel.BackendHealthState
import com.example.ui.viewmodel.CloudSyncUiState
import com.example.ui.viewmodel.MultimodalState
import com.example.ui.viewmodel.PatchForgeViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max

@Composable
fun SettingsScreen(
    viewModel: PatchForgeViewModel,
    userDisplayName: String,
    userEmail: String?,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    val forge = ForgeTheme.colors

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
            // Settings Header + 2-Tab Industrial Switcher
            Surface(
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, forge.borderHairline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Text(
                        text = stringResource(R.string.settings_title).uppercase(),
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(R.string.settings_subtitle),
                        style = MaterialTheme.typography.labelSmall,
                        color = forge.amber
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SettingsTabButton(
                            label = stringResource(R.string.settings_tab_ai),
                            selected = selectedTab == 0,
                            onClick = { selectedTab = 0 },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("tab_ai_multimodal")
                        )
                        SettingsTabButton(
                            label = stringResource(R.string.settings_tab_backend),
                            selected = selectedTab == 1,
                            onClick = { selectedTab = 1 },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("tab_backend_sync")
                        )
                    }
                }
            }

            if (selectedTab == 0) {
                AiMultimodalSettingsTab(viewModel = viewModel)
            } else {
                BackendSyncSettingsTab(
                    viewModel = viewModel,
                    userDisplayName = userDisplayName,
                    userEmail = userEmail,
                    onSignOut = onSignOut
                )
            }
        }
    }
}

@Composable
private fun SettingsTabButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val forge = ForgeTheme.colors
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.extraSmall,
        color = if (selected) forge.amberContainer else MaterialTheme.colorScheme.background,
        border = BorderStroke(1.dp, if (selected) forge.amber else forge.borderHairline),
        modifier = modifier.height(48.dp)
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 8.dp)) {
            Text(
                text = label.uppercase(),
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = if (selected) forge.amber else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun AiMultimodalSettingsTab(viewModel: PatchForgeViewModel) {
    val savedAiConfig by viewModel.aiIntegrationConfig.collectAsStateWithLifecycle()
    val multimodalState by viewModel.multimodalState.collectAsStateWithLifecycle()
    val agentState by viewModel.agentState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val forge = ForgeTheme.colors

    var activeVendorId by rememberSaveable { mutableStateOf(savedAiConfig.activeVendorId) }
    var openAiKey by rememberSaveable { mutableStateOf(savedAiConfig.openAiApiKey) }
    var openAiModel by rememberSaveable { mutableStateOf(savedAiConfig.openAiModel) }
    var anthropicKey by rememberSaveable { mutableStateOf(savedAiConfig.anthropicApiKey) }
    var anthropicModel by rememberSaveable { mutableStateOf(savedAiConfig.anthropicModel) }
    var customBaseUrl by rememberSaveable { mutableStateOf(savedAiConfig.customBaseUrl) }
    var customKey by rememberSaveable { mutableStateOf(savedAiConfig.customApiKey) }
    var customModel by rememberSaveable { mutableStateOf(savedAiConfig.customModel) }
    var showApiKeys by rememberSaveable { mutableStateOf(false) }

    var multimodalPrompt by rememberSaveable {
        mutableStateOf("Audit this diagram or screenshot for security risks.")
    }
    var selectedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isRecording by remember { mutableStateOf(false) }

    val audioRecorder = remember(context) { AudioRecorderHelper(context) }
    DisposableEffect(audioRecorder) {
        onDispose { audioRecorder.stopAndRelease() }
    }

    LaunchedEffect(savedAiConfig) {
        activeVendorId = savedAiConfig.activeVendorId
        openAiKey = savedAiConfig.openAiApiKey
        openAiModel = savedAiConfig.openAiModel
        anthropicKey = savedAiConfig.anthropicApiKey
        anthropicModel = savedAiConfig.anthropicModel
        customBaseUrl = savedAiConfig.customBaseUrl
        customKey = savedAiConfig.customApiKey
        customModel = savedAiConfig.customModel
    }

    // Zero-permission Android Photo Picker (Google Play compliant)
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            selectedBitmap = decodeScaledBitmap(context, uri, maxDim = 1024)
            if (selectedBitmap != null) {
                viewModel.notifyMessage("Attached image to Multimodal Workbench.")
            } else {
                viewModel.notifyMessage("Could not decode selected image.")
            }
        }
    }

    fun startVoiceDictation() {
        audioRecorder.startRecording()
            .onSuccess {
                isRecording = true
                viewModel.notifyMessage("Listening… tap Stop to transcribe via gemini-3.5-transcribe.")
            }
            .onFailure { err ->
                isRecording = false
                viewModel.notifyMessage("Mic unavailable: ${err.localizedMessage ?: "error"}")
            }
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startVoiceDictation()
        } else {
            viewModel.notifyMessage("Microphone permission denied.")
        }
    }

    val activeVendor = ThirdPartyAiVendor.entries.firstOrNull { it.id == activeVendorId }
        ?: ThirdPartyAiVendor.NATIVE_GEMINI

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .imePadding(),
        contentPadding = PaddingValues(start = 16.dp, top = 14.dp, end = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 3rd-Party AI API Key Configuration Panel
        item(key = "ai_api_keys_panel") {
            ForgePanel(elevated = true, accentBorder = forge.borderStrong) {
                SectionHeaderLabel(
                    title = "AI API Provider & Keys",
                    trailingText = activeVendor.label.uppercase()
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Native Gemini runs keyless via Firebase AI Logic. Optional keys route 3rd-party models locally.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ThirdPartyAiVendor.entries.forEach { vendor ->
                        val selected = activeVendorId == vendor.id
                        FilterChip(
                            selected = selected,
                            onClick = { activeVendorId = vendor.id },
                            label = {
                                Text(text = vendor.label, style = MaterialTheme.typography.labelSmall)
                            },
                            shape = MaterialTheme.shapes.extraSmall,
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = forge.amberContainer,
                                selectedLabelColor = forge.amber
                            ),
                            modifier = Modifier.testTag("vendor_${vendor.id.lowercase()}")
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                when (activeVendor) {
                    ThirdPartyAiVendor.NATIVE_GEMINI -> {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(MaterialTheme.shapes.extraSmall)
                                .background(forge.verifiedGreenContainer.copy(alpha = 0.55f))
                                .border(1.dp, forge.verifiedGreen, MaterialTheme.shapes.extraSmall)
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = forge.verifiedGreen,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Keyless Firebase AI Logic · 4-Carbon, 4-Hydrogen & Voice Transcribe.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    ThirdPartyAiVendor.OPENAI -> {
                        ApiKeyField(
                            value = openAiKey,
                            onValueChange = { openAiKey = it },
                            label = "OpenAI API Key (sk-...)",
                            visible = showApiKeys,
                            onToggleVisibility = { showApiKeys = !showApiKeys },
                            tag = "openai_key_input"
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        PlainMonoField(
                            value = openAiModel,
                            onValueChange = { openAiModel = it },
                            label = "OpenAI Multimodal Model ID",
                            tag = "openai_model_input"
                        )
                    }

                    ThirdPartyAiVendor.ANTHROPIC -> {
                        ApiKeyField(
                            value = anthropicKey,
                            onValueChange = { anthropicKey = it },
                            label = "Anthropic API Key (sk-ant-...)",
                            visible = showApiKeys,
                            onToggleVisibility = { showApiKeys = !showApiKeys },
                            tag = "anthropic_key_input"
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        PlainMonoField(
                            value = anthropicModel,
                            onValueChange = { anthropicModel = it },
                            label = "Anthropic Claude Model ID",
                            tag = "anthropic_model_input"
                        )
                    }

                    ThirdPartyAiVendor.OPENROUTER -> {
                        PlainMonoField(
                            value = customBaseUrl,
                            onValueChange = { customBaseUrl = it },
                            label = "OpenAI-Compatible Base URL",
                            tag = "custom_base_url_input"
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        ApiKeyField(
                            value = customKey,
                            onValueChange = { customKey = it },
                            label = "OpenRouter / Custom API Key",
                            visible = showApiKeys,
                            onToggleVisibility = { showApiKeys = !showApiKeys },
                            tag = "custom_key_input"
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        PlainMonoField(
                            value = customModel,
                            onValueChange = { customModel = it },
                            label = "Model Identifier",
                            tag = "custom_model_input"
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            viewModel.saveAiApiConfig(
                                AiApiIntegrationConfig(
                                    activeVendorId = activeVendorId,
                                    openAiApiKey = openAiKey,
                                    openAiModel = openAiModel,
                                    anthropicApiKey = anthropicKey,
                                    anthropicModel = anthropicModel,
                                    customBaseUrl = customBaseUrl,
                                    customApiKey = customKey,
                                    customModel = customModel
                                )
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
                            .testTag("save_ai_config_button")
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.settings_save_ai).uppercase(),
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                        )
                    }

                    OutlinedButton(
                        onClick = { viewModel.clearThirdPartyApiKeys() },
                        shape = MaterialTheme.shapes.extraSmall,
                        border = BorderStroke(1.dp, forge.borderStrong),
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("clear_ai_keys_button")
                    ) {
                        Icon(Icons.Default.Key, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = "CLEAR", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }

        // Live Multimodal Workbench (Text + Photo Picker Image + Mic Transcription)
        item(key = "multimodal_workbench") {
            ForgePanel(accentBorder = forge.tacticalBlue.copy(alpha = 0.55f)) {
                SectionHeaderLabel(
                    title = "Live Multimodal Workbench",
                    trailingText = "TEXT · VISION · VOICE"
                )
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        shape = MaterialTheme.shapes.extraSmall,
                        border = BorderStroke(1.dp, forge.tacticalBlue),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("pick_multimodal_image_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.AddPhotoAlternate,
                            contentDescription = "Attach image",
                            tint = forge.tacticalBlue,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (selectedBitmap != null) "CHANGE IMAGE" else "ATTACH IMAGE",
                            style = MaterialTheme.typography.labelSmall,
                            color = forge.tacticalBlue
                        )
                    }

                    OutlinedButton(
                        onClick = {
                            if (isRecording) {
                                isRecording = false
                                audioRecorder.stopAndReadBytes()
                                    .onSuccess { bytes ->
                                        viewModel.transcribeVoiceClip(bytes) { transcript ->
                                            multimodalPrompt = transcript
                                        }
                                    }
                                    .onFailure { err ->
                                        viewModel.notifyMessage(err.localizedMessage ?: "Audio capture failed.")
                                    }
                            } else {
                                val hasPerm = ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.RECORD_AUDIO
                                ) == PackageManager.PERMISSION_GRANTED
                                if (hasPerm) {
                                    startVoiceDictation()
                                } else {
                                    micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                }
                            }
                        },
                        enabled = !agentState.isTranscribingAudio,
                        shape = MaterialTheme.shapes.extraSmall,
                        border = BorderStroke(1.dp, if (isRecording) forge.criticalRed else forge.amber),
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("multimodal_voice_button")
                    ) {
                        Icon(
                            imageVector = if (isRecording) Icons.Default.Stop else Icons.Default.Mic,
                            contentDescription = "Dictate prompt",
                            tint = if (isRecording) forge.criticalRed else forge.amber,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (isRecording) "STOP" else "VOICE",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isRecording) forge.criticalRed else forge.amber
                        )
                    }
                }

                selectedBitmap?.let { bmp ->
                    Spacer(modifier = Modifier.height(10.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp)
                            .clip(MaterialTheme.shapes.extraSmall)
                            .border(1.dp, forge.borderStrong, MaterialTheme.shapes.extraSmall)
                    ) {
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = "Selected multimodal image",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                        Surface(
                            onClick = { selectedBitmap = null },
                            shape = MaterialTheme.shapes.extraSmall,
                            color = MaterialTheme.colorScheme.background.copy(alpha = 0.85f),
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(6.dp)
                                .size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Remove image",
                                    tint = MaterialTheme.colorScheme.onBackground,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = multimodalPrompt,
                    onValueChange = { multimodalPrompt = it },
                    label = { Text("Multimodal Inspection Prompt", style = MaterialTheme.typography.labelSmall) },
                    minLines = 3,
                    maxLines = 5,
                    shape = MaterialTheme.shapes.extraSmall,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = forge.amber,
                        unfocusedBorderColor = forge.borderHairline,
                        focusedContainerColor = MaterialTheme.colorScheme.background,
                        unfocusedContainerColor = MaterialTheme.colorScheme.background
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .bringIntoViewOnImeFocus(extraBottomPadding = 24.dp)
                        .testTag("multimodal_prompt_input")
                )

                Spacer(modifier = Modifier.height(10.dp))

                Button(
                    onClick = {
                        viewModel.runMultimodalWorkbench(
                            prompt = multimodalPrompt,
                            image = selectedBitmap,
                            modelProfile = AiModelProfile.HYDROGEN_GENERAL
                        )
                    },
                    enabled = multimodalState !is MultimodalState.Running,
                    shape = MaterialTheme.shapes.extraSmall,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = forge.tacticalBlue,
                        contentColor = MaterialTheme.colorScheme.onSecondary
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("run_multimodal_button")
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "${stringResource(R.string.settings_run_multimodal).uppercase()} (${activeVendor.label.uppercase()})",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                when (val state = multimodalState) {
                    MultimodalState.Idle -> Unit
                    MultimodalState.Running -> {
                        Spacer(modifier = Modifier.height(10.dp))
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth(),
                            color = forge.tacticalBlue,
                            trackColor = forge.borderHairline
                        )
                    }
                    is MultimodalState.Error -> {
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
                                contentDescription = "Multimodal error",
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
                    is MultimodalState.Success -> {
                        Spacer(modifier = Modifier.height(10.dp))
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(MaterialTheme.shapes.extraSmall)
                                .background(MaterialTheme.colorScheme.background)
                                .border(1.dp, forge.tacticalBlue.copy(alpha = 0.6f), MaterialTheme.shapes.extraSmall)
                                .padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "PROVIDER // ${state.providerLabel.uppercase()}",
                                style = MaterialTheme.typography.labelSmall,
                                color = forge.tacticalBlue
                            )
                            Text(
                                text = state.output,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ApiKeyField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    visible: Boolean,
    onToggleVisibility: () -> Unit,
    tag: String
) {
    val forge = ForgeTheme.colors
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(text = label, style = MaterialTheme.typography.labelSmall) },
        singleLine = true,
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = {
            IconButton(onClick = onToggleVisibility) {
                Icon(
                    imageVector = if (visible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                    contentDescription = if (visible) "Hide API key" else "Show API key"
                )
            }
        },
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
            .bringIntoViewOnImeFocus(extraBottomPadding = 24.dp)
            .testTag(tag)
    )
}

@Composable
private fun PlainMonoField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    tag: String
) {
    val forge = ForgeTheme.colors
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(text = label, style = MaterialTheme.typography.labelSmall) },
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
            .bringIntoViewOnImeFocus(extraBottomPadding = 24.dp)
            .testTag(tag)
    )
}

@Composable
private fun BackendSyncSettingsTab(
    viewModel: PatchForgeViewModel,
    userDisplayName: String,
    userEmail: String?,
    onSignOut: () -> Unit
) {
    val config by viewModel.backendConfig.collectAsStateWithLifecycle()
    val healthState by viewModel.healthState.collectAsStateWithLifecycle()
    val wsState by viewModel.wsConnectionState.collectAsStateWithLifecycle()
    val workerEvents by viewModel.workerEvents.collectAsStateWithLifecycle()
    val queueItems by viewModel.pendingSyncActions.collectAsStateWithLifecycle()
    val isRetrying by viewModel.isRetryingQueue.collectAsStateWithLifecycle()
    val cloudRunsState by viewModel.cloudRunsState.collectAsStateWithLifecycle()
    val cloudFindingsState by viewModel.cloudFindingsState.collectAsStateWithLifecycle()
    val forge = ForgeTheme.colors

    var baseUrl by rememberSaveable { mutableStateOf(config.baseUrl) }
    var bearerToken by rememberSaveable { mutableStateOf(config.bearerToken) }
    var defaultBranch by rememberSaveable { mutableStateOf(config.defaultTargetBranch) }
    var orchestratorMode by rememberSaveable { mutableStateOf(config.orchestratorMode) }

    LaunchedEffect(config) {
        if (baseUrl.isBlank()) baseUrl = config.baseUrl
        if (defaultBranch.isBlank()) defaultBranch = config.defaultTargetBranch
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .imePadding(),
        contentPadding = PaddingValues(start = 16.dp, top = 14.dp, end = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Operator Session & Cloud Firestore Sync Status
        item(key = "operator_cloud_sync") {
            ForgePanel(elevated = true, accentBorder = forge.verifiedGreen.copy(alpha = 0.55f)) {
                SectionHeaderLabel(
                    title = "Operator Session & Firestore Sync",
                    trailingText = "AUTHENTICATED"
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = userDisplayName,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (!userEmail.isNullOrBlank()) {
                            Text(
                                text = userEmail,
                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
                                color = forge.tacticalBlue
                            )
                        }
                    }
                    OutlinedButton(
                        onClick = onSignOut,
                        shape = MaterialTheme.shapes.extraSmall,
                        border = BorderStroke(1.dp, forge.criticalRed),
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("sign_out_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Logout,
                            contentDescription = "Sign out",
                            tint = forge.criticalRed,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.settings_sign_out).uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            color = forge.criticalRed
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(color = forge.borderHairline)
                Spacer(modifier = Modifier.height(8.dp))

                val runsSummary = when (val s = cloudRunsState) {
                    CloudSyncUiState.Loading -> "Syncing runs…"
                    is CloudSyncUiState.Success -> "${s.data.size} cloud runs"
                    is CloudSyncUiState.Error -> "Runs offline (${s.message})"
                }
                val findingsSummary = when (val s = cloudFindingsState) {
                    CloudSyncUiState.Loading -> "Syncing findings…"
                    is CloudSyncUiState.Success -> "${s.data.size} cloud findings"
                    is CloudSyncUiState.Error -> "Findings offline"
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CloudDone,
                        contentDescription = null,
                        tint = forge.verifiedGreen,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "$runsSummary · $findingsSummary (Scoped to your Google UID)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // FastAPI REST Control Plane Card
        item(key = "fastapi_config_panel") {
            ForgePanel(elevated = false, accentBorder = forge.borderStrong) {
                SectionHeaderLabel(
                    title = stringResource(R.string.control_title),
                    trailingText = "REST / POSTGRESQL / CELERY"
                )
                Spacer(modifier = Modifier.height(10.dp))

                val (statusLabel, statusColor, statusDetail) = when (val h = healthState) {
                    BackendHealthState.Unchecked -> Triple(
                        "UNPROBED",
                        forge.telemetryMuted,
                        "Probe /health to check FastAPI, DB & workers."
                    )
                    BackendHealthState.Probing -> Triple(
                        "PROBING…",
                        forge.amber,
                        "Contacting $baseUrl/health"
                    )
                    is BackendHealthState.Online -> Triple(
                        "ONLINE · ${h.health.workerBackend.uppercase()}",
                        forge.verifiedGreen,
                        "${h.health.service} · Workers ${h.health.activeWorkers} · DB ${if (h.health.dbConnected) "OK" else "DOWN"}"
                    )
                    is BackendHealthState.Offline -> Triple(
                        "OFFLINE (ROOM + FIRESTORE)",
                        forge.amber,
                        "Offline (${h.reason}). Updates buffered in Room queue."
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

                Spacer(modifier = Modifier.height(10.dp))

                PlainMonoField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it },
                    label = stringResource(R.string.control_endpoint_label),
                    tag = "fastapi_url_input"
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
                        .bringIntoViewOnImeFocus(extraBottomPadding = 24.dp)
                        .testTag("fastapi_token_input")
                )

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = orchestratorMode == "NATIVE_GEMINI",
                        onClick = { orchestratorMode = "NATIVE_GEMINI" },
                        label = { Text("Native Gemini", style = MaterialTheme.typography.labelSmall) },
                        shape = MaterialTheme.shapes.extraSmall,
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = orchestratorMode == "FASTAPI_HYBRID",
                        onClick = { orchestratorMode = "FASTAPI_HYBRID" },
                        label = { Text("FastAPI Hybrid Sync", style = MaterialTheme.typography.labelSmall) },
                        shape = MaterialTheme.shapes.extraSmall,
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
                    Icon(Icons.Default.Dns, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.control_save_probe).uppercase(),
                        style = MaterialTheme.typography.labelLarge
                    )
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
                    if (isConnected) {
                        OutlinedButton(
                            onClick = { viewModel.disconnectWorkerStream() },
                            shape = MaterialTheme.shapes.extraSmall,
                            border = BorderStroke(1.dp, forge.criticalRed),
                            modifier = Modifier.testTag("ws_disconnect_button")
                        ) {
                            Icon(Icons.Default.LinkOff, contentDescription = null, tint = forge.criticalRed, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("DISCONNECT", style = MaterialTheme.typography.labelSmall, color = forge.criticalRed)
                        }
                    } else {
                        OutlinedButton(
                            onClick = { viewModel.connectWorkerStream() },
                            shape = MaterialTheme.shapes.extraSmall,
                            border = BorderStroke(1.dp, forge.tacticalBlue),
                            modifier = Modifier.testTag("ws_connect_button")
                        ) {
                            Icon(Icons.Default.Link, contentDescription = null, tint = forge.tacticalBlue, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("CONNECT WS", style = MaterialTheme.typography.labelSmall, color = forge.tacticalBlue)
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
                            text = "// No events yet. Connect to ws://<host>/ws/events.",
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
            SyncQueueItemRow(
                item = item,
                onDelete = { viewModel.deleteQueueItem(item.actionId) }
            )
        }
    }
}

@Composable
private fun SyncQueueItemRow(
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

private fun decodeScaledBitmap(context: Context, uri: Uri, maxDim: Int): Bitmap? {
    return try {
        val raw = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                decoder.isMutableRequired = false
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        } else {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream)
            }
        } ?: return null

        val largest = max(raw.width, raw.height)
        if (largest <= maxDim) return raw
        val scale = maxDim.toFloat() / largest.toFloat()
        Bitmap.createScaledBitmap(
            raw,
            (raw.width * scale).toInt().coerceAtLeast(1),
            (raw.height * scale).toInt().coerceAtLeast(1),
            true
        )
    } catch (_: Exception) {
        null
    }
}
