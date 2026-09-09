package com.karadaglabs.cartv

import android.util.Log
import com.google.android.apps.auto.sdk.MenuAdapter
import com.google.android.apps.auto.sdk.MenuItem

/**
 * Native aauto MenuAdapter. Gearhead renders this list in the hosted
 * chrome. An adapter with getMenuItemCount()==0 is what the car screen
 * shows as "Öğe yok". This adapter never returns an empty list.
 */
class HostedListAdapter(
    private val titleText: String,
    items: List<Item>,
    private val emptyTitle: String,
    private val onItem: (Item) -> Unit
) : MenuAdapter() {

    data class Item(
        val id: String,
        val title: String,
        val subtitle: String = "",
        val payload: Any? = null,
        val placeholder: Boolean = false
    )

    private var items: List<Item> = withPlaceholder(items, emptyTitle)

    fun replace(
        next: List<Item>,
        emptyTitle: String = this.emptyTitle
    ) {
        items = withPlaceholder(next, emptyTitle)
        Log.d(
            TAG,
            "menu adapter '$titleText' count=${items.size} " +
                "placeholder=${items.firstOrNull()?.placeholder == true}"
        )
        notifyDataSetChanged()
    }

    override fun getTitle(): String {
        return titleText
    }

    override fun getMenuItemCount(): Int {
        return items.size
    }

    override fun getMenuItem(position: Int): MenuItem {
        val item = items[position]

        return MenuItem.Builder()
            .setTitle(item.title)
            .setSubtitle(item.subtitle)
            .setType(MenuItem.Type.ITEM)
            .build()
    }

    override fun onMenuItemClicked(position: Int) {
        val item = items.getOrNull(position) ?: return

        if (item.placeholder) {
            Log.d(TAG, "placeholder clicked title=${item.title}")
            return
        }

        Log.d(TAG, "menu item clicked id=${item.id} title=${item.title}")
        onItem(item)
    }

    companion object {
        private const val TAG = "KaradagHostedMenu"

        fun withPlaceholder(
            items: List<Item>,
            emptyTitle: String
        ): List<Item> {
            if (items.isNotEmpty()) {
                return items
            }

            return listOf(
                Item(
                    id = "_empty",
                    title = emptyTitle,
                    placeholder = true
                )
            )
        }

        fun channelItems(channels: List<IptvChannel>): List<Item> {
            return channels.map { channel ->
                Item(
                    id = channel.mediaId,
                    title = channel.name,
                    subtitle = channel.category.orEmpty(),
                    payload = channel
                )
            }
        }

        fun radioItems(stations: List<RadioStation>): List<Item> {
            return stations.map { station ->
                Item(
                    id = station.id,
                    title = station.name,
                    subtitle = "Radyo",
                    payload = station
                )
            }
        }
    }
}
