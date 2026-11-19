/**
 * Hardened Intermediary Token Authentication Proxy for Avaya WebRTC Connect 4.0.10 (Android).
 * Keeps AAWG mTLS client certificates server-side, authenticates clients, rate-limits requests,
 * validates payloads against SIP header injection, and proxies to AAWG over TLS 1.2+.
 */

const crypto = require("crypto");
const fs = require("fs");
const http = require("http");
const https = require("https");

const PORT = Number(process.env.PORT || 8443);
const HOST = process.env.HOST || "127.0.0.1";
const MODE = (process.env.TOKEN_PROXY_MODE || "aawg").toLowerCase();
const ALLOW_UNAUTHENTICATED_DEV = process.env.ALLOW_UNAUTHENTICATED_DEV === "true";

const AAWG_BASE_URL = process.env.AAWG_BASE_URL || "https://aawg.example.com:443";
const AAWG_TOKEN_PATH = process.env.AAWG_TOKEN_PATH || "/csa/resources/tokens";
const PROXY_API_KEY = process.env.PROXY_API_KEY || "";

const PROXY_TLS_CERT_PATH = process.env.PROXY_TLS_CERT_PATH || "";
const PROXY_TLS_KEY_PATH = process.env.PROXY_TLS_KEY_PATH || "";
const PROXY_TLS_KEY_PASSPHRASE = process.env.PROXY_TLS_KEY_PASSPHRASE || "";

const RATE_LIMIT_MAX = Math.max(1, Number(process.env.RATE_LIMIT_MAX || 30));
const RATE_LIMIT_WINDOW_MS = Math.max(1000, Number(process.env.RATE_LIMIT_WINDOW_MS || 60000));
const MAX_RATE_LIMIT_BUCKETS = 10000;

const AAWG_MEDIA_TYPE = "application/vnd.avaya.csa.tokens.v1+json";
const MAX_REQUEST_BYTES = 8 * 1024;
const MAX_UPSTREAM_BYTES = 64 * 1024;
const MIN_EXPIRATION_MS = 5000;
const MAX_EXPIRATION_MS = 300000;

const SAFE_SIP_USER_PATTERN = /^[A-Za-z0-9._+*#@-]{1,64}$/;
const UNSAFE_DISPLAY_CHARS = /[\x00-\x1F\x7F<>";\\]/;

const rateBuckets = new Map();

// Fail closed at startup if production AAWG mode lacks mandatory auth or mTLS config.
function validateStartupConfig() {
  if (MODE !== "aawg" && MODE !== "simulator") {
    throw new Error(`Unsupported TOKEN_PROXY_MODE "${MODE}". Use "aawg" or "simulator".`);
  }

  if (MODE === "aawg") {
    const parsedUrl = new URL(AAWG_BASE_URL);
    if (parsedUrl.protocol !== "https:") {
      throw new Error("AAWG_BASE_URL must use https:// in aawg mode.");
    }
    if (!ALLOW_UNAUTHENTICATED_DEV && PROXY_API_KEY.length < 32) {
      throw new Error(
        "PROXY_API_KEY (>= 32 chars) is required in aawg mode. Set ALLOW_UNAUTHENTICATED_DEV=true only for isolated lab testing."
      );
    }
    const certPath = process.env.AAWG_CLIENT_CERT_PATH || "";
    const keyPath = process.env.AAWG_CLIENT_KEY_PATH || "";
    if (!certPath || !fs.existsSync(certPath) || !keyPath || !fs.existsSync(keyPath)) {
      throw new Error(
        "AAWG_CLIENT_CERT_PATH and AAWG_CLIENT_KEY_PATH must point to existing PEM files in aawg mode."
      );
    }
  }
}

function sha256Digest(value) {
  return crypto.createHash("sha256").update(String(value), "utf8").digest();
}

// Constant-time API key comparison prevents timing side-channel leaks.
function verifyClientApiKey(providedKey) {
  if (!PROXY_API_KEY) {
    return ALLOW_UNAUTHENTICATED_DEV || MODE === "simulator";
  }
  if (typeof providedKey !== "string" || providedKey.length === 0) {
    return false;
  }
  return crypto.timingSafeEqual(sha256Digest(providedKey), sha256Digest(PROXY_API_KEY));
}

function checkRateLimit(clientIp) {
  const now = Date.now();
  if (rateBuckets.size >= MAX_RATE_LIMIT_BUCKETS && !rateBuckets.has(clientIp)) {
    for (const [ip, entry] of rateBuckets.entries()) {
      if (now - entry.windowStart >= RATE_LIMIT_WINDOW_MS) {
        rateBuckets.delete(ip);
      }
    }
    if (rateBuckets.size >= MAX_RATE_LIMIT_BUCKETS) {
      return { allowed: false, retryAfterSec: Math.ceil(RATE_LIMIT_WINDOW_MS / 1000) };
    }
  }

  const bucket = rateBuckets.get(clientIp);
  if (!bucket || now - bucket.windowStart >= RATE_LIMIT_WINDOW_MS) {
    rateBuckets.set(clientIp, { windowStart: now, count: 1 });
    return { allowed: true, retryAfterSec: 0 };
  }

  if (bucket.count >= RATE_LIMIT_MAX) {
    const remainingMs = Math.max(1000, RATE_LIMIT_WINDOW_MS - (now - bucket.windowStart));
    return { allowed: false, retryAfterSec: Math.ceil(remainingMs / 1000) };
  }

  bucket.count += 1;
  return { allowed: true, retryAfterSec: 0 };
}

function applySecurityHeaders(res, isTls) {
  res.setHeader("Cache-Control", "no-store");
  res.setHeader("Pragma", "no-cache");
  res.setHeader("X-Content-Type-Options", "nosniff");
  res.setHeader("X-Frame-Options", "DENY");
  res.setHeader("Referrer-Policy", "no-referrer");
  if (isTls) {
    res.setHeader("Strict-Transport-Security", "max-age=31536000; includeSubDomains");
  }
}

function sendJson(res, statusCode, payload, isTls, extraHeaders = {}) {
  applySecurityHeaders(res, isTls);
  res.writeHead(statusCode, {
    "Content-Type": "application/json; charset=utf-8",
    ...extraHeaders
  });
  res.end(JSON.stringify(payload));
}

function isAllowedContentType(headerValue) {
  if (typeof headerValue !== "string") return false;
  const mediaType = headerValue.split(";")[0].trim().toLowerCase();
  return mediaType === AAWG_MEDIA_TYPE || mediaType === "application/json";
}

function readJsonBody(req) {
  return new Promise((resolve, reject) => {
    const chunks = [];
    let total = 0;
    req.on("data", chunk => {
      total += chunk.length;
      if (total > MAX_REQUEST_BYTES) {
        reject(new Error("PAYLOAD_TOO_LARGE"));
        req.destroy();
        return;
      }
      chunks.push(chunk);
    });
    req.on("end", () => {
      try {
        const raw = Buffer.concat(chunks).toString("utf8").trim();
        const parsed = raw ? JSON.parse(raw) : {};
        if (!parsed || typeof parsed !== "object" || Array.isArray(parsed)) {
          reject(new Error("INVALID_JSON"));
          return;
        }
        resolve(parsed);
      } catch {
        reject(new Error("INVALID_JSON"));
      }
    });
    req.on("error", reject);
  });
}

// Validates token payload against Guide 4.0.10 (pp. 29-30) and blocks SIP URI/header injection.
function validateTokenPayload(body) {
  if (!body || body.use !== "csaGuest") {
    return { valid: false, error: 'Field "use" is mandatory and must be "csaGuest".' };
  }

  const rawExpiration = body.expiration !== undefined && body.expiration !== ""
    ? String(body.expiration).trim()
    : "120000";

  if (!/^\d{4,6}$/.test(rawExpiration)) {
    return { valid: false, error: `Field "expiration" must be between ${MIN_EXPIRATION_MS} and ${MAX_EXPIRATION_MS} ms.` };
  }
  const expNum = Number(rawExpiration);
  if (expNum < MIN_EXPIRATION_MS || expNum > MAX_EXPIRATION_MS) {
    return { valid: false, error: `Field "expiration" must be between ${MIN_EXPIRATION_MS} and ${MAX_EXPIRATION_MS} ms.` };
  }

  const sanitized = {
    use: "csaGuest",
    expiration: String(expNum)
  };

  if (body.calledNumber !== undefined && body.calledNumber !== null && String(body.calledNumber).trim() !== "") {
    const called = String(body.calledNumber).trim();
    if (!SAFE_SIP_USER_PATTERN.test(called)) {
      return { valid: false, error: 'Field "calledNumber" contains invalid characters or exceeds 64 chars.' };
    }
    sanitized.calledNumber = called;
  }

  if (body.callingNumber !== undefined && body.callingNumber !== null && String(body.callingNumber).trim() !== "") {
    const calling = String(body.callingNumber).trim();
    if (!SAFE_SIP_USER_PATTERN.test(calling)) {
      return { valid: false, error: 'Field "callingNumber" contains invalid characters or exceeds 64 chars.' };
    }
    sanitized.callingNumber = calling;
  }

  if (body.displayName !== undefined && body.displayName !== null && String(body.displayName).trim() !== "") {
    const display = String(body.displayName).trim();
    if (display.length > 80 || UNSAFE_DISPLAY_CHARS.test(display)) {
      return { valid: false, error: 'Field "displayName" contains disallowed characters or exceeds 80 chars.' };
    }
    sanitized.displayName = display;
  }

  return { valid: true, payload: sanitized };
}

function loadUpstreamMtlsOptions() {
  const tlsOptions = {
    rejectUnauthorized: true,
    minVersion: "TLSv1.2"
  };

  const certPath = process.env.AAWG_CLIENT_CERT_PATH;
  const keyPath = process.env.AAWG_CLIENT_KEY_PATH;
  const caPath = process.env.AAWG_CA_CERT_PATH;

  if (certPath && fs.existsSync(certPath)) {
    tlsOptions.cert = fs.readFileSync(certPath);
  }
  if (keyPath && fs.existsSync(keyPath)) {
    tlsOptions.key = fs.readFileSync(keyPath);
  }
  if (process.env.AAWG_CLIENT_KEY_PASSPHRASE) {
    tlsOptions.passphrase = process.env.AAWG_CLIENT_KEY_PASSPHRASE;
  }
  if (caPath && fs.existsSync(caPath)) {
    tlsOptions.ca = fs.readFileSync(caPath);
  }

  return tlsOptions;
}

// Proxies the validated payload to AAWG /csa/resources/tokens over mTLS (TLS 1.2+).
function requestAawgToken(payload) {
  return new Promise((resolve, reject) => {
    const targetUrl = new URL(AAWG_TOKEN_PATH, AAWG_BASE_URL);
    if (targetUrl.protocol !== "https:") {
      reject(new Error("Upstream AAWG endpoint must use HTTPS."));
      return;
    }

    const bodyText = JSON.stringify(payload);
    const tlsOptions = loadUpstreamMtlsOptions();

    const options = {
      protocol: "https:",
      hostname: targetUrl.hostname,
      port: targetUrl.port || 443,
      path: `${targetUrl.pathname}${targetUrl.search}`,
      method: "POST",
      headers: {
        "Content-Type": AAWG_MEDIA_TYPE,
        "Accept": `${AAWG_MEDIA_TYPE}, application/json, */*`,
        "Content-Length": Buffer.byteLength(bodyText)
      },
      timeout: 15000,
      ...tlsOptions
    };

    const proxyReq = https.request(options, proxyRes => {
      const chunks = [];
      let total = 0;
      proxyRes.on("data", chunk => {
        total += chunk.length;
        if (total > MAX_UPSTREAM_BYTES) {
          proxyReq.destroy(new Error("Upstream AAWG response exceeded size limit."));
          return;
        }
        chunks.push(chunk);
      });
      proxyRes.on("end", () => {
        const text = Buffer.concat(chunks).toString("utf8");
        resolve({ statusCode: proxyRes.statusCode || 502, bodyText: text });
      });
    });

    proxyReq.on("timeout", () => {
      proxyReq.destroy(new Error("AAWG request timed out after 15s."));
    });
    proxyReq.on("error", reject);
    proxyReq.write(bodyText);
    proxyReq.end();
  });
}

function createSimulatorToken(payload) {
  const envelope = {
    simulator: true,
    use: payload.use,
    calledNumber: payload.calledNumber || null,
    callingNumber: payload.callingNumber || `guest-${crypto.randomBytes(3).toString("hex")}`,
    displayName: payload.displayName || "",
    issuedAt: Date.now(),
    expiresInMs: Number(payload.expiration),
    nonce: crypto.randomBytes(16).toString("base64url")
  };
  return `SIM_AAWG_TOKEN.${Buffer.from(JSON.stringify(envelope)).toString("base64url")}`;
}

async function handleTokenRequest(req, res, isTls) {
  const clientIp = req.socket?.remoteAddress || "unknown";
  const rate = checkRateLimit(clientIp);
  if (!rate.allowed) {
    return sendJson(
      res,
      429,
      { error: "Rate limit exceeded. Retry later." },
      isTls,
      { "Retry-After": String(rate.retryAfterSec) }
    );
  }

  if (!verifyClientApiKey(req.headers["x-client-key"])) {
    return sendJson(res, 401, { error: "Client authentication failed." }, isTls);
  }

  if (!isAllowedContentType(req.headers["content-type"])) {
    return sendJson(
      res,
      415,
      { error: `Content-Type must be ${AAWG_MEDIA_TYPE} or application/json.` },
      isTls
    );
  }

  let body;
  try {
    body = await readJsonBody(req);
  } catch (err) {
    const status = err.message === "PAYLOAD_TOO_LARGE" ? 413 : 400;
    return sendJson(res, status, { error: err.message }, isTls);
  }

  const validation = validateTokenPayload(body);
  if (!validation.valid) {
    return sendJson(res, 400, { error: validation.error }, isTls);
  }

  if (MODE === "simulator") {
    return sendJson(res, 200, {
      encryptedToken: createSimulatorToken(validation.payload),
      mode: "simulator",
      expirationMs: Number(validation.payload.expiration)
    }, isTls);
  }

  try {
    const upstream = await requestAawgToken(validation.payload);
    if (upstream.statusCode >= 200 && upstream.statusCode < 300) {
      let parsed;
      try {
        parsed = JSON.parse(upstream.bodyText);
      } catch {
        return sendJson(res, 502, { error: "Upstream authorization service returned an invalid response." }, isTls);
      }
      if (!parsed.encryptedToken || typeof parsed.encryptedToken !== "string") {
        return sendJson(res, 502, { error: "Upstream authorization response omitted encryptedToken." }, isTls);
      }
      return sendJson(res, 200, { encryptedToken: parsed.encryptedToken }, isTls);
    }

    const clientStatus = upstream.statusCode === 400 ? 400 : 502;
    return sendJson(res, clientStatus, { error: "Authorization token could not be issued." }, isTls);
  } catch (err) {
    // Log sanitized failure server-side only; never expose internal hostnames or TLS stacks to clients.
    console.error("[TokenProxy] Upstream AAWG request failed:", err.code || err.message);
    return sendJson(res, 502, { error: "Authorization service temporarily unavailable." }, isTls);
  }
}

function createServer() {
  const useTls = Boolean(PROXY_TLS_CERT_PATH && PROXY_TLS_KEY_PATH);
  const requestHandler = (req, res) => {
    let urlPath;
    try {
      urlPath = new URL(req.url, "http://localhost").pathname;
    } catch {
      return sendJson(res, 400, { error: "Malformed request URI." }, useTls);
    }

    if (req.method === "GET" && urlPath === "/health") {
      return sendJson(res, 200, { status: "ok", mode: MODE }, useTls);
    }

    if (
      req.method === "POST" &&
      (urlPath === "/api/tokens" ||
        urlPath === "/token-generation-service/token/getEncryptedToken" ||
        urlPath === "/tokengenerationservice/token/getEncryptedToken")
    ) {
      return handleTokenRequest(req, res, useTls);
    }

    if (req.method !== "GET" && req.method !== "POST") {
      return sendJson(res, 405, { error: "Method Not Allowed" }, useTls);
    }

    return sendJson(res, 404, { error: "Not Found" }, useTls);
  };

  const server = useTls
    ? https.createServer(
        {
          cert: fs.readFileSync(PROXY_TLS_CERT_PATH),
          key: fs.readFileSync(PROXY_TLS_KEY_PATH),
          ...(PROXY_TLS_KEY_PASSPHRASE ? { passphrase: PROXY_TLS_KEY_PASSPHRASE } : {}),
          minVersion: "TLSv1.2"
        },
        requestHandler
      )
    : http.createServer(requestHandler);

  // Mitigate slow-client / Slowloris header and body exhaustion.
  server.requestTimeout = 15000;
  server.headersTimeout = 10000;
  server.keepAliveTimeout = 5000;

  return { server, useTls };
}

validateStartupConfig();
const { server, useTls } = createServer();
server.listen(PORT, HOST, () => {
  const scheme = useTls ? "https" : "http";
  console.log(`PhinCall Token Proxy listening on ${scheme}://${HOST}:${PORT} (mode=${MODE})`);
});
