package com.phincall.webrtc.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.avaya.ocs.Services.Device.Video.Enums.VideoCaptureOrientation
import com.avaya.ocs.Services.Device.Video.Enums.VideoCapturePreference
import com.avaya.ocs.Services.Work.Enums.PlatformType
import com.phincall.webrtc.PhinCallApplication
import com.phincall.webrtc.R
import com.phincall.webrtc.config.AmcServerConfig
import com.phincall.webrtc.config.CallMediaMode
import com.phincall.webrtc.config.CallerProfile
import com.phincall.webrtc.config.InteractionLaunchConfig
import com.phincall.webrtc.config.LocalVideoSurfacePreset
import com.phincall.webrtc.config.RoutingMetadata
import com.phincall.webrtc.config.SampleConfigurations
import com.phincall.webrtc.config.TokenProxyConfig
import com.phincall.webrtc.config.VideoSettings
import com.phincall.webrtc.config.WebGatewayConfigModel
import com.phincall.webrtc.databinding.ActivityPreCallBinding
import kotlinx.coroutines.launch

class PreCallActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPreCallBinding
    private lateinit var app: PhinCallApplication

    private var selectedPlatform: PlatformType = PlatformType.OCEANA
    private var pendingMediaMode: CallMediaMode? = null
    private var isFetchingToken = false

    private val localVideoPresets = LocalVideoSurfacePreset.entries
    private val captureResolutions = listOf(
        VideoCapturePreference.VideoCapturePreference_Min,
        VideoCapturePreference.VideoCapturePreference_270p,
        VideoCapturePreference.VideoCapturePreference_360p,
        VideoCapturePreference.VideoCapturePreference_540p,
        VideoCapturePreference.VideoCapturePreference_Max
    )
    private val captureOrientations = listOf(
        VideoCaptureOrientation.VideoCaptureOrientation_LandscapeOrPortrait,
        VideoCaptureOrientation.VideoCaptureOrientation_LandscapeOnly
    )

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

        setupSpinners()
        setupPlatformSwitcher()
        applySampleDefaults(PlatformType.OCEANA)
        setupActionButtons()
        observeCoordinatorLockState()
    }

    private fun setupSpinners() {
        binding.spinnerLocalVideoStyle.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            localVideoPresets.map { it.label }
        )
        binding.spinnerLocalVideoStyle.setSelection(LocalVideoSurfacePreset.ROUNDED_24_2.ordinal)

        binding.spinnerVideoResolution.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            captureResolutions.map { it.name.removePrefix("VideoCapturePreference_") }
        )
        binding.spinnerVideoResolution.setSelection(3) // 540p default

        binding.spinnerVideoOrientation.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            captureOrientations.map { it.name.removePrefix("VideoCaptureOrientation_") }
        )
    }

    private fun setupPlatformSwitcher() {
        binding.togglePlatformGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            val next = if (checkedId == R.id.buttonPlatformElite) {
                PlatformType.ELITE
            } else {
                PlatformType.OCEANA
            }
            if (next != selectedPlatform) {
                selectedPlatform = next
                updatePlatformFieldVisibility()
                applyPlatformSpecificDefaults(next)
            }
        }
        updatePlatformFieldVisibility()
    }

    private fun updatePlatformFieldVisibility() {
        val isOceana = selectedPlatform == PlatformType.OCEANA
        // AMC cluster config and skill attributes apply to Oceana only (Guide 4.0.10, pp. 31, 34).
        binding.layoutOceanaAmcGroup.visibility = if (isOceana) View.VISIBLE else View.GONE
        binding.inputLayoutAttributes.visibility = if (isOceana) View.VISIBLE else View.GONE
    }

    private fun applySampleDefaults(platformType: PlatformType) {
        val defaults = SampleConfigurations.defaultFor(platformType)
        binding.editAmcServer.setText(defaults.amcConfig.serverFqdn)
        binding.editAmcPort.setText(defaults.amcConfig.port.toString())
        binding.editAmcUrlPath.setText(defaults.amcConfig.urlPath)

        binding.editAawgServer.setText(defaults.webGatewayConfig.serverFqdn)
        binding.editAawgPort.setText(defaults.webGatewayConfig.port.toString())
        binding.editAawgUrlPath.setText(defaults.webGatewayConfig.urlPath)
        binding.switchSecureTls.isChecked = defaults.webGatewayConfig.secure

        binding.editTokenServer.setText(defaults.tokenProxyConfig.serverHost)
        binding.editTokenPort.setText(defaults.tokenProxyConfig.port.toString())
        binding.editTokenPath.setText(defaults.tokenProxyConfig.path)
        binding.switchTokenHttps.isChecked = defaults.tokenProxyConfig.secure

        binding.editDestination.setText(defaults.callerProfile.destinationNumber)
        binding.editCallingNumber.setText(defaults.callerProfile.callingNumber)
        binding.editDisplayName.setText(defaults.callerProfile.displayName)

        binding.editAttributes.setText(defaults.routingMetadata.rawAttributes)
        binding.editContext.setText(defaults.routingMetadata.contextId)
        binding.editTopic.setText(defaults.routingMetadata.topic)
    }

    private fun applyPlatformSpecificDefaults(platformType: PlatformType) {
        val defaults = SampleConfigurations.defaultFor(platformType)
        if (platformType == PlatformType.OCEANA && binding.editAmcServer.text.isNullOrBlank()) {
            binding.editAmcServer.setText(defaults.amcConfig.serverFqdn)
        }
        binding.editDestination.setText(defaults.callerProfile.destinationNumber)
        if (platformType == PlatformType.OCEANA && binding.editAttributes.text.isNullOrBlank()) {
            binding.editAttributes.setText(defaults.routingMetadata.rawAttributes)
        }
    }

    private fun setupActionButtons() {
        binding.buttonLiveHelpVoice.setOnClickListener {
            requestPermissionsAndStart(CallMediaMode.AUDIO)
        }
        binding.buttonLiveHelpVideo.setOnClickListener {
            requestPermissionsAndStart(CallMediaMode.VIDEO)
        }
    }

    private fun observeCoordinatorLockState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                app.interactionCoordinator.state.collect { snapshot ->
                    val locked = snapshot.isLocked || isFetchingToken
                    setCallButtonsEnabled(!locked)
                    if (!isFetchingToken) {
                        binding.progressLoading.visibility = View.GONE
                        when {
                            snapshot.isLocked -> showStatusInfo(getString(R.string.status_in_call_locked))
                            !snapshot.errorMessage.isNullOrBlank() -> showStatusError(snapshot.errorMessage)
                            else -> showStatusInfo(getString(R.string.status_ready))
                        }
                    }
                }
            }
        }
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
        val launchConfig = buildAndValidateConfig(mode) ?: return

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
                    val intent = Intent(this@PreCallActivity, InCallActivity::class.java)
                    startActivity(intent)
                },
                onFailure = { error ->
                    app.tokenRepository.clearToken()
                    app.interactionCoordinator.releaseLockOnPreCallFailure(
                        error.message ?: "Failed to obtain authorization token."
                    )
                    showStatusError(error.message ?: "Failed to obtain authorization token.")
                    setCallButtonsEnabled(true)
                }
            )
        }
    }

    private fun buildAndValidateConfig(mode: CallMediaMode): InteractionLaunchConfig? {
        val amcHost = binding.editAmcServer.text?.toString()?.trim().orEmpty()
        val amcPort = binding.editAmcPort.text?.toString()?.trim()?.toIntOrNull() ?: 443
        val amcPath = binding.editAmcUrlPath.text?.toString()?.trim().orEmpty()

        val aawgHost = binding.editAawgServer.text?.toString()?.trim().orEmpty()
        val aawgPort = binding.editAawgPort.text?.toString()?.trim()?.toIntOrNull() ?: 443
        val aawgPath = binding.editAawgUrlPath.text?.toString()?.trim().orEmpty()
        val useTls = binding.switchSecureTls.isChecked

        val tokenHost = binding.editTokenServer.text?.toString()?.trim().orEmpty()
        val tokenPort = binding.editTokenPort.text?.toString()?.trim()?.toIntOrNull() ?: 8443
        val tokenPath = binding.editTokenPath.text?.toString()?.trim().orEmpty()
        val tokenHttps = binding.switchTokenHttps.isChecked

        val destination = binding.editDestination.text?.toString()?.trim().orEmpty()
        val callingNumber = binding.editCallingNumber.text?.toString()?.trim().orEmpty()
        val displayName = binding.editDisplayName.text?.toString()?.trim().orEmpty()

        val attributesRaw = binding.editAttributes.text?.toString()?.trim().orEmpty()
        val contextId = binding.editContext.text?.toString()?.trim().orEmpty()
        val topic = binding.editTopic.text?.toString()?.trim().orEmpty()

        if (selectedPlatform == PlatformType.OCEANA && amcHost.isBlank()) {
            showStatusError("AMC Server FQDN is required when Avaya Oceana is selected.")
            return null
        }
        if (aawgHost.isBlank()) {
            showStatusError("AAWG Server FQDN is required.")
            return null
        }
        if (tokenHost.isBlank() || tokenPath.isBlank()) {
            showStatusError("Token Proxy host and path are required.")
            return null
        }
        if (selectedPlatform == PlatformType.ELITE && destination.isBlank()) {
            showStatusError("Destination Number is required for Avaya Aura Elite calls.")
            return null
        }

        val localPreset = localVideoPresets[binding.spinnerLocalVideoStyle.selectedItemPosition]
        val resolution = captureResolutions[binding.spinnerVideoResolution.selectedItemPosition]
        val orientation = captureOrientations[binding.spinnerVideoOrientation.selectedItemPosition]

        return InteractionLaunchConfig(
            platformType = selectedPlatform,
            mediaMode = mode,
            amcConfig = AmcServerConfig(amcHost, amcPort, useTls, amcPath),
            webGatewayConfig = WebGatewayConfigModel(aawgHost, aawgPort, useTls, aawgPath),
            tokenProxyConfig = TokenProxyConfig(tokenHost, tokenPort, tokenHttps, tokenPath),
            callerProfile = CallerProfile(destination, callingNumber, displayName),
            routingMetadata = RoutingMetadata(
                rawAttributes = attributesRaw,
                contextId = contextId,
                topic = topic
            ),
            videoSettings = VideoSettings(
                localSurfacePreset = localPreset,
                capturePreference = resolution,
                captureOrientation = orientation,
                muteVideoWhenBackgrounded = binding.switchMuteVideoOnBackground.isChecked
            )
        )
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
}
