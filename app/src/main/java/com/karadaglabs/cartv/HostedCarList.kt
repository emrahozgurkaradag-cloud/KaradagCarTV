package com.karadaglabs.cartv

import android.graphics.Color
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.apps.auto.sdk.ui.PagedListView

/**
 * Native Gearhead list inside CarActivity.g. Radio works because
 * Android Auto shows a real list; Live TV / YouTube / Google use the
 * same PagedListView widget from aauto.aar so the car does not show
 * the empty "Öğe yok" placeholder.
 */
object HostedCarList {

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
        val existing = paged.adapter as? Adapter
        if (existing != null) {
            existing.replace(rows, onClick)
            return
        }
        paged.setAdapter(Adapter(rows, onClick))
        paged.requestFocus()
    }

    private class Adapter(
        private var rows: List<Row>,
        private var onClick: (Row) -> Unit
    ) : RecyclerView.Adapter<Adapter.Holder>(), PagedListView.ItemCap {

        private var maxItems = -1

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
            if (maxItems >= 0) {
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
            val density = parent.resources.displayMetrics.density
            val text = TextView(parent.context).apply {
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
            return Holder(text)
        }

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val row = rows[position]
            holder.text.text = row.title
            holder.text.setOnClickListener { onClick(row) }
        }

        class Holder(val text: TextView) : RecyclerView.ViewHolder(text)
    }
}
