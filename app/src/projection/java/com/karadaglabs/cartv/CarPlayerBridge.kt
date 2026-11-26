package com.karadaglabs.cartv

import android.app.Activity
import android.content.pm.PackageManager
import androidx.annotation.OptIn
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer

/**
 * projection yapı türü — Android Auto araç ekranında görüntü.
 *
 * aauto.aar (resmî olmayan Google Auto SDK; Fermata ile aynı yöntem)
 * CarActivity penceresine ExoPlayer görüntüsü bağlar. Google bu yolu
 * üçüncü taraf uygulamalar için yayınlamaz. Bir kullanıcının AA 17.7
 * analizine göre (doğrulanmadı) Android Auto bu türü yalnızca Play
 * yükleyicisiyle veya geliştirici head unit'te listeler.
 * Sürüşte görüntü DrivingSafetyGate ile kapanır.
 * Bkz. docs/ANDROID_AUTO.md.
 */
@OptIn(UnstableApi::class)
object CarPlayerBridge {

    const val projectsVideo: Boolean = true

    fun attach(player: ExoPlayer) {
        CarVideoPlayerHolder.setPlayer(player)
    }

    fun detach() {
        CarVideoPlayerHolder.clearPlayer()
    }

    /**
     * aauto.aar gms CarSensorManager.registerListener:
     * @RequiresPermission(anyOf = {ACCESS_FINE_LOCATION,
     *   com.google.android.gms.permission.CAR_SPEED, ... }, conditional = true)
     * Hız sensörü için CAR_SPEED gerekir. Koruma seviyesi belgelenmediği
     * için verilmemişse çalışma anında istenir (normal izinse sistem
     * zaten verir, tanımsızsa istek anında reddedilir — zararsız).
     * Sürüş durumu / vites / el freni için SDK izin listelemiyor.
     */
    const val CAR_SPEED_PERMISSION = "com.google.android.gms.permission.CAR_SPEED"
    private const val REQUEST_CODE = 4107

    fun requestCarPermissions(activity: Activity) {
        if (
            ContextCompat.checkSelfPermission(activity, CAR_SPEED_PERMISSION) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                activity,
                arrayOf(CAR_SPEED_PERMISSION),
                REQUEST_CODE
            )
        }
    }
}
