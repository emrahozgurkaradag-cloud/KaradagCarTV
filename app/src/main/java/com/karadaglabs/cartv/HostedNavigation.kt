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
import androidx.media3.common.util.UnstableApi
import com.google.android.apps.auto.sdk.CarActivity
import java.util.Collections
import java.util.WeakHashMap

object HostedNavigation {

    private const val TAG = "KaradagHostedNav"

    @OptIn(UnstableApi::class)
    fun open(
        from: CarActivity,
        destination: ProjectionDestination
    ) {
        try {
            CarVideoPlayerHolder.setPlayWhenReady(false)

            if (from is CarDestinationHost) {
                from.showHostedDestination(destination)
                return
            }

            Log.e(
                TAG,
                "CAR HOST NAVIGATION FAILED destination=${destination.name} " +
                    "reason=single_car_activity_only"
            )
        } catch (exception: Exception) {
            Log.e(
                TAG,
                "CAR HOST NAVIGATION FAILED destination=${destination.name}",
                exception
            )
        }
    }

    fun className(destination: ProjectionDestination): String {
        return when (destination) {
            ProjectionDestination.LIVE_TV,
            ProjectionDestination.RADIO,
            ProjectionDestination.YOUTUBE,
            ProjectionDestination.GOOGLE ->
                CarVideoActivity::class.java.name
        }
    }

    fun hideChrome(activity: CarActivity) {
        try {
            val carUiController = activity.getCarUiController()
            carUiController.statusBarController.hideAppHeader()
            carUiController.menuController.hideMenuButton()
        } catch (exception: Exception) {
            Log.e(TAG, "Unable to hide car chrome", exception)
        }
    }

    private val boundMenus: MutableSet<CarActivity> =
        Collections.newSetFromMap(WeakHashMap())

    fun bindDrawerMenu(
        activity: CarActivity,
        current: ProjectionDestination,
        onSelect: (ProjectionDestination) -> Unit,
        openDrawerOnce: Boolean = false
    ) {
        try {
            val ui = activity.carUiController
            // Gearhead onStart wipes an onCreate adapter. Set it on
            // every bind so Canlı TV / YouTube / Google stay in the
            // official drawer after chrome restore.
            ui.menuController.setRootMenuAdapter(
                DestinationMenuAdapter(activity, onSelect)
            )
            ui.menuController.showMenuButton()
            if (openDrawerOnce && boundMenus.add(activity)) {
                try {
                    ui.drawerController.openDrawer()
                } catch (exception: Exception) {
                    Log.w(TAG, "openDrawer unavailable", exception)
                }
                Log.d(TAG, "HOST_MENU_BOUND destination=${current.name}")
            }
            // Do not showTitle(): that restores Gearhead chrome and
            // the empty PagedListView over slot g. CarStream keeps
            // the header hidden and only setTitle on the hidden bar.
            ui.statusBarController.setTitle(current.label)
        } catch (exception: Exception) {
            Log.e(TAG, "HOST_MENU_BIND failed", exception)
        }
    }

    fun showHostContent(activity: CarActivity) {
        try {
            activity.carUiController.drawerController.closeDrawer()
        } catch (exception: Exception) {
            Log.w(TAG, "closeDrawer unavailable", exception)
        }
    }

    private class DestinationMenuAdapter(
        private val activity: CarActivity,
        private val onSelect: (ProjectionDestination) -> Unit
    ) : com.google.android.apps.auto.sdk.MenuAdapter() {

        private val items = ProjectionDestination.entries

        override fun getMenuItemCount(): Int = items.size

        override fun getMenuItem(index: Int): com.google.android.apps.auto.sdk.MenuItem {
            return com.google.android.apps.auto.sdk.MenuItem.Builder()
                .setTitle(items[index].label)
                .setType(com.google.android.apps.auto.sdk.MenuItem.Type.ITEM)
                .build()
        }

        override fun onMenuItemClicked(index: Int) {
            if (index in items.indices) {
                onSelect(items[index])
                showHostContent(activity)
            }
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

        ProjectionDestination.entries.forEach { destination ->
            val selected = destination == current
            val button = Button(activity).apply {
                text = destination.label
                isSelected = selected
                isFocusable = true
                isFocusableInTouchMode = true
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
