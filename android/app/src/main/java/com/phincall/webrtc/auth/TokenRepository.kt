package com.phincall.webrtc.auth

import com.phincall.webrtc.BuildConfig
import com.phincall.webrtc.config.CallerProfile
import com.phincall.webrtc.config.InputValidator
import com.phincall.webrtc.config.TokenProxyConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.ConnectionSpec
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Fetches single-use AAWG authorization tokens from the enterprise intermediary proxy.
 * Enforces TLS 1.2+, disables HTTP redirects and disk caching, validates URL structure via
 * HttpUrl.Builder, and retains tokens strictly in memory.
 */
class TokenRepository(
    private val httpClient: OkHttpClient = buildHardenedHttpClient()
) {

    @Volatile
    private var inMemoryToken: String? = null

    suspend fun fetchToken(
        proxyConfig: TokenProxyConfig,
        callerProfile: CallerProfile
    ): Result<TokenFetchResult> = withContext(Dispatchers.IO) {
        try {
            val endpointUrl = buildValidatedUrl(proxyConfig)

            val payload = AawgTokenRequestPayload(
                use = "csaGuest",
                calledNumber = callerProfile.destinationNumber,
                callingNumber = callerProfile.callingNumber,
                displayName = callerProfile.displayName,
                expiration = proxyConfig.expirationMs
            )

            val requestBody = payload.toJsonString().toRequestBody(AAWG_TOKEN_MEDIA_TYPE)
            val requestBuilder = Request.Builder()
                .url(endpointUrl)
                .post(requestBody)
                .header("Accept", "application/json")
                .header("Cache-Control", "no-store")

            if (proxyConfig.clientApiKey.isNotBlank()) {
                requestBuilder.header("X-Client-Key", proxyConfig.clientApiKey)
            }

            httpClient.newCall(requestBuilder.build()).execute().use { response ->
                val rawBody = response.body?.string().orEmpty()
                if (rawBody.length > MAX_TOKEN_RESPONSE_CHARS) {
                    return@withContext Result.failure(IOException("Token proxy response exceeded allowed size."))
                }
                if (!response.isSuccessful) {
                    val detail = extractErrorMessage(rawBody) ?: "HTTP ${response.code}"
                    return@withContext Result.failure(IOException("Token proxy rejected request: $detail"))
                }

                val json = JSONObject(rawBody)
                val encryptedToken = json.optString("encryptedToken", "")
                if (encryptedToken.isBlank()) {
                    return@withContext Result.failure(IOException("Token response did not contain encryptedToken."))
                }

                // Store single-use token strictly in memory (Guide 4.0.10, p. 31).
                inMemoryToken = encryptedToken
                val simulator = json.optString("mode") == "simulator"
                Result.success(TokenFetchResult(encryptedToken, simulator))
            }
        } catch (ex: Exception) {
            Result.failure(ex)
        }
    }

    fun consumeInMemoryToken(): String? {
        val current = inMemoryToken
        inMemoryToken = null
        return current
    }

    fun clearToken() {
        inMemoryToken = null
    }

    private fun buildValidatedUrl(config: TokenProxyConfig): HttpUrl {
        if (!InputValidator.isValidHost(config.serverHost)) {
            throw IllegalArgumentException("Token Proxy host contains invalid characters.")
        }
        if (!InputValidator.isValidPort(config.port)) {
            throw IllegalArgumentException("Token Proxy port must be between 1 and 65535.")
        }
        if (!InputValidator.isValidUrlPath(config.path)) {
            throw IllegalArgumentException("Token Proxy path contains invalid characters.")
        }

        // Enforce HTTPS in release builds and block non-loopback HTTP even in debug builds.
        if (!config.secure) {
            if (!BuildConfig.DEBUG) {
                throw SecurityException("Cleartext HTTP is blocked for token requests in release builds.")
            }
            if (!InputValidator.isLoopbackHost(config.serverHost)) {
                throw SecurityException("Cleartext HTTP is permitted only for local emulator loopback hosts in debug builds.")
            }
        }

        val builder = HttpUrl.Builder()
            .scheme(if (config.secure) "https" else "http")
            .host(config.serverHost.trim())
            .port(config.port)

        config.path.trim().trim('/').split('/')
            .filter { it.isNotEmpty() }
            .forEach { segment -> builder.addPathSegment(segment) }

        return builder.build()
    }

    private fun extractErrorMessage(body: String): String? {
        if (body.isBlank()) return null
        return runCatching { JSONObject(body).optString("error").takeIf { it.isNotBlank() } }.getOrNull()
    }

    companion object {
        private const val MAX_TOKEN_RESPONSE_CHARS = 32 * 1024
        private val AAWG_TOKEN_MEDIA_TYPE = "application/vnd.avaya.csa.tokens.v1+json; charset=utf-8".toMediaType()

        private fun buildHardenedHttpClient(): OkHttpClient {
            val specs = if (BuildConfig.DEBUG) {
                listOf(ConnectionSpec.MODERN_TLS, ConnectionSpec.CLEARTEXT)
            } else {
                listOf(ConnectionSpec.MODERN_TLS)
            }
            return OkHttpClient.Builder()
                .connectionSpecs(specs)
                .followRedirects(false)
                .followSslRedirects(false)
                .cache(null)
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .writeTimeout(15, TimeUnit.SECONDS)
                .build()
        }
    }
}
