package com.phincall.webrtc.ui.home

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.text.format.DateUtils
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Dialpad
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.avaya.ocs.Services.Work.Enums.PlatformType
import com.phincall.webrtc.R
import com.phincall.webrtc.config.CallMediaMode
import com.phincall.webrtc.config.CallOutcome
import com.phincall.webrtc.config.RecentCallRecord
import com.phincall.webrtc.config.SampleConfigurations
import com.phincall.webrtc.ui.theme.BorderHairline
import com.phincall.webrtc.ui.theme.CanvasLight
import com.phincall.webrtc.ui.theme.CrimsonAccent
import com.phincall.webrtc.ui.theme.CrimsonOnAccentSub
import com.phincall.webrtc.ui.theme.CrimsonSubtleBg
import com.phincall.webrtc.ui.theme.CrimsonSubtleBorder
import com.phincall.webrtc.ui.theme.InkOnDarkMuted
import com.phincall.webrtc.ui.theme.InkPrimary
import com.phincall.webrtc.ui.theme.InkSecondary
import com.phincall.webrtc.ui.theme.PhinCallTheme
import com.phincall.webrtc.ui.theme.SlateNavy
import com.phincall.webrtc.ui.theme.SlateNavyPill
import com.phincall.webrtc.ui.theme.SlateNavyPillBorder
import com.phincall.webrtc.ui.theme.StatusFairLight
import com.phincall.webrtc.ui.theme.StatusPoorLight
import com.phincall.webrtc.ui.theme.SurfaceLight
import com.phincall.webrtc.ui.theme.SurfaceSubtle
import java.util.Locale

@Composable
fun HomeRoute(
    viewModel: HomeViewModel,
    onOpenSettings: () -> Unit,
    onNavigateToInCall: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var pendingCall by remember { mutableStateOf<Triple<CallMediaMode, String?, PlatformType?>?>(null) }
    var permissionDialogMessage by remember { mutableStateOf<String?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val pending = pendingCall ?: return@rememberLauncherForActivityResult
        pendingCall = null
        val (mode, destOverride, platformOverride) = pending

        val micGranted = results[Manifest.permission.RECORD_AUDIO]
            ?: context.hasPermission(Manifest.permission.RECORD_AUDIO)
        val camGranted = mode != CallMediaMode.VIDEO ||
            (results[Manifest.permission.CAMERA] ?: context.hasPermission(Manifest.permission.CAMERA))

        if (micGranted && camGranted) {
            viewModel.startCall(mode, destOverride, platformOverride)
        } else {
            val missing = buildList {
                if (!micGranted) add("Microphone")
                if (!camGranted) add("Camera")
            }.joinToString(" and ")
            val msg = "$missing permission is required for ${mode.name.lowercase()} calls."
            viewModel.reportPermissionDenied(msg)
            permissionDialogMessage = msg
        }
    }

    fun requestPermissionsAndCall(
        mode: CallMediaMode,
        destinationOverride: String?,
        platformOverride: PlatformType? = null
    ) {
        val needed = buildList {
            add(Manifest.permission.RECORD_AUDIO)
            if (mode == CallMediaMode.VIDEO) add(Manifest.permission.CAMERA)
            add(Manifest.permission.READ_PHONE_STATE)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                add(Manifest.permission.BLUETOOTH_CONNECT)
                add(Manifest.permission.BLUETOOTH_SCAN)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
        }
        val missing = needed.filterNot { context.hasPermission(it) }
        if (missing.isEmpty()) {
            viewModel.startCall(mode, destinationOverride, platformOverride)
        } else {
            pendingCall = Triple(mode, destinationOverride, platformOverride)
            permissionLauncher.launch(missing.toTypedArray())
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                HomeEvent.NavigateToInCall -> onNavigateToInCall()
            }
        }
    }

    HomeScreen(
        state = state,
        onTogglePlatform = viewModel::togglePlatform,
        onOpenSettings = onOpenSettings,
        onStartCall = { mode, destOverride, platformOverride ->
            requestPermissionsAndCall(mode, destOverride, platformOverride)
        },
        onOpenDialpad = viewModel::openDialpad,
        onClearRecent = viewModel::clearRecentCalls
    )

    if (state.isDialpadOpen) {
        DialpadBottomSheet(
            platformType = state.config.platformType,
            initialNumber = state.dialpadInitialNumber,
            onTogglePlatform = viewModel::togglePlatform,
            onDismiss = viewModel::dismissDialpad,
            onDialCall = { dialedNumber, mode ->
                viewModel.dismissDialpad()
                requestPermissionsAndCall(mode, dialedNumber)
            }
        )
    }

    if (permissionDialogMessage != null) {
        AlertDialog(
            onDismissRequest = { permissionDialogMessage = null },
            title = { Text("Permission Required") },
            text = {
                Text("${permissionDialogMessage.orEmpty()} Grant access in Android App Settings to place WebRTC calls.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        permissionDialogMessage = null
                        val settingsIntent = Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.fromParts("package", context.packageName, null)
                        )
                        context.startActivity(settingsIntent)
                    }
                ) {
                    Text("Open App Settings")
                }
            },
            dismissButton = {
                TextButton(onClick = { permissionDialogMessage = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun HomeScreen(
    state: HomeUiState,
    onTogglePlatform: () -> Unit,
    onOpenSettings: () -> Unit,
    onStartCall: (CallMediaMode, String?, PlatformType?) -> Unit,
    onOpenDialpad: (String) -> Unit,
    onClearRecent: () -> Unit
) {
    val isOceana = state.config.platformType == PlatformType.OCEANA

    Scaffold(
        containerColor = CanvasLight,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            HomeTopBar(
                isOceana = isOceana,
                onTogglePlatform = onTogglePlatform,
                onOpenSettings = onOpenSettings
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    if (state.isCallActionsEnabled) {
                        onOpenDialpad(state.config.callerProfile.destinationNumber)
                    }
                },
                modifier = Modifier
                    .navigationBarsPadding()
                    .alpha(if (state.isCallActionsEnabled) 1f else 0.45f),
                containerColor = SlateNavy,
                contentColor = Color.White,
                icon = {
                    Icon(
                        imageVector = Icons.Rounded.Dialpad,
                        contentDescription = null
                    )
                },
                text = { Text("Keypad", fontWeight = FontWeight.Bold) }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 640.dp),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 16.dp,
                    bottom = 104.dp
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (!state.isOnline) {
                    item(key = "offline_banner") {
                        StatusAlertCard(
                            message = "Offline. Connect to Wi-Fi or mobile data to reach token and media servers.",
                            toneColor = StatusFairLight,
                            isLoading = false,
                            showSettingsAction = false,
                            onOpenSettings = onOpenSettings,
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Rounded.WifiOff,
                                    contentDescription = null,
                                    tint = StatusFairLight,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        )
                    }
                }

                val bannerInfo = resolveStatusBanner(state)
                if (bannerInfo != null) {
                    item(key = "status_banner") {
                        StatusAlertCard(
                            message = bannerInfo.message,
                            toneColor = bannerInfo.toneColor,
                            isLoading = bannerInfo.isLoading,
                            showSettingsAction = bannerInfo.showSettingsAction,
                            onOpenSettings = onOpenSettings
                        )
                    }
                }

                item(key = "quick_call_card") {
                    QuickCallCard(
                        state = state,
                        onOpenSettings = onOpenSettings,
                        onStartVoice = { onStartCall(CallMediaMode.AUDIO, null, null) },
                        onStartVideo = { onStartCall(CallMediaMode.VIDEO, null, null) }
                    )
                }

                item(key = "recent_header") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Recent Calls",
                            style = MaterialTheme.typography.titleMedium,
                            color = InkPrimary,
                            modifier = Modifier.weight(1f)
                        )
                        if (state.recentCalls.isNotEmpty()) {
                            TextButton(onClick = onClearRecent) {
                                Text("Clear", color = InkSecondary)
                            }
                        }
                    }
                }

                if (state.recentCalls.isEmpty()) {
                    item(key = "recent_empty") {
                        RecentCallsEmptyCard()
                    }
                } else {
                    itemsIndexed(
                        items = state.recentCalls,
                        key = { _, rec -> rec.id }
                    ) { index, record ->
                        val shape = when {
                            state.recentCalls.size == 1 -> RoundedCornerShape(12.dp)
                            index == 0 -> RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)
                            index == state.recentCalls.lastIndex -> RoundedCornerShape(
                                bottomStart = 12.dp,
                                bottomEnd = 12.dp
                            )
                            else -> RoundedCornerShape(0.dp)
                        }
                        Surface(
                            color = SurfaceLight,
                            shape = shape,
                            border = BorderStroke(1.dp, BorderHairline)
                        ) {
                            Column {
                                RecentCallRow(
                                    record = record,
                                    enabled = state.isCallActionsEnabled,
                                    onTapRow = { onOpenDialpad(record.destinationNumber) },
                                    onRedialAudio = {
                                        onStartCall(CallMediaMode.AUDIO, record.destinationNumber, record.platformType)
                                    },
                                    onRedialVideo = {
                                        onStartCall(CallMediaMode.VIDEO, record.destinationNumber, record.platformType)
                                    }
                                )
                                if (index < state.recentCalls.lastIndex) {
                                    HorizontalDivider(color = BorderHairline)
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
private fun HomeTopBar(
    isOceana: Boolean,
    onTogglePlatform: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Surface(
        color = SlateNavy,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(
                    WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
                )
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                color = Color.White,
                shape = RoundedCornerShape(9.dp),
                modifier = Modifier.size(34.dp)
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_phincall_brand),
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(3.dp)
                )
            }
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "PhinCall",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (isOceana) "Avaya Oceana" else "Avaya Aura Elite",
                    color = InkOnDarkMuted,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // One-tap platform switcher pill (replaces bulky tabs on Home)
            Surface(
                onClick = onTogglePlatform,
                shape = RoundedCornerShape(20.dp),
                color = SlateNavyPill,
                border = BorderStroke(1.dp, SlateNavyPillBorder),
                modifier = Modifier
                    .height(44.dp)
                    .semantics {
                        contentDescription = "Switch between Avaya Oceana and Avaya Aura Elite"
                    }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.SwapHoriz,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = if (isOceana) "Oceana" else "Elite",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(Modifier.width(4.dp))

            IconButton(
                onClick = onOpenSettings,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Settings,
                    contentDescription = "Open runtime settings",
                    tint = Color.White
                )
            }
        }
    }
}

@Composable
private fun QuickCallCard(
    state: HomeUiState,
    onOpenSettings: () -> Unit,
    onStartVoice: () -> Unit,
    onStartVideo: () -> Unit
) {
    val isOceana = state.config.platformType == PlatformType.OCEANA
    val dest = state.config.callerProfile.destinationNumber.trim()
    val targetTitle = when {
        dest.isNotEmpty() -> "Target: $dest"
        isOceana -> "Live Help (Default Oceana Routing)"
        else -> "Enter destination via Keypad or Settings"
    }
    val aawgHost = state.config.webGatewayConfig.serverFqdn.ifBlank { "Not configured" }
    val tokenHost = state.config.tokenProxyConfig.serverHost.ifBlank { "Not configured" }
    val endpointMeta = if (isOceana) {
        val amcHost = state.config.amcConfig.serverFqdn.ifBlank { "Not configured" }
        "AMC: $amcHost | AAWG: $aawgHost\nToken: $tokenHost:${state.config.tokenProxyConfig.port}"
    } else {
        "AAWG: $aawgHost | Token: $tokenHost:${state.config.tokenProxyConfig.port}"
    }

    Surface(
        color = SurfaceLight,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, BorderHairline),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = targetTitle,
                        style = MaterialTheme.typography.titleMedium,
                        color = InkPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = endpointMeta,
                        style = MaterialTheme.typography.bodySmall,
                        color = InkSecondary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                TextButton(onClick = onOpenSettings) {
                    Text("Edit", color = SlateNavy, fontWeight = FontWeight.SemiBold)
                }
            }

            Spacer(Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                CallActionTile(
                    title = "Voice Call",
                    subtitle = "Audio interaction",
                    containerColor = SlateNavy,
                    subtitleColor = InkOnDarkMuted,
                    icon = Icons.Rounded.Call,
                    enabled = state.isCallActionsEnabled,
                    onClick = onStartVoice,
                    modifier = Modifier.weight(1f)
                )
                CallActionTile(
                    title = "Video Call",
                    subtitle = "HD video interaction",
                    containerColor = CrimsonAccent,
                    subtitleColor = CrimsonOnAccentSub,
                    icon = Icons.Rounded.Videocam,
                    enabled = state.isCallActionsEnabled,
                    onClick = onStartVideo,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun CallActionTile(
    title: String,
    subtitle: String,
    containerColor: Color,
    subtitleColor: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(14.dp),
        color = containerColor,
        modifier = modifier
            .height(62.dp)
            .alpha(if (enabled) 1f else 0.45f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(22.dp)
            )
            Spacer(Modifier.width(10.dp))
            Column {
                Text(
                    text = title,
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
                Text(
                    text = subtitle,
                    color = subtitleColor,
                    fontSize = 11.sp,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun RecentCallRow(
    record: RecentCallRecord,
    enabled: Boolean,
    onTapRow: () -> Unit,
    onRedialAudio: () -> Unit,
    onRedialVideo: () -> Unit
) {
    val isVideo = record.mediaMode == CallMediaMode.VIDEO
    val destLabel = record.destinationNumber.ifBlank { "Default Oceana Routing" }
    val platformTag = if (record.platformType == PlatformType.ELITE) "Elite" else "Oceana"
    val outcomeText = when (record.outcome) {
        CallOutcome.CONNECTED -> formatShortDuration(record.durationMillis)
        CallOutcome.FAILED -> "Failed"
        CallOutcome.INITIATED -> "Ended"
    }
    val relativeTime = remember(record.timestampMillis) {
        if (record.timestampMillis <= 0L) {
            "Just now"
        } else {
            DateUtils.getRelativeTimeSpanString(
                record.timestampMillis,
                System.currentTimeMillis(),
                DateUtils.MINUTE_IN_MILLIS,
                DateUtils.FORMAT_ABBREV_RELATIVE
            ).toString()
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onTapRow)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(if (isVideo) CrimsonAccent else SlateNavy),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isVideo) Icons.Rounded.Videocam else Icons.Rounded.Call,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = destLabel,
                    style = MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp),
                    color = InkPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Spacer(Modifier.width(6.dp))
                Surface(
                    color = SurfaceSubtle,
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = platformTag,
                        style = MaterialTheme.typography.labelSmall,
                        color = InkSecondary,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
            Spacer(Modifier.height(2.dp))
            Text(
                text = "${if (isVideo) "Video" else "Voice"} | $outcomeText | $relativeTime",
                style = MaterialTheme.typography.bodySmall,
                color = InkSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(Modifier.width(8.dp))

        Surface(
            onClick = onRedialAudio,
            enabled = enabled,
            shape = CircleShape,
            color = SurfaceSubtle,
            border = BorderStroke(1.dp, BorderHairline),
            modifier = Modifier
                .size(44.dp)
                .alpha(if (enabled) 1f else 0.45f)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Rounded.Call,
                    contentDescription = "Redial with voice call",
                    tint = SlateNavy,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Spacer(Modifier.width(6.dp))

        Surface(
            onClick = onRedialVideo,
            enabled = enabled,
            shape = CircleShape,
            color = CrimsonSubtleBg,
            border = BorderStroke(1.dp, CrimsonSubtleBorder),
            modifier = Modifier
                .size(44.dp)
                .alpha(if (enabled) 1f else 0.45f)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Rounded.Videocam,
                    contentDescription = "Redial with video call",
                    tint = CrimsonAccent,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun RecentCallsEmptyCard() {
    Surface(
        color = SurfaceLight,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, BorderHairline),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 36.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Rounded.History,
                contentDescription = null,
                tint = InkSecondary,
                modifier = Modifier.size(36.dp)
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = "No recent calls yet",
                style = MaterialTheme.typography.titleMedium,
                color = InkPrimary
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Tap Keypad to dial a destination number, or use Voice / Video Call above.",
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                color = InkSecondary,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun StatusAlertCard(
    message: String,
    toneColor: Color,
    isLoading: Boolean,
    showSettingsAction: Boolean,
    onOpenSettings: () -> Unit,
    leadingIcon: (@Composable () -> Unit)? = null
) {
    Surface(
        color = SurfaceLight,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, BorderHairline),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = SlateNavy
                )
                Spacer(Modifier.width(10.dp))
            } else if (leadingIcon != null) {
                leadingIcon()
                Spacer(Modifier.width(10.dp))
            }

            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                color = toneColor,
                modifier = Modifier.weight(1f)
            )

            if (showSettingsAction) {
                TextButton(onClick = onOpenSettings) {
                    Text("Settings", color = SlateNavy, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

private data class StatusBannerState(
    val message: String,
    val toneColor: Color,
    val isLoading: Boolean,
    val showSettingsAction: Boolean
)

private fun resolveStatusBanner(state: HomeUiState): StatusBannerState? = when {
    state.isFetchingToken -> StatusBannerState(
        message = "Requesting csaGuest token from intermediary proxy...",
        toneColor = InkPrimary,
        isLoading = true,
        showSettingsAction = false
    )
    state.isCallLocked -> StatusBannerState(
        message = "Interaction in progress. Pre-call controls are disabled until termination.",
        toneColor = InkSecondary,
        isLoading = false,
        showSettingsAction = false
    )
    !state.errorMessage.isNullOrBlank() -> StatusBannerState(
        message = state.errorMessage,
        toneColor = StatusPoorLight,
        isLoading = false,
        showSettingsAction = true
    )
    !state.validationIssue.isNullOrBlank() -> StatusBannerState(
        message = state.validationIssue,
        toneColor = InkSecondary,
        isLoading = false,
        showSettingsAction = true
    )
    else -> null
}

private fun formatShortDuration(durationMillis: Long): String {
    val totalSeconds = durationMillis.coerceAtLeast(0L) / 1000L
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return String.format(Locale.US, "%02d:%02d", minutes, seconds)
}

private fun Context.hasPermission(permission: String): Boolean =
    ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

@Preview(showBackground = true)
@Composable
private fun HomeScreenPreview() {
    PhinCallTheme {
        HomeScreen(
            state = HomeUiState(
                config = SampleConfigurations.defaultFor(PlatformType.OCEANA),
                recentCalls = listOf(
                    RecentCallRecord(
                        id = "1",
                        destinationNumber = "5000",
                        displayName = "Agent Desk",
                        platformType = PlatformType.ELITE,
                        mediaMode = CallMediaMode.VIDEO,
                        timestampMillis = 0L,
                        durationMillis = 125_000L,
                        outcome = CallOutcome.CONNECTED
                    ),
                    RecentCallRecord(
                        id = "2",
                        destinationNumber = "",
                        displayName = "Sales",
                        platformType = PlatformType.OCEANA,
                        mediaMode = CallMediaMode.AUDIO,
                        timestampMillis = 0L,
                        durationMillis = 48_000L,
                        outcome = CallOutcome.CONNECTED
                    )
                )
            ),
            onTogglePlatform = {},
            onOpenSettings = {},
            onStartCall = { _, _, _ -> },
            onOpenDialpad = {},
            onClearRecent = {}
        )
    }
}
