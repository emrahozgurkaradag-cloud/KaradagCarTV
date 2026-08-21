package com.karadaglabs.cartv

import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.media3.common.util.UnstableApi

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

@androidx.annotation.OptIn(
    markerClass = [androidx.media3.common.util.UnstableApi::class]
)
@UnstableApi
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val email = AppSession.email(this)
        val licenseKey = AppSession.licenseKey(this)

        if (email.isBlank() || licenseKey.isBlank()) {
            startActivity(Intent(this, RegistrationActivity::class.java))
            finish()
            return
        }

        setContent {
            KaradagCarTVTheme {
                CarTvApp()
            }
        }
    }

    @Composable
    private fun CarTvApp() {
        val context = LocalContext.current

        var loading by remember { mutableStateOf(true) }
        var refreshing by remember { mutableStateOf(false) }
        var errorMessage by remember { mutableStateOf("") }
        var customerName by remember { mutableStateOf("") }
        var deviceName by remember { mutableStateOf("") }
        var contents by remember { mutableStateOf(emptyList<CarContent>()) }
        var features by remember { mutableStateOf(LicenseFeatures()) }
        var selectedType by remember { mutableStateOf<String?>(null) }
        var lastUpdate by remember { mutableStateOf("") }

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

                    val loadedFeatures = featuresFromJson(
                        license?.optJSONObject("features")
                    )

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

                    runOnUiThread {
                        customerName = customer?.optString("name").orEmpty()
                        deviceName = device?.optString("name").orEmpty()
                        features = loadedFeatures
                        contents = loadedContents
                        lastUpdate = saveContentsCache(context, loadedContents)
                        loading = false
                        refreshing = false
                    }

                } catch (exception: ApiException) {

                    runOnUiThread {
                        loading = false
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
                            startActivity(
                                Intent(
                                    this,
                                    RegistrationActivity::class.java
                                )
                            )
                            finish()
                        } else {
                            errorMessage = exception.message

                            if (contents.isEmpty()) {
                                contents = loadContentsCache(context)
                            }
                        }
                    }

                } catch (exception: Exception) {

                    runOnUiThread {
                        loading = false
                        refreshing = false

                        errorMessage = exception.message
                            ?: "Sunucuya bağlanırken hata oluştu."

                        if (contents.isEmpty()) {
                            contents = loadContentsCache(context)
                        }
                    }
                }
            }.start()
        }

        LaunchedEffect(Unit) {
            refresh()
        }

        when {
            loading && contents.isEmpty() -> {
                LoadingScreen()
            }

            selectedType != null -> {
                ContentListScreen(
                    title = "Canlı Radyo",
                    contents = contents.filter {
                        it.type == "radio"
                    },
                    onBack = {
                        selectedType = null
                    }
                )
            }

            else -> {
                HomeScreen(
                    customerName = customerName,
                    deviceName = deviceName,
                    contents = contents,
                    features = features,
                    refreshing = refreshing,
                    lastUpdate = lastUpdate,
                    errorMessage = errorMessage,
                    onRefresh = {
                        refresh()
                    },
                    onCategoryClick = {
                        selectedType = it
                    }
                )
            }
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

    @Composable
    private fun LoadingScreen() {

        Column(
            Modifier
                .fillMaxSize()
                .padding(32.dp),

            horizontalAlignment =
                Alignment.CenterHorizontally,

            verticalArrangement =
                Arrangement.Center
        ) {

            Text(
                "KARADAG CAR TV",
                style =
                    MaterialTheme.typography.headlineLarge
            )

            Spacer(
                Modifier.height(24.dp)
            )

            CircularProgressIndicator()

            Spacer(
                Modifier.height(16.dp)
            )

            Text(
                "Lisans ve içerikler kontrol ediliyor..."
            )
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
        errorMessage: String,
        onRefresh: () -> Unit,
        onCategoryClick: (String) -> Unit
    ) {

        val context =
            LocalContext.current

        val iptvCount =
            runCatching {

                val raw =
                    AppSession.prefs(context)
                        .getString(
                            "iptv_channels",
                            null
                        )

                if (raw.isNullOrBlank()) {
                    0
                } else {
                    JSONArray(raw).length()
                }

            }.getOrDefault(0)

        val menu =
            buildList {

                if (features.iptv) {
                    add(
                        "📺 Canlı TV" to
                                "${iptvCount} kanal"
                    )
                }

                if (features.radio) {
                    add(
                        "📻 Canlı Radyo" to
                                "${contents.count {
                                    it.type == "radio"
                                }} içerik"
                    )
                }

                if (features.youtube) {
                    add(
                        "▶ YouTube" to
                                "YouTube"
                    )
                }

                if (features.googleSearch) {
                    add(
                        "🔎 Google Arama" to
                                "Web"
                    )
                }
            }

        Column(
            Modifier
                .fillMaxSize()
                .padding(20.dp)
        ) {

            Text(
                "KARADAG CAR TV",
                style =
                    MaterialTheme.typography.headlineLarge
            )

            Spacer(
                Modifier.height(4.dp)
            )

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement =
                    Arrangement.SpaceBetween
            ) {

                Text(customerName)

                Text(deviceName)
            }

            Spacer(
                Modifier.height(12.dp)
            )

            Row(
                Modifier.fillMaxWidth(),

                horizontalArrangement =
                    Arrangement.SpaceBetween,

                verticalAlignment =
                    Alignment.CenterVertically
            ) {

                Text(
                    if (lastUpdate.isBlank()) {
                        "Henüz güncellenmedi"
                    } else {
                        "Son güncelleme: $lastUpdate"
                    },

                    style =
                        MaterialTheme.typography.bodySmall
                )

                Button(
                    enabled = !refreshing,
                    onClick = onRefresh
                ) {

                    if (refreshing) {

                        CircularProgressIndicator(
                            Modifier.height(18.dp)
                        )

                    } else {

                        Text("🔄 Güncelle")
                    }
                }
            }

            if (errorMessage.isNotBlank()) {

                Spacer(
                    Modifier.height(8.dp)
                )

                Text(
                    errorMessage,
                    color =
                        MaterialTheme.colorScheme.error
                )
            }

            Spacer(
                Modifier.height(20.dp)
            )

            LazyVerticalGrid(
                columns =
                    GridCells.Adaptive(230.dp),

                modifier =
                    Modifier.fillMaxSize(),

                contentPadding =
                    PaddingValues(
                        bottom = 20.dp
                    ),

                horizontalArrangement =
                    Arrangement.spacedBy(14.dp),

                verticalArrangement =
                    Arrangement.spacedBy(14.dp)
            ) {

                items(menu) { item ->

                    Card(
                        Modifier
                            .fillMaxWidth()
                            .clickable {

                                when (item.first)
                                {
                                        "📺 Canlı TV" -> {
                                            context.startActivity(
                                                Intent(
                                                    context,
                                                    IptvActivity::class.java
                                                )
                                            )
                                        }

                                    "📻 Canlı Radyo" -> {

                                        onCategoryClick(
                                            "radio"
                                        )
                                    }

                                    "▶ YouTube" -> {

                                        context.startActivity(
                                            Intent(
                                                context,
                                                YouTubeActivity::class.java
                                            )
                                        )
                                    }

                                    "🔎 Google Arama" -> {

                                        context.startActivity(
                                            Intent(
                                                Intent.ACTION_VIEW,
                                                Uri.parse(
                                                    "https://www.google.com/"
                                                )
                                            )
                                        )
                                    }
                                }
                            }
                    ) {

                        Column(
                            Modifier
                                .fillMaxWidth()
                                .padding(24.dp)
                        ) {

                            Text(
                                item.first,
                                style =
                                    MaterialTheme
                                        .typography
                                        .titleLarge
                            )

                            Spacer(
                                Modifier.height(6.dp)
                            )

                            Text(
                                item.second
                            )
                        }
                    }
                }
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
            Modifier
                .fillMaxSize()
                .padding(20.dp)
        ) {

            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment =
                    Alignment.CenterVertically
            ) {

                Button(
                    onClick = onBack
                ) {
                    Text("← Geri")
                }

                Spacer(
                    Modifier.weight(1f)
                )

                Text(
                    title,
                    style =
                        MaterialTheme
                            .typography
                            .headlineMedium
                )
            }

            Spacer(
                Modifier.height(16.dp)
            )

            if (contents.isEmpty()) {

                Box(
                    Modifier.fillMaxSize(),
                    contentAlignment =
                        Alignment.Center
                ) {

                    Text(
                        "Bu kategoride içerik bulunamadı."
                    )
                }

            } else {

                LazyColumn(
                    Modifier.fillMaxSize(),

                    verticalArrangement =
                        Arrangement.spacedBy(12.dp)
                ) {

                    items(contents) { content ->

                        Card(
                            Modifier
                                .fillMaxWidth()
                                .clickable {

                                    context.startActivity(
                                        Intent(
                                            context,
                                            PlayerActivity::class.java
                                        ).apply {

                                            putExtra(
                                                PlayerActivity.EXTRA_STREAM_URL,
                                                content.streamUrl
                                            )

                                            putExtra(
                                                PlayerActivity.EXTRA_TITLE,
                                                content.title
                                            )

                                            putExtra(
                                                PlayerActivity.EXTRA_MEDIA_TYPE,
                                                content.type
                                            )
                                        }
                                    )
                                }
                        ) {

                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(20.dp)
                            ) {

                                Text(
                                    content.title,
                                    style =
                                        MaterialTheme
                                            .typography
                                            .titleLarge
                                )

                                content.description?.let {

                                    if (it.isNotBlank()) {

                                        Spacer(
                                            Modifier.height(6.dp)
                                        )

                                        Text(it)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}