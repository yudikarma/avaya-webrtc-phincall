package com.phincall.webrtc.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.ArrayAdapter
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.avaya.ocs.Services.Device.Video.Enums.VideoCaptureOrientation
import com.avaya.ocs.Services.Device.Video.Enums.VideoCapturePreference
import com.avaya.ocs.Services.Work.Enums.PlatformType
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
import com.phincall.webrtc.config.RuntimeConfigManager
import com.phincall.webrtc.config.TokenProxyConfig
import com.phincall.webrtc.config.VideoSettings
import com.phincall.webrtc.config.WebGatewayConfigModel
import com.phincall.webrtc.databinding.ActivitySettingsBinding

/**
 * Runtime configuration screen supporting live switching between Avaya Oceana and
 * Avaya Aura Elite. Changing the platform swaps persisted profiles, updates form
 * visibility/labels immediately, and reconfigures the SDK runtime path.
 */
class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var app: PhinCallApplication

    private var selectedPlatform: PlatformType = PlatformType.OCEANA
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Prevent task-switcher screenshots from capturing the optional proxy API key field.
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        app = application as PhinCallApplication
        val initialConfig = app.runtimeConfigManager.activeConfig.value
        selectedPlatform = initialConfig.platformType

        setupSpinners()
        setupPlatformSwitcher()
        populateForm(initialConfig)
        setupButtons()
        setupBackNavigation()
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
        syncToggleButton(selectedPlatform)

        binding.togglePlatformGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked || suppressPlatformToggle) return@addOnButtonCheckedListener
            val targetPlatform = if (checkedId == R.id.buttonPlatformElite) {
                PlatformType.ELITE
            } else {
                PlatformType.OCEANA
            }
            if (targetPlatform != selectedPlatform) {
                // Persist current platform draft before loading the target platform profile.
                app.runtimeConfigManager.saveAndApply(readFormConfig(strictNumbers = false))
                selectedPlatform = targetPlatform
                val switched = app.runtimeConfigManager.switchPlatform(targetPlatform)
                populateForm(switched)
                hideFeedback()
            }
        }
    }

    private fun syncToggleButton(platformType: PlatformType) {
        suppressPlatformToggle = true
        binding.togglePlatformGroup.check(
            if (platformType == PlatformType.ELITE) {
                R.id.buttonPlatformElite
            } else {
                R.id.buttonPlatformOceana
            }
        )
        suppressPlatformToggle = false
    }

    private fun applyPlatformUiMode(platformType: PlatformType) {
        val isOceana = platformType == PlatformType.OCEANA
        // AMC cluster config and skill attributes apply to Oceana only (Guide 4.0.10, pp. 31, 34).
        binding.panelOceanaAmc.visibility = if (isOceana) View.VISIBLE else View.GONE
        binding.panelOceanaAttributes.visibility = if (isOceana) View.VISIBLE else View.GONE

        binding.textPlatformModeNotice.text = getString(
            if (isOceana) R.string.mode_notice_oceana else R.string.mode_notice_elite
        )
        binding.inputLayoutDestination.hint = getString(
            if (isOceana) R.string.hint_destination_oceana else R.string.hint_destination_elite
        )
        binding.textSettingsSubtitle.text = if (isOceana) {
            "Active profile: ${getString(R.string.platform_oceana)}"
        } else {
            "Active profile: ${getString(R.string.platform_elite)}"
        }
    }

    private fun populateForm(config: InteractionLaunchConfig) {
        selectedPlatform = config.platformType
        syncToggleButton(config.platformType)
        applyPlatformUiMode(config.platformType)

        binding.editAmcServer.setText(config.amcConfig.serverFqdn)
        binding.editAmcPort.setText(config.amcConfig.port.toString())
        binding.editAmcUrlPath.setText(config.amcConfig.urlPath)
        binding.switchAmcHttps.isChecked = config.amcConfig.secure

        binding.editAawgServer.setText(config.webGatewayConfig.serverFqdn)
        binding.editAawgPort.setText(config.webGatewayConfig.port.toString())
        binding.editAawgUrlPath.setText(config.webGatewayConfig.urlPath)
        binding.switchAawgHttps.isChecked = config.webGatewayConfig.secure

        binding.editTokenServer.setText(config.tokenProxyConfig.serverHost)
        binding.editTokenPort.setText(config.tokenProxyConfig.port.toString())
        binding.editTokenPath.setText(config.tokenProxyConfig.path)
        binding.editTokenApiKey.setText(config.tokenProxyConfig.clientApiKey)
        binding.switchTokenHttps.isChecked = config.tokenProxyConfig.secure

        binding.editDisplayName.setText(config.callerProfile.displayName)
        binding.editCallingNumber.setText(config.callerProfile.callingNumber)
        binding.editDestination.setText(config.callerProfile.destinationNumber)
        binding.editContext.setText(config.routingMetadata.contextId)
        binding.editTopic.setText(config.routingMetadata.topic)

        binding.editPriority.setText(config.routingMetadata.servicePriority.toString())
        binding.editLocale.setText(config.routingMetadata.locale)
        binding.spinnerStrategy.setSelection(
            RoutingStrategyOption.fromApiValue(config.routingMetadata.routingStrategy).ordinal
        )

        if (config.platformType == PlatformType.OCEANA) {
            val attrs = config.routingMetadata.attributes
            binding.editAttrAName.setText(attrs.getOrNull(0)?.name.orEmpty())
            binding.editAttrAValue.setText(attrs.getOrNull(0)?.valuesCsv.orEmpty())
            binding.editAttrBName.setText(attrs.getOrNull(1)?.name.orEmpty())
            binding.editAttrBValue.setText(attrs.getOrNull(1)?.valuesCsv.orEmpty())
            binding.editAttrCName.setText(attrs.getOrNull(2)?.name.orEmpty())
            binding.editAttrCValue.setText(attrs.getOrNull(2)?.valuesCsv.orEmpty())
        }

        binding.editSourceName.setText(config.routingMetadata.resourceSourceName)
        binding.editResourceId.setText(config.routingMetadata.resourceId)

        binding.spinnerLocalVideoStyle.setSelection(config.videoSettings.localSurfacePreset.ordinal)
        val resIdx = captureResolutions.indexOf(config.videoSettings.capturePreference).coerceAtLeast(0)
        binding.spinnerVideoResolution.setSelection(resIdx)
        val orientIdx = captureOrientations.indexOf(config.videoSettings.captureOrientation).coerceAtLeast(0)
        binding.spinnerVideoOrientation.setSelection(orientIdx)
        binding.switchMuteVideoOnBackground.isChecked = config.videoSettings.muteVideoWhenBackgrounded

        binding.switchLogToDevice.isChecked = config.loggingConfig.logToDevice
        binding.editLogFileName.setText(config.loggingConfig.logFileName)
        binding.editSupportEmail.setText(config.loggingConfig.supportEmail)
    }

    private fun setupButtons() {
        binding.buttonBack.setOnClickListener {
            saveDraftAndClose()
        }

        binding.buttonResetDefaults.setOnClickListener {
            val defaults = app.runtimeConfigManager.resetPlatformToDefaults(selectedPlatform)
            populateForm(defaults)
            showFeedback(getString(R.string.status_reset), isError = false)
        }

        binding.buttonSendLogs.setOnClickListener {
            val draft = readFormConfig(strictNumbers = false)
            if (!InputValidator.isValidEmailOrBlank(draft.loggingConfig.supportEmail)) {
                showFeedback("Enter a valid support email address or leave it blank.", isError = true)
                return@setOnClickListener
            }
            val intent = app.diagnosticLogManager.createShareLogIntent(draft.loggingConfig)
            if (intent == null) {
                showFeedback(
                    "No log file found yet. Enable 'Log to device' and run a session first.",
                    isError = true
                )
            } else {
                startActivity(Intent.createChooser(intent, getString(R.string.action_send_logs)))
            }
        }

        binding.buttonApplySettings.setOnClickListener {
            val strictConfig = readFormConfig(strictNumbers = true)
            val issues = RuntimeConfigManager.validateForLaunch(strictConfig)

            // Persist safe draft values so valid entries are never lost when fixing remaining fields.
            app.runtimeConfigManager.saveAndApply(readFormConfig(strictNumbers = false))

            if (issues.isEmpty()) {
                finish()
            } else {
                showFeedback(issues.first().message, isError = true)
            }
        }
    }

    private fun setupBackNavigation() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                saveDraftAndClose()
            }
        })
    }

    private fun saveDraftAndClose() {
        app.runtimeConfigManager.saveAndApply(readFormConfig(strictNumbers = false))
        finish()
    }

    private fun readFormConfig(strictNumbers: Boolean): InteractionLaunchConfig {
        val amcHost = binding.editAmcServer.text?.toString()?.trim().orEmpty()
        val amcPort = parseIntField(binding.editAmcPort.text?.toString(), fallback = 443, strictNumbers)
        val amcPath = binding.editAmcUrlPath.text?.toString()?.trim().orEmpty()
        val amcHttps = binding.switchAmcHttps.isChecked

        val aawgHost = binding.editAawgServer.text?.toString()?.trim().orEmpty()
        val aawgPort = parseIntField(binding.editAawgPort.text?.toString(), fallback = 443, strictNumbers)
        val aawgPath = binding.editAawgUrlPath.text?.toString()?.trim().orEmpty()
        val aawgHttps = binding.switchAawgHttps.isChecked

        val tokenHost = binding.editTokenServer.text?.toString()?.trim().orEmpty()
        val tokenPort = parseIntField(binding.editTokenPort.text?.toString(), fallback = 443, strictNumbers)
        val tokenPath = binding.editTokenPath.text?.toString()?.trim().orEmpty()
        val tokenApiKey = binding.editTokenApiKey.text?.toString()?.trim().orEmpty()
        val tokenHttps = binding.switchTokenHttps.isChecked

        val displayName = binding.editDisplayName.text?.toString()?.trim().orEmpty()
        val callingNumber = binding.editCallingNumber.text?.toString()?.trim().orEmpty()
        val destination = binding.editDestination.text?.toString()?.trim().orEmpty()
        val contextId = binding.editContext.text?.toString()?.trim().orEmpty()
        val topic = binding.editTopic.text?.toString()?.trim().orEmpty()

        val priority = parseIntField(binding.editPriority.text?.toString(), fallback = 5, strictNumbers)
        val locale = binding.editLocale.text?.toString()?.trim().orEmpty()
        val strategy = strategyOptions[binding.spinnerStrategy.selectedItemPosition].apiValue

        val attrs = if (selectedPlatform == PlatformType.OCEANA) {
            listOf(
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
        } else {
            emptyList()
        }

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
            mediaMode = CallMediaMode.VIDEO,
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

    private fun parseIntField(raw: String?, fallback: Int, strict: Boolean): Int {
        val parsed = raw?.trim()?.toIntOrNull()
        return if (strict) {
            parsed ?: -1
        } else {
            parsed ?: fallback
        }
    }

    private fun showFeedback(message: String, isError: Boolean) {
        binding.textSettingsFeedback.visibility = View.VISIBLE
        binding.textSettingsFeedback.text = message
        binding.textSettingsFeedback.setTextColor(
            ContextCompat.getColor(
                this,
                if (isError) R.color.status_poor_light else R.color.ink_secondary
            )
        )
    }

    private fun hideFeedback() {
        binding.textSettingsFeedback.visibility = View.GONE
    }

    override fun onDestroy() {
        // Clear the widget buffer; the active in-memory session key stays inside RuntimeConfigManager.
        binding.editTokenApiKey.text?.clear()
        super.onDestroy()
    }
}
