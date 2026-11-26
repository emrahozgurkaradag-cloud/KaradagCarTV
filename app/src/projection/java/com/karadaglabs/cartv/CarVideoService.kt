package com.karadaglabs.cartv

import android.content.Intent
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import com.google.android.apps.auto.sdk.CarActivity
import com.google.android.apps.auto.sdk.CarActivityService

/**
 * Android Auto PROJECTION giriş noktası (manifest: MAIN +
 * CATEGORY_PROJECTION + CATEGORY_PROJECTION_OEM, exported).
 *
 * Bağlantı zinciri (aauto.aar bytecode, javap ile doğrulandı):
 *  1. Android Auto (gearhead) bu servise bindService yapar.
 *  2. CarActivityService.onCreate -> gms.car.d.onCreate ->
 *     gms.car.a.a(context):
 *       - gms.car.g: MAIN + CAR_DOCK etkinliğini çözer; paket
 *         com.google.android.projection.gearhead veya ...bumblebee olmalı,
 *       - gms.car.i: paketin imzası SDK'ya gömülü Google "gearhead"
 *         sertifikasıyla AYNI olmalı (değilse SecurityException),
 *       - createPackageContext ile gearhead kodu bu sürece yüklenir,
 *         com.google.android.gms.car.DynamicApiFactory.initialize ve
 *         newCarActivityServiceProxy(context) çağrılır.
 *  3. onBind -> CarActivityServiceProxy.onBind: gearhead'e verilen binder.
 *     Host, getCarActivity() sınıfını (CarVideoActivity) örnekleyip
 *     CarActivityHost ile attach eder; pencere (Window) host'tan gelir.
 *
 * Yükleme başarısız olursa (Android Auto yok / imza farklı / taslak paket)
 * süreç ÇÖKMEZ: hata [lastHostLoad]'a yazılır, onBind null döner.
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class CarVideoService : CarActivityService() {

    data class HostLoadResult(
        val ok: Boolean,
        val error: String?,
        val atElapsedMs: Long
    )

    companion object {
        private const val TAG = "KaradagCarVideo"

        /** Son host yükleme sonucu (cihaz testi ve teşhis için). */
        @Volatile
        var lastHostLoad: HostLoadResult? = null
            private set

        @Volatile
        var bindCount: Int = 0
            private set
    }

    private var hostReady = false

    override fun getCarActivity(): Class<out CarActivity> {
        return CarVideoActivity::class.java
    }

    override fun getHandledConfigChanges(): Int {
        return 0xFFFFFFFF.toInt()
    }

    override fun onCreate() {
        Log.d(TAG, "CAR VIDEO SERVICE ON CREATE")

        hostReady = try {
            super.onCreate()
            Log.d(TAG, "CAR VIDEO SERVICE HOST READY (Android Auto proxy loaded)")
            true
        } catch (throwable: Throwable) {
            val root = generateSequence(throwable) { it.cause }.last()
            Log.e(TAG, "CAR VIDEO SERVICE HOST LOAD FAILED: ${root.javaClass.name}: ${root.message}", throwable)
            false
        }

        lastHostLoad = HostLoadResult(
            ok = hostReady,
            error = if (hostReady) null else "host_load_failed",
            atElapsedMs = SystemClock.elapsedRealtime()
        )
    }

    override fun onBind(intent: Intent?): IBinder? {
        bindCount++
        Log.d(TAG, "CAR VIDEO SERVICE ON BIND action=${intent?.action} categories=${intent?.categories}")

        if (!hostReady) {
            Log.e(TAG, "CAR VIDEO SERVICE BIND REFUSED: Android Auto proxy not loaded")
            return null
        }

        return try {
            val binder = super.onBind(intent)
            Log.d(TAG, "CAR VIDEO SERVICE HOST BIND ${if (binder == null) "FAILED binder=null" else "OK"}")
            binder
        } catch (throwable: Throwable) {
            Log.e(TAG, "CAR VIDEO SERVICE HOST BIND FAILED", throwable)
            null
        }
    }

    override fun onUnbind(intent: Intent?): Boolean {
        Log.d(TAG, "CAR VIDEO SERVICE ON UNBIND")
        return if (hostReady) super.onUnbind(intent) else false
    }

    override fun onDestroy() {
        Log.d(TAG, "CAR VIDEO SERVICE ON DESTROY")
        if (hostReady) {
            try {
                super.onDestroy()
            } catch (throwable: Throwable) {
                Log.e(TAG, "CAR VIDEO SERVICE destroy failed", throwable)
            }
        }
    }
}
