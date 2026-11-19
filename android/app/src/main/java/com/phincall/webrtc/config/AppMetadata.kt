package com.phincall.webrtc.config

import android.os.Build
import com.phincall.webrtc.BuildConfig
import java.util.Locale

/**
 * Shared application, Android SDK, and Avaya WebRTC Connect metadata displayed across
 * the launch Splash Screen and Settings About Dialog.
 */
data class AppMetadataInfo(
    val appName: String,
    val appTagline: String,
    val appVersionName: String,
    val appVersionCode: Int,
    val applicationId: String,
    val buildType: String,
    val avayaSdkVersion: String,
    val csdkVersion: String,
    val androidRelease: String,
    val deviceApiLevel: Int,
    val minSdk: Int,
    val targetSdk: Int,
    val supportedPlatforms: String,
    val securitySummary: String,
    val architectureSummary: String
) {
    val formattedAppVersion: String
        get() = "v$appVersionName (Build $appVersionCode)"

    val formattedAvayaSdk: String
        get() = "$avayaSdkVersion (CSDK $csdkVersion)"

    val formattedDeviceOs: String
        get() = if (deviceApiLevel > 0 && androidRelease.isNotBlank()) {
            "Android $androidRelease (API $deviceApiLevel)"
        } else {
            "Android SDK"
        }

    val formattedSdkRange: String
        get() = "Target API $targetSdk / Min API $minSdk"

    fun formatDiagnosticsSummary(activeProfileLabel: String): String = buildString {
        appendLine("$appName $formattedAppVersion [$buildType]")
        appendLine("Package: $applicationId")
        appendLine("Avaya WebRTC SDK: $formattedAvayaSdk")
        appendLine("Active Profile: $activeProfileLabel")
        appendLine("Device OS: $formattedDeviceOs")
        appendLine("Android Compatibility: $formattedSdkRange")
        append("Security & Media: $securitySummary")
    }
}

object AppMetadata {
    const val APP_NAME = "PhinCall"
    const val APP_TAGLINE = "Avaya WebRTC Connect Mobile Client"
    const val DEFAULT_AVAYA_SDK_VERSION = "4.0.10"
    const val CSDK_ENGINE_VERSION = "605.1.10"
    const val MIN_SDK_VERSION = 28
    const val TARGET_SDK_VERSION = 36

    fun current(
        avayaSdkVersion: String = DEFAULT_AVAYA_SDK_VERSION
    ): AppMetadataInfo = create(
        appVersionName = BuildConfig.VERSION_NAME,
        appVersionCode = BuildConfig.VERSION_CODE,
        applicationId = BuildConfig.APPLICATION_ID,
        buildTypeRaw = BuildConfig.BUILD_TYPE,
        avayaSdkVersion = avayaSdkVersion,
        androidRelease = Build.VERSION.RELEASE ?: "",
        deviceApiLevel = Build.VERSION.SDK_INT
    )

    fun create(
        appVersionName: String,
        appVersionCode: Int,
        applicationId: String,
        buildTypeRaw: String,
        avayaSdkVersion: String = DEFAULT_AVAYA_SDK_VERSION,
        csdkVersion: String = CSDK_ENGINE_VERSION,
        androidRelease: String = "",
        deviceApiLevel: Int = 0,
        minSdk: Int = MIN_SDK_VERSION,
        targetSdk: Int = TARGET_SDK_VERSION
    ): AppMetadataInfo {
        val cleanBuildType = buildTypeRaw
            .trim()
            .ifBlank { "release" }
            .replaceFirstChar { ch ->
                if (ch.isLowerCase()) ch.titlecase(Locale.US) else ch.toString()
            }

        return AppMetadataInfo(
            appName = APP_NAME,
            appTagline = APP_TAGLINE,
            appVersionName = appVersionName.ifBlank { DEFAULT_AVAYA_SDK_VERSION },
            appVersionCode = appVersionCode,
            applicationId = applicationId,
            buildType = cleanBuildType,
            avayaSdkVersion = avayaSdkVersion.ifBlank { DEFAULT_AVAYA_SDK_VERSION },
            csdkVersion = csdkVersion,
            androidRelease = androidRelease.trim(),
            deviceApiLevel = deviceApiLevel,
            minSdk = minSdk,
            targetSdk = targetSdk,
            supportedPlatforms = "Avaya Oceana & Aura Elite",
            securitySummary = "TLS 1.2+ | DTLS-SRTP | 16 KB Aligned",
            architectureSummary = "Jetpack Compose, Material 3, Hilt DI, Android SDK $targetSdk"
        )
    }
}
