package com.karadaglabs.cartv

import android.content.Context
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.FrameLayout
import android.widget.GridView
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Araç ekranı kanal tarayıcısı (Android View): durum satırı, kategori
 * şeridi (Tümü / Favoriler / Son izlenen / sunucu kategorileri) ve kanal
 * kartı ızgarası. CarVideoActivity içinde kullanılır; oynatma, park kilidi
 * ve yüzey yönetimi bu sınıfta DEĞİLDİR (yalnızca görünüm + filtre).
 *
 * Filtre yalnızca görünen listeyi değiştirir; kanal seçimi ve ⏮/⏭ sırası
 * ProjectionUiState'in tam listesinde kalır.
 */
class CarChannelBrowser(
    private val context: Context,
    private val library: ChannelLibraryStore,
    private val onChannelClick: (IptvChannel) -> Unit,
    private val onFavoritesChanged: () -> Unit
) {

    companion object {
        const val FILTER_FAVORITES = "__favorites"
        const val FILTER_RECENTS = "__recents"
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private fun dp(value: Int): Int = CarUiKit.dp(context, value)

    private var radioMode = false
    private var allChannels: List<IptvChannel> = emptyList()
    private var selectedId: Int? = null
    private var chipSignature: String? = null

    /** null = tümü, FILTER_* veya sunucu kategori adı. */
    var filter: String? = null
        private set

    private val statusView = TextView(context).apply {
        setTextColor(CarUiKit.INK_MUTED)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
        setPadding(0, dp(4), 0, dp(8))
    }

    private val chipRow = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }

    private val chipScroll = HorizontalScrollView(context).apply {
        isHorizontalScrollBarEnabled = false
        addView(chipRow)
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(10) }
    }

    private val adapter = ChannelAdapter()

    private val gridView = GridView(context).apply {
        // Head-unit genişliğine göre sütun sayısı (800dp ~ 3, 1280dp ~ 5).
        numColumns = GridView.AUTO_FIT
        columnWidth = dp(240)
        stretchMode = GridView.STRETCH_COLUMN_WIDTH
        horizontalSpacing = dp(12)
        verticalSpacing = dp(12)
        selector = android.graphics.drawable.ColorDrawable(Color.TRANSPARENT)
        adapter = this@CarChannelBrowser.adapter
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
    }

    val view: LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        layoutParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        setBackgroundColor(CarUiKit.ANTHRACITE)
        setPadding(dp(16), dp(4), dp(16), dp(12))
        addView(statusView)
        addView(chipScroll)
        addView(gridView)
    }

    /** Canlı TV: tam kanal listesi (filtre burada uygulanır). */
    fun showChannels(channels: List<IptvChannel>, selected: Int?, status: String) {
        radioMode = false
        allChannels = channels
        selectedId = selected
        statusView.text = status
        chipScroll.visibility = if (channels.isEmpty()) View.GONE else View.VISIBLE
        rebuildChips()
        adapter.update(visibleChannels(), selected)
    }

    /** Radyo: kategori şeridi gizli, favori yok. */
    fun showRadio(items: List<IptvChannel>, selected: Int?, status: String) {
        radioMode = true
        statusView.text = status
        chipScroll.visibility = View.GONE
        adapter.update(items, selected)
    }

    fun setFilter(next: String?) {
        filter = next
        showChannels(allChannels, selectedId, statusView.text.toString())
    }

    fun visibleChannels(): List<IptvChannel> = when (val f = filter) {
        null -> allChannels
        FILTER_FAVORITES -> ChannelLibrary.resolve(library.favorites(), allChannels)
        FILTER_RECENTS -> ChannelLibrary.resolve(library.recents(), allChannels)
        else -> allChannels.filter { ChannelCategories.belongs(it, f) }
    }

    private fun rebuildChips() {
        val categories = ChannelCategories.counts(allChannels)
        val favorites = ChannelLibrary.resolve(library.favorites(), allChannels).size
        val recents = ChannelLibrary.resolve(library.recents(), allChannels).size
        val signature = "$filter|$favorites|$recents|$categories"
        if (signature == chipSignature) return
        chipSignature = signature

        chipRow.removeAllViews()
        chipRow.addView(CarUiKit.chip(context, "Tümü ${allChannels.size}", filter == null, R.drawable.ic_ok_grid) { setFilter(null) })
        chipRow.addView(CarUiKit.chip(context, "Favoriler $favorites", filter == FILTER_FAVORITES, R.drawable.ic_ok_star) { setFilter(FILTER_FAVORITES) })
        chipRow.addView(CarUiKit.chip(context, "Son izlenen $recents", filter == FILTER_RECENTS, R.drawable.ic_ok_history) { setFilter(FILTER_RECENTS) })
        categories.forEach { (category, count) ->
            chipRow.addView(
                CarUiKit.chip(context, "${ChannelCategories.displayName(category)} $count",
                    filter.equals(category, ignoreCase = true), null) { setFilter(category) }
            )
        }
    }

    private inner class ChannelAdapter : BaseAdapter() {

        private val density = context.resources.displayMetrics.density

        private var channels: List<IptvChannel> = emptyList()
        private var selectedId: Int? = null
        private var favorites: Set<Int> = emptySet()

        fun update(
            next: List<IptvChannel>,
            selected: Int?
        ) {
            channels = next
            selectedId = selected
            favorites = if (!radioMode) library.favorites().toSet() else emptySet()
            notifyDataSetChanged()
        }

        override fun getCount(): Int = channels.size

        override fun getItem(position: Int): IptvChannel = channels[position]

        override fun getItemId(position: Int): Long = channels[position].id.toLong()

        override fun getView(
            position: Int,
            convertView: View?,
            parent: ViewGroup
        ): View {
            val channel = channels[position]
            val row = convertView as? LinearLayout ?: buildRow()
            val logo = row.getChildAt(0) as ImageView
            val texts = row.getChildAt(1) as LinearLayout
            val name = texts.getChildAt(0) as TextView
            val meta = texts.getChildAt(1) as TextView
            val star = row.getChildAt(2) as ImageView

            val selected = channel.id == selectedId
            val favorite = channel.id in favorites
            name.text = channel.name
            meta.text = listOfNotNull(
                if (radioMode) "RADYO" else "CANLI",
                channel.categories.firstOrNull()?.let(ChannelCategories::displayName)
                    ?.takeIf { !radioMode }
            ).joinToString(" · ")
            star.visibility = if (favorite) View.VISIBLE else View.INVISIBLE
            row.isSelected = selected
            row.isClickable = true
            row.isFocusable = true
            row.contentDescription = channel.name + if (favorite) ", favori" else ""
            row.background = cardBackground(selected, focused = false)
            row.setOnFocusChangeListener { _, hasFocus ->
                row.background = cardBackground(selected, focused = hasFocus)
            }

            bindLogo(logo, channel)
            row.setOnClickListener { onChannelClick(channel) }
            // Uzun basış: favoriye ekle / çıkar (yalnızca Canlı TV).
            row.setOnLongClickListener {
                if (!radioMode) {
                    library.toggleFavorite(channel.id)
                    chipSignature = null
                    showChannels(allChannels, selectedId, statusView.text.toString())
                    onFavoritesChanged()
                    true
                } else {
                    false
                }
            }
            return row
        }

        private fun cardBackground(selected: Boolean, focused: Boolean) = CarUiKit.pressable(
            CarUiKit.rounded(
                if (selected) CarUiKit.ORANGE_SOFT else CarUiKit.SURFACE,
                18,
                context,
                when {
                    selected -> CarUiKit.ORANGE
                    focused -> CarUiKit.INK
                    else -> CarUiKit.OUTLINE
                }
            ),
            context
        )

        private fun buildRow(): LinearLayout {
            val dp = { value: Int -> (value * density).toInt() }

            return LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                minimumHeight = dp(80)
                setPadding(dp(12), dp(10), dp(10), dp(10))

                addView(
                    ImageView(context).apply {
                        layoutParams = LinearLayout.LayoutParams(dp(56), dp(56))
                        scaleType = ImageView.ScaleType.FIT_CENTER
                        setPadding(dp(4), dp(4), dp(4), dp(4))
                    }
                )

                addView(
                    LinearLayout(context).apply {
                        orientation = LinearLayout.VERTICAL
                        layoutParams = LinearLayout.LayoutParams(
                            0,
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            1f
                        ).apply {
                            leftMargin = dp(12)
                        }
                        addView(
                            TextView(context).apply {
                                setTextColor(CarUiKit.INK)
                                setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
                                typeface = android.graphics.Typeface.DEFAULT_BOLD
                                maxLines = 2
                                ellipsize = android.text.TextUtils.TruncateAt.END
                            }
                        )
                        addView(
                            TextView(context).apply {
                                setTextColor(CarUiKit.INK_MUTED)
                                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                                maxLines = 1
                                setCompoundDrawables(
                                    CarUiKit.icon(context, R.drawable.ic_ok_live, CarUiKit.LIVE_RED)?.apply {
                                        setBounds(0, 0, dp(12), dp(12))
                                    },
                                    null, null, null
                                )
                                compoundDrawablePadding = dp(4)
                            }
                        )
                    }
                )

                addView(
                    ImageView(context).apply {
                        layoutParams = LinearLayout.LayoutParams(dp(28), dp(28))
                        setImageDrawable(CarUiKit.icon(context, R.drawable.ic_ok_star, CarUiKit.ORANGE))
                        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                    }
                )
            }
        }
    }

    /** Ortak [ChannelLogos] önbelleği; logo yoksa baş harf rozeti. */
    private fun bindLogo(
        imageView: ImageView,
        channel: IptvChannel
    ) {
        val logoUrl = channel.logoUrl
        imageView.tag = logoUrl

        val cached = ChannelLogos.cached(logoUrl)
        if (cached != null) {
            imageView.background = CarUiKit.rounded(Color.WHITE, 12, context)
            imageView.setImageBitmap(cached)
            return
        }

        imageView.background = null
        imageView.setImageDrawable(CarUiKit.initialsBadge(context, channel.name))

        if (logoUrl.isNullOrBlank()) {
            return
        }

        Thread {
            val bitmap = ChannelLogos.load(logoUrl) ?: return@Thread

            mainHandler.post {
                if (imageView.tag == logoUrl) {
                    imageView.background = CarUiKit.rounded(Color.WHITE, 12, context)
                    imageView.setImageBitmap(bitmap)
                }
            }
        }.start()
    }
}
