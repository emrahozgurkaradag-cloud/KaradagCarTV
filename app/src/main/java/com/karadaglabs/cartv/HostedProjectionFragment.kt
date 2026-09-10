package com.karadaglabs.cartv

import android.content.Context
import android.content.ContextWrapper
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.fragment.app.Fragment

/**
 * aauto-sdk-demo hosts content by adding a support fragment to the
 * MATCH_PARENT FrameLayout inflated into CarActivity.g. Gearhead
 * often wraps the inflater in a ContextThemeWrapper, so the host
 * CarVideoActivity is found by walking baseContext, then
 * BoundCarHost. The fragment view stays a child of car_host_root;
 * it is not passed to CarActivity.setContentView.
 */
class HostedProjectionFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val car = carActivity(inflater.context)
        if (car == null) {
            Log.e(TAG, "HOST_FRAGMENT_NO_CAR context=${inflater.context.javaClass.name}")
            return FrameLayout(inflater.context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                isFocusable = true
                isFocusableInTouchMode = true
                keepScreenOn = true
            }
        }
        return car.hostedPageIfPresent() ?: car.buildHostedPage()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val car = carActivity(view.context) ?: return
        car.onHostedViewCreated(view)
    }

    private fun carActivity(start: Context?): CarVideoActivity? {
        var current: Context? = start
        while (current != null) {
            if (current is CarVideoActivity) {
                return current
            }
            current = (current as? ContextWrapper)?.baseContext
        }
        return BoundCarHost.currentHost() as? CarVideoActivity
    }

    companion object {
        private const val TAG = "KaradagCarVideo"
    }
}
