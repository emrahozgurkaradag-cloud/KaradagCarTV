package com.karadaglabs.cartv

import android.graphics.Typeface
import android.os.Bundle
import android.util.TypedValue
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity

class DiscoveryDiagnosticActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val report = AndroidAutoDiscoveryDiagnostic.collectAndLog(this)
        val scroll = ScrollView(this)
        val text = TextView(this).apply {
            this.text = report
            setTextIsSelectable(true)
            typeface = Typeface.MONOSPACE
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setPadding(32, 32, 32, 32)
        }
        scroll.addView(text)
        setContentView(scroll)
        title = "AA Discovery"
    }
}
