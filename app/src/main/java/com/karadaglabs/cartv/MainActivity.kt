package com.karadaglabs.cartv

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.karadaglabs.cartv.ui.OkBanner
import com.karadaglabs.cartv.ui.OkChannelCard
import com.karadaglabs.cartv.ui.OkChannelPoster
import com.karadaglabs.cartv.ui.OkChip
import com.karadaglabs.cartv.ui.OkFeatureTile
import com.karadaglabs.cartv.ui.OkIconButton
import com.karadaglabs.cartv.ui.OkLogoMark
import com.karadaglabs.cartv.ui.OkMetrics
import com.karadaglabs.cartv.ui.OkPrimaryButton
import com.karadaglabs.cartv.ui.OkSearchField
import com.karadaglabs.cartv.ui.OkSectionTitle
import com.karadaglabs.cartv.ui.OkStateMessage
import com.karadaglabs.cartv.ui.OkTopBar
import com.karadaglabs.cartv.ui.OkWordmark
import com.karadaglabs.cartv.ui.rememberOkMetrics
import com.karadaglabs.cartv.ui.theme.KaradagCarTVTheme
import com.karadaglabs.cartv.ui.theme.OkColors
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private data class CarContent(
    val id: Int,
    val title: String,
    val type: String,
    val streamUrl: String,
    val description: String?
)

private data class LicenseFeatures(
    val liveTv: Boolean = false,
    val radio: Boolean = false,
    val video: Boolean = false,
    val iptv: Boolean = false,
    val youtube: Boolean = false,
    val youtubeKids: Boolean = false,
    val googleSearch: Boolean = false
)

private class ApiException(
    val code: String,
    override val message: String
) : Exception(message)

private enum class HomePage { HOME, RADIO, SETTINGS }

@androidx.annotation.OptIn(
    markerClass = [androidx.media3.common.util.UnstableApi::class]
)
class MainActivity : ComponentActivity() {

    private companion object {
        const val KEY_FEATURES = "license_features_cache"
        const val KEY_CUSTOMER = "license_customer_name"
        const val KEY_DEVICE = "license_device_name"
    }

    private var resumeCount by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Telefonda yatay; araç ekranı / AAOS'ta yön zorlanmaz.
        CarScreen.applyPhoneLandscape(this)

        // projection: araç hız sensörü izni (play: işlem yok).
        CarPlayerBridge.requestCarPermissions(this)

        val email = AppSession.email(this)
        val licenseKey = AppSession.licenseKey(this)

        if (email.isBlank() || licenseKey.isBlank()) {
            startActivity(Intent(this, RegistrationActivity::class.java))
            finish()
            return
        }

        setContent {
            KaradagCarTVTheme {
                Box(Modifier.fillMaxSize().background(OkColors.Anthracite)) {
                    CarTvApp()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        resumeCount++
    }

    /**
     * Ana ekran önbellekten ANINDA çizilir (lisans özellikleri, içerikler,
     * kanallar); lisans doğrulama + içerik + IPTV arka planda yenilenir.
     * Lisans hatası kodlarında kayıt ekranına dönülür (lisans denetimi aynen).
     */
    @Composable
    private fun CarTvApp() {
        val context = LocalContext.current
        val prefs = remember { AppSession.prefs(context) }

        val cachedFeatures = remember {
            prefs.getString(KEY_FEATURES, null)?.let { raw ->
                runCatching { featuresFromJson(JSONObject(raw)) }.getOrNull()
            }
        }

        var refreshing by remember { mutableStateOf(false) }
        var licenseChecked by remember { mutableStateOf(cachedFeatures != null) }
        var errorMessage by remember { mutableStateOf("") }
        var offline by remember { mutableStateOf(false) }
        var customerName by remember { mutableStateOf(prefs.getString(KEY_CUSTOMER, "").orEmpty()) }
        var deviceName by remember { mutableStateOf(prefs.getString(KEY_DEVICE, "").orEmpty()) }
        var contents by remember { mutableStateOf(loadContentsCache(context)) }
        var features by remember { mutableStateOf(cachedFeatures ?: LicenseFeatures()) }
        var channels by remember { mutableStateOf(IptvRepository.loadCached(context)) }
        var page by remember { mutableStateOf(HomePage.HOME) }
        var lastUpdate by remember { mutableStateOf(prefs.getString("last_update", "").orEmpty()) }

        fun refresh() {
            if (refreshing) return

            refreshing = true
            errorMessage = ""

            Thread {
                try {
                    val licenseResponse = postJson(
                        "/api/license/validate",
                        JSONObject().apply {
                            put("device_uuid", AppSession.deviceUuid(context))
                            put("license_key", AppSession.licenseKey(context))
                        }
                    )

                    val license = licenseResponse.optJSONObject("license")
                    val customer = licenseResponse.optJSONObject("customer")
                    val device = licenseResponse.optJSONObject("device")

                    val featuresJson = license?.optJSONObject("features")
                    val loadedFeatures = featuresFromJson(featuresJson)

                    val contentResponse = postJson(
                        "/api/content",
                        JSONObject().apply {
                            put("device_uuid", AppSession.deviceUuid(context))
                            put("license_key", AppSession.licenseKey(context))
                        }
                    )

                    val loadedContents = parseContents(
                        contentResponse.optJSONArray("contents")
                    )

                    if (loadedFeatures.iptv) {
                        runCatching {
                            updateIptvCache(context)
                        }
                    }

                    val name = customer?.optString("name").orEmpty()
                    val device2 = device?.optString("name").orEmpty()
                    prefs.edit()
                        .putString(KEY_FEATURES, (featuresJson ?: JSONObject()).toString())
                        .putString(KEY_CUSTOMER, name)
                        .putString(KEY_DEVICE, device2)
                        .apply()

                    runOnUiThread {
                        customerName = name
                        deviceName = device2
                        features = loadedFeatures
                        licenseChecked = true
                        contents = loadedContents
                        channels = IptvRepository.loadCached(context)
                        lastUpdate = saveContentsCache(context, loadedContents)
                        offline = false
                        refreshing = false
                    }

                } catch (exception: ApiException) {

                    runOnUiThread {
                        refreshing = false

                        if (
                            exception.code in setOf(
                                "DEVICE_NOT_FOUND",
                                "LICENSE_NOT_FOUND",
                                "LICENSE_EXPIRED",
                                "LICENSE_NOT_STARTED"
                            )
                        ) {
                            AppSession.clearLicense(context)
                            prefs.edit().remove(KEY_FEATURES).apply()
                            startActivity(
                                Intent(
                                    this,
                                    RegistrationActivity::class.java
                                )
                            )
                            finish()
                        } else {
                            errorMessage = exception.message
                        }
                    }

                } catch (exception: Exception) {

                    runOnUiThread {
                        refreshing = false
                        offline = NetworkErrors.isOffline(exception)
                        errorMessage = NetworkErrors.describe(exception)
                    }
                }
            }.start()
        }

        LaunchedEffect(Unit) {
            refresh()
        }

        when (page) {
            HomePage.RADIO -> RadioScreen(
                contents = contents.filter { it.type == "radio" },
                onBack = { page = HomePage.HOME }
            )

            HomePage.SETTINGS -> SettingsScreen(
                customerName = customerName,
                deviceName = deviceName,
                lastUpdate = lastUpdate,
                channelCount = channels.size,
                refreshing = refreshing,
                onRefresh = { refresh() },
                onBack = { page = HomePage.HOME }
            )

            HomePage.HOME -> HomeScreen(
                customerName = customerName,
                deviceName = deviceName,
                contents = contents,
                channels = channels,
                features = features,
                licenseChecked = licenseChecked,
                refreshing = refreshing,
                errorMessage = errorMessage,
                offline = offline,
                onRefresh = { refresh() },
                onRadio = { page = HomePage.RADIO },
                onSettings = { page = HomePage.SETTINGS }
            )
        }
    }

    private fun postJson(
        path: String,
        body: JSONObject
    ): JSONObject {

        val connection = URL(
            AppSession.API_BASE_URL + path
        ).openConnection() as HttpURLConnection

        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 10_000
            connection.readTimeout = 10_000
            connection.doOutput = true

            connection.setRequestProperty(
                "Content-Type",
                "application/json"
            )

            connection.setRequestProperty(
                "Accept",
                "application/json"
            )

            connection.outputStream.use { output ->
                output.write(
                    body.toString().toByteArray(
                        Charsets.UTF_8
                    )
                )
            }

            val code = connection.responseCode

            val text =
                if (code in 200..299) {
                    connection.inputStream
                        .bufferedReader()
                        .use { it.readText() }
                } else {
                    connection.errorStream
                        ?.bufferedReader()
                        ?.use { it.readText() }
                        .orEmpty()
                }

            val json = JSONObject(
                text.ifBlank {
                    "{}"
                }
            )

            if (code !in 200..299) {
                throw ApiException(
                    json.optString(
                        "code",
                        "API_ERROR"
                    ),
                    json.optString(
                        "message",
                        "API hatası: $code"
                    )
                )
            }

            return json

        } finally {
            connection.disconnect()
        }
    }

    private fun parseContents(
        array: JSONArray?
    ): List<CarContent> {

        if (array == null) {
            return emptyList()
        }

        return buildList {

            for (index in 0 until array.length()) {

                val item = array.getJSONObject(index)

                add(
                    CarContent(
                        id = item.optInt("id"),
                        title = item.optString("title"),
                        type = item.optString("type"),
                        streamUrl = item.optString("stream_url"),
                        description = item
                            .optString("description")
                            .ifBlank {
                                null
                            }
                    )
                )
            }
        }
    }

    private fun featuresFromJson(
        json: JSONObject?
    ): LicenseFeatures {

        return LicenseFeatures(
            liveTv = json?.optBoolean(
                "live_tv",
                false
            ) == true,

            radio = json?.optBoolean(
                "radio",
                false
            ) == true,

            video = json?.optBoolean(
                "video",
                false
            ) == true,

            iptv = json?.optBoolean(
                "iptv",
                false
            ) == true,

            youtube = json?.optBoolean(
                "youtube",
                false
            ) == true,

            youtubeKids = json?.optBoolean(
                "youtube_kids",
                false
            ) == true,

            googleSearch = json?.optBoolean(
                "google_search",
                false
            ) == true
        )
    }

    private fun updateIptvCache(
        context: android.content.Context
    ) {

        val response = postJson(
            "/api/iptv",
            JSONObject().apply {
                put(
                    "device_uuid",
                    AppSession.deviceUuid(context)
                )

                put(
                    "license_key",
                    AppSession.licenseKey(context)
                )
            }
        )

        val channels =
            response.optJSONArray("channels")
                ?: return

        AppSession.prefs(context)
            .edit()
            .putString(
                "iptv_channels",
                channels.toString()
            )
            .putString(
                "iptv_last_update",
                nowText()
            )
            .apply()
    }

    private fun saveContentsCache(
        context: android.content.Context,
        contents: List<CarContent>
    ): String {

        val array = JSONArray()

        contents.forEach { content ->

            array.put(
                JSONObject().apply {

                    put(
                        "id",
                        content.id
                    )

                    put(
                        "title",
                        content.title
                    )

                    put(
                        "type",
                        content.type
                    )

                    put(
                        "stream_url",
                        content.streamUrl
                    )

                    put(
                        "description",
                        content.description ?: ""
                    )
                }
            )
        }

        val time = nowText()

        AppSession.prefs(context)
            .edit()
            .putString(
                "contents",
                array.toString()
            )
            .putString(
                "last_update",
                time
            )
            .apply()

        return time
    }

    private fun loadContentsCache(
        context: android.content.Context
    ): List<CarContent> {

        val raw = AppSession.prefs(context)
            .getString(
                "contents",
                null
            )
            ?: return emptyList()

        return runCatching {
            parseContents(
                JSONArray(raw)
            )
        }.getOrDefault(
            emptyList()
        )
    }

    private fun nowText(): String =
        SimpleDateFormat(
            "dd.MM.yyyy HH:mm",
            Locale("tr", "TR")
        ).format(Date())

    private fun openIptv(filter: String? = null, query: String? = null) {
        startActivity(
            Intent(this, IptvActivity::class.java).apply {
                filter?.let { putExtra(IptvActivity.EXTRA_FILTER, it) }
                query?.let { putExtra(IptvActivity.EXTRA_QUERY, it) }
            }
        )
    }

    private fun playChannel(channel: IptvChannel) {
        ChannelLibraryStore(this).markWatched(channel.id)
        IptvRepository.saveLastSelectedChannelId(this, channel.id)
        startActivity(
            Intent(this, PlayerActivity::class.java).apply {
                putExtra(PlayerActivity.EXTRA_STREAM_URL, channel.streamUrl)
                putExtra(PlayerActivity.EXTRA_TITLE, channel.name)
                putExtra(PlayerActivity.EXTRA_MEDIA_TYPE, "iptv")
            }
        )
    }

    private fun openGoogle(query: String?) {
        val url = if (query.isNullOrBlank()) {
            "https://www.google.com/"
        } else {
            "https://www.google.com/search?q=" + Uri.encode(query)
        }
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }

    private fun shareDiagnostics() {
        val text = CarPlayerBridge.carDiagnosticsText(this).ifBlank { "Henüz kayıt yok." }
        startActivity(
            Intent.createChooser(
                Intent(Intent.ACTION_SEND)
                    .setType("text/plain")
                    .putExtra(Intent.EXTRA_SUBJECT, "KaradagCarTV araç teşhis kaydı")
                    .putExtra(Intent.EXTRA_TEXT, text),
                "Teşhis kaydını paylaş"
            )
        )
    }

    /**
     * Medya merkezi ana ekranı. Canlı TV, YouTube, Radyo ve Google her zaman
     * görünür; lisansta olmayan veya araç ekranında izin verilmeyen özellik
     * kilitli kutucuk olarak, nedeniyle gösterilir (gizlenmez).
     */
    @Composable
    private fun HomeScreen(
        customerName: String,
        deviceName: String,
        contents: List<CarContent>,
        channels: List<IptvChannel>,
        features: LicenseFeatures,
        licenseChecked: Boolean,
        refreshing: Boolean,
        errorMessage: String,
        offline: Boolean,
        onRefresh: () -> Unit,
        onRadio: () -> Unit,
        onSettings: () -> Unit
    ) {
        val context = LocalContext.current
        val metrics = rememberOkMetrics()
        val store = remember { ChannelLibraryStore(context) }
        var query by remember { mutableStateOf("") }
        var recents by remember { mutableStateOf(store.recents()) }
        var favorites by remember { mutableStateOf(store.favorites()) }

        LaunchedEffect(resumeCount) {
            recents = store.recents()
            favorites = store.favorites()
        }

        val webAllowed = CarScreen.allowsWebFeatures(CarScreen.isCarScreen(context))
        val radioCount = contents.count { it.type == "radio" }
        val checking = !licenseChecked

        fun lockReason(licensed: Boolean, carBlocked: Boolean = false): String = when {
            checking -> "Lisans kontrol ediliyor…"
            !licensed -> "Lisansınızda yok"
            carBlocked -> "Araç ekranında kapalı"
            else -> ""
        }

        val tiles = listOf(
            HomeTile(R.drawable.ic_ok_tv, "Canlı TV",
                if (features.iptv) "${channels.size} kanal" else lockReason(false),
                enabled = features.iptv, accent = true) { openIptv() },
            HomeTile(R.drawable.ic_ok_video, "YouTube",
                if (features.youtube && webAllowed) "Videolar" else lockReason(features.youtube, !webAllowed),
                enabled = features.youtube && webAllowed) {
                startActivity(Intent(this, YouTubeActivity::class.java))
            },
            HomeTile(R.drawable.ic_ok_radio, "Radyo",
                if (features.radio) "$radioCount istasyon" else lockReason(false),
                enabled = features.radio, onClick = onRadio),
            HomeTile(R.drawable.ic_ok_web, "Google",
                if (features.googleSearch && webAllowed) "Web'de ara" else lockReason(features.googleSearch, !webAllowed),
                enabled = features.googleSearch && webAllowed) { openGoogle(null) }
        )

        val sorted = remember(channels) { ChannelLibrary.sorted(channels) }
        val featured = remember(channels) { ChannelLibrary.featured(channels) }
        val recentChannels = ChannelLibrary.resolve(recents, channels)
        val favoriteChannels = ChannelLibrary.resolve(favorites, channels)
        val categories = remember(channels) { ChannelCategories.counts(channels) }
        val results = if (query.isBlank() || !features.iptv) emptyList() else ChannelLibrary.search(sorted, query)

        Column(Modifier.fillMaxSize()) {
            OkTopBar(
                metrics = metrics,
                subtitle = listOf(customerName, deviceName).filter { it.isNotBlank() }.joinToString(" · ")
                    .ifBlank { "Araç içi medya merkezi" }
            ) {
                OkIconButton(R.drawable.ic_ok_refresh, "Yenile", metrics, onRefresh, busy = refreshing)
                OkIconButton(R.drawable.ic_ok_settings, "Ayarlar", metrics, onSettings)
            }

            LazyColumn(
                Modifier.fillMaxSize().testTag("home_list"),
                contentPadding = PaddingValues(start = metrics.gutter, end = metrics.gutter, bottom = metrics.gutter)
            ) {
                item {
                    OkSearchField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = if (features.iptv) "Kanal, kategori ara" else "Ara",
                        metrics = metrics,
                        modifier = Modifier.fillMaxWidth().testTag("home_search")
                    )
                }

                if (errorMessage.isNotBlank()) {
                    item {
                        Spacer(Modifier.height(10.dp))
                        OkBanner(
                            if (offline) R.drawable.ic_ok_offline else R.drawable.ic_ok_alert,
                            if (offline) "Çevrimdışı: kayıtlı içerik gösteriliyor. $errorMessage" else errorMessage,
                            metrics
                        )
                    }
                }

                if (query.isNotBlank()) {
                    item { OkSectionTitle("Arama sonuçları", metrics, "${results.size} kanal") }
                    if (results.isEmpty()) {
                        item {
                            OkStateMessage(
                                R.drawable.ic_ok_search,
                                "Kanal bulunamadı",
                                "\"$query\" için kanal yok." +
                                    if (features.googleSearch && webAllowed) " Google'da arayabilirsiniz." else "",
                                metrics,
                                actionLabel = if (features.googleSearch && webAllowed) "Google'da ara" else null,
                                onAction = { openGoogle(query) }
                            )
                        }
                    } else {
                        items(results.take(8), key = { "r${it.id}" }) { channel ->
                            OkChannelCard(
                                channel = channel,
                                categoryLabel = channel.categories.firstOrNull()?.let(ChannelCategories::displayName),
                                favorite = channel.id in favorites,
                                metrics = metrics,
                                onOpen = { playChannel(channel) },
                                onToggleFavorite = { favorites = store.toggleFavorite(channel.id) },
                                modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)
                            )
                        }
                        if (results.size > 8) {
                            item {
                                OkPrimaryButton("Tüm sonuçlar (${results.size})", metrics) { openIptv(query = query) }
                            }
                        }
                    }
                    return@LazyColumn
                }

                item {
                    Spacer(Modifier.height(14.dp))
                    FeatureTiles(tiles, metrics)
                }

                if (features.iptv && recentChannels.isNotEmpty()) {
                    item { OkSectionTitle("Son izlenenler", metrics) }
                    item { PosterRow(recentChannels, metrics, "Devam et") }
                }

                if (features.iptv && featured.isNotEmpty()) {
                    item {
                        OkSectionTitle(
                            "Öne çıkanlar",
                            metrics,
                            if (channels.any { it.isFeatured }) null else "Sunucu sırası"
                        )
                    }
                    item { PosterRow(featured, metrics, null) }
                }

                if (features.iptv && categories.isNotEmpty()) {
                    item { OkSectionTitle("Kategoriler", metrics, "${categories.size} kategori") }
                    item {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.testTag("home_categories")) {
                            item {
                                OkChip("Favoriler", false, metrics, { openIptv(IptvActivity.FILTER_FAVORITES) },
                                    favoriteChannels.size, R.drawable.ic_ok_star)
                            }
                            items(categories, key = { it.first }) { (category, count) ->
                                OkChip(ChannelCategories.displayName(category), false, metrics, { openIptv(category) }, count)
                            }
                        }
                    }
                }

                if (features.iptv && favoriteChannels.isNotEmpty()) {
                    item { OkSectionTitle("Favoriler", metrics, "${favoriteChannels.size}") }
                    item { PosterRow(favoriteChannels, metrics, "★") }
                }

                if (features.iptv && channels.isEmpty() && licenseChecked) {
                    item {
                        OkStateMessage(
                            if (offline) R.drawable.ic_ok_offline else R.drawable.ic_ok_tv,
                            if (refreshing) "Kanallar yükleniyor…" else "Kanal listesi boş",
                            "Kanal listesi sunucudan alınınca burada görünür.",
                            metrics,
                            actionLabel = if (refreshing) null else "Yenile",
                            onAction = onRefresh
                        )
                    }
                }
            }
        }
    }

    private class HomeTile(
        val icon: Int,
        val title: String,
        val subtitle: String,
        val enabled: Boolean,
        val accent: Boolean = false,
        val onClick: () -> Unit
    )

    /** Geniş (yatay / araç) ekranda tek satır 4 kutucuk, dar ekranda 2x2. */
    @Composable
    private fun FeatureTiles(tiles: List<HomeTile>, metrics: OkMetrics) {
        BoxWithConstraints(Modifier.fillMaxWidth().testTag("home_tiles")) {
            val perRow = if (maxWidth >= 560.dp) 4 else 2
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                tiles.chunked(perRow).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        row.forEach { tile ->
                            OkFeatureTile(
                                icon = tile.icon,
                                title = tile.title,
                                subtitle = tile.subtitle,
                                metrics = metrics,
                                enabled = tile.enabled,
                                accent = tile.accent,
                                onClick = tile.onClick,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun PosterRow(channels: List<IptvChannel>, metrics: OkMetrics, badge: String?) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(channels, key = { it.id }) { channel ->
                OkChannelPoster(
                    channel,
                    metrics,
                    badge ?: channel.categories.firstOrNull()?.let(ChannelCategories::displayName)
                ) { playChannel(channel) }
            }
        }
    }

    @Composable
    private fun RadioScreen(contents: List<CarContent>, onBack: () -> Unit) {
        val metrics = rememberOkMetrics()
        androidx.activity.compose.BackHandler(onBack = onBack)

        Column(Modifier.fillMaxSize()) {
            OkTopBar(metrics, subtitle = "Radyo · ${contents.size} istasyon", onBack = onBack)

            if (contents.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    OkStateMessage(R.drawable.ic_ok_radio, "Radyo istasyonu yok",
                        "Lisansınıza tanımlı radyo içeriği bulunamadı.", metrics)
                }
                return@Column
            }

            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = metrics.gutter, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(contents, key = { it.id }) { content ->
                    Surface(
                        onClick = {
                            startActivity(
                                Intent(this@MainActivity, PlayerActivity::class.java).apply {
                                    putExtra(PlayerActivity.EXTRA_STREAM_URL, content.streamUrl)
                                    putExtra(PlayerActivity.EXTRA_TITLE, content.title)
                                    putExtra(PlayerActivity.EXTRA_MEDIA_TYPE, content.type)
                                }
                            )
                        },
                        shape = RoundedCornerShape(18.dp),
                        color = OkColors.Surface,
                        border = BorderStroke(1.dp, OkColors.Outline),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier.size(metrics.logo).background(OkColors.OrangeSoft, RoundedCornerShape(14.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(painterResource(R.drawable.ic_ok_radio), null, tint = OkColors.Orange, modifier = Modifier.size(28.dp))
                            }
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text(content.title, color = OkColors.Ink, fontSize = metrics.body,
                                    fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                content.description?.takeIf { it.isNotBlank() }?.let {
                                    Text(it, color = OkColors.InkMuted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                            Icon(painterResource(R.drawable.ic_ok_play), null, tint = OkColors.Orange, modifier = Modifier.size(28.dp))
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun SettingsScreen(
        customerName: String,
        deviceName: String,
        lastUpdate: String,
        channelCount: Int,
        refreshing: Boolean,
        onRefresh: () -> Unit,
        onBack: () -> Unit
    ) {
        val metrics = rememberOkMetrics()
        val context = LocalContext.current
        androidx.activity.compose.BackHandler(onBack = onBack)
        var libraryCleared by remember { mutableStateOf(false) }

        Column(Modifier.fillMaxSize()) {
            OkTopBar(metrics, subtitle = "Ayarlar", onBack = onBack)
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = metrics.gutter, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OkLogoMark(72.dp)
                        Spacer(Modifier.width(16.dp))
                        Column {
                            OkWordmark(24.sp)
                            Text(
                                "Sürüm " + runCatching {
                                    context.packageManager.getPackageInfo(context.packageName, 0).versionName
                                }.getOrNull().orEmpty(),
                                color = OkColors.InkMuted, fontSize = 13.sp
                            )
                        }
                    }
                }
                item { SettingRow("Müşteri", customerName.ifBlank { "—" }, metrics) }
                item { SettingRow("Cihaz", deviceName.ifBlank { "—" }, metrics) }
                item { SettingRow("Cihaz kimliği", AppSession.deviceUuid(context), metrics) }
                item { SettingRow("Kanal sayısı", "$channelCount", metrics) }
                item { SettingRow("Son güncelleme", lastUpdate.ifBlank { "—" }, metrics) }
                item {
                    SettingAction(
                        if (refreshing) "Lisans ve içerik yenileniyor…" else "Lisansı ve içeriği yenile",
                        R.drawable.ic_ok_refresh, metrics, onRefresh
                    )
                }
                item {
                    SettingAction(
                        if (libraryCleared) "Favoriler ve son izlenenler temizlendi" else "Favorileri ve son izlenenleri temizle",
                        R.drawable.ic_ok_history, metrics
                    ) {
                        AppSession.prefs(context).edit()
                            .remove(ChannelLibraryStore.KEY_FAVORITES)
                            .remove(ChannelLibraryStore.KEY_RECENTS)
                            .apply()
                        libraryCleared = true
                    }
                }
                // projection: gerçek araç denemesinin kaydı (adb gerekmez).
                if (CarPlayerBridge.hasCarDiagnostics) {
                    item { SettingAction("Araç teşhis kaydını paylaş", R.drawable.ic_ok_alert, metrics) { shareDiagnostics() } }
                }
                item {
                    Text(
                        "Park güvenliği: araç ekranında video yalnızca araç park hâlindeyken gösterilir; bu kilit kapatılamaz.",
                        color = OkColors.InkMuted, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        }
    }

    @Composable
    private fun SettingRow(label: String, value: String, metrics: OkMetrics) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(OkColors.Surface, RoundedCornerShape(16.dp))
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, color = OkColors.InkMuted, fontSize = metrics.body, modifier = Modifier.weight(1f))
            Text(value, color = OkColors.Ink, fontSize = metrics.body, fontWeight = FontWeight.SemiBold,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }

    @Composable
    private fun SettingAction(text: String, icon: Int, metrics: OkMetrics, onClick: () -> Unit) {
        Surface(
            onClick = onClick,
            shape = RoundedCornerShape(16.dp),
            color = OkColors.Surface,
            border = BorderStroke(1.dp, OkColors.Outline),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(painterResource(icon), null, tint = OkColors.Orange, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(14.dp))
                Text(text, color = OkColors.Ink, fontSize = metrics.body, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
