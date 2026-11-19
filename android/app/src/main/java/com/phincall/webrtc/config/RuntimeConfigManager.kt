package com.phincall.webrtc.config

import com.avaya.ocs.Services.Work.Enums.PlatformType
import com.phincall.webrtc.BuildConfig
import com.phincall.webrtc.logging.DiagnosticLogManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ConfigValidationIssue(
    val field: String,
    val message: String
)

/**
 * Single runtime source of truth for the active Avaya platform (Oceana vs Elite)
 * and its configuration. Switching platforms immediately swaps the persisted profile,
 * form state, validation rules, and SDK execution path.
 */
class RuntimeConfigManager(
    private val settingsStore: SettingsStore,
    private val diagnosticLogManager: DiagnosticLogManager
) {
    // Keep proxy API keys strictly in memory per platform; never persist them to disk.
    private val inMemoryApiKeys = mutableMapOf<PlatformType, String>()

    private val _activeConfig = MutableStateFlow(loadPlatformWithApiKey(settingsStore.loadLastPlatform()))
    val activeConfig: StateFlow<InteractionLaunchConfig> = _activeConfig.asStateFlow()

    private val _allowScreenCapture = MutableStateFlow(settingsStore.loadAllowScreenCapture())
    val allowScreenCapture: StateFlow<Boolean> = _allowScreenCapture.asStateFlow()

    init {
        diagnosticLogManager.configure(_activeConfig.value.loggingConfig)
    }

    val currentPlatform: PlatformType
        get() = _activeConfig.value.platformType

    fun switchPlatform(platformType: PlatformType): InteractionLaunchConfig {
        settingsStore.saveLastPlatform(platformType)
        val loaded = loadPlatformWithApiKey(platformType)
        _activeConfig.value = loaded
        diagnosticLogManager.configure(loaded.loggingConfig)
        return loaded
    }

    fun saveAndApply(config: InteractionLaunchConfig) {
        inMemoryApiKeys[config.platformType] = config.tokenProxyConfig.clientApiKey
        settingsStore.save(config)
        _activeConfig.value = config
        diagnosticLogManager.configure(config.loggingConfig)
    }

    fun setAllowScreenCapture(allow: Boolean) {
        val changed = _allowScreenCapture.value != allow
        settingsStore.saveAllowScreenCapture(allow)
        _allowScreenCapture.value = allow
        if (changed) {
            diagnosticLogManager.recordEvent(
                "RuntimeConfigManager",
                "Screen capture policy -> ${if (allow) "ALLOWED" else "BLOCKED"}"
            )
        }
    }

    fun resetPlatformToDefaults(platformType: PlatformType): InteractionLaunchConfig {
        val defaults = SampleConfigurations.defaultFor(platformType)
        inMemoryApiKeys.remove(platformType)
        settingsStore.save(defaults)
        setAllowScreenCapture(false)
        _activeConfig.value = defaults
        diagnosticLogManager.configure(defaults.loggingConfig)
        return defaults
    }

    fun clearInMemorySecrets() {
        inMemoryApiKeys.clear()
        val current = _activeConfig.value
        _activeConfig.value = current.copy(
            tokenProxyConfig = current.tokenProxyConfig.copy(clientApiKey = "")
        )
    }

    private fun loadPlatformWithApiKey(platformType: PlatformType): InteractionLaunchConfig {
        val stored = settingsStore.load(platformType)
        val memKey = inMemoryApiKeys[platformType].orEmpty()
        return stored.copy(
            tokenProxyConfig = stored.tokenProxyConfig.copy(clientApiKey = memKey)
        )
    }

    companion object {
        /**
         * Platform-aware validation: Oceana requires AMC cluster details and allows optional
         * destination routing; Elite skips AMC and requires Destination Address.
         */
        fun validateForLaunch(
            config: InteractionLaunchConfig,
            isDebugBuild: Boolean = BuildConfig.DEBUG
        ): List<ConfigValidationIssue> {
            val issues = mutableListOf<ConfigValidationIssue>()

            if (config.platformType == PlatformType.OCEANA) {
                if (!InputValidator.isValidHost(config.amcConfig.serverFqdn)) {
                    issues += ConfigValidationIssue("amcServer", "AMC Cluster Address is required for Avaya Oceana.")
                }
                if (!InputValidator.isValidPort(config.amcConfig.port)) {
                    issues += ConfigValidationIssue("amcPort", "AMC Cluster Port must be between 1 and 65535.")
                }
                if (!InputValidator.isValidUrlPath(config.amcConfig.urlPath)) {
                    issues += ConfigValidationIssue("amcUrlPath", "AMC URL Path contains invalid characters.")
                }
                if (!isDebugBuild && !config.amcConfig.secure) {
                    issues += ConfigValidationIssue("amcSecure", "HTTPS is mandatory for AMC in release builds.")
                }
            }

            if (!InputValidator.isValidHost(config.webGatewayConfig.serverFqdn)) {
                issues += ConfigValidationIssue("aawgServer", "AAWG Server Address is required.")
            }
            if (!InputValidator.isValidPort(config.webGatewayConfig.port)) {
                issues += ConfigValidationIssue("aawgPort", "AAWG Server Port must be between 1 and 65535.")
            }
            if (!InputValidator.isValidUrlPath(config.webGatewayConfig.urlPath)) {
                issues += ConfigValidationIssue("aawgUrlPath", "AAWG URL Path contains invalid characters.")
            }
            if (!isDebugBuild && !config.webGatewayConfig.secure) {
                issues += ConfigValidationIssue("aawgSecure", "HTTPS is mandatory for AAWG in release builds.")
            }

            if (!InputValidator.isValidHost(config.tokenProxyConfig.serverHost)) {
                issues += ConfigValidationIssue("tokenServer", "Token Service Address is required.")
            }
            if (!InputValidator.isValidPort(config.tokenProxyConfig.port)) {
                issues += ConfigValidationIssue("tokenPort", "Token Service Port must be between 1 and 65535.")
            }
            if (config.tokenProxyConfig.path.isBlank() || !InputValidator.isValidUrlPath(config.tokenProxyConfig.path)) {
                issues += ConfigValidationIssue("tokenPath", "Valid Token Service URL Path is required.")
            }
            if (!config.tokenProxyConfig.secure && (!isDebugBuild || !InputValidator.isLoopbackHost(config.tokenProxyConfig.serverHost))) {
                issues += ConfigValidationIssue(
                    "tokenSecure",
                    "Use HTTPS for Token Service (HTTP is limited to local emulator loopback in debug builds)."
                )
            }

            if (config.platformType == PlatformType.ELITE && config.callerProfile.destinationNumber.isBlank()) {
                issues += ConfigValidationIssue("destination", "Destination Address is required for Avaya Aura Elite.")
            }
            if (!InputValidator.isValidSipUser(config.callerProfile.destinationNumber)) {
                issues += ConfigValidationIssue("destination", "Destination Address contains disallowed SIP characters.")
            }
            if (!InputValidator.isValidSipUser(config.callerProfile.callingNumber)) {
                issues += ConfigValidationIssue("callingNumber", "From Address contains disallowed SIP characters.")
            }
            if (!InputValidator.isSafeDisplayText(config.callerProfile.displayName, 80)) {
                issues += ConfigValidationIssue("displayName", "Display Name contains disallowed characters or exceeds 80 chars.")
            }
            if (!InputValidator.isSafeDisplayText(config.routingMetadata.contextId, 128)) {
                issues += ConfigValidationIssue("context", "Context contains disallowed characters.")
            }
            if (!InputValidator.isSafeDisplayText(config.routingMetadata.topic, 80)) {
                issues += ConfigValidationIssue("topic", "Topic contains disallowed characters.")
            }
            if (!InputValidator.isValidPriority(config.routingMetadata.servicePriority)) {
                issues += ConfigValidationIssue("priority", "Service Priority must be between 1 and 10.")
            }
            if (!InputValidator.isValidEmailOrBlank(config.loggingConfig.supportEmail)) {
                issues += ConfigValidationIssue("supportEmail", "Support email address is not valid.")
            }

            return issues
        }
    }
}
