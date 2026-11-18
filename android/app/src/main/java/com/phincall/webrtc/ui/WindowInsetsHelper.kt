package com.phincall.webrtc.ui

import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/**
 * Configures Android 15/16 edge-to-edge safe areas so top app bars clear the
 * notification bar / display cutout and bottom controls clear gesture bars.
 */
object WindowInsetsHelper {

    fun enableSafeEdgeToEdge(
        activity: AppCompatActivity,
        darkStatusBarIcons: Boolean = false,
        darkNavigationBarIcons: Boolean = true
    ) {
        WindowCompat.setDecorFitsSystemWindows(activity.window, false)
        val controller = WindowInsetsControllerCompat(activity.window, activity.window.decorView)
        controller.isAppearanceLightStatusBars = darkStatusBarIcons
        controller.isAppearanceLightNavigationBars = darkNavigationBarIcons
    }

    fun applyTopSafeArea(view: View) {
        val initialTop = view.paddingTop
        val initialLeft = view.paddingLeft
        val initialRight = view.paddingRight
        val initialBottom = view.paddingBottom

        ViewCompat.setOnApplyWindowInsetsListener(view) { target, windowInsets ->
            val bars = windowInsets.getInsets(
                WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.displayCutout()
            )
            target.setPadding(
                initialLeft + bars.left,
                initialTop + bars.top,
                initialRight + bars.right,
                initialBottom
            )
            windowInsets
        }
        ViewCompat.requestApplyInsets(view)
    }

    fun applyBottomSafeArea(view: View, includeIme: Boolean = false) {
        val initialTop = view.paddingTop
        val initialLeft = view.paddingLeft
        val initialRight = view.paddingRight
        val initialBottom = view.paddingBottom
        val insetType = if (includeIme) {
            WindowInsetsCompat.Type.navigationBars() or WindowInsetsCompat.Type.ime()
        } else {
            WindowInsetsCompat.Type.navigationBars()
        }

        ViewCompat.setOnApplyWindowInsetsListener(view) { target, windowInsets ->
            val bars = windowInsets.getInsets(insetType)
            target.setPadding(
                initialLeft + bars.left,
                initialTop,
                initialRight + bars.right,
                initialBottom + bars.bottom
            )
            windowInsets
        }
        ViewCompat.requestApplyInsets(view)
    }

    fun applyBottomMarginSafeArea(view: View) {
        val lp = view.layoutParams as? ViewGroup.MarginLayoutParams ?: return
        val initialBottom = lp.bottomMargin
        val initialRight = lp.rightMargin

        ViewCompat.setOnApplyWindowInsetsListener(view) { target, windowInsets ->
            val nav = windowInsets.getInsets(WindowInsetsCompat.Type.navigationBars())
            val params = target.layoutParams as? ViewGroup.MarginLayoutParams
            if (params != null) {
                params.bottomMargin = initialBottom + nav.bottom
                params.rightMargin = initialRight + nav.right
                target.layoutParams = params
            }
            windowInsets
        }
        ViewCompat.requestApplyInsets(view)
    }
}
