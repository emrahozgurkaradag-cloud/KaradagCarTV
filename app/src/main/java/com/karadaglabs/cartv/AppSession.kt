package com.karadaglabs.cartv

import android.content.Context
import android.provider.Settings

object AppSession {
    const val API_BASE_URL = "https://api.karadaglabs.com"
    private const val PREFS = "karadag_car_tv_cache"
    private const val KEY_EMAIL = "customer_email"
    private const val KEY_LICENSE = "license_key"

    fun prefs(context: Context) =
        (context.applicationContext ?: context)
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun deviceUuid(context: Context): String {
        val resolver = (context.applicationContext ?: context).contentResolver
        val androidId = Settings.Secure.getString(
            resolver,
            Settings.Secure.ANDROID_ID
        )

        return "ANDROID-${androidId ?: "UNKNOWN"}"
    }

    fun email(context: Context): String =
        prefs(context).getString(KEY_EMAIL, "").orEmpty()

    fun licenseKey(context: Context): String =
        prefs(context).getString(KEY_LICENSE, "").orEmpty()

    fun saveLicense(
        context: Context,
        email: String,
        licenseKey: String
    ) {
        prefs(context).edit()
            .putString(KEY_EMAIL, email.trim().lowercase())
            .putString(KEY_LICENSE, licenseKey)
            .apply()
    }

    fun clearLicense(context: Context) {
        prefs(context).edit()
            .remove(KEY_EMAIL)
            .remove(KEY_LICENSE)
            .remove("license")
            .remove("contents")
            .remove("iptv_channels")
            .apply()
    }
}