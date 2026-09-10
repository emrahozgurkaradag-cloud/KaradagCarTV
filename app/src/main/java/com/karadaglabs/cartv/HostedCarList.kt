package com.karadaglabs.cartv

import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.apps.auto.sdk.CarActivity
import com.google.android.apps.auto.sdk.ui.PagedListView
import java.util.WeakHashMap

/**
 * Puts Live TV / YouTube / Google rows on the real aauto.aar
 * PagedListView in CarActivity.g — the same widget Gearhead uses
 * for the empty "Öğe yok" placeholder.
 */
object HostedCarList {

    private const val TAG = "KaradagCarList"
    private const val ROW_TAG = "karadag_host_rows"
    private val mainHandler = Handler(Looper.getMainLooper())
    private val bindDelays = longArrayOf(0L, 50L, 250L, 500L, 1000L, 2000L)
    private val pending = WeakHashMap<CarActivity, PendingBind>()

    private class PendingBind(
        val extra: View?,
        val rows: List<Row>,
        val onClick: (Row) -> Unit
    )

    data class Row(
        val key: String,
        val title: String
    )

    fun attach(
        listView: View,
        rows: List<Row>,
        onClick: (Row) -> Unit
    ) {
        val paged = listView as? PagedListView
        if (paged != null) {
            paged.isFocusable = true
            paged.isFocusableInTouchMode = true
            paged.descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
            try {
                val existing = paged.adapter as? Adapter
                if (existing != null) {
                    existing.replace(rows, onClick)
                } else {
                    paged.setAdapter(Adapter(rows, onClick))
                }
                paged.requestFocus()
            } catch (exception: Exception) {
                Log.e(TAG, "HOST_LIST_ATTACH failed", exception)
            }
            // PagedListView is a FrameLayout. Gearhead's empty
            // placeholder lives in the same widget; put the real
            // YouTube / Google / Live TV rows on top of it.
            overlayRows(paged, rows, onClick)
            return
        }
        val group = when {
            listView.javaClass.name.contains("CarRecyclerView") ->
                listView.parent as? ViewGroup
            else -> listView as? ViewGroup
        } ?: return
        overlayRows(group, rows, onClick)
    }

    fun bindActivity(
        activity: CarActivity,
        extra: View?,
        rows: List<Row>,
        onClick: (Row) -> Unit
    ) {
        pending[activity] = PendingBind(extra, rows, onClick)
        bindNow(activity)
        bindDelays.forEach { delay ->
            mainHandler.postDelayed({ bindNow(activity) }, delay)
        }
    }

    fun showInSlot(activity: CarActivity, page: View?) {
        val overlay = page ?: return
        val liveRoot = try {
            activity.findViewById(R.id.car_host_root) as? ViewGroup
        } catch (_: Exception) {
            null
        }
        if (
            liveRoot != null &&
            liveRoot.isAttachedToWindow &&
            isUnder(liveRoot, overlay)
        ) {
            overlay.visibility = View.VISIBLE
            Log.d(
                TAG,
                "HOST_LIST_OVERLAY class=${liveRoot.javaClass.name} " +
                    "${liveRoot.width}x${liveRoot.height}"
            )
            return
        }
        val container = liveRoot?.takeIf { it.isAttachedToWindow }
            ?: visibleHost(activity)
            ?: contentSlot(activity)
            ?: return
        val params = overlay.layoutParams
            ?: FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        params.width = ViewGroup.LayoutParams.MATCH_PARENT
        params.height = ViewGroup.LayoutParams.MATCH_PARENT
        overlay.layoutParams = params
        if (overlay.parent !== container) {
            (overlay.parent as? ViewGroup)?.removeView(overlay)
            container.addView(overlay, params)
        }
        overlay.visibility = View.VISIBLE
        Log.d(
            TAG,
            "HOST_LIST_OVERLAY class=${container.javaClass.name} " +
                "${container.width}x${container.height}"
        )
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

    private fun bindNow(activity: CarActivity) {
        val bound = pending[activity] ?: return
        val seen = HashSet<View>()
        hostRoots(activity, bound.extra).forEach { root ->
            collectHostLists(root).forEach { host ->
                if (seen.add(host) && shouldFill(host)) {
                    attach(host, bound.rows, bound.onClick)
                }
            }
            emptyHost(root)?.let { host ->
                if (seen.add(host)) {
                    attach(host, bound.rows, bound.onClick)
                }
            }
            relabelEmpty(root, bound.rows, bound.onClick)
        }
        Log.d(TAG, "HOST_LIST_BOUND count=${seen.size} rows=${bound.rows.size}")
    }

    private fun shouldFill(host: View): Boolean {
        val paged = host as? PagedListView
        if (paged != null) {
            return try {
                val adapter = paged.adapter
                adapter == null ||
                    adapter is Adapter ||
                    adapter.itemCount == 0
            } catch (_: Exception) {
                true
            }
        }
        return host is ViewGroup
    }

    private fun hostRoots(activity: CarActivity, extra: View?): List<View> {
        val roots = ArrayList<View>(5)
        extra?.let { roots.add(it) }
        contentSlot(activity)?.let { roots.add(it) }
        appLayout(activity)?.let { roots.add(it) }
        windowRoot(activity)?.let { roots.add(it) }
        return roots
    }

    private fun visibleHost(activity: CarActivity): ViewGroup? {
        hostRoots(activity, null).forEach { root ->
            emptyHost(root)?.let { empty ->
                if (empty.isAttachedToWindow && empty.visibility == View.VISIBLE) {
                    return empty
                }
            }
        }
        val lists = ArrayList<ViewGroup>()
        hostRoots(activity, null).forEach { root ->
            collectHostLists(root).forEach { host ->
                (host as? ViewGroup)?.let { lists.add(it) }
            }
        }
        return lists
            .filter { list ->
                list.isAttachedToWindow &&
                    list.visibility == View.VISIBLE &&
                    list.width > 0 &&
                    list.height > 0
            }
            .maxByOrNull { list -> list.width * list.height }
            ?: contentSlot(activity)
    }

    private fun contentSlot(activity: CarActivity): ViewGroup? {
        return try {
            val id = activity.carUiController.c()
            activity.findViewById(id) as? ViewGroup
        } catch (exception: Exception) {
            Log.w(TAG, "HOST_LIST_SLOT unavailable", exception)
            null
        }
    }

    private fun appLayout(activity: CarActivity): View? {
        return try {
            activity.carUiController.d()
        } catch (exception: Exception) {
            Log.w(TAG, "HOST_LIST_APP_LAYOUT unavailable", exception)
            null
        }
    }

    private fun windowRoot(activity: CarActivity): View? {
        return try {
            activity.c().decorView
        } catch (exception: Exception) {
            Log.w(TAG, "HOST_LIST_WINDOW unavailable", exception)
            null
        }
    }

    private fun collectHostLists(root: View?): List<View> {
        val out = ArrayList<View>()
        fun walk(view: View) {
            if (isHostList(view)) {
                out.add(view)
            }
            if (view is ViewGroup) {
                for (index in 0 until view.childCount) {
                    walk(view.getChildAt(index))
                }
            }
        }
        if (root != null) {
            walk(root)
        }
        return out
    }

    private fun isHostList(view: View): Boolean {
        if (view is PagedListView) {
            return true
        }
        val name = view.javaClass.name
        return name.contains("PagedListView") ||
            name.contains("CarRecyclerView") ||
            name.contains("PagedRecycler")
    }

    private fun relabelEmpty(
        root: View?,
        rows: List<Row>,
        onClick: (Row) -> Unit
    ) {
        if (root == null || rows.isEmpty()) {
            return
        }
        fun walk(view: View) {
            if (view is TextView && view.visibility == View.VISIBLE) {
                val text = view.text?.toString().orEmpty()
                if (isEmptyLabel(text)) {
                    val row = rows.first()
                    view.text = row.title
                    view.isClickable = true
                    view.isFocusable = true
                    view.setOnClickListener { onClick(row) }
                    Log.d(TAG, "HOST_LIST_RELABEL title=${row.title}")
                }
            }
            if (view is ViewGroup) {
                for (index in 0 until view.childCount) {
                    walk(view.getChildAt(index))
                }
            }
        }
        walk(root)
    }

    private fun emptyHost(root: View?): ViewGroup? {
        if (root == null) {
            return null
        }
        val labels = ArrayList<TextView>()
        fun walk(view: View) {
            if (view is TextView && view.visibility == View.VISIBLE) {
                val text = view.text?.toString().orEmpty()
                if (isEmptyLabel(text)) {
                    labels.add(view)
                }
            }
            if (view is ViewGroup) {
                for (index in 0 until view.childCount) {
                    walk(view.getChildAt(index))
                }
            }
        }
        walk(root)
        return labels.firstOrNull()?.let { hostContainer(it) }
    }

    private fun isEmptyLabel(text: String): Boolean {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) {
            return false
        }
        val lower = trimmed.lowercase()
        return trimmed.contains("Öğe yok") ||
            lower.contains("no items") ||
            lower.contains("no item") ||
            lower == "empty"
    }

    private fun hostContainer(start: View): ViewGroup? {
        var current: View? = start
        var fallback: ViewGroup? = start as? ViewGroup
        while (current != null) {
            if (isHostList(current) && current is ViewGroup) {
                return current
            }
            fallback = current as? ViewGroup ?: fallback
            current = current.parent as? View
        }
        return fallback
    }

    private fun overlayRows(
        group: ViewGroup,
        rows: List<Row>,
        onClick: (Row) -> Unit
    ) {
        val existing = group.findViewWithTag<View>(ROW_TAG) as? LinearLayout
        val column = existing ?: LinearLayout(group.context).apply {
            tag = ROW_TAG
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.BLACK)
            isFocusable = true
            isClickable = true
            descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            group.addView(this)
        }
        column.removeAllViews()
        val density = group.resources.displayMetrics.density
        val pad = (16 * density).toInt()
        rows.forEach { row ->
            val text = TextView(group.context).apply {
                text = row.title
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
                setPadding(pad, pad, pad, pad)
                isFocusable = true
                isClickable = true
                isFocusableInTouchMode = true
                setOnClickListener { onClick(row) }
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            }
            column.addView(text)
        }
        Log.d(
            TAG,
            "HOST_LIST_OVERLAY_ROWS class=${group.javaClass.name} rows=${rows.size}"
        )
    }

    private class Adapter(
        private var rows: List<Row>,
        private var onClick: (Row) -> Unit
    ) : RecyclerView.Adapter<Adapter.Holder>(), PagedListView.ItemCap {

        private var maxItems = -1

        init {
            setHasStableIds(true)
        }

        fun replace(
            next: List<Row>,
            click: (Row) -> Unit
        ) {
            rows = next
            onClick = click
            notifyDataSetChanged()
        }

        override fun setMaxItems(maxItems: Int) {
            this.maxItems = maxItems
        }

        override fun getItemCount(): Int {
            if (maxItems > 0) {
                return minOf(rows.size, maxItems)
            }
            return rows.size
        }

        override fun getItemId(position: Int): Long {
            return rows[position].key.hashCode().toLong()
        }

        override fun onCreateViewHolder(
            parent: ViewGroup,
            viewType: Int
        ): Holder {
            val item = inflateCarRow(parent)
            val text = item as? TextView
                ?: findTitle(item)
                ?: fallbackTitle(parent)
            if (text.parent == null && item !== text) {
                (item as? ViewGroup)?.addView(text)
            }
            return Holder(item, text)
        }

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val row = rows[position]
            holder.text.text = row.title
            holder.itemView.isClickable = true
            holder.itemView.isFocusable = true
            holder.itemView.setOnClickListener { onClick(row) }
            holder.text.setOnClickListener { onClick(row) }
        }

        class Holder(
            view: View,
            val text: TextView
        ) : RecyclerView.ViewHolder(view)
    }

    private fun inflateCarRow(parent: ViewGroup): View {
        val context = parent.context
        val inflater = LayoutInflater.from(context)
        val layoutId = intArrayOf(
            context.resources.getIdentifier(
                "car_list_item_1",
                "layout",
                context.packageName
            ),
            context.resources.getIdentifier(
                "car_list_item_1",
                "layout",
                "com.google.android.apps.auto.sdk.ui"
            )
        ).firstOrNull { id -> id != 0 } ?: 0
        if (layoutId != 0) {
            try {
                return inflater.inflate(layoutId, parent, false)
            } catch (exception: Exception) {
                Log.w(TAG, "HOST_LIST_ITEM inflate failed", exception)
            }
        }
        return fallbackTitle(parent)
    }

    private fun findTitle(item: View): TextView? {
        if (item is TextView) {
            return item
        }
        val context = item.context
        val ids = intArrayOf(
            context.resources.getIdentifier(
                "text",
                "id",
                context.packageName
            ),
            context.resources.getIdentifier(
                "text",
                "id",
                "com.google.android.apps.auto.sdk.ui"
            ),
            context.resources.getIdentifier(
                "title",
                "id",
                context.packageName
            )
        )
        ids.forEach { id ->
            if (id != 0) {
                (item.findViewById<View>(id) as? TextView)?.let { return it }
            }
        }
        return findFirstText(item)
    }

    private fun findFirstText(view: View): TextView? {
        if (view is TextView) {
            return view
        }
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                findFirstText(view.getChildAt(index))?.let { return it }
            }
        }
        return null
    }

    private fun fallbackTitle(parent: ViewGroup): TextView {
        val density = parent.resources.displayMetrics.density
        return TextView(parent.context).apply {
            layoutParams = RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            val pad = (16 * density).toInt()
            setPadding(pad, pad, pad, pad)
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            isFocusable = true
            isClickable = true
            isFocusableInTouchMode = true
        }
    }
}
