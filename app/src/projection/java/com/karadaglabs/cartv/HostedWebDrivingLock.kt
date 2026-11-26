package com.karadaglabs.cartv

import android.graphics.Color
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.widget.FrameLayout
import android.widget.TextView
import com.google.android.apps.auto.sdk.CarActivity

/**
 * Araç ekranındaki YouTube / Google WebView pencereleri için park
 * kilidi. Sürüşte (veya park doğrulanamazsa) WebView duraklatılır
 * (video/ses durur) ve gizlenir; park edilince sürer.
 */
class HostedWebDrivingLock(
    private val activity: CarActivity,
    private val overlayParent: FrameLayout,
    private val webViewProvider: () -> WebView?
) {

    companion object {
        const val PAUSE_ALL_MEDIA_JS =
            "document.querySelectorAll('video,audio')" +
                ".forEach(function(m){try{m.pause();}catch(e){}});"
    }

    private val message = TextView(activity).apply {
        setTextColor(Color.WHITE)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 24f)
        gravity = Gravity.CENTER
        setBackgroundColor(Color.BLACK)
        text = "Araç hareket hâlinde veya park durumu doğrulanamadı.\n" +
            "Bu ekran park edildiğinde açılır."
        layoutParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        isClickable = true
        visibility = View.GONE
    }

    private var allowed: Boolean? = null
    private var resumed = false

    private val sensors = CarDrivingSensors(activity) { decision ->
        apply(decision.videoAllowed)
    }

    init {
        overlayParent.addView(message)
        apply(false)
    }

    fun onStart() = sensors.start()

    fun onStop() {
        sensors.stop()
        apply(false)
    }

    fun onResume() {
        resumed = true
        if (allowed == true) {
            webViewProvider()?.onResume()
        }
    }

    fun onPause() {
        resumed = false
    }

    val isAllowed: Boolean
        get() = allowed == true

    private fun apply(videoAllowed: Boolean) {
        if (allowed == videoAllowed) {
            return
        }
        allowed = videoAllowed

        val view = webViewProvider()
        if (videoAllowed) {
            message.visibility = View.GONE
            view?.visibility = View.VISIBLE
            if (resumed) {
                view?.onResume()
            }
        } else {
            // HTML5 video WebView.onPause ile her zaman durmaz:
            // sayfadaki tüm medya öğelerini açıkça duraklat.
            try {
                view?.evaluateJavascript(PAUSE_ALL_MEDIA_JS, null)
            } catch (_: Throwable) {
            }
            view?.onPause()
            view?.visibility = View.INVISIBLE
            message.visibility = View.VISIBLE
            message.bringToFront()
        }
    }
}
