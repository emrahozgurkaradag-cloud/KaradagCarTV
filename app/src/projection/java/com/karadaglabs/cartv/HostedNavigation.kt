package com.karadaglabs.cartv

import android.content.Intent
import android.graphics.Typeface
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.media3.common.util.UnstableApi
import com.google.android.apps.auto.sdk.CarActivity

@androidx.annotation.OptIn(UnstableApi::class)
object HostedNavigation {

    private const val TAG = "KaradagHostedNav"


    fun open(
        from: CarActivity,
        destination: ProjectionDestination
    ) {
        try {
            CarVideoPlayerHolder.setPlayWhenReady(false)

            val intent = Intent()
            intent.setClassName(
                from.packageName,
                className(destination)
            )
            intent.putExtra(
                ProjectionDestination.EXTRA,
                destination.name
            )
            from.startCarActivity(intent)
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
        } catch (exception: Exception) {
            Log.e(TAG, "Unable to hide car chrome", exception)
        }
    }

    private fun iconFor(destination: ProjectionDestination): Int = when (destination) {
        ProjectionDestination.LIVE_TV -> R.drawable.ic_ok_tv
        ProjectionDestination.RADIO -> R.drawable.ic_ok_radio
        ProjectionDestination.YOUTUBE -> R.drawable.ic_ok_video
        ProjectionDestination.GOOGLE -> R.drawable.ic_ok_web
    }

    /**
     * Araç ekranı üst bölümü: ÖK + KaradagCarTV ve dört ana bölüm (Canlı TV,
     * Radyo, YouTube, Google) doğrudan, büyük dokunma hedefleriyle; alt menü
     * veya hamburger yok. Seçili bölüm turuncu.
     */
    fun menuBar(
        activity: android.content.Context,
        current: ProjectionDestination,
        onSelect: (ProjectionDestination) -> Unit
    ): LinearLayout {
        val dp = { value: Int -> CarUiKit.dp(activity, value) }
        val compact = CarUiKit.compact(activity)

        val bar = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setBackgroundColor(CarUiKit.ANTHRACITE)
            setPadding(dp(16), dp(if (compact) 6 else 10), dp(16), dp(if (compact) 6 else 10))
        }

        val brand = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            contentDescription = "Karadag Car TV"
            addView(CarUiKit.logo(activity, if (compact) 44 else 52))
            addView(
                CarUiKit.wordmark(activity, if (compact) 18f else 21f).apply {
                    setPadding(dp(10), 0, dp(16), 0)
                }
            )
        }
        bar.addView(brand)

        ProjectionDestination.entries.forEach { destination ->
            val selected = destination == current
            val fg = if (selected) CarUiKit.ANTHRACITE else CarUiKit.INK
            val tile = TextView(activity).apply {
                // Kısa etiket: 800x480 head-unit'te tek satıra sığsın.
                text = if (destination == ProjectionDestination.RADIO) "Radyo" else destination.label
                contentDescription = destination.label
                ellipsize = android.text.TextUtils.TruncateAt.END
                isSelected = selected
                gravity = Gravity.CENTER
                maxLines = 1
                typeface = Typeface.DEFAULT_BOLD
                setTextSize(TypedValue.COMPLEX_UNIT_SP, if (compact) 15f else 17f)
                setTextColor(fg)
                minHeight = dp(if (compact) 60 else 72)
                setPadding(dp(10), dp(6), dp(10), dp(6))
                setCompoundDrawablesWithIntrinsicBounds(
                    CarUiKit.icon(activity, iconFor(destination), if (selected) CarUiKit.ANTHRACITE else CarUiKit.ORANGE),
                    null, null, null
                )
                compoundDrawablePadding = dp(8)
                background = CarUiKit.pressable(
                    CarUiKit.rounded(
                        if (selected) CarUiKit.ORANGE else CarUiKit.SURFACE,
                        18, activity,
                        if (selected) CarUiKit.ORANGE else CarUiKit.OUTLINE
                    ),
                    activity
                )
                isClickable = true
                isFocusable = true
                setOnClickListener { onSelect(destination) }
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginStart = dp(8)
                }
            }
            bar.addView(tile)
        }

        return bar
    }
}
