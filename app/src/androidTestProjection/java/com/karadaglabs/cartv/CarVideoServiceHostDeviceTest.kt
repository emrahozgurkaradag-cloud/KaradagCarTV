package com.karadaglabs.cartv

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * CarVideoService'e Android Auto'nun kullandığı Intent ile bağlanır ve
 * aauto.aar'ın Android Auto (gearhead) kodunu bu sürece yükleyip
 * yükleyemediğini ölçer (gms.car.a -> DynamicApiFactory
 * .newCarActivityServiceProxy). Bu bir ARAÇ BAĞLANTISI DEĞİLDİR: bağlanan
 * taraf Android Auto değil testtir; araç penceresi açılmaz.
 *
 * Beklenen:
 *  - gearhead yok / taslak (stub) / imza farklı: süreç çökmez, binder null.
 *  - Google imzalı, kod içeren gearhead: proxy yüklenir, binder null değil.
 */
@RunWith(AndroidJUnit4::class)
class CarVideoServiceHostDeviceTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun gearheadInfo(): Pair<String?, Boolean> {
        return try {
            val info = context.packageManager.getPackageInfo("com.google.android.projection.gearhead", 0)
            val hasCode = (info.applicationInfo!!.flags and android.content.pm.ApplicationInfo.FLAG_HAS_CODE) != 0
            info.versionName to hasCode
        } catch (_: PackageManager.NameNotFoundException) {
            null to false
        }
    }

    @Test
    fun bindLikeAndroidAutoLoadsHostProxyOrFailsGracefully() {
        val (version, hasCode) = gearheadInfo()
        android.util.Log.i("KaradagHostTest", "gearhead versionName=$version hasCode=$hasCode")

        val connected = CountDownLatch(1)
        var binder: IBinder? = null
        var nullBinding = false
        val conn = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                binder = service; connected.countDown()
            }
            override fun onServiceDisconnected(name: ComponentName?) {}
            override fun onNullBinding(name: ComponentName?) {
                nullBinding = true; connected.countDown()
            }
        }

        val intent = Intent(Intent.ACTION_MAIN)
            .addCategory("com.google.android.gms.car.category.CATEGORY_PROJECTION_OEM")
            .setPackage(context.packageName)
        val resolved = context.packageManager.queryIntentServices(intent, 0)
        assertTrue("CarVideoService intent ile çözülmeli", resolved.any { it.serviceInfo.name.endsWith(".CarVideoService") })
        intent.setClassName(context.packageName, "com.karadaglabs.cartv.CarVideoService")

        assertTrue(context.bindService(intent, conn, Context.BIND_AUTO_CREATE))
        try {
            if (!connected.await(20, TimeUnit.SECONDS)) fail("service did not connect")
            val load = CarVideoService.lastHostLoad
            assertNotNull("onCreate host yükleme sonucunu kaydetmeli", load)
            android.util.Log.i(
                "KaradagHostTest",
                "hostLoad ok=${load!!.ok} error=${load.error} binder=${binder != null} nullBinding=$nullBinding"
            )

            val realAndroidAuto = version != null && hasCode && !version.contains("stub", ignoreCase = true)
            if (realAndroidAuto) {
                assertTrue("Google imzalı Android Auto ($version) varken proxy yüklenmeli", load.ok)
                assertTrue("proxy binder döndürmeli", binder != null)
            } else {
                assertTrue("Android Auto yok/taslak: süreç çökmeden null binder", !load.ok && binder == null)
            }
        } finally {
            context.unbindService(conn)
        }
    }
}
