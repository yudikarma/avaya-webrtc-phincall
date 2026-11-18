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

### 2. Live Runtime Platform Switching (Oceana vs Elite)

`RuntimeConfigManager` exposes a reactive `StateFlow<InteractionLaunchConfig>` backed by separate persisted Oceana and Elite profiles in `SettingsStore`:

- **Dedicated Runtime Settings Screen (`SettingsActivity`)**: Switch live between **Avaya Oceana** and **Avaya Aura Elite**. Switching immediately saves the outgoing platform's draft, loads the target platform's profile, dynamically hides/shows Oceana-only sections (AMC Cluster and Routing Attributes), updates Destination validation hints (optional for Oceana, required for Elite), and updates the runtime execution path.
- **Click-to-Call Launcher (`PreCallActivity`)**: Displays a live Active Runtime Profile summary card, quick platform switcher synced with Settings, quick Context/Topic fields, live validation banner, and Voice/Video Live Help triggers.
- **SDK Execution (`InteractionCoordinator`)**:
  - `PlatformType.OCEANA`: Initializes `Config` (AMC cluster) + `WebGatewayConfiguration` (AAWG), attaches `Work` Services with 3 skill attribute rows and priority, and treats `destinationAddress` as optional.
  - `PlatformType.ELITE`: Initializes neutral `Config(null)` + `WebGatewayConfiguration` (AAWG), skips AMC and Oceana skill attributes, enforces `destinationAddress`, and dials directly over AAWG.

### 3. Sample App Settings Parity (`preferences.xml` and `preferences_elite.xml`)

Every setting from the official Avaya Android Reference Client is persisted per platform in app-private `MODE_PRIVATE` SharedPreferences (while tokens and proxy API keys remain strictly in memory):

- **AMC Cluster (Oceana only)**: Address, Port (`443`), Use HTTPS (`true`), URL Path (`services/AvayaMobileCommunications/sessions`)
- **AAWG Configuration**: Server Address, Port (`443`), Use HTTPS (`true`), URL Path (`csa/resources/tenants/default`)
- **Token Service**: Address (default `Labdemo.phintraco.com`), Port (`443`), Use HTTPS (`true`), URL Path (`token-generation-service/token/getEncryptedToken`), Proxy API Key (`X-Client-Key`, masked, in-memory only)
- **Caller and Interaction**: Display Name, From Address, Destination Address, Context (Agent Screen Pop), Topic (Customer Journey)
- **Service (Work API)**: Priority (`5`), Locale (`en_us`), Routing Strategy (`MOST_IDLE`, `LEAST_OCCUPIED`)
- **Routing Attributes (Oceana only)**: Three key/value entries (`Location: Inhouse`, `Language: English`, `Service: Sales`)
- **Resource (Work API)**: Source Name, Resource Id
- **Video**: Local `VideoSurfaceView` corner radius and border width presets (`0/0`, `16/2`, `24/2`, `32/3`), capture resolution (`Min`, `270p`, `360p`, `540p`, `Max`), orientation (`LandscapeOrPortrait`, `LandscapeOnly`), optional auto-mute on background
- **Logging and Diagnostics**: Log to device (app-private `filesDir/logs/`), sanitized Log File Name (`WebRTCReferenceClient`), Support Email + Send Logs via `FileProvider`, and live In-Call Audio/Video Statistics (`readAudioDetails` / `readVideoDetails`)

## Repository Layout

- `android/` : Self-contained Kotlin Android Studio project (`compileSdk = 36`, `targetSdk = 36`, `minSdk = 28`, 16 KB aligned `jniLibs`, patched Netty `4.1.119.Final`, release/debug `network_security_config.xml`). Open `android/` directly in Android Studio.
- `token-proxy/` : Zero-dependency hardened Node.js intermediary authentication proxy (`server.js`) with automated security test suite (`npm test`).
- `.github/workflows/ci-cd.yml` : GitHub Actions CI/CD workflow for security tests, Android unit tests, 16 KB `zipalign` verification, APK artifact uploads, and GitHub Releases on `v*` tags.
- `DESIGN.md` : Visual system, WCAG AA contrast verification, and layout decisions.
- `SECURITY.md` : Security audit findings and remediations.

## Quick Start

### 1. Copy the Avaya Android SDK AAR

Copy `oceanacustomerwebvoicevideo.aar` (Release 4.0.10) into `android/app/libs/oceanacustomerwebvoicevideo.aar`.

### 2. Open in Android Studio or Build via Gradle

Open the `android/` directory directly in Android Studio, or build and test from the command line:

```bash
cd android
./gradlew testDebugUnitTest assembleDebug compileReleaseKotlin
```

### 3. Run and Test the Intermediary Token Proxy

Run the automated security verification suite:

```bash
cd token-proxy
npm test
```

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

## CI/CD and Automatic APK Builds (GitHub Actions)

`.github/workflows/ci-cd.yml` runs automatically on pushes to `main`, pull requests, version tags (`v*`), and manual `workflow_dispatch`:

1. **`token-proxy-security`**: Validates syntax and runs `npm test` (fail-closed startup, auth, SIP injection, rate limiting, and security header checks).
2. **`android-project-checks`**: Verifies Android Gradle wrapper files, release cleartext/CA hardening, and ensures no `.aar` or keystore files are committed.
3. **`android-build-apk`**: Runs unit tests (`testDebugUnitTest`), builds `phincall-debug.apk` (and `phincall-app-release.apk` when signing secrets or `v*` tags are used), verifies 16 KB alignment with `zipalign -c -P 16 -v 4`, uploads APKs + `SHA256SUMS.txt` as workflow artifacts, and publishes a GitHub Release on `v*` tags.

Because `oceanacustomerwebvoicevideo.aar` is proprietary and excluded from Git, enable APK compilation in GitHub Actions with one of the following:

| Secret / Variable | Purpose |
|---|---|
| `AVAYA_SDK_RELEASE_TAG` (Variable, optional) | Release tag hosting `oceanacustomerwebvoicevideo.aar` (defaults to `sdk-4.0.10`). Works automatically with `GITHUB_TOKEN` when this repo is **Private**. |
| `AVAYA_SDK_RELEASE_REPO` (Variable, optional) | Separate **Private** GitHub repo (e.g. `yudikarma/avaya-sdk-private`) hosting the `sdk-4.0.10` release asset if keeping this repo public. |
| `AVAYA_SDK_REPO_TOKEN` (Secret, optional) | Fine-grained PAT (`Contents: Read`) required only when using a separate private `AVAYA_SDK_RELEASE_REPO`. |
| `AVAYA_SDK_AAR_URL` (Secret, optional) | Alternative private HTTPS URL to `oceanacustomerwebvoicevideo.aar` (S3, GCS, Artifactory, Nexus). |
| `AVAYA_SDK_AAR_AUTH_HEADER` (Secret, optional) | Authorization header passed to `curl` when downloading `AVAYA_SDK_AAR_URL` (e.g. `Authorization: Bearer <token>`). |
| `AVAYA_SDK_AAR_SHA256` (Secret, optional) | Overrides the built-in SHA-256 checksum for `oceanacustomerwebvoicevideo.aar` (Release 4.0.10). |
| `ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, `ANDROID_KEY_PASSWORD` (Secrets, optional) | Base64-encoded release keystore and credentials for signing `assembleRelease` in CI. |
