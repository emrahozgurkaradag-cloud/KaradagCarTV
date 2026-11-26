package com.karadaglabs.cartv

import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Gerçek araç denemesi için yapılandırılmış teşhis kaydı.
 *
 * Her olay tek satır:  EVT=<OLAY> k1=v1 k2=v2 ...
 *  - logcat etiketi: KaradagProjectionDiag
 *  - dosya: filesDir/projection-diag.log (en çok ~256 KB, döner)
 * Telefon uygulamasında menü -> "Araç teşhis kaydını paylaş" ile adb
 * gerekmeden paylaşılır.
 *
 * Olaylar: AA_PACKAGE, HOST_LOAD, SERVICE_CREATE/BIND/UNBIND/DESTROY,
 * ACTIVITY_CREATE/START/RESUME/PAUSE/STOP/DESTROY, WINDOW, DISPLAY,
 * SURFACE_CREATED/CHANGED/DESTROYED, PLAYER_SURFACE_BOUND/CLEARED,
 * VIDEO_FORMAT, PLAYBACK, PARK_GATE, CAR_DISCONNECTED, SESSION.
 *
 * ÖNEMLİ: DISPLAY satırındaki phoneDisplay=false, yüzeyin telefon
 * ekranında OLMADIĞINI gösterir; görüntünün fiziksel araç ekranına
 * ulaştığını tek başına KANITLAMAZ (araç ekranında gözle doğrulanmalı).
 */
object ProjectionDiag {

    const val TAG = "KaradagProjectionDiag"
    private const val FILE_NAME = "projection-diag.log"
    private const val MAX_BYTES = 256 * 1024L

    @Volatile
    private var file: File? = null
    private val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.ROOT)

    fun init(context: Context) {
        if (file != null) return
        file = File(context.applicationContext.filesDir, FILE_NAME)
        event(
            "SESSION",
            "app" to context.packageName,
            "device" to "${Build.MANUFACTURER} ${Build.MODEL}",
            "android" to Build.VERSION.SDK_INT
        )
        logAndroidAuto(context)
    }

    fun logAndroidAuto(context: Context) {
        for (pkg in listOf("com.google.android.projection.gearhead", "com.google.android.projection.bumblebee")) {
            try {
                @Suppress("DEPRECATION")
                val info = context.packageManager.getPackageInfo(pkg, 0)
                val hasCode = (info.applicationInfo!!.flags and android.content.pm.ApplicationInfo.FLAG_HAS_CODE) != 0
                val installer = try {
                    if (Build.VERSION.SDK_INT >= 30) {
                        context.packageManager.getInstallSourceInfo(pkg).installingPackageName
                    } else {
                        @Suppress("DEPRECATION")
                        context.packageManager.getInstallerPackageName(pkg)
                    }
                } catch (_: Throwable) { null }
                @Suppress("DEPRECATION")
                event(
                    "AA_PACKAGE",
                    "pkg" to pkg,
                    "versionName" to info.versionName,
                    "versionCode" to (if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()),
                    "hasCode" to hasCode,
                    "stub" to (info.versionName?.contains("stub", true) == true),
                    "installer" to installer
                )
            } catch (_: Throwable) {
                event("AA_PACKAGE", "pkg" to pkg, "installed" to false)
            }
        }
        val selfInstaller = try {
            if (Build.VERSION.SDK_INT >= 30) {
                context.packageManager.getInstallSourceInfo(context.packageName).installingPackageName
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getInstallerPackageName(context.packageName)
            }
        } catch (_: Throwable) { null }
        // AA 17.x görünürlük denetimi kurulum kaynağına bakar (Fermata #846).
        event("SELF_INSTALL", "pkg" to context.packageName, "installer" to selfInstaller,
            "playInstalled" to (selfInstaller == "com.android.vending"))
    }

    fun event(name: String, vararg fields: Pair<String, Any?>) {
        val line = buildString {
            append("EVT=").append(name)
            for ((k, v) in fields) {
                append(' ').append(k).append('=')
                val s = v?.toString() ?: "null"
                if (s.any { it == ' ' || it == '=' }) append('"').append(s.replace("\"", "'")).append('"') else append(s)
            }
        }
        Log.i(TAG, line)
        write(line)
    }

    fun error(name: String, throwable: Throwable, vararg fields: Pair<String, Any?>) {
        val root = generateSequence(throwable) { it.cause }.last()
        event(name, *fields, "error" to "${root.javaClass.name}: ${root.message}")
    }

    @Synchronized
    private fun write(line: String) {
        val f = file ?: return
        try {
            if (f.length() > MAX_BYTES) {
                val old = File(f.parentFile, "$FILE_NAME.1")
                old.delete()
                f.renameTo(old)
            }
            f.appendText("${stamp.format(Date())} +${SystemClock.elapsedRealtime()}ms $line\n")
        } catch (_: Throwable) {
            // Teşhis yazımı uygulamayı asla bozmaz.
        }
    }

    /** Paylaşım için son kayıtlar (önceki dosya + güncel). */
    fun readAll(context: Context, maxChars: Int = 200_000): String {
        val dir = context.applicationContext.filesDir
        val text = listOf(File(dir, "$FILE_NAME.1"), File(dir, FILE_NAME))
            .filter { it.isFile }
            .joinToString("") { it.readText() }
        return if (text.length > maxChars) text.takeLast(maxChars) else text
    }
}
