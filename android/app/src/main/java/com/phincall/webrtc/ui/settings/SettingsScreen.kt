package com.phincall.webrtc.ui.settings

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.avaya.ocs.Services.Device.Video.Enums.VideoCaptureOrientation
import com.avaya.ocs.Services.Device.Video.Enums.VideoCapturePreference
import com.avaya.ocs.Services.Work.Enums.PlatformType
import com.phincall.webrtc.config.LocalVideoSurfacePreset
import com.phincall.webrtc.config.RoutingStrategyOption
import com.phincall.webrtc.ui.theme.BorderHairline
import com.phincall.webrtc.ui.theme.BorderInteractive
import com.phincall.webrtc.ui.theme.CanvasLight
import com.phincall.webrtc.ui.theme.InkOnDarkMuted
import com.phincall.webrtc.ui.theme.InkPrimary
import com.phincall.webrtc.ui.theme.InkSecondary
import com.phincall.webrtc.ui.theme.PhinCallTheme
import com.phincall.webrtc.ui.theme.SlateNavy
import com.phincall.webrtc.ui.theme.StatusPoorLight
import com.phincall.webrtc.ui.theme.SurfaceLight
import com.phincall.webrtc.ui.theme.SurfaceSubtle

private val CAPTURE_RESOLUTIONS = listOf(
    VideoCapturePreference.VideoCapturePreference_Min,
    VideoCapturePreference.VideoCapturePreference_270p,
    VideoCapturePreference.VideoCapturePreference_360p,
    VideoCapturePreference.VideoCapturePreference_540p,
    VideoCapturePreference.VideoCapturePreference_Max
)

private val CAPTURE_ORIENTATIONS = listOf(
    VideoCaptureOrientation.VideoCaptureOrientation_LandscapeOrPortrait,
    VideoCaptureOrientation.VideoCaptureOrientation_LandscapeOnly
)

@Composable
fun SettingsRoute(
    viewModel: SettingsViewModel,
    onNavigateBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    BackHandler {
        viewModel.saveDraftOnly()
        onNavigateBack()
    }

    SettingsScreen(
        state = state,
        onBack = {
            viewModel.saveDraftOnly()
            onNavigateBack()
        },
        onSwitchPlatform = viewModel::switchPlatform,
        onResetDefaults = viewModel::resetCurrentPlatform,
        onUpdateForm = viewModel::updateForm,
        onSendLogs = {
            val intent = viewModel.createSendLogsIntent()
            if (intent != null) {
                context.startActivity(Intent.createChooser(intent, "Send Logs"))
            }
        },
        onSaveAndApply = {
            if (viewModel.saveAndValidate()) {
                onNavigateBack()
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    state: SettingsFormState,
    onBack: () -> Unit,
    onSwitchPlatform: (PlatformType) -> Unit,
    onResetDefaults: () -> Unit,
    onUpdateForm: ((SettingsFormState) -> SettingsFormState) -> Unit,
    onSendLogs: () -> Unit,
    onSaveAndApply: () -> Unit
) {
    val isOceana = state.platformType == PlatformType.OCEANA
    var apiKeyVisible by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = CanvasLight,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            Surface(color = SlateNavy, modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(
                            WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
                        )
                        .padding(horizontal = 8.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Navigate back",
                            tint = Color.White
                        )
                    }
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 4.dp)
                    ) {
                        Text(
                            text = "Runtime Settings",
                            color = Color.White,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (isOceana) "Active profile: Avaya Oceana" else "Active profile: Avaya Aura Elite",
                            color = InkOnDarkMuted,
                            fontSize = 12.sp
                        )
                    }
                    OutlinedButton(
                        onClick = onResetDefaults,
                        border = BorderStroke(1.dp, InkOnDarkMuted),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .height(44.dp)
                            .padding(end = 6.dp)
                    ) {
                        Text("Reset", color = Color.White, fontSize = 13.sp)
                    }
                }
            }
        },
        bottomBar = {
            Surface(
                color = SurfaceLight,
                shadowElevation = 6.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .imePadding()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Button(
                        onClick = onSaveAndApply,
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 640.dp)
                            .height(52.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = SlateNavy,
                            contentColor = Color.White
                        )
                    ) {
                        Text(
                            text = "Save and Apply Runtime Config",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 640.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Runtime platform switcher
                SettingsCard(title = "Contact Center Platform") {
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        SegmentedButton(
                            selected = isOceana,
                            onClick = { onSwitchPlatform(PlatformType.OCEANA) },
                            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                            colors = SegmentedButtonDefaults.colors(
                                activeContainerColor = SlateNavy,
                                activeContentColor = Color.White,
                                inactiveContainerColor = SurfaceLight,
                                inactiveContentColor = InkPrimary
                            )
                        ) {
                            Text("Avaya Oceana")
                        }
                        SegmentedButton(
                            selected = !isOceana,
                            onClick = { onSwitchPlatform(PlatformType.ELITE) },
                            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                            colors = SegmentedButtonDefaults.colors(
                                activeContainerColor = SlateNavy,
                                activeContentColor = Color.White,
                                inactiveContainerColor = SurfaceLight,
                                inactiveContentColor = InkPrimary
                            )
                        ) {
                            Text("Avaya Aura Elite")
                        }
                    }

                    Text(
                        text = if (isOceana) {
                            "Oceana runtime: connects to AMC cluster and AAWG, submits Work Services with skill attributes, and supports optional destination override."
                        } else {
                            "Elite runtime: connects directly via AAWG to Destination Address. AMC cluster and Oceana skill attributes are hidden and skipped at runtime."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = InkSecondary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(SurfaceSubtle, RoundedCornerShape(8.dp))
                            .padding(10.dp)
                    )
                }

                // AMC Cluster (Oceana only)
                AnimatedVisibility(visible = isOceana) {
                    SettingsCard(title = "AMC Cluster (Oceana only)") {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SettingsTextField(
                                value = state.amcServer,
                                onValueChange = { v -> onUpdateForm { it.copy(amcServer = v) } },
                                label = "AMC Cluster Address (Required)",
                                modifier = Modifier.weight(2f)
                            )
                            SettingsTextField(
                                value = state.amcPort,
                                onValueChange = { v -> onUpdateForm { it.copy(amcPort = v) } },
                                label = "Port",
                                keyboardType = KeyboardType.Number,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        SettingsTextField(
                            value = state.amcUrlPath,
                            onValueChange = { v -> onUpdateForm { it.copy(amcUrlPath = v) } },
                            label = "AMC URL Path"
                        )
                        SettingsSwitchRow(
                            label = "Use HTTPS",
                            checked = state.amcHttps,
                            onCheckedChange = { c -> onUpdateForm { it.copy(amcHttps = c) } }
                        )
                    }
                }

                // AAWG Configuration
                SettingsCard(title = "AAWG Configuration") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SettingsTextField(
                            value = state.aawgServer,
                            onValueChange = { v -> onUpdateForm { it.copy(aawgServer = v) } },
                            label = "AAWG Server Address (Required)",
                            modifier = Modifier.weight(2f)
                        )
                        SettingsTextField(
                            value = state.aawgPort,
                            onValueChange = { v -> onUpdateForm { it.copy(aawgPort = v) } },
                            label = "Port",
                            keyboardType = KeyboardType.Number,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    SettingsTextField(
                        value = state.aawgUrlPath,
                        onValueChange = { v -> onUpdateForm { it.copy(aawgUrlPath = v) } },
                        label = "AAWG URL Path"
                    )
                    SettingsSwitchRow(
                        label = "Use HTTPS",
                        checked = state.aawgHttps,
                        onCheckedChange = { c -> onUpdateForm { it.copy(aawgHttps = c) } }
                    )
                }

                // Token Service Configuration
                SettingsCard(title = "Token Service Configuration") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SettingsTextField(
                            value = state.tokenServer,
                            onValueChange = { v -> onUpdateForm { it.copy(tokenServer = v) } },
                            label = "Token Service Address (Required)",
                            modifier = Modifier.weight(2f)
                        )
                        SettingsTextField(
                            value = state.tokenPort,
                            onValueChange = { v -> onUpdateForm { it.copy(tokenPort = v) } },
                            label = "Port",
                            keyboardType = KeyboardType.Number,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    SettingsTextField(
                        value = state.tokenPath,
                        onValueChange = { v -> onUpdateForm { it.copy(tokenPath = v) } },
                        label = "Token Service URL Path"
                    )
                    OutlinedTextField(
                        value = state.tokenApiKey,
                        onValueChange = { v -> onUpdateForm { it.copy(tokenApiKey = v) } },
                        label = { Text("Proxy Client API Key (in-memory only)") },
                        singleLine = true,
                        visualTransformation = if (apiKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { apiKeyVisible = !apiKeyVisible }) {
                                Icon(
                                    imageVector = if (apiKeyVisible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                    contentDescription = if (apiKeyVisible) "Hide API key" else "Show API key"
                                )
                            }
                        },
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    SettingsSwitchRow(
                        label = "Use HTTPS",
                        checked = state.tokenHttps,
                        onCheckedChange = { c -> onUpdateForm { it.copy(tokenHttps = c) } }
                    )
                }

                // Caller and Interaction Details
                SettingsCard(title = "Caller and Interaction Details") {
                    SettingsTextField(
                        value = state.displayName,
                        onValueChange = { v -> onUpdateForm { it.copy(displayName = v) } },
                        label = "Display Name"
                    )
                    SettingsTextField(
                        value = state.callingNumber,
                        onValueChange = { v -> onUpdateForm { it.copy(callingNumber = v) } },
                        label = "From Address (Unique Customer ID)"
                    )
                    SettingsTextField(
                        value = state.destination,
                        onValueChange = { v -> onUpdateForm { it.copy(destination = v) } },
                        label = if (isOceana) {
                            "Destination Address (Optional for Oceana)"
                        } else {
                            "Destination Address (Required for Elite)"
                        }
                    )
                    SettingsTextField(
                        value = state.contextId,
                        onValueChange = { v -> onUpdateForm { it.copy(contextId = v) } },
                        label = "Context (Agent Screen Pop)"
                    )
                    SettingsTextField(
                        value = state.topic,
                        onValueChange = { v -> onUpdateForm { it.copy(topic = v) } },
                        label = "Topic (Customer Journey)"
                    )
                }

                // Work API Service Settings
                SettingsCard(title = "Service (Work API)") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SettingsTextField(
                            value = state.priority,
                            onValueChange = { v -> onUpdateForm { it.copy(priority = v) } },
                            label = "Priority (1-10)",
                            keyboardType = KeyboardType.Number,
                            modifier = Modifier.weight(1f)
                        )
                        SettingsTextField(
                            value = state.locale,
                            onValueChange = { v -> onUpdateForm { it.copy(locale = v) } },
                            label = "Locale",
                            modifier = Modifier.weight(1f)
                        )
                    }
                    SettingsDropdown(
                        label = "Routing Strategy",
                        selectedLabel = state.routingStrategy.apiValue,
                        options = RoutingStrategyOption.entries,
                        optionLabel = { it.apiValue },
                        onSelected = { opt -> onUpdateForm { it.copy(routingStrategy = opt) } }
                    )
                }

                // Oceana Routing Attributes
                AnimatedVisibility(visible = isOceana) {
                    SettingsCard(title = "Routing Attributes (Oceana only)") {
                        AttributePairRow(
                            name = state.attrAName,
                            value = state.attrAValue,
                            onNameChange = { v -> onUpdateForm { it.copy(attrAName = v) } },
                            onValueChange = { v -> onUpdateForm { it.copy(attrAValue = v) } }
                        )
                        AttributePairRow(
                            name = state.attrBName,
                            value = state.attrBValue,
                            onNameChange = { v -> onUpdateForm { it.copy(attrBName = v) } },
                            onValueChange = { v -> onUpdateForm { it.copy(attrBValue = v) } }
                        )
                        AttributePairRow(
                            name = state.attrCName,
                            value = state.attrCValue,
                            onNameChange = { v -> onUpdateForm { it.copy(attrCName = v) } },
                            onValueChange = { v -> onUpdateForm { it.copy(attrCValue = v) } }
                        )
                    }
                }

                // Resource (Work API)
                SettingsCard(title = "Resource (Work API)") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SettingsTextField(
                            value = state.sourceName,
                            onValueChange = { v -> onUpdateForm { it.copy(sourceName = v) } },
                            label = "Source Name (e.g., CM)",
                            modifier = Modifier.weight(1f)
                        )
                        SettingsTextField(
                            value = state.resourceId,
                            onValueChange = { v -> onUpdateForm { it.copy(resourceId = v) } },
                            label = "Resource Id",
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // Video Appearance and Capture
                SettingsCard(title = "Local Video Appearance and Capture") {
                    SettingsDropdown(
                        label = "Local VideoSurfaceView Preset",
                        selectedLabel = state.localVideoPreset.label,
                        options = LocalVideoSurfacePreset.entries,
                        optionLabel = { it.label },
                        onSelected = { preset -> onUpdateForm { it.copy(localVideoPreset = preset) } }
                    )
                    SettingsDropdown(
                        label = "Video Capture Resolution",
                        selectedLabel = state.capturePreference.name.removePrefix("VideoCapturePreference_"),
                        options = CAPTURE_RESOLUTIONS,
                        optionLabel = { it.name.removePrefix("VideoCapturePreference_") },
                        onSelected = { pref -> onUpdateForm { it.copy(capturePreference = pref) } }
                    )
                    SettingsDropdown(
                        label = "Video Capture Orientation",
                        selectedLabel = state.captureOrientation.name.removePrefix("VideoCaptureOrientation_"),
                        options = CAPTURE_ORIENTATIONS,
                        optionLabel = { it.name.removePrefix("VideoCaptureOrientation_") },
                        onSelected = { orient -> onUpdateForm { it.copy(captureOrientation = orient) } }
                    )
                    SettingsSwitchRow(
                        label = "Mute camera automatically when backgrounded",
                        checked = state.muteVideoOnBackground,
                        onCheckedChange = { c -> onUpdateForm { it.copy(muteVideoOnBackground = c) } }
                    )
                }

                // Logging and Support
                SettingsCard(title = "Logging and Support") {
                    SettingsSwitchRow(
                        label = "Log to device (app-private storage)",
                        checked = state.logToDevice,
                        onCheckedChange = { c -> onUpdateForm { it.copy(logToDevice = c) } }
                    )
                    SettingsTextField(
                        value = state.logFileName,
                        onValueChange = { v -> onUpdateForm { it.copy(logFileName = v) } },
                        label = "Log File Name"
                    )
                    SettingsTextField(
                        value = state.supportEmail,
                        onValueChange = { v -> onUpdateForm { it.copy(supportEmail = v) } },
                        label = "Email address to send log files",
                        keyboardType = KeyboardType.Email
                    )
                    OutlinedButton(
                        onClick = onSendLogs,
                        border = BorderStroke(1.dp, BorderInteractive),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    ) {
                        Text("Send Logs", color = SlateNavy, fontWeight = FontWeight.SemiBold)
                    }
                }

                if (!state.feedbackMessage.isNullOrBlank()) {
                    Surface(
                        color = SurfaceLight,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, if (state.isFeedbackError) StatusPoorLight else BorderHairline),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = state.feedbackMessage,
                            color = if (state.isFeedbackError) StatusPoorLight else InkSecondary,
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))
            }
        }
    }
}

@Composable
private fun SettingsCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        color = SurfaceLight,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, BorderHairline),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = InkPrimary
            )
            content()
        }
    }
}

@Composable
private fun SettingsTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier.fillMaxWidth(),
    keyboardType: KeyboardType = KeyboardType.Text
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        shape = RoundedCornerShape(8.dp),
        modifier = modifier
    )
}

@Composable
private fun AttributePairRow(
    name: String,
    value: String,
    onNameChange: (String) -> Unit,
    onValueChange: (String) -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SettingsTextField(
            value = name,
            onValueChange = onNameChange,
            label = "Attribute Name",
            modifier = Modifier.weight(1f)
        )
        SettingsTextField(
            value = value,
            onValueChange = onValueChange,
            label = "Value(s)",
            modifier = Modifier.weight(1.3f)
        )
    }
}

@Composable
private fun SettingsSwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = InkPrimary,
            modifier = Modifier.weight(1f)
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> SettingsDropdown(
    label: String,
    selectedLabel: String,
    options: List<T>,
    optionLabel: (T) -> String,
    onSelected: (T) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded }
    ) {
        OutlinedTextField(
            value = selectedLabel,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(optionLabel(option)) },
                    onClick = {
                        onSelected(option)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun SettingsScreenPreview() {
    PhinCallTheme {
        SettingsScreen(
            state = SettingsFormState(),
            onBack = {},
            onSwitchPlatform = {},
            onResetDefaults = {},
            onUpdateForm = {},
            onSendLogs = {},
            onSaveAndApply = {}
        )
    }
}
