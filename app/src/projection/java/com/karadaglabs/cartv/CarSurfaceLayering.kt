package com.karadaglabs.cartv

import android.util.Log
import android.view.SurfaceView

/**
 * Araç penceresindeki video SurfaceView'inin katman sırası.
 *
 * VARSAYILAN z-sırası kullanılır: yüzey pencerenin ARKASINDA kalır ve
 * SurfaceView pencerede şeffaf bir delik açar. Böylece aynı pencerede
 * çizilen kontrol çubuğu ve park uyarısı videonun ÜSTÜNDE görünür.
 *
 * Önceki sürüm setZOrderMediaOverlay(true) kullanıyordu: yüzey pencerenin
 * üstüne çıktığı için kontroller ve "araç hareket hâlinde" uyarısı video
 * oynarken görünmüyordu. Fermata Auto'nun araç penceresindeki video
 * yüzeyi de varsayılan sırayı kullanır (altyazı yüzeyi hariç):
 * fermata/src/main/java/me/aap/fermata/ui/view/VideoView.java
 * Cihaz testi: CarSurfaceLayeringDeviceTest (ekran görüntüsü pikselleri).
 */
object CarSurfaceLayering {

    private const val TAG = "KaradagCarSurface"

    fun configureVideoSurface(view: SurfaceView) {
        view.setZOrderOnTop(false)
        view.setZOrderMediaOverlay(false)
        Log.d(TAG, "video SurfaceView z-order: default (behind window, controls on top)")
    }
}
