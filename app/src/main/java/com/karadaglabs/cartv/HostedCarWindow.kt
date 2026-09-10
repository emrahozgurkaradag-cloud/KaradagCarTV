package com.karadaglabs.cartv

import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.view.Window
import com.google.android.apps.auto.sdk.CarActivity

/**
 * CarStream / Fermata hosted-window contract:
 * CarActivity.c() is CarActivityHost.getWindow().
 * Putting the app root on that Window makes it the car-screen content.
 * CarActivity.setContentView only fills Gearhead's inner container.
 */
object HostedCarWindow {

    private const val TAG = "KaradagHostedWindow"

    fun install(activity: CarActivity, root: View) {
        hideHostChrome(activity)

        val match = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        root.layoutParams = match
        root.keepScreenOn = true

        val window = hostedWindow(activity)
        val content = window.decorView.findViewById<ViewGroup>(android.R.id.content)

        if (content != null && root.parent === content) {
            fillMatchParent(root)
            Log.d(TAG, "CAR HOST ROOT ALREADY ATTACHED")
            return
        }

        (root.parent as? ViewGroup)?.removeView(root)
        window.setContentView(root, match)
        fillMatchParent(root)

        Log.d(
            TAG,
            "CAR HOST ROOT ATTACHED window=${window.javaClass.name}"
        )
    }

    fun hostedWindow(activity: CarActivity): Window {
        return activity.c()
    }

    fun hideHostChrome(activity: CarActivity) {
        try {
            val ui = activity.carUiController
            ui.statusBarController.hideAppHeader()
            ui.menuController.hideMenuButton()
            ui.drawerController.closeDrawer()
        } catch (exception: Exception) {
            Log.e(TAG, "Unable to hide car chrome", exception)
        }
    }

    private fun fillMatchParent(view: View) {
        var node: View? = view
        while (node != null) {
            val params = node.layoutParams
            if (params != null) {
                params.width = ViewGroup.LayoutParams.MATCH_PARENT
                params.height = ViewGroup.LayoutParams.MATCH_PARENT
                node.layoutParams = params
            }
            node = node.parent as? View
        }
    }
}
