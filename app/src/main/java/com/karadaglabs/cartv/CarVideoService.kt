package com.karadaglabs.cartv

import com.google.android.apps.auto.sdk.CarActivity
import com.google.android.apps.auto.sdk.CarActivityService

class CarVideoService : CarActivityService() {

    override fun getCarActivity(): Class<out CarActivity> {
        return CarVideoActivity::class.java
    }

    override fun getHandledConfigChanges(): Int {
        return 0xFFFFFFFF.toInt()
    }
}
