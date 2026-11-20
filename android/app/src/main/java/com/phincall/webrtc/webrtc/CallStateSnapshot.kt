package com.phincall.webrtc.webrtc

import com.avaya.ocs.Services.Device.Video.Enums.CallQuality
import com.avaya.ocs.Services.Device.Video.Enums.CameraType
import com.avaya.ocs.Services.Work.Enums.AudioDeviceType
import com.avaya.ocs.Services.Work.Enums.InteractionError
import com.avaya.ocs.Services.Work.Enums.InteractionState
import com.avaya.ocs.Services.Work.Enums.PlatformType
import com.phincall.webrtc.config.CallMediaMode

data class CallStateSnapshot(
    val isLocked: Boolean = false,
    val sdkVersion: String = "4.0.10",
    val platformType: PlatformType = PlatformType.OCEANA,
    val mediaMode: CallMediaMode = CallMediaMode.VIDEO,
    val interactionState: InteractionState = InteractionState.IDLE,
    val callQuality: CallQuality? = null,
    val elapsedMillis: Long = 0L,
    val isAudioMuted: Boolean = false,
    val isVideoMuted: Boolean = false,
    val isVideoEnabled: Boolean = true,
    val isHeldLocally: Boolean = false,
    val isHeldRemotely: Boolean = false,
    val activeCamera: CameraType = CameraType.FRONT,
    val currentAudioDevice: AudioDeviceType? = null,
    val availableAudioDevices: List<AudioDeviceType> = emptyList(),
    val signallingBanner: String? = null,
    val errorMessage: String? = null,
    val lastError: InteractionError? = null,
    val isMinimizedInApp: Boolean = false,
    val isDiscardCompleted: Boolean = false
)
