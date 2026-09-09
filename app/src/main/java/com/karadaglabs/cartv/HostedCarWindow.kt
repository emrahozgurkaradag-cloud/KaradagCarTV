package com.karadaglabs.cartv

import android.content.Intent
import android.util.Log
import android.view.View
import android.view.ViewGroup
import com.google.android.apps.auto.sdk.CarActivity
import java.lang.reflect.Method

/**
 * aauto.aar CarActivityHost helpers.
 *
 * SDK [CarActivity.startCarActivity] talks to Gearhead
 * ICarUiController.startCarActivity. That remote method is often
 * null ("Error invoking a null method. Ignored.") and does not
 * instantiate another HostedCarActivity. The host API that actually
 * creates a hosted window is CarActivityHost.startCarProjectionActivity.
 */
object HostedCarWindow {

    private const val TAG = "KaradagCarHost"

    data class StartResult(
        val attempted: Boolean,
        val succeeded: Boolean,
        val reason: String
    )

    fun startProjectionActivity(
        hosted: Any?,
        intent: Intent
    ): StartResult {
        if (hosted == null) {
            Log.e(TAG, "startCarProjectionActivity skipped host=null")
            return StartResult(false, false, "host_unavailable")
        }

        val host = findHost(hosted)
        if (host == null) {
            Log.e(
                TAG,
                "startCarProjectionActivity host not found " +
                    "from=${hosted.javaClass.name}"
            )
            return StartResult(false, false, "host_not_found")
        }

        val method = findMethod(
            host,
            "startCarProjectionActivity",
            Intent::class.java
        )

        if (method == null) {
            Log.e(
                TAG,
                "startCarProjectionActivity missing on ${host.javaClass.name}"
            )
            return StartResult(true, false, "api_missing")
        }

        return try {
            method.invoke(host, intent)
            Log.d(
                TAG,
                "startCarProjectionActivity OK " +
                    "component=${intent.component} " +
                    "extra=${intent.getStringExtra(ProjectionDestination.EXTRA)}"
            )
            StartResult(true, true, "startCarProjectionActivity")
        } catch (throwable: Throwable) {
            val cause = if (throwable is java.lang.reflect.InvocationTargetException) {
                throwable.cause ?: throwable
            } else {
                throwable
            }

            Log.e(
                TAG,
                "startCarProjectionActivity FAILED " +
                    "component=${intent.component}",
                cause
            )
            StartResult(
                true,
                false,
                cause.javaClass.simpleName + ":" + cause.message
            )
        }
    }

    fun attachFillParent(
        activity: CarActivity,
        view: View
    ) {
        val fill = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        view.layoutParams = fill
        view.minimumWidth = 1
        view.minimumHeight = 1
        activity.setContentView(view)
        logView("setContentView", view)

        view.post {
            if (view.width <= 0 || view.height <= 0) {
                val metrics = activity.resources.displayMetrics
                view.minimumWidth = metrics.widthPixels.coerceAtLeast(1)
                view.minimumHeight = metrics.heightPixels.coerceAtLeast(1)
                view.layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                view.requestLayout()
                Log.w(
                    TAG,
                    "hosted content was 0x0, forced display " +
                        "${metrics.widthPixels}x${metrics.heightPixels}"
                )
            }

            logView("setContentView.posted", view)
        }
    }

    fun logView(
        label: String,
        view: View?
    ) {
        if (view == null) {
            Log.w(TAG, "$label view=null")
            return
        }

        Log.d(
            TAG,
            "$label class=${view.javaClass.simpleName} " +
                "vis=${visibilityName(view.visibility)} " +
                "size=${view.width}x${view.height} " +
                "measured=${view.measuredWidth}x${view.measuredHeight} " +
                "lp=${layoutName(view.layoutParams)}"
        )
    }

    fun visibilityName(visibility: Int): String {
        return when (visibility) {
            View.VISIBLE -> "VISIBLE"
            View.INVISIBLE -> "INVISIBLE"
            View.GONE -> "GONE"
            else -> visibility.toString()
        }
    }

    private fun layoutName(params: ViewGroup.LayoutParams?): String {
        if (params == null) {
            return "null"
        }

        return "${sizeName(params.width)}x${sizeName(params.height)}"
    }

    private fun sizeName(value: Int): String {
        return when (value) {
            ViewGroup.LayoutParams.MATCH_PARENT -> "MATCH"
            ViewGroup.LayoutParams.WRAP_CONTENT -> "WRAP"
            else -> value.toString()
        }
    }

    fun findHost(hosted: Any): Any? {
        findMethod(hosted, "startCarProjectionActivity", Intent::class.java)
            ?.let { return hosted }

        var type: Class<*>? = hosted.javaClass

        while (type != null && type != Any::class.java) {
            if (shouldInspectFields(type)) {
                for (field in type.declaredFields) {
                    val value = readField(hosted, field) ?: continue

                    findMethod(
                        value,
                        "startCarProjectionActivity",
                        Intent::class.java
                    )?.let { return value }
                }
            }

            type = type.superclass
        }

        return null
    }

    private fun shouldInspectFields(type: Class<*>): Boolean {
        val name = type.name

        return !name.startsWith("java.") &&
            !name.startsWith("javax.") &&
            !name.startsWith("kotlin.") &&
            !name.startsWith("android.os.") &&
            !name.startsWith("android.util.") &&
            !name.startsWith("android.view.")
    }

    private fun readField(
        target: Any,
        field: java.lang.reflect.Field
    ): Any? {
        return try {
            field.isAccessible = true
            field.get(target)
        } catch (_: Throwable) {
            null
        }
    }

    private fun findMethod(
        target: Any,
        name: String,
        vararg parameterTypes: Class<*>
    ): Method? {
        var type: Class<*>? = target.javaClass

        while (type != null && type != Any::class.java) {
            val declared = type.declaredMethods.firstOrNull { method ->
                method.name == name &&
                    method.parameterTypes.contentEquals(parameterTypes)
            }

            if (declared != null) {
                declared.isAccessible = true
                return declared
            }

            val published = type.methods.firstOrNull { method ->
                method.name == name &&
                    method.parameterTypes.contentEquals(parameterTypes)
            }

            if (published != null) {
                return published
            }

            type = type.superclass
        }

        return null
    }
}
