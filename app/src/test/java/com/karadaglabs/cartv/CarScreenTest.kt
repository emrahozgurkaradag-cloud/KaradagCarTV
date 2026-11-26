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

    @Test
    fun landscapeLockOnlyOnPhones() {
        assertTrue(CarScreen.shouldLockLandscape(hasLandscape = true, hasPortrait = true, onCarScreen = false))
        // Android Automotive OS / araç ekranı: yön zorlanmaz.
        assertFalse(CarScreen.shouldLockLandscape(hasLandscape = true, hasPortrait = true, onCarScreen = true))
        // Sabit yönlü ekran (yalnızca yatay veya yalnızca dikey).
        assertFalse(CarScreen.shouldLockLandscape(hasLandscape = true, hasPortrait = false, onCarScreen = false))
        assertFalse(CarScreen.shouldLockLandscape(hasLandscape = false, hasPortrait = true, onCarScreen = false))
    }
}
