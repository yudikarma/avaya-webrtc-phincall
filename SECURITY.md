# Security Audit and Hardening Summary

Audit scope: `android/` (self-contained Kotlin Android Studio SDK 36 client), `token-proxy/` (Node.js intermediary AAWG token service), and `.github/workflows/ci-cd.yml` (CI/CD pipeline).

## Findings and Remediations

| Severity | Component | Finding | Remediation Applied |
|---|---|---|---|
| High | `token-proxy/server.js` | Non-constant-time API key comparison (`!==`) vulnerable to timing side channels. | Replaced with `crypto.timingSafeEqual` on SHA-256 digests (`verifyClientApiKey`). |
| High | `token-proxy/server.js` | Fail-open authentication when `PROXY_API_KEY` was unset in `aawg` mode. | Fail-closed startup validation requires `PROXY_API_KEY` (>= 32 chars), `https://` `AAWG_BASE_URL`, and readable mTLS PEM files in `aawg` mode. |
| High | `token-proxy/server.js` | Wildcard CORS (`*`), default `0.0.0.0` bind, and static web file serving increased attack surface. | Removed static file serving and wildcard CORS; default bind tightened to `127.0.0.1`; added native TLS 1.2+ listener option (`PROXY_TLS_*`). |
| High | `token-proxy/server.js` | Missing rate limiting allowed token flooding / DoS against AAWG. | Added bounded per-IP sliding-window rate limiter returning `429 Too Many Requests` with `Retry-After`. |
| High | `android/app` network config | User CA trust (`<certificates src="user"/>`) and loopback cleartext applied to release builds. | Split `android/app/src/main/res/xml/network_security_config.xml` (system CAs only, `cleartextTrafficPermitted="false"`) from `android/app/src/debug/res/xml/network_security_config.xml`. Added `android:usesCleartextTraffic="false"` in `AndroidManifest.xml`. |
| High | `android/app/build.gradle.kts` | Legacy `io.netty:netty-all:4.1.17.Final` carried known HTTP/TLS CVEs. | Upgraded to patched `io.netty:netty-all:4.1.119.Final` and `org.slf4j:slf4j-api:2.0.16`. |
| High | `TokenRepository.kt` | Raw string URL concatenation risked authority/path injection; redirects and cache not explicitly disabled; HTTP allowed on non-loopback hosts. | Built URLs strictly via `HttpUrl.Builder`; enforced HTTPS in release and loopback-only HTTP in debug; enforced `ConnectionSpec.MODERN_TLS`, `followRedirects(false)`, `followSslRedirects(false)`, and `cache(null)`. |
| Medium | `token-proxy/server.js` | Unbounded `expiration` and unvalidated `calledNumber`/`callingNumber`/`displayName` risked SIP header/URI injection. | Bounded `expiration` (`5000..300000` ms), enforced SIP user allowlist regex and control-char rejection, enforced strict `Content-Type`, and added security headers (`Cache-Control: no-store`, `X-Content-Type-Options: nosniff`, `X-Frame-Options: DENY`, HSTS on TLS) plus Slowloris timeouts. |
| Medium | `token-proxy/server.js` | `/health` exposed internal `aawgTarget` URL and `502` returned raw upstream error messages. | Removed internal target disclosure from `/health` and returned generic client errors while logging sanitized error codes server-side only. |
| Medium | `SettingsActivity.kt`, `InCallActivity.kt`, `DtmfKeypadDialog.kt` | Active video frames, entered IVR DTMF digits, and proxy API key input were exposed to screenshots and Recents thumbnails. | Applied `WindowManager.LayoutParams.FLAG_SECURE` to `SettingsActivity`, `InCallActivity`, `DtmfKeypadDialog`, and `CallStatsDialog`; masked on-screen DTMF echo with bullets. |
| Medium | `RuntimeConfigManager.kt`, `SettingsStore.kt`, `InteractionCoordinator.kt` | Missing strict input validation; launch config retained in memory after call end; verbose SDK logging enabled in release builds. | Added `InputValidator` and platform-aware `RuntimeConfigManager.validateForLaunch`; kept proxy API keys in memory only; cleared `launchConfig`, secrets, and tokens on teardown; gated SDK/Logcat logging by `BuildConfig.DEBUG`; excluded backups via `backup_rules.xml` and `data_extraction_rules.xml`; scoped `FileProvider` strictly to `filesDir/logs/` with secret redaction. |
| Low | `android/app/proguard-rules.pro` | Debug/info `android.util.Log` calls remained in release bytecode. | Added `-assumenosideeffects` rules stripping `Log.v`, `Log.d`, and `Log.i` in release builds. |

## Third-Party Vendor Binary Note

`oceanacustomerwebvoicevideo.aar` (Avaya Release 4.0.10) embeds `libs/jackson-databind-2.9.4.jar` and `libs/eventbus-3.1.1.jar` inside the vendor archive. Because these JARs are packaged by Avaya inside the binary `.aar`, application code must never pass untrusted external JSON directly to Jackson's `ObjectMapper` (this client uses Android `org.json` and `InputValidator` exclusively for app-level parsing). Track Avaya DevConnect security advisories for future `.aar` updates.
