/**
 * Intermediary Token Authentication Proxy for Avaya WebRTC Connect 4.0.10.
 * Clients call this service instead of AAWG directly so mTLS client certificates remain server-side.
 */

const crypto = require("crypto");
const fs = require("fs");
const http = require("http");
const https = require("https");
const path = require("path");

const PORT = Number(process.env.PORT || 8443);
const HOST = process.env.HOST || "0.0.0.0";
const MODE = (process.env.TOKEN_PROXY_MODE || "aawg").toLowerCase();
const AAWG_BASE_URL = process.env.AAWG_BASE_URL || "https://aawg.example.com:443";
const AAWG_TOKEN_PATH = process.env.AAWG_TOKEN_PATH || "/csa/resources/tokens";
const PROXY_API_KEY = process.env.PROXY_API_KEY || "";
const CORS_ORIGIN = process.env.CORS_ORIGIN || "*";
const WEB_ROOT = path.resolve(__dirname, "..", "web");

const AAWG_MEDIA_TYPE = "application/vnd.avaya.csa.tokens.v1+json";
const MAX_BODY_BYTES = 16 * 1024;

const MIME_TYPES = {
  ".html": "text/html; charset=utf-8",
  ".css": "text/css; charset=utf-8",
  ".js": "application/javascript; charset=utf-8",
  ".json": "application/json; charset=utf-8",
  ".svg": "image/svg+xml",
  ".png": "image/png",
  ".ico": "image/x-icon",
  ".txt": "text/plain; charset=utf-8"
};

function setCorsHeaders(res) {
  res.setHeader("Access-Control-Allow-Origin", CORS_ORIGIN);
  res.setHeader("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
  res.setHeader("Access-Control-Allow-Headers", "Content-Type, Authorization, X-Client-Key");
}

function sendJson(res, statusCode, payload) {
  setCorsHeaders(res);
  res.writeHead(statusCode, { "Content-Type": "application/json; charset=utf-8" });
  res.end(JSON.stringify(payload));
}

function readJsonBody(req) {
  return new Promise((resolve, reject) => {
    const chunks = [];
    let total = 0;
    req.on("data", chunk => {
      total += chunk.length;
      if (total > MAX_BODY_BYTES) {
        reject(new Error("PAYLOAD_TOO_LARGE"));
        req.destroy();
        return;
      }
      chunks.push(chunk);
    });
    req.on("end", () => {
      try {
        const raw = Buffer.concat(chunks).toString("utf8").trim();
        resolve(raw ? JSON.parse(raw) : {});
      } catch {
        reject(new Error("INVALID_JSON"));
      }
    });
    req.on("error", reject);
  });
}

// Validates required fields per Avaya WebRTC Connect Guide 4.0.10 (pp. 29-30).
function validateTokenPayload(body) {
  if (!body || body.use !== "csaGuest") {
    return { valid: false, error: 'Field "use" is mandatory and must be "csaGuest".' };
  }

  const expiration = body.expiration !== undefined && body.expiration !== ""
    ? String(body.expiration).trim()
    : "120000";

  if (!/^\d+$/.test(expiration)) {
    return { valid: false, error: 'Field "expiration" must be a positive integer in milliseconds.' };
  }

  const sanitized = {
    use: "csaGuest",
    expiration
  };

  if (typeof body.calledNumber === "string" && body.calledNumber.trim()) {
    sanitized.calledNumber = body.calledNumber.trim();
  }
  if (typeof body.callingNumber === "string" && body.callingNumber.trim()) {
    sanitized.callingNumber = body.callingNumber.trim();
  }
  if (typeof body.displayName === "string" && body.displayName.trim()) {
    sanitized.displayName = body.displayName.trim();
  }

  return { valid: true, payload: sanitized };
}

function loadMtlsOptions() {
  const certPath = process.env.AAWG_CLIENT_CERT_PATH;
  const keyPath = process.env.AAWG_CLIENT_KEY_PATH;
  const caPath = process.env.AAWG_CA_CERT_PATH;

  const tlsOptions = { rejectUnauthorized: true };

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

// Proxies the validated payload to AAWG /csa/resources/tokens over mTLS.
function requestAawgToken(payload) {
  return new Promise((resolve, reject) => {
    const targetUrl = new URL(AAWG_TOKEN_PATH, AAWG_BASE_URL);
    const bodyText = JSON.stringify(payload);
    const tlsOptions = loadMtlsOptions();

    const options = {
      protocol: targetUrl.protocol,
      hostname: targetUrl.hostname,
      port: targetUrl.port || (targetUrl.protocol === "https:" ? 443 : 80),
      path: `${targetUrl.pathname}${targetUrl.search}`,
      method: "POST",
      headers: {
        "Content-Type": AAWG_MEDIA_TYPE,
        "Accept": "application/json",
        "Content-Length": Buffer.byteLength(bodyText)
      },
      timeout: 15000,
      ...tlsOptions
    };

    const transport = targetUrl.protocol === "https:" ? https : http;
    const proxyReq = transport.request(options, proxyRes => {
      const chunks = [];
      proxyRes.on("data", chunk => chunks.push(chunk));
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
    nonce: crypto.randomBytes(12).toString("base64url")
  };
  return `SIM_AAWG_TOKEN.${Buffer.from(JSON.stringify(envelope)).toString("base64url")}`;
}

async function handleTokenRequest(req, res) {
  if (PROXY_API_KEY) {
    const providedKey = req.headers["x-client-key"] || "";
    if (providedKey !== PROXY_API_KEY) {
      return sendJson(res, 401, { error: "Client authentication failed." });
    }
  }

  let body;
  try {
    body = await readJsonBody(req);
  } catch (err) {
    const status = err.message === "PAYLOAD_TOO_LARGE" ? 413 : 400;
    return sendJson(res, status, { error: err.message });
  }

  const validation = validateTokenPayload(body);
  if (!validation.valid) {
    return sendJson(res, 400, { error: validation.error });
  }

  if (MODE === "simulator") {
    return sendJson(res, 200, {
      encryptedToken: createSimulatorToken(validation.payload),
      mode: "simulator",
      expirationMs: Number(validation.payload.expiration)
    });
  }

  try {
    const upstream = await requestAawgToken(validation.payload);
    if (upstream.statusCode >= 200 && upstream.statusCode < 300) {
      let parsed;
      try {
        parsed = JSON.parse(upstream.bodyText);
      } catch {
        return sendJson(res, 502, { error: "AAWG returned non-JSON token response." });
      }
      if (!parsed.encryptedToken) {
        return sendJson(res, 502, { error: "AAWG response omitted encryptedToken." });
      }
      return sendJson(res, 200, { encryptedToken: parsed.encryptedToken });
    }

    return sendJson(res, upstream.statusCode, {
      error: "AAWG token service rejected the request.",
      upstreamStatus: upstream.statusCode
    });
  } catch (err) {
    return sendJson(res, 502, {
      error: "Unable to reach Avaya Aura Web Gateway token service.",
      detail: err.message
    });
  }
}

function serveStaticWebFile(req, res) {
  const reqUrl = new URL(req.url, `http://${req.headers.host || "localhost"}`);
  let relPath = decodeURIComponent(reqUrl.pathname);
  if (relPath === "/") relPath = "/index.html";

  const fullPath = path.resolve(WEB_ROOT, `.${relPath}`);
  if (!fullPath.startsWith(WEB_ROOT)) {
    res.writeHead(403, { "Content-Type": "text/plain; charset=utf-8" });
    return res.end("Forbidden");
  }

  fs.stat(fullPath, (err, stat) => {
    if (err || !stat.isFile()) {
      res.writeHead(404, { "Content-Type": "text/plain; charset=utf-8" });
      return res.end("Not Found");
    }

    const ext = path.extname(fullPath).toLowerCase();
    res.writeHead(200, { "Content-Type": MIME_TYPES[ext] || "application/octet-stream" });
    fs.createReadStream(fullPath).pipe(res);
  });
}

const server = http.createServer((req, res) => {
  const urlPath = new URL(req.url, `http://${req.headers.host || "localhost"}`).pathname;

  if (req.method === "OPTIONS") {
    setCorsHeaders(res);
    res.writeHead(204);
    return res.end();
  }

  if (req.method === "GET" && urlPath === "/health") {
    return sendJson(res, 200, {
      status: "ok",
      mode: MODE,
      aawgTarget: MODE === "aawg" ? `${AAWG_BASE_URL}${AAWG_TOKEN_PATH}` : "local-simulator"
    });
  }

  if (
    req.method === "POST" &&
    (urlPath === "/api/tokens" || urlPath === "/tokengenerationservice/token/getEncryptedToken")
  ) {
    return handleTokenRequest(req, res);
  }

  if (req.method === "GET") {
    return serveStaticWebFile(req, res);
  }

  return sendJson(res, 405, { error: "Method Not Allowed" });
});

server.listen(PORT, HOST, () => {
  console.log(`PhinCall Token Proxy listening on http://${HOST}:${PORT} (mode=${MODE})`);
  console.log(`Web reference client served from ${WEB_ROOT}`);
});
