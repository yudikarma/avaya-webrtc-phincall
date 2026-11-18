package com.phincall.webrtc.auth

import org.json.JSONObject

/**
 * Token request payload sent to the intermediary authentication proxy (Guide 4.0.10, p. 29).
 * "use" is mandatory and must be "csaGuest"; "expiration" is in milliseconds.
 */
data class AawgTokenRequestPayload(
    val use: String = "csaGuest",
    val calledNumber: String?,
    val callingNumber: String?,
    val displayName: String?,
    val expiration: String = "120000"
) {
    fun toJsonString(): String {
        val obj = JSONObject()
        obj.put("use", use)
        if (!calledNumber.isNullOrBlank()) obj.put("calledNumber", calledNumber.trim())
        if (!callingNumber.isNullOrBlank()) obj.put("callingNumber", callingNumber.trim())
        if (!displayName.isNullOrBlank()) obj.put("displayName", displayName.trim())
        obj.put("expiration", expiration)
        return obj.toString()
    }
}

data class TokenFetchResult(
    val encryptedToken: String,
    val isSimulatorToken: Boolean
)
