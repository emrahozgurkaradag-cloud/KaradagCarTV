package com.karadaglabs.cartv

import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Arayüz ekran görüntüleri (Android Emulator; gerçek araç / Android Auto
 * host YOK).
 *
 *  - Telefon ekranları: gerçek MainActivity / IptvActivity / PlayerActivity.
 *    Cihazın ağı test süresince KAPATILIR; böylece sahte test lisansı
 *    sunucuya gitmez, ekranlar önbellekten çizilir (çevrimdışı şeridi
 *    görünür; bu da gerçek bir durumdur).
 *  - Araç ekranı: CarVideoActivity'nin kullandığı AYNI görünümler
 *    (HostedNavigation.menuBar + CarChannelBrowser) head-unit piksel
 *    boyutlarında ve yoğunluğunda ekran dışı çizilir. Android Auto host
 *    penceresinde çalıştırma DEĞİLDİR.
 *
 * Kanal verisi test sabitidir: yayın adresi yok (file:// yer tutucu),
 * gerçek/yetkisiz URL içermez. Oynatıcı görüntüsü test varlığı tv1.ts ile.
 */
@RunWith(AndroidJUnit4::class)
class UiScreenshotDeviceTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val prefs = AppSession.prefs(context)
    private var savedPrefs: Map<String, *> = emptyMap<String, Any>()

    private val outDir: File by lazy {
        val arg = InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")
        val dir = if (arg != null) File(arg, "screenshots") else File(context.getExternalFilesDir(null), "screenshots")
        dir.mkdirs()
        dir
    }

    private fun sampleChannels(): List<IptvChannel> {
        val names = listOf(
            "Haber Merkez" to "News", "Spor Arena" to "Sports", "Çocuk Dünyası" to "Kids",
            "Müzik Kutusu" to "Music", "Belgesel Plus" to "Documentary", "Film Gecesi" to "Movies",
            "Ekonomi 24" to "Business", "Haber Hattı" to "News;General", "Spor Max" to "Sports",
            "Yerel Kanal" to "General", "Kültür Sanat" to "Culture", "Dizi Saati" to "Series",
            "Gezi Rotası" to "Travel", "Animasyon TV" to "Animation;Kids", "Eğlence Bir" to "Entertainment",
            "Bilim Kanalı" to "Education", "Yaşam Stili" to "Lifestyle", "Haber Global" to "News"
        )
        return names.mapIndexed { index, (name, category) ->
            IptvChannel(
                id = index + 1,
                name = name,
                streamUrl = "file:///screenshot-fixture/${index + 1}.ts",
                logoUrl = null,
                category = category,
                isFeatured = index in setOf(0, 1, 3, 5, 8),
                sortOrder = index + 1
            )
        }
    }

    private fun shell(command: String) {
        instrumentation.uiAutomation.executeShellCommand(command).close()
    }

    private fun setNetwork(enabled: Boolean) {
        val state = if (enabled) "enable" else "disable"
        shell("svc wifi $state")
        shell("svc data $state")
        SystemClock.sleep(2_000)
    }

    @Before
    fun setUp() {
        savedPrefs = HashMap(prefs.all)
        // Ana ekran CAR_SPEED izni ister; izin cihazda tanımlıysa önceden
        // verilir ki sistem iletişim kutusu ekran görüntüsünü örtmesin.
        try {
            instrumentation.uiAutomation.grantRuntimePermission(context.packageName, CarPlayerBridge.CAR_SPEED_PERMISSION)
        } catch (_: Exception) {
            // İzin bu emülatörde tanımlı değil: istek iletişim kutusu açılmaz.
        }
        setNetwork(false)

        val channels = sampleChannels()
        val contents = JSONArray().apply {
            listOf("Radyo Bir", "Klasik FM", "Yol Radyosu").forEachIndexed { i, title ->
                put(JSONObject().apply {
                    put("id", 100 + i); put("title", title); put("type", "radio")
                    put("stream_url", "file:///screenshot-fixture/radio$i.mp3"); put("description", "Test istasyonu")
                })
            }
        }
        prefs.edit()
            .putString("customer_email", "screenshot@test.invalid")
            .putString("license_key", "SCREENSHOT-TEST")
            .putString("license_features_cache", JSONObject().apply {
                put("iptv", true); put("radio", true); put("youtube", true); put("google_search", true)
            }.toString())
            .putString("license_customer_name", "Test Müşterisi")
            .putString("license_device_name", "Emülatör")
            .putString("contents", contents.toString())
            .putString("last_update", "27.11.2026 10:00")
            .putString(IptvRepository.CHANNELS_KEY, IptvChannels.toJson(channels).toString())
            .putString(IptvRepository.LAST_UPDATE_KEY, "27.11.2026 10:00")
            .putString(ChannelLibraryStore.KEY_FAVORITES, ChannelLibrary.encode(listOf(2, 4, 9)))
            .putString(ChannelLibraryStore.KEY_RECENTS, ChannelLibrary.encode(listOf(1, 3, 6, 8)))
            .commit()
    }

    @After
    fun tearDown() {
        // Kullanıcı/test verisi geri yüklenir, ağ yeniden açılır.
        prefs.edit().clear().apply {
            savedPrefs.forEach { (k, v) ->
                when (v) {
                    is String -> putString(k, v)
                    is Int -> putInt(k, v)
                    is Boolean -> putBoolean(k, v)
                    is Long -> putLong(k, v)
                    is Float -> putFloat(k, v)
                }
            }
        }.commit()
        setNetwork(true)
    }

    private fun screen(name: String) {
        SystemClock.sleep(1_500)
        instrumentation.waitForIdleSync()
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        assertTrue("screenshot $name", bitmap != null && bitmap.width > 0)
        save(bitmap!!, name)
    }

    private fun save(bitmap: Bitmap, name: String) {
        File(outDir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test
    fun phoneHomeLiveTvCategoriesAndPlayer() {
        ActivityScenario.launch(MainActivity::class.java).use { home ->
            SystemClock.sleep(3_000)
            screen("01-phone-home")
            home.onActivity { activity ->
                val list = activity.window.decorView
                assertTrue(list.isShown)
            }
        }

        ActivityScenario.launch(IptvActivity::class.java).use {
            SystemClock.sleep(2_500)
            screen("02-phone-live-tv-all")
        }

        val byCategory = Intent(context, IptvActivity::class.java)
            .putExtra(IptvActivity.EXTRA_FILTER, "News")
        ActivityScenario.launch<IptvActivity>(byCategory).use {
            SystemClock.sleep(2_000)
            screen("03-phone-live-tv-category-news")
        }

        val favorites = Intent(context, IptvActivity::class.java)
            .putExtra(IptvActivity.EXTRA_FILTER, IptvActivity.FILTER_FAVORITES)
        ActivityScenario.launch<IptvActivity>(favorites).use {
            SystemClock.sleep(2_000)
            screen("04-phone-live-tv-favorites")
        }

        // Oynatıcı: test varlığı (tv1.ts) yerel dosyadan, mevcut Media3 yolu.
        val media = File(context.cacheDir, "screenshot-media/tv1.ts")
        media.parentFile!!.mkdirs()
        instrumentation.context.assets.open("media/tv1.ts").use { input ->
            media.outputStream().use { input.copyTo(it) }
        }
        val player = Intent(context, PlayerActivity::class.java)
            .putExtra(PlayerActivity.EXTRA_STREAM_URL, android.net.Uri.fromFile(media).toString())
            .putExtra(PlayerActivity.EXTRA_TITLE, "Haber Merkez")
            .putExtra(PlayerActivity.EXTRA_MEDIA_TYPE, "iptv")
        ActivityScenario.launch<PlayerActivity>(player).use { scenario ->
            SystemClock.sleep(4_000)
            scenario.onActivity { activity ->
                // Kullanıcının ekrana dokunmasıyla açılan kontroller.
                activity.findViewById<View>(R.id.topBar).visibility = View.VISIBLE
                activity.findViewById<View>(R.id.controlBar).visibility = View.VISIBLE
            }
            screen("05-phone-player")
        }
    }

    /**
     * Araç ekranı yerleşimi: üst başlık (ÖK + 4 ana bölüm) ve kanal
     * tarayıcısı, yaygın head-unit çözünürlüklerinde.
     */
    @Test
    fun carLayoutAtHeadUnitSizes() {
        data class HeadUnit(val name: String, val widthPx: Int, val heightPx: Int, val dpi: Int)

        val units = listOf(
            HeadUnit("800x480-mdpi", 800, 480, 160),
            HeadUnit("1280x720-tvdpi", 1280, 720, 213),
            HeadUnit("1920x720-hdpi", 1920, 720, 240)
        )
        val channels = ChannelLibrary.sorted(sampleChannels())

        for (unit in units) {
            for (filter in listOf<String?>(null, "Sports", CarChannelBrowser.FILTER_FAVORITES)) {
                var root: LinearLayout? = null
                var browser: CarChannelBrowser? = null
                instrumentation.runOnMainSync {
                    val config = Configuration(context.resources.configuration).apply {
                        densityDpi = unit.dpi
                        screenWidthDp = unit.widthPx * 160 / unit.dpi
                        screenHeightDp = unit.heightPx * 160 / unit.dpi
                        uiMode = (uiMode and Configuration.UI_MODE_TYPE_MASK.inv()) or Configuration.UI_MODE_TYPE_CAR
                    }
                    val carContext = context.createConfigurationContext(config)
                    val library = ChannelLibraryStore(carContext)
                    val b = CarChannelBrowser(carContext, library, {}, {})
                    b.showChannels(channels, selected = 1, status = "${channels.size} kanal")
                    if (filter != null) b.setFilter(filter)
                    browser = b
                    root = LinearLayout(carContext).apply {
                        orientation = LinearLayout.VERTICAL
                        setBackgroundColor(CarUiKit.ANTHRACITE)
                        addView(HostedNavigation.menuBar(carContext, ProjectionDestination.LIVE_TV) {})
                        b.view.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
                        addView(b.view)
                    }
                    val view = root!!
                    view.measure(
                        View.MeasureSpec.makeMeasureSpec(unit.widthPx, View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(unit.heightPx, View.MeasureSpec.EXACTLY)
                    )
                    view.layout(0, 0, unit.widthPx, unit.heightPx)
                    // GridView çocuklarını ikinci geçişte yerleştirir.
                    view.measure(
                        View.MeasureSpec.makeMeasureSpec(unit.widthPx, View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(unit.heightPx, View.MeasureSpec.EXACTLY)
                    )
                    view.layout(0, 0, unit.widthPx, unit.heightPx)
                }

                val view = root!!
                val header = view.getChildAt(0) as LinearLayout
                // ÖK markası + 4 ana bölüm aynı satırda, ekran içinde.
                assertEquals(5, header.childCount)
                for (i in 0 until header.childCount) {
                    val child = header.getChildAt(i)
                    assertTrue("${unit.name} header child $i fits", child.right <= unit.widthPx && child.width > 0)
                }
                val density = unit.dpi / 160f
                for (i in 1 until header.childCount) {
                    assertTrue("${unit.name} tile touch target", header.getChildAt(i).height >= (60 * density).toInt())
                }
                val visible = browser!!.visibleChannels()
                assertTrue("${unit.name} $filter has channels", visible.isNotEmpty())

                val bitmap = Bitmap.createBitmap(unit.widthPx, unit.heightPx, Bitmap.Config.ARGB_8888)
                instrumentation.runOnMainSync { view.draw(Canvas(bitmap)) }
                val suffix = when (filter) {
                    null -> "all"
                    CarChannelBrowser.FILTER_FAVORITES -> "favorites"
                    else -> "category-" + filter.lowercase()
                }
                save(bitmap, "10-car-${unit.name}-$suffix")
            }
        }
    }
}
