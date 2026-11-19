package com.phincall.webrtc.ui.navigation

import kotlinx.serialization.Serializable

/**
 * Type-safe Compose Navigation routes (Navigation Compose 2.9 + Kotlin Serialization).
 */
@Serializable
sealed interface PhinCallRoute {
    @Serializable
    data object Splash : PhinCallRoute

    @Serializable
    data object Home : PhinCallRoute

    @Serializable
    data object Settings : PhinCallRoute
}
