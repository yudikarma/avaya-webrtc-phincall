package com.phincall.webrtc.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.avaya.ocs.Services.Work.Enums.PlatformType
import com.phincall.webrtc.PhinCallApplication
import com.phincall.webrtc.R
import com.phincall.webrtc.config.CallMediaMode
import com.phincall.webrtc.config.InteractionLaunchConfig
import com.phincall.webrtc.config.RuntimeConfigManager
import com.phincall.webrtc.databinding.ActivityPreCallBinding
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Click-to-Call launcher screen driven by RuntimeConfigManager.
 * Mirrors the active Avaya Oceana or Avaya Aura Elite runtime profile and opens
 * SettingsActivity for full server, routing, video, and logging configuration.
 */
class PreCallActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPreCallBinding
    private lateinit var app: PhinCallApplication

    private var pendingMediaMode: CallMediaMode? = null
    private var isFetchingToken = false
    private var suppressPlatformToggle = false
    private var lastRenderedPlatform: PlatformType? = null

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val mode = pendingMediaMode ?: return@registerForActivityResult
        pendingMediaMode = null

        val micGranted = results[Manifest.permission.RECORD_AUDIO]
            ?: hasPermission(Manifest.permission.RECORD_AUDIO)
        val camGranted = mode != CallMediaMode.VIDEO ||
            (results[Manifest.permission.CAMERA] ?: hasPermission(Manifest.permission.CAMERA))

        if (micGranted && camGranted) {
            beginClickToCallSequence(mode)
        } else {
            val missing = buildList {
                if (!micGranted) add("Microphone")
                if (!camGranted) add("Camera")
            }.joinToString(" and ")
            showStatusError("$missing permission is required to place a ${mode.name.lowercase()} interaction.")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPreCallBinding.inflate(layoutInflater)
        setContentView(binding.root)

        app = application as PhinCallApplication

        setupPlatformSwitcher()
        setupActionButtons()
        observeRuntimeAndCallState()
    }

    private fun setupPlatformSwitcher() {
        binding.togglePreCallPlatform.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked || suppressPlatformToggle) return@addOnButtonCheckedListener
            val targetPlatform = if (checkedId == R.id.buttonPreCallElite) {
                PlatformType.ELITE
            } else {
                PlatformType.OCEANA
            }
            val current = app.runtimeConfigManager.activeConfig.value
            if (targetPlatform != current.platformType) {
                persistQuickOverridesIntoActiveProfile()
                app.runtimeConfigManager.switchPlatform(targetPlatform)
            }
        }
    }

    private fun setupActionButtons() {
        val openSettingsAction = View.OnClickListener {
            persistQuickOverridesIntoActiveProfile()
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        binding.buttonOpenSettings.setOnClickListener(openSettingsAction)
        binding.buttonConfigureProfile.setOnClickListener(openSettingsAction)

        binding.buttonLiveHelpVoice.setOnClickListener {
            requestPermissionsAndStart(CallMediaMode.AUDIO)
        }
        binding.buttonLiveHelpVideo.setOnClickListener {
            requestPermissionsAndStart(CallMediaMode.VIDEO)
        }
    }

    private fun observeRuntimeAndCallState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(
                    app.runtimeConfigManager.activeConfig,
                    app.interactionCoordinator.state
                ) { config, callState -> config to callState }
                    .collect { (config, snapshot) ->
                        renderRuntimeProfile(config)

                        val locked = snapshot.isLocked || isFetchingToken
                        setCallButtonsEnabled(!locked)

                        if (!isFetchingToken) {
                            binding.progressLoading.visibility = View.GONE
                            val issues = RuntimeConfigManager.validateForLaunch(config)
                            when {
                                snapshot.isLocked -> showStatusInfo(getString(R.string.status_in_call_locked))
                                !snapshot.errorMessage.isNullOrBlank() -> showStatusError(snapshot.errorMessage)
                                issues.isNotEmpty() -> showStatusInfo("${issues.first().message} Open Settings to configure.")
                                else -> showStatusInfo(getString(R.string.status_ready))
                            }
                        }
                    }
            }
        }
    }

    private fun renderRuntimeProfile(config: InteractionLaunchConfig) {
        val isOceana = config.platformType == PlatformType.OCEANA

        suppressPlatformToggle = true
        binding.togglePreCallPlatform.check(
            if (isOceana) R.id.buttonPreCallOceana else R.id.buttonPreCallElite
        )
        suppressPlatformToggle = false

        binding.textPreCallModeNote.text = getString(
            if (isOceana) R.string.mode_notice_oceana else R.string.mode_notice_elite
        )

        if (isOceana) {
            binding.textSummaryAmc.visibility = View.VISIBLE
            binding.textSummaryAmc.text = formatEndpointSummary(
                label = "AMC Cluster",
                secure = config.amcConfig.secure,
                host = config.amcConfig.serverFqdn,
                port = config.amcConfig.port,
                path = config.amcConfig.urlPath
            )
        } else {
            binding.textSummaryAmc.visibility = View.GONE
        }

        binding.textSummaryAawg.text = formatEndpointSummary(
            label = "AAWG",
            secure = config.webGatewayConfig.secure,
            host = config.webGatewayConfig.serverFqdn,
            port = config.webGatewayConfig.port,
            path = config.webGatewayConfig.urlPath
        )

        val apiKeyTag = if (config.tokenProxyConfig.clientApiKey.isNotBlank()) "API key set" else "no API key"
        val tokenBase = formatEndpointSummary(
            label = "Token Service",
            secure = config.tokenProxyConfig.secure,
            host = config.tokenProxyConfig.serverHost,
            port = config.tokenProxyConfig.port,
            path = config.tokenProxyConfig.path
        )
        binding.textSummaryToken.text = "$tokenBase ($apiKeyTag)"

        val callerLabel = listOf(
            config.callerProfile.displayName.takeIf { it.isNotBlank() },
            config.callerProfile.callingNumber.takeIf { it.isNotBlank() }
        ).joinToString(" / ").ifBlank { getString(R.string.not_configured) }

        val destLabel = config.callerProfile.destinationNumber.ifBlank {
            if (isOceana) "Default Oceana routing" else getString(R.string.not_configured)
        }
        binding.textSummaryCaller.text = "Caller: $callerLabel -> Dest: $destLabel"

        if (isOceana) {
            val attrText = config.routingMetadata.toAttributeMap()
                .entries
                .joinToString("; ") { (k, v) -> "$k=${v.joinToString(",")}" }
                .ifBlank { "none" }
            binding.textSummaryAttributes.text =
                "Oceana Work: [$attrText], Priority=${config.routingMetadata.servicePriority}, ${config.routingMetadata.routingStrategy}"
        } else {
            val resourceTag = config.routingMetadata.resourceId.ifBlank { "none" }
            binding.textSummaryAttributes.text =
                "Elite Work: Locale=${config.routingMetadata.locale.ifBlank { "default" }}, ${config.routingMetadata.routingStrategy}, Resource=$resourceTag"
        }

        val resName = config.videoSettings.capturePreference.name.removePrefix("VideoCapturePreference_")
        val orientName = config.videoSettings.captureOrientation.name.removePrefix("VideoCaptureOrientation_")
        binding.textSummaryVideo.text =
            "Video: ${config.videoSettings.localSurfacePreset.label}, $resName ($orientName)"

        // Sync quick fields on platform change or when updated externally in SettingsActivity.
        val platformChanged = lastRenderedPlatform != config.platformType
        lastRenderedPlatform = config.platformType
        if (platformChanged || !binding.editQuickContext.hasFocus()) {
            val currentText = binding.editQuickContext.text?.toString().orEmpty()
            if (currentText != config.routingMetadata.contextId) {
                binding.editQuickContext.setText(config.routingMetadata.contextId)
            }
        }
        if (platformChanged || !binding.editQuickTopic.hasFocus()) {
            val currentText = binding.editQuickTopic.text?.toString().orEmpty()
            if (currentText != config.routingMetadata.topic) {
                binding.editQuickTopic.setText(config.routingMetadata.topic)
            }
        }
    }

    private fun formatEndpointSummary(
        label: String,
        secure: Boolean,
        host: String,
        port: Int,
        path: String
    ): String {
        if (host.isBlank()) {
            return "$label: ${getString(R.string.not_configured)}"
        }
        val scheme = if (secure) "https" else "http"
        val cleanPath = path.trim().trimStart('/')
        val suffix = if (cleanPath.isNotEmpty()) "/$cleanPath" else ""
        return "$label: $scheme://$host:$port$suffix"
    }

    private fun persistQuickOverridesIntoActiveProfile(): InteractionLaunchConfig {
        val base = app.runtimeConfigManager.activeConfig.value
        val updated = base.copy(
            routingMetadata = base.routingMetadata.copy(
                contextId = binding.editQuickContext.text?.toString()?.trim().orEmpty(),
                topic = binding.editQuickTopic.text?.toString()?.trim().orEmpty()
            )
        )
        if (updated != base) {
            app.runtimeConfigManager.saveAndApply(updated)
        }
        return updated
    }

    private fun requestPermissionsAndStart(mode: CallMediaMode) {
        val needed = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (mode == CallMediaMode.VIDEO) {
            needed += Manifest.permission.CAMERA
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            needed += Manifest.permission.BLUETOOTH_CONNECT
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            needed += Manifest.permission.POST_NOTIFICATIONS
        }

        val missing = needed.filterNot { hasPermission(it) }
        if (missing.isEmpty()) {
            beginClickToCallSequence(mode)
        } else {
            pendingMediaMode = mode
            permissionLauncher.launch(missing.toTypedArray())
        }
    }

    private fun hasPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

    private fun beginClickToCallSequence(mode: CallMediaMode) {
        val launchConfig = persistQuickOverridesIntoActiveProfile().copy(mediaMode = mode)
        val issues = RuntimeConfigManager.validateForLaunch(launchConfig)
        if (issues.isNotEmpty()) {
            showStatusError("${issues.first().message} Open Settings to update.")
            return
        }

        // Disable call buttons immediately until the interaction terminates (Guide 4.0.10, p. 33).
        if (!app.interactionCoordinator.tryLockForCall(launchConfig)) {
            showStatusError("An interaction is already in progress.")
            return
        }

        isFetchingToken = true
        setCallButtonsEnabled(false)
        showStatusLoading(getString(R.string.status_fetching_token))

        lifecycleScope.launch {
            val tokenResult = app.tokenRepository.fetchToken(
                proxyConfig = launchConfig.tokenProxyConfig,
                callerProfile = launchConfig.callerProfile
            )

            isFetchingToken = false
            binding.progressLoading.visibility = View.GONE

            tokenResult.fold(
                onSuccess = {
                    startActivity(Intent(this@PreCallActivity, InCallActivity::class.java))
                },
                onFailure = { error ->
                    app.tokenRepository.clearToken()
                    val msg = error.message ?: "Failed to obtain authorization token."
                    app.interactionCoordinator.releaseLockOnPreCallFailure(msg)
                    showStatusError(msg)
                    setCallButtonsEnabled(true)
                }
            )
        }
    }

    private fun setCallButtonsEnabled(enabled: Boolean) {
        binding.buttonLiveHelpVoice.isEnabled = enabled
        binding.buttonLiveHelpVideo.isEnabled = enabled
        binding.buttonLiveHelpVoice.alpha = if (enabled) 1.0f else 0.5f
        binding.buttonLiveHelpVideo.alpha = if (enabled) 1.0f else 0.5f
    }

    private fun showStatusLoading(message: String) {
        binding.progressLoading.visibility = View.VISIBLE
        binding.textStatusMessage.setTextColor(ContextCompat.getColor(this, R.color.ink_primary))
        binding.textStatusMessage.text = message
    }

    private fun showStatusInfo(message: String) {
        binding.progressLoading.visibility = View.GONE
        binding.textStatusMessage.setTextColor(ContextCompat.getColor(this, R.color.ink_secondary))
        binding.textStatusMessage.text = message
    }

    private fun showStatusError(message: String) {
        binding.progressLoading.visibility = View.GONE
        binding.textStatusMessage.setTextColor(ContextCompat.getColor(this, R.color.status_poor_light))
        binding.textStatusMessage.text = message
    }

    override fun onDestroy() {
        if (isFinishing) {
            app.tokenRepository.clearToken()
            app.runtimeConfigManager.clearInMemorySecrets()
        }
        super.onDestroy()
    }
}
