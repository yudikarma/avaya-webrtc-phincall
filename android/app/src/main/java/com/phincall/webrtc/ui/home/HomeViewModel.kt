package com.phincall.webrtc.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.avaya.ocs.Services.Work.Enums.PlatformType
import com.phincall.webrtc.config.CallHistoryStore
import com.phincall.webrtc.config.CallMediaMode
import com.phincall.webrtc.config.RuntimeConfigManager
import com.phincall.webrtc.domain.usecase.StartInteractionOutcome
import com.phincall.webrtc.domain.usecase.StartInteractionUseCase
import com.phincall.webrtc.platform.NetworkMonitor
import com.phincall.webrtc.webrtc.InteractionCoordinator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

private data class HomeTransientState(
    val isFetchingToken: Boolean = false,
    val localError: String? = null,
    val isDialpadOpen: Boolean = false,
    val dialpadInitialNumber: String = ""
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val runtimeConfigManager: RuntimeConfigManager,
    private val callHistoryStore: CallHistoryStore,
    private val interactionCoordinator: InteractionCoordinator,
    networkMonitor: NetworkMonitor,
    private val startInteractionUseCase: StartInteractionUseCase
) : ViewModel() {

    private val transient = MutableStateFlow(HomeTransientState())
    private val eventChannel = Channel<HomeEvent>(Channel.BUFFERED)
    val events: Flow<HomeEvent> = eventChannel.receiveAsFlow()

    val uiState: StateFlow<HomeUiState> = combine(
        runtimeConfigManager.activeConfig,
        callHistoryStore.history,
        interactionCoordinator.state,
        networkMonitor.isOnline,
        transient
    ) { config, history, callSnapshot, isOnline, local ->
        val validation = RuntimeConfigManager.validateForLaunch(config).firstOrNull()?.message
        val combinedError = local.localError ?: callSnapshot.errorMessage
        HomeUiState(
            config = config,
            recentCalls = history,
            isOnline = isOnline,
            isFetchingToken = local.isFetchingToken,
            isCallLocked = callSnapshot.isLocked,
            validationIssue = validation,
            errorMessage = combinedError,
            isDialpadOpen = local.isDialpadOpen,
            dialpadInitialNumber = local.dialpadInitialNumber
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = HomeUiState(config = runtimeConfigManager.activeConfig.value)
    )

    fun togglePlatform() {
        val next = if (runtimeConfigManager.currentPlatform == PlatformType.OCEANA) {
            PlatformType.ELITE
        } else {
            PlatformType.OCEANA
        }
        runtimeConfigManager.switchPlatform(next)
        transient.update { it.copy(localError = null) }
    }

    fun openDialpad(initialNumber: String = runtimeConfigManager.activeConfig.value.callerProfile.destinationNumber) {
        transient.update {
            it.copy(
                isDialpadOpen = true,
                dialpadInitialNumber = initialNumber,
                localError = null
            )
        }
    }

    fun dismissDialpad() {
        transient.update { it.copy(isDialpadOpen = false) }
    }

    fun clearRecentCalls() {
        callHistoryStore.clearAll()
    }

    fun reportPermissionDenied(message: String) {
        transient.update { it.copy(localError = message) }
    }

    fun startCall(
        mediaMode: CallMediaMode,
        destinationOverride: String? = null,
        platformOverride: PlatformType? = null
    ) {
        if (transient.value.isFetchingToken) return
        if (!uiState.value.isOnline) {
            transient.update {
                it.copy(
                    isDialpadOpen = false,
                    localError = "No active network connection detected. Reconnect and try again."
                )
            }
            return
        }

        viewModelScope.launch {
            transient.update {
                it.copy(
                    isDialpadOpen = false,
                    isFetchingToken = true,
                    localError = null
                )
            }

            when (val outcome = startInteractionUseCase(mediaMode, destinationOverride, platformOverride)) {
                StartInteractionOutcome.ReadyToLaunchCall -> {
                    transient.update { it.copy(isFetchingToken = false) }
                    eventChannel.send(HomeEvent.NavigateToInCall)
                }
                is StartInteractionOutcome.ValidationFailed -> {
                    transient.update {
                        it.copy(
                            isFetchingToken = false,
                            localError = "${outcome.message} Open Settings to update."
                        )
                    }
                }
                is StartInteractionOutcome.Failed -> {
                    transient.update {
                        it.copy(
                            isFetchingToken = false,
                            localError = outcome.message
                        )
                    }
                }
            }
        }
    }
}
