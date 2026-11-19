package com.phincall.webrtc

import com.avaya.ocs.Services.Work.Enums.AudioDeviceType
import com.avaya.ocs.Services.Work.Enums.PlatformType
import com.phincall.webrtc.auth.TokenRepository
import com.phincall.webrtc.config.AmcServerConfig
import com.phincall.webrtc.config.AppMetadata
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
import com.phincall.webrtc.ui.home.HomeUiState
import com.phincall.webrtc.ui.settings.SettingsFormState
import com.phincall.webrtc.webrtc.InteractionCoordinator
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.net.InetAddress
import java.net.ServerSocket
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import kotlin.concurrent.thread

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

    @Test
    fun settingsFormStateRoundTripsAndSeparatesOceanaVsEliteAttributes() {
        val oceanaConfig = SampleConfigurations.defaultFor(PlatformType.OCEANA).copy(
            amcConfig = AmcServerConfig(serverFqdn = "amc.lab.local", port = 8443),
            webGatewayConfig = WebGatewayConfigModel(serverFqdn = "aawg.lab.local", port = 443),
            callerProfile = CallerProfile(destinationNumber = "", callingNumber = "9001", displayName = "Tester")
        )
        val form = SettingsFormState.fromLaunchConfig(oceanaConfig)
        val rebuiltOceana = form.toLaunchConfig(strictNumbers = true)

        assertEquals("amc.lab.local", rebuiltOceana.amcConfig.serverFqdn)
        assertEquals(8443, rebuiltOceana.amcConfig.port)
        assertEquals(3, rebuiltOceana.routingMetadata.attributes.size)

        val rebuiltElite = form.copy(platformType = PlatformType.ELITE, destination = "5000")
            .toLaunchConfig(strictNumbers = true)
        assertEquals(PlatformType.ELITE, rebuiltElite.platformType)
        assertTrue("Elite config must omit Oceana attributes", rebuiltElite.routingMetadata.attributes.isEmpty())

        val invalidPortStrict = form.copy(aawgPort = "abc").toLaunchConfig(strictNumbers = true)
        assertTrue(
            RuntimeConfigManager.validateForLaunch(invalidPortStrict, isDebugBuild = true)
                .any { it.field == "aawgPort" }
        )
    }

    @Test
    fun homeUiStateDisablesCallActionsDuringTokenFetchOrActiveCall() {
        assertTrue(HomeUiState().isCallActionsEnabled)
        assertFalse(HomeUiState(isFetchingToken = true).isCallActionsEnabled)
        assertFalse(HomeUiState(isCallLocked = true).isCallActionsEnabled)
    }

    @Test
    fun allRawCertificatesAreValidX509WithoutPrivateKeysAndConfiguredInNetworkSecurityXml() {
        val rawDir = listOf(File("src/main/res/raw"), File("app/src/main/res/raw")).first { it.isDirectory }
        val mainXml = listOf(
            File("src/main/res/xml/network_security_config.xml"),
            File("app/src/main/res/xml/network_security_config.xml")
        ).first { it.isFile }.readText()
        val debugXml = listOf(
            File("src/debug/res/xml/network_security_config.xml"),
            File("app/src/debug/res/xml/network_security_config.xml")
        ).first { it.isFile }.readText()

        assertFalse("Release network_security_config.xml must not trust user CAs", mainXml.contains("src=\"user\""))
        assertFalse("Release network_security_config.xml must not trust non-cert res/raw media", mainXml.contains("@raw/loading"))
        assertFalse("Debug network_security_config.xml must not trust non-cert res/raw media", debugXml.contains("@raw/loading"))

        val certFactory = CertificateFactory.getInstance("X.509")
        val certFiles = rawDir.listFiles { file ->
            file.isFile && (file.extension.equals("crt", ignoreCase = true) || file.extension.equals("pem", ignoreCase = true))
        }?.toList().orEmpty()

        assertEquals(5, certFiles.size)

        certFiles.forEach { file ->
            val content = file.readText()
            assertFalse("${file.name} must not contain a private key", content.contains("PRIVATE KEY"))

            val parsedCerts = file.inputStream().use { certFactory.generateCertificates(it) }
            assertTrue("${file.name} must contain at least one X.509 certificate", parsedCerts.isNotEmpty())
            assertTrue(parsedCerts.all { it is X509Certificate })

            val resRef = "@raw/${file.nameWithoutExtension}"
            assertTrue("Missing $resRef in main network_security_config.xml", mainXml.contains("src=\"$resRef\""))
            assertTrue("Missing $resRef in debug network_security_config.xml", debugXml.contains("src=\"$resRef\""))
        }
    }

    @Test
    fun tokenRepositorySendsAvayaVendorAcceptHeaderToPreventHttp406AndKeepsTokenInMemory() = runTest {
        val server = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))
        var capturedAccept = ""
        var capturedContentType = ""

        val serverThread = thread(start = true) {
            server.use { ss ->
                ss.accept().use { socket ->
                    val reader = socket.getInputStream().bufferedReader()
                    var contentLength = 0
                    while (true) {
                        val line = reader.readLine() ?: break
                        if (line.isEmpty()) break
                        if (line.startsWith("Accept:", ignoreCase = true)) {
                            capturedAccept = line.substringAfter(":").trim()
                        } else if (line.startsWith("Content-Type:", ignoreCase = true)) {
                            capturedContentType = line.substringAfter(":").trim()
                        } else if (line.startsWith("Content-Length:", ignoreCase = true)) {
                            contentLength = line.substringAfter(":").trim().toIntOrNull() ?: 0
                        }
                    }
                    repeat(contentLength) { reader.read() }

                    val responseJson = "{\"encryptedToken\":\"tok-abc-123\"}"
                    val out = socket.getOutputStream()
                    if (capturedAccept.contains("application/vnd.avaya.csa.tokens.v1+json")) {
                        val headers = "HTTP/1.1 200 OK\r\n" +
                            "Content-Type: application/vnd.avaya.csa.tokens.v1+json\r\n" +
                            "Content-Length: ${responseJson.toByteArray().size}\r\n" +
                            "Connection: close\r\n\r\n"
                        out.write((headers + responseJson).toByteArray())
                    } else {
                        val err = "HTTP/1.1 406 Not Acceptable\r\nContent-Length: 0\r\nConnection: close\r\n\r\n"
                        out.write(err.toByteArray())
                    }
                    out.flush()
                }
            }
        }

        val repo = TokenRepository(OkHttpClient.Builder().build())
        val result = repo.fetchToken(
            proxyConfig = TokenProxyConfig(
                serverHost = "127.0.0.1",
                port = server.localPort,
                secure = false,
                path = "token-generation-service/token/getEncryptedToken"
            ),
            callerProfile = CallerProfile(destinationNumber = "", callingNumber = "1001", displayName = "Alice")
        )
        serverThread.join(5_000)

        assertTrue("Expected Accept to advertise Avaya vendor media type, got: $capturedAccept",
            capturedAccept.contains("application/vnd.avaya.csa.tokens.v1+json"))
        assertTrue("Expected Content-Type to advertise Avaya vendor media type, got: $capturedContentType",
            capturedContentType.contains("application/vnd.avaya.csa.tokens.v1+json"))
        assertTrue("Expected HTTP 200 success from fetchToken, got: $result", result.isSuccess)
        assertEquals("tok-abc-123", repo.consumeInMemoryToken())
        assertNull("Single-use token must clear after consumption", repo.consumeInMemoryToken())
    }

    @Test
    fun appMetadataFormatsSdkVersionsAndDiagnosticsCleanly() {
        val info = AppMetadata.create(
            appVersionName = "4.0.10",
            appVersionCode = 2,
            applicationId = "com.phincall.webrtc",
            buildTypeRaw = "release",
            androidRelease = "16",
            deviceApiLevel = 36
        )

        assertEquals("v4.0.10 (Build 2)", info.formattedAppVersion)
        assertEquals("Release", info.buildType)
        assertEquals("4.0.10 (CSDK 605.1.10)", info.formattedAvayaSdk)
        assertEquals("Android 16 (API 36)", info.formattedDeviceOs)
        assertEquals("Target API 36 / Min API 28", info.formattedSdkRange)

        val summary = info.formatDiagnosticsSummary("Avaya Oceana")
        assertTrue(summary.contains("PhinCall v4.0.10 (Build 2) [Release]"))
        assertTrue(summary.contains("Avaya WebRTC SDK: 4.0.10 (CSDK 605.1.10)"))
        assertTrue(summary.contains("Active Profile: Avaya Oceana"))
        assertTrue(summary.contains("Target API 36 / Min API 28"))
        assertFalse("Diagnostics summary must not contain em dashes", summary.contains("\u2014"))
    }

    @Test
    fun videoStageAndLocalSurfaceLayoutsCarryNonNullSizingAndAvayaAttributes() {
        val layoutDir = listOf(
            File("app/src/main/res/layout"),
            File("src/main/res/layout")
        ).firstOrNull { it.isDirectory }
        assertTrue("Expected layout directory to exist", layoutDir != null)

        val remoteXml = File(layoutDir!!, "view_remote_video_stage.xml").readText()
        assertTrue(remoteXml.contains("avayaVideoStageRoot"))
        assertTrue(remoteXml.contains("android:layout_width=\"match_parent\""))
        assertTrue(remoteXml.contains("android:layout_height=\"match_parent\""))
        assertTrue(remoteXml.contains("app:videoDirection=\"Receive\""))
        assertTrue(remoteXml.contains("app:videoStyle=\"Fixed\""))
        assertFalse("Stage root must stay transparent to avoid covering GLSurfaceView", remoteXml.contains("@color/stage_dark"))

        listOf(
            "view_local_video_square.xml",
            "view_local_video_subtle.xml",
            "view_local_video_rounded.xml",
            "view_local_video_deep.xml"
        ).forEach { fileName ->
            val xml = File(layoutDir, fileName).readText()
            assertTrue("$fileName must declare Transmit direction", xml.contains("app:videoDirection=\"Transmit\""))
            assertTrue("$fileName must declare MovableCorner style", xml.contains("app:videoStyle=\"MovableCorner\""))
            assertTrue("$fileName must declare explicit layout_width", xml.contains("android:layout_width=\""))
            assertTrue("$fileName must declare explicit layout_height", xml.contains("android:layout_height=\""))
        }
    }

    @Test
    fun sanitizeAudioDeviceListDropsNullsAndDuplicatesAndManifestDeclaresBluetoothPermissions() {
        val raw = mutableListOf<AudioDeviceType?>(
            AudioDeviceType.BLUETOOTH_HEADSET,
            null,
            AudioDeviceType.SPEAKER,
            AudioDeviceType.BLUETOOTH_HEADSET,
            AudioDeviceType.HANDSET,
            null
        )
        val sanitized = InteractionCoordinator.sanitizeAudioDeviceList(raw)
        assertEquals(
            listOf(
                AudioDeviceType.BLUETOOTH_HEADSET,
                AudioDeviceType.SPEAKER,
                AudioDeviceType.HANDSET
            ),
            sanitized
        )
        assertTrue(InteractionCoordinator.sanitizeAudioDeviceList(null).isEmpty())

        val manifestFile = listOf(
            File("app/src/main/AndroidManifest.xml"),
            File("src/main/AndroidManifest.xml")
        ).firstOrNull { it.isFile }
        assertTrue("Expected AndroidManifest.xml to exist", manifestFile != null)
        val manifest = manifestFile!!.readText()
        listOf(
            "android.permission.BLUETOOTH",
            "android.permission.BLUETOOTH_ADMIN",
            "android.permission.BLUETOOTH_CONNECT",
            "android.permission.BLUETOOTH_SCAN",
            "android.permission.READ_PHONE_STATE",
            "android.permission.MODIFY_AUDIO_SETTINGS"
        ).forEach { perm ->
            assertTrue("Manifest must declare $perm", manifest.contains(perm))
        }
    }
}
