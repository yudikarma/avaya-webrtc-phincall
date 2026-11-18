package com.phincall.webrtc.config

import com.avaya.ocs.Services.Work.Enums.PlatformType

/**
 * Sample non-production lab defaults matching Avaya WebRTC Connect Guide 4.0.10.
 * Replace FQDNs with your enterprise AMC cluster, AAWG server, and token proxy endpoints.
 */
object SampleConfigurations {

    fun defaultFor(platformType: PlatformType): InteractionLaunchConfig {
        val isOceana = platformType == PlatformType.OCEANA
        return InteractionLaunchConfig(
            platformType = platformType,
            mediaMode = CallMediaMode.VIDEO,
            amcConfig = AmcServerConfig(
                serverFqdn = if (isOceana) "amc.contactcenter.example.com" else "",
                port = 443,
                secure = true,
                urlPath = ""
            ),
            webGatewayConfig = WebGatewayConfigModel(
                serverFqdn = "aawg.contactcenter.example.com",
                port = 443,
                secure = true,
                urlPath = ""
            ),
            // 10.0.2.2 maps Android Emulator to localhost where token-proxy/server.js listens.
            tokenProxyConfig = TokenProxyConfig(
                serverHost = "10.0.2.2",
                port = 8443,
                secure = false,
                path = "api/tokens",
                expirationMs = "120000"
            ),
            callerProfile = CallerProfile(
                destinationNumber = if (isOceana) "1000" else "2000",
                callingNumber = "",
                displayName = ""
            ),
            routingMetadata = RoutingMetadata(
                rawAttributes = if (isOceana) "Language: English; Service: Sales" else "",
                servicePriority = 5,
                contextId = "ORD-2026-00419",
                topic = "billing"
            ),
            videoSettings = VideoSettings()
        )
    }
}
