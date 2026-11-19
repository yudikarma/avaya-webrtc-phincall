package com.phincall.webrtc.ui.incall

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.avaya.ocs.Services.Device.Video.VideoSurfaceView
import com.avaya.ocs.Services.Work.Enums.InteractionState
import com.phincall.webrtc.MainActivity
import com.phincall.webrtc.R
import com.phincall.webrtc.config.CallMediaMode
import com.phincall.webrtc.platform.applyCapturePolicy
import com.phincall.webrtc.ui.theme.PhinCallTheme
import com.phincall.webrtc.webrtc.ActiveCallForegroundService
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Dedicated in-call host combining Compose top HUD / bottom controls / dialogs with a native
 * Android ViewGroup media stage inside the Activity Window so Avaya's OpenGL SurfaceView
 * (local and remote VideoSurfaceView planes) renders directly without Compose hole-punch clipping.
 */
@AndroidEntryPoint
class InCallActivity : ComponentActivity() {

    private val viewModel: InCallViewModel by viewModels()

    private var remoteVideoSurface: VideoSurfaceView? = null
    private var localVideoSurface: VideoSurfaceView? = null
    private var fallbackOverlayView: ComposeView? = null

    private var showDtmfDialog by mutableStateOf(false)
    private var showStatsDialog by mutableStateOf(false)
    private var showAudioSheet by mutableStateOf(false)
    private var hangupTapped by mutableStateOf(false)
    private var localPipMeta by mutableStateOf("")
    private var hasFinished by mutableStateOf(false)
    private var isMinimizingToApp = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val darkStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        enableEdgeToEdge(statusBarStyle = darkStyle, navigationBarStyle = darkStyle)
        // Follow runtime Settings capture policy (blocks screenshots / Recents by default when disabled).
        window.applyCapturePolicy(viewModel.allowScreenCapture.value)
        window.setBackgroundDrawable(ColorDrawable(STAGE_DARK_ARGB))

        val config = viewModel.launchConfig
        if (config == null && !viewModel.callState.value.isLocked) {
            ActiveCallForegroundService.stop(this)
            finish()
            return
        }

        val isVideoCall = config?.mediaMode == CallMediaMode.VIDEO
        if (isVideoCall) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }

        val rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        val topHudCompose = ComposeView(this).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            consumeWindowInsets = false
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setContent {
                val snapshot by viewModel.callState.collectAsStateWithLifecycle()
                val isOnline by viewModel.isOnline.collectAsStateWithLifecycle()
                PhinCallTheme {
                    InCallTopHud(
                        config = config,
                        snapshot = snapshot,
                        isOnline = isOnline,
                        localPipMeta = localPipMeta,
                        onOpenStats = { showStatsDialog = true },
                        onMinimizeToApp = ::minimizeToApp
                    )
                }
            }
        }
        rootLayout.addView(topHudCompose)

        // Native ViewGroup media stage sits directly inside the Activity View tree with transparent
        // container backgrounds so Avaya's OnionView/GLSurfaceView shows both local and remote video.
        val stageHostContainer = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        }

        var stageRoot: FrameLayout? = null
        val videoSettings = config?.videoSettings
        if (isVideoCall && videoSettings != null) {
            LayoutInflater.from(this).inflate(
                R.layout.view_remote_video_stage,
                stageHostContainer,
                true
            )
            val inflatedStageRoot = stageHostContainer.findViewById<FrameLayout>(R.id.avayaVideoStageRoot)
            remoteVideoSurface = inflatedStageRoot.findViewById(R.id.avayaRemoteVideoSurface)

            val preset = videoSettings.localSurfacePreset
            val localView = LayoutInflater.from(this).inflate(
                preset.layoutRes,
                inflatedStageRoot,
                false
            ) as VideoSurfaceView

            val density = resources.displayMetrics.density
            val marginPx = (16 * density).toInt()
            val baseParams = localView.layoutParams
            val localWidth = baseParams?.width ?: (132 * density).toInt()
            val localHeight = baseParams?.height ?: (176 * density).toInt()
            val localParams = FrameLayout.LayoutParams(
                localWidth,
                localHeight,
                Gravity.BOTTOM or Gravity.END
            ).apply {
                marginEnd = marginPx
                bottomMargin = marginPx
            }
            inflatedStageRoot.addView(localView, localParams)
            localVideoSurface = localView

            localPipMeta = String.format(
                Locale.US,
                "Local PiP: radius=%d, border=%d",
                localView.videoCornerRadius,
                localView.videoBorderWidth
            )
            stageRoot = inflatedStageRoot
        }

        val fallbackCompose = ComposeView(this).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            consumeWindowInsets = false
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            visibility = if (isVideoCall) View.GONE else View.VISIBLE
            setContent {
                PhinCallTheme {
                    InCallAudioStageFallback(isVideoCall = isVideoCall)
                }
            }
        }
        fallbackOverlayView = fallbackCompose
        stageHostContainer.addView(fallbackCompose)
        rootLayout.addView(stageHostContainer)

        val bottomDockCompose = ComposeView(this).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            consumeWindowInsets = false
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setContent {
                val snapshot by viewModel.callState.collectAsStateWithLifecycle()
                val statsText by viewModel.statsText.collectAsStateWithLifecycle()

                // System Back minimizes to MainActivity without pausing or hanging up the call.
                BackHandler {
                    if (!hangupTapped) {
                        minimizeToApp()
                    }
                }

                LaunchedEffect(showStatsDialog) {
                    while (showStatsDialog) {
                        viewModel.refreshStatistics()
                        delay(3000L)
                    }
                }

                PhinCallTheme {
                    InCallBottomDock(
                        isVideoCall = isVideoCall,
                        snapshot = snapshot,
                        hangupDisabled = hangupTapped,
                        onToggleAudioMute = viewModel::toggleAudioMute,
                        onToggleVideoMute = viewModel::toggleVideoMute,
                        onToggleVideoEnabled = viewModel::toggleVideoEnabled,
                        onSwitchCamera = viewModel::switchCamera,
                        onToggleHold = viewModel::toggleHold,
                        onOpenDtmf = { showDtmfDialog = true },
                        onOpenAudioPicker = {
                            if (snapshot.availableAudioDevices.isEmpty()) {
                                Toast.makeText(
                                    this@InCallActivity,
                                    "No alternate audio outputs detected yet.",
                                    Toast.LENGTH_SHORT
                                ).show()
                            } else {
                                showAudioSheet = true
                            }
                        },
                        onHangUp = {
                            hangupTapped = true
                            viewModel.hangUpAndDiscard()
                        }
                    )

                    if (showDtmfDialog) {
                        InCallDtmfDialog(
                            onDigitPressed = viewModel::sendDtmf,
                            onDismiss = { showDtmfDialog = false }
                        )
                    }

                    if (showStatsDialog) {
                        InCallStatsDialog(
                            statsText = statsText,
                            onDismiss = { showStatsDialog = false }
                        )
                    }

                    if (showAudioSheet) {
                        AudioOutputDeviceSheet(
                            devices = snapshot.availableAudioDevices,
                            currentDevice = snapshot.currentAudioDevice,
                            onSelectDevice = { device ->
                                showAudioSheet = false
                                rootLayout.post {
                                    viewModel.selectAudioDevice(device)
                                }
                            },
                            onDismiss = { showAudioSheet = false }
                        )
                    }
                }
            }
        }
        rootLayout.addView(bottomDockCompose)

        setContentView(rootLayout)

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.callState.collect { snapshot ->
                    val showVideo = isVideoCall && snapshot.isVideoEnabled
                    val videoVisibility = if (showVideo) View.VISIBLE else View.GONE
                    remoteVideoSurface?.visibility = videoVisibility
                    localVideoSurface?.visibility = videoVisibility
                    fallbackOverlayView?.visibility = if (showVideo) View.GONE else View.VISIBLE
                }
            }
        }

        lifecycleScope.launch {
            viewModel.allowScreenCapture.collect { allowCapture ->
                window.applyCapturePolicy(allowCapture)
            }
        }

        // Keep observing completion even while minimized behind MainActivity (CREATED state)
        // so remote hangups finish InCallActivity immediately (Appendix D).
        lifecycleScope.launch {
            combine(
                viewModel.hasStarted,
                viewModel.callState,
                viewModel.startupFailure
            ) { started, snapshot, failure ->
                Triple(started, snapshot, failure)
            }.collect { (started, snapshot, failure) ->
                if (hasFinished) return@collect
                if (failure != null) {
                    Toast.makeText(this@InCallActivity, failure, Toast.LENGTH_LONG).show()
                    exitCallActivity()
                } else if (started && snapshot.isDiscardCompleted) {
                    if (!snapshot.errorMessage.isNullOrBlank() &&
                        snapshot.interactionState == InteractionState.FAILED
                    ) {
                        Toast.makeText(
                            this@InCallActivity,
                            snapshot.errorMessage,
                            Toast.LENGTH_LONG
                        ).show()
                    }
                    exitCallActivity()
                }
            }
        }

        ActiveCallForegroundService.start(this, isVideoCall)
        viewModel.startInteractionOnce(stageRoot)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        isMinimizingToApp = false
    }

    override fun onStop() {
        showDtmfDialog = false
        showStatsDialog = false
        showAudioSheet = false
        val internalMinimize = isMinimizingToApp
        isMinimizingToApp = false
        viewModel.onBackgrounded(isInternalMinimize = internalMinimize)
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        isMinimizingToApp = false
        viewModel.onResumed()
    }

    private fun minimizeToApp() {
        isMinimizingToApp = true
        startActivity(
            Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
        )
    }

    override fun onDestroy() {
        if (isFinishing) {
            ActiveCallForegroundService.stop(this)
        }
        super.onDestroy()
    }

    private fun exitCallActivity() {
        if (!hasFinished) {
            hasFinished = true
            ActiveCallForegroundService.stop(this)
            finish()
        }
    }

    companion object {
        private const val STAGE_DARK_ARGB = 0xFF0F141B.toInt()
    }
}
