package com.karadaglabs.cartv

import android.util.Log
import android.view.SurfaceView

/**
 * Araç penceresindeki video SurfaceView'inin katman sırası.
 *
 * VARSAYILAN z-sırası kullanılır: yüzey pencerenin arkasında kalır ve
 * SurfaceView pencerede şeffaf bir delik açar; aynı pencerede çizilen
 * kontrol çubuğu ve park uyarısı videonun üstünde görünür. Fermata
 * Auto'nun araç penceresindeki video yüzeyi de varsayılan sırayı kullanır:
 * fermata/src/main/java/me/aap/fermata/ui/view/VideoView.java
 *
 * Not (cihazda ölçüldü, CarSurfaceLayeringDeviceTest): önceki
 * setZOrderMediaOverlay(true) de pencerenin ARKASINDA kalır, kontrolleri
 * gizlemez; yalnızca diğer SurfaceView'lere göre sırayı değiştirir.
 * Varsayılana dönüş Fermata ile aynı yapılandırma içindir, bir hata
 * düzeltmesi değildir. Gerçek Android Auto host penceresinde
 * doğrulanmadı.
 */
object CarSurfaceLayering {

    private const val TAG = "KaradagCarSurface"

    fun configureVideoSurface(view: SurfaceView) {
        view.setZOrderOnTop(false)
        view.setZOrderMediaOverlay(false)
        Log.d(TAG, "video SurfaceView z-order: default (behind window, controls on top)")
    }
}
