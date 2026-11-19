package com.phincall.webrtc.ui.navigation

import androidx.lifecycle.ViewModel
import com.phincall.webrtc.webrtc.CallStateSnapshot
import com.phincall.webrtc.webrtc.InteractionCoordinator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/**
 * Exposes live active interaction state to the persistent top call header across MainActivity destinations.
 */
@HiltViewModel
class ActiveCallBannerViewModel @Inject constructor(
    private val interactionCoordinator: InteractionCoordinator
) : ViewModel() {

    val callState: StateFlow<CallStateSnapshot> = interactionCoordinator.state

    val activeDestination: String
        get() = interactionCoordinator.currentLaunchConfig
            ?.callerProfile
            ?.destinationNumber
            ?.trim()
            .orEmpty()

    fun toggleAudioMute() {
        interactionCoordinator.toggleAudioMute()
    }
}
