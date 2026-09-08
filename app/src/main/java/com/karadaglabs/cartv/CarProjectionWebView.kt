package com.karadaglabs.cartv

import android.content.Context
import android.view.View
import android.webkit.WebView

/**
 * Hosted CarActivity windows often report INVISIBLE even while the
 * projection surface is showing. WebView pauses media in that state,
 * so HTML5 video (YouTube) would play audio-only. Keep the engine
 * VISIBLE unless the view is actually GONE.
 */
class CarProjectionWebView(
    context: Context
) : WebView(context) {

    override fun onWindowVisibilityChanged(visibility: Int) {
        if (visibility != View.GONE) {
            super.onWindowVisibilityChanged(View.VISIBLE)
        } else {
            super.onWindowVisibilityChanged(visibility)
        }
    }
}
