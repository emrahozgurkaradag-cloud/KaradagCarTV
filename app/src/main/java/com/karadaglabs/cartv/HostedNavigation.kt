package com.karadaglabs.cartv

import android.graphics.Color
import android.graphics.Typeface
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.apps.auto.sdk.CarActivity
import com.google.android.apps.auto.sdk.CarUiController

/**
 * Fermata MainCarActivity.initCarActivity():
 *   setIgnoreConfigChanges(0xFFFFFFFF)
 *   CarUiController.statusBarController.hideAppHeader()
 *   CarUiController.menuController.hideMenuButton()
 *
 * Hosted destinations stay inside one CarVideoActivity
 * content window. Navigation is in-activity state, not a
 * second hosted CarActivity class.
 */
object HostedNavigation {

    private const val TAG = "KaradagHostedNav"

    fun initCarActivity(activity: CarActivity) {
        activity.setIgnoreConfigChanges(0xFFFFFFFF.toInt())
        hideChrome(activity)
    }

    fun hideChrome(activity: CarActivity) {
        try {
            val carUiController: CarUiController = activity.getCarUiController()
            carUiController.statusBarController.hideAppHeader()
            carUiController.statusBarController.hideTitle()
            carUiController.menuController.hideMenuButton()
            carUiController.drawerController.closeDrawer()
        } catch (exception: Exception) {
            Log.e(TAG, "Unable to hide car chrome", exception)
        }
    }

    fun menuBar(
        activity: CarActivity,
        current: ProjectionDestination,
        onSelect: (ProjectionDestination) -> Unit
    ): LinearLayout {
        val density = activity.resources.displayMetrics.density
        val dp = { value: Int -> (value * density).toInt() }

        val column = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setBackgroundColor(Color.rgb(16, 16, 16))
            setPadding(dp(8), dp(6), dp(8), dp(6))
        }

        val brand = TextView(activity).apply {
            text = "Karadag Car TV"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, 0, 0, dp(4))
        }

        val row = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        ProjectionDestination.entries
            .filter { it != ProjectionDestination.HOME }
            .forEach { destination ->
                val selected = destination == current
                val button = Button(activity).apply {
                    text = destination.label
                    isSelected = selected
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                    setOnClickListener { onSelect(destination) }
                    val params = LinearLayout.LayoutParams(
                        0,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        1f
                    )
                    params.rightMargin = dp(4)
                    layoutParams = params
                }
                row.addView(button)
            }

        column.addView(brand)
        column.addView(row)
        return column
    }
}
