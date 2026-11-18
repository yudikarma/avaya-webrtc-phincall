package com.phincall.webrtc.domain.usecase

import com.avaya.ocs.Services.Work.Enums.PlatformType
import com.phincall.webrtc.auth.TokenRepository
import com.phincall.webrtc.config.CallMediaMode
import com.phincall.webrtc.config.RuntimeConfigManager
import com.phincall.webrtc.webrtc.InteractionCoordinator
import javax.inject.Inject

sealed interface StartInteractionOutcome {
    data object ReadyToLaunchCall : StartInteractionOutcome
    data class ValidationFailed(val message: String) : StartInteractionOutcome
    data class Failed(val message: String) : StartInteractionOutcome
}

/**
 * Orchestrates Step 1 of the Avaya WebRTC Connect 4.0.10 call sequence:
 * applies optional platform or dialed destination overrides, runs platform-aware
 * validation, locks the coordinator to prevent duplicate calls, and fetches the
 * single-use csaGuest token into memory.
 */
class StartInteractionUseCase @Inject constructor(
    private val runtimeConfigManager: RuntimeConfigManager,
    private val tokenRepository: TokenRepository,
    private val interactionCoordinator: InteractionCoordinator
) {
    suspend operator fun invoke(
        mediaMode: CallMediaMode,
        destinationOverride: String? = null,
        platformOverride: PlatformType? = null
    ): StartInteractionOutcome {
        val base = if (platformOverride != null && platformOverride != runtimeConfigManager.currentPlatform) {
            runtimeConfigManager.switchPlatform(platformOverride)
        } else {
            runtimeConfigManager.activeConfig.value
        }
        val effectiveConfig = if (destinationOverride != null) {
            val updated = base.copy(
                callerProfile = base.callerProfile.copy(destinationNumber = destinationOverride.trim())
            )
            runtimeConfigManager.saveAndApply(updated)
            updated.copy(mediaMode = mediaMode)
        } else {
            base.copy(mediaMode = mediaMode)
        }

        val issues = RuntimeConfigManager.validateForLaunch(effectiveConfig)
        if (issues.isNotEmpty()) {
            return StartInteractionOutcome.ValidationFailed(issues.first().message)
        }

        if (!interactionCoordinator.tryLockForCall(effectiveConfig)) {
            return StartInteractionOutcome.Failed("An interaction is already in progress.")
        }

        val tokenResult = tokenRepository.fetchToken(
            proxyConfig = effectiveConfig.tokenProxyConfig,
            callerProfile = effectiveConfig.callerProfile
        )

        return tokenResult.fold(
            onSuccess = { StartInteractionOutcome.ReadyToLaunchCall },
            onFailure = { error ->
                tokenRepository.clearToken()
                val msg = error.message ?: "Failed to obtain authorization token."
                interactionCoordinator.releaseLockOnPreCallFailure(msg)
                StartInteractionOutcome.Failed(msg)
            }
        )
    }
}
