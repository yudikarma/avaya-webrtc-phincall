# Intermediary Token Authentication Proxy

Implements the three-tier authorization architecture required by the Avaya WebRTC Connect Software Development Guide (Release 4.0.10, pp. 28-30):

1. Client app (Android, iOS, or Web) sends `POST /api/tokens` (or `/tokengenerationservice/token/getEncryptedToken`) to this intermediary service.
2. This service validates the client (`X-Client-Key`, replaceable with enterprise OAuth/OIDC) and payload (`use: "csaGuest"`).
3. This service performs an mTLS `POST` to `https://<AAWGSERVER_ADDRESS>/csa/resources/tokens` using a server-side client certificate registered in AAWG's trusted hosts list, and returns `{ "encryptedToken": "..." }`.

Client applications never hold AAWG client certificates and never contact `/csa/resources/tokens` directly.

## Running Locally

Production AAWG mTLS proxy:

```bash
cp .env.example .env
# Populate AAWG_BASE_URL and mTLS certificate paths in your environment
TOKEN_PROXY_MODE=aawg AAWG_BASE_URL=https://aawg.corp.example:443 node server.js
```

Local UI simulator mode (for testing without a live AAWG cluster):

```bash
npm run start:simulator
```

Then open `http://localhost:8443` to access the Web reference client, or point the Android/iOS apps to `<your-lan-ip>:8443` with path `api/tokens`.
