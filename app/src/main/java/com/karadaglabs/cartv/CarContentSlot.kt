package com.karadaglabs.cartv

import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.FrameLayout
import com.google.android.apps.auto.sdk.CarActivity

/**
 * Fermata / CarStream / aauto.aar hosted content path, from bytecode:
 *
 * CarActivity.onCreate:
 *   super.setContentView(CarUiController.d())  // Gearhead getAppLayout()
 *   g = (ViewGroup) findViewById(CarUiController.c())  // getContentContainerId()
 *
 * CarActivity.setContentView(int):
 *   g.removeAllViews(); inflate(layout, g, true);
 *
 * CarStream MainCarActivity.onCreate:
 *   super.onCreate(bundle);
 *   setContentView(R.layout.activity_car_main); // MATCH_PARENT FrameLayout
 *   fragment replace into that container;
 *   then hideAppHeader.
 *
 * Chrome is applied after app content is in g so Gearhead does not
 * lay out the empty PagedListView as the only pane.
 */
object CarContentSlot {

    const val TAG = "KaradagCarSlot"

    private const val CAR_ACTIVITY_CLASS =
        "com.google.android.apps.auto.sdk.CarActivity"
    private const val SLOT_FIELD = "g"
    private val dumpDelays = longArrayOf(0L, 50L, 250L)

    fun prepareHost(activity: CarActivity) {
        hideHeader(activity)
        hideMenu(activity)
    }

    fun hideHeader(activity: CarActivity) {
        try {
            activity.setIgnoreConfigChanges(0xFFFFFFFF.toInt())
        } catch (exception: Exception) {
            Log.w(TAG, "setIgnoreConfigChanges unavailable", exception)
        }

        try {
            val bar = activity.carUiController.statusBarController
            bar.hideAppHeader()
            bar.hideConnectivityLevel()
        } catch (exception: Exception) {
            Log.w(TAG, "hideAppHeader unavailable", exception)
        }
    }

    fun hideMenu(activity: CarActivity) {
        try {
            activity.carUiController.menuController.hideMenuButton()
        } catch (exception: Exception) {
            Log.w(TAG, "hideMenuButton unavailable", exception)
        }
    }

    fun install(activity: CarActivity): FrameLayout {
        try {
            activity.setContentView(R.layout.car_host_root)
        } catch (exception: Exception) {
            Log.e(
                TAG,
                "setContentView(R.layout.car_host_root) into g failed",
                exception
            )
        }

        var root = hostRootFromSlot(activity)
        if (root == null) {
            root = activity.findViewById(R.id.car_host_root) as? FrameLayout
        }
        if (root == null) {
            Log.w(TAG, "car_host_root missing after inflate; adding FrameLayout to g")
            root = FrameLayout(activity).apply {
                id = R.id.car_host_root
                keepScreenOn = true
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            }
            putInSlot(activity, root)
        }

        root.keepScreenOn = true
        logChosen(activity, root)
        dumpHost(activity, root)
        scheduleDump(activity, root)
        return root
    }

    fun attach(activity: CarActivity, root: View) {
        root.keepScreenOn = true
        val slot = resolveSlot(activity)
        if (root.parent === slot || root.parent?.parent === slot) {
            prepareHost(activity)
            dumpHost(activity, root)
            scheduleDump(activity, root)
            return
        }

        val host = install(activity)
        if (root !== host) {
            (root.parent as? ViewGroup)?.removeView(root)
            fillParent(root)
            host.addView(
                root,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            )
        }
        dumpHost(activity, host)
        scheduleDump(activity, host)
    }

    fun rebind(activity: CarActivity, root: View) {
        prepareHost(activity)
        val slot = resolveSlot(activity)
        val attached =
            root.parent === slot ||
                root.parent?.parent === slot ||
                root.parent != null
        if (!attached) {
            putInSlot(activity, root)
        }
        dumpHost(activity, root)
        scheduleDump(activity, root)
    }

    fun hostWindow(activity: CarActivity): Window {
        return activity.c()
    }

    fun isSurfaceReadyForPlayback(view: View): Boolean {
        return view.visibility == View.VISIBLE &&
            view.width > 0 &&
            view.height > 0
    }

    private fun hostRootFromSlot(activity: CarActivity): FrameLayout? {
        val slot = readCarActivitySlot(activity) ?: return null
        for (index in 0 until slot.childCount) {
            val child = slot.getChildAt(index)
            if (child.id == R.id.car_host_root && child is FrameLayout) {
                return child
            }
        }
        if (slot.childCount == 1) {
            return slot.getChildAt(0) as? FrameLayout
        }
        return null
    }

    private fun putInSlot(activity: CarActivity, root: View) {
        val slot = resolveSlot(activity)
        if (slot == null) {
            Log.e(
                TAG,
                "CarActivity.g missing; setContentView cannot attach hosted UI"
            )
        }

        try {
            (root.parent as? ViewGroup)?.removeView(root)
            fillParent(root)
            activity.setContentView(root)
        } catch (exception: Exception) {
            Log.e(TAG, "CarActivity.setContentView into g failed", exception)
            return
        }

        logChosen(activity, root)
    }

    private fun logChosen(activity: CarActivity, root: View) {
        val attached = resolveSlot(activity)
        Log.d(
            TAG,
            "HOST_CHOSEN_CLASS class=${attached?.javaClass?.name} " +
                "parent=${parentName(root)} " +
                "slotArea=${area(attached)}"
        )
    }

    private fun fillParent(view: View) {
        val params = view.layoutParams
            ?: ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        if (
            params.width != ViewGroup.LayoutParams.MATCH_PARENT ||
            params.height != ViewGroup.LayoutParams.MATCH_PARENT
        ) {
            params.width = ViewGroup.LayoutParams.MATCH_PARENT
            params.height = ViewGroup.LayoutParams.MATCH_PARENT
        }
        view.layoutParams = params
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
        Log.d(
            TAG,
            "content slot fieldG=${fromField?.javaClass?.name} " +
                "containerId=$containerId fromId=${fromId?.javaClass?.name}"
        )
        return fromField ?: fromId
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

    private fun area(view: View?): Int {
        if (view == null) {
            return 0
        }
        return firstPositive(view.width, view.measuredWidth) *
            firstPositive(view.height, view.measuredHeight)
    }

    private fun firstPositive(vararg values: Int): Int {
        return values.firstOrNull { it > 0 } ?: 0
    }

    private fun scheduleDump(activity: CarActivity, root: View) {
        dumpDelays.forEach { delay ->
            root.postDelayed({ dumpHost(activity, root) }, delay)
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
        dumpChildren(slot, "slot")
    }

    private fun dumpChildren(group: ViewGroup?, label: String) {
        if (group == null) {
            return
        }
        for (index in 0 until group.childCount) {
            val child = group.getChildAt(index)
            Log.d(
                TAG,
                "HOST_SLOT_CHILD label=$label i=$index " +
                    "class=${child.javaClass.name} " +
                    "${child.width}x${child.height} " +
                    "vis=${visibilityName(child)}"
            )
        }
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
}

interface CarDestinationHost {
    fun showHostedDestination(destination: ProjectionDestination)
}
