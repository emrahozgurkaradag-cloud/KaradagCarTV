package com.karadaglabs.cartv

import android.os.Handler
import android.os.Looper
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
 *   fragment add+detach commitNow, then attach commit();
 *   then hideAppHeader.
 *
 * Fermata initCarActivity hides chrome before setContentView, and
 * MainActivityDelegate.setContentView runs after media connect —
 * after CarActivity.onStart / Gearhead CarUiController.a().
 * Inflating car_host_root in onCreate lets that restore cover g.
 *
 * CarActivity.onStart runs gms.car.e.onStart (execPendingActions)
 * and then Gearhead CarUiController.a(). setContentView(car_host_root)
 * and fragment attach happen after super.onStart so the hosted page
 * is created in g after chrome restore.
 *
 * CarActivity.setContentView always writes field g. If Gearhead
 * onStart replaces the window layout, g can point at a detached
 * view. bindLiveSlotField reassigns g to the live window container
 * from the same public ID, then setContentView inflates into that
 * official slot. Content is never addView'd into a different
 * container than g.
 *
 * keepInSlot retries that put on the main Handler. View.postDelayed
 * only drains after attach, so a detached page would never retry.
 *
 * CarStream never setContentView's the fragment view. The MATCH_PARENT
 * FrameLayout stays in g; the hosted page stays a child of that
 * FrameLayout. Putting the fragment view into g removes car_host_root
 * and leaves FragmentManager holding a container that is not in the
 * window.
 */
object CarContentSlot {

    const val TAG = "KaradagCarSlot"

    private const val CAR_ACTIVITY_CLASS =
        "com.google.android.apps.auto.sdk.CarActivity"
    private const val SLOT_FIELD = "g"
    private val dumpDelays = longArrayOf(0L, 50L, 250L)
    private val attachDelays = longArrayOf(0L, 16L, 50L, 250L, 500L, 1000L, 2000L)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val keepCallbacks = mutableMapOf<Int, MutableList<Runnable>>()

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
            bar.hideTitle()
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
        bindLiveSlotField(activity)
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
        bindLiveSlotField(activity)
        val slot = resolveSlot(activity)
        if (slot != null && isUnder(slot, root)) {
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

    fun putPage(activity: CarActivity, page: View): View {
        page.keepScreenOn = true
        fillParent(page)
        putInSlot(activity, page)
        dumpHost(activity, page)
        scheduleDump(activity, page)
        return page
    }

    fun keepInSlot(activity: CarActivity, page: View) {
        stopKeep(activity)
        val token = System.identityHashCode(activity)
        val posted = mutableListOf<Runnable>()
        keepCallbacks[token] = posted
        attachDelays.forEach { delay ->
            val runnable = Runnable {
                try {
                    keepOnce(activity, page, delay)
                } catch (exception: Exception) {
                    Log.e(TAG, "HOST_SLOT_KEEP failed delay=$delay", exception)
                }
            }
            posted.add(runnable)
            mainHandler.postDelayed(runnable, delay)
        }
    }

    fun stopKeep(activity: CarActivity) {
        val token = System.identityHashCode(activity)
        keepCallbacks.remove(token)?.forEach { mainHandler.removeCallbacks(it) }
    }

    fun isInLiveSlot(activity: CarActivity, view: View): Boolean {
        bindLiveSlotField(activity)
        val slot = resolveSlot(activity) ?: return false
        return slot.isAttachedToWindow && isUnder(slot, view)
    }

    fun rebind(activity: CarActivity, root: View) {
        prepareHost(activity)
        bindLiveSlotField(activity)
        val slot = resolveSlot(activity)
        val attached = slot != null &&
            slot.isAttachedToWindow &&
            isUnder(slot, root)
        if (!attached) {
            putHostRoot(activity, root)
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

    private fun keepOnce(activity: CarActivity, page: View, delay: Long) {
        bindLiveSlotField(activity)
        val slot = resolveSlot(activity)
        val container = ancestorHostRoot(page)
        val inSlot = slot != null &&
            slot.isAttachedToWindow &&
            isUnder(slot, page)
        Log.d(
            TAG,
            "HOST_SLOT_KEEP delay=$delay inSlot=$inSlot " +
                "slotAttached=${slot?.isAttachedToWindow == true} " +
                "pageAttached=${page.isAttachedToWindow} " +
                "container=${container?.javaClass?.name}"
        )
        if (!inSlot) {
            putHostRoot(activity, page)
            return
        }
        prepareHost(activity)
        Log.d(TAG, "HOST_SLOT_ATTACHED")
        dumpHost(activity, page)
    }

    private fun putHostRoot(activity: CarActivity, page: View) {
        val existingRoot = ancestorHostRoot(page)
        val container = when {
            existingRoot != null -> existingRoot
            page.id == R.id.car_host_root && page is FrameLayout -> page
            else -> hostRootFromSlot(activity) ?: install(activity)
        }
        if (page !== container && !isUnder(container, page)) {
            (page.parent as? ViewGroup)?.removeView(page)
            fillParent(page)
            container.addView(
                page,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            )
        }
        Log.d(
            TAG,
            "HOST_SLOT_CONTAINER class=${container.javaClass.name} " +
                "id=${container.id} children=${container.childCount}"
        )
        putInSlot(activity, container)
        dumpHost(activity, page)
        scheduleDump(activity, page)
    }

    private fun ancestorHostRoot(view: View): FrameLayout? {
        var current: View? = view
        while (current != null) {
            if (current.id == R.id.car_host_root && current is FrameLayout) {
                return current
            }
            current = current.parent as? View
        }
        return null
    }

    private fun hostRootFromSlot(activity: CarActivity): FrameLayout? {
        val slot = resolveSlot(activity) ?: return null
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
        bindLiveSlotField(activity)
        if (readCarActivitySlot(activity) == null) {
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
        val fromId = contentContainerFromWindow(activity)
        val fieldAttached = fromField?.isAttachedToWindow == true
        val idAttached = fromId?.isAttachedToWindow == true
        Log.d(
            TAG,
            "HOST_SLOT_LIVE same=${fromField === fromId} " +
                "fieldAttached=$fieldAttached idAttached=$idAttached " +
                "fieldG=${fromField?.javaClass?.name} " +
                "fromId=${fromId?.javaClass?.name}"
        )
        return when {
            idAttached && !fieldAttached -> fromId
            idAttached && fromId !== fromField -> fromId
            fieldAttached -> fromField
            fromId != null -> fromId
            else -> fromField
        }
    }

    private fun contentContainerFromWindow(activity: CarActivity): ViewGroup? {
        val containerId = try {
            activity.carUiController.c()
        } catch (_: Exception) {
            0
        }
        if (containerId == 0) {
            return null
        }
        return activity.findViewById(containerId) as? ViewGroup
    }

    private fun bindLiveSlotField(activity: CarActivity) {
        val live = contentContainerFromWindow(activity) ?: return
        val field = readCarActivitySlot(activity)
        if (field === live && live.isAttachedToWindow) {
            return
        }
        if (!live.isAttachedToWindow && field?.isAttachedToWindow == true) {
            return
        }
        if (writeCarActivitySlot(activity, live)) {
            Log.d(
                TAG,
                "HOST_SLOT_REBOUND attached=${live.isAttachedToWindow} " +
                    "class=${live.javaClass.name}"
            )
        }
    }

    private fun isUnder(parent: ViewGroup, child: View): Boolean {
        var current: View? = child
        while (current != null) {
            if (current === parent) {
                return true
            }
            current = current.parent as? View
        }
        return false
    }

    private fun readCarActivitySlot(activity: CarActivity): ViewGroup? {
        return carActivitySlotField(activity)?.let { field ->
            try {
                field.get(activity) as? ViewGroup
            } catch (exception: Exception) {
                Log.e(TAG, "CarActivity.$SLOT_FIELD unavailable", exception)
                null
            }
        }
    }

    private fun writeCarActivitySlot(
        activity: CarActivity,
        slot: ViewGroup
    ): Boolean {
        val field = carActivitySlotField(activity) ?: return false
        return try {
            field.set(activity, slot)
            true
        } catch (exception: Exception) {
            Log.e(TAG, "CarActivity.$SLOT_FIELD write failed", exception)
            false
        }
    }

    private fun carActivitySlotField(
        activity: CarActivity
    ): java.lang.reflect.Field? {
        var type: Class<*>? = activity.javaClass
        while (type != null && type != Any::class.java) {
            if (type.name == CAR_ACTIVITY_CLASS) {
                return try {
                    val field = type.getDeclaredField(SLOT_FIELD)
                    field.isAccessible = true
                    field
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
            mainHandler.postDelayed({ dumpHost(activity, root) }, delay)
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
                "slot=${slot?.width ?: 0}x${slot?.height ?: 0} " +
                "slotAttached=${slot?.isAttachedToWindow == true} " +
                "rootAttached=${root.isAttachedToWindow}"
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

/**
 * Media browse and the hosted CarActivity run in the same process.
 * Live TV / YouTube / Google taps on the media session must switch
 * the already-bound projection UI instead of creating a Now Playing
 * playlist.
 */
object BoundCarHost {

    @Volatile
    private var current: CarDestinationHost? = null

    @Volatile
    private var pending: ProjectionDestination? = null

    fun bind(host: CarDestinationHost) {
        current = host
    }

    fun unbind(host: CarDestinationHost) {
        if (current === host) {
            current = null
        }
    }

    fun show(destination: ProjectionDestination): Boolean {
        pending = destination
        val bound = current
        Handler(Looper.getMainLooper()).post {
            val live = current ?: CarVideoService.hostedActivity()
            if (live != null) {
                live.showHostedDestination(destination)
            } else {
                Log.i(
                    CarContentSlot.TAG,
                    "HOST_SLOT_PENDING destination=${destination.name}"
                )
            }
        }
        if (bound != null) {
            return true
        }
        return CarVideoService.hostedActivity() != null
    }

    fun consumePending(host: CarDestinationHost): ProjectionDestination? {
        val destination = pending ?: return null
        pending = null
        Log.d(
            CarContentSlot.TAG,
            "HOST_SLOT_PENDING_CONSUMED destination=${destination.name}"
        )
        host.showHostedDestination(destination)
        return destination
    }
}
