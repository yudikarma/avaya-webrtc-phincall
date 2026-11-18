# PhinCall iOS (Avaya WebRTC Connect SDK 4.0.10)

Swift and Objective-C bridged reference client supporting Avaya Oceana and Avaya Aura Elite voice and video interactions.

## XCFramework Integration Table (Guide 4.0.10, pp. 46-47)

Download the iOS Avaya WebRTC Connect SDK 4.0.10 bundle from Avaya DevConnect and copy the four universal XCFrameworks into `ios/Frameworks/`:

| Framework | Embed Setting in Xcode | Purpose |
|---|---|---|
| `OceanaCustomerWebVoiceVideo.xcframework` | **Do Not Embed** | Static Avaya WebRTC Connect wrapper SDK |
| `AvayaClientServicesLite.xcframework` | **Embed & Sign** | Avaya Client SDK signaling and session core |
| `AvayaClientMedia.xcframework` | **Embed & Sign** | WebRTC audio/video media engine |
| `RVVideoCodec.xcframework` | **Embed & Sign** | Video codec binary |
| `Foundation.framework` | Do Not Embed | iOS system framework |
| `MessageUI.framework` | Do Not Embed | iOS system framework |
| `AVFoundation.framework` / `AVKit.framework` | Do Not Embed | Audio session & `AVRoutePickerView` routing |

## Generating and Building on macOS (Xcode 11+ / Xcode 16+)

```bash
cd ios
xcodegen generate
open PhinCalliOS.xcodeproj
```

Verify in Xcode Build Settings and Capabilities:
- **Enable Bitcode**: `NO`
- **Architectures**: 64-bit (`arm64`, `arm64e` on device; `arm64` / `x86_64` on simulator)
- **Signing & Capabilities > Background Modes**: check **Audio, AirPlay, and Picture in Picture** and **Voice over IP** (already declared in `Info.plist`)
- **Testing**: Use a physical iPhone/iPad for real microphone and camera media validation, and verify IPv6/NAT64 DNS resolution using hostnames rather than IPv4 literals (Guide 4.0.10, p. 44).
