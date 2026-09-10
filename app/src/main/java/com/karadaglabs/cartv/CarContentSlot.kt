package com.karadaglabs.cartv

import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
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
 * App content is a sibling of Gearhead chrome, including the empty
 * PagedListView that shows "Öğe yok". CarActivity.onStart then calls
 * CarUiController.a() → Gearhead onStart(), which rebinds that chrome.
 *
 * This binder uses those real fields/methods:
 *   CarActivity.g : ViewGroup
 *   CarUiController.c() : int getContentContainerId
 *   CarUiController.d() : View getAppLayout
 *   gms.car.c.c() : Window
 *
 * It puts the app root into `g`, sizes `g` to the hosted window's
 * real pixels, and bringToFront() so the native empty list is no
 * longer the top sibling. It does not GONE the list, does not call
 * Window.setContentView, and does not close the drawer.
 */
object CarContentSlot {

    const val TAG = "KaradagCarSlot"

    private const val SLOT_ELEVATION = 64f
    private const val CAR_ACTIVITY_CLASS =
        "com.google.android.apps.auto.sdk.CarActivity"
    private const val SLOT_FIELD = "g"

    private val lastHostPixels = WeakHashMap<View, HostPixels>()

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
        dumpHost(activity, root)
    }

    fun rebind(activity: CarActivity, root: View) {
        bindIntoSlot(activity, root)
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
        val appLayout = resolveAppLayout(activity)
        val size = hostSize(activity, appLayout)
        val alreadyBound = root.parent === slot && lastHostPixels[root] == size

        if (alreadyBound) {
            slot.elevation = SLOT_ELEVATION
            slot.translationZ = SLOT_ELEVATION
            slot.bringToFront()
            root.bringToFront()
            return
        }

        lastHostPixels[root] = size

        ensureChild(slot, root)
        applyHostPixels(slot, size.width, size.height)
        applyHostPixels(root, size.width, size.height)

        var ancestor: View? = slot
        while (ancestor != null && ancestor !== appLayout) {
            (ancestor.parent as? ViewGroup)?.let { parent ->
                parent.clipChildren = false
                parent.clipToPadding = false
            }
            applyHostPixels(ancestor, size.width, size.height)
            ancestor = ancestor.parent as? View
        }

        if (appLayout != null) {
            applyHostPixels(appLayout, size.width, size.height)
            (appLayout as? ViewGroup)?.clipChildren = false
            (appLayout as? ViewGroup)?.clipToPadding = false
        }

        slot.elevation = SLOT_ELEVATION
        slot.translationZ = SLOT_ELEVATION
        slot.isClickable = true
        slot.bringToFront()
        root.bringToFront()
        slot.requestLayout()
        root.requestLayout()
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

    private fun hostSize(
        activity: CarActivity,
        appLayout: View?
    ): HostPixels {
        val window = try {
            hostWindow(activity)
        } catch (_: Exception) {
            null
        }
        val decor = window?.decorView
        val metrics = activity.resources.displayMetrics

        val width = firstPositive(
            decor?.width ?: 0,
            decor?.measuredWidth ?: 0,
            appLayout?.width ?: 0,
            appLayout?.measuredWidth ?: 0,
            window?.attributes?.width ?: 0,
            metrics.widthPixels
        )
        val height = firstPositive(
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
        val windowView = try {
            hostWindow(activity).decorView
        } catch (_: Exception) {
            root
        }

        windowView.viewTreeObserver.addOnGlobalLayoutListener(
            object : ViewTreeObserver.OnGlobalLayoutListener {
                override fun onGlobalLayout() {
                    if (root.parent == null) {
                        windowView.viewTreeObserver.removeOnGlobalLayoutListener(this)
                        return
                    }
                    bindIntoSlot(activity, root)
                }
            }
        )

        root.post { bindIntoSlot(activity, root) }
    }

    private fun dumpHost(activity: CarActivity, root: View) {
        val window = try {
            hostWindow(activity)
        } catch (_: Exception) {
            null
        }
        val decor = window?.decorView
        val slot = resolveSlot(activity)
        val nativeList = findNativeList(decor)

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
                "slot=${slot?.javaClass?.name}"
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
}

interface CarDestinationHost {
    fun showHostedDestination(destination: ProjectionDestination)
}
