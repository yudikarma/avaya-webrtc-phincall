# PhinCall Visual Direction

Design Read: Enterprise telephony console and Android WebRTC reference client for contact center engineers and integrators, styled as a crisp operational light workspace paired with a dark video stage, deep crimson accent, and slate neutrals.

Dial: ENERGY 2 / RHYTHM 2 / MOTION 1

## Brand Identity and Assets (R-23)

- Product name: PhinCall (Avaya WebRTC Connect 4.0.10 Android Reference Client)
- Brand mark: Typographic wordmark paired with a geometric monochrome signal-wave icon placeholder. No invented logos, fake team avatars, or unverified customer claims.

## Color System (R-01, R-25, R-29)

Active palette uses two core institutional colors, one deliberate accent, and slate neutrals. Every text and interactive border pairing was verified against WCAG AA (minimum 4.5:1 normal text, 3.0:1 non-text UI boundaries).

| Role | Hex | Contrast Verified | Purpose (R-31) |
|---|---|---|---|
| Light canvas | `#F6F7F9` | Base surface | Reduces glare during configuration work on mobile and tablet. |
| Elevated surface | `#FFFFFF` | 17.83:1 with `#111822` | Separates configuration groups from the ground without heavy shadows. |
| Primary ink (light) | `#111822` | 16.64:1 on `#F6F7F9` | High-legibility labels and form values. |
| Secondary ink (light) | `#475569` | 7.07:1 on `#F6F7F9` | Supporting hints and SDK mapping notes. |
| Interactive border | `#7A8594` | 3.74:1 on `#FFFFFF` | Meets WCAG 1.4.11 non-text contrast for form inputs and secondary buttons. |
| Structural hairline | `#D8DEE6` | Divider only | Quiet visual separation between grouped settings. |
| Core 1: Slate Navy | `#1B2A41` | 14.44:1 with `#FFFFFF` | App chrome, active platform segmented control, and structural headers. |
| Accent: Deep Crimson | `#C8102E` | 5.88:1 with `#FFFFFF` | Reserved strictly for the primary Live Help call trigger, hang-up button, and active mute alert. |
| Accent Hover | `#A50D26` | 7.82:1 with `#FFFFFF` | Distinct pressed state for primary call actions. |
| Dark video stage | `#0F141B` | 17.67:1 with `#F8FAFC` | Frames live camera and remote video feeds without bright-border distraction. |
| Dark control dock | `#1A222D` | 8.88:1 with `#B6C2D2` | Anchors in-call controls (mute, video, hold, DTMF, audio route, hang-up) below media. |
| Status Good | `#157347` (light) / `#4ADE80` (dark) | 5.87:1 / 9.20:1 | Paired with text and signal bars for Excellent/Good CallQuality. |
| Status Fair | `#8A5000` (light) / `#FBBF24` (dark) | 6.51:1 / 9.60:1 | Paired with text and signal bars for Fair CallQuality and hold/reconnecting state. |
| Status Poor / Error | `#B42318` (light) / `#F87171` (dark) | 6.57:1 / 5.79:1 | Paired with text and icons for Poor/Bad quality and interaction failures. |

## Typography, Radius, Elevation, and Motion Decisions (R-06, R-11, R-12, R-19, R-31)

- Typography: Native Android Roboto sans for immediate operational clarity; monospace tabular digits on call duration timers and live media statistics so digits never jitter while ticking.
- Border radius hierarchy: `12dp` for outer panels, `8dp` for buttons and text inputs, `4dp` for status tags, circular (`oval`) strictly on the hang-up and DTMF digit buttons.
- Elevation: Flat 1dp bordered panels on light surfaces; elevation only on floating Keypad action button, bottom apply bar, and overlay sheets/dialogs (Dialpad bottom sheet, DTMF keypad, Call Statistics).
- Safe areas (Android 15/16 SDK 36): `enableEdgeToEdge()` in `MainActivity` paired with `WindowInsets.safeDrawing` top/horizontal padding on Slate Navy / Dock Dark top bars and `navigationBarsPadding()` / `imePadding()` on bottom docks, Settings Apply bar, and the floating Keypad FAB across `HomeScreen`, `SettingsScreen`, and `InCallScreen`.
- Motion (MOTION 1): Crisp state transitions only; zero decorative pulses or loops.
