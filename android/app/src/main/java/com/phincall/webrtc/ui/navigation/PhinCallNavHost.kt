package com.phincall.webrtc.ui.navigation

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.phincall.webrtc.ui.home.HomeRoute
import com.phincall.webrtc.ui.home.HomeViewModel
import com.phincall.webrtc.ui.incall.InCallActivity
import com.phincall.webrtc.ui.settings.SettingsRoute
import com.phincall.webrtc.ui.settings.SettingsViewModel
import com.phincall.webrtc.ui.splash.SplashRoute

@Composable
fun PhinCallNavHost(
    onUpdateWindowFlags: (
        secureScreen: Boolean,
        keepScreenOn: Boolean,
        darkStatusBar: Boolean,
        darkNavBar: Boolean
    ) -> Unit
) {
    val context = LocalContext.current
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val destination = backStackEntry?.destination
    val activeCallBannerViewModel: ActiveCallBannerViewModel = hiltViewModel()
    val callSnapshot by activeCallBannerViewModel.callState.collectAsStateWithLifecycle()
    val isSplash = destination?.hasRoute<PhinCallRoute.Splash>() == true
    val isSettings = destination?.hasRoute<PhinCallRoute.Settings>() == true
    val showStickyCallBanner = shouldShowActiveCallStickyBanner(callSnapshot, isSplash)

    LaunchedEffect(isSplash, isSettings, showStickyCallBanner) {
        onUpdateWindowFlags(
            isSettings,
            false,
            !isSplash || showStickyCallBanner,
            false
        )
    }

    val returnToActiveCall = {
        context.startActivity(
            Intent(context, InCallActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = showStickyCallBanner,
            enter = expandVertically(expandFrom = Alignment.Top) + fadeIn(tween(180)),
            exit = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut(tween(160))
        ) {
            ActiveCallStickyBanner(
                snapshot = callSnapshot,
                destination = activeCallBannerViewModel.activeDestination,
                onReturnToCall = returnToActiveCall,
                onToggleAudioMute = activeCallBannerViewModel::toggleAudioMute
            )
        }

        NavHost(
            navController = navController,
            startDestination = PhinCallRoute.Splash,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            enterTransition = { fadeIn(animationSpec = tween(240)) },
            exitTransition = { fadeOut(animationSpec = tween(220)) }
        ) {
            composable<PhinCallRoute.Splash> {
                SplashRoute(
                    onSplashComplete = {
                        navController.navigate(PhinCallRoute.Home) {
                            popUpTo<PhinCallRoute.Splash> {
                                inclusive = true
                            }
                            launchSingleTop = true
                        }
                    }
                )
            }

            composable<PhinCallRoute.Home> {
                val viewModel: HomeViewModel = hiltViewModel()
                HomeRoute(
                    viewModel = viewModel,
                    onOpenSettings = {
                        navController.navigate(PhinCallRoute.Settings) {
                            launchSingleTop = true
                        }
                    },
                    onNavigateToInCall = {
                        context.startActivity(
                            Intent(context, InCallActivity::class.java).apply {
                                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                            }
                        )
                    },
                    onReturnToActiveCall = returnToActiveCall,
                    includeTopSafeArea = !showStickyCallBanner
                )
            }

            composable<PhinCallRoute.Settings> {
                val viewModel: SettingsViewModel = hiltViewModel()
                SettingsRoute(
                    viewModel = viewModel,
                    onNavigateBack = { navController.popBackStack() },
                    includeTopSafeArea = !showStickyCallBanner
                )
            }
        }
    }
}
