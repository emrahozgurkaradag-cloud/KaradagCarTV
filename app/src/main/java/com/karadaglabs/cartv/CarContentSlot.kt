package com.karadaglabs.cartv

import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.view.Window
import com.google.android.apps.auto.sdk.CarActivity
import java.util.WeakHashMap

/**
 * Official aauto.aar hosted-content path, taken from bytecode:
 *
 * CarActivity.onCreate:
 *   super.setContentView(CarUiController.d())  // Gearhead getAppLayout()
 *   g = (ViewGroup) findViewById(CarUiController.c())  // getContentContainerId()
 *
 * CarActivity.setContentView(View):
 *   g.removeAllViews(); g.addView(view);
 *
 * That does **not** replace CarActivityHost.getWindow() (gms.car.c.c()).
 * Field CarActivity.g is the content_frame analogue. Gearhead's empty PagedListView
 * lives in the same getAppLayout() and is what paints "Öğe yok".
 *
 * After the official add into `g`, the app root is lifted onto an
 * overlay-capable parent that already exists in the hosted Window:
 * android.R.id.content (FrameLayout from CarActivityHost.setContentView),
 * or the nearest FrameLayout/DrawerLayout ancestor. That is addView on
 * the existing container, not Window.setContentView, so Gearhead's
 * getAppLayout stays as a sibling underneath. LinearLayout parents are
 * never used as overlay hosts. The list is not GONE and the drawer is
 * not closed.
 *
 * Rebind after onStart: CarUiController.a() → Gearhead onStart().
 */
object CarContentSlot {

    const val TAG = "KaradagCarSlot"

    private const val SLOT_ELEVATION = 64f
    private const val CAR_ACTIVITY_CLASS =
        "com.google.android.apps.auto.sdk.CarActivity"
    private const val SLOT_FIELD = "g"

    private val lastBind = WeakHashMap<View, BindKey>()
    private val hookedParents = WeakHashMap<ViewGroup, Boolean>()
    private val followupDelays = longArrayOf(0L, 16L, 50L, 100L, 250L, 500L)

    fun attach(activity: CarActivity, root: View) {
        root.keepScreenOn = true

        try {
            activity.carUiController.statusBarController.hideAppHeader()
        } catch (exception: Exception) {
            Log.w(TAG, "hideAppHeader unavailable", exception)
        }

        activity.setContentView(root)
        bindIntoSlot(activity, root)
        watchHostLayout(activity, root)
        scheduleFollowup(activity, root)
        dumpHost(activity, root)
    }

    fun rebind(activity: CarActivity, root: View) {
        bindIntoSlot(activity, root)
        scheduleFollowup(activity, root)
        dumpHost(activity, root)
    }

    fun hostWindow(activity: CarActivity): Window {
        return activity.c()
    }

    fun isSurfaceReadyForPlayback(view: View): Boolean {
        return view.visibility == View.VISIBLE &&
            view.width > 0 &&
            view.height > 0
    }

    private fun bindIntoSlot(activity: CarActivity, root: View) {
        val slot = resolveSlot(activity)
        val appLayout = resolveAppLayout(activity) as? ViewGroup
        val nativeList = findNativeList(appLayout) ?: findNativeList(decor(activity))
        val windowContent = findWindowContent(activity)
        val host = chooseVisibleHost(
            windowContent,
            slot,
            appLayout,
            nativeList
        ) ?: return
        val size = hostSize(activity, appLayout, host)
        val key = BindKey(
            hostIdentity = System.identityHashCode(host),
            width = size.width,
            height = size.height
        )

        if (root.parent === host && lastBind[root] == key) {
            promote(host, root)
            return
        }

        lastBind[root] = key
        val overlayHost = host !== slot
        ensureChild(host, root, replaceChildren = !overlayHost)
        if (!overlayHost) {
            applyHostPixels(host, size.width, size.height)
        }
        applyHostPixels(root, size.width, size.height)
        unclipAncestors(host, appLayout)
        promote(host, root)
        watchHierarchy(activity, root, host)
        host.requestLayout()
        root.requestLayout()

        Log.d(
            TAG,
            "HOST_CHOSEN_CLASS class=${host.javaClass.name} " +
                "overlay=$overlayHost " +
                "windowContent=${windowContent?.javaClass?.name} " +
                "slot=${slot?.javaClass?.name} " +
                "appLayout=${appLayout?.javaClass?.name} " +
                "list=${nativeList?.javaClass?.name} " +
                "slotArea=${area(slot)} listArea=${area(nativeList)} " +
                "hostArea=${area(host)}"
        )
    }

    private fun chooseVisibleHost(
        windowContent: ViewGroup?,
        slot: ViewGroup?,
        appLayout: ViewGroup?,
        nativeList: View?
    ): ViewGroup? {
        if (canOverlay(windowContent)) {
            return windowContent
        }

        val fromList = overlayAncestor(nativeList)
        if (fromList != null) {
            return fromList
        }

        val fromSlot = overlayAncestor(slot)
        if (fromSlot != null) {
            return fromSlot
        }

        if (canOverlay(appLayout)) {
            return appLayout
        }

        val slotArea = area(slot)
        val appArea = area(appLayout)
        if (
            slot != null &&
            slot.isAttachedToWindow &&
            slotArea > 0 &&
            (appArea == 0 || slotArea * 3 >= appArea)
        ) {
            return slot
        }

        return slot
    }

    private fun findWindowContent(activity: CarActivity): ViewGroup? {
        val fromActivity = try {
            activity.findViewById(android.R.id.content) as? ViewGroup
        } catch (_: Exception) {
            null
        }
        if (fromActivity != null) {
            return fromActivity
        }

        return try {
            hostWindow(activity).decorView.findViewById(android.R.id.content)
                as? ViewGroup
        } catch (_: Exception) {
            null
        }
    }

    private fun overlayAncestor(start: View?): ViewGroup? {
        var node: View? = start?.parent as? View
        while (node != null) {
            if (canOverlay(node)) {
                return node as ViewGroup
            }
            node = node.parent as? View
        }
        return null
    }

    private fun canOverlay(view: View?): Boolean {
        if (view !is ViewGroup) {
            return false
        }

        val name = view.javaClass.name
        if (
            view is android.widget.LinearLayout ||
            name.contains("LinearLayout") ||
            name.contains("Recycler") ||
            name.contains("PagedList") ||
            name.contains("ScrollView")
        ) {
            return false
        }

        return view is android.widget.FrameLayout ||
            view is android.widget.RelativeLayout ||
            name.contains("FrameLayout") ||
            name.contains("ContentFrame") ||
            name.contains("DrawerLayout") ||
            name.contains("ConstraintLayout")
    }

    private fun promote(host: ViewGroup, root: View) {
        root.elevation = SLOT_ELEVATION
        root.translationZ = SLOT_ELEVATION
        root.isClickable = true
        root.bringToFront()
        if (host.id != android.R.id.content) {
            host.elevation = SLOT_ELEVATION
            host.translationZ = SLOT_ELEVATION
            host.bringToFront()
        }
    }

    private fun unclipAncestors(from: View, appLayout: View?) {
        var ancestor: View? = from
        while (ancestor != null) {
            (ancestor as? ViewGroup)?.let { group ->
                group.clipChildren = false
                group.clipToPadding = false
            }
            if (ancestor === appLayout) {
                break
            }
            ancestor = ancestor.parent as? View
        }
    }

    private fun resolveSlot(activity: CarActivity): ViewGroup? {
        val fromField = readCarActivitySlot(activity)
        val containerId = try {
            activity.carUiController.c()
        } catch (_: Exception) {
            0
        }
        val fromId = if (containerId != 0) {
            activity.findViewById(containerId) as? ViewGroup
        } else {
            null
        }

        val slot = fromField ?: fromId
        Log.d(
            TAG,
            "content slot fieldG=${fromField?.javaClass?.name} " +
                "containerId=$containerId " +
                "fromId=${fromId?.javaClass?.name}"
        )
        return slot
    }

    private fun readCarActivitySlot(activity: CarActivity): ViewGroup? {
        var type: Class<*>? = activity.javaClass

        while (type != null && type != Any::class.java) {
            if (type.name == CAR_ACTIVITY_CLASS) {
                return try {
                    val field = type.getDeclaredField(SLOT_FIELD)
                    field.isAccessible = true
                    field.get(activity) as? ViewGroup
                } catch (exception: Exception) {
                    Log.e(TAG, "CarActivity.$SLOT_FIELD unavailable", exception)
                    null
                }
            }
            type = type.superclass
        }

        return null
    }

    private fun resolveAppLayout(activity: CarActivity): View? {
        return try {
            activity.carUiController.d()
        } catch (exception: Exception) {
            Log.w(TAG, "CarUiController.d() getAppLayout unavailable", exception)
            null
        }
    }

    private fun decor(activity: CarActivity): View? {
        return try {
            hostWindow(activity).decorView
        } catch (_: Exception) {
            null
        }
    }

    private fun hostSize(
        activity: CarActivity,
        appLayout: View?,
        host: View
    ): HostPixels {
        val window = try {
            hostWindow(activity)
        } catch (_: Exception) {
            null
        }
        val decor = window?.decorView
        val metrics = activity.resources.displayMetrics

        val width = firstPositive(
            host.width,
            host.measuredWidth,
            decor?.width ?: 0,
            decor?.measuredWidth ?: 0,
            appLayout?.width ?: 0,
            appLayout?.measuredWidth ?: 0,
            window?.attributes?.width ?: 0,
            metrics.widthPixels
        )
        val height = firstPositive(
            host.height,
            host.measuredHeight,
            decor?.height ?: 0,
            decor?.measuredHeight ?: 0,
            appLayout?.height ?: 0,
            appLayout?.measuredHeight ?: 0,
            window?.attributes?.height ?: 0,
            metrics.heightPixels
        )

        return HostPixels(width, height)
    }

    private fun firstPositive(vararg values: Int): Int {
        return values.firstOrNull { it > 0 } ?: 0
    }

    private fun area(view: View?): Int {
        if (view == null) {
            return 0
        }
        val width = firstPositive(view.width, view.measuredWidth)
        val height = firstPositive(view.height, view.measuredHeight)
        return width * height
    }

    private fun ensureChild(
        host: ViewGroup,
        root: View,
        replaceChildren: Boolean
    ) {
        if (root.parent === host) {
            return
        }

        (root.parent as? ViewGroup)?.removeView(root)

        if (replaceChildren) {
            host.removeAllViews()
        }

        if (host.indexOfChild(root) < 0) {
            host.addView(
                root,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            )
        }
    }

    private fun applyHostPixels(view: View, width: Int, height: Int) {
        if (width <= 0 || height <= 0) {
            return
        }

        view.minimumWidth = width
        view.minimumHeight = height

        val params = view.layoutParams
            ?: ViewGroup.LayoutParams(width, height)
        if (params.width == width && params.height == height) {
            return
        }

        params.width = width
        params.height = height
        view.layoutParams = params
    }

    private fun watchHostLayout(activity: CarActivity, root: View) {
        val windowView = decor(activity) ?: root

        windowView.viewTreeObserver.addOnGlobalLayoutListener {
            bindIntoSlot(activity, root)
        }

        scheduleFollowup(activity, root)
    }

    private fun watchHierarchy(
        activity: CarActivity,
        root: View,
        host: ViewGroup
    ) {
        if (hookedParents.containsKey(host)) {
            return
        }
        hookedParents[host] = true

        host.setOnHierarchyChangeListener(
            object : ViewGroup.OnHierarchyChangeListener {
                override fun onChildViewAdded(parent: View, child: View) {
                    if (child === root) {
                        return
                    }
                    root.post {
                        if (root.parent === parent) {
                            root.bringToFront()
                        } else {
                            bindIntoSlot(activity, root)
                        }
                    }
                }

                override fun onChildViewRemoved(parent: View, child: View) {
                    if (child !== root) {
                        return
                    }
                    parent.post { bindIntoSlot(activity, root) }
                }
            }
        )
    }

    private fun scheduleFollowup(activity: CarActivity, root: View) {
        followupDelays.forEach { delay ->
            root.postDelayed(
                { bindIntoSlot(activity, root) },
                delay
            )
        }
    }

    private fun dumpHost(activity: CarActivity, root: View) {
        val window = try {
            hostWindow(activity)
        } catch (_: Exception) {
            null
        }
        val decor = window?.decorView
        val slot = resolveSlot(activity)
        val appLayout = resolveAppLayout(activity)
        val nativeList = findNativeList(appLayout) ?: findNativeList(decor)

        Log.d(TAG, "HOST_ACTIVITY_CREATED class=${activity.javaClass.name}")
        Log.d(
            TAG,
            "HOST_ROOT_CLASS class=${decor?.javaClass?.name}"
        )
        Log.d(
            TAG,
            "HOST_ROOT_PARENT parent=${parentName(decor)}"
        )
        Log.d(
            TAG,
            "HOST_ROOT_SIZE ${decor?.width ?: 0}x${decor?.height ?: 0} " +
                "window=${window?.javaClass?.name}"
        )
        Log.d(
            TAG,
            "HOST_CHILDREN_COUNT count=${(decor as? ViewGroup)?.childCount ?: 0}"
        )
        Log.d(
            TAG,
            "NATIVE_LIST_CLASS class=${nativeList?.javaClass?.name}"
        )
        Log.d(
            TAG,
            "NATIVE_LIST_VISIBILITY visibility=${visibilityName(nativeList)}"
        )
        Log.d(
            TAG,
            "NATIVE_LIST_PARENT parent=${parentName(nativeList)}"
        )
        Log.d(
            TAG,
            "HOSTED_CONTENT_PARENT parent=${parentName(root)} " +
                "slot=${slot?.javaClass?.name} " +
                "appLayout=${appLayout?.javaClass?.name}"
        )
        Log.d(
            TAG,
            "HOSTED_CONTENT_SIZE ${root.width}x${root.height} " +
                "slot=${slot?.width ?: 0}x${slot?.height ?: 0}"
        )
        dumpChildren("HOST_APP_LAYOUT", appLayout)
        dumpChildren("HOST_WINDOW_CONTENT", findWindowContent(activity))
    }

    private fun dumpChildren(label: String, view: View?) {
        val group = view as? ViewGroup ?: return
        val parts = ArrayList<String>(group.childCount)
        for (index in 0 until group.childCount) {
            val child = group.getChildAt(index)
            parts.add(
                "${child.javaClass.simpleName}:${child.width}x${child.height}:" +
                    visibilityName(child)
            )
        }
        Log.d(TAG, "$label children=${parts.joinToString(",")}")
    }

    private fun findNativeList(root: View?): View? {
        if (root == null) {
            return null
        }

        val name = root.javaClass.name
        if (
            name.contains("PagedListView") ||
            name.contains("CarRecyclerView") ||
            name.contains("PagedRecycler")
        ) {
            return root
        }

        if (root is ViewGroup) {
            for (index in 0 until root.childCount) {
                findNativeList(root.getChildAt(index))?.let { return it }
            }
        }

        return null
    }

    private fun parentName(view: View?): String {
        val parent = view?.parent
        return parent?.javaClass?.name ?: "null"
    }

    private fun visibilityName(view: View?): String {
        return when (view?.visibility) {
            View.VISIBLE -> "VISIBLE"
            View.INVISIBLE -> "INVISIBLE"
            View.GONE -> "GONE"
            null -> "missing"
            else -> view.visibility.toString()
        }
    }

    private data class HostPixels(
        val width: Int,
        val height: Int
    )

    private data class BindKey(
        val hostIdentity: Int,
        val width: Int,
        val height: Int
    )
}

interface CarDestinationHost {
    fun showHostedDestination(destination: ProjectionDestination)
}
