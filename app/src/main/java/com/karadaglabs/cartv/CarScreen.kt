package com.karadaglabs.cartv

import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration

/**
 * Uygulama bir araç ekranında mı çalışıyor?
 *
 * - Android Automotive OS (Google built-in): FEATURE_AUTOMOTIVE.
 * - Araç modu (UI_MODE_TYPE_CAR): Android Auto park video ekranı
 *   veya araç UI modu.
 *
 * Araç ekranında yalnızca uygulamanın kayıtlı kategorisine (video)
 * uyan özellikler gösterilir; web tarayıcı / YouTube WebView gibi
 * kategori dışı özellikler gizlenir (car-app-quality PC-1).
 */
object CarScreen {

    fun isCarScreen(context: Context): Boolean {
        val automotive =
            context.packageManager.hasSystemFeature(
                PackageManager.FEATURE_AUTOMOTIVE
            )

        return isCarScreen(
            isAutomotiveDevice = automotive,
            uiMode = context.resources.configuration.uiMode
        )
    }

    fun isCarScreen(
        isAutomotiveDevice: Boolean,
        uiMode: Int
    ): Boolean {
        val type = uiMode and Configuration.UI_MODE_TYPE_MASK
        return isAutomotiveDevice || type == Configuration.UI_MODE_TYPE_CAR
    }

    /** Araç ekranında izin verilen ana menü kategorileri. */
    fun allowsWebFeatures(onCarScreen: Boolean): Boolean = !onCarScreen
}
