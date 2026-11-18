package com.phincall.webrtc.ui.home

import com.avaya.ocs.Services.Work.Enums.PlatformType
import com.phincall.webrtc.config.InteractionLaunchConfig
import com.phincall.webrtc.config.RecentCallRecord
import com.phincall.webrtc.config.SampleConfigurations

data class HomeUiState(
    val config: InteractionLaunchConfig = SampleConfigurations.defaultFor(PlatformType.OCEANA),
    val recentCalls: List<RecentCallRecord> = emptyList(),
    val isOnline: Boolean = true,
    val isFetchingToken: Boolean = false,
    val isCallLocked: Boolean = false,
    val validationIssue: String? = null,
    val errorMessage: String? = null,
    val isDialpadOpen: Boolean = false,
    val dialpadInitialNumber: String = ""
) {
    val isCallActionsEnabled: Boolean
        get() = !isFetchingToken && !isCallLocked
}

sealed interface HomeEvent {
    data object NavigateToInCall : HomeEvent
}
