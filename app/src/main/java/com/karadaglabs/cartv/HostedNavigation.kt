package com.karadaglabs.cartv

import android.content.Intent
import android.util.Log
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
}
