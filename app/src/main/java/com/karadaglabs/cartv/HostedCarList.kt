package com.karadaglabs.cartv

import android.graphics.Color
import android.util.Log
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.apps.auto.sdk.CarActivity
import com.google.android.apps.auto.sdk.ui.PagedListView

/**
 * Puts Live TV / YouTube / Google rows on the real aauto.aar
 * PagedListView in CarActivity.g — the same widget Gearhead uses
 * for the empty "Öğe yok" placeholder.
 */
object HostedCarList {

    private const val TAG = "KaradagCarList"

    data class Row(
        val key: String,
        val title: String
    )

    fun attach(
        listView: View,
        rows: List<Row>,
        onClick: (Row) -> Unit
    ) {
        val paged = listView as? PagedListView ?: return
        paged.isFocusable = true
        paged.isFocusableInTouchMode = true
        paged.descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
        try {
            val existing = paged.adapter as? Adapter
            if (existing != null) {
                existing.replace(rows, onClick)
                return
            }
            paged.setAdapter(Adapter(rows, onClick))
            paged.requestFocus()
        } catch (exception: Exception) {
            Log.e(TAG, "HOST_LIST_ATTACH failed", exception)
        }
    }

    fun bindActivity(
        activity: CarActivity,
        extra: View?,
        rows: List<Row>,
        onClick: (Row) -> Unit
    ) {
        val seen = HashSet<View>()
        collectPaged(extra).forEach { paged ->
            if (seen.add(paged)) {
                attach(paged, rows, onClick)
            }
        }
        collectPaged(contentSlot(activity)).forEach { paged ->
            if (seen.add(paged)) {
                attach(paged, rows, onClick)
            }
        }
        Log.d(TAG, "HOST_LIST_BOUND count=${seen.size} rows=${rows.size}")
    }

    fun showInSlot(activity: CarActivity, page: View?) {
        val overlay = page ?: return
        val slot = contentSlot(activity) ?: return
        val params = overlay.layoutParams
            ?: FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        params.width = ViewGroup.LayoutParams.MATCH_PARENT
        params.height = ViewGroup.LayoutParams.MATCH_PARENT
        overlay.layoutParams = params
        if (overlay.parent !== slot) {
            (overlay.parent as? ViewGroup)?.removeView(overlay)
            slot.addView(overlay, params)
        }
        overlay.visibility = View.VISIBLE
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

    private fun collectPaged(root: View?): List<PagedListView> {
        val out = ArrayList<PagedListView>()
        fun walk(view: View) {
            if (view is PagedListView) {
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
