# PhinCall Visual Direction

Design Read: Enterprise telephony console and multi-platform WebRTC reference client for contact center engineers and integrators, styled as a crisp operational light workspace paired with a dark video stage, deep crimson accent, and slate neutrals.

Dial: ENERGY 2 / RHYTHM 2 / MOTION 1

## Brand Identity and Assets (R-23)

- Product name: PhinCall (Avaya WebRTC Connect 4.0.10 Reference Showcase)
- Brand mark: Typographic wordmark paired with a geometric monochrome signal-wave icon placeholder. No invented logos, fake team avatars, or unverified customer claims.

## Color System (R-01, R-25, R-29)

Active palette uses two core institutional colors, one deliberate accent, and slate neutrals. Every text and interactive border pairing was verified against WCAG AA via `contrast-check` (minimum 4.5:1 normal text, 3.0:1 non-text UI boundaries).

| Role | Hex | Contrast Verified | Purpose (R-31) |
|---|---|---|---|
| Light canvas | `#F6F7F9` | Base surface | Reduces glare during configuration work on desktop and mobile. |
| Elevated surface | `#FFFFFF` | 17.83:1 with `#111822` | Separates configuration groups from the page ground without heavy shadows. |
| Primary ink (light) | `#111822` | 16.64:1 on `#F6F7F9` | High-legibility labels and form values. |
| Secondary ink (light) | `#475569` | 7.07:1 on `#F6F7F9` | Supporting hints and SDK mapping notes. |
| Interactive border | `#7A8594` | 3.74:1 on `#FFFFFF` | Meets WCAG 1.4.11 non-text contrast for form inputs and secondary buttons. |
| Structural hairline | `#D8DEE6` | Divider only | Quiet visual separation between grouped settings. |
| Core 1: Slate Navy | `#1B2A41` | 14.44:1 with `#FFFFFF` | App chrome, active platform segmented control, and structural headers. |
| Accent: Deep Crimson | `#C8102E` | 5.88:1 with `#FFFFFF` | Reserved strictly for the primary Live Help call trigger, hang-up button, and active mute alert. |
| Accent Hover | `#A50D26` | 7.82:1 with `#FFFFFF` | Distinct hover/pressed state for primary call actions. |
| Dark video stage | `#0F141B` | 17.67:1 with `#F8FAFC` | Frames live camera and remote video feeds without bright-border distraction. |
| Dark control dock | `#1A222D` | 8.88:1 with `#B6C2D2` | Anchors in-call controls (mute, video, hold, DTMF, hang-up) below or beside media. |
| Status Good | `#157347` (light) / `#4ADE80` (dark) | 5.87:1 / 9.20:1 | Paired with text and signal bars for Excellent/Good CallQuality and connected state. |
| Status Fair | `#8A5000` (light) / `#FBBF24` (dark) | 6.51:1 / 9.60:1 | Paired with text and signal bars for Fair CallQuality and hold/reconnecting state. |
| Status Poor / Error | `#B42318` (light) / `#F87171` (dark) | 6.57:1 / 5.79:1 | Paired with text and icons for Poor/Bad quality and interaction failures. |

## Typography, Radius, Elevation, and Motion Decisions (R-06, R-11, R-12, R-19, R-31)

- Typography: System UI sans (`IBM Plex Sans` / native platform sans: Roboto on Android, SF Pro on iOS, system-ui fallback on Web) for immediate operational clarity; tabular figures (`font-variant-numeric: tabular-nums`) on call duration timers and ports so digits never jitter while ticking.
- Border radius hierarchy: `12px` for outer panels and video stage frames, `8px` for buttons and text inputs, `4px` for compact status tags, full circle (`50%`) strictly on the in-call circular hang-up and DTMF digit buttons. Never uniform pills across every element.
- Elevation: Flat 1px bordered panels on light surfaces; subtle elevation (`0 4px 16px rgba(17, 24, 34, 0.08)`) only on floating overlays (DTMF dialog and PiP local video tile) to signal foreground layers.
- Motion (MOTION 1): Crisp `150ms` state transitions on hover, focus, active press, and view switching only. Zero decorative loops, bouncing cards, or pulsing indicators. Respects `prefers-reduced-motion: reduce`.
- Layout rhythm (RHYTHM 2): Two-column desktop console (configuration form left, live routing summary and 4-step lifecycle telemetry right) reflowing cleanly to a balanced tablet two-band layout and a single-column mobile stack with 44px+ touch targets.
