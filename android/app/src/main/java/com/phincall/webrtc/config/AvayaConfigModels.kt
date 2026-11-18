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

data class AmcServerConfig(
    val serverFqdn: String,
    val port: Int = 443,
    val secure: Boolean = true,
    val urlPath: String = ""
)

data class WebGatewayConfigModel(
    val serverFqdn: String,
    val port: Int = 443,
    val secure: Boolean = true,
    val urlPath: String = ""
)

data class TokenProxyConfig(
    val serverHost: String,
    val port: Int = 8443,
    val secure: Boolean = false,
    val path: String = "api/tokens",
    val clientApiKey: String = "",
    val expirationMs: String = "120000"
) {
    fun buildEndpointUrl(): String {
        val scheme = if (secure) "https" else "http"
        val cleanPath = path.trimStart('/')
        return "$scheme://$serverHost:$port/$cleanPath"
    }
}

data class CallerProfile(
    val destinationNumber: String,
    val callingNumber: String,
    val displayName: String
)

data class RoutingMetadata(
    val rawAttributes: String,
    val servicePriority: Int = 5,
    val contextId: String = "",
    val topic: String = "",
    val locale: String = "en-US",
    val routingStrategy: String = "Most Idle",
    val resourceId: String = "",
    val resourceSourceName: String = ""
) {
    /**
     * Parses semicolon-delimited Oceana attributes such as "Language: English; Service: Sales".
     * Multiple comma-separated values per key are preserved for Work Assignment matching.
     */
    fun parseAttributeMap(): Map<String, List<String>> {
        val parsed = linkedMapOf<String, List<String>>()
        rawAttributes
            .split(';')
            .map { it.trim() }
            .filter { it.isNotEmpty() && it.contains(':') }
            .forEach { entry ->
                val idx = entry.indexOf(':')
                val key = entry.substring(0, idx).trim()
                val values = entry.substring(idx + 1)
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
    val videoSettings: VideoSettings
)
