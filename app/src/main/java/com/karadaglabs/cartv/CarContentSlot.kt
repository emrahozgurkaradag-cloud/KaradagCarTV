package com.karadaglabs.cartv

import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.view.Window
import com.google.android.apps.auto.sdk.CarActivity
import java.util.WeakHashMap

/**
 * Fermata / aauto.aar hosted content path, from bytecode:
 *
 * CarActivity.onCreate:
 *   super.setContentView(CarUiController.d())  // Gearhead getAppLayout()
 *   g = (ViewGroup) findViewById(CarUiController.c())  // getContentContainerId()
 *
 * CarActivity.setContentView(View):
 *   g.removeAllViews(); g.addView(view);
 *
 * Fermata MainCarActivity.initCarActivity then:
 *   hideAppHeader(); hideMenuButton();
 *   getWindow() { return c(); }
 *
 * App content stays in field CarActivity.g. It is not moved onto
 * android.R.id.content and Window.setContentView is not called.
 * Gearhead's empty PagedListView is not GONE; hideMenuButton is the
 * public API that keeps that list from being the visible pane.
 * After onStart, CarUiController.a() (Gearhead onStart) can restore
 * chrome, so prepareHost + bind run again.
 */
object CarContentSlot {

    const val TAG = "KaradagCarSlot"

    private const val SLOT_ELEVATION = 64f
    private const val CAR_ACTIVITY_CLASS =
        "com.google.android.apps.auto.sdk.CarActivity"
    private const val SLOT_FIELD = "g"

    private val lastBind = WeakHashMap<View, BindKey>()
    private val hookedSlots = WeakHashMap<ViewGroup, Boolean>()
    private val followupDelays = longArrayOf(0L, 16L, 50L, 100L, 250L)

    fun prepareHost(activity: CarActivity) {
        try {
            activity.setIgnoreConfigChanges(0xFFFFFFFF.toInt())
        } catch (exception: Exception) {
            Log.w(TAG, "setIgnoreConfigChanges unavailable", exception)
        }

        try {
            val ui = activity.carUiController
            ui.statusBarController.hideAppHeader()
            ui.menuController.hideMenuButton()
        } catch (exception: Exception) {
            Log.w(TAG, "hideAppHeader/hideMenuButton unavailable", exception)
        }
    }

    fun attach(activity: CarActivity, root: View) {
        root.keepScreenOn = true
        prepareHost(activity)
        activity.setContentView(root)
        bindIntoSlot(activity, root)
        scheduleFollowup(activity, root)
        dumpHost(activity, root)
    }

    fun rebind(activity: CarActivity, root: View) {
        prepareHost(activity)
        if (root.parent == null) {
            activity.setContentView(root)
        }
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
        val slot = resolveSlot(activity) ?: return
        val appLayout = resolveAppLayout(activity) as? ViewGroup
        val size = hostSize(activity, appLayout, slot)

        val key = BindKey(
            hostIdentity = System.identityHashCode(slot),
            width = size.width,
            height = size.height
        )
        if (root.parent === slot && lastBind[root] == key) {
            promote(slot, root)
            return
        }
        lastBind[root] = key

        ensureChild(slot, root)
        applyHostPixels(slot, size.width, size.height)
        applyHostPixels(root, size.width, size.height)
        unclipAncestors(slot, appLayout)
        promote(slot, root)
        watchSlot(activity, root, slot)
        slot.requestLayout()
        root.requestLayout()

        Log.d(
            TAG,
            "HOST_CHOSEN_CLASS class=${slot.javaClass.name} " +
                "appLayout=${appLayout?.javaClass?.name} " +
                "slotArea=${area(slot)}"
        )
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
                "containerId=$containerId fromId=${fromId?.javaClass?.name}"
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

    private fun hostSize(
        activity: CarActivity,
        appLayout: View?,
        slot: View
    ): HostPixels {
        val window = try {
            hostWindow(activity)
        } catch (_: Exception) {
            null
        }
        val decor = window?.decorView
        val metrics = activity.resources.displayMetrics
        val width = firstPositive(
            slot.width,
            slot.measuredWidth,
            appLayout?.width ?: 0,
            decor?.width ?: 0,
            metrics.widthPixels
        )
        val height = firstPositive(
            slot.height,
            slot.measuredHeight,
            appLayout?.height ?: 0,
            decor?.height ?: 0,
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
        return firstPositive(view.width, view.measuredWidth) *
            firstPositive(view.height, view.measuredHeight)
    }

    private fun ensureChild(slot: ViewGroup, root: View) {
        if (root.parent === slot) {
            return
        }
        (root.parent as? ViewGroup)?.removeView(root)
        if (slot.indexOfChild(root) < 0) {
            slot.removeAllViews()
            slot.addView(
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
        val params = view.layoutParams ?: ViewGroup.LayoutParams(width, height)
        if (params.width == width && params.height == height) {
            return
        }
        params.width = width
        params.height = height
        view.layoutParams = params
    }

    private fun promote(slot: ViewGroup, root: View) {
        slot.elevation = SLOT_ELEVATION
        slot.translationZ = SLOT_ELEVATION
        slot.bringToFront()
        root.elevation = SLOT_ELEVATION
        root.translationZ = SLOT_ELEVATION
        root.bringToFront()
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

    private fun watchSlot(
        activity: CarActivity,
        root: View,
        slot: ViewGroup
    ) {
        if (hookedSlots.containsKey(slot)) {
            return
        }
        hookedSlots[slot] = true
        slot.setOnHierarchyChangeListener(
            object : ViewGroup.OnHierarchyChangeListener {
                override fun onChildViewAdded(parent: View, child: View) {
                    if (child !== root) {
                        parent.post { bindIntoSlot(activity, root) }
                    }
                }

                override fun onChildViewRemoved(parent: View, child: View) {
                    if (child === root) {
                        parent.post { bindIntoSlot(activity, root) }
                    }
                }
            }
        )
    }

    private fun scheduleFollowup(activity: CarActivity, root: View) {
        followupDelays.forEach { delay ->
            root.postDelayed({ bindIntoSlot(activity, root) }, delay)
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
        val windowContent = try {
            activity.findViewById(android.R.id.content) as? ViewGroup
        } catch (_: Exception) {
            null
        }

        Log.d(TAG, "HOST_ACTIVITY_CREATED class=${activity.javaClass.name}")
        Log.d(TAG, "HOST_ROOT_CLASS class=${decor?.javaClass?.name}")
        Log.d(TAG, "HOST_ROOT_PARENT parent=${parentName(decor)}")
        Log.d(
            TAG,
            "HOST_ROOT_SIZE ${decor?.width ?: 0}x${decor?.height ?: 0} " +
                "window=${window?.javaClass?.name}"
        )
        Log.d(
            TAG,
            "HOST_CHILDREN_COUNT count=${(decor as? ViewGroup)?.childCount ?: 0}"
        )
        Log.d(TAG, "NATIVE_LIST_CLASS class=${nativeList?.javaClass?.name}")
        Log.d(
            TAG,
            "NATIVE_LIST_VISIBILITY visibility=${visibilityName(nativeList)}"
        )
        Log.d(TAG, "NATIVE_LIST_PARENT parent=${parentName(nativeList)}")
        Log.d(
            TAG,
            "HOSTED_CONTENT_PARENT parent=${parentName(root)} " +
                "slot=${slot?.javaClass?.name} " +
                "windowContent=${windowContent?.javaClass?.name}"
        )
        Log.d(
            TAG,
            "HOSTED_CONTENT_SIZE ${root.width}x${root.height} " +
                "slot=${slot?.width ?: 0}x${slot?.height ?: 0}"
        )
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
        return view?.parent?.javaClass?.name ?: "null"
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

    private data class HostPixels(val width: Int, val height: Int)

    private data class BindKey(
        val hostIdentity: Int,
        val width: Int,
        val height: Int
    )
}

interface CarDestinationHost {
    fun showHostedDestination(destination: ProjectionDestination)
}
