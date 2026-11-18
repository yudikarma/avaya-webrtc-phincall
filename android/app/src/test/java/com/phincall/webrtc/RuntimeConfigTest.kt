package com.phincall.webrtc

import com.avaya.ocs.Services.Work.Enums.PlatformType
import com.phincall.webrtc.config.AmcServerConfig
import com.phincall.webrtc.config.AttributeEntry
import com.phincall.webrtc.config.CallHistoryStore
import com.phincall.webrtc.config.CallMediaMode
import com.phincall.webrtc.config.CallOutcome
import com.phincall.webrtc.config.CallerProfile
import com.phincall.webrtc.config.InputValidator
import com.phincall.webrtc.config.RecentCallRecord
import com.phincall.webrtc.config.RoutingMetadata
import com.phincall.webrtc.config.RuntimeConfigManager
import com.phincall.webrtc.config.SampleConfigurations
import com.phincall.webrtc.config.TokenProxyConfig
import com.phincall.webrtc.config.WebGatewayConfigModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RuntimeConfigTest {

    @Test
    fun defaultTokenEndpointUsesLabdemoPhintracoPort443Https() {
        val oceanaDefault = SampleConfigurations.defaultFor(PlatformType.OCEANA)
        val eliteDefault = SampleConfigurations.defaultFor(PlatformType.ELITE)

        assertEquals("Labdemo.phintraco.com", oceanaDefault.tokenProxyConfig.serverHost)
        assertEquals(443, oceanaDefault.tokenProxyConfig.port)
        assertTrue(oceanaDefault.tokenProxyConfig.secure)
        assertEquals("Labdemo.phintraco.com", eliteDefault.tokenProxyConfig.serverHost)
        assertEquals(443, eliteDefault.tokenProxyConfig.port)
        assertTrue(eliteDefault.tokenProxyConfig.secure)
    }

    @Test
    fun oceanaRequiresAmcAndPermitsBlankDestination() {
        val base = SampleConfigurations.defaultFor(PlatformType.OCEANA).copy(
            webGatewayConfig = WebGatewayConfigModel(serverFqdn = "aawg.example.com"),
            tokenProxyConfig = TokenProxyConfig(serverHost = "token.example.com"),
            callerProfile = CallerProfile(destinationNumber = "", callingNumber = "1001", displayName = "Alice")
        )

        val missingAmcIssues = RuntimeConfigManager.validateForLaunch(base, isDebugBuild = false)
        assertTrue(missingAmcIssues.any { it.field == "amcServer" })

        val validOceana = base.copy(
            amcConfig = AmcServerConfig(serverFqdn = "amc.example.com")
        )
        val issues = RuntimeConfigManager.validateForLaunch(validOceana, isDebugBuild = false)
        assertTrue("Expected zero validation issues for valid Oceana config: $issues", issues.isEmpty())
    }

    @Test
    fun eliteSkipsAmcAndRequiresDestination() {
        val base = SampleConfigurations.defaultFor(PlatformType.ELITE).copy(
            amcConfig = AmcServerConfig(serverFqdn = "", port = 0, secure = false),
            webGatewayConfig = WebGatewayConfigModel(serverFqdn = "aawg.example.com"),
            tokenProxyConfig = TokenProxyConfig(serverHost = "token.example.com"),
            callerProfile = CallerProfile(destinationNumber = "", callingNumber = "1001", displayName = "Bob")
        )

        val missingDestIssues = RuntimeConfigManager.validateForLaunch(base, isDebugBuild = false)
        assertTrue(missingDestIssues.any { it.field == "destination" })
        assertFalse(missingDestIssues.any { it.field == "amcServer" })

        val validElite = base.copy(
            callerProfile = base.callerProfile.copy(destinationNumber = "5000")
        )
        val issues = RuntimeConfigManager.validateForLaunch(validElite, isDebugBuild = false)
        assertTrue("Expected zero validation issues for valid Elite config: $issues", issues.isEmpty())
    }

    @Test
    fun releaseBuildsRejectCleartextEndpointsAndAllowDebugLoopbackTokenProxy() {
        val httpConfig = SampleConfigurations.defaultFor(PlatformType.ELITE).copy(
            webGatewayConfig = WebGatewayConfigModel(serverFqdn = "aawg.example.com", secure = false),
            tokenProxyConfig = TokenProxyConfig(serverHost = "10.0.2.2", port = 8443, secure = false),
            callerProfile = CallerProfile(destinationNumber = "5000", callingNumber = "1001", displayName = "Bob")
        )

        val releaseIssues = RuntimeConfigManager.validateForLaunch(httpConfig, isDebugBuild = false)
        assertTrue(releaseIssues.any { it.field == "aawgSecure" })
        assertTrue(releaseIssues.any { it.field == "tokenSecure" })

        val debugIssues = RuntimeConfigManager.validateForLaunch(httpConfig, isDebugBuild = true)
        assertTrue("Debug build should allow loopback token proxy HTTP: $debugIssues", debugIssues.isEmpty())
    }

    @Test
    fun inputValidatorRejectsTraversalAndSipInjection() {
        assertTrue(InputValidator.isValidHost("aawg.example.com"))
        assertFalse(InputValidator.isValidHost("https://aawg.example.com"))
        assertFalse(InputValidator.isValidHost("bad..host"))

        assertTrue(InputValidator.isValidPort(443))
        assertFalse(InputValidator.isValidPort(0))
        assertFalse(InputValidator.isValidPort(70000))

        assertTrue(InputValidator.isValidUrlPath("token-generation-service/token/getEncryptedToken"))
        assertFalse(InputValidator.isValidUrlPath("../secret"))
        assertFalse(InputValidator.isValidUrlPath("a//b"))

        assertTrue(InputValidator.isValidSipUser("cust-1001@example.com"))
        assertFalse(InputValidator.isValidSipUser("5000\r\nX-Inject: evil"))
        assertFalse(InputValidator.isValidSipUser("5000;transport=tcp"))

        assertTrue(InputValidator.isValidPriority(5))
        assertFalse(InputValidator.isValidPriority(0))
        assertFalse(InputValidator.isValidPriority(11))

        val sanitized = InputValidator.sanitizeLogFileName("../../etc/passwd")
        assertFalse(sanitized.contains("/"))
        assertFalse(sanitized.contains("\\"))
    }

    @Test
    fun routingMetadataParsesMultiValueAttributesAndSkipsBlanks() {
        val metadata = RoutingMetadata(
            attributes = listOf(
                AttributeEntry("Location", "Inhouse"),
                AttributeEntry("Language", "English, Spanish"),
                AttributeEntry(" ", "Ignored")
            )
        )

        val map = metadata.toAttributeMap()
        assertEquals(2, map.size)
        assertEquals(listOf("Inhouse"), map["Location"])
        assertEquals(listOf("English", "Spanish"), map["Language"])
    }

    @Test
    fun callHistorySerializationPreservesRecordsAndBoundsMaxEntries() {
        val sample = (1..30).map { i ->
            RecentCallRecord(
                id = "rec_$i",
                destinationNumber = "50$i",
                displayName = "Caller $i",
                platformType = if (i % 2 == 0) PlatformType.ELITE else PlatformType.OCEANA,
                mediaMode = if (i % 2 == 0) CallMediaMode.VIDEO else CallMediaMode.AUDIO,
                timestampMillis = 1_700_000_000_000L + i * 1000L,
                durationMillis = i * 5_000L,
                outcome = CallOutcome.CONNECTED
            )
        }

        val serialized = CallHistoryStore.serializeRecords(sample)
        val parsed = CallHistoryStore.parseRecords(serialized)

        assertEquals(CallHistoryStore.MAX_RECORDS, parsed.size)
        assertEquals("501", parsed.first().destinationNumber)
        assertEquals(PlatformType.OCEANA, parsed.first().platformType)
        assertEquals(CallOutcome.CONNECTED, parsed.first().outcome)
        assertTrue(CallHistoryStore.parseRecords("not-json").isEmpty())
    }
}
