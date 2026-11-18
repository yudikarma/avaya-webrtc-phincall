# Avaya WebRTC Connect JavaScript SDK Vendor Files

Place the official Avaya WebRTC Connect 4.0.10 JavaScript SDK files downloaded from Avaya DevConnect into this directory:

1. `jquery-3.7.1.min.js` (minimum required version: jQuery 3.3.1 per Guide 4.0.10, p. 54)
2. `OceanaCustomerWebVoiceVideo.js`

Then uncomment the two `<script defer>` tags in `web/index.html`.

When `OceanaCustomerWebVoiceVideo.js` is not present locally, the web client detects its absence and lets you opt into the clearly labeled **Local SDK Simulator** to exercise the token proxy, UI state safety, call lifecycle, quality banner, hold/mute toggles, and DTMF keypad without a live Avaya Aura Web Gateway deployment.
