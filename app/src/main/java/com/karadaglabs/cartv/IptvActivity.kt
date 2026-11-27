package com.karadaglabs.cartv

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import com.karadaglabs.cartv.ui.OkBanner
import com.karadaglabs.cartv.ui.OkChannelCard
import com.karadaglabs.cartv.ui.OkChip
import com.karadaglabs.cartv.ui.OkIconButton
import com.karadaglabs.cartv.ui.OkLoading
import com.karadaglabs.cartv.ui.OkMetrics
import com.karadaglabs.cartv.ui.OkSearchField
import com.karadaglabs.cartv.ui.OkStateMessage
import com.karadaglabs.cartv.ui.OkTopBar
import com.karadaglabs.cartv.ui.rememberOkMetrics
import com.karadaglabs.cartv.ui.theme.KaradagCarTVTheme
import com.karadaglabs.cartv.ui.theme.OkColors

/**
 * Canlı TV: sunucunun /api/iptv ile verdiği TÜM doğrulanmış kanallar.
 *
 * - Önce önbellek anında gösterilir (boş/bekleme ekranı yok), ardından API
 *   arka planda yenilenir; API başarısızsa önbellek + çevrimdışı şeridi.
 * - Kategoriler sunucu verisinden dinamik ([ChannelCategories]).
 * - Favoriler / son izlenenler cihazda ([ChannelLibraryStore]).
 * - Oynatma mevcut Media3 PlayerActivity ile.
 */
@UnstableApi
class IptvActivity : ComponentActivity() {

    companion object {
        /** "favorites" | "recents" | kategori adı; ana ekrandan doğrudan açılış. */
        const val EXTRA_FILTER = "iptv_filter"
        const val EXTRA_QUERY = "iptv_query"
        const val FILTER_FAVORITES = "__favorites"
        const val FILTER_RECENTS = "__recents"
    }

    private var resumeCount by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Telefonda yatay; araç ekranı / AAOS'ta yön zorlanmaz.
        CarScreen.applyPhoneLandscape(this)

        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    finish()
                }
            }
        )

        val initialFilter = intent?.getStringExtra(EXTRA_FILTER)
        val initialQuery = intent?.getStringExtra(EXTRA_QUERY).orEmpty()

        setContent {
            KaradagCarTVTheme {
                Box(Modifier.fillMaxSize().background(OkColors.Anthracite)) {
                    IptvScreen(initialFilter, initialQuery)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Oynatıcıdan dönünce son izlenenler / favoriler yeniden okunur.
        resumeCount++
    }

    @Composable
    private fun IptvScreen(initialFilter: String?, initialQuery: String) {
        val metrics = rememberOkMetrics()
        val store = remember { ChannelLibraryStore(this) }

        var channels by remember { mutableStateOf(IptvRepository.loadCached(this)) }
        var loading by remember { mutableStateOf(channels.isEmpty()) }
        var refreshing by remember { mutableStateOf(false) }
        var errorMessage by remember { mutableStateOf("") }
        var offline by remember { mutableStateOf(false) }
        var lastUpdate by remember { mutableStateOf(IptvRepository.lastUpdate(this)) }
        var filter by remember { mutableStateOf(initialFilter) }
        var query by remember { mutableStateOf(initialQuery) }
        var favorites by remember { mutableStateOf(store.favorites()) }
        var recents by remember { mutableStateOf(store.recents()) }

        LaunchedEffect(resumeCount) {
            favorites = store.favorites()
            recents = store.recents()
        }

        fun refresh() {
            if (refreshing) return
            refreshing = true
            loadIptv(
                context = this,
                onSuccess = { loaded ->
                    channels = loaded
                    errorMessage = ""
                    offline = false
                    loading = false
                    refreshing = false
                    lastUpdate = IptvRepository.lastUpdate(this)
                },
                onError = { message, isOffline, cached ->
                    if (cached.isNotEmpty()) channels = cached
                    errorMessage = message
                    offline = isOffline
                    loading = false
                    refreshing = false
                }
            )
        }

        LaunchedEffect(Unit) { refresh() }

        Column(Modifier.fillMaxSize()) {
            OkTopBar(
                metrics = metrics,
                subtitle = buildString {
                    append("Canlı TV")
                    if (channels.isNotEmpty()) append(" · ${channels.size} kanal")
                    if (lastUpdate.isNotBlank()) append(" · Güncelleme: $lastUpdate")
                },
                onBack = { finish() }
            ) {
                OkIconButton(R.drawable.ic_ok_refresh, "Kanal listesini yenile", metrics, { refresh() }, busy = refreshing)
            }

            when {
                loading -> OkLoading("Kanallar yükleniyor…", metrics)

                channels.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    OkStateMessage(
                        icon = if (offline) R.drawable.ic_ok_offline else R.drawable.ic_ok_alert,
                        title = if (offline) "Bağlantı yok" else "Kanal listesi alınamadı",
                        message = errorMessage.ifBlank { "Bu lisansa tanımlı doğrulanmış kanal bulunamadı." },
                        metrics = metrics,
                        actionLabel = "Yeniden dene",
                        onAction = { refresh() }
                    )
                }

                else -> ChannelBrowser(
                    metrics = metrics,
                    channels = channels,
                    favorites = favorites,
                    recents = recents,
                    filter = filter,
                    query = query,
                    banner = when {
                        errorMessage.isBlank() -> null
                        offline -> "Çevrimdışı: son kaydedilen kanal listesi gösteriliyor ($lastUpdate)."
                        else -> "Liste yenilenemedi: $errorMessage"
                    },
                    onFilter = { filter = it },
                    onQuery = { query = it },
                    onToggleFavorite = { favorites = store.toggleFavorite(it.id) },
                    onOpen = { channel ->
                        recents = store.markWatched(channel.id)
                        openPlayer(channel)
                    }
                )
            }
        }
    }

    @Composable
    private fun ChannelBrowser(
        metrics: OkMetrics,
        channels: List<IptvChannel>,
        favorites: List<Int>,
        recents: List<Int>,
        filter: String?,
        query: String,
        banner: String?,
        onFilter: (String?) -> Unit,
        onQuery: (String) -> Unit,
        onToggleFavorite: (IptvChannel) -> Unit,
        onOpen: (IptvChannel) -> Unit
    ) {
        val categories = remember(channels) { ChannelCategories.counts(channels) }
        val sorted = remember(channels) { ChannelLibrary.sorted(channels) }

        val base = when (filter) {
            null -> sorted
            FILTER_FAVORITES -> ChannelLibrary.resolve(favorites, channels)
            FILTER_RECENTS -> ChannelLibrary.resolve(recents, channels)
            else -> sorted.filter { ChannelCategories.belongs(it, filter) }
        }
        val visible = ChannelLibrary.search(base, query)

        Column(Modifier.fillMaxSize().padding(horizontal = metrics.gutter)) {
            if (banner != null) {
                OkBanner(R.drawable.ic_ok_offline, banner, metrics)
                Spacer(Modifier.height(10.dp))
            }

            OkSearchField(
                value = query,
                onValueChange = onQuery,
                placeholder = "Kanal veya kategori ara",
                metrics = metrics,
                modifier = Modifier.fillMaxWidth().testTag("iptv_search")
            )

            Spacer(Modifier.height(12.dp))

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth().testTag("iptv_categories")
            ) {
                item { OkChip("Tümü", filter == null, metrics, { onFilter(null) }, channels.size, R.drawable.ic_ok_grid) }
                item {
                    OkChip("Favoriler", filter == FILTER_FAVORITES, metrics, { onFilter(FILTER_FAVORITES) },
                        ChannelLibrary.resolve(favorites, channels).size, R.drawable.ic_ok_star)
                }
                item {
                    OkChip("Son izlenen", filter == FILTER_RECENTS, metrics, { onFilter(FILTER_RECENTS) },
                        ChannelLibrary.resolve(recents, channels).size, R.drawable.ic_ok_history)
                }
                items(categories, key = { it.first }) { (category, count) ->
                    OkChip(
                        ChannelCategories.displayName(category),
                        filter.equals(category, ignoreCase = true),
                        metrics,
                        { onFilter(category) },
                        count
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            if (visible.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    OkStateMessage(
                        icon = when (filter) {
                            FILTER_FAVORITES -> R.drawable.ic_ok_star_outline
                            FILTER_RECENTS -> R.drawable.ic_ok_history
                            else -> R.drawable.ic_ok_search
                        },
                        title = when {
                            query.isNotBlank() -> "Sonuç yok"
                            filter == FILTER_FAVORITES -> "Henüz favori yok"
                            filter == FILTER_RECENTS -> "Henüz izlenen kanal yok"
                            else -> "Bu kategoride kanal yok"
                        },
                        message = when {
                            query.isNotBlank() -> "\"$query\" için kanal bulunamadı."
                            filter == FILTER_FAVORITES -> "Kanal kartındaki yıldıza dokunarak favorilere ekleyin."
                            filter == FILTER_RECENTS -> "İzlediğiniz kanallar burada görünür."
                            else -> "Başka bir kategori seçin."
                        },
                        metrics = metrics,
                        actionLabel = if (filter != null || query.isNotBlank()) "Tüm kanallar" else null,
                        onAction = { onFilter(null); onQuery("") }
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(metrics.cardMinWidth),
                    contentPadding = PaddingValues(bottom = metrics.gutter),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize().testTag("iptv_grid")
                ) {
                    items(visible, key = { it.id }) { channel ->
                        OkChannelCard(
                            channel = channel,
                            categoryLabel = channel.categories.firstOrNull()?.let(ChannelCategories::displayName),
                            favorite = channel.id in favorites,
                            metrics = metrics,
                            onOpen = { onOpen(channel) },
                            onToggleFavorite = { onToggleFavorite(channel) }
                        )
                    }
                }
            }
        }
    }

    /**
     * API -> başarı; başarısızsa önbellek (varsa) + açık hata metni.
     * İş parçacığı: arka plan; geri çağrılar ana iş parçacığında.
     */
    private fun loadIptv(
        context: Context,
        onSuccess: (List<IptvChannel>) -> Unit,
        onError: (message: String, offline: Boolean, cached: List<IptvChannel>) -> Unit
    ) {
        Thread {
            try {
                val loaded = IptvRepository.fetchFromApi(context)
                IptvRepository.save(context, loaded)
                runOnUiThread { onSuccess(loaded) }
            } catch (exception: Exception) {
                val cached = IptvRepository.loadCached(context)
                val message = NetworkErrors.describe(exception)
                val offline = NetworkErrors.isOffline(exception)
                runOnUiThread { onError(message, offline, cached) }
            }
        }.start()
    }

    private fun openPlayer(channel: IptvChannel) {
        IptvRepository.saveLastSelectedChannelId(this, channel.id)
        startActivity(
            Intent(this, PlayerActivity::class.java).apply {
                putExtra(PlayerActivity.EXTRA_STREAM_URL, channel.streamUrl)
                putExtra(PlayerActivity.EXTRA_TITLE, channel.name)
                putExtra(PlayerActivity.EXTRA_MEDIA_TYPE, "iptv")
            }
        )
    }
}
