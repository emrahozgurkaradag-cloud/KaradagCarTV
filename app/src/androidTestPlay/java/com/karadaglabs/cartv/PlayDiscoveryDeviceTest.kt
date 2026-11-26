package com.karadaglabs.cartv

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** play APK'sında projection / yansıtma bileşeni olmamalı (gerçek cihaz). */
@RunWith(AndroidJUnit4::class)
class PlayDiscoveryDeviceTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val pm = context.packageManager

    @Test
    fun playPackageHasMediaButNoProjection() {
        assertEquals("com.karadaglabs.cartv", context.packageName)
        val projection = pm.queryIntentServices(
            Intent(Intent.ACTION_MAIN)
                .addCategory("com.google.android.gms.car.category.CATEGORY_PROJECTION_OEM")
                .setPackage(context.packageName),
            0
        )
        assertTrue(projection.isEmpty())
        val media = pm.queryIntentServices(
            Intent("android.media.browse.MediaBrowserService").setPackage(context.packageName),
            0
        )
        assertTrue(media.any { it.serviceInfo.name == "com.karadaglabs.cartv.CarMediaService" })
    }
}
