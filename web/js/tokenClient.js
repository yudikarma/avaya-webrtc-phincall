/**
 * Intermediary Token Proxy client (Avaya WebRTC Connect Guide 4.0.10, pp. 28-31).
 * The browser never holds client certificates or calls AAWG /csa/resources/tokens directly.
 */
(function (global) {
  "use strict";

  let inMemoryToken = null;

  async function fetchToken({ endpoint, calledNumber, callingNumber, displayName, expiration, apiKey }) {
    const payload = {
      use: "csaGuest",
      expiration: String(expiration || "120000")
    };
    if (calledNumber && calledNumber.trim()) payload.calledNumber = calledNumber.trim();
    if (callingNumber && callingNumber.trim()) payload.callingNumber = callingNumber.trim();
    if (displayName && displayName.trim()) payload.displayName = displayName.trim();

    const headers = {
      "Content-Type": "application/vnd.avaya.csa.tokens.v1+json",
      "Accept": "application/json"
    };
    if (apiKey && apiKey.trim()) {
      headers["X-Client-Key"] = apiKey.trim();
    }

    const response = await fetch(endpoint, {
      method: "POST",
      headers,
      body: JSON.stringify(payload)
    });

    const data = await response.json().catch(() => ({}));
    if (!response.ok) {
      throw new Error(data.error || `Token proxy failed with HTTP ${response.status}`);
    }
    if (!data.encryptedToken) {
      throw new Error("Token proxy response did not return encryptedToken.");
    }

    // Tokens are single-use and stored in memory only (Guide 4.0.10, p. 31).
    inMemoryToken = data.encryptedToken;
    return {
      encryptedToken: data.encryptedToken,
      isSimulatorToken: data.mode === "simulator"
    };
  }

  function consumeToken() {
    const token = inMemoryToken;
    inMemoryToken = null;
    return token;
  }

  function clearToken() {
    inMemoryToken = null;
  }

  global.PhinCallTokenClient = Object.freeze({
    fetchToken,
    consumeToken,
    clearToken
  });
})(window);
