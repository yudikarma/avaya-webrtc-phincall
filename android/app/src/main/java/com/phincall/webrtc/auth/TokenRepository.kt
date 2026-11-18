package com.phincall.webrtc.auth

import com.phincall.webrtc.config.CallerProfile
import com.phincall.webrtc.config.TokenProxyConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Fetches single-use AAWG authorization tokens from the enterprise intermediary proxy.
 * Client apps never present mTLS certificates directly to AAWG /csa/resources/tokens.
 */
class TokenRepository(
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()
) {

    @Volatile
    private var inMemoryToken: String? = null

    suspend fun fetchToken(
        proxyConfig: TokenProxyConfig,
        callerProfile: CallerProfile
    ): Result<TokenFetchResult> = withContext(Dispatchers.IO) {
        try {
            val payload = AawgTokenRequestPayload(
                use = "csaGuest",
                calledNumber = callerProfile.destinationNumber,
                callingNumber = callerProfile.callingNumber,
                displayName = callerProfile.displayName,
                expiration = proxyConfig.expirationMs
            )

            val requestBody = payload.toJsonString().toRequestBody(AAWG_TOKEN_MEDIA_TYPE)
            val requestBuilder = Request.Builder()
                .url(proxyConfig.buildEndpointUrl())
                .post(requestBody)
                .header("Accept", "application/json")

            if (proxyConfig.clientApiKey.isNotBlank()) {
                requestBuilder.header("X-Client-Key", proxyConfig.clientApiKey)
            }

            httpClient.newCall(requestBuilder.build()).execute().use { response ->
                val rawBody = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    val detail = extractErrorMessage(rawBody) ?: "HTTP ${response.code} ${response.message}"
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

    private fun extractErrorMessage(body: String): String? {
        if (body.isBlank()) return null
        return runCatching { JSONObject(body).optString("error").takeIf { it.isNotBlank() } }.getOrNull()
    }

    companion object {
        // Content type specified by Avaya WebRTC Connect Guide 4.0.10 (p. 29).
        private val AAWG_TOKEN_MEDIA_TYPE = "application/vnd.avaya.csa.tokens.v1+json; charset=utf-8".toMediaType()
    }
}
