package com.karadaglabs.cartv

import android.content.Intent
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
import com.google.android.apps.auto.sdk.MenuAdapter

object HostedNavigation {

    private const val TAG = "KaradagHostedNav"

    @OptIn(UnstableApi::class)
    fun open(
        from: CarActivity,
        destination: ProjectionDestination
    ) {
        Log.d(
            TAG,
            "CAR HOST NAVIGATION start destination=${destination.name} " +
                "from=${from.javaClass.simpleName}"
        )

        try {
            CarVideoPlayerHolder.setPlayWhenReady(false)
        } catch (_: Exception) {
        }

        if (from is CarVideoActivity) {
            from.showDestination(destination, launchedByHost = false)

            if (
                destination == ProjectionDestination.LIVE_TV ||
                destination == ProjectionDestination.RADIO
            ) {
                return
            }
        }

        val target = className(destination)
        val intent = Intent()
        intent.setClassName(from.packageName, target)
        intent.putExtra(
            ProjectionDestination.EXTRA,
            destination.name
        )

        val projection = HostedCarWindow.startProjectionActivity(from, intent)
        Log.d(
            TAG,
            "startCarProjectionActivity destination=${destination.name} " +
                "target=$target " +
                "attempted=${projection.attempted} " +
                "succeeded=${projection.succeeded} " +
                "reason=${projection.reason}"
        )

        if (from is CarVideoActivity) {
            Log.d(
                TAG,
                "startCarActivity skipped; CarVideoActivity already hosts " +
                    destination.name
            )
            return
        }

        try {
            from.startCarActivity(intent)
            Log.d(
                TAG,
                "startCarActivity dispatched destination=${destination.name} " +
                    "component=${intent.component}"
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
            ProjectionDestination.RADIO ->
                CarVideoActivity::class.java.name

            ProjectionDestination.YOUTUBE ->
                CarYouTubeActivity::class.java.name

            ProjectionDestination.GOOGLE ->
                CarGoogleActivity::class.java.name
        }
    }

    fun hideChrome(activity: CarActivity) {
        try {
            val carUiController = activity.getCarUiController()
            carUiController.statusBarController.hideAppHeader()
            carUiController.menuController.hideMenuButton()
            carUiController.drawerController.closeDrawer()
            Log.d(TAG, "car chrome hidden and drawer closed")
        } catch (exception: Exception) {
            Log.e(TAG, "Unable to hide car chrome", exception)
        }
    }

    fun bindList(
        activity: CarActivity,
        adapter: MenuAdapter
    ) {
        try {
            val controller = activity.getCarUiController().menuController
            controller.setRootMenuAdapter(adapter)
            controller.hideMenuButton()
            activity.getCarUiController().drawerController.closeDrawer()
            Log.d(
                TAG,
                "root MenuAdapter bound count=${adapter.menuItemCount} " +
                    "title=${adapter.title} drawer=closed menuButton=hidden"
            )
        } catch (exception: Exception) {
            Log.e(TAG, "Unable to bind car MenuAdapter", exception)
        }
    }

    fun closeDrawer(activity: CarActivity) {
        try {
            activity.getCarUiController().drawerController.closeDrawer()
        } catch (exception: Exception) {
            Log.w(TAG, "Unable to close car drawer", exception)
        }
    }

    fun openDrawer(activity: CarActivity) {
        closeDrawer(activity)
        Log.d(
            TAG,
            "openDrawer suppressed; Gearhead PagedListView empty state " +
                "is the car-screen 'Öğe yok' placeholder"
        )
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
