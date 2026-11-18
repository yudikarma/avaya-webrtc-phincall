import Foundation

enum AvayaPlatformChoice: String, CaseIterable, Identifiable {
    case oceana = "Avaya Oceana"
    case elite = "Avaya Aura Elite"

    var id: String { rawValue }
}

enum CallMediaChoice: String {
    case audio = "Audio"
    case video = "Video"
}

enum VideoResolutionChoice: String, CaseIterable, Identifiable {
    case min = "Min"
    case p270 = "270p"
    case p360 = "360p"
    case p540 = "540p"
    case max = "Max"

    var id: String { rawValue }
}

enum VideoOrientationChoice: String, CaseIterable, Identifiable {
    case landscapeOrPortrait = "Landscape or Portrait"
    case landscapeOnly = "Landscape Only"

    var id: String { rawValue }
}

enum CameraSelectionChoice: String, CaseIterable, Identifiable {
    case front = "Front"
    case back = "Back"

    var id: String { rawValue }
}

/// Complete client configuration for Avaya WebRTC Connect 4.0.10 (iOS).
struct AvayaClientSettings {
    var platform: AvayaPlatformChoice = .oceana

    // AMC Cluster (Oceana only; ignored on Avaya Aura Elite per Guide 4.0.10, p. 47)
    var amcServer: String = "amc.contactcenter.example.com"
    var amcPort: Int = 443
    var amcSecure: Bool = true
    var amcUrlPath: String = ""

    // Avaya Aura Web Gateway (AAWG)
    var aawgServer: String = "aawg.contactcenter.example.com"
    var aawgPort: Int = 443
    var aawgSecure: Bool = true
    var aawgUrlPath: String = ""

    // Intermediary Token Proxy (client never calls AAWG /csa/resources/tokens directly)
    var tokenProxyHost: String = "token-proxy.contactcenter.example.com"
    var tokenProxyPort: Int = 8443
    var tokenProxySecure: Bool = true
    var tokenProxyPath: String = "api/tokens"
    var tokenProxyApiKey: String = ""
    var tokenExpirationMs: String = "120000"

    // Caller and destination
    var destinationNumber: String = "1000"
    var callingNumber: String = ""
    var displayName: String = ""

    // Work routing attributes (Oceana), Screen Pop context, and Customer Journey topic
    var attributesRaw: String = "Language: English; Service: Sales"
    var servicePriority: Int = 5
    var contextId: String = "ORD-2026-00419"
    var topic: String = "billing"
    var locale: String = "en-US"
    var routingStrategy: String = "Most Idle"

    // Camera and resolution settings (Guide 4.0.10, pp. 51, 63)
    var initialCamera: CameraSelectionChoice = .front
    var resolutionPreference: VideoResolutionChoice = .p540
    var orientationPreference: VideoOrientationChoice = .landscapeOrPortrait
    var muteVideoInBackground: Bool = false

    func tokenProxyURL() -> URL? {
        let scheme = tokenProxySecure ? "https" : "http"
        let cleanPath = tokenProxyPath.trimmingCharacters(in: CharacterSet(charactersIn: "/"))
        return URL(string: "\(scheme)://\(tokenProxyHost):\(tokenProxyPort)/\(cleanPath)")
    }

    /// Parses semicolon-delimited key:value pairs into Oceana Work Assignment attribute map.
    func parsedAttributeMap() -> [String: [String]] {
        var result: [String: [String]] = [:]
        for pair in attributesRaw.split(separator: ";") {
            let parts = pair.split(separator: ":", maxSplits: 1)
            guard parts.count == 2 else { continue }
            let key = parts[0].trimmingCharacters(in: .whitespacesAndNewlines)
            let values = parts[1]
                .split(separator: ",")
                .map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }
                .filter { !$0.isEmpty }
            if !key.isEmpty && !values.isEmpty {
                result[key] = values
            }
        }
        return result
    }
}
