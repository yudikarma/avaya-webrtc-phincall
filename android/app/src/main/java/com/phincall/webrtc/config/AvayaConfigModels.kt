package com.phincall.webrtc.config

import androidx.annotation.LayoutRes
import com.avaya.ocs.Services.Device.Video.Enums.VideoCaptureOrientation
import com.avaya.ocs.Services.Device.Video.Enums.VideoCapturePreference
import com.avaya.ocs.Services.Work.Enums.PlatformType
import com.phincall.webrtc.R

enum class CallMediaMode {
    AUDIO,
    VIDEO
}

enum class RoutingStrategyOption(val apiValue: String) {
    MOST_IDLE("MOST_IDLE"),
    LEAST_OCCUPIED("LEAST_OCCUPIED");

    companion object {
        fun fromApiValue(value: String?): RoutingStrategyOption =
            entries.firstOrNull { it.apiValue.equals(value?.trim(), ignoreCase = true) } ?: MOST_IDLE
    }
}

/**
 * Local VideoSurfaceView presets mapped to XML attributes (Guide 4.0.10, p. 39).
 * Avaya recommends videoCornerRadius in multiples of 8 and integer videoBorderWidth.
 */
enum class LocalVideoSurfacePreset(
    val label: String,
    val cornerRadius: Int,
    val borderWidth: Int,
    @LayoutRes val layoutRes: Int
) {
    SQUARE_0_0("Square (radius=0, border=0)", 0, 0, R.layout.view_local_video_square),
    SUBTLE_16_2("Subtle rounded (radius=16, border=2)", 16, 2, R.layout.view_local_video_subtle),
    ROUNDED_24_2("Rounded (radius=24, border=2)", 24, 2, R.layout.view_local_video_rounded),
    DEEP_32_3("Deep rounded (radius=32, border=3)", 32, 3, R.layout.view_local_video_deep)
}

data class AttributeEntry(
    val name: String,
    val valuesCsv: String
)

data class AmcServerConfig(
    val serverFqdn: String,
    val port: Int = 443,
    val secure: Boolean = true,
    val urlPath: String = "services/AvayaMobileCommunications/sessions"
)

data class WebGatewayConfigModel(
    val serverFqdn: String,
    val port: Int = 443,
    val secure: Boolean = true,
    val urlPath: String = "csa/resources/tenants/default"
)

data class TokenProxyConfig(
    val serverHost: String = SampleConfigurations.DEFAULT_TOKEN_SERVER,
    val port: Int = SampleConfigurations.DEFAULT_TOKEN_PORT,
    val secure: Boolean = true,
    val path: String = SampleConfigurations.DEFAULT_TOKEN_URL_PATH,
    // Kept strictly in memory; never persisted to SharedPreferences or written to logs.
    val clientApiKey: String = "",
    val expirationMs: String = "120000"
)

data class CallerProfile(
    val destinationNumber: String,
    val callingNumber: String,
    val displayName: String
)

data class RoutingMetadata(
    val attributes: List<AttributeEntry>,
    val servicePriority: Int = 5,
    val contextId: String = "",
    val topic: String = "",
    val locale: String = "en_us",
    val routingStrategy: String = RoutingStrategyOption.MOST_IDLE.apiValue,
    val resourceSourceName: String = "",
    val resourceId: String = ""
) {
    /**
     * Converts the 3 configured Oceana attribute entries into the SDK Service map format.
     * Comma-separated values per attribute name support multi-value Work Assignment rules.
     */
    fun toAttributeMap(): Map<String, List<String>> {
        val parsed = linkedMapOf<String, List<String>>()
        attributes.forEach { entry ->
            val key = entry.name.trim()
            val values = entry.valuesCsv
                .split(',')
                .map { it.trim() }
                .filter { it.isNotEmpty() }
            if (key.isNotEmpty() && values.isNotEmpty()) {
                parsed[key] = values
            }
        }
        return parsed
    }
}

data class LoggingConfig(
    val logToDevice: Boolean = false,
    val logFileName: String = "WebRTCReferenceClient",
    val supportEmail: String = ""
)

data class VideoSettings(
    val localSurfacePreset: LocalVideoSurfacePreset = LocalVideoSurfacePreset.ROUNDED_24_2,
    val capturePreference: VideoCapturePreference = VideoCapturePreference.VideoCapturePreference_540p,
    val captureOrientation: VideoCaptureOrientation = VideoCaptureOrientation.VideoCaptureOrientation_LandscapeOrPortrait,
    val muteVideoWhenBackgrounded: Boolean = false
)

data class InteractionLaunchConfig(
    val platformType: PlatformType,
    val mediaMode: CallMediaMode,
    val amcConfig: AmcServerConfig,
    val webGatewayConfig: WebGatewayConfigModel,
    val tokenProxyConfig: TokenProxyConfig,
    val callerProfile: CallerProfile,
    val routingMetadata: RoutingMetadata,
    val loggingConfig: LoggingConfig,
    val videoSettings: VideoSettings
)
