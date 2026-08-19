package com.karadaglabs.cartv

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items as lazyItems
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.karadaglabs.cartv.ui.theme.KaradagCarTVTheme
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class CarContent(
    val id: Int,
    val title: String,
    val type: String,
    val streamUrl: String,
    val imageUrl: String?,
    val description: String?
)

data class LicenseFeatures(
    val liveTv: Boolean = false,
    val radio: Boolean = false,
    val video: Boolean = false,
    val iptv: Boolean = false,
    val youtube: Boolean = false,
    val youtubeKids: Boolean = false,
    val googleSearch: Boolean = false
)

data class MenuItem(
    val title: String,
    val subtitle: String,
    val type: String
)

class MainActivity : ComponentActivity() {

    private val apiBaseUrl =
        "https://api.karadaglabs.com"

    private val deviceUuid =
        "CAR-TEST-001"

    private val licenseKey =
        "KDG-TEST-2026-001"

    private val prefsName =
        "karadag_car_tv_cache"

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {
        super.onCreate(savedInstanceState)

        setContent {
            KaradagCarTVTheme {
                CarTvApp()
            }
        }
    }

    @Composable
    private fun CarTvApp() {

        val context =
            LocalContext.current

        var loading by rememberState(true)

        var refreshing by rememberState(false)

        var licensed by rememberState(false)

        var errorMessage by rememberState("")

        var customerName by rememberState("")

        var deviceName by rememberState("")

        var contents by rememberState(
            emptyList<CarContent>()
        )

        var features by rememberState(
            LicenseFeatures()
        )

        var selectedType by rememberState<String?>(null)

        var lastUpdate by rememberState(
            getCachedLastUpdate(context)
        )

        fun refreshData() {

            if (refreshing) {
                return
            }

            refreshing = true
            errorMessage = ""

            loadData(
                context = context,

                onLoading = {
                    loading = it
                },

                onLicenseSuccess = {
                        customer,
                        device,
                        licenseFeatures ->

                    licensed = true

                    customerName =
                        customer

                    deviceName =
                        device

                    features =
                        licenseFeatures
                },

                onContentsLoaded = {
                        newContents ->

                    contents =
                        newContents

                    if (
                        newContents.isNotEmpty()
                    ) {

                        lastUpdate =
                            saveContentsToCache(
                                context,
                                newContents
                            )
                    }
                },

                onError = {
                        message ->

                    errorMessage =
                        message

                    val cached =
                        loadCachedContents(
                            context
                        )

                    if (
                        cached.isNotEmpty()
                    ) {

                        contents =
                            cached

                        val cachedLicense =
                            loadCachedLicense(
                                context
                            )

                        if (
                            cachedLicense != null
                        ) {

                            customerName =
                                cachedLicense.optString(
                                    "customer"
                                )

                            deviceName =
                                cachedLicense.optString(
                                    "device"
                                )

                            features =
                                licenseFeaturesFromJson(
                                    cachedLicense
                                        .optJSONObject(
                                            "features"
                                        )
                                )

                            licensed =
                                true
                        }

                    } else {

                        licensed =
                            false
                    }
                },

                onFinished = {
                    refreshing = false
                }
            )
        }

        LaunchedEffect(Unit) {
            refreshData()
        }

        when {

            loading &&
                    contents.isEmpty() -> {

                LoadingScreen()
            }

            !licensed &&
                    contents.isEmpty() -> {

                LicenseErrorScreen(
                    message =
                        errorMessage.ifBlank {
                            "Lisans doğrulanamadı."
                        },

                    onRetry = {

                        loading = true

                        refreshData()
                    }
                )
            }

            selectedType != null -> {

                ContentListScreen(
                    title =
                        when (selectedType) {

                            "radio" ->
                                "Canlı Radyo"

                            "video" ->
                                "Videolar"

                            else ->
                                "İçerikler"
                        },

                    contents =
                        contents.filter {
                            it.type == selectedType
                        },

                    onBack = {
                        selectedType = null
                    }
                )
            }

            else -> {

                HomeScreen(
                    customerName =
                        customerName,

                    deviceName =
                        deviceName,

                    contents =
                        contents,

                    features =
                        features,

                    refreshing =
                        refreshing,

                    lastUpdate =
                        lastUpdate,

                    onRefresh = {
                        refreshData()
                    },

                    onCategoryClick = {
                        selectedType = it
                    }
                )
            }
        }
    }

    private fun loadData(
        context: Context,
        onLoading: (Boolean) -> Unit,
        onLicenseSuccess: (
            String,
            String,
            LicenseFeatures
        ) -> Unit,
        onContentsLoaded: (
            List<CarContent>
        ) -> Unit,
        onError: (
            String
        ) -> Unit,
        onFinished: () -> Unit
    ) {

        Thread {

            try {

                runOnUiThread {
                    onLoading(true)
                }

                /*
                 * =====================================================
                 * 1. LISANS
                 * =====================================================
                 */

                val licenseResponse =
                    postJson(
                        url =
                            "$apiBaseUrl/api/license/validate",

                        body =
                            JSONObject().apply {

                                put(
                                    "device_uuid",
                                    deviceUuid
                                )

                                put(
                                    "license_key",
                                    licenseKey
                                )
                            }
                    )

                if (
                    !licenseResponse.optBoolean(
                        "licensed",
                        false
                    )
                ) {

                    throw IllegalStateException(
                        licenseResponse.optString(
                            "message",
                            "Lisans geçersiz."
                        )
                    )
                }

                val customerName =
                    licenseResponse
                        .optJSONObject(
                            "customer"
                        )
                        ?.optString(
                            "name"
                        )
                        .orEmpty()

                val deviceName =
                    licenseResponse
                        .optJSONObject(
                            "device"
                        )
                        ?.optString(
                            "name"
                        )
                        .orEmpty()

                val featuresObject =
                    licenseResponse
                        .optJSONObject(
                            "license"
                        )
                        ?.optJSONObject(
                            "features"
                        )

                val licenseFeatures =
                    licenseFeaturesFromJson(
                        featuresObject
                    )

                saveLicenseToCache(
                    context =
                        context,

                    customerName =
                        customerName,

                    deviceName =
                        deviceName,

                    features =
                        licenseFeatures
                )

                runOnUiThread {

                    onLicenseSuccess(
                        customerName,
                        deviceName,
                        licenseFeatures
                    )
                }

                /*
                 * =====================================================
                 * 2. TV / RADYO / VIDEO ICERIKLERI
                 *
                 * Eski "tv" içerikleri API'de kalabilir.
                 * Ancak artık kullanıcıya ayrı TV menüsü
                 * olarak gösterilmeyecek.
                 * Canlı TV ekranı IPTV olacak.
                 * =====================================================
                 */

                val contentResponse =
                    postJson(
                        url =
                            "$apiBaseUrl/api/content",

                        body =
                            JSONObject().apply {

                                put(
                                    "device_uuid",
                                    deviceUuid
                                )

                                put(
                                    "license_key",
                                    licenseKey
                                )
                            }
                    )

                val contentArray =
                    contentResponse
                        .optJSONArray(
                            "contents"
                        )

                val contentList =
                    mutableListOf<CarContent>()

                if (
                    contentArray != null
                ) {

                    for (
                    index in
                    0 until contentArray.length()
                    ) {

                        val item =
                            contentArray
                                .getJSONObject(
                                    index
                                )

                        contentList.add(
                            CarContent(

                                id =
                                    item.optInt(
                                        "id"
                                    ),

                                title =
                                    item.optString(
                                        "title"
                                    ),

                                type =
                                    item.optString(
                                        "type"
                                    ),

                                streamUrl =
                                    item.optString(
                                        "stream_url"
                                    ),

                                imageUrl =
                                    item.optString(
                                        "image_url"
                                    ).ifBlank {
                                        null
                                    },

                                description =
                                    item.optString(
                                        "description"
                                    ).ifBlank {
                                        null
                                    }
                            )
                        )
                    }
                }

                /*
                 * =====================================================
                 * 3. IPTV
                 *
                 * Ana ekrandaki Güncelle butonuna basıldığında
                 * IPTV listesi de yenilenir.
                 * Başarılı sonuç cache'e yazılır.
                 * =====================================================
                 */

                if (
                    licenseFeatures.iptv
                ) {

                    try {

                        updateIptvCache(
                            context
                        )

                    } catch (
                        _: Exception
                    ) {

                        /*
                         * IPTV güncellemesi başarısız olsa bile
                         * TV/Radyo/Video güncellemesini bozma.
                         *
                         * IptvActivity kendi cache'ini kullanacaktır.
                         */
                    }
                }

                runOnUiThread {

                    onContentsLoaded(
                        contentList
                    )

                    onLoading(false)

                    onFinished()
                }

            } catch (
                exception: Exception
            ) {

                runOnUiThread {

                    onError(
                        exception.message
                            ?: "Sunucuya bağlanırken hata oluştu."
                    )

                    onLoading(false)

                    onFinished()
                }
            }

        }.start()
    }

    private fun updateIptvCache(
        context: Context
    ) {

        val response =
            postJson(
                url =
                    "$apiBaseUrl/api/iptv",

                body =
                    JSONObject().apply {

                        put(
                            "device_uuid",
                            deviceUuid
                        )

                        put(
                            "license_key",
                            licenseKey
                        )
                    }
            )

        if (
            !response.optBoolean(
                "licensed",
                false
            )
        ) {

            throw IllegalStateException(
                response.optString(
                    "message",
                    "IPTV erişimi yok."
                )
            )
        }

        val channels =
            response.optJSONArray(
                "channels"
            )
                ?: throw IllegalStateException(
                    "IPTV kanal listesi alınamadı."
                )

        if (
            channels.length() == 0
        ) {

            throw IllegalStateException(
                "IPTV kanal listesi boş."
            )
        }

        val updateTime =
            SimpleDateFormat(
                "dd.MM.yyyy HH:mm",
                Locale("tr", "TR")
            ).format(Date())

        prefs(context)
            .edit()
            .putString(
                "iptv_channels",
                channels.toString()
            )
            .putString(
                "iptv_last_update",
                updateTime
            )
            .apply()
    }

    private fun postJson(
        url: String,
        body: JSONObject
    ): JSONObject {

        val connection =
            URL(url)
                .openConnection()
                    as HttpURLConnection

        try {

            connection.requestMethod =
                "POST"

            connection.connectTimeout =
                10_000

            connection.readTimeout =
                10_000

            connection.doOutput =
                true

            connection.setRequestProperty(
                "Content-Type",
                "application/json"
            )

            connection.setRequestProperty(
                "Accept",
                "application/json"
            )

            connection.outputStream.use {
                    output ->

                output.write(
                    body.toString()
                        .toByteArray(
                            Charsets.UTF_8
                        )
                )
            }

            val responseCode =
                connection.responseCode

            val responseText =
                if (
                    responseCode in 200..299
                ) {

                    connection.inputStream
                        .bufferedReader()
                        .use {
                            it.readText()
                        }

                } else {

                    connection.errorStream
                        ?.bufferedReader()
                        ?.use {
                            it.readText()
                        }
                        ?: ""
                }

            if (
                responseCode !in 200..299
            ) {

                throw IllegalStateException(

                    try {

                        JSONObject(
                            responseText
                        ).optString(
                            "message",
                            "API hatası: $responseCode"
                        )

                    } catch (
                        _: Exception
                    ) {

                        "API hatası: $responseCode"
                    }
                )
            }

            return JSONObject(
                responseText
            )

        } finally {

            connection.disconnect()
        }
    }

    /*
     * =========================================================
     * CACHE
     * =========================================================
     */

    private fun prefs(
        context: Context
    ) =
        context.getSharedPreferences(
            prefsName,
            Context.MODE_PRIVATE
        )

    private fun saveContentsToCache(
        context: Context,
        contents: List<CarContent>
    ): String {

        val array =
            JSONArray()

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
                        "image_url",
                        content.imageUrl ?: ""
                    )

                    put(
                        "description",
                        content.description ?: ""
                    )
                }
            )
        }

        val updateTime =
            SimpleDateFormat(
                "dd.MM.yyyy HH:mm",
                Locale("tr", "TR")
            ).format(Date())

        prefs(context)
            .edit()
            .putString(
                "contents",
                array.toString()
            )
            .putString(
                "last_update",
                updateTime
            )
            .apply()

        return updateTime
    }

    private fun loadCachedContents(
        context: Context
    ): List<CarContent> {

        val json =
            prefs(context)
                .getString(
                    "contents",
                    null
                )
                ?: return emptyList()

        return try {

            val array =
                JSONArray(json)

            val result =
                mutableListOf<CarContent>()

            for (
            index in
            0 until array.length()
            ) {

                val item =
                    array.getJSONObject(
                        index
                    )

                result.add(
                    CarContent(

                        id =
                            item.optInt(
                                "id"
                            ),

                        title =
                            item.optString(
                                "title"
                            ),

                        type =
                            item.optString(
                                "type"
                            ),

                        streamUrl =
                            item.optString(
                                "stream_url"
                            ),

                        imageUrl =
                            item.optString(
                                "image_url"
                            ).ifBlank {
                                null
                            },

                        description =
                            item.optString(
                                "description"
                            ).ifBlank {
                                null
                            }
                    )
                )
            }

            result

        } catch (
            _: Exception
        ) {

            emptyList()
        }
    }

    private fun getCachedLastUpdate(
        context: Context
    ): String {

        return prefs(context)
            .getString(
                "last_update",
                ""
            )
            .orEmpty()
    }

    private fun getCachedIptvCount(
        context: Context
    ): Int {

        val json =
            prefs(context)
                .getString(
                    "iptv_channels",
                    null
                )
                ?: return 0

        return try {

            JSONArray(json).length()

        } catch (
            _: Exception
        ) {

            0
        }
    }

    private fun saveLicenseToCache(
        context: Context,
        customerName: String,
        deviceName: String,
        features: LicenseFeatures
    ) {

        prefs(context)
            .edit()
            .putString(
                "license",

                JSONObject().apply {

                    put(
                        "customer",
                        customerName
                    )

                    put(
                        "device",
                        deviceName
                    )

                    put(
                        "features",

                        JSONObject().apply {

                            put(
                                "live_tv",
                                features.liveTv
                            )

                            put(
                                "radio",
                                features.radio
                            )

                            put(
                                "video",
                                features.video
                            )

                            put(
                                "iptv",
                                features.iptv
                            )

                            put(
                                "youtube",
                                features.youtube
                            )

                            put(
                                "youtube_kids",
                                features.youtubeKids
                            )

                            put(
                                "google_search",
                                features.googleSearch
                            )
                        }
                    )
                }.toString()
            )
            .apply()
    }

    private fun loadCachedLicense(
        context: Context
    ): JSONObject? {

        val json =
            prefs(context)
                .getString(
                    "license",
                    null
                )
                ?: return null

        return try {

            JSONObject(json)

        } catch (
            _: Exception
        ) {

            null
        }
    }

    private fun licenseFeaturesFromJson(
        objectJson: JSONObject?
    ): LicenseFeatures {

        return LicenseFeatures(

            liveTv =
                objectJson?.optBoolean(
                    "live_tv",
                    false
                ) ?: false,

            radio =
                objectJson?.optBoolean(
                    "radio",
                    false
                ) ?: false,

            video =
                objectJson?.optBoolean(
                    "video",
                    false
                ) ?: false,

            iptv =
                objectJson?.optBoolean(
                    "iptv",
                    false
                ) ?: false,

            youtube =
                objectJson?.optBoolean(
                    "youtube",
                    false
                ) ?: false,

            youtubeKids =
                objectJson?.optBoolean(
                    "youtube_kids",
                    false
                ) ?: false,

            googleSearch =
                objectJson?.optBoolean(
                    "google_search",
                    false
                ) ?: false
        )
    }

    /*
     * =========================================================
     * UI
     * =========================================================
     */

    @Composable
    private fun LoadingScreen() {

        Column(
            modifier =
                Modifier.fillMaxSize(),

            horizontalAlignment =
                Alignment.CenterHorizontally,

            verticalArrangement =
                Arrangement.Center
        ) {

            Text(
                text =
                    "KARADAG CAR TV",

                style =
                    MaterialTheme
                        .typography
                        .headlineLarge
            )

            Spacer(
                modifier =
                    Modifier.height(24.dp)
            )

            CircularProgressIndicator()

            Spacer(
                modifier =
                    Modifier.height(16.dp)
            )

            Text(
                text =
                    "Lisans ve içerikler kontrol ediliyor..."
            )
        }
    }

    @Composable
    private fun LicenseErrorScreen(
        message: String,
        onRetry: () -> Unit
    ) {

        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(32.dp),

            horizontalAlignment =
                Alignment.CenterHorizontally,

            verticalArrangement =
                Arrangement.Center
        ) {

            Text(
                text =
                    "KARADAG CAR TV",

                style =
                    MaterialTheme
                        .typography
                        .headlineLarge
            )

            Spacer(
                modifier =
                    Modifier.height(20.dp)
            )

            Text(
                text =
                    "Lisans doğrulanamadı.",

                style =
                    MaterialTheme
                        .typography
                        .headlineMedium
            )

            Spacer(
                modifier =
                    Modifier.height(12.dp)
            )

            Text(
                text =
                    message
            )

            Spacer(
                modifier =
                    Modifier.height(24.dp)
            )

            Button(
                onClick =
                    onRetry
            ) {

                Text(
                    text =
                        "Tekrar Dene"
                )
            }
        }
    }

    @Composable
    private fun HomeScreen(
        customerName: String,
        deviceName: String,
        contents: List<CarContent>,
        features: LicenseFeatures,
        refreshing: Boolean,
        lastUpdate: String,
        onRefresh: () -> Unit,
        onCategoryClick: (String) -> Unit
    ) {

        val context =
            LocalContext.current

        val iptvCount =
            getCachedIptvCount(
                context
            )

        val menuItems =
            buildList {

                /*
                 * =================================================
                 * CANLI TV
                 *
                 * Eski "tv" içeriğini göstermiyoruz.
                 * IPTV artık kullanıcıya Canlı TV olarak sunuluyor.
                 * =================================================
                 */

                if (
                    features.iptv
                ) {

                    add(
                        MenuItem(
                            title =
                                "📺 Canlı TV",

                            subtitle =
                                if (
                                    iptvCount > 0
                                ) {
                                    "$iptvCount kanal"
                                } else {
                                    "TV Kanalları"
                                },

                            type =
                                "iptv"
                        )
                    )
                }

                if (
                    features.radio
                ) {

                    add(
                        MenuItem(
                            title =
                                "📻 Canlı Radyo",

                            subtitle =
                                "${contents.count {
                                    it.type == "radio"
                                }} içerik",

                            type =
                                "radio"
                        )
                    )
                }

                if (
                    features.video
                ) {

                    add(
                        MenuItem(
                            title =
                                "🎬 Videolar",

                            subtitle =
                                "${contents.count {
                                    it.type == "video"
                                }} içerik",

                            type =
                                "video"
                        )
                    )
                }

                if (
                    features.youtube
                ) {

                    add(
                        MenuItem(
                            title =
                                "▶ YouTube",

                            subtitle =
                                "YouTube",

                            type =
                                "youtube"
                        )
                    )
                }

                if (
                    features.youtubeKids
                ) {

                    add(
                        MenuItem(
                            title =
                                "🧒 YouTube Kids",

                            subtitle =
                                "Çocuk",

                            type =
                                "youtube_kids"
                        )
                    )
                }

                if (
                    features.googleSearch
                ) {

                    add(
                        MenuItem(
                            title =
                                "🔎 Google Arama",

                            subtitle =
                                "Web",

                            type =
                                "google"
                        )
                    )
                }
            }

        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(20.dp)
        ) {

            Text(
                text =
                    "KARADAG CAR TV",

                style =
                    MaterialTheme
                        .typography
                        .headlineLarge
            )

            Spacer(
                modifier =
                    Modifier.height(4.dp)
            )

            Row(
                modifier =
                    Modifier.fillMaxWidth(),

                horizontalArrangement =
                    Arrangement.SpaceBetween
            ) {

                Text(
                    text =
                        customerName
                )

                Text(
                    text =
                        deviceName
                )
            }

            Spacer(
                modifier =
                    Modifier.height(12.dp)
            )

            Row(
                modifier =
                    Modifier.fillMaxWidth(),

                horizontalArrangement =
                    Arrangement.SpaceBetween,

                verticalAlignment =
                    Alignment.CenterVertically
            ) {

                Text(
                    text =
                        if (
                            lastUpdate.isBlank()
                        ) {

                            "Henüz güncellenmedi"

                        } else {

                            "Son güncelleme: $lastUpdate"
                        },

                    style =
                        MaterialTheme
                            .typography
                            .bodySmall
                )

                Button(
                    enabled =
                        !refreshing,

                    onClick =
                        onRefresh
                ) {

                    if (
                        refreshing
                    ) {

                        CircularProgressIndicator(
                            modifier =
                                Modifier.height(
                                    18.dp
                                )
                        )

                    } else {

                        Text(
                            text =
                                "🔄 Güncelle"
                        )
                    }
                }
            }

            Spacer(
                modifier =
                    Modifier.height(20.dp)
            )

            LazyVerticalGrid(

                columns =
                    GridCells.Adaptive(
                        minSize = 230.dp
                    ),

                modifier =
                    Modifier.fillMaxSize(),

                contentPadding =
                    PaddingValues(
                        bottom = 20.dp
                    ),

                horizontalArrangement =
                    Arrangement.spacedBy(
                        14.dp
                    ),

                verticalArrangement =
                    Arrangement.spacedBy(
                        14.dp
                    )
            ) {

                gridItems(
                    items =
                        menuItems
                ) { item ->

                    MenuCard(
                        item =
                            item,

                        onClick = {

                            when (
                                item.type
                            ) {

                                "radio",
                                "video" -> {

                                    onCategoryClick(
                                        item.type
                                    )
                                }

                                "iptv" -> {

                                    context.startActivity(
                                        Intent(
                                            context,
                                            IptvActivity::class.java
                                        )
                                    )
                                }

                                "youtube" -> {

                                    context.startActivity(
                                        Intent(
                                            context,
                                            YouTubeActivity::class.java
                                        )
                                    )
                                }

                                "youtube_kids" -> {

                                    context.startActivity(
                                        Intent(
                                            context,
                                            YouTubeActivity::class.java
                                        ).apply {

                                            putExtra(
                                                "youtube_mode",
                                                "kids"
                                            )
                                        }
                                    )
                                }

                                "google" -> {

                                    context.startActivity(
                                        Intent(
                                            Intent.ACTION_VIEW,

                                            android.net.Uri.parse(
                                                "https://www.google.com/"
                                            )
                                        )
                                    )
                                }
                            }
                        }
                    )
                }
            }
        }
    }

    @Composable
    private fun MenuCard(
        item: MenuItem,
        onClick: () -> Unit
    ) {

        Card(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clickable {
                        onClick()
                    }
        ) {

            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(24.dp)
            ) {

                Text(
                    text =
                        item.title,

                    style =
                        MaterialTheme
                            .typography
                            .titleLarge
                )

                Spacer(
                    modifier =
                        Modifier.height(6.dp)
                )

                Text(
                    text =
                        item.subtitle,

                    style =
                        MaterialTheme
                            .typography
                            .bodyMedium
                )
            }
        }
    }

    @Composable
    private fun ContentListScreen(
        title: String,
        contents: List<CarContent>,
        onBack: () -> Unit
    ) {

        val context =
            LocalContext.current

        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(20.dp)
        ) {

            Row(
                modifier =
                    Modifier.fillMaxWidth(),

                verticalAlignment =
                    Alignment.CenterVertically
            ) {

                Button(
                    onClick =
                        onBack
                ) {

                    Text(
                        text =
                            "← Geri"
                    )
                }

                Spacer(
                    modifier =
                        Modifier.weight(1f)
                )

                Text(
                    text =
                        title,

                    style =
                        MaterialTheme
                            .typography
                            .headlineMedium
                )

                Spacer(
                    modifier =
                        Modifier.weight(1f)
                )
            }

            Spacer(
                modifier =
                    Modifier.height(16.dp)
            )

            if (
                contents.isEmpty()
            ) {

                Box(
                    modifier =
                        Modifier.fillMaxSize(),

                    contentAlignment =
                        Alignment.Center
                ) {

                    Text(
                        text =
                            "Bu kategoride içerik bulunamadı."
                    )
                }

            } else {

                LazyColumn(
                    modifier =
                        Modifier.fillMaxSize(),

                    verticalArrangement =
                        Arrangement.spacedBy(
                            12.dp
                        )
                ) {

                    lazyItems(
                        items =
                            contents
                    ) { content ->

                        Card(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .clickable {

                                        context.startActivity(

                                            Intent(
                                                context,
                                                PlayerActivity::class.java
                                            ).apply {

                                                putExtra(
                                                    PlayerActivity
                                                        .EXTRA_STREAM_URL,

                                                    content.streamUrl
                                                )

                                                putExtra(
                                                    PlayerActivity
                                                        .EXTRA_TITLE,

                                                    content.title
                                                )

                                                putExtra(
                                                    PlayerActivity
                                                        .EXTRA_MEDIA_TYPE,

                                                    content.type
                                                )
                                            }
                                        )
                                    }
                        ) {

                            Column(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(20.dp)
                            ) {

                                Text(
                                    text =
                                        content.title,

                                    style =
                                        MaterialTheme
                                            .typography
                                            .titleLarge
                                )

                                Spacer(
                                    modifier =
                                        Modifier.height(6.dp)
                                )

                                content.description
                                    ?.takeIf {
                                        it.isNotBlank()
                                    }
                                    ?.let {

                                        Text(
                                            text =
                                                it
                                        )
                                    }
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun <T> rememberState(
        initial: T
    ) =
        androidx.compose.runtime.remember {
            mutableStateOf(initial)
        }
}