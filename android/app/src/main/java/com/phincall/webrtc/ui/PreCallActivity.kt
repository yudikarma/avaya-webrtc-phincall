package com.phincall.webrtc.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
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
import com.phincall.webrtc.config.CallOutcome
import com.phincall.webrtc.config.InteractionLaunchConfig
import com.phincall.webrtc.config.RecentCallRecord
import com.phincall.webrtc.config.RuntimeConfigManager
import com.phincall.webrtc.databinding.ActivityPreCallBinding
import com.phincall.webrtc.databinding.ItemRecentCallBinding
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Home dialer and Click-to-Call console.
 * Provides a one-tap Oceana/Elite switcher in the safe-area top bar, upgraded
 * Voice and Video call controls, Recent Calls with instant redial, and a
 * floating Keypad button for dialing arbitrary destination numbers.
 */
class PreCallActivity : AppCompatActivity(), DialpadBottomSheetDialog.DialpadHost {

    private lateinit var binding: ActivityPreCallBinding
    private lateinit var app: PhinCallApplication

    private var pendingCallRequest: Pair<CallMediaMode, String?>? = null
    private var isFetchingToken = false

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val pending = pendingCallRequest ?: return@registerForActivityResult
        pendingCallRequest = null
        val (mode, destinationOverride) = pending

        val micGranted = results[Manifest.permission.RECORD_AUDIO]
            ?: hasPermission(Manifest.permission.RECORD_AUDIO)
        val camGranted = mode != CallMediaMode.VIDEO ||
            (results[Manifest.permission.CAMERA] ?: hasPermission(Manifest.permission.CAMERA))

        if (micGranted && camGranted) {
            beginClickToCallSequence(mode, destinationOverride)
        } else {
            val missing = buildList {
                if (!micGranted) add("Microphone")
                if (!camGranted) add("Camera")
            }.joinToString(" and ")
            showStatusError("$missing permission is required to place a ${mode.name.lowercase()} call.")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowInsetsHelper.enableSafeEdgeToEdge(
            activity = this,
            darkStatusBarIcons = false,
            darkNavigationBarIcons = true
        )

        binding = ActivityPreCallBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Ensure notification bar / display cutout and bottom navigation bar have clean safe areas.
        WindowInsetsHelper.applyTopSafeArea(binding.topAppBar)
        WindowInsetsHelper.applyBottomSafeArea(binding.scrollHomeContent)
        WindowInsetsHelper.applyBottomMarginSafeArea(binding.fabDialpad)

        app = application as PhinCallApplication

        setupActionButtons()
        observeRuntimeAndHistoryState()
    }

    private fun setupActionButtons() {
        val openSettingsAction = View.OnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        binding.buttonOpenSettings.setOnClickListener(openSettingsAction)
        binding.buttonConfigureProfile.setOnClickListener(openSettingsAction)
        binding.buttonBannerSettings.setOnClickListener(openSettingsAction)

        binding.buttonSwitchPlatform.setOnClickListener {
            val next = if (app.runtimeConfigManager.currentPlatform == PlatformType.OCEANA) {
                PlatformType.ELITE
            } else {
                PlatformType.OCEANA
            }
            app.runtimeConfigManager.switchPlatform(next)
        }

        binding.buttonLiveHelpVoice.setOnClickListener {
            requestPermissionsAndStart(CallMediaMode.AUDIO, destinationOverride = null)
        }
        binding.buttonLiveHelpVideo.setOnClickListener {
            requestPermissionsAndStart(CallMediaMode.VIDEO, destinationOverride = null)
        }

        binding.buttonClearRecent.setOnClickListener {
            app.callHistoryStore.clearAll()
        }

        binding.fabDialpad.setOnClickListener {
            val currentDest = app.runtimeConfigManager.activeConfig.value.callerProfile.destinationNumber
            openDialpadSheet(currentDest)
        }
    }

    private fun openDialpadSheet(initialNumber: String) {
        if (supportFragmentManager.findFragmentByTag(DialpadBottomSheetDialog.TAG) != null) return
        DialpadBottomSheetDialog.newInstance(initialNumber)
            .show(supportFragmentManager, DialpadBottomSheetDialog.TAG)
    }

    override fun onDialpadCallRequested(destinationNumber: String, mode: CallMediaMode) {
        requestPermissionsAndStart(mode, destinationOverride = destinationNumber)
    }

    private fun observeRuntimeAndHistoryState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(
                    app.runtimeConfigManager.activeConfig,
                    app.interactionCoordinator.state,
                    app.callHistoryStore.history
                ) { config, callState, recentCalls ->
                    Triple(config, callState, recentCalls)
                }.collect { (config, snapshot, recentCalls) ->
                    renderHeaderAndQuickCard(config)
                    renderRecentCalls(recentCalls, locked = snapshot.isLocked || isFetchingToken)

                    val locked = snapshot.isLocked || isFetchingToken
                    setCallControlsEnabled(!locked)

                    if (!isFetchingToken) {
                        binding.progressLoading.visibility = View.GONE
                        val issues = RuntimeConfigManager.validateForLaunch(config)
                        when {
                            snapshot.isLocked -> showStatusInfo(getString(R.string.status_in_call_locked), showSettingsButton = false)
                            !snapshot.errorMessage.isNullOrBlank() -> showStatusError(snapshot.errorMessage)
                            issues.isNotEmpty() -> showStatusInfo(issues.first().message, showSettingsButton = true)
                            else -> hideStatusBanner()
                        }
                    }
                }
            }
        }
    }

    private fun renderHeaderAndQuickCard(config: InteractionLaunchConfig) {
        val isOceana = config.platformType == PlatformType.OCEANA

        binding.textHeaderPlatformSubtitle.text = getString(
            if (isOceana) R.string.platform_oceana else R.string.platform_elite
        )
        binding.textPlatformPillLabel.text = getString(
            if (isOceana) R.string.platform_short_oceana else R.string.platform_short_elite
        )

        val dest = config.callerProfile.destinationNumber.trim()
        binding.textQuickTargetTitle.text = when {
            dest.isNotEmpty() -> "Target: $dest"
            isOceana -> "Live Help (${getString(R.string.recent_default_oceana)})"
            else -> "Enter destination via Keypad or Settings"
        }

        val aawgHost = config.webGatewayConfig.serverFqdn.ifBlank { getString(R.string.not_configured) }
        val tokenHost = config.tokenProxyConfig.serverHost.ifBlank { getString(R.string.not_configured) }
        val modeDetail = if (isOceana) {
            val amcHost = config.amcConfig.serverFqdn.ifBlank { getString(R.string.not_configured) }
            "AMC: $amcHost | AAWG: $aawgHost\nToken: $tokenHost:${config.tokenProxyConfig.port}"
        } else {
            "AAWG: $aawgHost | Token: $tokenHost:${config.tokenProxyConfig.port}"
        }
        binding.textQuickTargetMeta.text = modeDetail
    }

    private fun renderRecentCalls(records: List<RecentCallRecord>, locked: Boolean) {
        binding.containerRecentCalls.removeAllViews()
        val hasRecords = records.isNotEmpty()
        binding.layoutRecentEmpty.visibility = if (hasRecords) View.GONE else View.VISIBLE
        binding.buttonClearRecent.visibility = if (hasRecords) View.VISIBLE else View.GONE

        if (!hasRecords) return

        val inflater = LayoutInflater.from(this)
        val dividerHeightPx = (1 * resources.displayMetrics.density).toInt().coerceAtLeast(1)
        val dividerColor = ContextCompat.getColor(this, R.color.border_hairline)

        records.forEachIndexed { index, record ->
            val rowBinding = ItemRecentCallBinding.inflate(inflater, binding.containerRecentCalls, false)

            val isVideo = record.mediaMode == CallMediaMode.VIDEO
            rowBinding.badgeCallType.setBackgroundResource(
                if (isVideo) R.drawable.bg_circle_video else R.drawable.bg_circle_audio
            )
            rowBinding.iconCallType.setImageResource(
                if (isVideo) R.drawable.ic_video_on else R.drawable.ic_call_audio
            )

            rowBinding.textRecentDestination.text = record.destinationNumber.ifBlank {
                getString(R.string.recent_default_oceana)
            }
            rowBinding.textRecentPlatformTag.text = if (record.platformType == PlatformType.ELITE) {
                getString(R.string.platform_short_elite)
            } else {
                getString(R.string.platform_short_oceana)
            }

            val timeAgo = DateUtils.getRelativeTimeSpanString(
                record.timestampMillis,
                System.currentTimeMillis(),
                DateUtils.MINUTE_IN_MILLIS,
                DateUtils.FORMAT_ABBREV_RELATIVE
            )
            val outcomeLabel = when (record.outcome) {
                CallOutcome.CONNECTED -> formatShortDuration(record.durationMillis)
                CallOutcome.FAILED -> "Failed"
                CallOutcome.INITIATED -> "Ended"
            }
            val mediaLabel = if (isVideo) "Video" else "Voice"
            rowBinding.textRecentMeta.text = "$mediaLabel | $outcomeLabel | $timeAgo"

            rowBinding.root.setOnClickListener {
                openDialpadSheet(record.destinationNumber)
            }

            rowBinding.buttonRowAudioCall.isEnabled = !locked
            rowBinding.buttonRowVideoCall.isEnabled = !locked
            rowBinding.buttonRowAudioCall.alpha = if (locked) 0.45f else 1.0f
            rowBinding.buttonRowVideoCall.alpha = if (locked) 0.45f else 1.0f

            rowBinding.buttonRowAudioCall.setOnClickListener {
                requestPermissionsAndStart(CallMediaMode.AUDIO, destinationOverride = record.destinationNumber)
            }
            rowBinding.buttonRowVideoCall.setOnClickListener {
                requestPermissionsAndStart(CallMediaMode.VIDEO, destinationOverride = record.destinationNumber)
            }

            binding.containerRecentCalls.addView(rowBinding.root)

            if (index < records.lastIndex) {
                val divider = View(this).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        dividerHeightPx
                    )
                    setBackgroundColor(dividerColor)
                }
                binding.containerRecentCalls.addView(divider)
            }
        }
    }

    private fun formatShortDuration(durationMillis: Long): String {
        val totalSeconds = (durationMillis.coerceAtLeast(0L) / 1000L)
        val minutes = totalSeconds / 60L
        val seconds = totalSeconds % 60L
        return String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }

    private fun requestPermissionsAndStart(mode: CallMediaMode, destinationOverride: String?) {
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
            beginClickToCallSequence(mode, destinationOverride)
        } else {
            pendingCallRequest = mode to destinationOverride
            permissionLauncher.launch(missing.toTypedArray())
        }
    }

    private fun hasPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

    private fun beginClickToCallSequence(mode: CallMediaMode, destinationOverride: String?) {
        val base = app.runtimeConfigManager.activeConfig.value
        val updatedConfig = if (destinationOverride != null) {
            val withDialedDest = base.copy(
                callerProfile = base.callerProfile.copy(destinationNumber = destinationOverride.trim())
            )
            app.runtimeConfigManager.saveAndApply(withDialedDest)
            withDialedDest.copy(mediaMode = mode)
        } else {
            base.copy(mediaMode = mode)
        }

        val issues = RuntimeConfigManager.validateForLaunch(updatedConfig)
        if (issues.isNotEmpty()) {
            showStatusError("${issues.first().message} Open Settings to update.")
            return
        }

        // Disable call buttons immediately until the interaction terminates (Guide 4.0.10, p. 33).
        if (!app.interactionCoordinator.tryLockForCall(updatedConfig)) {
            showStatusError("An interaction is already in progress.")
            return
        }

        isFetchingToken = true
        setCallControlsEnabled(false)
        showStatusLoading(getString(R.string.status_fetching_token))

        lifecycleScope.launch {
            val tokenResult = app.tokenRepository.fetchToken(
                proxyConfig = updatedConfig.tokenProxyConfig,
                callerProfile = updatedConfig.callerProfile
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
                    setCallControlsEnabled(true)
                }
            )
        }
    }

    private fun setCallControlsEnabled(enabled: Boolean) {
        val alpha = if (enabled) 1.0f else 0.45f
        binding.buttonLiveHelpVoice.isEnabled = enabled
        binding.buttonLiveHelpVideo.isEnabled = enabled
        binding.buttonLiveHelpVoice.alpha = alpha
        binding.buttonLiveHelpVideo.alpha = alpha
        binding.fabDialpad.isEnabled = enabled
        binding.fabDialpad.alpha = alpha
    }

    private fun showStatusLoading(message: String) {
        binding.statusBanner.visibility = View.VISIBLE
        binding.progressLoading.visibility = View.VISIBLE
        binding.buttonBannerSettings.visibility = View.GONE
        binding.textStatusMessage.setTextColor(ContextCompat.getColor(this, R.color.ink_primary))
        binding.textStatusMessage.text = message
    }

    private fun showStatusInfo(message: String, showSettingsButton: Boolean) {
        binding.statusBanner.visibility = View.VISIBLE
        binding.progressLoading.visibility = View.GONE
        binding.buttonBannerSettings.visibility = if (showSettingsButton) View.VISIBLE else View.GONE
        binding.textStatusMessage.setTextColor(ContextCompat.getColor(this, R.color.ink_secondary))
        binding.textStatusMessage.text = message
    }

    private fun showStatusError(message: String) {
        binding.statusBanner.visibility = View.VISIBLE
        binding.progressLoading.visibility = View.GONE
        binding.buttonBannerSettings.visibility = View.VISIBLE
        binding.textStatusMessage.setTextColor(ContextCompat.getColor(this, R.color.status_poor_light))
        binding.textStatusMessage.text = message
    }

    private fun hideStatusBanner() {
        binding.statusBanner.visibility = View.GONE
    }

    override fun onDestroy() {
        if (isFinishing) {
            app.tokenRepository.clearToken()
            app.runtimeConfigManager.clearInMemorySecrets()
        }
        super.onDestroy()
    }
}
