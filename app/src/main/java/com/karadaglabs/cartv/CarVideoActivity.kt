package com.karadaglabs.cartv

import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.ViewGroup
import android.widget.TextView
import com.google.android.apps.auto.sdk.CarActivity

class CarVideoActivity : CarActivity() {

    companion object {
        private const val TAG = "KaradagCarVideo"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Log.d(TAG, "================================")
        Log.d(TAG, "CAR VIDEO ACTIVITY AÇILDI")
        Log.d(TAG, "================================")

        val testView = TextView(this).apply {

            text = "VIDEO SURFACE TEST"

            textSize = 32f

            setTextColor(Color.WHITE)

            setBackgroundColor(Color.BLACK)

            gravity = Gravity.CENTER

            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        setContentView(testView)
    }

    override fun onResume() {
        super.onResume()

        Log.d(TAG, "CAR VIDEO ACTIVITY RESUMED")
    }

    override fun onPause() {
        Log.d(TAG, "CAR VIDEO ACTIVITY PAUSED")

        super.onPause()
    }

    override fun onDestroy() {
        Log.d(TAG, "CAR VIDEO ACTIVITY DESTROYED")

        super.onDestroy()
    }
}