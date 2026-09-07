package com.karadaglabs.cartv

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.LruCache
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items as lazyItems
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.media3.common.util.UnstableApi
import com.karadaglabs.cartv.ui.theme.KaradagCarTVTheme
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class IptvChannel(
    val id: Int,
    val name: String,
    val streamUrl: String,
    val logoUrl: String?,
    val category: String?
)

@UnstableApi
class IptvActivity : ComponentActivity() {

    private val apiBaseUrl =
        AppSession.API_BASE_URL

    private val deviceUuid by lazy {
        AppSession.deviceUuid(this)
    }

    private val licenseKey by lazy {
        AppSession.licenseKey(this)
    }

    private val prefsName =
        "karadag_car_tv_cache"

    private val logoCache =
        object : LruCache<String, Bitmap>(30) {}

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {
        super.onCreate(savedInstanceState)

        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {

                override fun handleOnBackPressed() {
                    finish()
                }
            }
        )

        setContent {

            KaradagCarTVTheme {
                IptvScreen()
            }
        }
    }

    @Composable
    private fun IptvScreen() {

        var loading by remember {
            mutableStateOf(true)
        }

        var refreshing by remember {
            mutableStateOf(false)
        }

        var errorMessage by remember {
            mutableStateOf("")
        }

        var channels by remember {
            mutableStateOf(
                emptyList<IptvChannel>()
            )
        }

        var selectedCategory by remember {
            mutableStateOf<String?>(null)
        }

        var searchText by remember {
            mutableStateOf("")
        }

        var lastUpdate by remember {
            mutableStateOf(
                getLastIptvUpdate(this)
            )
        }

        LaunchedEffect(Unit) {

            loadIptv(
                context = this@IptvActivity,

                onSuccess = { loadedChannels ->

                    channels =
                        loadedChannels

                    loading = false
                    refreshing = false

                    lastUpdate =
                        getLastIptvUpdate(
                            this@IptvActivity
                        )
                },

                onError = { message ->

                    errorMessage = message

                    loading = false
                    refreshing = false
                }
            )
        }

        when {

            loading -> {

                LoadingScreen()
            }

            errorMessage.isNotBlank() &&
                    channels.isEmpty() -> {

                ErrorScreen(
                    message = errorMessage,

                    onRetry = {

                        errorMessage = ""
                        loading = true

                        loadIptv(
                            context = this@IptvActivity,

                            onSuccess = {
                                channels = it
                                loading = false
                                refreshing = false

                                lastUpdate =
                                    getLastIptvUpdate(
                                        this@IptvActivity
                                    )
                            },

                            onError = {
                                errorMessage = it
                                loading = false
                                refreshing = false
                            }
                        )
                    }
                )
            }

            else -> {

                val categories =
                    buildCategoryList(
                        channels
                    )

                val normalizedSearch =
                    searchText.trim()

                val filteredChannels =
                    channels.filter { channel ->

                        val matchesCategory =
                            selectedCategory == null ||
                                    channelBelongsToCategory(
                                        channel.category,
                                        selectedCategory
                                    )

                        val matchesSearch =
                            normalizedSearch.isBlank() ||
                                    channel.name.contains(
                                        normalizedSearch,
                                        ignoreCase = true
                                    )

                        matchesCategory &&
                                matchesSearch
                    }

                IptvLandscapeLayout(
                    channels = filteredChannels,

                    totalChannelCount =
                        channels.size,

                    categories = categories,

                    selectedCategory =
                        selectedCategory,

                    searchText =
                        searchText,

                    lastUpdate =
                        lastUpdate,

                    refreshing =
                        refreshing,

                    onSearchChanged = {
                        searchText = it
                    },

                    onCategoryChanged = {
                        selectedCategory = it
                    },

                    onRefresh = {

                        if (!refreshing) {

                            refreshing = true
                            errorMessage = ""

                            loadIptv(
                                context =
                                    this@IptvActivity,

                                onSuccess = {
                                    channels = it
                                    refreshing = false

                                    lastUpdate =
                                        getLastIptvUpdate(
                                            this@IptvActivity
                                        )
                                },

                                onError = {
                                    errorMessage = it
                                    refreshing = false
                                }
                            )
                        }
                    },

                    onBack = {
                        finish()
                    },

                    onChannelClick = {
                        openPlayer(it)
                    }
                )
            }
        }
    }

    private fun loadIptv(
        context: Context,
        onSuccess: (
            List<IptvChannel>
        ) -> Unit,
        onError: (
            String
        ) -> Unit
    ) {

        Thread {

            try {

                if (licenseKey.isBlank()) {

                    throw IllegalStateException(
                        "Cihaz aktive edilmemiş: license_key boş"
                    )
                }

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

                val channelArray =
                    response.optJSONArray(
                        "channels"
                    )

                val loadedChannels =
                    parseIptvChannels(
                        channelArray
                    )

                if (loadedChannels.isEmpty()) {

                    throw IllegalStateException(
                        "IPTV kanal listesi boş."
                    )
                }

                saveIptvCache(
                    context,
                    loadedChannels
                )

                runOnUiThread {

                    onSuccess(
                        loadedChannels
                    )
                }

            } catch (
                exception: Exception
            ) {

                /*
                 * API başarısızsa son başarılı
                 * IPTV listesini kullan.
                 */

                val cachedChannels =
                    loadCachedIptv(
                        context
                    )

                if (
                    cachedChannels.isNotEmpty()
                ) {

                    runOnUiThread {

                        onSuccess(
                            cachedChannels
                        )
                    }

                } else {

                    runOnUiThread {

                        onError(
                            exception.message
                                ?: "IPTV verileri alınamadı."
                        )
                    }
                }
            }
        }.start()
    }

    private fun parseIptvChannels(
        channelArray: JSONArray?
    ): List<IptvChannel> {

        val result =
            mutableListOf<IptvChannel>()

        if (channelArray == null) {
            return result
        }

        for (
        index in
        0 until channelArray.length()
        ) {

            val item =
                channelArray.getJSONObject(
                    index
                )

            result.add(
                IptvChannel(

                    id =
                        item.optInt(
                            "id"
                        ),

                    name =
                        item.optString(
                            "name"
                        ),

                    streamUrl =
                        item.optString(
                            "stream_url"
                        ),

                    logoUrl =
                        item.optString(
                            "logo_url"
                        ).ifBlank {
                            null
                        },

                    category =
                        item.optString(
                            "category"
                        ).ifBlank {
                            null
                        }
                )
            )
        }

        return result
    }

    private fun saveIptvCache(
        context: Context,
        channels: List<IptvChannel>
    ) {

        val array =
            JSONArray()

        channels.forEach { channel ->

            array.put(
                JSONObject().apply {

                    put(
                        "id",
                        channel.id
                    )

                    put(
                        "name",
                        channel.name
                    )

                    put(
                        "stream_url",
                        channel.streamUrl
                    )

                    put(
                        "logo_url",
                        channel.logoUrl ?: ""
                    )

                    put(
                        "category",
                        channel.category ?: ""
                    )
                }
            )
        }

        val updateTime =
            SimpleDateFormat(
                "dd.MM.yyyy HH:mm",
                Locale("tr", "TR")
            ).format(Date())

        context
            .getSharedPreferences(
                prefsName,
                Context.MODE_PRIVATE
            )
            .edit()
            .putString(
                "iptv_channels",
                array.toString()
            )
            .putString(
                "iptv_last_update",
                updateTime
            )
            .apply()
    }

    private fun loadCachedIptv(
        context: Context
    ): List<IptvChannel> {

        val json =
            context
                .getSharedPreferences(
                    prefsName,
                    Context.MODE_PRIVATE
                )
                .getString(
                    "iptv_channels",
                    null
                )
                ?: return emptyList()

        return try {

            parseIptvChannels(
                JSONArray(json)
            )

        } catch (
            _: Exception
        ) {

            emptyList()
        }
    }

    private fun getLastIptvUpdate(
        context: Context
    ): String {

        return context
            .getSharedPreferences(
                prefsName,
                Context.MODE_PRIVATE
            )
            .getString(
                "iptv_last_update",
                ""
            )
            .orEmpty()
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
                15_000

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
                            "IPTV API hatası: $responseCode"
                        )

                    } catch (
                        _: Exception
                    ) {

                        "IPTV API hatası: $responseCode"
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

    @Composable
    private fun LoadingScreen() {

        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(
                        MaterialTheme
                            .colorScheme
                            .background
                    ),

            contentAlignment =
                Alignment.Center
        ) {

            Column(
                horizontalAlignment =
                    Alignment.CenterHorizontally
            ) {

                CircularProgressIndicator()

                Spacer(
                    modifier =
                        Modifier.height(14.dp)
                )

                Text(
                    text =
                        "IPTV kanalları yükleniyor..."
                )
            }
        }
    }

    @Composable
    private fun ErrorScreen(
        message: String,
        onRetry: () -> Unit
    ) {

        Box(
            modifier =
                Modifier.fillMaxSize(),

            contentAlignment =
                Alignment.Center
        ) {

            Column(
                horizontalAlignment =
                    Alignment.CenterHorizontally
            ) {

                Text(
                    text =
                        "IPTV yüklenemedi.",

                    style =
                        MaterialTheme
                            .typography
                            .headlineMedium,

                    fontWeight =
                        FontWeight.Bold
                )

                Spacer(
                    modifier =
                        Modifier.height(10.dp)
                )

                Text(
                    text = message
                )

                Spacer(
                    modifier =
                        Modifier.height(18.dp)
                )

                Button(
                    onClick = onRetry
                ) {

                    Text(
                        text = "Tekrar Dene"
                    )
                }
            }
        }
    }

    @Composable
    private fun IptvLandscapeLayout(
        channels: List<IptvChannel>,
        totalChannelCount: Int,
        categories: List<String>,
        selectedCategory: String?,
        searchText: String,
        lastUpdate: String,
        refreshing: Boolean,
        onSearchChanged: (String) -> Unit,
        onCategoryChanged: (String?) -> Unit,
        onRefresh: () -> Unit,
        onBack: () -> Unit,
        onChannelClick: (
            IptvChannel
        ) -> Unit
    ) {

        Row(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(12.dp)
        ) {

            CategoryPanel(
                categories =
                    categories,

                selectedCategory =
                    selectedCategory,

                onCategoryChanged =
                    onCategoryChanged,

                onBack =
                    onBack
            )

            Spacer(
                modifier =
                    Modifier.width(12.dp)
            )

            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .weight(1f)
            ) {

                Row(
                    modifier =
                        Modifier.fillMaxWidth(),

                    verticalAlignment =
                        Alignment.CenterVertically
                ) {

                    Column(
                        modifier =
                            Modifier.weight(1f)
                    ) {

                        Text(
                            text = "IPTV",

                            style =
                                MaterialTheme
                                    .typography
                                    .headlineLarge,

                            fontWeight =
                                FontWeight.Bold
                        )

                        Text(
                            text =
                                if (
                                    selectedCategory == null
                                ) {
                                    "$totalChannelCount kanal"
                                } else {
                                    "${channels.size} kanal"
                                }
                        )

                        if (
                            lastUpdate.isNotBlank()
                        ) {

                            Text(
                                text =
                                    "Son güncelleme: $lastUpdate",

                                style =
                                    MaterialTheme
                                        .typography
                                        .bodySmall
                            )
                        }
                    }

                    Button(
                        enabled =
                            !refreshing,

                        onClick =
                            onRefresh
                    ) {

                        if (refreshing) {

                            CircularProgressIndicator(
                                modifier =
                                    Modifier.size(
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

                    Spacer(
                        modifier =
                            Modifier.width(12.dp)
                    )

                    OutlinedTextField(
                        value =
                            searchText,

                        onValueChange =
                            onSearchChanged,

                        modifier =
                            Modifier.width(300.dp),

                        singleLine =
                            true,

                        label = {
                            Text(
                                "Kanal ara"
                            )
                        },

                        placeholder = {
                            Text(
                                "TRT, ATV, NTV..."
                            )
                        }
                    )
                }

                Spacer(
                    modifier =
                        Modifier.height(12.dp)
                )

                if (
                    channels.isEmpty()
                ) {

                    Box(
                        modifier =
                            Modifier.fillMaxSize(),

                        contentAlignment =
                            Alignment.Center
                    ) {

                        Text(
                            text =
                                if (
                                    searchText.isNotBlank()
                                ) {
                                    "Aramanızla eşleşen kanal bulunamadı."
                                } else {
                                    "Bu kategoride kanal bulunamadı."
                                }
                        )
                    }

                } else {

                    LazyVerticalGrid(

                        columns =
                            GridCells.Adaptive(
                                minSize = 250.dp
                            ),

                        modifier =
                            Modifier.fillMaxSize(),

                        horizontalArrangement =
                            Arrangement.spacedBy(
                                10.dp
                            ),

                        verticalArrangement =
                            Arrangement.spacedBy(
                                10.dp
                            )
                    ) {

                        gridItems(
                            items = channels
                        ) { channel ->

                            ChannelCard(
                                channel =
                                    channel,

                                onClick = {
                                    onChannelClick(
                                        channel
                                    )
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun CategoryPanel(
        categories: List<String>,
        selectedCategory: String?,
        onCategoryChanged: (
            String?
        ) -> Unit,
        onBack: () -> Unit
    ) {

        Surface(
            modifier =
                Modifier
                    .width(190.dp)
                    .fillMaxHeight(),

            shape =
                RoundedCornerShape(
                    16.dp
                ),

            tonalElevation = 3.dp
        ) {

            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(12.dp)
            ) {

                Text(
                    text = "Kategoriler",

                    style =
                        MaterialTheme
                            .typography
                            .titleMedium,

                    fontWeight =
                        FontWeight.Bold
                )

                Spacer(
                    modifier =
                        Modifier.height(10.dp)
                )

                SmallMenuButton(
                    text = "← Geri",

                    selected = false,

                    onClick = onBack
                )

                Spacer(
                    modifier =
                        Modifier.height(6.dp)
                )

                SmallMenuButton(
                    text = "Tümü",

                    selected =
                        selectedCategory == null,

                    onClick = {
                        onCategoryChanged(
                            null
                        )
                    }
                )

                Spacer(
                    modifier =
                        Modifier.height(6.dp)
                )

                LazyColumn(
                    modifier =
                        Modifier.fillMaxSize(),

                    verticalArrangement =
                        Arrangement.spacedBy(
                            6.dp
                        )
                ) {

                    lazyItems(
                        items = categories
                    ) { category ->

                        SmallMenuButton(
                            text =
                                categoryDisplayName(
                                    category
                                ),

                            selected =
                                selectedCategory
                                    ?.equals(
                                        category,
                                        ignoreCase = true
                                    ) == true,

                            onClick = {

                                onCategoryChanged(
                                    category
                                )
                            }
                        )
                    }
                }
            }
        }
    }

    @Composable
    private fun SmallMenuButton(
        text: String,
        selected: Boolean,
        onClick: () -> Unit
    ) {

        Surface(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clickable {
                        onClick()
                    },

            shape =
                RoundedCornerShape(
                    10.dp
                ),

            color =
                if (selected) {

                    MaterialTheme
                        .colorScheme
                        .primaryContainer

                } else {

                    MaterialTheme
                        .colorScheme
                        .surface
                }
        ) {

            Text(
                text = text,

                modifier =
                    Modifier.padding(
                        horizontal = 12.dp,
                        vertical = 10.dp
                    ),

                style =
                    MaterialTheme
                        .typography
                        .bodyMedium,

                fontWeight =
                    if (selected) {

                        FontWeight.Bold

                    } else {

                        FontWeight.Normal
                    }
            )
        }
    }

    @Composable
    private fun ChannelCard(
        channel: IptvChannel,
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

            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(12.dp),

                verticalAlignment =
                    Alignment.CenterVertically
            ) {

                ChannelLogo(
                    logoUrl =
                        channel.logoUrl,

                    modifier =
                        Modifier.size(64.dp)
                )

                Spacer(
                    modifier =
                        Modifier.width(12.dp)
                )

                Column(
                    modifier =
                        Modifier.weight(1f)
                ) {

                    Text(
                        text =
                            channel.name,

                        style =
                            MaterialTheme
                                .typography
                                .titleMedium,

                        fontWeight =
                            FontWeight.SemiBold,

                        maxLines = 2
                    )

                    Spacer(
                        modifier =
                            Modifier.height(4.dp)
                    )

                    Text(
                        text =
                            categoryDisplayName(
                                channel.category
                                    ?: "General"
                            ),

                        style =
                            MaterialTheme
                                .typography
                                .bodySmall
                    )
                }

                Text(
                    text = "▶",

                    style =
                        MaterialTheme
                            .typography
                            .headlineSmall,

                    color =
                        MaterialTheme
                            .colorScheme
                            .primary
                )
            }
        }
    }

    @Composable
    private fun ChannelLogo(
        logoUrl: String?,
        modifier: Modifier
    ) {

        var bitmap by remember(
            logoUrl
        ) {
            mutableStateOf<Bitmap?>(
                null
            )
        }

        LaunchedEffect(
            logoUrl
        ) {

            if (logoUrl.isNullOrBlank()) {

                bitmap = null

                return@LaunchedEffect
            }

            val cached =
                logoCache.get(
                    logoUrl
                )

            if (cached != null) {

                bitmap = cached

                return@LaunchedEffect
            }

            bitmap =
                loadLogo(
                    logoUrl
                )
        }

        Box(
            modifier =
                modifier
                    .clip(
                        RoundedCornerShape(
                            10.dp
                        )
                    )
                    .background(
                        MaterialTheme
                            .colorScheme
                            .surfaceVariant
                    ),

            contentAlignment =
                Alignment.Center
        ) {

            val image =
                bitmap

            if (image != null) {

                Image(
                    bitmap =
                        image.asImageBitmap(),

                    contentDescription =
                        null,

                    modifier =
                        Modifier.fillMaxSize(),

                    contentScale =
                        ContentScale.Fit
                )

            } else {

                Text(
                    text = "TV",

                    style =
                        MaterialTheme
                            .typography
                            .labelLarge
                )
            }
        }
    }

    private fun loadLogo(
        urlString: String
    ): Bitmap? {

        return try {

            val connection =
                URL(urlString)
                    .openConnection()
                        as HttpURLConnection

            connection.connectTimeout =
                5000

            connection.readTimeout =
                5000

            connection.setRequestProperty(
                "User-Agent",
                "KaradagCarTV/1.0"
            )

            connection.doInput =
                true

            connection.connect()

            val bitmap =
                connection.inputStream.use {
                    BitmapFactory.decodeStream(
                        it
                    )
                }

            connection.disconnect()

            if (bitmap != null) {

                logoCache.put(
                    urlString,
                    bitmap
                )
            }

            bitmap

        } catch (
            _: Exception
        ) {

            null
        }
    }

    private fun buildCategoryList(
        channels: List<IptvChannel>
    ): List<String> {

        val parts =
            channels
                .flatMap { channel ->

                    channel.category
                        ?.split(";")
                        ?: emptyList()
                }
                .map {
                    it.trim()
                }
                .filter {
                    it.isNotBlank()
                }
                .distinct()

        val preferredOrder =
            listOf(
                "News",
                "Sports",
                "Kids",
                "Music",
                "Movies",
                "Entertainment",
                "General",
                "Business",
                "Documentary",
                "Education",
                "Culture",
                "Travel",
                "Lifestyle",
                "Religious",
                "Animation",
                "Series",
                "Undefined"
            )

        return parts.sortedWith(
            compareBy(
                { value ->

                    val index =
                        preferredOrder.indexOfFirst {
                            it.equals(
                                value,
                                ignoreCase = true
                            )
                        }

                    if (index == -1) {
                        999
                    } else {
                        index
                    }
                },

                {
                    it.lowercase(
                        Locale.getDefault()
                    )
                }
            )
        )
    }

    private fun channelBelongsToCategory(
        rawCategory: String?,
        selectedCategory: String?
    ): Boolean {

        if (
            selectedCategory.isNullOrBlank()
        ) {

            return true
        }

        return rawCategory
            ?.split(";")
            ?.any {

                it.trim().equals(
                    selectedCategory,
                    ignoreCase = true
                )
            } == true
    }

    private fun categoryDisplayName(
        category: String
    ): String {

        return when (
            category.trim()
                .lowercase(
                    Locale.getDefault()
                )
        ) {

            "news" ->
                "Haber"

            "sports" ->
                "Spor"

            "kids" ->
                "Çocuk"

            "music" ->
                "Müzik"

            "movies" ->
                "Film"

            "entertainment" ->
                "Eğlence"

            "business" ->
                "İş Dünyası"

            "documentary" ->
                "Belgesel"

            "education" ->
                "Eğitim"

            "culture" ->
                "Kültür"

            "travel" ->
                "Seyahat"

            "lifestyle" ->
                "Yaşam"

            "religious" ->
                "Dini"

            "animation" ->
                "Animasyon"

            "series" ->
                "Dizi"

            "general" ->
                "Genel"

            "undefined" ->
                "Diğer"

            else ->
                category
        }
    }

    private fun openPlayer(
        channel: IptvChannel
    ) {

        val intent =
            android.content.Intent(
                this,
                PlayerActivity::class.java
            ).apply {

                putExtra(
                    PlayerActivity.EXTRA_STREAM_URL,
                    channel.streamUrl
                )

                putExtra(
                    PlayerActivity.EXTRA_TITLE,
                    channel.name
                )

                putExtra(
                    PlayerActivity.EXTRA_MEDIA_TYPE,
                    "iptv"
                )
            }

        startActivity(intent)
    }
}