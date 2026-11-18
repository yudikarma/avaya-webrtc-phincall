package com.phincall.webrtc.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.phincall.webrtc.ui.home.HomeRoute
import com.phincall.webrtc.ui.home.HomeViewModel
import com.phincall.webrtc.ui.incall.InCallRoute
import com.phincall.webrtc.ui.incall.InCallViewModel
import com.phincall.webrtc.ui.settings.SettingsRoute
import com.phincall.webrtc.ui.settings.SettingsViewModel

@Composable
fun PhinCallNavHost(
    onUpdateWindowFlags: (secureScreen: Boolean, keepScreenOn: Boolean, darkNavBar: Boolean) -> Unit
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val destination = backStackEntry?.destination

    LaunchedEffect(destination) {
        val isSettings = destination?.hasRoute<PhinCallRoute.Settings>() == true
        val isInCall = destination?.hasRoute<PhinCallRoute.InCall>() == true
        onUpdateWindowFlags(
            isSettings || isInCall,
            isInCall,
            isInCall
        )
    }

    NavHost(
        navController = navController,
        startDestination = PhinCallRoute.Home
    ) {
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
                    navController.navigate(PhinCallRoute.InCall) {
                        launchSingleTop = true
                    }
                }
            )
        }

        composable<PhinCallRoute.Settings> {
            val viewModel: SettingsViewModel = hiltViewModel()
            SettingsRoute(
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable<PhinCallRoute.InCall> {
            val viewModel: InCallViewModel = hiltViewModel()
            InCallRoute(
                viewModel = viewModel,
                onCallTerminated = {
                    navController.popBackStack(PhinCallRoute.Home, inclusive = false)
                }
            )
        }
    }
}
