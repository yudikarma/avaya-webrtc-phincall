# PhinCall: Avaya WebRTC Connect 4.0.10 Android Reference App and Hardened Token Proxy

Android (Kotlin, SDK 36, 16 KB page-size compliant) reference implementation and hardened Node.js Intermediary Token Proxy for Avaya Oceana and Avaya Aura Elite contact center infrastructure using `oceanacustomerwebvoicevideo.aar` (Release 4.0.10, CSDK 605.1.10).

## Architecture Overview

### 1. Three-Tier Token Authorization Pattern (Guide 4.0.10, pp. 28-31)

The Android client never stores AAWG mTLS client certificates and never sends direct requests to `https://<AAWGSERVER_ADDRESS>/csa/resources/tokens`.

```text
[Android Client (Kotlin, SDK 36)]
        |  HTTPS POST /token-generation-service/token/getEncryptedToken (or /api/tokens)
        |  Headers: Content-Type: application/vnd.avaya.csa.tokens.v1+json, X-Client-Key
        |  JSON: { use: "csaGuest", calledNumber, callingNumber, displayName, expiration }
        v
[Intermediary Token Proxy (token-proxy/server.js)]
        |  1) Constant-time API key auth + sliding-window IP rate limit + SIP input sanitization
        |  2) mTLS (TLS 1.2+) POST https://<AAWGSERVER_ADDRESS>/csa/resources/tokens
        v
[Avaya Aura Web Gateway Authorization Service]
        |  200 OK -> { "encryptedToken": "<single-use token>" }
        v
[Android stores token in memory only -> attached to Interaction -> valid for one call]
```

### 2. Four-Step Work and Interaction Lifecycle

1. **Fetch Authorization Token**: `TokenRepository` posts to the intermediary proxy over TLS 1.2+ (no redirects, no disk cache) and stores `encryptedToken` in memory only.
2. **Initialize SDK Client**: `InteractionCoordinator` builds `Config` (AMC cluster for Oceana) and `WebGatewayConfiguration` (AAWG), then instantiates `OceanaCustomerWebVoiceVideo`.
3. **Create and Populate `Work`**:
   - **Services / Attributes** (Oceana): 3 configurable attribute pairs (defaults matching Avaya Reference Client: `Location: Inhouse`, `Language: English`, `Service: Sales`), `priority` (`1-10`, default `5`), `locale` (`en_us`), and `routingStrategy` (`MOST_IDLE` or `LEAST_OCCUPIED`).
   - **Resources**: optional `sourceName` and `nativeResourceId`.
   - **Context** (Agent Screen Pops, Appendix A) and **Topic** (Customer Journey).
4. **Create and Start Interaction**: Derives `AudioInteraction` or `VideoInteraction`, sets `PlatformType.OCEANA` or `PlatformType.ELITE`, binds local/remote `VideoSurfaceView` views with configurable `videoCornerRadius` and `videoBorderWidth`, registers listeners, and calls `start()`. Termination calls `end()` followed by `discard()` and returns to Pre-Call on `onDiscardComplete()` (Appendix D).

## Sample App Settings Parity (`preferences.xml` and `preferences_elite.xml`)

Every setting from the official Avaya Android Reference Client is persisted per platform (Oceana vs Elite) in app-private `MODE_PRIVATE` SharedPreferences (while tokens and proxy API keys remain strictly in memory):

- **AMC Cluster (Oceana)**: Address, Port (`443`), Use HTTPS (`true`), URL Path (`services/AvayaMobileCommunications/sessions`)
- **AAWG Configuration**: Server Address, Port (`443`), Use HTTPS (`true`), URL Path (`csa/resources/tenants/default`)
- **Token Service**: Address, Port (`443`), Use HTTPS (`true`), URL Path (`token-generation-service/token/getEncryptedToken`), Proxy API Key (`X-Client-Key`, masked, in-memory only)
- **Caller and Interaction**: Display Name, From Address, Destination Address, Context, Topic
- **Service (Work API)**: Priority (`5`), Locale (`en_us`), Routing Strategy (`MOST_IDLE`, `LEAST_OCCUPIED`)
- **Routing Attributes (Oceana)**: Three key/value entries (`Location: Inhouse`, `Language: English`, `Service: Sales`)
- **Resource (Work API)**: Source Name, Resource Id
- **Video**: Local `VideoSurfaceView` corner radius and border width presets (`0/0`, `16/2`, `24/2`, `32/3`), capture resolution (`Min`, `270p`, `360p`, `540p`, `Max`), orientation (`LandscapeOrPortrait`, `LandscapeOnly`), optional auto-mute on background
- **Logging and Diagnostics**: Log to device (app-private `filesDir/logs/`), sanitized Log File Name (`WebRTCReferenceClient`), Support Email + Send Logs via `FileProvider`, and live In-Call Audio/Video Statistics (`readAudioDetails` / `readVideoDetails`)

## Repository Layout

- `android/` : Kotlin Android Studio project (`compileSdk = 36`, `targetSdk = 36`, `minSdk = 28`, 16 KB aligned `jniLibs`, patched Netty `4.1.119.Final`, release/debug `network_security_config.xml`).
- `token-proxy/` : Zero-dependency hardened Node.js intermediary authentication proxy (`server.js`).
- `DESIGN.md` : Visual system, WCAG AA contrast verification, and layout decisions.

## Quick Start

### 1. Copy the Avaya Android SDK AAR

Copy `oceanacustomerwebvoicevideo.aar` (Release 4.0.10) into `android/app/libs/oceanacustomerwebvoicevideo.aar`.

### 2. Build Android Debug and Release APKs

```bash
cd android
./gradlew assembleDebug
```

### 3. Run the Intermediary Token Proxy

Production mTLS mode (fails closed unless `PROXY_API_KEY` >= 32 chars and mTLS PEM certificates exist):

```bash
cd token-proxy
cp .env.example .env
node server.js
```

Offline simulator mode for local Android Emulator testing (`10.0.2.2:8443`):

```bash
cd token-proxy
npm run start:simulator
```
