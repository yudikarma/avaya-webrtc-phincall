# Intermediary Token Authentication Proxy

Implements the three-tier authorization architecture required by the Avaya WebRTC Connect Software Development Guide (Release 4.0.10, pp. 28-30):

1. The Android client sends `POST /api/tokens` (or `/tokengenerationservice/token/getEncryptedToken`) with `X-Client-Key` and JSON `{ use: "csaGuest", calledNumber, callingNumber, displayName, expiration }`.
2. This proxy validates the client key in constant time (`crypto.timingSafeEqual`), enforces per-IP rate limiting, sanitizes SIP identity fields, and enforces bounded expiration (`5000` to `300000` ms).
3. This proxy executes an mTLS (TLS 1.2+) `POST` to `https://<AAWGSERVER_ADDRESS>/csa/resources/tokens` using a server-side client certificate registered in AAWG's trusted hosts list, and returns `{ "encryptedToken": "..." }` with `Cache-Control: no-store`.

Client apps never hold AAWG client certificates and never call `/csa/resources/tokens` directly.

## Running Locally

Production AAWG mTLS proxy (fails closed unless `PROXY_API_KEY` >= 32 chars and mTLS PEM files exist):

```bash
cp .env.example .env
TOKEN_PROXY_MODE=aawg \
PROXY_API_KEY="<32+ char secret>" \
AAWG_BASE_URL=https://aawg.corp.example:443 \
AAWG_CLIENT_CERT_PATH=./certs/token-service-cert.pem \
AAWG_CLIENT_KEY_PATH=./certs/token-service-key.pem \
AAWG_CA_CERT_PATH=./certs/aawg-ca-bundle.pem \
PROXY_TLS_CERT_PATH=./certs/proxy-server-cert.pem \
PROXY_TLS_KEY_PATH=./certs/proxy-server-key.pem \
node server.js
```

Offline simulator mode for local Android Emulator testing (`10.0.2.2:8443`, `api/tokens`):

```bash
npm run start:simulator
```
