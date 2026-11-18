package com.phincall.webrtc

import android.app.Application
import com.phincall.webrtc.auth.TokenRepository
import com.phincall.webrtc.config.RuntimeConfigManager
import com.phincall.webrtc.config.SettingsStore
import com.phincall.webrtc.logging.DiagnosticLogManager
import com.phincall.webrtc.webrtc.InteractionCoordinator

class PhinCallApplication : Application() {

    lateinit var settingsStore: SettingsStore
        private set

    lateinit var diagnosticLogManager: DiagnosticLogManager
        private set

    lateinit var runtimeConfigManager: RuntimeConfigManager
        private set

    lateinit var tokenRepository: TokenRepository
        private set

    lateinit var interactionCoordinator: InteractionCoordinator
        private set

    override fun onCreate() {
        super.onCreate()
        settingsStore = SettingsStore(this)
        diagnosticLogManager = DiagnosticLogManager(this)
        runtimeConfigManager = RuntimeConfigManager(settingsStore, diagnosticLogManager)
        tokenRepository = TokenRepository()
        interactionCoordinator = InteractionCoordinator(this, diagnosticLogManager)
    }
}
