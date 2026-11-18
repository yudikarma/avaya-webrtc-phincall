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
import com.phincall.webrtc.BuildConfig
import com.phincall.webrtc.PhinCallApplication
import com.phincall.webrtc.R
import com.phincall.webrtc.config.AmcServerConfig
import com.phincall.webrtc.config.AttributeEntry
import com.phincall.webrtc.config.CallMediaMode
import com.phincall.webrtc.config.CallerProfile
import com.phincall.webrtc.config.InputValidator
import com.phincall.webrtc.config.InteractionLaunchConfig
import com.phincall.webrtc.config.LocalVideoSurfacePreset
import com.phincall.webrtc.config.LoggingConfig
import com.phincall.webrtc.config.RoutingMetadata
import com.phincall.webrtc.config.RoutingStrategyOption
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
    private var suppressPlatformToggle = false

    private val strategyOptions = RoutingStrategyOption.entries
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
        selectedPlatform = app.settingsStore.loadLastPlatform()

        setupSpinners()
        setupPlatformSwitcher()
        loadProfileIntoForm(selectedPlatform)
        setupActionButtons()
        observeCoordinatorLockState()
    }

    private fun setupSpinners() {
        binding.spinnerStrategy.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            strategyOptions.map { it.apiValue }
        )

        binding.spinnerLocalVideoStyle.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            localVideoPresets.map { it.label }
        )

        binding.spinnerVideoResolution.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            captureResolutions.map { it.name.removePrefix("VideoCapturePreference_") }
        )

        binding.spinnerVideoOrientation.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            captureOrientations.map { it.name.removePrefix("VideoCaptureOrientation_") }
        )
    }

    private fun setupPlatformSwitcher() {
        suppressPlatformToggle = true
        binding.togglePlatformGroup.check(
            if (selectedPlatform == PlatformType.ELITE) R.id.buttonPlatformElite else R.id.buttonPlatformOceana
        )
        suppressPlatformToggle = false

        binding.togglePlatformGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked || suppressPlatformToggle) return@addOnButtonCheckedListener
            val next = if (checkedId == R.id.buttonPlatformElite) {
                PlatformType.ELITE
            } else {
                PlatformType.OCEANA
            }
            if (next != selectedPlatform) {
                // Save current platform profile cleanly before loading the target platform profile.
                saveDraftForCurrentPlatform()
                selectedPlatform = next
                app.settingsStore.saveLastPlatform(next)
                loadProfileIntoForm(next)
            }
        }
        updatePlatformFieldVisibility()
    }

    private fun updatePlatformFieldVisibility() {
        val isOceana = selectedPlatform == PlatformType.OCEANA
        // AMC cluster config and skill attributes apply to Oceana only (Guide 4.0.10, pp. 31, 34).
        binding.panelOceanaAmc.visibility = if (isOceana) View.VISIBLE else View.GONE
        binding.panelOceanaAttributes.visibility = if (isOceana) View.VISIBLE else View.GONE
    }

    private fun loadProfileIntoForm(platformType: PlatformType) {
        val cfg = app.settingsStore.load(platformType)
        updatePlatformFieldVisibility()

        binding.editAmcServer.setText(cfg.amcConfig.serverFqdn)
        binding.editAmcPort.setText(cfg.amcConfig.port.toString())
        binding.editAmcUrlPath.setText(cfg.amcConfig.urlPath)
        binding.switchAmcHttps.isChecked = cfg.amcConfig.secure

        binding.editAawgServer.setText(cfg.webGatewayConfig.serverFqdn)
        binding.editAawgPort.setText(cfg.webGatewayConfig.port.toString())
        binding.editAawgUrlPath.setText(cfg.webGatewayConfig.urlPath)
        binding.switchAawgHttps.isChecked = cfg.webGatewayConfig.secure

        binding.editTokenServer.setText(cfg.tokenProxyConfig.serverHost)
        binding.editTokenPort.setText(cfg.tokenProxyConfig.port.toString())
        binding.editTokenPath.setText(cfg.tokenProxyConfig.path)
        binding.switchTokenHttps.isChecked = cfg.tokenProxyConfig.secure

        binding.editDisplayName.setText(cfg.callerProfile.displayName)
        binding.editCallingNumber.setText(cfg.callerProfile.callingNumber)
        binding.editDestination.setText(cfg.callerProfile.destinationNumber)
        binding.editContext.setText(cfg.routingMetadata.contextId)
        binding.editTopic.setText(cfg.routingMetadata.topic)

        binding.editPriority.setText(cfg.routingMetadata.servicePriority.toString())
        binding.editLocale.setText(cfg.routingMetadata.locale)
        binding.spinnerStrategy.setSelection(
            RoutingStrategyOption.fromApiValue(cfg.routingMetadata.routingStrategy).ordinal
        )

        if (platformType == PlatformType.OCEANA) {
            val attrs = cfg.routingMetadata.attributes
            binding.editAttrAName.setText(attrs.getOrNull(0)?.name.orEmpty())
            binding.editAttrAValue.setText(attrs.getOrNull(0)?.valuesCsv.orEmpty())
            binding.editAttrBName.setText(attrs.getOrNull(1)?.name.orEmpty())
            binding.editAttrBValue.setText(attrs.getOrNull(1)?.valuesCsv.orEmpty())
            binding.editAttrCName.setText(attrs.getOrNull(2)?.name.orEmpty())
            binding.editAttrCValue.setText(attrs.getOrNull(2)?.valuesCsv.orEmpty())
        }

        binding.editSourceName.setText(cfg.routingMetadata.resourceSourceName)
        binding.editResourceId.setText(cfg.routingMetadata.resourceId)

        binding.spinnerLocalVideoStyle.setSelection(cfg.videoSettings.localSurfacePreset.ordinal)
        val resIdx = captureResolutions.indexOf(cfg.videoSettings.capturePreference).coerceAtLeast(0)
        binding.spinnerVideoResolution.setSelection(resIdx)
        val orientIdx = captureOrientations.indexOf(cfg.videoSettings.captureOrientation).coerceAtLeast(0)
        binding.spinnerVideoOrientation.setSelection(orientIdx)
        binding.switchMuteVideoOnBackground.isChecked = cfg.videoSettings.muteVideoWhenBackgrounded

        binding.switchLogToDevice.isChecked = cfg.loggingConfig.logToDevice
        binding.editLogFileName.setText(cfg.loggingConfig.logFileName)
        binding.editSupportEmail.setText(cfg.loggingConfig.supportEmail)

        app.diagnosticLogManager.configure(cfg.loggingConfig)
    }

    private fun setupActionButtons() {
        binding.buttonSaveSettings.setOnClickListener {
            val draft = readDraftFromForm(CallMediaMode.VIDEO)
            if (!InputValidator.isValidEmailOrBlank(draft.loggingConfig.supportEmail)) {
                showStatusError("Support email address is not valid.")
                return@setOnClickListener
            }
            app.settingsStore.save(draft)
            app.diagnosticLogManager.configure(draft.loggingConfig)
            showStatusInfo(getString(R.string.status_saved))
        }

        binding.buttonSendLogs.setOnClickListener {
            val draft = readDraftFromForm(CallMediaMode.VIDEO)
            if (!InputValidator.isValidEmailOrBlank(draft.loggingConfig.supportEmail)) {
                showStatusError("Enter a valid support email address or leave it blank.")
                return@setOnClickListener
            }
            val intent = app.diagnosticLogManager.createShareLogIntent(draft.loggingConfig)
            if (intent == null) {
                showStatusError("No log file found yet. Enable 'Log to device' and run a session first.")
            } else {
                startActivity(Intent.createChooser(intent, getString(R.string.action_send_logs)))
            }
        }

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
        app.settingsStore.save(launchConfig)

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

    private fun saveDraftForCurrentPlatform() {
        app.settingsStore.save(readDraftFromForm(CallMediaMode.VIDEO))
    }

    private fun readDraftFromForm(mode: CallMediaMode): InteractionLaunchConfig {
        val amcHost = binding.editAmcServer.text?.toString()?.trim().orEmpty()
        val amcPort = binding.editAmcPort.text?.toString()?.trim()?.toIntOrNull() ?: 443
        val amcPath = binding.editAmcUrlPath.text?.toString()?.trim().orEmpty()
        val amcHttps = binding.switchAmcHttps.isChecked

        val aawgHost = binding.editAawgServer.text?.toString()?.trim().orEmpty()
        val aawgPort = binding.editAawgPort.text?.toString()?.trim()?.toIntOrNull() ?: 443
        val aawgPath = binding.editAawgUrlPath.text?.toString()?.trim().orEmpty()
        val aawgHttps = binding.switchAawgHttps.isChecked

        val tokenHost = binding.editTokenServer.text?.toString()?.trim().orEmpty()
        val tokenPort = binding.editTokenPort.text?.toString()?.trim()?.toIntOrNull() ?: 443
        val tokenPath = binding.editTokenPath.text?.toString()?.trim().orEmpty()
        val tokenApiKey = binding.editTokenApiKey.text?.toString()?.trim().orEmpty()
        val tokenHttps = binding.switchTokenHttps.isChecked

        val displayName = binding.editDisplayName.text?.toString()?.trim().orEmpty()
        val callingNumber = binding.editCallingNumber.text?.toString()?.trim().orEmpty()
        val destination = binding.editDestination.text?.toString()?.trim().orEmpty()
        val contextId = binding.editContext.text?.toString()?.trim().orEmpty()
        val topic = binding.editTopic.text?.toString()?.trim().orEmpty()

        val priority = binding.editPriority.text?.toString()?.trim()?.toIntOrNull() ?: 5
        val locale = binding.editLocale.text?.toString()?.trim().orEmpty()
        val strategy = strategyOptions[binding.spinnerStrategy.selectedItemPosition].apiValue

        val attrs = listOf(
            AttributeEntry(
                binding.editAttrAName.text?.toString()?.trim().orEmpty(),
                binding.editAttrAValue.text?.toString()?.trim().orEmpty()
            ),
            AttributeEntry(
                binding.editAttrBName.text?.toString()?.trim().orEmpty(),
                binding.editAttrBValue.text?.toString()?.trim().orEmpty()
            ),
            AttributeEntry(
                binding.editAttrCName.text?.toString()?.trim().orEmpty(),
                binding.editAttrCValue.text?.toString()?.trim().orEmpty()
            )
        )

        val sourceName = binding.editSourceName.text?.toString()?.trim().orEmpty()
        val resourceId = binding.editResourceId.text?.toString()?.trim().orEmpty()

        val logToDevice = binding.switchLogToDevice.isChecked
        val logFileName = InputValidator.sanitizeLogFileName(
            binding.editLogFileName.text?.toString().orEmpty()
        )
        val supportEmail = binding.editSupportEmail.text?.toString()?.trim().orEmpty()

        val localPreset = localVideoPresets[binding.spinnerLocalVideoStyle.selectedItemPosition]
        val resolution = captureResolutions[binding.spinnerVideoResolution.selectedItemPosition]
        val orientation = captureOrientations[binding.spinnerVideoOrientation.selectedItemPosition]

        return InteractionLaunchConfig(
            platformType = selectedPlatform,
            mediaMode = mode,
            amcConfig = AmcServerConfig(amcHost, amcPort, amcHttps, amcPath),
            webGatewayConfig = WebGatewayConfigModel(aawgHost, aawgPort, aawgHttps, aawgPath),
            tokenProxyConfig = TokenProxyConfig(tokenHost, tokenPort, tokenHttps, tokenPath, tokenApiKey),
            callerProfile = CallerProfile(destination, callingNumber, displayName),
            routingMetadata = RoutingMetadata(
                attributes = attrs,
                servicePriority = priority,
                contextId = contextId,
                topic = topic,
                locale = locale,
                routingStrategy = strategy,
                resourceSourceName = sourceName,
                resourceId = resourceId
            ),
            loggingConfig = LoggingConfig(logToDevice, logFileName, supportEmail),
            videoSettings = VideoSettings(
                localSurfacePreset = localPreset,
                capturePreference = resolution,
                captureOrientation = orientation,
                muteVideoWhenBackgrounded = binding.switchMuteVideoOnBackground.isChecked
            )
        )
    }

    private fun buildAndValidateConfig(mode: CallMediaMode): InteractionLaunchConfig? {
        val cfg = readDraftFromForm(mode)

        val amcPortRaw = binding.editAmcPort.text?.toString()?.trim()?.toIntOrNull()
        val aawgPortRaw = binding.editAawgPort.text?.toString()?.trim()?.toIntOrNull()
        val tokenPortRaw = binding.editTokenPort.text?.toString()?.trim()?.toIntOrNull()
        val priorityRaw = binding.editPriority.text?.toString()?.trim()?.toIntOrNull()

        if (selectedPlatform == PlatformType.OCEANA) {
            if (!InputValidator.isValidHost(cfg.amcConfig.serverFqdn)) {
                showStatusError("Valid AMC Cluster Address is required for Avaya Oceana.")
                return null
            }
            if (!InputValidator.isValidPort(amcPortRaw)) {
                showStatusError("AMC Cluster Port must be between 1 and 65535.")
                return null
            }
            if (!InputValidator.isValidUrlPath(cfg.amcConfig.urlPath)) {
                showStatusError("AMC URL Path contains invalid characters.")
                return null
            }
            if (!BuildConfig.DEBUG && !cfg.amcConfig.secure) {
                showStatusError("HTTPS is mandatory for AMC connections in release builds.")
                return null
            }
        }

        if (!InputValidator.isValidHost(cfg.webGatewayConfig.serverFqdn)) {
            showStatusError("Valid AAWG Server Address is required.")
            return null
        }
        if (!InputValidator.isValidPort(aawgPortRaw)) {
            showStatusError("AAWG Server Port must be between 1 and 65535.")
            return null
        }
        if (!InputValidator.isValidUrlPath(cfg.webGatewayConfig.urlPath)) {
            showStatusError("AAWG URL Path contains invalid characters.")
            return null
        }
        if (!BuildConfig.DEBUG && !cfg.webGatewayConfig.secure) {
            showStatusError("HTTPS is mandatory for AAWG connections in release builds.")
            return null
        }

        if (!InputValidator.isValidHost(cfg.tokenProxyConfig.serverHost)) {
            showStatusError("Valid Token Service Address is required.")
            return null
        }
        if (!InputValidator.isValidPort(tokenPortRaw)) {
            showStatusError("Token Service Port must be between 1 and 65535.")
            return null
        }
        if (cfg.tokenProxyConfig.path.isBlank() || !InputValidator.isValidUrlPath(cfg.tokenProxyConfig.path)) {
            showStatusError("Valid Token Service URL Path is required.")
            return null
        }
        if (!cfg.tokenProxyConfig.secure && (!BuildConfig.DEBUG || !InputValidator.isLoopbackHost(cfg.tokenProxyConfig.serverHost))) {
            showStatusError("Use HTTPS for Token Service connections (HTTP is limited to local emulator loopback in debug builds).")
            return null
        }

        if (selectedPlatform == PlatformType.ELITE && cfg.callerProfile.destinationNumber.isBlank()) {
            showStatusError("Destination Address is required for Avaya Aura Elite calls.")
            return null
        }
        if (!InputValidator.isValidSipUser(cfg.callerProfile.destinationNumber)) {
            showStatusError("Destination Address contains disallowed SIP characters.")
            return null
        }
        if (!InputValidator.isValidSipUser(cfg.callerProfile.callingNumber)) {
            showStatusError("From Address contains disallowed SIP characters.")
            return null
        }
        if (!InputValidator.isSafeDisplayText(cfg.callerProfile.displayName, 80)) {
            showStatusError("Display Name contains disallowed characters or exceeds 80 chars.")
            return null
        }
        if (!InputValidator.isSafeDisplayText(cfg.routingMetadata.contextId, 128)) {
            showStatusError("Context contains disallowed characters.")
            return null
        }
        if (!InputValidator.isSafeDisplayText(cfg.routingMetadata.topic, 80)) {
            showStatusError("Topic contains disallowed characters.")
            return null
        }
        if (!InputValidator.isValidPriority(priorityRaw)) {
            showStatusError("Service Priority must be between 1 and 10.")
            return null
        }

        return cfg
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
        // Clear any sensitive in-memory API key input and unused token on exit.
        binding.editTokenApiKey.text?.clear()
        if (isFinishing) {
            app.tokenRepository.clearToken()
        }
        super.onDestroy()
    }
}
