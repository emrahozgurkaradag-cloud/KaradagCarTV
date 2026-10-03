package com.karadaglabs.cartv

import android.view.WindowManager
import com.google.android.gms.car.CarActivityHost
import com.google.android.gms.car.CarActivityServiceProxy

/**
 * Fermata-style Android Auto video layer. Gearhead only composites
 * video pixels after CarActivityServiceProxy.requestVideoFocus()
 * and CarActivityHost.enableWindowTransparency().
 *
 * Uses the public aauto.aar host/proxy types. Does not patch
 * Gearhead, hook LSPosed, or spoof the Play Store installer.
 */
object CarHost {

    fun activateVideoLayer(hosted: Any?): CarVideoFocusResult {
        val host = hostOf(hosted)
        if (host != null) {
            try {
                host.enableWindowTransparency()
            } catch (_: Throwable) {
            }

            try {
                host.getWindow()?.addFlags(
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                )
            } catch (_: Throwable) {
            }

            val proxy = proxyFromHost(host) ?: proxyOf(hosted)
            if (proxy != null) {
                proxy.requestVideoFocus()
                return CarVideoFocusResult(
                    granted = true,
                    reason = "requestVideoFocus"
                )
            }
        }

        val serviceProxy = proxyOf(hosted)
        if (serviceProxy != null) {
            serviceProxy.requestVideoFocus()
            return CarVideoFocusResult(
                granted = true,
                reason = "requestVideoFocus"
            )
        }

        return CarVideoFocus.request(hosted)
    }

    fun deactivateVideoLayer(hosted: Any?): CarVideoFocusResult {
        val host = hostOf(hosted)
        val proxy = host?.let { proxyFromHost(it) } ?: proxyOf(hosted)
        if (proxy != null) {
            proxy.relinquishVideoFocus()
            return CarVideoFocusResult(
                granted = true,
                reason = "relinquishVideoFocus"
            )
        }

        return CarVideoFocus.relinquish(hosted)
    }

    private fun proxyFromHost(host: CarActivityHost): CarActivityServiceProxy? {
        return try {
            host.getCarActivityService() as? CarActivityServiceProxy
        } catch (_: Throwable) {
            null
        }
    }

    private fun hostOf(target: Any?): CarActivityHost? {
        if (target == null) {
            return null
        }

        if (target is CarActivityHost) {
            return target
        }

        return typedField(target, CarActivityHost::class.java)
    }

    private fun proxyOf(target: Any?): CarActivityServiceProxy? {
        if (target == null) {
            return null
        }

        if (target is CarActivityServiceProxy) {
            return target
        }

        return typedField(target, CarActivityServiceProxy::class.java)
    }

    private fun <T> typedField(target: Any, type: Class<T>): T? {
        var current: Class<*>? = target.javaClass

        while (current != null && current != Any::class.java) {
            for (field in current.declaredFields) {
                if (!type.isAssignableFrom(field.type)) {
                    continue
                }

                try {
                    field.isAccessible = true
                    val value = field.get(target)
                    if (type.isInstance(value)) {
                        @Suppress("UNCHECKED_CAST")
                        return value as T
                    }
                } catch (_: Throwable) {
                }
            }

            current = current.superclass
        }

        return null
    }
}
