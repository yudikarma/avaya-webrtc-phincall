package com.phincall.webrtc.ui

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.avaya.ocs.Services.Device.Video.Enums.CallQuality
import com.avaya.ocs.Services.Device.Video.VideoSurfaceView
import com.avaya.ocs.Services.Work.Enums.AudioDeviceType
import com.avaya.ocs.Services.Work.Enums.InteractionState
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.phincall.webrtc.PhinCallApplication
import com.phincall.webrtc.R
import com.phincall.webrtc.config.CallMediaMode
import com.phincall.webrtc.config.InteractionLaunchConfig
import com.phincall.webrtc.databinding.ActivityInCallBinding
import com.phincall.webrtc.webrtc.ActiveCallForegroundService
import com.phincall.webrtc.webrtc.CallStateSnapshot
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale

class InCallActivity : AppCompatActivity() {

    private lateinit var binding: ActivityInCallBinding
    private lateinit var app: PhinCallApplication
    private var localVideoSurface: VideoSurfaceView? = null
    private var hasStartedInteraction = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Block screenshots, screen recording, and Recents thumbnails of live calls and IVR digits.
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)

        binding = ActivityInCallBinding.inflate(layoutInflater)
        setContentView(binding.root)

        app = application as PhinCallApplication
        val launchConfig = app.interactionCoordinator.currentLaunchConfig
        val token = app.tokenRepository.consumeInMemoryToken()

        if (launchConfig == null || token.isNullOrBlank()) {
            app.interactionCoordinator.releaseLockOnPreCallFailure("Missing session configuration or authorization token.")
            finish()
            return
        }

        configureMediaStage(launchConfig)
        bindControls()
        registerBackNavigationHandler()
        observeCallState()
        startElapsedTimerLoop()

        ActiveCallForegroundService.start(this, launchConfig.mediaMode == CallMediaMode.VIDEO)

        val stageForVideo = if (launchConfig.mediaMode == CallMediaMode.VIDEO) {
            binding.videoStageContainer
        } else {
            null
        }
        hasStartedInteraction = true
        app.interactionCoordinator.startInteraction(token, stageForVideo).onFailure { err ->
            Toast.makeText(this, err.message ?: "Unable to start call", Toast.LENGTH_LONG).show()
            ActiveCallForegroundService.stop(this)
            finish()
        }
    }

    private fun configureMediaStage(config: InteractionLaunchConfig) {
        val platformLabel = "${config.platformType.name} / ${config.mediaMode.name}"
        binding.textPlatformMode.text = platformLabel

        if (config.mediaMode == CallMediaMode.VIDEO) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            binding.rowVideoControls.visibility = View.VISIBLE
            binding.layoutAudioStage.visibility = View.GONE
            binding.remoteVideoSurface.visibility = View.VISIBLE

            // Inflate the selected local VideoSurfaceView preset carrying exact XML radius/border attrs.
            val preset = config.videoSettings.localSurfacePreset
            val inflated = LayoutInflater.from(this).inflate(
                preset.layoutRes,
                binding.videoStageContainer,
                false
            ) as VideoSurfaceView

            val marginPx = (16 * resources.displayMetrics.density).toInt()
            val params = ConstraintLayout.LayoutParams(inflated.layoutParams).apply {
                endToEnd = ConstraintLayout.LayoutParams.PARENT_ID
                bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID
                marginEnd = marginPx
                bottomMargin = marginPx
            }
            binding.videoStageContainer.addView(inflated, params)
            localVideoSurface = inflated

            binding.textVideoSurfaceMeta.visibility = View.VISIBLE
            binding.textVideoSurfaceMeta.text = String.format(
                Locale.US,
                "Local PiP: radius=%d, border=%d",
                inflated.videoCornerRadius,
                inflated.videoBorderWidth
            )
        } else {
            binding.rowVideoControls.visibility = View.GONE
            binding.remoteVideoSurface.visibility = View.GONE
            binding.layoutAudioStage.visibility = View.VISIBLE
            binding.textVideoSurfaceMeta.visibility = View.GONE
        }
    }

    private fun bindControls() {
        binding.buttonCallStats.setOnClickListener {
            if (supportFragmentManager.findFragmentByTag(CallStatsDialog.TAG) == null) {
                CallStatsDialog().show(supportFragmentManager, CallStatsDialog.TAG)
            }
        }
        binding.buttonMuteAudio.setOnClickListener {
            app.interactionCoordinator.toggleAudioMute()
        }
        binding.buttonMuteVideo.setOnClickListener {
            app.interactionCoordinator.toggleVideoMute()
        }
        binding.buttonEnableVideo.setOnClickListener {
            app.interactionCoordinator.toggleVideoEnabled()
        }
        binding.buttonSwitchCamera.setOnClickListener {
            app.interactionCoordinator.switchCamera()
        }
        binding.buttonHold.setOnClickListener {
            app.interactionCoordinator.toggleHold()
        }
        binding.buttonDtmf.setOnClickListener {
            if (supportFragmentManager.findFragmentByTag(DtmfKeypadDialog.TAG) == null) {
                DtmfKeypadDialog { digit ->
                    app.interactionCoordinator.sendDtmf(digit)
                }.show(supportFragmentManager, DtmfKeypadDialog.TAG)
            }
        }
        binding.buttonAudioDevice.setOnClickListener {
            showAudioDevicePicker()
        }
        binding.buttonHangUp.setOnClickListener {
            binding.buttonHangUp.isEnabled = false
            app.interactionCoordinator.hangUpAndDiscard()
        }
    }

    private fun registerBackNavigationHandler() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                app.interactionCoordinator.hangUpAndDiscard()
            }
        })
    }

    private fun observeCallState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                app.interactionCoordinator.state.collect { snapshot ->
                    renderSnapshot(snapshot)
                }
            }
        }
    }

    private fun startElapsedTimerLoop() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (isActive) {
                    app.interactionCoordinator.refreshElapsedTime()
                    delay(1000L)
                }
            }
        }
    }

    private fun renderSnapshot(snapshot: CallStateSnapshot) {
        binding.textInteractionState.text = snapshot.interactionState.name
        binding.textCallTimer.text = formatDuration(snapshot.elapsedMillis)
        renderCallQuality(snapshot.callQuality)

        if (snapshot.signallingBanner.isNullOrBlank()) {
            binding.textSignallingWarning.visibility = View.GONE
        } else {
            binding.textSignallingWarning.visibility = View.VISIBLE
            binding.textSignallingWarning.text = snapshot.signallingBanner
        }

        binding.buttonMuteAudio.setImageResource(
            if (snapshot.isAudioMuted) R.drawable.ic_mic_off else R.drawable.ic_mic_on
        )
        binding.buttonMuteAudio.backgroundTintList = ColorStateList.valueOf(
            ContextCompat.getColor(
                this,
                if (snapshot.isAudioMuted) R.color.crimson_accent else R.color.dock_button_idle
            )
        )

        binding.buttonHold.isEnabled = !snapshot.isHeldRemotely
        binding.buttonHold.alpha = if (snapshot.isHeldRemotely) 0.45f else 1.0f
        binding.buttonHold.backgroundTintList = ColorStateList.valueOf(
            ContextCompat.getColor(
                this,
                if (snapshot.isHeldLocally) R.color.status_fair_light else R.color.dock_button_idle
            )
        )

        if (snapshot.mediaMode == CallMediaMode.VIDEO) {
            binding.buttonMuteVideo.setImageResource(
                if (snapshot.isVideoMuted) R.drawable.ic_video_off else R.drawable.ic_video_on
            )
            binding.buttonMuteVideo.backgroundTintList = ColorStateList.valueOf(
                ContextCompat.getColor(
                    this,
                    if (snapshot.isVideoMuted) R.color.crimson_accent else R.color.dock_button_idle
                )
            )

            binding.buttonEnableVideo.backgroundTintList = ColorStateList.valueOf(
                ContextCompat.getColor(
                    this,
                    if (snapshot.isVideoEnabled) R.color.dock_button_idle else R.color.status_fair_light
                )
            )
            binding.buttonMuteVideo.isEnabled = snapshot.isVideoEnabled
            binding.buttonSwitchCamera.isEnabled = snapshot.isVideoEnabled
            binding.buttonMuteVideo.alpha = if (snapshot.isVideoEnabled) 1.0f else 0.45f
            binding.buttonSwitchCamera.alpha = if (snapshot.isVideoEnabled) 1.0f else 0.45f

            val surfaceVisibility = if (snapshot.isVideoEnabled) View.VISIBLE else View.GONE
            binding.remoteVideoSurface.visibility = surfaceVisibility
            localVideoSurface?.visibility = surfaceVisibility
            binding.layoutAudioStage.visibility = if (snapshot.isVideoEnabled) View.GONE else View.VISIBLE
            if (!snapshot.isVideoEnabled) {
                binding.textAudioStageTitle.text = getString(R.string.video_disabled_notice)
            }
        }

        // Transition back only after onDiscardComplete() fires (Appendix D).
        if (hasStartedInteraction && snapshot.isDiscardCompleted) {
            if (!snapshot.errorMessage.isNullOrBlank() && snapshot.interactionState == InteractionState.FAILED) {
                Toast.makeText(this, snapshot.errorMessage, Toast.LENGTH_LONG).show()
            }
            ActiveCallForegroundService.stop(this)
            finish()
        }
    }

    private fun renderCallQuality(quality: CallQuality?) {
        val (labelRes, colorRes) = when (quality) {
            CallQuality.EXCELLENT -> R.string.quality_excellent to R.color.status_good_dark
            CallQuality.GOOD -> R.string.quality_good to R.color.status_good_dark
            CallQuality.FAIR -> R.string.quality_fair to R.color.status_fair_dark
            CallQuality.POOR -> R.string.quality_poor to R.color.status_poor_dark
            CallQuality.BAD -> R.string.quality_bad to R.color.status_poor_dark
            null -> R.string.quality_unknown to R.color.ink_on_dark_muted
        }
        val tint = ContextCompat.getColor(this, colorRes)
        binding.textCallQuality.setText(labelRes)
        binding.textCallQuality.setTextColor(tint)
        binding.iconCallQuality.imageTintList = ColorStateList.valueOf(tint)
    }

    private fun showAudioDevicePicker() {
        val snapshot = app.interactionCoordinator.state.value
        val devices = snapshot.availableAudioDevices
        if (devices.isEmpty()) {
            Toast.makeText(this, "No alternate audio outputs detected yet.", Toast.LENGTH_SHORT).show()
            return
        }
        val labels = devices.map { device ->
            val label = when (device) {
                AudioDeviceType.SPEAKER -> "Speakerphone"
                AudioDeviceType.HANDSET -> "Earpiece"
                AudioDeviceType.WIRED_HEADSET -> "Wired Headset"
                AudioDeviceType.BLUETOOTH_HEADSET -> "Bluetooth Headset"
            }
            if (device == snapshot.currentAudioDevice) "$label (Active)" else label
        }.toTypedArray()

        MaterialAlertDialogBuilder(this)
            .setTitle("Audio Output Device")
            .setItems(labels) { _, which ->
                app.interactionCoordinator.selectAudioDevice(devices[which])
            }
            .show()
    }

    private fun formatDuration(elapsedMs: Long): String {
        val totalSeconds = (elapsedMs.coerceAtLeast(0L) / 1000L)
        val minutes = totalSeconds / 60L
        val seconds = totalSeconds % 60L
        return String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }

    override fun onStop() {
        super.onStop()
        (supportFragmentManager.findFragmentByTag(DtmfKeypadDialog.TAG) as? DtmfKeypadDialog)?.dismissAllowingStateLoss()
        (supportFragmentManager.findFragmentByTag(CallStatsDialog.TAG) as? CallStatsDialog)?.dismissAllowingStateLoss()
        app.interactionCoordinator.onActivityStopped()
    }

    override fun onResume() {
        super.onResume()
        app.interactionCoordinator.onActivityResumed()
    }

    override fun onDestroy() {
        if (isFinishing) {
            app.interactionCoordinator.hangUpAndDiscard()
            ActiveCallForegroundService.stop(this)
        }
        super.onDestroy()
    }
}
