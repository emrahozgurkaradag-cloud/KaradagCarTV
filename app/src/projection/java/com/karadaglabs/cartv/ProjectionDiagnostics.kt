package com.karadaglabs.cartv

import android.util.Log
import android.view.Display
import android.view.SurfaceView

/**
 * Araç penceresinin ve video yüzeyinin GERÇEKTE hangi ekranda olduğunu
 * kaydeder. Telefon ekranı Display.DEFAULT_DISPLAY (0) ve
 * FLAG_PRESENTATION içermez; host'un projection penceresi ise ayrı bir
 * (sanal) Display üzerinde olmalıdır. Gerçek araçta logcat:
 *   adb logcat -s KaradagProjectionDiag
 */
object ProjectionDiagnostics {

    private const val TAG = "KaradagProjectionDiag"

    data class SurfaceDisplayInfo(
        val displayId: Int,
        val name: String,
        val width: Int,
        val height: Int,
        val densityDpi: Int,
        val flags: Int,
        val isDefaultDisplay: Boolean,
        val surfaceWidth: Int,
        val surfaceHeight: Int
    ) {
        val isPresentationDisplay: Boolean
            get() = (flags and Display.FLAG_PRESENTATION) != 0
    }

    @Volatile
    var last: SurfaceDisplayInfo? = null
        private set

    fun capture(event: String, view: SurfaceView): SurfaceDisplayInfo? {
        val display = view.display
        if (display == null) {
            Log.w(TAG, "$event: SurfaceView has no Display (not attached)")
            return null
        }
        val metrics = android.util.DisplayMetrics()
        @Suppress("DEPRECATION")
        display.getRealMetrics(metrics)

        val info = SurfaceDisplayInfo(
            displayId = display.displayId,
            name = display.name ?: "",
            width = metrics.widthPixels,
            height = metrics.heightPixels,
            densityDpi = metrics.densityDpi,
            flags = display.flags,
            isDefaultDisplay = display.displayId == Display.DEFAULT_DISPLAY,
            surfaceWidth = view.width,
            surfaceHeight = view.height
        )
        last = info
        ProjectionDiag.event(
            "DISPLAY",
            "when" to event,
            "displayId" to info.displayId,
            "name" to info.name,
            "size" to "${info.width}x${info.height}",
            "dpi" to info.densityDpi,
            "flags" to "0x${Integer.toHexString(info.flags)}",
            "presentation" to info.isPresentationDisplay,
            "phoneDisplay" to info.isDefaultDisplay,
            "surfaceView" to "${info.surfaceWidth}x${info.surfaceHeight}"
        )
        Log.i(
            TAG,
            "$event: displayId=${info.displayId} name='${info.name}' " +
                "${info.width}x${info.height}@${info.densityDpi}dpi flags=0x${Integer.toHexString(info.flags)} " +
                "presentation=${info.isPresentationDisplay} phoneDisplay=${info.isDefaultDisplay} " +
                "surfaceView=${info.surfaceWidth}x${info.surfaceHeight}"
        )
        return info
    }
}
