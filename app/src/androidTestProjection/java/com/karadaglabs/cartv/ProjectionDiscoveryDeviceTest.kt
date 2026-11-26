package com.karadaglabs.cartv

import android.content.Intent
import android.content.pm.PackageManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.xmlpull.v1.XmlPullParser

/**
 * Kurulu projection APK'sını, Android Auto host'unun yaptığı türde
 * PackageManager sorgularıyla denetler (gerçek cihaz/emülatör).
 * Android Auto'nun ek görünürlük denetimini (yükleyici, sunucu listesi)
 * DOĞRULAMAZ; yalnızca uygulamanın keşfedilebilir tanımlandığını gösterir.
 */
@RunWith(AndroidJUnit4::class)
class ProjectionDiscoveryDeviceTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val pm = context.packageManager

    private fun services(intent: Intent) =
        pm.queryIntentServices(intent.setPackage(context.packageName), PackageManager.GET_META_DATA)
            .map { it.serviceInfo }

    @Test
    fun packageIsSeparateFromPlay() {
        assertEquals("com.karadaglabs.cartv.projection", context.packageName)
    }

    @Test
    fun projectionOemServiceIsDiscoverableAndExported() {
        for (category in listOf(
            "com.google.android.gms.car.category.CATEGORY_PROJECTION_OEM",
            "com.google.android.gms.car.category.CATEGORY_PROJECTION"
        )) {
            val found = services(Intent(Intent.ACTION_MAIN).addCategory(category))
            val svc = found.firstOrNull { it.name == "com.karadaglabs.cartv.CarVideoService" }
            assertNotNull("CarVideoService for $category", svc)
            assertTrue(svc!!.exported)
            assertTrue(svc.enabled)
        }
    }

    @Test
    fun mediaAndTemplateServicesAreDiscoverable() {
        assertTrue(
            services(Intent("android.media.browse.MediaBrowserService"))
                .any { it.name == "com.karadaglabs.cartv.CarMediaService" && it.exported }
        )
        assertTrue(
            services(Intent("androidx.car.app.CarAppService").addCategory("androidx.car.app.category.NAVIGATION"))
                .any { it.name == "com.karadaglabs.cartv.CarMirrorService" && it.exported }
        )
    }

    @Test
    fun automotiveDescriptorMetadataParsesWithProjectionUses() {
        val info = pm.getApplicationInfo(context.packageName, PackageManager.GET_META_DATA)
        val res = info.metaData.getInt("com.google.android.gms.car.application")
        assertTrue("metadata resource", res != 0)
        val uses = mutableSetOf<String>()
        context.resources.getXml(res).use { xml ->
            while (xml.next() != XmlPullParser.END_DOCUMENT) {
                if (xml.eventType == XmlPullParser.START_TAG && xml.name == "uses") {
                    uses += xml.getAttributeValue(null, "name")
                }
            }
        }
        assertEquals(setOf("media", "template", "service", "projection"), uses)
    }

    @Test
    fun carSpeedPermissionIsRequested() {
        val info = pm.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
        assertTrue(info.requestedPermissions!!.contains("com.google.android.gms.permission.CAR_SPEED"))
    }
}
