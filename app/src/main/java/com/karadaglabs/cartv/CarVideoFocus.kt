package com.karadaglabs.cartv

import java.lang.reflect.Method

data class CarVideoFocusResult(
    val granted: Boolean,
    val reason: String
) {

    val failed: Boolean
        get() = !granted
}

/**
 * aauto.aar CarActivityServiceProxy.requestVideoFocus /
 * relinquishVideoFocus erişimi.
 *
 * Hosted CarActivity, CarActivityHost alanını (gms.car.c.a)
 * attach() sonrası tutar. Host.getCarActivityService() proxy
 * döndürür. Host yoksa veya API yoksa crash etmez.
 *
 * Android Log kullanılmaz: unit test JVM'sinde stub Log patlar.
 */
object CarVideoFocus {

    fun request(hosted: Any?): CarVideoFocusResult {
        return invokeFocus(hosted, "requestVideoFocus")
    }

    fun relinquish(hosted: Any?): CarVideoFocusResult {
        return invokeFocus(hosted, "relinquishVideoFocus")
    }

    private fun invokeFocus(
        hosted: Any?,
        methodName: String
    ): CarVideoFocusResult {
        if (hosted == null) {
            return fallback("host_unavailable")
        }

        return try {
            findNoArgMethod(hosted, methodName)?.let { method ->
                method.invoke(hosted)
                return granted(methodName)
            }

            invokeOnCarActivityService(hosted, methodName)?.let {
                return it
            }

            var type: Class<*>? = hosted.javaClass

            while (type != null && type != Any::class.java) {
                if (shouldInspectFields(type)) {
                    for (field in type.declaredFields) {
                        val value = readField(hosted, field) ?: continue

                        findNoArgMethod(value, methodName)?.let { method ->
                            method.invoke(value)
                            return granted(methodName)
                        }

                        invokeOnCarActivityService(value, methodName)?.let {
                            return it
                        }
                    }
                }

                type = type.superclass
            }

            fallback("video_focus_unavailable")
        } catch (throwable: Throwable) {
            val cause = if (throwable is java.lang.reflect.InvocationTargetException) {
                throwable.cause ?: throwable
            } else {
                throwable
            }

            fallback(cause.javaClass.simpleName)
        }
    }

    private fun invokeOnCarActivityService(
        target: Any,
        methodName: String
    ): CarVideoFocusResult? {
        val service = findNoArgMethod(
            target,
            "getCarActivityService"
        )?.invoke(target) ?: return null

        val method = findNoArgMethod(service, methodName)
            ?: return fallback("video_focus_unavailable")

        method.invoke(service)
        return granted(methodName)
    }

    private fun shouldInspectFields(type: Class<*>): Boolean {
        val name = type.name

        return !name.startsWith("java.") &&
            !name.startsWith("javax.") &&
            !name.startsWith("kotlin.") &&
            !name.startsWith("android.os.") &&
            !name.startsWith("android.util.")
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

    private fun findNoArgMethod(
        target: Any,
        name: String
    ): Method? {
        var type: Class<*>? = target.javaClass

        while (type != null && type != Any::class.java) {
            val declared = type.declaredMethods.firstOrNull { method ->
                method.name == name && method.parameterTypes.isEmpty()
            }

            if (declared != null) {
                declared.isAccessible = true
                return declared
            }

            val published = type.methods.firstOrNull { method ->
                method.name == name && method.parameterTypes.isEmpty()
            }

            if (published != null) {
                return published
            }

            type = type.superclass
        }

        return null
    }

    private fun granted(methodName: String): CarVideoFocusResult {
        return CarVideoFocusResult(
            granted = true,
            reason = methodName
        )
    }

    private fun fallback(reason: String): CarVideoFocusResult {
        return CarVideoFocusResult(
            granted = false,
            reason = reason
        )
    }
}
