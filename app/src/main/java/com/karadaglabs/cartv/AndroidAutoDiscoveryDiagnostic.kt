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
        val videoName = CarVideoService::class.java.name
        val mediaName = CarMediaService::class.java.name
        val video = serviceSnapshot(pm, pkg, videoName)
        val media = serviceSnapshot(pm, pkg, mediaName)
        val projectionHits = queryServices(
            pm,
            Intent(Intent.ACTION_MAIN).addCategory(CATEGORY_PROJECTION)
        )
        val oemHits = queryServices(
            pm,
            Intent(Intent.ACTION_MAIN).addCategory(CATEGORY_PROJECTION_OEM)
        )
        val mediaHits = queryServices(pm, Intent(MEDIA_BROWSER))
        val desc = automotiveDesc(context, pm, pkg)
        val installer = installerPackageName(pm, pkg)
        val app = pm.getApplicationInfo(pkg, PackageManager.GET_META_DATA)
        val debuggable =
            app.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
        val summary =
            "CAR_VIDEO_SERVICE_FOUND=${video.found} " +
                "EXPORTED=${video.exported} " +
                "PERMISSION=${video.permission} " +
                "MAIN=${projectionHits.contains(videoName) || oemHits.contains(videoName)} " +
                "PROJECTION=${projectionHits.contains(videoName)} " +
                "PROJECTION_OEM=${oemHits.contains(videoName)} " +
                "MEDIA_SERVICE_FOUND=${media.found} " +
                "AUTOMOTIVE_DESC=${desc.readable} " +
                "INSTALLER=$installer " +
                "DEBUGGABLE=$debuggable"

        val out = StringBuilder()
        out.appendLine("KARADAG AA DISCOVERY DIAGNOSTIC")
        out.appendLine(summary)
        out.appendLine("package=$pkg")
        out.appendLine("sdk=${Build.VERSION.SDK_INT}")
        out.appendLine("installerPackageName=$installer")
        out.appendLine(installerLine(pm, pkg))
        out.appendLine("applicationInfo.flags=${app.flags}")
        out.appendLine("applicationInfo.flagsHex=0x${Integer.toHexString(app.flags)}")
        out.appendLine("application.debuggable=$debuggable")
        appendApplication(out, context, pm, pkg, app)
        appendServiceSnapshot(out, "CarVideoService", video)
        appendServiceSnapshot(out, "CarMediaService", media)
        appendQuery(
            out,
            projectionHits,
            "QUERY MAIN+CATEGORY_PROJECTION"
        )
        appendQuery(
            out,
            oemHits,
            "QUERY MAIN+CATEGORY_PROJECTION_OEM"
        )
        appendQuery(
            out,
            mediaHits,
            "QUERY MediaBrowserService"
        )
        out.appendLine("--- automotive_app_desc ---")
        out.appendLine("automotive_app_desc.readable=${desc.readable}")
        if (desc.uses.isNotEmpty()) {
            out.appendLine(
                "automotive_app_desc.uses=${desc.uses.joinToString(",")}"
            )
        }
        if (desc.error != null) {
            out.appendLine("automotive_app_desc.error=${desc.error}")
        }
        out.appendLine(summary)
        out.appendLine(
            "NOTE: PackageManager seeing CarVideoService does not " +
                "mean Gearhead will list it. Media and projection " +
                "use different host allowlists."
        )
        return out.toString()
    }

    private data class ServiceSnapshot(
        val found: Boolean,
        val name: String = "",
        val exported: String = "false",
        val enabled: String = "false",
        val permission: String = "NONE",
        val label: String = "",
        val error: String? = null
    )

    private data class QueryHit(
        val name: String,
        val exported: Boolean,
        val permission: String,
        val main: Boolean,
        val projection: Boolean,
        val oem: Boolean,
        val media: Boolean
    )

    private data class QueryResult(
        val hits: List<QueryHit>
    ) {
        fun contains(className: String): Boolean {
            return hits.any { it.name == className }
        }
    }

    private data class AutomotiveDesc(
        val readable: Boolean,
        val uses: List<String> = emptyList(),
        val error: String? = null
    )

    private fun installerPackageName(
        pm: PackageManager,
        pkg: String
    ): String {
        return try {
            if (Build.VERSION.SDK_INT >= 30) {
                val info = pm.getInstallSourceInfo(pkg)
                info.installingPackageName
                    ?: info.initiatingPackageName
                    ?: "null"
            } else {
                @Suppress("DEPRECATION")
                pm.getInstallerPackageName(pkg) ?: "null"
            }
        } catch (error: Exception) {
            "ERROR:${error.message}"
        }
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
        pkg: String,
        app: ApplicationInfo
    ) {
        val meta = app.metaData
        out.appendLine("application.label=${app.loadLabel(pm)}")
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
        out.appendLine(
            "device.hasAutomotiveFeature=" +
                pm.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)
        )
        out.appendLine(
            "app.labelResolved=${context.getString(R.string.app_name)}"
        )
        out.appendLine("applicationInfo.packageName=${app.packageName}")
    }

    private fun serviceSnapshot(
        pm: PackageManager,
        pkg: String,
        className: String
    ): ServiceSnapshot {
        val component = ComponentName(pkg, className)
        return try {
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
            ServiceSnapshot(
                found = true,
                name = info.name,
                exported = info.exported.toString(),
                enabled = info.enabled.toString(),
                permission = info.permission ?: "NONE",
                label = info.loadLabel(pm).toString()
            )
        } catch (error: Exception) {
            ServiceSnapshot(
                found = false,
                error = error.message
            )
        }
    }

    private fun appendServiceSnapshot(
        out: StringBuilder,
        title: String,
        snapshot: ServiceSnapshot
    ) {
        out.appendLine("--- $title ---")
        out.appendLine("$title.found=${snapshot.found}")
        if (snapshot.found) {
            out.appendLine("$title.name=${snapshot.name}")
            out.appendLine("$title.exported=${snapshot.exported}")
            out.appendLine("$title.enabled=${snapshot.enabled}")
            out.appendLine("$title.permission=${snapshot.permission}")
            out.appendLine("$title.label=${snapshot.label}")
        } else {
            out.appendLine("$title.error=${snapshot.error}")
        }
    }

    private fun queryServices(
        pm: PackageManager,
        intent: Intent
    ): QueryResult {
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
        val hits = matches.mapNotNull { resolve ->
            val service = resolve.serviceInfo ?: return@mapNotNull null
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
            QueryHit(
                name = service.name,
                exported = service.exported,
                permission = service.permission ?: "NONE",
                main = actions.contains(Intent.ACTION_MAIN) ||
                    intent.action == Intent.ACTION_MAIN,
                projection = categories.contains(CATEGORY_PROJECTION) ||
                    intent.hasCategory(CATEGORY_PROJECTION),
                oem = categories.contains(CATEGORY_PROJECTION_OEM) ||
                    intent.hasCategory(CATEGORY_PROJECTION_OEM),
                media = actions.contains(MEDIA_BROWSER) ||
                    intent.action == MEDIA_BROWSER
            )
        }
        return QueryResult(hits)
    }

    private fun appendQuery(
        out: StringBuilder,
        result: QueryResult,
        title: String
    ) {
        out.appendLine("--- $title ---")
        out.appendLine("$title.count=${result.hits.size}")
        result.hits.forEach { hit ->
            out.appendLine(
                "  ${hit.name} exported=${hit.exported} " +
                    "permission=${hit.permission} " +
                    "MAIN=${hit.main} " +
                    "PROJECTION=${hit.projection} " +
                    "OEM=${hit.oem} " +
                    "MEDIA=${hit.media}"
            )
        }
    }

    private fun automotiveDesc(
        context: Context,
        pm: PackageManager,
        pkg: String
    ): AutomotiveDesc {
        return try {
            val app = pm.getApplicationInfo(pkg, PackageManager.GET_META_DATA)
            val resId = app.metaData?.getInt(GMS_CAR_APPLICATION) ?: 0
            if (resId == 0) {
                return AutomotiveDesc(readable = false)
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
            AutomotiveDesc(readable = true, uses = uses)
        } catch (error: Exception) {
            AutomotiveDesc(readable = false, error = error.message)
        }
    }
}
