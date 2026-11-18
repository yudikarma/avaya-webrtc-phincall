package com.phincall.webrtc.config

import com.avaya.ocs.Services.Work.Enums.PlatformType

/**
 * Default values aligned with the Avaya WebRTC Connect 4.0.10 Android Reference Client
 * (preferences.xml, preferences_elite.xml, attributes.xml, and strings.xml).
 */
object SampleConfigurations {

    const val DEFAULT_AMC_URL_PATH = "services/AvayaMobileCommunications/sessions"
    const val DEFAULT_AAWG_URL_PATH = "csa/resources/tenants/default"
    const val DEFAULT_TOKEN_SERVER = "Labdemo.phintraco.com"
    const val DEFAULT_TOKEN_PORT = 443
    const val DEFAULT_TOKEN_URL_PATH = "token-generation-service/token/getEncryptedToken"
    const val DEFAULT_LOCALE = "en_us"
    const val DEFAULT_PRIORITY = 5
    const val DEFAULT_LOG_FILE_NAME = "WebRTCReferenceClient"

    fun defaultAttributesForOceana(): List<AttributeEntry> = listOf(
        AttributeEntry("Location", "Inhouse"),
        AttributeEntry("Language", "English"),
        AttributeEntry("Service", "Sales")
    )

    fun defaultFor(platformType: PlatformType): InteractionLaunchConfig {
        val isOceana = platformType == PlatformType.OCEANA
        return InteractionLaunchConfig(
            platformType = platformType,
            mediaMode = CallMediaMode.VIDEO,
            amcConfig = AmcServerConfig(
                serverFqdn = "",
                port = 443,
                secure = true,
                urlPath = DEFAULT_AMC_URL_PATH
            ),
            webGatewayConfig = WebGatewayConfigModel(
                serverFqdn = "",
                port = 443,
                secure = true,
                urlPath = DEFAULT_AAWG_URL_PATH
            ),
            tokenProxyConfig = TokenProxyConfig(
                serverHost = DEFAULT_TOKEN_SERVER,
                port = DEFAULT_TOKEN_PORT,
                secure = true,
                path = DEFAULT_TOKEN_URL_PATH,
                clientApiKey = "",
                expirationMs = "120000"
            ),
            callerProfile = CallerProfile(
                destinationNumber = "",
                callingNumber = "",
                displayName = ""
            ),
            routingMetadata = RoutingMetadata(
                attributes = if (isOceana) defaultAttributesForOceana() else emptyList(),
                servicePriority = DEFAULT_PRIORITY,
                contextId = "",
                topic = "",
                locale = DEFAULT_LOCALE,
                routingStrategy = RoutingStrategyOption.MOST_IDLE.apiValue,
                resourceSourceName = "",
                resourceId = ""
            ),
            loggingConfig = LoggingConfig(
                logToDevice = false,
                logFileName = DEFAULT_LOG_FILE_NAME,
                supportEmail = ""
            ),
            videoSettings = VideoSettings()
        )
    }
}
