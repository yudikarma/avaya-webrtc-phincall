package com.phincall.webrtc.ui.settings

import android.content.Intent
import androidx.lifecycle.ViewModel
import com.avaya.ocs.Services.Work.Enums.PlatformType
import com.phincall.webrtc.config.InputValidator
import com.phincall.webrtc.config.RuntimeConfigManager
import com.phincall.webrtc.logging.DiagnosticLogManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val runtimeConfigManager: RuntimeConfigManager,
    private val diagnosticLogManager: DiagnosticLogManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        SettingsFormState.fromLaunchConfig(
            config = runtimeConfigManager.activeConfig.value,
            allowScreenCapture = runtimeConfigManager.allowScreenCapture.value
        )
    )
    val uiState: StateFlow<SettingsFormState> = _uiState.asStateFlow()

    fun updateForm(transform: (SettingsFormState) -> SettingsFormState) {
        _uiState.update { current ->
            transform(current).copy(feedbackMessage = null)
        }
    }

    fun updateAllowScreenCapture(allow: Boolean) {
        runtimeConfigManager.setAllowScreenCapture(allow)
        _uiState.update { current ->
            current.copy(allowScreenCapture = allow, feedbackMessage = null)
        }
    }

    fun switchPlatform(target: PlatformType) {
        val current = _uiState.value
        if (current.platformType == target) return
        // Persist current platform draft and capture policy before swapping profiles.
        runtimeConfigManager.setAllowScreenCapture(current.allowScreenCapture)
        runtimeConfigManager.saveAndApply(current.toLaunchConfig(strictNumbers = false))
        val loaded = runtimeConfigManager.switchPlatform(target)
        _uiState.value = SettingsFormState.fromLaunchConfig(
            config = loaded,
            allowScreenCapture = runtimeConfigManager.allowScreenCapture.value
        )
    }

    fun resetCurrentPlatform() {
        val platform = _uiState.value.platformType
        val defaults = runtimeConfigManager.resetPlatformToDefaults(platform)
        _uiState.value = SettingsFormState.fromLaunchConfig(
            config = defaults,
            allowScreenCapture = runtimeConfigManager.allowScreenCapture.value
        ).copy(
            feedbackMessage = "Active platform profile reset to Avaya sample defaults.",
            isFeedbackError = false
        )
    }

    fun saveDraftOnly() {
        val current = _uiState.value
        runtimeConfigManager.setAllowScreenCapture(current.allowScreenCapture)
        runtimeConfigManager.saveAndApply(current.toLaunchConfig(strictNumbers = false))
    }

    fun saveAndValidate(): Boolean {
        val current = _uiState.value
        val strictConfig = current.toLaunchConfig(strictNumbers = true)
        val issues = RuntimeConfigManager.validateForLaunch(strictConfig)

        runtimeConfigManager.setAllowScreenCapture(current.allowScreenCapture)
        runtimeConfigManager.saveAndApply(current.toLaunchConfig(strictNumbers = false))

        return if (issues.isEmpty()) {
            true
        } else {
            _uiState.update {
                it.copy(
                    feedbackMessage = issues.first().message,
                    isFeedbackError = true
                )
            }
            false
        }
    }

    fun createSendLogsIntent(): Intent? {
        val draft = _uiState.value.toLaunchConfig(strictNumbers = false)
        if (!InputValidator.isValidEmailOrBlank(draft.loggingConfig.supportEmail)) {
            _uiState.update {
                it.copy(
                    feedbackMessage = "Enter a valid support email address or leave it blank.",
                    isFeedbackError = true
                )
            }
            return null
        }
        val intent = diagnosticLogManager.createShareLogIntent(draft.loggingConfig)
        if (intent == null) {
            _uiState.update {
                it.copy(
                    feedbackMessage = "No log file found yet. Enable 'Log to device' and run a session first.",
                    isFeedbackError = true
                )
            }
        }
        return intent
    }
}
