package com.phincall.webrtc.config

import android.content.Context
import com.avaya.ocs.Services.Device.Video.Enums.VideoCaptureOrientation
import com.avaya.ocs.Services.Device.Video.Enums.VideoCapturePreference
import com.avaya.ocs.Services.Work.Enums.PlatformType

/**
 * Persists non-secret configuration settings separately for Oceana and Elite profiles,
 * mirroring SettingsService / SettingsServiceElite in the Avaya Reference Client.
 * Authorization tokens and proxy API keys are intentionally excluded from disk storage.
 */
class SettingsStore(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun loadLastPlatform(): PlatformType {
        val raw = prefs.getString(KEY_LAST_PLATFORM, PlatformType.OCEANA.name)
        return runCatching { PlatformType.valueOf(raw ?: PlatformType.OCEANA.name) }
            .getOrDefault(PlatformType.OCEANA)
    }

    fun saveLastPlatform(platformType: PlatformType) {
        prefs.edit().putString(KEY_LAST_PLATFORM, platformType.name).apply()
    }

    fun load(platformType: PlatformType): InteractionLaunchConfig {
        val defaults = SampleConfigurations.defaultFor(platformType)
        val p = prefixFor(platformType)

        val defaultAttrs = SampleConfigurations.defaultAttributesForOceana()
        val attributes = if (platformType == PlatformType.OCEANA) {
            listOf(
                AttributeEntry(
                    prefs.getString("${p}attr_a_name", defaultAttrs[0].name).orEmpty(),
                    prefs.getString("${p}attr_a_val", defaultAttrs[0].valuesCsv).orEmpty()
                ),
                AttributeEntry(
                    prefs.getString("${p}attr_b_name", defaultAttrs[1].name).orEmpty(),
                    prefs.getString("${p}attr_b_val", defaultAttrs[1].valuesCsv).orEmpty()
                ),
                AttributeEntry(
                    prefs.getString("${p}attr_c_name", defaultAttrs[2].name).orEmpty(),
                    prefs.getString("${p}attr_c_val", defaultAttrs[2].valuesCsv).orEmpty()
                )
            )
        } else {
            emptyList()
        }

        val presetName = prefs.getString("${p}local_preset", defaults.videoSettings.localSurfacePreset.name)
        val preset = runCatching {
            LocalVideoSurfacePreset.valueOf(presetName ?: defaults.videoSettings.localSurfacePreset.name)
        }.getOrDefault(defaults.videoSettings.localSurfacePreset)

        val resName = prefs.getString("${p}video_res", defaults.videoSettings.capturePreference.name)
        val resolution = runCatching {
            VideoCapturePreference.valueOf(resName ?: defaults.videoSettings.capturePreference.name)
        }.getOrDefault(defaults.videoSettings.capturePreference)

        val orientName = prefs.getString("${p}video_orient", defaults.videoSettings.captureOrientation.name)
        val orientation = runCatching {
            VideoCaptureOrientation.valueOf(orientName ?: defaults.videoSettings.captureOrientation.name)
        }.getOrDefault(defaults.videoSettings.captureOrientation)

        return InteractionLaunchConfig(
            platformType = platformType,
            mediaMode = CallMediaMode.VIDEO,
            amcConfig = AmcServerConfig(
                serverFqdn = prefs.getString("${p}amc_server", defaults.amcConfig.serverFqdn).orEmpty(),
                port = prefs.getInt("${p}amc_port", defaults.amcConfig.port),
                secure = prefs.getBoolean("${p}amc_secure", defaults.amcConfig.secure),
                urlPath = prefs.getString("${p}amc_path", defaults.amcConfig.urlPath).orEmpty()
            ),
            webGatewayConfig = WebGatewayConfigModel(
                serverFqdn = prefs.getString("${p}aawg_server", defaults.webGatewayConfig.serverFqdn).orEmpty(),
                port = prefs.getInt("${p}aawg_port", defaults.webGatewayConfig.port),
                secure = prefs.getBoolean("${p}aawg_secure", defaults.webGatewayConfig.secure),
                urlPath = prefs.getString("${p}aawg_path", defaults.webGatewayConfig.urlPath).orEmpty()
            ),
            tokenProxyConfig = TokenProxyConfig(
                serverHost = prefs.getString("${p}token_server", defaults.tokenProxyConfig.serverHost).orEmpty(),
                port = prefs.getInt("${p}token_port", defaults.tokenProxyConfig.port),
                secure = prefs.getBoolean("${p}token_secure", defaults.tokenProxyConfig.secure),
                path = prefs.getString("${p}token_path", defaults.tokenProxyConfig.path).orEmpty(),
                clientApiKey = ""
            ),
            callerProfile = CallerProfile(
                destinationNumber = prefs.getString("${p}dest", defaults.callerProfile.destinationNumber).orEmpty(),
                callingNumber = prefs.getString("${p}from_addr", defaults.callerProfile.callingNumber).orEmpty(),
                displayName = prefs.getString("${p}display_name", defaults.callerProfile.displayName).orEmpty()
            ),
            routingMetadata = RoutingMetadata(
                attributes = attributes,
                servicePriority = prefs.getInt("${p}priority", defaults.routingMetadata.servicePriority),
                contextId = prefs.getString("${p}context", defaults.routingMetadata.contextId).orEmpty(),
                topic = prefs.getString("${p}topic", defaults.routingMetadata.topic).orEmpty(),
                locale = prefs.getString("${p}locale", defaults.routingMetadata.locale).orEmpty(),
                routingStrategy = prefs.getString("${p}strategy", defaults.routingMetadata.routingStrategy).orEmpty(),
                resourceSourceName = prefs.getString("${p}source_name", defaults.routingMetadata.resourceSourceName).orEmpty(),
                resourceId = prefs.getString("${p}resource_id", defaults.routingMetadata.resourceId).orEmpty()
            ),
            loggingConfig = LoggingConfig(
                logToDevice = prefs.getBoolean("${p}log_to_device", defaults.loggingConfig.logToDevice),
                logFileName = prefs.getString("${p}log_file_name", defaults.loggingConfig.logFileName).orEmpty(),
                supportEmail = prefs.getString("${p}support_email", defaults.loggingConfig.supportEmail).orEmpty()
            ),
            videoSettings = VideoSettings(
                localSurfacePreset = preset,
                capturePreference = resolution,
                captureOrientation = orientation,
                muteVideoWhenBackgrounded = prefs.getBoolean("${p}mute_bg_video", defaults.videoSettings.muteVideoWhenBackgrounded)
            )
        )
    }

    fun save(config: InteractionLaunchConfig) {
        val p = prefixFor(config.platformType)
        prefs.edit().apply {
            putString(KEY_LAST_PLATFORM, config.platformType.name)

            putString("${p}amc_server", config.amcConfig.serverFqdn)
            putInt("${p}amc_port", config.amcConfig.port)
            putBoolean("${p}amc_secure", config.amcConfig.secure)
            putString("${p}amc_path", config.amcConfig.urlPath)

            putString("${p}aawg_server", config.webGatewayConfig.serverFqdn)
            putInt("${p}aawg_port", config.webGatewayConfig.port)
            putBoolean("${p}aawg_secure", config.webGatewayConfig.secure)
            putString("${p}aawg_path", config.webGatewayConfig.urlPath)

            putString("${p}token_server", config.tokenProxyConfig.serverHost)
            putInt("${p}token_port", config.tokenProxyConfig.port)
            putBoolean("${p}token_secure", config.tokenProxyConfig.secure)
            putString("${p}token_path", config.tokenProxyConfig.path)

            putString("${p}dest", config.callerProfile.destinationNumber)
            putString("${p}from_addr", config.callerProfile.callingNumber)
            putString("${p}display_name", config.callerProfile.displayName)

            putInt("${p}priority", config.routingMetadata.servicePriority)
            putString("${p}context", config.routingMetadata.contextId)
            putString("${p}topic", config.routingMetadata.topic)
            putString("${p}locale", config.routingMetadata.locale)
            putString("${p}strategy", config.routingMetadata.routingStrategy)
            putString("${p}source_name", config.routingMetadata.resourceSourceName)
            putString("${p}resource_id", config.routingMetadata.resourceId)

            if (config.platformType == PlatformType.OCEANA) {
                val attrs = config.routingMetadata.attributes
                putString("${p}attr_a_name", attrs.getOrNull(0)?.name.orEmpty())
                putString("${p}attr_a_val", attrs.getOrNull(0)?.valuesCsv.orEmpty())
                putString("${p}attr_b_name", attrs.getOrNull(1)?.name.orEmpty())
                putString("${p}attr_b_val", attrs.getOrNull(1)?.valuesCsv.orEmpty())
                putString("${p}attr_c_name", attrs.getOrNull(2)?.name.orEmpty())
                putString("${p}attr_c_val", attrs.getOrNull(2)?.valuesCsv.orEmpty())
            }

            putBoolean("${p}log_to_device", config.loggingConfig.logToDevice)
            putString("${p}log_file_name", config.loggingConfig.logFileName)
            putString("${p}support_email", config.loggingConfig.supportEmail)

            putString("${p}local_preset", config.videoSettings.localSurfacePreset.name)
            putString("${p}video_res", config.videoSettings.capturePreference.name)
            putString("${p}video_orient", config.videoSettings.captureOrientation.name)
            putBoolean("${p}mute_bg_video", config.videoSettings.muteVideoWhenBackgrounded)
        }.apply()
    }

    private fun prefixFor(platformType: PlatformType): String =
        if (platformType == PlatformType.OCEANA) "oceana_" else "elite_"

    companion object {
        private const val PREFS_NAME = "phincall_avaya_settings"
        private const val KEY_LAST_PLATFORM = "last_platform"
    }
}
