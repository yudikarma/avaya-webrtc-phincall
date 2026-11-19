package com.phincall.webrtc.ui.incall

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Assessment
import androidx.compose.material.icons.rounded.CallEnd
import androidx.compose.material.icons.rounded.Cameraswitch
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Dialpad
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MicOff
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.SignalCellularAlt
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.VideocamOff
import androidx.compose.material.icons.rounded.VideoSettings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.avaya.ocs.Services.Device.Video.Enums.CallQuality
import com.avaya.ocs.Services.Work.Enums.AudioDeviceType
import com.phincall.webrtc.R
import com.phincall.webrtc.config.CallMediaMode
import com.phincall.webrtc.config.InteractionLaunchConfig
import com.phincall.webrtc.ui.theme.CrimsonAccent
import com.phincall.webrtc.ui.theme.DockButtonIdle
import com.phincall.webrtc.ui.theme.DockDark
import com.phincall.webrtc.ui.theme.InkOnDark
import com.phincall.webrtc.ui.theme.InkOnDarkMuted
import com.phincall.webrtc.ui.theme.StageDark
import com.phincall.webrtc.ui.theme.StatusFairDark
import com.phincall.webrtc.ui.theme.StatusFairLight
import com.phincall.webrtc.ui.theme.StatusGoodDark
import com.phincall.webrtc.ui.theme.StatusPoorDark
import com.phincall.webrtc.ui.theme.TabularMonospaceStyle
import com.phincall.webrtc.webrtc.CallStateSnapshot
import java.util.Locale

@Composable
internal fun InCallTopHud(
    config: InteractionLaunchConfig?,
    snapshot: CallStateSnapshot,
    isOnline: Boolean,
    localPipMeta: String,
    onOpenStats: () -> Unit,
    onMinimizeToApp: () -> Unit = {}
) {
    val isVideoCall = (config?.mediaMode ?: snapshot.mediaMode) == CallMediaMode.VIDEO
    val platformLabel = "${(config?.platformType ?: snapshot.platformType).name} / ${if (isVideoCall) "VIDEO" else "AUDIO"}"
    val destSuffix = config?.callerProfile?.destinationNumber?.takeIf { it.isNotBlank() }?.let { " | $it" }.orEmpty()
    val (qualityText, qualityColor) = resolveQualityIndicator(snapshot.callQuality)

    Surface(
        color = DockDark,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(
                    WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
                )
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                DockIconButton(
                    icon = Icons.Rounded.KeyboardArrowDown,
                    contentDescription = "Minimize active call and return to app",
                    containerColor = DockButtonIdle,
                    size = 44.dp,
                    onClick = onMinimizeToApp
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "$platformLabel$destSuffix",
                    color = InkOnDark,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = formatDuration(snapshot.elapsedMillis),
                    style = TabularMonospaceStyle.copy(fontSize = 16.sp),
                    color = InkOnDark,
                    modifier = Modifier.padding(end = 10.dp)
                )
                DockIconButton(
                    icon = Icons.Rounded.Assessment,
                    contentDescription = "View live audio and video call statistics",
                    containerColor = DockButtonIdle,
                    size = 44.dp,
                    onClick = onOpenStats
                )
            }

            Spacer(Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = snapshot.interactionState.name,
                    color = InkOnDarkMuted,
                    fontSize = 13.sp,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = Icons.Rounded.SignalCellularAlt,
                    contentDescription = null,
                    tint = qualityColor,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = qualityText,
                    color = qualityColor,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            if (isVideoCall && localPipMeta.isNotEmpty() && snapshot.isVideoEnabled) {
                Text(
                    text = localPipMeta,
                    color = InkOnDarkMuted,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            val warningBanner = when {
                !isOnline -> "Network connection lost. Waiting for connectivity to recover signalling."
                !snapshot.signallingBanner.isNullOrBlank() -> snapshot.signallingBanner
                else -> null
            }
            if (warningBanner != null) {
                Text(
                    text = warningBanner,
                    color = StatusFairDark,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
        }
    }
}

@Composable
internal fun InCallAudioStageFallback(
    isVideoCall: Boolean
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(StageDark),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                color = Color.White,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.size(64.dp)
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_phincall_brand),
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(6.dp)
                )
            }
            Spacer(Modifier.height(16.dp))
            Text(
                text = if (isVideoCall) {
                    "Video stream disabled (audio remains active)"
                } else {
                    "Active Voice Interaction"
                },
                color = InkOnDark,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Audio is routed through the selected device.",
                color = InkOnDarkMuted,
                fontSize = 14.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
internal fun InCallBottomDock(
    isVideoCall: Boolean,
    snapshot: CallStateSnapshot,
    hangupDisabled: Boolean,
    onToggleAudioMute: () -> Unit,
    onToggleVideoMute: () -> Unit,
    onToggleVideoEnabled: () -> Unit,
    onSwitchCamera: () -> Unit,
    onToggleHold: () -> Unit,
    onOpenDtmf: () -> Unit,
    onOpenAudioPicker: () -> Unit,
    onHangUp: () -> Unit
) {
    Surface(
        color = DockDark,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (isVideoCall) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    DockIconButton(
                        icon = if (snapshot.isVideoMuted) Icons.Rounded.VideocamOff else Icons.Rounded.Videocam,
                        contentDescription = "Toggle camera mute",
                        containerColor = if (snapshot.isVideoMuted) CrimsonAccent else DockButtonIdle,
                        enabled = snapshot.isVideoEnabled,
                        onClick = onToggleVideoMute
                    )
                    DockIconButton(
                        icon = Icons.Rounded.VideoSettings,
                        contentDescription = "Enable or disable video stream",
                        containerColor = if (snapshot.isVideoEnabled) DockButtonIdle else StatusFairLight,
                        onClick = onToggleVideoEnabled
                    )
                    DockIconButton(
                        icon = Icons.Rounded.Cameraswitch,
                        contentDescription = "Switch front or rear camera",
                        containerColor = DockButtonIdle,
                        enabled = snapshot.isVideoEnabled,
                        onClick = onSwitchCamera
                    )
                }
                Spacer(Modifier.height(12.dp))
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                DockIconButton(
                    icon = if (snapshot.isAudioMuted) Icons.Rounded.MicOff else Icons.Rounded.Mic,
                    contentDescription = "Toggle microphone mute",
                    containerColor = if (snapshot.isAudioMuted) CrimsonAccent else DockButtonIdle,
                    onClick = onToggleAudioMute
                )
                DockIconButton(
                    icon = Icons.AutoMirrored.Rounded.VolumeUp,
                    contentDescription = "Select audio output device",
                    containerColor = DockButtonIdle,
                    onClick = onOpenAudioPicker
                )
                DockIconButton(
                    icon = Icons.Rounded.Pause,
                    contentDescription = "Hold or resume call",
                    containerColor = if (snapshot.isHeldLocally) StatusFairLight else DockButtonIdle,
                    enabled = !snapshot.isHeldRemotely,
                    onClick = onToggleHold
                )
                DockIconButton(
                    icon = Icons.Rounded.Dialpad,
                    contentDescription = "Open DTMF keypad",
                    containerColor = DockButtonIdle,
                    onClick = onOpenDtmf
                )
                Surface(
                    onClick = onHangUp,
                    enabled = !hangupDisabled,
                    shape = CircleShape,
                    color = CrimsonAccent,
                    modifier = Modifier
                        .size(56.dp)
                        .alpha(if (hangupDisabled) 0.5f else 1f)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.CallEnd,
                            contentDescription = "End call",
                            tint = Color.White,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DockIconButton(
    icon: ImageVector,
    contentDescription: String,
    containerColor: Color,
    enabled: Boolean = true,
    size: Dp = 52.dp,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(14.dp),
        color = containerColor,
        modifier = Modifier
            .size(size)
            .alpha(if (enabled) 1f else 0.45f)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = Color.White,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

@Composable
internal fun InCallDtmfDialog(
    onDigitPressed: (String) -> Unit,
    onDismiss: () -> Unit
) {
    // Avaya Oceana supports DTMF digits 0-9, *, and # (Guide 4.0.10, p. 78).
    val rows = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        listOf("*", "0", "#")
    )
    var digitCount by remember { mutableIntStateOf(0) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DockDark,
        titleContentColor = InkOnDark,
        textContentColor = InkOnDark,
        title = { Text("DTMF Keypad", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                // Mask entered digits with bullets to protect IVR PINs from shoulder surfing.
                Text(
                    text = "\u2022".repeat(digitCount).ifEmpty { " " },
                    style = TabularMonospaceStyle.copy(fontSize = 20.sp),
                    color = InkOnDark
                )
                rows.forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        row.forEach { digit ->
                            Surface(
                                onClick = {
                                    digitCount += 1
                                    onDigitPressed(digit)
                                },
                                shape = CircleShape,
                                color = DockButtonIdle,
                                modifier = Modifier.size(60.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = digit,
                                        color = InkOnDark,
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = InkOnDark)
            }
        }
    )
}

@Composable
internal fun InCallStatsDialog(
    statsText: String,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DockDark,
        titleContentColor = InkOnDark,
        textContentColor = InkOnDark,
        title = { Text("Call Statistics", fontWeight = FontWeight.Bold) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    text = statsText,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    color = InkOnDark
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = InkOnDark)
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AudioOutputDeviceSheet(
    devices: List<AudioDeviceType>,
    currentDevice: AudioDeviceType?,
    onSelectDevice: (AudioDeviceType) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val safeDevices = remember(devices) {
        (devices as Iterable<*>).filterIsInstance<AudioDeviceType>().distinct()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = DockDark,
        contentColor = InkOnDark
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            Text(
                text = "Audio Output Device",
                style = MaterialTheme.typography.titleMedium,
                color = InkOnDark,
                modifier = Modifier.padding(bottom = 10.dp)
            )
            safeDevices.forEach { device ->
                val label = when (device) {
                    AudioDeviceType.SPEAKER -> "Speakerphone"
                    AudioDeviceType.HANDSET -> "Earpiece"
                    AudioDeviceType.WIRED_HEADSET -> "Wired Headset"
                    AudioDeviceType.BLUETOOTH_HEADSET -> "Bluetooth Headset"
                }
                val selected = device == currentDevice
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelectDevice(device) }
                        .padding(vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = label,
                        color = InkOnDark,
                        fontSize = 15.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        modifier = Modifier.weight(1f)
                    )
                    if (selected) {
                        Icon(
                            imageVector = Icons.Rounded.Check,
                            contentDescription = "Active",
                            tint = StatusGoodDark
                        )
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
        }
    }
}

private fun resolveQualityIndicator(quality: CallQuality?): Pair<String, Color> = when (quality) {
    CallQuality.EXCELLENT -> "Excellent" to StatusGoodDark
    CallQuality.GOOD -> "Good" to StatusGoodDark
    CallQuality.FAIR -> "Fair" to StatusFairDark
    CallQuality.POOR -> "Poor" to StatusPoorDark
    CallQuality.BAD -> "Bad" to StatusPoorDark
    null -> "Measuring quality" to InkOnDarkMuted
}

private fun formatDuration(elapsedMs: Long): String {
    val totalSeconds = elapsedMs.coerceAtLeast(0L) / 1000L
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return String.format(Locale.US, "%02d:%02d", minutes, seconds)
}
