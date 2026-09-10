package com.karadaglabs.cartv

import android.util.Log
import com.google.android.apps.auto.sdk.MenuAdapter
import com.google.android.apps.auto.sdk.MenuItem

object HostedCarMenu {

    fun toSdkItem(entry: HostedMenuEntry): MenuItem {
        val type = if (entry.type == HostedMenuType.SUBMENU) {
            MenuItem.Type.SUBMENU
        } else {
            MenuItem.Type.ITEM
        }

        return MenuItem.Builder()
            .setTitle(entry.title)
            .setSubtitle(entry.subtitle)
            .setType(type)
            .build()
    }
}

class HostedRootMenuAdapter(
    private val onAction: (HostedMenuEntry) -> Unit,
    private val channels: () -> List<IptvChannel>,
    private val channelLoading: () -> Boolean,
    private val channelError: () -> String
) : MenuAdapter() {

    companion object {
        private const val TAG = "KaradagHostedMenu"
    }

    private var channelAdapter: HostedItemMenuAdapter? = null
    private var radioAdapter: HostedItemMenuAdapter? = null

    override fun getTitle(): String = "Karadag Car TV"

    override fun getMenuItemCount(): Int {
        return HostedCarMenuModel.neverEmpty(
            HostedCarMenuModel.root(channelCount = channels().size)
        ).size
    }

    override fun getMenuItem(position: Int): MenuItem {
        val items = HostedCarMenuModel.neverEmpty(
            HostedCarMenuModel.root(channelCount = channels().size)
        )
        return HostedCarMenu.toSdkItem(items[position.coerceIn(0, items.lastIndex)])
    }

    override fun onLoadSubmenu(position: Int): MenuAdapter? {
        val items = HostedCarMenuModel.root(channelCount = channels().size)
        val entry = items.getOrNull(position) ?: return null

        Log.d(TAG, "CAR HOST SUBMENU ${entry.title}")

        return when (entry.action) {
            HostedMenuAction.LIVE_TV -> {
                val adapter = HostedItemMenuAdapter(
                    title = "Canlı TV",
                    itemsProvider = {
                        HostedCarMenuModel.channels(
                            channels(),
                            channelLoading(),
                            channelError()
                        )
                    },
                    onAction = onAction
                )
                if (channelLoading() && channels().isEmpty()) {
                    adapter.showLoadingIndicator()
                }
                channelAdapter = adapter
                adapter
            }

            HostedMenuAction.RADIO -> {
                val adapter = HostedItemMenuAdapter(
                    title = "Canlı Radyo",
                    itemsProvider = { HostedCarMenuModel.radios() },
                    onAction = onAction
                )
                radioAdapter = adapter
                adapter
            }

            else -> null
        }
    }

    override fun onMenuItemClicked(position: Int) {
        val items = HostedCarMenuModel.root(channelCount = channels().size)
        val entry = items.getOrNull(position) ?: return

        if (entry.type == HostedMenuType.ITEM) {
            Log.d(TAG, "CAR HOST MENU CLICK ${entry.title}")
            onAction(entry)
        }
    }

    fun refreshChannels() {
        val adapter = channelAdapter ?: return

        if (channelLoading() && channels().isEmpty()) {
            adapter.showLoadingIndicator()
        } else {
            adapter.hideLoadingIndicator()
        }

        adapter.refresh()
        notifyItemChanged(0)
    }
}

class HostedItemMenuAdapter(
    private val title: String,
    private val itemsProvider: () -> List<HostedMenuEntry>,
    private val onAction: (HostedMenuEntry) -> Unit
) : MenuAdapter() {

    companion object {
        private const val TAG = "KaradagHostedMenu"
    }

    override fun getTitle(): String = title

    override fun getMenuItemCount(): Int {
        return HostedCarMenuModel.neverEmpty(itemsProvider()).size
    }

    override fun getMenuItem(position: Int): MenuItem {
        val items = HostedCarMenuModel.neverEmpty(itemsProvider())
        return HostedCarMenu.toSdkItem(items[position.coerceIn(0, items.lastIndex)])
    }

    override fun onMenuItemClicked(position: Int) {
        val items = HostedCarMenuModel.neverEmpty(itemsProvider())
        val entry = items.getOrNull(position) ?: return

        if (entry.action == HostedMenuAction.PLACEHOLDER) {
            return
        }

        Log.d(TAG, "CAR HOST ITEM CLICK ${entry.title}")
        onAction(entry)
    }

    fun refresh() {
        notifyDataSetChanged()
    }
}
