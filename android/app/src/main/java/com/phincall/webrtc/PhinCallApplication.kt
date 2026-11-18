package com.phincall.webrtc

import android.app.Application
import com.phincall.webrtc.auth.TokenRepository
import com.phincall.webrtc.webrtc.InteractionCoordinator

class PhinCallApplication : Application() {

    lateinit var tokenRepository: TokenRepository
        private set

    lateinit var interactionCoordinator: InteractionCoordinator
        private set

    override fun onCreate() {
        super.onCreate()
        tokenRepository = TokenRepository()
        interactionCoordinator = InteractionCoordinator(this)
    }
}
