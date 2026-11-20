package com.phincall.webrtc.ui.incall

import android.view.ViewGroup
import androidx.lifecycle.ViewModel
import com.avaya.ocs.Services.Work.Enums.AudioDeviceType
import com.phincall.webrtc.auth.TokenRepository
import com.phincall.webrtc.config.InteractionLaunchConfig
import com.phincall.webrtc.config.RuntimeConfigManager
import com.phincall.webrtc.platform.NetworkMonitor
import com.phincall.webrtc.webrtc.CallStateSnapshot
import com.phincall.webrtc.webrtc.InteractionCoordinator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@HiltViewModel
class InCallViewModel @Inject constructor(
    private val interactionCoordinator: InteractionCoordinator,
    private val tokenRepository: TokenRepository,
    runtimeConfigManager: RuntimeConfigManager,
    networkMonitor: NetworkMonitor
) : ViewModel() {

    val launchConfig: InteractionLaunchConfig? = interactionCoordinator.currentLaunchConfig
    val callState: StateFlow<CallStateSnapshot> = interactionCoordinator.state
    val allowScreenCapture: StateFlow<Boolean> = runtimeConfigManager.allowScreenCapture
    val isOnline: StateFlow<Boolean> = networkMonitor.isOnline

    private val _hasStarted = MutableStateFlow(false)
    val hasStarted: StateFlow<Boolean> = _hasStarted.asStateFlow()

    private val _startupFailure = MutableStateFlow<String?>(null)
    val startupFailure: StateFlow<String?> = _startupFailure.asStateFlow()

    private val _statsText = MutableStateFlow("Reading live media statistics...")
    val statsText: StateFlow<String> = _statsText.asStateFlow()


    fun startInteractionOnce(videoStageContainer: ViewGroup?) {
        if (_hasStarted.value) return
        val config = launchConfig
        val token = tokenRepository.consumeInMemoryToken()

        if (config == null || token.isNullOrBlank()) {
            val msg = "Missing session configuration or authorization token."
            interactionCoordinator.releaseLockOnPreCallFailure(msg)
            _startupFailure.value = msg
            return
        }

        _hasStarted.value = true
        interactionCoordinator.startInteraction(token, videoStageContainer).onFailure { err ->
            _startupFailure.value = err.message ?: "Unable to start call."
        }
    }

    fun refreshStatistics() {
        interactionCoordinator.readMediaStatistics { summary ->
            _statsText.value = summary
        }
    }

    fun toggleAudioMute() = interactionCoordinator.toggleAudioMute()
    fun toggleVideoMute() = interactionCoordinator.toggleVideoMute()
    fun toggleVideoEnabled() = interactionCoordinator.toggleVideoEnabled()
    fun switchCamera() = interactionCoordinator.switchCamera()
    fun toggleHold() = interactionCoordinator.toggleHold()
    fun sendDtmf(digit: String) = interactionCoordinator.sendDtmf(digit)
    fun selectAudioDevice(device: AudioDeviceType) = interactionCoordinator.selectAudioDevice(device)
    fun hangUpAndDiscard() = interactionCoordinator.hangUpAndDiscard()
    fun setMinimizedInApp(minimized: Boolean) = interactionCoordinator.setMinimizedInApp(minimized)
    fun onBackgrounded(isInternalMinimize: Boolean = false) =
        interactionCoordinator.onActivityStopped(isInternalMinimize)
    fun onResumed() = interactionCoordinator.onActivityResumed()

    override fun onCleared() {
        if (callState.value.isLocked && !callState.value.isDiscardCompleted) {
            interactionCoordinator.hangUpAndDiscard()
        }
        super.onCleared()
    }
}
