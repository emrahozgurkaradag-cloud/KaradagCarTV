package com.karadaglabs.cartv

import android.content.Intent
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.TextView
import com.google.android.apps.auto.sdk.CarActivity
import java.lang.ref.WeakReference
import java.lang.reflect.Method
import java.util.Collections
import java.util.WeakHashMap

/**
 * aauto.aar CarActivityHost helpers.
 *
 * Bytecode proof for the car-screen "Öğe yok" text:
 *
 * 1. aauto.aar layout [car_list_item_empty.xml] inflates a full-screen
 *    LinearLayout with TextView @id/title. Gearhead writes the localized
 *    empty-list string ("Öğe yok") into that title when the native
 *    PagedListView adapter count is 0.
 *
 * 2. [CarActivity.onCreate] always calls
 *    super.setContentView(CarUiController.d()) where d() reflects
 *    ICarUiController.getAppLayout(). Field g is
 *    findViewById(getContentContainerId()). App [setContentView(View)]
 *    is g.removeAllViews(); g.addView(view) — it does not replace the
 *    sibling Gearhead PagedListView.
 *
 * 3. [CarActivity.onStart] calls CarUiController.a() which reflects
 *    ICarUiController.onStart(). That host onStart re-shows the native
 *    menu/drawer. MenuController.a() logs "onDrawerOpening" and binds
 *    the MenuAdapter into PagedListView. An empty adapter (or a list
 *    restored before our adapter is attached) renders car_list_item_empty.
 *
 * 4. If g is WRAP_CONTENT, a MATCH_PARENT child with a weight-1
 *    SurfaceView/WebView collapses to 0 height. The menu bar remains
 *    visible and the Gearhead empty list fills the rest of the window.
 */
object HostedCarWindow {

    private const val TAG = "KaradagCarHost"
    private const val HOSTED_ROOT_TAG = "karadag_hosted_root"

    private val guards =
        Collections.synchronizedMap(WeakHashMap<CarActivity, ChromeGuard>())

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
        view.tag = HOSTED_ROOT_TAG
        val fill = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        view.layoutParams = fill
        activity.setContentView(view)
        logView("setContentView", view)
        installChromeGuard(activity, view)
        revealHostedContent(activity, view)
        view.post {
            revealHostedContent(activity, view)
            logView("setContentView.posted", view)
        }
    }

    fun revealHostedContent(
        activity: CarActivity,
        hosted: View? = hostedRoot(activity)
    ) {
        val root = hosted ?: hostedRoot(activity) ?: return
        forceFillHostContainer(activity, root)
        suppressHostPagedLists(activity)
        root.bringToFront()
        try {
            root.elevation = 64f
            root.translationZ = 64f
        } catch (_: Throwable) {
        }
        logHostChrome(activity, root)
    }

    fun installChromeGuard(
        activity: CarActivity,
        hosted: View
    ) {
        guards.remove(activity)?.detach()
        val guard = ChromeGuard(activity, hosted)
        guards[activity] = guard
        guard.attach()
    }

    /**
     * Gearhead chrome inflates [PagedListView]. With no MenuAdapter
     * items that list shows the localized empty placeholder
     * ("Öğe yok"). Hide those host lists so setContentView content
     * is what the car screen displays. CarUiController.onStart can
     * make the list visible again, so this is also called from the
     * chrome guard on every layout / hierarchy change.
     */
    fun suppressHostPagedLists(hosted: Any?) {
        val root = windowDecorView(hosted) ?: return
        val hostedRoot = hostedRoot(hosted as? CarActivity)
        var hidden = 0

        walk(root) { child ->
            if (hostedRoot != null && isDescendantOf(child, hostedRoot)) {
                return@walk
            }

            if (isNativeEmptyList(child)) {
                val was = child.visibility
                if (was != View.GONE) {
                    child.visibility = View.GONE
                    hidden += 1
                    Log.d(
                        TAG,
                        "hid host list view ${child.javaClass.name} " +
                            "was=${visibilityName(was)}"
                    )
                }
                val params = child.layoutParams
                if (params != null && (params.width != 0 || params.height != 0)) {
                    params.width = 0
                    params.height = 0
                    child.layoutParams = params
                }
            }
        }

        if (hidden > 0) {
            Log.d(TAG, "suppressHostPagedLists hidden=$hidden")
        }
    }

    fun logHostChrome(
        activity: CarActivity,
        hosted: View?
    ) {
        val decor = windowDecorView(activity)
        val childCount = (decor as? ViewGroup)?.childCount ?: -1
        Log.d(TAG, "CAR HOST ROOT CHILDREN=$childCount")

        var native = "ABSENT"
        if (decor != null) {
            walk(decor) { child ->
                if (hosted != null && isDescendantOf(child, hosted)) {
                    return@walk
                }
                if (isNativeEmptyList(child)) {
                    native = visibilityName(child.visibility)
                }
            }
        }
        Log.d(TAG, "CAR NATIVE LIST VISIBILITY=$native")
        logView("hostedRoot", hosted)
        logView("contentParent", hosted?.parent as? View)
    }

    private fun forceFillHostContainer(
        activity: CarActivity,
        hosted: View
    ) {
        val metrics = activity.resources.displayMetrics
        val width = metrics.widthPixels.coerceAtLeast(1)
        val height = metrics.heightPixels.coerceAtLeast(1)

        var current: View? = hosted
        var depth = 0
        while (current != null && depth < 16) {
            fillView(current, width, height)
            val parent = current.parent as? ViewGroup
            parent?.let { group ->
                fillView(group, width, height)
                for (index in 0 until group.childCount) {
                    val sibling = group.getChildAt(index)
                    if (
                        sibling !== current &&
                        !isDescendantOf(sibling, hosted) &&
                        isNativeEmptyList(sibling)
                    ) {
                        sibling.visibility = View.GONE
                    }
                }
            }
            current = parent
            depth += 1
        }

        if (hosted.width <= 0 || hosted.height <= 0) {
            Log.w(
                TAG,
                "hosted content was 0x0, forced display ${width}x${height}"
            )
        }
    }

    private fun fillView(
        view: View,
        minWidth: Int,
        minHeight: Int
    ) {
        if (view.minimumWidth < minWidth) {
            view.minimumWidth = minWidth
        }
        if (view.minimumHeight < minHeight) {
            view.minimumHeight = minHeight
        }
        val params = view.layoutParams ?: return
        if (
            params.width != ViewGroup.LayoutParams.MATCH_PARENT ||
            params.height != ViewGroup.LayoutParams.MATCH_PARENT
        ) {
            params.width = ViewGroup.LayoutParams.MATCH_PARENT
            params.height = ViewGroup.LayoutParams.MATCH_PARENT
            view.layoutParams = params
        }
    }

    private fun hostedRoot(activity: CarActivity?): View? {
        if (activity == null) {
            return null
        }

        guards[activity]?.hostedView()?.let { return it }

        val decor = windowDecorView(activity) ?: return null
        var found: View? = null
        walk(decor) { child ->
            if (child.tag == HOSTED_ROOT_TAG) {
                found = child
            }
        }
        return found
    }

    private fun isNativeEmptyList(view: View): Boolean {
        val name = view.javaClass.name
        if (
            name.contains("PagedListView") ||
            name.contains("PagedRecycler") ||
            name.contains("PagedScrollBar") ||
            name.endsWith("car_list_item_empty")
        ) {
            return true
        }

        if (view is TextView) {
            val text = view.text?.toString().orEmpty()
            if (
                text.equals("Öğe yok", ignoreCase = true) ||
                text.equals("No items", ignoreCase = true) ||
                text.equals("No item", ignoreCase = true)
            ) {
                return true
            }
        }

        return false
    }

    private fun isDescendantOf(
        child: View,
        ancestor: View
    ): Boolean {
        var current: View? = child
        while (current != null) {
            if (current === ancestor) {
                return true
            }
            current = current.parent as? View
        }
        return false
    }

    private fun windowDecorView(hosted: Any?): View? {
        if (hosted == null) {
            return null
        }

        return try {
            val window = findMethod(hosted, "getWindow")?.invoke(hosted)
                ?: findHost(hosted)?.let { host ->
                    findMethod(host, "getWindow")?.invoke(host)
                }
                ?: return null

            window.javaClass.methods.firstOrNull { method ->
                method.name == "getDecorView" && method.parameterTypes.isEmpty()
            }?.invoke(window) as? View
        } catch (_: Throwable) {
            null
        }
    }

    private fun walk(
        view: View,
        visitor: (View) -> Unit
    ) {
        visitor(view)
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                walk(view.getChildAt(index), visitor)
            }
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

    private class ChromeGuard(
        activity: CarActivity,
        hosted: View
    ) : ViewTreeObserver.OnGlobalLayoutListener,
        ViewGroup.OnHierarchyChangeListener {

        private val activityRef = WeakReference(activity)
        private val hostedRef = WeakReference(hosted)
        private var decor: ViewGroup? = null
        private var observer: ViewTreeObserver? = null

        fun hostedView(): View? = hostedRef.get()

        fun attach() {
            val activity = activityRef.get() ?: return
            val root = windowDecorView(activity) as? ViewGroup ?: return
            decor = root
            root.setOnHierarchyChangeListener(this)
            val tree = root.viewTreeObserver
            observer = tree
            if (tree.isAlive) {
                tree.addOnGlobalLayoutListener(this)
            }
        }

        fun detach() {
            try {
                decor?.setOnHierarchyChangeListener(null)
                val tree = observer
                if (tree != null && tree.isAlive) {
                    tree.removeOnGlobalLayoutListener(this)
                }
            } catch (_: Throwable) {
            }
            observer = null
            decor = null
        }

        override fun onGlobalLayout() {
            reassert()
        }

        override fun onChildViewAdded(
            parent: View?,
            child: View?
        ) {
            reassert()
        }

        override fun onChildViewRemoved(
            parent: View?,
            child: View?
        ) {
            reassert()
        }

        private fun reassert() {
            val activity = activityRef.get() ?: return
            val hosted = hostedRef.get() ?: return
            suppressHostPagedLists(activity)
            forceFillHostContainer(activity, hosted)
        }
    }
}
