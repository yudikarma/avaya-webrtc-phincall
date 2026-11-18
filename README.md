# PhinCall: Multi-Platform Avaya WebRTC Connect 4.0.10 Reference Suite

Production reference implementation demonstrating how to integrate Avaya WebRTC Connect SDK (Release 4.0.10) across **Android (Kotlin)**, **iOS (Swift / Objective-C)**, and **Web (HTML5 / JavaScript)** with an **Intermediary Token Authentication Proxy (Node.js)** for Avaya Oceana and Avaya Aura Elite contact centers.

## Architecture Overview

### 1. Three-Tier Token Authorization Pattern (Guide 4.0.10, pp. 28-31)

Client apps never store AAWG client certificates and never send direct requests to `https://<AAWGSERVER_ADDRESS>/csa/resources/tokens`.

```text
[Android / iOS / Web Client]
        |  POST /api/tokens (JSON: use="csaGuest", calledNumber, callingNumber, displayName, expiration)
        v
[Intermediary Token Proxy (token-proxy/server.js)]
        |  1) Authenticates client (X-Client-Key / enterprise OAuth hook)
        |  2) mTLS POST https://<AAWGSERVER_ADDRESS>/csa/resources/tokens
        |     Content-Type: application/vnd.avaya.csa.tokens.v1+json
        v
[Avaya Aura Web Gateway Authorization Service]
        |  200 OK -> { "encryptedToken": "<single-use token>" }
        v
[Client stores token in memory only -> attached to Interaction -> valid for one call]
```

### 2. Four-Step Work and Interaction Lifecycle

Every platform follows the identical 4-step sequence defined in Avaya WebRTC Connect Guide 4.0.10 (pp. 31-32):

1. **Fetch Authorization Token**: POST to intermediary proxy and keep `encryptedToken` strictly in memory.
2. **Initialize SDK Client**: Instantiate `OceanaCustomerWebVoiceVideo` (Android/Web) or `AOOceanaCustomerWebVoiceVideo` (iOS) with AMC `Config` (Oceana only) and `WebGatewayConfiguration` (AAWG).
3. **Create and Populate `Work`**:
   - **Services / Attributes** (Oceana skill-based routing): e.g., `Language: English; Service: Sales`
   - **Context** (Agent Screen Pops, Appendix A): e.g., `ORD-2026-00419` mapped to `Prompted Digits` / `CollectedDigits`
   - **Topic** (Customer Journey widget): e.g., `billing` or `technical support`
4. **Create and Start Interaction**: Derive `AudioInteraction` or `VideoInteraction`, set `PlatformType` (`OCEANA` or `ELITE`), attach token and event listeners, configure local/remote video views, and call `start()`. Termination calls `end()` followed by `discard()`, returning to Pre-Call only on `onDiscardComplete()` / `discardComplete()` (Appendix D).

## Repository Layout

- `token-proxy/` : Zero-dependency Node.js intermediary authentication proxy (`server.js`) with mTLS AAWG forwarding, local simulator mode, and static Web hosting.
- `android/` : Kotlin Android Studio project (`compileSdk = 36`, `targetSdk = 36`, `minSdk = 28`, 16 KB page-size compliant `jniLibs` packaging, `oceanacustomerwebvoicevideo.aar`, configurable `VideoSurfaceView` presets with `videoCornerRadius` and `videoBorderWidth`, Android 14-16 foreground service, background camera restoration).
- `ios/` : Swift / Objective-C bridged Xcode project (`project.yml`, `Podfile`, `Info.plist` with `audio` and `voip` background modes, universal XCFramework embed configuration, delegate callbacks, camera select/switch and `setVideoCaptureResolutionWithCaptureOrientation`).
- `web/` : Accessible HTML5 / JS reference client targeting `OceanaCustomerWebVoiceVideo.js`, plus an explicit opt-in Local SDK Simulator for lab-free UI testing.
- `DESIGN.md` : Shared visual system, WCAG AA contrast verification, and layout decisions.

## Quick Start

### Intermediary Token Proxy and Web Client

Start in local simulator mode (no live AAWG cluster required):

```bash
cd token-proxy
npm run start:simulator
```

Open `http://localhost:8443` in Chrome, Firefox, or Chromium Edge.

To run against a live Avaya Aura Web Gateway:

1. Copy `token-proxy/.env.example` and supply `AAWG_BASE_URL`, `AAWG_CLIENT_CERT_PATH`, `AAWG_CLIENT_KEY_PATH`, and `AAWG_CA_CERT_PATH`.
2. Copy Avaya's `OceanaCustomerWebVoiceVideo.js` and `jquery-3.7.1.min.js` into `web/vendor/` and uncomment their `<script defer>` tags in `web/index.html`.
3. Run `TOKEN_PROXY_MODE=aawg node token-proxy/server.js`.

### Android Application (Kotlin, SDK 36, 16 KB Page Support)

`oceanacustomerwebvoicevideo.aar` (Release 4.0.10, CSDK 605.1.10) is pre-placed in `android/app/libs/`.

```bash
cd android
./gradlew assembleDebug
```

All four bundled `arm64-v8a` shared libraries (`libavayaclientmedia.so`, `libavayaclientservices.so`, `libc++_shared.so`, `librvVideoCodec.so`) have 16 KB (`0x4000`) ELF `PT_LOAD` alignment, and `app/build.gradle.kts` sets `packaging.jniLibs.useLegacyPackaging = false` so `.so` files are stored uncompressed and aligned to 16 KB ZIP boundaries for Android 15+ devices.

### iOS Application (Swift / Objective-C, Xcode 11+ / 16+)

See `ios/README.md` for the exact XCFramework **Do Not Embed** (`OceanaCustomerWebVoiceVideo.xcframework`) vs **Embed & Sign** (`AvayaClientServicesLite`, `AvayaClientMedia`, `RVVideoCodec`) configuration and `xcodegen generate` command.
