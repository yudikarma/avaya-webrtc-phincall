package com.phincall.webrtc

import android.graphics.Color
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.phincall.webrtc.auth.TokenRepository
import com.phincall.webrtc.config.RuntimeConfigManager
import com.phincall.webrtc.ui.navigation.PhinCallNavHost
import com.phincall.webrtc.ui.theme.PhinCallTheme
import com.phincall.webrtc.webrtc.ActiveCallForegroundService
import com.phincall.webrtc.webrtc.InteractionCoordinator
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Single-Activity Jetpack Compose host with Hilt DI, Android 15/16 edge-to-edge
 * safe-area system bars, and dynamic FLAG_SECURE / wake-lock coordination.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var tokenRepository: TokenRepository

    @Inject
    lateinit var runtimeConfigManager: RuntimeConfigManager

    @Inject
    lateinit var interactionCoordinator: InteractionCoordinator

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applySystemBarAppearance(darkNavBar = false)

        setContent {
            PhinCallTheme {
                PhinCallNavHost(
                    onUpdateWindowFlags = { secureScreen, keepScreenOn, darkNavBar ->
                        updateWindowSecurity(secureScreen, keepScreenOn)
                        applySystemBarAppearance(darkNavBar)
                    }
                )
            }
        }
    }

    private fun applySystemBarAppearance(darkNavBar: Boolean) {
        // Dark Slate Navy / Stage Dark top bar always pairs with light status bar icons.
        val statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        val navBarStyle = if (darkNavBar) {
            SystemBarStyle.dark(Color.TRANSPARENT)
        } else {
            SystemBarStyle.light(Color.TRANSPARENT, Color.argb(0x80, 0x11, 0x18, 0x22))
        }
        enableEdgeToEdge(statusBarStyle = statusBarStyle, navigationBarStyle = navBarStyle)
    }

    private fun updateWindowSecurity(secureScreen: Boolean, keepScreenOn: Boolean) {
        if (secureScreen) {
            window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
        if (keepScreenOn) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    override fun onDestroy() {
        if (isFinishing) {
            interactionCoordinator.hangUpAndDiscard()
            ActiveCallForegroundService.stop(this)
            tokenRepository.clearToken()
            runtimeConfigManager.clearInMemorySecrets()
        }
        super.onDestroy()
    }
}
