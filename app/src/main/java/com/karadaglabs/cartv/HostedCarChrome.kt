package com.karadaglabs.cartv

import android.util.Log
import android.view.View
import android.view.ViewGroup
import com.google.android.apps.auto.sdk.CarActivity
import com.google.android.apps.auto.sdk.DayNightStyle
import com.google.android.apps.auto.sdk.MenuAdapter

/**
 * Uses the aauto.aar host chrome the way Gearhead expects:
 * MenuAdapter fills the native list, drawer shows that list,
 * setContentView hosts SurfaceView / WebView when the drawer is closed.
 *
 * Does not hide or replace the host native list.
 */
object HostedCarChrome {

    private const val TAG = "KaradagHostedChrome"

    fun install(
        activity: CarActivity,
        adapter: MenuAdapter,
        title: String
    ) {
        try {
            val ui = activity.carUiController
            val status = ui.statusBarController
            status.showAppHeader()
            status.showTitle()
            status.setTitle(title)
            status.setDayNightStyle(DayNightStyle.FORCE_NIGHT)

            val menu = ui.menuController
            menu.showMenuButton()
            menu.setRootMenuAdapter(adapter)

            Log.d(TAG, "CAR HOST MENU BOUND title=$title")
        } catch (exception: Exception) {
            Log.e(TAG, "Unable to bind car menu adapter", exception)
        }
    }

    fun showList(activity: CarActivity, title: String) {
        try {
            val ui = activity.carUiController
            ui.statusBarController.setTitle(title)
            ui.menuController.showMenuButton()
            ui.drawerController.openDrawer()
            Log.d(TAG, "CAR HOST DRAWER OPEN title=$title")
        } catch (exception: Exception) {
            Log.e(TAG, "Unable to open car drawer", exception)
        }
    }

    fun showContent(activity: CarActivity, title: String) {
        try {
            val ui = activity.carUiController
            ui.statusBarController.setTitle(title)
            ui.menuController.showMenuButton()
            ui.drawerController.closeDrawer()
            Log.d(TAG, "CAR HOST DRAWER CLOSE title=$title")
        } catch (exception: Exception) {
            Log.e(TAG, "Unable to close car drawer", exception)
        }
    }

    fun fillHostContent(view: View) {
        val match = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        view.layoutParams = match

        view.post {
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
}
