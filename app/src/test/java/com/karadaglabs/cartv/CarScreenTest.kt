package com.karadaglabs.cartv

import android.content.res.Configuration
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CarScreenTest {

    @Test
    fun automotiveDeviceIsCarScreen() {
        assertTrue(
            CarScreen.isCarScreen(
                isAutomotiveDevice = true,
                uiMode = Configuration.UI_MODE_TYPE_NORMAL
            )
        )
    }

    @Test
    fun carUiModeIsCarScreen() {
        assertTrue(
            CarScreen.isCarScreen(
                isAutomotiveDevice = false,
                uiMode = Configuration.UI_MODE_TYPE_CAR or
                    Configuration.UI_MODE_NIGHT_YES
            )
        )
    }

    @Test
    fun phoneIsNotCarScreen() {
        assertFalse(
            CarScreen.isCarScreen(
                isAutomotiveDevice = false,
                uiMode = Configuration.UI_MODE_TYPE_NORMAL or
                    Configuration.UI_MODE_NIGHT_NO
            )
        )
    }

    @Test
    fun webFeaturesHiddenOnCarScreen() {
        assertFalse(CarScreen.allowsWebFeatures(onCarScreen = true))
        assertTrue(CarScreen.allowsWebFeatures(onCarScreen = false))
    }
}
