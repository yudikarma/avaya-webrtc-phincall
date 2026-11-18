"use strict";

const { spawn } = require("child_process");
const http = require("http");
const path = require("path");

const SERVER_JS = path.resolve(__dirname, "..", "server.js");
const PORT = 18543;
const API_KEY = "test-key-with-at-least-32-characters-123456";

function sleep(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

function spawnExpectFailure(envOverrides) {
  return new Promise((resolve) => {
    const child = spawn(process.execPath, [SERVER_JS], {
      env: { ...process.env, PORT: String(PORT + 1), ...envOverrides },
      stdio: ["ignore", "pipe", "pipe"]
    });
    let stderr = "";
    child.stderr.on("data", (d) => {
      stderr += d.toString("utf8");
    });
    child.on("close", (code) => resolve({ code, stderr }));
  });
}

function sendRequest(method, urlPath, body, headers = {}) {
  return new Promise((resolve, reject) => {
    const raw = typeof body === "string" ? body : body ? JSON.stringify(body) : null;
    const req = http.request(
      {
        hostname: "127.0.0.1",
        port: PORT,
        path: urlPath,
        method,
        headers: {
          ...(raw ? { "Content-Length": Buffer.byteLength(raw) } : {}),
          ...headers
        }
      },
      (res) => {
        const chunks = [];
        res.on("data", (c) => chunks.push(c));
        res.on("end", () =>
          resolve({
            status: res.statusCode,
            headers: res.headers,
            body: Buffer.concat(chunks).toString("utf8")
          })
        );
      }
    );
    req.on("error", reject);
    if (raw) req.write(raw);
    req.end();
  });
}

async function run() {
  const results = [];

  const noKey = await spawnExpectFailure({
    TOKEN_PROXY_MODE: "aawg",
    AAWG_BASE_URL: "https://aawg.example.com",
    PROXY_API_KEY: ""
  });
  if (noKey.code === 0) {
    throw new Error("Expected fail-closed exit when PROXY_API_KEY is missing in aawg mode");
  }
  results.push("Fail-closed startup (missing PROXY_API_KEY in aawg mode)");

  const httpAawg = await spawnExpectFailure({
    TOKEN_PROXY_MODE: "aawg",
    AAWG_BASE_URL: "http://aawg.example.com",
    PROXY_API_KEY: API_KEY
  });
  if (httpAawg.code === 0) {
    throw new Error("Expected fail-closed exit for plaintext http:// AAWG_BASE_URL");
  }
  results.push("Fail-closed startup (plaintext http:// AAWG_BASE_URL)");

  const proxy = spawn(process.execPath, [SERVER_JS], {
    env: {
      ...process.env,
      PORT: String(PORT),
      HOST: "127.0.0.1",
      TOKEN_PROXY_MODE: "simulator",
      PROXY_API_KEY: API_KEY,
      RATE_LIMIT_MAX: "6",
      RATE_LIMIT_WINDOW_MS: "60000"
    },
    stdio: ["ignore", "pipe", "pipe"]
  });

  try {
    await sleep(500);

    const health = await sendRequest("GET", "/health");
    if (
      health.status !== 200 ||
      health.body.includes("aawgTarget") ||
      health.headers["access-control-allow-origin"]
    ) {
      throw new Error("Health check leaked data or wildcard CORS: " + health.body);
    }
    results.push("GET /health returns 200 without target disclosure or wildcard CORS");

    const traversal = await sendRequest("GET", "/../package.json");
    if (traversal.status !== 404) {
      throw new Error("Expected 404 on static path, got " + traversal.status);
    }
    results.push("GET /../package.json returns 404");

    const unauth = await sendRequest(
      "POST",
      "/token-generation-service/token/getEncryptedToken",
      { use: "csaGuest" },
      { "Content-Type": "application/vnd.avaya.csa.tokens.v1+json" }
    );
    if (unauth.status !== 401) {
      throw new Error("Expected 401 without API key, got " + unauth.status);
    }
    results.push("POST without X-Client-Key rejected with 401");

    const badCt = await sendRequest(
      "POST",
      "/token-generation-service/token/getEncryptedToken",
      "{}",
      { "Content-Type": "text/plain", "X-Client-Key": API_KEY }
    );
    if (badCt.status !== 415) {
      throw new Error("Expected 415 for text/plain, got " + badCt.status);
    }
    results.push("POST with text/plain rejected with 415");

    const sipInj = await sendRequest(
      "POST",
      "/token-generation-service/token/getEncryptedToken",
      { use: "csaGuest", calledNumber: "1000;transport=tcp\r\nX-Inject: 1" },
      {
        "Content-Type": "application/vnd.avaya.csa.tokens.v1+json",
        "X-Client-Key": API_KEY
      }
    );
    if (sipInj.status !== 400) {
      throw new Error("Expected 400 for SIP injection, got " + sipInj.status);
    }
    results.push("POST with SIP injection blocked with 400");

    const badExp = await sendRequest(
      "POST",
      "/token-generation-service/token/getEncryptedToken",
      { use: "csaGuest", expiration: "9999999" },
      {
        "Content-Type": "application/vnd.avaya.csa.tokens.v1+json",
        "X-Client-Key": API_KEY
      }
    );
    if (badExp.status !== 400) {
      throw new Error("Expected 400 for excessive expiration, got " + badExp.status);
    }
    results.push("POST with expiration > 300000ms blocked with 400");

    const ok = await sendRequest(
      "POST",
      "/token-generation-service/token/getEncryptedToken",
      {
        use: "csaGuest",
        calledNumber: "1000",
        callingNumber: "cust-42",
        displayName: "Caller",
        expiration: "120000"
      },
      {
        "Content-Type": "application/vnd.avaya.csa.tokens.v1+json",
        "X-Client-Key": API_KEY
      }
    );
    const okJson = JSON.parse(ok.body);
    if (
      ok.status !== 200 ||
      !okJson.encryptedToken ||
      ok.headers["cache-control"] !== "no-store" ||
      ok.headers["x-content-type-options"] !== "nosniff" ||
      ok.headers["x-frame-options"] !== "DENY"
    ) {
      throw new Error("Valid token request or security headers failed: " + ok.body);
    }
    results.push("Valid csaGuest token request returns 200 with security headers");

    await sendRequest(
      "POST",
      "/api/tokens",
      { use: "csaGuest" },
      {
        "Content-Type": "application/vnd.avaya.csa.tokens.v1+json",
        "X-Client-Key": API_KEY
      }
    );
    const throttled = await sendRequest(
      "POST",
      "/api/tokens",
      { use: "csaGuest" },
      {
        "Content-Type": "application/vnd.avaya.csa.tokens.v1+json",
        "X-Client-Key": API_KEY
      }
    );
    if (throttled.status !== 429 || !throttled.headers["retry-after"]) {
      throw new Error("Expected 429 + Retry-After, got " + throttled.status);
    }
    results.push("Per-IP sliding-window rate limiter enforces 429 + Retry-After");

    console.log("PASS: All " + results.length + " token-proxy security checks succeeded.");
    results.forEach((line) => console.log("  - " + line));
  } finally {
    proxy.kill();
  }
}

run().catch((err) => {
  console.error("FAIL:", err.message || err);
  process.exit(1);
});
