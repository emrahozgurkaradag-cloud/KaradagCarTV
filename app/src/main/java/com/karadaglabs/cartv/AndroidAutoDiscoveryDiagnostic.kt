package com.karadaglabs.cartv

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.util.Log
import org.xmlpull.v1.XmlPullParser

/**
 * Phone-side PackageManager dump. This answers what the OS can see
 * about CarVideoService vs CarMediaService. It does not prove that
 * Gearhead will list the projection service on the car.
 */
object AndroidAutoDiscoveryDiagnostic {

    const val TAG = "KaradagAADiag"

    const val CATEGORY_PROJECTION =
        "com.google.android.gms.car.category.CATEGORY_PROJECTION"
    const val CATEGORY_PROJECTION_OEM =
        "com.google.android.gms.car.category.CATEGORY_PROJECTION_OEM"
    const val GMS_CAR_APPLICATION =
        "com.google.android.gms.car.application"
    const val MEDIA_BROWSER =
        "android.media.browse.MediaBrowserService"

    fun collectAndLog(context: Context): String {
        val report = collect(context)
        report.lineSequence().forEach { line ->
            if (line.isNotBlank()) {
                Log.i(TAG, line)
            }
        }
        return report
    }

    fun collect(context: Context): String {
        val pm = context.packageManager
        val pkg = context.packageName
        val out = StringBuilder()

        out.appendLine("KARADAG AA DISCOVERY DIAGNOSTIC")
        out.appendLine("package=$pkg")
        out.appendLine("sdk=${Build.VERSION.SDK_INT}")
        out.appendLine(installerLine(pm, pkg))
        appendApplication(out, context, pm, pkg)
        appendService(
            out,
            pm,
            pkg,
            CarVideoService::class.java.name,
            "CarVideoService"
        )
        appendService(
            out,
            pm,
            pkg,
            CarMediaService::class.java.name,
            "CarMediaService"
        )
        appendQuery(
            out,
            pm,
            Intent(Intent.ACTION_MAIN).addCategory(CATEGORY_PROJECTION),
            "QUERY MAIN+CATEGORY_PROJECTION"
        )
        appendQuery(
            out,
            pm,
            Intent(Intent.ACTION_MAIN).addCategory(CATEGORY_PROJECTION_OEM),
            "QUERY MAIN+CATEGORY_PROJECTION_OEM"
        )
        appendQuery(
            out,
            pm,
            Intent(MEDIA_BROWSER),
            "QUERY MediaBrowserService"
        )
        appendAutomotiveDesc(out, context, pm, pkg)
        out.appendLine(
            "NOTE: PackageManager seeing CarVideoService does not " +
                "mean Gearhead will list it. Media and projection " +
                "use different host allowlists."
        )
        return out.toString()
    }

    private fun installerLine(
        pm: PackageManager,
        pkg: String
    ): String {
        return try {
            if (Build.VERSION.SDK_INT >= 30) {
                val info = pm.getInstallSourceInfo(pkg)
                "installer installing=${info.installingPackageName} " +
                    "initiating=${info.initiatingPackageName} " +
                    "originating=${info.originatingPackageName}"
            } else {
                @Suppress("DEPRECATION")
                "installer=${pm.getInstallerPackageName(pkg)}"
            }
        } catch (error: Exception) {
            "installer ERROR ${error.message}"
        }
    }

    private fun appendApplication(
        out: StringBuilder,
        context: Context,
        pm: PackageManager,
        pkg: String
    ) {
        val flags = PackageManager.GET_META_DATA
        val app = pm.getApplicationInfo(pkg, flags)
        val meta = app.metaData
        val debuggable =
            app.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
        out.appendLine("application.label=${app.loadLabel(pm)}")
        out.appendLine("application.debuggable=$debuggable")
        out.appendLine(
            "application.meta.gms.car.application=" +
                (meta?.containsKey(GMS_CAR_APPLICATION) == true)
        )
        out.appendLine(
            "application.meta.theme=" +
                (meta?.containsKey(
                    "com.google.android.gms.car.application.theme"
                ) == true)
        )
        out.appendLine(
            "application.meta.SmallIcon=" +
                (meta?.containsKey(
                    "com.google.android.gms.car.notification.SmallIcon"
                ) == true)
        )
        val automotive = pm.hasSystemFeature(
            PackageManager.FEATURE_AUTOMOTIVE
        )
        out.appendLine("device.hasAutomotiveFeature=$automotive")
        out.appendLine(
            "app.labelResolved=${context.getString(R.string.app_name)}"
        )
    }

    private fun appendService(
        out: StringBuilder,
        pm: PackageManager,
        pkg: String,
        className: String,
        title: String
    ) {
        out.appendLine("--- $title ---")
        val component = ComponentName(pkg, className)
        try {
            val info: ServiceInfo = if (Build.VERSION.SDK_INT >= 33) {
                pm.getServiceInfo(
                    component,
                    PackageManager.ComponentInfoFlags.of(
                        PackageManager.GET_META_DATA.toLong()
                    )
                )
            } else {
                @Suppress("DEPRECATION")
                pm.getServiceInfo(component, PackageManager.GET_META_DATA)
            }
            out.appendLine("$title.found=true")
            out.appendLine("$title.name=${info.name}")
            out.appendLine("$title.exported=${info.exported}")
            out.appendLine("$title.enabled=${info.enabled}")
            out.appendLine(
                "$title.permission=${info.permission ?: "NONE"}"
            )
            out.appendLine("$title.label=${info.loadLabel(pm)}")
        } catch (error: Exception) {
            out.appendLine("$title.found=false")
            out.appendLine("$title.error=${error.message}")
        }
    }

    private fun appendQuery(
        out: StringBuilder,
        pm: PackageManager,
        intent: Intent,
        title: String
    ) {
        out.appendLine("--- $title ---")
        val matches = if (Build.VERSION.SDK_INT >= 33) {
            pm.queryIntentServices(
                intent,
                PackageManager.ResolveInfoFlags.of(
                    PackageManager.MATCH_ALL.toLong()
                )
            )
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentServices(intent, PackageManager.MATCH_ALL)
        }
        out.appendLine("$title.count=${matches.size}")
        matches.forEach { resolve ->
            val service = resolve.serviceInfo ?: return@forEach
            val filters = resolve.filter
            val actions = mutableListOf<String>()
            val categories = mutableListOf<String>()
            if (filters != null) {
                for (i in 0 until filters.countActions()) {
                    actions += filters.getAction(i)
                }
                for (i in 0 until filters.countCategories()) {
                    categories += filters.getCategory(i)
                }
            }
            out.appendLine(
                "  ${service.name} exported=${service.exported} " +
                    "permission=${service.permission ?: "NONE"} " +
                    "MAIN=${actions.contains(Intent.ACTION_MAIN)} " +
                    "PROJECTION=${categories.contains(CATEGORY_PROJECTION)} " +
                    "OEM=${categories.contains(CATEGORY_PROJECTION_OEM)} " +
                    "MEDIA=${actions.contains(MEDIA_BROWSER)}"
            )
        }
    }

    private fun appendAutomotiveDesc(
        out: StringBuilder,
        context: Context,
        pm: PackageManager,
        pkg: String
    ) {
        out.appendLine("--- automotive_app_desc ---")
        try {
            val app = pm.getApplicationInfo(pkg, PackageManager.GET_META_DATA)
            val resId = app.metaData?.getInt(GMS_CAR_APPLICATION) ?: 0
            if (resId == 0) {
                out.appendLine("automotive_app_desc.readable=false")
                return
            }
            val parser = context.resources.getXml(resId)
            val uses = mutableListOf<String>()
            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG &&
                    parser.name == "uses"
                ) {
                    val name = parser.getAttributeValue(null, "name")
                    if (!name.isNullOrBlank()) {
                        uses += name
                    }
                }
                event = parser.next()
            }
            out.appendLine("automotive_app_desc.readable=true")
            out.appendLine(
                "automotive_app_desc.uses=${uses.joinToString(",")}"
            )
        } catch (error: Exception) {
            out.appendLine("automotive_app_desc.readable=false")
            out.appendLine("automotive_app_desc.error=${error.message}")
        }
    }
}
