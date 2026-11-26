package com.karadaglabs.cartv

import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
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

    /**
     * Yön kilidi yalnızca telefonda: cihaz iki yönü de destekliyor ve
     * araç ekranı değil. Android Automotive OS ve sabit yönlü araç
     * ekranlarında yön zorlanmaz (car-app-quality DO-1; zorlamak
     * letterbox / uyumluluk moduna yol açar).
     */
    fun shouldLockLandscape(
        hasLandscape: Boolean,
        hasPortrait: Boolean,
        onCarScreen: Boolean
    ): Boolean = hasLandscape && hasPortrait && !onCarScreen

    fun applyPhoneLandscape(activity: Activity) {
        val pm = activity.packageManager
        if (
            shouldLockLandscape(
                hasLandscape = pm.hasSystemFeature(PackageManager.FEATURE_SCREEN_LANDSCAPE),
                hasPortrait = pm.hasSystemFeature(PackageManager.FEATURE_SCREEN_PORTRAIT),
                onCarScreen = isCarScreen(activity)
            )
        ) {
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }
    }

    /** Araç ekranında izin verilen ana menü kategorileri. */
    fun allowsWebFeatures(onCarScreen: Boolean): Boolean = !onCarScreen
}
