package com.phincall.webrtc.ui.settings

import com.avaya.ocs.Services.Device.Video.Enums.VideoCaptureOrientation
import com.avaya.ocs.Services.Device.Video.Enums.VideoCapturePreference
import com.avaya.ocs.Services.Work.Enums.PlatformType
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
import com.phincall.webrtc.config.SampleConfigurations
import com.phincall.webrtc.config.TokenProxyConfig
import com.phincall.webrtc.config.VideoSettings
import com.phincall.webrtc.config.WebGatewayConfigModel

data class SettingsFormState(
    val platformType: PlatformType = PlatformType.OCEANA,
    val amcServer: String = "",
    val amcPort: String = "443",
    val amcUrlPath: String = SampleConfigurations.DEFAULT_AMC_URL_PATH,
    val amcHttps: Boolean = true,
    val aawgServer: String = "",
    val aawgPort: String = "443",
    val aawgUrlPath: String = SampleConfigurations.DEFAULT_AAWG_URL_PATH,
    val aawgHttps: Boolean = true,
    val tokenServer: String = SampleConfigurations.DEFAULT_TOKEN_SERVER,
    val tokenPort: String = SampleConfigurations.DEFAULT_TOKEN_PORT.toString(),
    val tokenPath: String = SampleConfigurations.DEFAULT_TOKEN_URL_PATH,
    val tokenApiKey: String = "",
    val tokenHttps: Boolean = true,
    val displayName: String = "",
    val callingNumber: String = "",
    val destination: String = "",
    val contextId: String = "",
    val topic: String = "",
    val priority: String = SampleConfigurations.DEFAULT_PRIORITY.toString(),
    val locale: String = SampleConfigurations.DEFAULT_LOCALE,
    val routingStrategy: RoutingStrategyOption = RoutingStrategyOption.MOST_IDLE,
    val attrAName: String = "Location",
    val attrAValue: String = "Inhouse",
    val attrBName: String = "Language",
    val attrBValue: String = "English",
    val attrCName: String = "Service",
    val attrCValue: String = "Sales",
    val sourceName: String = "",
    val resourceId: String = "",
    val localVideoPreset: LocalVideoSurfacePreset = LocalVideoSurfacePreset.ROUNDED_24_2,
    val capturePreference: VideoCapturePreference = VideoCapturePreference.VideoCapturePreference_540p,
    val captureOrientation: VideoCaptureOrientation = VideoCaptureOrientation.VideoCaptureOrientation_LandscapeOrPortrait,
    val muteVideoOnBackground: Boolean = false,
    val logToDevice: Boolean = false,
    val logFileName: String = SampleConfigurations.DEFAULT_LOG_FILE_NAME,
    val supportEmail: String = "",
    val feedbackMessage: String? = null,
    val isFeedbackError: Boolean = false
) {
    fun toLaunchConfig(strictNumbers: Boolean): InteractionLaunchConfig {
        fun parseNum(raw: String, fallback: Int): Int {
            val parsed = raw.trim().toIntOrNull()
            return if (strictNumbers) parsed ?: -1 else parsed ?: fallback
        }

        val attrs = if (platformType == PlatformType.OCEANA) {
            listOf(
                AttributeEntry(attrAName.trim(), attrAValue.trim()),
                AttributeEntry(attrBName.trim(), attrBValue.trim()),
                AttributeEntry(attrCName.trim(), attrCValue.trim())
            )
        } else {
            emptyList()
        }

        return InteractionLaunchConfig(
            platformType = platformType,
            mediaMode = CallMediaMode.VIDEO,
            amcConfig = AmcServerConfig(
                serverFqdn = amcServer.trim(),
                port = parseNum(amcPort, 443),
                secure = amcHttps,
                urlPath = amcUrlPath.trim()
            ),
            webGatewayConfig = WebGatewayConfigModel(
                serverFqdn = aawgServer.trim(),
                port = parseNum(aawgPort, 443),
                secure = aawgHttps,
                urlPath = aawgUrlPath.trim()
            ),
            tokenProxyConfig = TokenProxyConfig(
                serverHost = tokenServer.trim(),
                port = parseNum(tokenPort, 443),
                secure = tokenHttps,
                path = tokenPath.trim(),
                clientApiKey = tokenApiKey.trim()
            ),
            callerProfile = CallerProfile(
                destinationNumber = destination.trim(),
                callingNumber = callingNumber.trim(),
                displayName = displayName.trim()
            ),
            routingMetadata = RoutingMetadata(
                attributes = attrs,
                servicePriority = parseNum(priority, SampleConfigurations.DEFAULT_PRIORITY),
                contextId = contextId.trim(),
                topic = topic.trim(),
                locale = locale.trim(),
                routingStrategy = routingStrategy.apiValue,
                resourceSourceName = sourceName.trim(),
                resourceId = resourceId.trim()
            ),
            loggingConfig = LoggingConfig(
                logToDevice = logToDevice,
                logFileName = InputValidator.sanitizeLogFileName(logFileName),
                supportEmail = supportEmail.trim()
            ),
            videoSettings = VideoSettings(
                localSurfacePreset = localVideoPreset,
                capturePreference = capturePreference,
                captureOrientation = captureOrientation,
                muteVideoWhenBackgrounded = muteVideoOnBackground
            )
        )
    }

    companion object {
        fun fromLaunchConfig(config: InteractionLaunchConfig): SettingsFormState {
            val attrs = config.routingMetadata.attributes
            return SettingsFormState(
                platformType = config.platformType,
                amcServer = config.amcConfig.serverFqdn,
                amcPort = config.amcConfig.port.toString(),
                amcUrlPath = config.amcConfig.urlPath,
                amcHttps = config.amcConfig.secure,
                aawgServer = config.webGatewayConfig.serverFqdn,
                aawgPort = config.webGatewayConfig.port.toString(),
                aawgUrlPath = config.webGatewayConfig.urlPath,
                aawgHttps = config.webGatewayConfig.secure,
                tokenServer = config.tokenProxyConfig.serverHost,
                tokenPort = config.tokenProxyConfig.port.toString(),
                tokenPath = config.tokenProxyConfig.path,
                tokenApiKey = config.tokenProxyConfig.clientApiKey,
                tokenHttps = config.tokenProxyConfig.secure,
                displayName = config.callerProfile.displayName,
                callingNumber = config.callerProfile.callingNumber,
                destination = config.callerProfile.destinationNumber,
                contextId = config.routingMetadata.contextId,
                topic = config.routingMetadata.topic,
                priority = config.routingMetadata.servicePriority.toString(),
                locale = config.routingMetadata.locale,
                routingStrategy = RoutingStrategyOption.fromApiValue(config.routingMetadata.routingStrategy),
                attrAName = attrs.getOrNull(0)?.name.orEmpty(),
                attrAValue = attrs.getOrNull(0)?.valuesCsv.orEmpty(),
                attrBName = attrs.getOrNull(1)?.name.orEmpty(),
                attrBValue = attrs.getOrNull(1)?.valuesCsv.orEmpty(),
                attrCName = attrs.getOrNull(2)?.name.orEmpty(),
                attrCValue = attrs.getOrNull(2)?.valuesCsv.orEmpty(),
                sourceName = config.routingMetadata.resourceSourceName,
                resourceId = config.routingMetadata.resourceId,
                localVideoPreset = config.videoSettings.localSurfacePreset,
                capturePreference = config.videoSettings.capturePreference,
                captureOrientation = config.videoSettings.captureOrientation,
                muteVideoOnBackground = config.videoSettings.muteVideoWhenBackgrounded,
                logToDevice = config.loggingConfig.logToDevice,
                logFileName = config.loggingConfig.logFileName,
                supportEmail = config.loggingConfig.supportEmail
            )
        }
    }
}
