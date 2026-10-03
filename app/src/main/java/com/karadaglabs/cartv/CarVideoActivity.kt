package com.karadaglabs.cartv

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.widget.TextView
import com.google.android.apps.auto.sdk.CarActivity

/**
 * First-version hosted CarActivity. Fermata MainCarActivity.onCreate
 * calls super.onCreate, setIgnoreConfigChanges, then setContentView.
 * This build only shows the projection test label.
 */
class CarVideoActivity : CarActivity() {

    companion object {
        private const val TAG = "KaradagCarVideo"
        const val TEST_SCREEN_TEXT = "KARADAG CAR TV\nPROJECTION OK"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Log.d(TAG, "CAR VIDEO ACTIVITY AÇILDI")
        CarProjectionRouter.markHosted(packageName)

        setIgnoreConfigChanges(0xFFFFFFFF.toInt())
        try {
            val ui = getCarUiController()
            ui.statusBarController.hideAppHeader()
            ui.menuController.hideMenuButton()
        } catch (_: Throwable) {
        }

        val text = TextView(this).apply {
            this.text = TEST_SCREEN_TEXT
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.BLACK)
            gravity = Gravity.CENTER
            typeface = Typeface.DEFAULT_BOLD
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 28f)
        }
        setContentView(text)
    }
}
