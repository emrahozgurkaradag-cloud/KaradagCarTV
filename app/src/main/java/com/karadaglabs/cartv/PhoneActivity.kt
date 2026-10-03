package com.karadaglabs.cartv

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.widget.TextView

class PhoneActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val text = TextView(this).apply {
            this.text = "Karadag Car TV\n\nTelefon uygulaması hazır.\nAndroid Auto bağlandığında\nKaradag Car TV ve Karadag Radyo\nayrı servisler olarak keşfedilir."
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.BLACK)
            gravity = Gravity.CENTER
            typeface = Typeface.DEFAULT_BOLD
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
            setPadding(48, 48, 48, 48)
        }
        setContentView(text)
    }
}
