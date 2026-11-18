import Foundation

struct AAWGTokenRequestBody: Encodable {
    let use: String
    let calledNumber: String?
    let callingNumber: String?
    let displayName: String?
    let expiration: String
}

struct AAWGTokenResponseBody: Decodable {
    let encryptedToken: String
}

enum TokenServiceError: LocalizedError {
    case invalidEndpoint
    case httpFailure(statusCode: Int, message: String)
    case emptyToken

    var errorDescription: String? {
        switch self {
        case .invalidEndpoint:
            return "Token proxy URL is invalid. Verify host, port, and path."
        case let .httpFailure(code, message):
            return "Token proxy returned HTTP \(code): \(message)"
        case .emptyToken:
            return "Token proxy response did not include encryptedToken."
        }
    }
}

/// Requests single-use csaGuest tokens from the enterprise intermediary authentication proxy.
/// Uses URLSession with hostnames for full IPv6 / NAT64 App Store compliance (Guide 4.0.10, p. 44).
final class TokenService {

    private let session: URLSession
    private var inMemoryToken: String?
    private let lock = NSLock()

    init(session: URLSession = .shared) {
        self.session = session
    }

    func fetchAuthorizationToken(settings: AvayaClientSettings) async throws -> String {
        guard let url = settings.tokenProxyURL() else {
            throw TokenServiceError.invalidEndpoint
        }

        let payload = AAWGTokenRequestBody(
            use: "csaGuest",
            calledNumber: settings.destinationNumber.isEmpty ? nil : settings.destinationNumber,
            callingNumber: settings.callingNumber.isEmpty ? nil : settings.callingNumber,
            displayName: settings.displayName.isEmpty ? nil : settings.displayName,
            expiration: settings.tokenExpirationMs
        )

        var request = URLRequest(url: url, timeoutInterval: 15)
        request.httpMethod = "POST"
        // Media type defined by Avaya WebRTC Connect Guide 4.0.10 (p. 29).
        request.setValue("application/vnd.avaya.csa.tokens.v1+json", forHTTPHeaderField: "Content-Type")
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        if !settings.tokenProxyApiKey.isEmpty {
            request.setValue(settings.tokenProxyApiKey, forHTTPHeaderField: "X-Client-Key")
        }
        request.httpBody = try JSONEncoder().encode(payload)

        let (data, response) = try await session.data(for: request)
        guard let http = response as? HTTPURLResponse else {
            throw TokenServiceError.httpFailure(statusCode: -1, message: "No HTTP response")
        }
        guard (200..<300).contains(http.statusCode) else {
            let bodyText = String(data: data, encoding: .utf8) ?? HTTPURLResponse.localizedString(forStatusCode: http.statusCode)
            throw TokenServiceError.httpFailure(statusCode: http.statusCode, message: bodyText)
        }

        let decoded = try JSONDecoder().decode(AAWGTokenResponseBody.self, from: data)
        guard !decoded.encryptedToken.isEmpty else {
            throw TokenServiceError.emptyToken
        }

        // Persist token strictly in memory; tokens are valid for a single call only (Guide 4.0.10, p. 31).
        lock.lock()
        inMemoryToken = decoded.encryptedToken
        lock.unlock()

        return decoded.encryptedToken
    }

    func consumeInMemoryToken() -> String? {
        lock.lock()
        defer { lock.unlock() }
        let token = inMemoryToken
        inMemoryToken = nil
        return token
    }

    func clearToken() {
        lock.lock()
        inMemoryToken = nil
        lock.unlock()
    }
}
