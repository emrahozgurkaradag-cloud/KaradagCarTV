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

interface HostedDestinationHost {
    fun showHostedDestination(destination: ProjectionDestination)
}

object HostedNavigation {

    private const val TAG = "KaradagHostedNav"

    @OptIn(UnstableApi::class)
    fun open(
        from: CarActivity,
        destination: ProjectionDestination
    ) {
        try {
            CarVideoPlayerHolder.setPlayWhenReady(false)

            if (from is CarVideoActivity) {
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
        destination.name
        return CarVideoActivity::class.java.name
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
