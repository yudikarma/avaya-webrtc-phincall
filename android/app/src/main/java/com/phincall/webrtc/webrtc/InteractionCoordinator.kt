package com.phincall.webrtc.webrtc

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import com.avaya.clientservices.call.CallType
import com.avaya.ocs.Config.ClientConfiguration
import com.avaya.ocs.Config.Config
import com.avaya.ocs.Config.WebGatewayConfiguration
import com.avaya.ocs.Exceptions.AuthorizationTokenException
import com.avaya.ocs.OceanaCustomerWebVoiceVideo
import com.avaya.ocs.Services.Device.Video.Enums.CallQuality
import com.avaya.ocs.Services.Device.Video.Enums.CameraType
import com.avaya.ocs.Services.Device.Video.VideoDevice
import com.avaya.ocs.Services.Statistics.AudioDetails
import com.avaya.ocs.Services.Statistics.CallDetails
import com.avaya.ocs.Services.Statistics.VideoDetails
import com.avaya.ocs.Services.Work.Enums.AudioDeviceError
import com.avaya.ocs.Services.Work.Enums.AudioDeviceType
import com.avaya.ocs.Services.Work.Enums.DTMFTone
import com.avaya.ocs.Services.Work.Enums.InteractionError
import com.avaya.ocs.Services.Work.Enums.InteractionState
import com.avaya.ocs.Services.Work.Enums.PlatformType
import com.avaya.ocs.Services.Work.Interactions.AbstractInteraction
import com.avaya.ocs.Services.Work.Interactions.AudioInteraction
import com.avaya.ocs.Services.Work.Interactions.Listeners.AudioInteractionListener
import com.avaya.ocs.Services.Work.Interactions.Listeners.ConnectionListener
import com.avaya.ocs.Services.Work.Interactions.Listeners.OnAudioDeviceChangeListener
import com.avaya.ocs.Services.Work.Interactions.Listeners.VideoInteractionListener
import com.avaya.ocs.Services.Work.Interactions.VideoInteraction
import com.avaya.ocs.Services.Work.Schema.Attributes
import com.avaya.ocs.Services.Work.Schema.Resource
import com.avaya.ocs.Services.Work.Schema.Service
import com.avaya.ocs.Services.Work.Work
import com.phincall.webrtc.BuildConfig
import com.phincall.webrtc.config.CallHistoryStore
import com.phincall.webrtc.config.CallMediaMode
import com.phincall.webrtc.config.CallOutcome
import com.phincall.webrtc.config.InteractionLaunchConfig
import com.phincall.webrtc.logging.DiagnosticLogManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import java.util.logging.Level

/**
 * Coordinates the 4-step Avaya WebRTC Connect 4.0.10 interaction lifecycle:
 * 1) In-memory token handoff -> 2) SDK ClientConfiguration -> 3) Work setup -> 4) Interaction start.
 */
class InteractionCoordinator(
    private val application: Application,
    private val diagnosticLogManager: DiagnosticLogManager,
    private val callHistoryStore: CallHistoryStore
) : VideoInteractionListener, ConnectionListener, OnAudioDeviceChangeListener {

    private val _state = MutableStateFlow(CallStateSnapshot())
    val state: StateFlow<CallStateSnapshot> = _state.asStateFlow()

    private var launchConfig: InteractionLaunchConfig? = null
    private var audioInteraction: AudioInteraction? = null
    private var videoInteraction: VideoInteraction? = null
    private var videoDevice: VideoDevice? = null

    private val mainHandler = Handler(Looper.getMainLooper())
    private val coordinatorScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var elapsedTickerJob: Job? = null
    private val interactionLocked = AtomicBoolean(false)
    private val discardRequested = AtomicBoolean(false)

    // Tracks camera recovery after backgrounding (Avaya SDK 4.0.10 release fix).
    private var restoreCameraOnResume = false
    private var mutedVideoForBackground = false
    private var activeHistoryRecordId: String? = null
    private var reachedActiveState = false

    val currentLaunchConfig: InteractionLaunchConfig?
        get() = launchConfig

    fun tryLockForCall(config: InteractionLaunchConfig): Boolean {
        if (!interactionLocked.compareAndSet(false, true)) {
            return false
        }
        launchConfig = config
        diagnosticLogManager.configure(config.loggingConfig)
        discardRequested.set(false)
        restoreCameraOnResume = false
        mutedVideoForBackground = false
        reachedActiveState = false
        activeHistoryRecordId = callHistoryStore.recordCallStart(config)
        _state.value = CallStateSnapshot(
            isLocked = true,
            platformType = config.platformType,
            mediaMode = config.mediaMode,
            interactionState = InteractionState.IDLE,
            isVideoEnabled = config.mediaMode == CallMediaMode.VIDEO,
            isMinimizedInApp = false
        )
        return true
    }

    fun setMinimizedInApp(minimized: Boolean) {
        _state.update { snapshot ->
            if (!snapshot.isLocked || snapshot.isDiscardCompleted) {
                if (snapshot.isMinimizedInApp) snapshot.copy(isMinimizedInApp = false) else snapshot
            } else {
                snapshot.copy(isMinimizedInApp = minimized)
            }
        }
    }

    fun releaseLockOnPreCallFailure(errorMessage: String) {
        activeHistoryRecordId?.let { id ->
            callHistoryStore.updateCallResult(id, 0L, CallOutcome.FAILED)
        }
        activeHistoryRecordId = null
        launchConfig = null
        interactionLocked.set(false)
        diagnosticLogManager.recordEvent(TAG, "Pre-call failure: $errorMessage")
        _state.update {
            it.copy(
                isLocked = false,
                isMinimizedInApp = false,
                errorMessage = errorMessage
            )
        }
    }

    /**
     * Executes Steps 2 through 4 of the Avaya WebRTC Connect 4.0.10 SDK sequence.
     * Video calls must pass the ViewGroup hosting both Receive and Transmit VideoSurfaceViews.
     */
    fun startInteraction(
        authorizationToken: String,
        videoStageContainer: ViewGroup?
    ): Result<Unit> {
        val config = launchConfig ?: return Result.failure(
            IllegalStateException("Missing launch configuration.")
        )

        return try {
            val client = createSdkClient(config)
            val sdkVersion = runCatching { client.versionNumber }.getOrDefault("4.0.10")
            _state.update { it.copy(sdkVersion = sdkVersion) }
            val runtimePathNote = if (config.platformType == PlatformType.OCEANA) {
                "OCEANA (AMC + Work Services/Attributes + AAWG)"
            } else {
                "ELITE (AAWG direct + Destination; AMC and Oceana attributes skipped)"
            }
            diagnosticLogManager.recordEvent(
                TAG,
                "Initialized SDK $sdkVersion (${config.mediaMode.name}) | Runtime path: $runtimePathNote"
            )

            val work = createConfiguredWork(client, config)

            when (config.mediaMode) {
                CallMediaMode.AUDIO -> {
                    val interaction = work.createAudioInteraction(application, this)
                    configureCommonInteraction(interaction, config, authorizationToken)
                    interaction.registerListener(this as AudioInteractionListener)
                    interaction.registerConnectionListener(this)
                    audioInteraction = interaction
                    interaction.start()
                }
                CallMediaMode.VIDEO -> {
                    requireNotNull(videoStageContainer) {
                        "Video stage ViewGroup with VideoSurfaceView children is required for video calls."
                    }
                    val interaction = work.createVideoInteraction(application, this)
                    configureCommonInteraction(interaction, config, authorizationToken)
                    interaction.registerListener(this)
                    interaction.registerConnectionListener(this)
                    videoInteraction = interaction

                    val device = interaction.videoDevice
                    device.setVideoSurface(videoStageContainer)
                    videoDevice = device

                    interaction.start()
                }
            }
            startElapsedTicker()
            Result.success(Unit)
        } catch (ex: AuthorizationTokenException) {
            logFailure("Authorization token rejected by SDK", ex)
            handleStartupFailure("Authorization token error: ${ex.message}")
            Result.failure(ex)
        } catch (ex: Throwable) {
            if (ex is VirtualMachineError) throw ex
            logFailure("Failed to start interaction", ex)
            handleStartupFailure("Unable to start interaction (${ex.javaClass.simpleName}): ${ex.message}")
            Result.failure(ex)
        }
    }

    private fun createSdkClient(launch: InteractionLaunchConfig): OceanaCustomerWebVoiceVideo {
        // Avaya SDK instantiates Config even for Elite; AMC fields remain unused on Elite.
        val amcConfig = if (launch.platformType == PlatformType.OCEANA) {
            Config(launch.amcConfig.serverFqdn).apply {
                isSecure = launch.amcConfig.secure
                port = launch.amcConfig.port
                if (launch.amcConfig.urlPath.isNotBlank()) {
                    urlPath = launch.amcConfig.urlPath
                }
            }
        } else {
            Config(null).apply {
                isSecure = false
                port = 0
            }
        }

        val webGateway = WebGatewayConfiguration().apply {
            webGatewayAddress = launch.webGatewayConfig.serverFqdn
            port = launch.webGatewayConfig.port
            isSecure = launch.webGatewayConfig.secure
            if (launch.webGatewayConfig.urlPath.isNotBlank()) {
                webGatewayUrlPath = launch.webGatewayConfig.urlPath
            }
        }

        val clientConfiguration = ClientConfiguration().apply {
            config = amcConfig
            webGatewayConfiguration = webGateway
        }

        return OceanaCustomerWebVoiceVideo(clientConfiguration).apply {
            // Restrict verbose SDK logging to debug builds so release Logcat stays clean.
            registerLogger(if (BuildConfig.DEBUG) Level.INFO else Level.SEVERE)
        }
    }

    private fun createConfiguredWork(
        client: OceanaCustomerWebVoiceVideo,
        launch: InteractionLaunchConfig
    ): Work {
        val work = client.createWork()
        val routing = launch.routingMetadata

        if (routing.contextId.isNotBlank()) {
            work.context = routing.contextId
        }
        if (routing.topic.isNotBlank()) {
            work.topic = routing.topic
        }
        if (routing.locale.isNotBlank()) {
            work.locale = routing.locale
        }
        if (routing.routingStrategy.isNotBlank()) {
            work.routingStrategy = routing.routingStrategy
        }

        // Skill-based Service/Attribute routing applies to Avaya Oceana (Guide 4.0.10, p. 31).
        if (launch.platformType == PlatformType.OCEANA) {
            val attrMap = routing.toAttributeMap()
            if (attrMap.isNotEmpty()) {
                val sdkAttributes = Attributes()
                attrMap.forEach { (key, values) -> sdkAttributes.add(key, values) }
                val service = Service().apply {
                    attributes = sdkAttributes
                    priority = routing.servicePriority.coerceIn(1, 10)
                }
                work.services = listOf(service)
            }
        }

        if (routing.resourceId.isNotBlank() && routing.resourceSourceName.isNotBlank()) {
            val resource = Resource().apply {
                nativeResourceId = routing.resourceId
                sourceName = routing.resourceSourceName
            }
            work.resources = listOf(resource)
        }

        return work
    }

    private fun configureCommonInteraction(
        interaction: AbstractInteraction,
        launch: InteractionLaunchConfig,
        token: String
    ) {
        if (launch.platformType == PlatformType.ELITE) {
            require(launch.callerProfile.destinationNumber.isNotBlank()) {
                "Destination Address is required when running in Avaya Aura Elite mode."
            }
            interaction.destinationAddress = launch.callerProfile.destinationNumber
        } else if (launch.callerProfile.destinationNumber.isNotBlank()) {
            interaction.destinationAddress = launch.callerProfile.destinationNumber
        }
        if (launch.routingMetadata.contextId.isNotBlank()) {
            interaction.context = launch.routingMetadata.contextId
        }
        interaction.platformType = launch.platformType
        interaction.authorizationToken = token
    }

    private fun activeInteraction(): AbstractInteraction? = videoInteraction ?: audioInteraction

    fun readMediaStatistics(onResult: (String) -> Unit) {
        val interaction = activeInteraction()
        if (interaction == null) {
            onResult("No active interaction.")
            return
        }

        val callTypeLabel = runCatching {
            if (interaction.callType == CallType.DEFAULT_REGISTERED_CALLTYPE) "SIP-UA" else "HTTP-UA"
        }.getOrDefault("Unknown")

        interaction.readAudioDetails { audioDetails: AudioDetails? ->
            val sb = StringBuilder()
            sb.appendLine("Audio Statistics")
            if (audioDetails == null) {
                sb.appendLine("  Unavailable")
            } else {
                val transport = if ((audioDetails as CallDetails).isMediaTunnelled) "TLS" else "UDP"
                sb.appendLine("  Codec: ${audioDetails.codec}")
                sb.appendLine("  Encryption: ${audioDetails.mediaEncryptionType}")
                sb.appendLine("  Call Type: $callTypeLabel ($transport)")
                sb.appendLine("  Packetization: ${audioDetails.packetizationIntervalMillis} ms")
                sb.appendLine("  Round Trip Delay: ${audioDetails.roundTripTimeMillis} ms")
                sb.appendLine("  Packets Tx / Rx: ${audioDetails.packetsTransmitted} / ${audioDetails.packetsReceived}")
                sb.appendLine("  Bytes Tx / Rx: ${audioDetails.bytesTransmitted} / ${audioDetails.bytesReceived}")
                sb.appendLine("  Loss Tx / Rx: ${audioDetails.fractionLostTransmitted}% / ${audioDetails.fractionLostReceived}%")
                sb.appendLine("  Jitter Tx / Rx: ${audioDetails.averageJitterTransmittedMillis} ms / ${audioDetails.averageJitterReceivedMillis} ms")
                sb.appendLine("  Buffer Current / Pref: ${audioDetails.currentBufferSizeMillis} ms / ${audioDetails.preferredBufferSizeMillis} ms")
                sb.appendLine("  Packet Loss Rate: ${audioDetails.currentPacketLossRate}%")
                sb.appendLine("  Discard / Expand Rate: ${audioDetails.currentDiscardRate}% / ${audioDetails.currentExpandRate}%")
            }

            if (videoInteraction != null) {
                interaction.readVideoDetails { videoDetails: VideoDetails? ->
                    sb.appendLine()
                    sb.appendLine("Video Statistics")
                    if (videoDetails == null) {
                        sb.appendLine("  Unavailable")
                    } else {
                        val rx = videoDetails.receiveStatistics
                        val tx = videoDetails.transmitStatistics
                        sb.appendLine("  Codec: ${videoDetails.codec} (${videoDetails.mediaEncryptionType})")
                        sb.appendLine("  Round Trip Delay: ${videoDetails.roundTripTimeMillis} ms")
                        if (rx != null) {
                            sb.appendLine("  Receive: ${rx.width}x${rx.height} @ ${rx.actualFrameRate} fps, ${rx.actualBitRate}/${rx.targetBitRate} kbps, jitter ${rx.jitterBufferSizeMillis} ms, loss ${rx.packetLossTotal} pkts")
                        }
                        if (tx != null) {
                            sb.appendLine("  Transmit: ${tx.width}x${tx.height} @ ${tx.actualFrameRate} fps, ${tx.actualBitRate}/${tx.targetBitRate} kbps, jitter ${tx.jitterBufferSizeMillis} ms, loss ${tx.packetLossTotal} pkts")
                        }
                    }
                    onResult(sb.toString().trimEnd())
                }
            } else {
                onResult(sb.toString().trimEnd())
            }
        }
    }

    fun refreshElapsedTime() {
        val interaction = activeInteraction() ?: return
        val currentState = _state.value.interactionState
        val isMediaActive = reachedActiveState ||
            currentState == InteractionState.ESTABLISHED ||
            currentState == InteractionState.HELD ||
            currentState == InteractionState.HELD_REMOTELY
        if (!isMediaActive) return

        val elapsed = runCatching { interaction.interactionTimeElapsed }.getOrDefault(-1L)
        if (elapsed >= 0L) {
            _state.update { it.copy(elapsedMillis = elapsed) }
        }
    }

    fun toggleAudioMute() {
        val interaction = activeInteraction() ?: return
        runCatching { interaction.muteAudio(!interaction.isAudioMuted) }
            .onFailure { logFailure("muteAudio failed", it) }
    }

    fun toggleVideoMute() {
        val interaction = videoInteraction ?: return
        runCatching { interaction.muteVideo(!interaction.isVideoMuted) }
            .onFailure { logFailure("muteVideo failed", it) }
    }

    fun toggleVideoEnabled() {
        val interaction = videoInteraction ?: return
        runCatching { interaction.enableVideo(!interaction.isVideoEnabled) }
            .onFailure { logFailure("enableVideo failed", it) }
    }

    fun switchCamera() {
        val device = videoDevice ?: return
        runCatching {
            val next = if (device.selectedCamera == CameraType.FRONT) {
                CameraType.BACK
            } else {
                CameraType.FRONT
            }
            device.selectCamera(next)
            _state.update { it.copy(activeCamera = next) }
        }.onFailure { logFailure("switchCamera failed", it) }
    }

    fun toggleHold() {
        val interaction = activeInteraction() ?: return
        runCatching {
            if (interaction.interactionState == InteractionState.HELD) {
                interaction.unhold()
            } else {
                interaction.hold()
            }
        }.onFailure { logFailure("hold/unhold failed", it) }
    }

    fun sendDtmf(digit: String) {
        val interaction = activeInteraction() ?: return
        val tone = DTMFTone.get(digit) ?: return
        runCatching { interaction.sendDtmf(tone) }
            .onFailure { logFailure("sendDtmf failed", it) }
    }

    fun selectAudioDevice(deviceType: AudioDeviceType) {
        val interaction = activeInteraction() ?: return
        val current = _state.value.currentAudioDevice
        val hasBluetoothConnect = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            ContextCompat.checkSelfPermission(
                application,
                Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED

        if (!hasBluetoothConnect &&
            (deviceType == AudioDeviceType.BLUETOOTH_HEADSET || current == AudioDeviceType.BLUETOOTH_HEADSET)
        ) {
            if (deviceType == AudioDeviceType.BLUETOOTH_HEADSET) {
                diagnosticLogManager.recordEvent(TAG, "Skipped Bluetooth selection without BLUETOOTH_CONNECT")
                return
            }
            routeAudioDeviceViaAudioManager(deviceType)
            _state.update { it.copy(currentAudioDevice = deviceType) }
            return
        }

        runCatching { interaction.setAudioDevice(deviceType) }
            .onFailure {
                logFailure("setAudioDevice($deviceType) failed", it)
                routeAudioDeviceViaAudioManager(deviceType)
                _state.update { state -> state.copy(currentAudioDevice = deviceType) }
            }

        if (deviceType == AudioDeviceType.SPEAKER) {
            mainHandler.postDelayed(
                {
                    if (activeInteraction() != null &&
                        _state.value.currentAudioDevice == AudioDeviceType.SPEAKER
                    ) {
                        ensureSpeakerCommunicationRouteIfStillBluetooth()
                    }
                },
                350L
            )
        }
    }

    private fun routeAudioDeviceViaAudioManager(deviceType: AudioDeviceType) {
        runCatching {
            val audioManager = application.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val targetTypes = when (deviceType) {
                    AudioDeviceType.SPEAKER -> intArrayOf(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER)
                    AudioDeviceType.HANDSET -> intArrayOf(AudioDeviceInfo.TYPE_BUILTIN_EARPIECE)
                    AudioDeviceType.WIRED_HEADSET -> intArrayOf(
                        AudioDeviceInfo.TYPE_WIRED_HEADSET,
                        AudioDeviceInfo.TYPE_USB_HEADSET
                    )
                    AudioDeviceType.BLUETOOTH_HEADSET -> intArrayOf(
                        AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
                        AudioDeviceInfo.TYPE_BLE_HEADSET
                    )
                }
                val target = audioManager.availableCommunicationDevices
                    .firstOrNull { info -> targetTypes.contains(info.type) }
                if (target != null) {
                    audioManager.setCommunicationDevice(target)
                } else if (deviceType == AudioDeviceType.HANDSET) {
                    audioManager.clearCommunicationDevice()
                }
            } else {
                @Suppress("DEPRECATION")
                audioManager.apply {
                    if (deviceType != AudioDeviceType.BLUETOOTH_HEADSET) {
                        stopBluetoothSco()
                        isBluetoothScoOn = false
                    }
                    isSpeakerphoneOn = deviceType == AudioDeviceType.SPEAKER
                }
            }
        }.onFailure { logFailure("AudioManager fallback route ($deviceType) failed", it) }
    }

    private fun ensureSpeakerCommunicationRouteIfStillBluetooth() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        runCatching {
            val audioManager = application.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
            val currentType = audioManager.communicationDevice?.type
            if (currentType == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                currentType == AudioDeviceInfo.TYPE_BLE_HEADSET ||
                currentType == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP
            ) {
                val speaker = audioManager.availableCommunicationDevices
                    .firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
                if (speaker != null) {
                    audioManager.setCommunicationDevice(speaker)
                }
            }
        }.onFailure { logFailure("Failed updating Android communication device to speaker", it) }
    }

    private fun startElapsedTicker() {
        elapsedTickerJob?.cancel()
        elapsedTickerJob = coordinatorScope.launch {
            while (isActive) {
                delay(1000L)
                refreshElapsedTime()
            }
        }
    }

    /**
     * Handles background or in-app minimize transitions cleanly without leaking camera streams.
     * Supports optional privacy mute when leaving the app and camera restoration on resume.
     */
    fun onActivityStopped(isInternalMinimize: Boolean = false) {
        val interaction = videoInteraction ?: return
        val config = launchConfig ?: return
        val isActiveVideo = interaction.interactionState == InteractionState.ESTABLISHED &&
            interaction.isVideoEnabled && !interaction.isVideoMuted

        if (!isActiveVideo) return

        if (!isInternalMinimize && config.videoSettings.muteVideoWhenBackgrounded) {
            runCatching {
                interaction.muteVideo(true)
                mutedVideoForBackground = true
            }
        } else {
            // Android closes the hardware camera when the activity stops; re-select on resume.
            restoreCameraOnResume = true
        }
    }

    fun onActivityResumed() {
        setMinimizedInApp(false)
        val interaction = videoInteraction ?: return
        val device = videoDevice ?: return

        if (mutedVideoForBackground) {
            mutedVideoForBackground = false
            runCatching { interaction.muteVideo(false) }
        }

        if (restoreCameraOnResume) {
            restoreCameraOnResume = false
            val selected = device.selectedCamera ?: CameraType.FRONT
            runCatching { device.selectCamera(selected) }
                .onFailure { logFailure("Camera restoration on resume failed", it) }
        }
    }

    /**
     * Executes Appendix D shutdown sequence: end() followed by discard().
     * UI remains open until onDiscardComplete() fires so CSDK tears down media safely.
     */
    fun hangUpAndDiscard() {
        if (!discardRequested.compareAndSet(false, true)) return
        val interaction = activeInteraction()
        if (interaction == null) {
            finalizeCleanup()
            return
        }
        runCatching {
            interaction.end()
            interaction.discard()
        }.onFailure { ex ->
            logFailure("Exception during end()/discard()", ex)
            finalizeCleanup()
        }
    }

    private fun triggerDiscardOnly() {
        if (!discardRequested.compareAndSet(false, true)) return
        val interaction = activeInteraction()
        if (interaction == null) {
            finalizeCleanup()
            return
        }
        runCatching { interaction.discard() }
            .onFailure { ex ->
                logFailure("Exception during discard()", ex)
                finalizeCleanup()
            }
    }

    private fun handleStartupFailure(message: String) {
        activeHistoryRecordId?.let { id ->
            callHistoryStore.updateCallResult(id, _state.value.elapsedMillis, CallOutcome.FAILED)
        }
        activeHistoryRecordId = null
        diagnosticLogManager.recordEvent(TAG, "Startup failure: $message")
        unregisterAndClearReferences()
        runCatching { ActiveCallForegroundService.stop(application) }
        launchConfig = null
        interactionLocked.set(false)
        _state.update {
            it.copy(
                interactionState = InteractionState.FAILED,
                errorMessage = message,
                isDiscardCompleted = true,
                isMinimizedInApp = false,
                isLocked = false
            )
        }
    }

    private fun finalizeCleanup() {
        activeHistoryRecordId?.let { id ->
            val outcome = when {
                reachedActiveState -> CallOutcome.CONNECTED
                _state.value.interactionState == InteractionState.FAILED -> CallOutcome.FAILED
                else -> CallOutcome.INITIATED
            }
            callHistoryStore.updateCallResult(id, _state.value.elapsedMillis, outcome)
        }
        activeHistoryRecordId = null
        diagnosticLogManager.recordEvent(TAG, "Interaction discard complete")
        unregisterAndClearReferences()
        runCatching { ActiveCallForegroundService.stop(application) }
        // Drop launch configuration (including any in-memory proxy key) once the call ends.
        launchConfig = null
        interactionLocked.set(false)
        _state.update {
            it.copy(
                isLocked = false,
                isMinimizedInApp = false,
                isDiscardCompleted = true
            )
        }
    }

    private fun unregisterAndClearReferences() {
        elapsedTickerJob?.cancel()
        elapsedTickerJob = null
        mainHandler.removeCallbacksAndMessages(null)
        audioInteraction?.let { interaction ->
            runCatching {
                interaction.unregisterListener(this as AudioInteractionListener)
                interaction.unregisterConnectionListener(this)
            }
        }
        videoInteraction?.let { interaction ->
            runCatching {
                interaction.unregisterListener(this)
                interaction.unregisterConnectionListener(this)
            }
        }
        audioInteraction = null
        videoInteraction = null
        videoDevice = null
    }

    private fun syncInteractionState() {
        val stateNow = runCatching { activeInteraction()?.interactionState }.getOrNull() ?: return
        diagnosticLogManager.recordEvent(TAG, "State -> ${stateNow.name}")
        _state.update { it.copy(interactionState = stateNow) }
    }

    private fun logFailure(summary: String, error: Throwable) {
        if (BuildConfig.DEBUG) {
            Log.e(TAG, summary, error)
        }
        diagnosticLogManager.recordEvent(TAG, "$summary (${error.javaClass.simpleName})")
    }

    // AudioInteractionListener & VideoInteractionListener callbacks

    override fun onInteractionInitiating() {
        syncInteractionState()
    }

    override fun onInteractionRemoteAlerting() {
        syncInteractionState()
        // Apply resolution/orientation once CSDK VideoDevice initialization completes.
        val device = videoDevice
        val videoSettings = launchConfig?.videoSettings
        if (device != null && videoSettings != null) {
            runCatching {
                device.setVideoCaptureResolutionWithCaptureOrientation(
                    videoSettings.capturePreference,
                    videoSettings.captureOrientation
                )
            }.onFailure { logFailure("setVideoCaptureResolutionWithCaptureOrientation failed", it) }
        }
    }

    override fun onInteractionActive() {
        reachedActiveState = true
        syncInteractionState()
        _state.update { it.copy(signallingBanner = null) }
    }

    override fun onInteractionEnded() {
        syncInteractionState()
        triggerDiscardOnly()
    }

    override fun onInteractionHeld() {
        val remoteHeld = runCatching { activeInteraction()?.isHeldRemotely ?: false }.getOrDefault(false)
        diagnosticLogManager.recordEvent(TAG, "Interaction held (remote=$remoteHeld)")
        _state.update {
            it.copy(
                interactionState = InteractionState.HELD,
                isHeldLocally = !remoteHeld,
                isHeldRemotely = remoteHeld
            )
        }
    }

    override fun onInteractionUnheld() {
        _state.update {
            it.copy(
                isHeldLocally = false,
                isHeldRemotely = false
            )
        }
        syncInteractionState()
    }

    override fun onInteractionHeldRemotely() {
        diagnosticLogManager.recordEvent(TAG, "Interaction held remotely")
        _state.update {
            it.copy(
                interactionState = InteractionState.HELD_REMOTELY,
                isHeldRemotely = true
            )
        }
    }

    override fun onInteractionUnheldRemotely() {
        _state.update { it.copy(isHeldRemotely = false) }
        syncInteractionState()
    }

    override fun onInteractionAudioMuteStatusChanged(muted: Boolean) {
        _state.update { it.copy(isAudioMuted = muted) }
    }

    override fun onInteractionVideoMuteStatusChanged(muted: Boolean) {
        _state.update { it.copy(isVideoMuted = muted) }
    }

    override fun onInteractionVideoEnabledStatusChanged(enabled: Boolean) {
        _state.update { it.copy(isVideoEnabled = enabled) }
    }

    override fun onInteractionQualityChanged(quality: CallQuality?) {
        _state.update { it.copy(callQuality = quality) }
    }

    override fun onInteractionFailed(error: InteractionError?) {
        val errCode = error?.name ?: "UNKNOWN"
        diagnosticLogManager.recordEvent(TAG, "Interaction failed: $errCode")
        _state.update {
            it.copy(
                interactionState = InteractionState.FAILED,
                lastError = error,
                errorMessage = "Interaction failed: $errCode"
            )
        }
        triggerDiscardOnly()
    }

    override fun onDiscardComplete() {
        finalizeCleanup()
    }

    // ConnectionListener callbacks

    override fun onInteractionServiceConnecting() {
        diagnosticLogManager.recordEvent(TAG, "Signalling reconnecting")
        _state.update {
            it.copy(signallingBanner = "Signalling interrupted. Attempting reconnect while media stays active.")
        }
    }

    override fun onInteractionServiceConnected() {
        diagnosticLogManager.recordEvent(TAG, "Signalling connected")
        _state.update { it.copy(signallingBanner = null) }
    }

    override fun onInteractionServiceDisconnected(error: InteractionError?) {
        val code = error?.name ?: "UNKNOWN"
        diagnosticLogManager.recordEvent(TAG, "Signalling disconnected ($code)")
        _state.update {
            it.copy(signallingBanner = "Signalling disconnected ($code). Call cannot recover.")
        }
    }

    // OnAudioDeviceChangeListener callbacks

    override fun onAudioDeviceListChanged(devices: MutableList<AudioDeviceType>?) {
        val sanitized = sanitizeAudioDeviceList(devices)
        _state.update { it.copy(availableAudioDevices = sanitized) }
    }

    override fun onAudioDeviceChanged(deviceType: AudioDeviceType?) {
        val available = sanitizeAudioDeviceList(
            runCatching { activeInteraction()?.availableAudioDevices }.getOrNull()
        )
        _state.update {
            it.copy(
                currentAudioDevice = deviceType,
                availableAudioDevices = if (available.isNotEmpty()) available else it.availableAudioDevices
            )
        }
    }

    override fun onAudioDeviceError(error: AudioDeviceError?) {
        diagnosticLogManager.recordEvent(TAG, "Audio device error: $error")
    }

    companion object {
        private const val TAG = "InteractionCoordinator"

        internal fun sanitizeAudioDeviceList(rawDevices: List<AudioDeviceType?>?): List<AudioDeviceType> {
            if (rawDevices == null) return emptyList()
            return runCatching {
                val snapshot = synchronized(rawDevices) { rawDevices.toTypedArray() }
                snapshot.filterIsInstance<AudioDeviceType>().distinct()
            }.getOrDefault(emptyList())
        }
    }
}
