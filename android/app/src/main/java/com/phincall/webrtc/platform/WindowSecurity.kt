package com.phincall.webrtc.platform

import android.view.Window
import android.view.WindowManager

internal fun shouldApplySecureWindowFlag(allowScreenCapture: Boolean): Boolean = !allowScreenCapture

/**
 * Applies or clears WindowManager.LayoutParams.FLAG_SECURE so runtime capture policy
 * updates screenshots, screen recording, and Recents previews cleanly without Activity restarts.
 */
internal fun Window.applyCapturePolicy(allowScreenCapture: Boolean) {
    if (shouldApplySecureWindowFlag(allowScreenCapture)) {
        addFlags(WindowManager.LayoutParams.FLAG_SECURE)
    } else {
        clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }
}
